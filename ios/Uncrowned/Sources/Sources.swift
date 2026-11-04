import Foundation

/* ---------- SoundCloud (sem chave: usa o mesmo client_id que o site dele usa) ---------- */
enum SC {
    private static var cid: String? = UserDefaults.standard.string(forKey: "scid")
    static func clientId(force: Bool = false) async -> String? {
        if !force, let c = cid { return c }
        guard let home = try? await Net.text("https://soundcloud.com/") else { return nil }
        let re = try! NSRegularExpression(pattern: "<script[^>]+src=\"(https://a-v2\\.sndcdn\\.com/assets/[^\"]+\\.js)\"")
        let scripts = re.matches(in: home, range: NSRange(home.startIndex..., in: home)).compactMap { Range($0.range(at: 1), in: home).map { String(home[$0]) } }.reversed()
        let idRe = try! NSRegularExpression(pattern: "client_id\\s*[:=]\\s*\"?([a-zA-Z0-9]{32})")
        for s in scripts {
            guard let js = try? await Net.text(s) else { continue }
            if let m = idRe.firstMatch(in: js, range: NSRange(js.startIndex..., in: js)), let r = Range(m.range(at: 1), in: js) {
                cid = String(js[r]); UserDefaults.standard.set(cid, forKey: "scid"); return cid
            }
        }
        return nil
    }
    /* chama a API; se o client_id venceu, pega outro e tenta de novo */
    private static func api(_ path: String) async -> Any? {
        for attempt in 0..<2 {
            guard let id = await clientId(force: attempt == 1) else { return nil }
            let sep = path.contains("?") ? "&" : "?"
            do { return try await Net.json("https://api-v2.soundcloud.com\(path)\(sep)client_id=\(id)") }
            catch let e as Net.HTTPError where e.code == 401 || e.code == 403 { cid = nil; continue }
            catch { return nil }
        }
        return nil
    }
    static func map(_ t: JObj) -> Track? {
        let url = t.s("permalink_url"); guard !url.isEmpty else { return nil }
        let pub = t.o("publisher_metadata")?.s("artist") ?? ""
        let art = (t.s("artwork_url").nilIfEmpty ?? t.o("user")?.s("avatar_url") ?? "").replacingOccurrences(of: "-large.", with: "-t500x500.")
        var tr = Track(src: "sc", id: url.replacingOccurrences(of: "https://soundcloud.com/", with: ""), title: t.s("title"), artist: pub.nilIfEmpty ?? t.o("user")?.s("username") ?? "", art: art, duration: t.i("duration"))
        tr.genre = t.s("genre")
        if t.b("downloadable") { tr.download = "sc" }
        return tr
    }
    struct Hit { var t: Track; var policy: String; var user: String; var plays: Int }
    static func searchRich(_ q: String) async -> [Hit] {
        guard let j = await api("/search/tracks?q=\(Net.enc(q))&limit=25") as? JObj else { return [] }
        return j.a("collection").compactMap { x in
            guard let o = x as? JObj, let t = map(o) else { return nil }
            return Hit(t: t, policy: o.s("policy"), user: o.o("user")?.s("username") ?? "", plays: o.i("playback_count"))
        }
    }
    static func search(_ q: String) async -> [Track] { await searchRich(q).map { $0.t } }
    /* endereço de áudio que o próprio site usa pra tocar (inteiro; prévia de 30 s não serve) */
    static func stream(_ path: String) async -> (url: String, hls: Bool, art: String, duration: Int)? {
        guard let tr = await api("/resolve?url=\(Net.enc("https://soundcloud.com/" + path))") as? JObj, tr.s("kind") == "track" else { return nil }
        if ["SNIP", "BLOCK"].contains(tr.s("policy")) { return nil }
        let tcs = tr.o("media")?.a("transcodings").compactMap { $0 as? JObj } ?? []
        let pick = tcs.first { $0.o("format")?.s("protocol") == "progressive" } ?? tcs.first { $0.o("format")?.s("protocol") == "hls" && $0.o("format")?.s("mime_type").contains("mpeg") == true } ?? tcs.first { $0.o("format")?.s("protocol") == "hls" }
        guard let tc = pick, let id = await clientId() else { return nil }
        let auth = tr.s("track_authorization")
        guard let j = try? await Net.json("\(tc.s("url"))?client_id=\(id)\(auth.isEmpty ? "" : "&track_authorization=" + auth)") as? JObj, !j.s("url").isEmpty else { return nil }
        return (j.s("url"), tc.o("format")?.s("protocol") == "hls", tr.s("artwork_url").replacingOccurrences(of: "-large.", with: "-t500x500."), tr.i("duration"))
    }
    static func charts(_ region: String) async -> [Track] {
        let reg = region == "ZZ" ? "" : "&region=soundcloud:regions:\(region)"
        for p in ["/charts?kind=top&genre=soundcloud:genres:all-music\(reg)&high_tier_only=false&limit=50", "/charts?kind=trending&genre=soundcloud:genres:all-music\(reg)&high_tier_only=false&limit=50", "/charts?kind=top&genre=soundcloud:genres:all-music&high_tier_only=false&limit=50"] {
            if let j = await api(p) as? JObj {
                let l = j.a("collection").compactMap { x -> Track? in guard let o = x as? JObj else { return nil }; return map(o.o("track") ?? o) }
                if l.count >= 10 { return l }
            }
        }
        // plano B: artistas em alta no YouTube, procurados no SoundCloud
        let names = Array(Set(await YT.charts(region).top.map { cleanArtist($0.artist) }.filter { !$0.isEmpty })).prefix(10)
        let found = await pool(Array(names), 4) { await search($0) }
        var per: [String: Int] = [:]
        return found.compactMap { $0 }.flatMap { $0.prefix(4) }.filter { t in per[t.artist, default: 0] += 1; return per[t.artist]! <= 3 }
    }
    /* acha no SoundCloud a mesma música de um vídeo do YouTube (pra continuar tocando com a tela apagada) */
    static func match(_ v: Track) async -> Track? {
        var title = v.title.replacingOccurrences(of: "\\s*[(\\[].*?[)\\]]", with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces)
        var artist = cleanArtist(v.artist)
        if let r = title.range(of: " - ") { artist = String(title[..<r.lowerBound]).trimmingCharacters(in: .whitespaces); title = String(title[r.upperBound...]).trimmingCharacters(in: .whitespaces) }
        let c = { (x: String) in norm(x).replacingOccurrences(of: " ", with: "") }
        let ct = c(title), ca = c(artist)
        let junk = "(?i)slowed|sped up|nightcore|remix|\\bcover\\b|reverb|instrumental|karaok|\\blive\\b|ao vivo|8d|type beat|snippet|preview"
        func score(_ h: Hit) -> Int {
            if h.policy == "BLOCK" || h.policy == "SNIP" { return -99 }
            let ht = c(h.t.title.replacingOccurrences(of: "^.*? - ", with: "", options: .regularExpression))
            var s: Int
            if ht == ct { s = 6 } else if ht.hasPrefix(ct) || ht.hasSuffix(ct) { s = 4 } else if ht.contains(ct) { s = 2 } else { return -99 }
            let ha = c(h.t.artist), hu = c(h.user)
            if !ca.isEmpty && (ha == ca || hu == ca) { s += 5 }
            else if !ca.isEmpty && (ha.contains(ca) || (!ha.isEmpty && ca.contains(ha)) || hu.contains(ca)) { s += 3 }
            else if !ca.isEmpty { return -99 }
            if h.t.title.range(of: junk, options: .regularExpression) != nil && v.title.range(of: junk, options: .regularExpression) == nil { s -= 4 }
            if h.plays > 50000 { s += 1 }
            return s
        }
        var best: (Hit, Int)? = nil
        for q in ["\(artist) \(title)", title] {
            for h in await searchRich(q) { let sc = score(h); if sc >= 5 && sc > (best?.1 ?? 0) { best = (h, sc) } }
            if (best?.1 ?? 0) >= 9 { break }
        }
        return best?.0.t
    }
}

/* ---------- Audius (aberto e grátis) e Internet Archive: dá pra ouvir e baixar ---------- */
enum Audius {
    static let base = "https://api.audius.co/v1"
    static func map(_ o: JObj) -> Track {
        let id = o.s("id")
        var t = Track(src: "au", id: id, title: o.s("title"), artist: o.o("user")?.s("name") ?? "", art: o.o("artwork")?.s("480x480") ?? "", duration: o.i("duration") * 1000,
                      stream: "\(base)/tracks/\(id)/stream?app_name=UncrownedMusic")
        t.genre = o.s("genre")
        if o.b("is_downloadable") || o.o("download")?.b("is_downloadable") == true { t.download = "\(base)/tracks/\(id)/download?app_name=UncrownedMusic" }
        return t
    }
    static func search(_ q: String) async -> [Track] {
        ((try? await Net.json("\(base)/tracks/search?query=\(Net.enc(q))&app_name=UncrownedMusic")) as? JObj)?.a("data").compactMap { ($0 as? JObj).map(map) } ?? []
    }
    static func trending(_ genre: String? = nil) async -> [Track] {
        await YT.cached("au|\(genre ?? "")", 3600) {
            ((try? await Net.json("\(base)/tracks/trending?time=week\(genre.map { "&genre=" + Net.enc($0) } ?? "")&app_name=UncrownedMusic")) as? JObj)?.a("data").compactMap { ($0 as? JObj).map(map) } ?? []
        }
    }
}
enum Archive {
    static func search(_ q: String) async -> [Track] {
        let query = Net.enc("(\(q)) AND mediatype:audio AND collection:(opensource_audio OR netlabels OR audio_music)")
        guard let j = try? await Net.json("https://archive.org/advancedsearch.php?q=\(query)&fl[]=identifier&fl[]=title&fl[]=creator&rows=8&sort[]=downloads+desc&output=json") as? JObj else { return [] }
        let docs = j.o("response")?.a("docs").compactMap { $0 as? JObj } ?? []
        let res = await pool(docs, 4) { d -> [Track]? in
            let ident = d.s("identifier")
            guard let m = try? await Net.json("https://archive.org/metadata/\(ident)") as? JObj else { return nil }
            let creator = (d["creator"] as? [Any])?.first as? String ?? d.s("creator")
            return m.a("files").compactMap { $0 as? JObj }.filter { $0.s("name").lowercased().hasSuffix(".mp3") }.prefix(3).map { f in
                let url = "https://archive.org/download/\(ident)/\(f.s("name").addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? f.s("name"))"
                return Track(src: "ia", id: ident + "/" + f.s("name"), title: f.s("title").nilIfEmpty ?? f.s("name").replacingOccurrences(of: ".mp3", with: ""), artist: f.s("creator").nilIfEmpty ?? creator,
                             art: "https://archive.org/services/img/\(ident)", duration: Int(f.d("length") * 1000), stream: url, download: url, genre: "Archive")
            }
        }
        return res.compactMap { $0 }.flatMap { $0 }
    }
}

/* ---------- Deezer (grátis, sem chave): quem é parecido com quem ---------- */
enum Deezer {
    struct Artist { var id: Int; var name: String; var pic: String; var fans: Int }
    struct Song { var title: String; var artist: String; var dur: Int; var art: String }
    private static var artists: [String: Artist?] = [:]
    private static let lock = NSLock()
    static func artist(_ name: String) async -> Artist? {
        let k = name.lowercased()
        lock.lock(); if let a = artists[k] { lock.unlock(); return a }; lock.unlock()
        let l = ((try? await Net.json("https://api.deezer.com/search/artist?q=\(Net.enc(name))&limit=3")) as? JObj)?.a("data").compactMap { $0 as? JObj } ?? []
        let o = l.first { $0.s("name").lowercased() == k } ?? l.first
        let a = o.map { Artist(id: $0.i("id"), name: $0.s("name"), pic: $0.s("picture_medium"), fans: $0.i("nb_fan")) }
        lock.lock(); if artists.count > 600 { artists.removeAll() }; artists[k] = a; lock.unlock()
        return a
    }
    static func related(_ name: String) async -> [Artist] {
        guard let a = await artist(name) else { return [] }
        return ((try? await Net.json("https://api.deezer.com/artist/\(a.id)/related?limit=22")) as? JObj)?.a("data").compactMap { $0 as? JObj }.map { Artist(id: $0.i("id"), name: $0.s("name"), pic: $0.s("picture_medium"), fans: $0.i("nb_fan")) } ?? []
    }
    static func top(_ id: Int, _ n: Int) async -> [Song] {
        ((try? await Net.json("https://api.deezer.com/artist/\(id)/top?limit=\(n)")) as? JObj)?.a("data").compactMap { $0 as? JObj }.map {
            Song(title: $0.s("title_short").nilIfEmpty ?? $0.s("title"), artist: $0.o("artist")?.s("name") ?? "", dur: $0.i("duration"), art: $0.o("album")?.s("cover_xl") ?? "")
        } ?? []
    }
}

/* ---------- letras: Letras.mus.br (texto) e LRCLIB (sincronizada) ---------- */
enum Lyrics {
    struct L { var plain: String?; var synced: [(Double, String)]; var source: String }
    static func clean(_ s: String) -> String {
        s.replacingOccurrences(of: "(?i)\\s*[(\\[][^)\\]]*(official|oficial|video|v[ií]deo|lyric|audio|áudio|visualizer|prod|ft\\.|feat)[^)\\]]*[)\\]]", with: "", options: .regularExpression)
            .replacingOccurrences(of: "(?i)\\s*-\\s*topic$", with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces)
    }
    static func slug(_ s: String) -> String { norm(s).replacingOccurrences(of: " ", with: "-") }
    static func htmlText(_ h: String) -> String {
        var t = h.replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: .regularExpression).replacingOccurrences(of: "</p>", with: "\n\n")
        t = t.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        for (a, b) in [("&amp;", "&"), ("&quot;", "\""), ("&#39;", "'"), ("&apos;", "'"), ("&lt;", "<"), ("&gt;", ">"), ("&nbsp;", " ")] { t = t.replacingOccurrences(of: a, with: b) }
        return t.replacingOccurrences(of: "\n{3,}", with: "\n\n", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines)
    }
    static func letras(_ artist: String, _ title: String) async -> String? {
        func page(_ url: String) async -> String? {
            guard let html = try? await Net.text(url), let i = html.range(of: "lyric-original"), let gt = html[i.upperBound...].firstIndex(of: ">"),
                  let end = html[gt...].range(of: "</div>") else { return nil }
            let t = htmlText(String(html[html.index(after: gt)..<end.lowerBound])); return t.count > 20 ? t : nil
        }
        if let body = try? await Net.text("https://solr.sscdn.co/letras/m1/?q=\(Net.enc(artist + " " + title))&wt=json&callback=LetrasSug"),
           let a = body.firstIndex(of: "{"), let b = body.lastIndex(of: "}"),
           let j = try? JSONSerialization.jsonObject(with: Data(body[a...b].utf8)) as? JObj {
            for d in j.o("response")?.a("docs").compactMap({ $0 as? JObj }) ?? [] where !d.s("dns").isEmpty && !d.s("url").isEmpty {
                if let t = await page("https://www.letras.mus.br/\(d.s("dns"))/\(d.s("url"))/") { return t }
            }
        }
        return await page("https://www.letras.mus.br/\(slug(artist))/\(slug(title))/")
    }
    static func lrclib(_ artist: String, _ title: String, _ dur: Int) async -> L? {
        var j = (try? await Net.json("https://lrclib.net/api/get?artist_name=\(Net.enc(artist))&track_name=\(Net.enc(title))&duration=\(dur / 1000)")) as? JObj
        if j == nil { j = ((try? await Net.json("https://lrclib.net/api/search?q=\(Net.enc(artist + " " + title))")) as? [Any])?.first as? JObj }
        guard let j else { return nil }
        let synced = j.s("syncedLyrics").components(separatedBy: "\n").compactMap { l -> (Double, String)? in
            guard let r = l.range(of: "^\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]", options: .regularExpression) else { return nil }
            let tag = l[r].dropFirst().dropLast(); let p = tag.split(whereSeparator: { $0 == ":" || $0 == "." })
            guard p.count >= 2, let m = Double(p[0]), let s = Double(p[1]) else { return nil }
            let frac = p.count > 2 ? (Double("0." + String(p[2])) ?? 0) : 0
            return (m * 60 + s + frac, String(l[r.upperBound...]).trimmingCharacters(in: .whitespaces))
        }
        let plain = j.s("plainLyrics").nilIfEmpty
        if synced.isEmpty && plain == nil { return nil }
        return L(plain: plain, synced: synced, source: "LRCLIB")
    }
    static func get(_ t: Track, synced: Bool) async -> L? {
        var artist = clean(cleanArtist(t.artist)), title = clean(t.title)
        if let r = title.range(of: " - ") { let a = String(title[..<r.lowerBound]); if artist.isEmpty || artist.lowercased().contains("youtube") || norm(a).contains(norm(artist)) { artist = a }; title = String(title[r.upperBound...]) }
        if !synced, let p = await letras(artist, title) { return L(plain: p, synced: [], source: "Letras.mus.br") }
        if let l = await lrclib(artist, title, t.duration) { return l }
        if synced, let p = await letras(artist, title) { return L(plain: p, synced: [], source: "Letras.mus.br") }
        return nil
    }
}

/* ---------- informações da faixa (catálogo do iTunes) ---------- */
enum Info {
    struct I { var title: String; var artist: String; var album: String; var year: String; var genre: String; var art: String }
    static func get(_ t: Track) async -> I? {
        let title = Lyrics.clean(t.title).replacingOccurrences(of: "^.* - ", with: "", options: .regularExpression)
        let artist = t.title.contains(" - ") && t.artist.lowercased().contains("topic") ? String(t.title.split(separator: "-").first ?? "") : cleanArtist(t.artist)
        guard let j = try? await Net.json("https://itunes.apple.com/search?term=\(Net.enc(artist + " " + title))&entity=song&limit=5&country=BR") as? JObj else { return nil }
        let rs = j.a("results").compactMap { $0 as? JObj }
        guard let r = rs.first(where: { $0.s("trackName").lowercased().contains(title.prefix(12).lowercased()) }) ?? rs.first else { return nil }
        return I(title: r.s("trackName"), artist: r.s("artistName"), album: r.s("collectionName"), year: String(r.s("releaseDate").prefix(4)), genre: r.s("primaryGenreName"), art: r.s("artworkUrl100").replacingOccurrences(of: "100x100", with: "600x600"))
    }
}

/* ---------- rádios ao vivo (Radio Browser: catálogo aberto do mundo todo) ---------- */
enum Radio {
    static let servers = ["https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info", "https://at1.api.radio-browser.info", "https://fi1.api.radio-browser.info"]
    static func search(q: String = "", tag: String = "", country: String = "BR", limit: Int = 80) async -> [Station] {
        await YT.cached("radio|\(q)|\(tag)|\(country)", 1800) {
            var p = "hidebroken=true&order=clickcount&reverse=true&limit=\(limit)"
            if !q.isEmpty { p += "&name=\(Net.enc(q))" }; if !tag.isEmpty { p += "&tag=\(Net.enc(tag))" }; if country != "ZZ" { p += "&countrycode=\(country)" }
            for s in servers {
                if let a = try? await Net.json("\(s)/json/stations/search?\(p)") as? [Any] {
                    var seen = Set<String>()
                    return a.compactMap { $0 as? JObj }.filter { !$0.b("hls") && !($0.s("url_resolved").nilIfEmpty ?? $0.s("url")).isEmpty && !$0.s("name").isEmpty }.compactMap { x -> Station? in
                        let name = x.s("name").trimmingCharacters(in: .whitespaces); guard seen.insert(name.lowercased()).inserted else { return nil }
                        let tags = x.s("tags").split(separator: ",").prefix(2).joined(separator: ", ")
                        return Station(id: x.s("stationuuid"), name: name, stream: x.s("url_resolved").nilIfEmpty ?? x.s("url"), art: x.s("favicon"), info: [x.s("state").nilIfEmpty ?? x.s("country"), tags].filter { !$0.isEmpty }.joined(separator: " · "), votes: x.i("votes"))
                    }
                }
            }
            return []
        }
    }
    static func click(_ id: String) { Task { _ = try? await Net.data("\(servers[0])/json/url/\(id)") } }
}

/* ---------- importar playlists por link: YouTube / YouTube Music / Mix / Spotify ---------- */
enum Importer {
    static func link(_ raw: String) async throws -> (String, String, [Track], String) {
        let u = raw.trimmingCharacters(in: .whitespaces)
        if u.contains("spotify.com") { return try await spotify(u) }
        let list = u.range(of: "[?&]list=([\\w-]+)", options: .regularExpression).map { String(u[$0]).components(separatedBy: "=").last ?? "" }
        let alb = u.range(of: "browse/(MPRE[\\w-]+)", options: .regularExpression).map { String(u[$0]).replacingOccurrences(of: "browse/", with: "") }
        if let l = list, l.hasPrefix("RD") {
            let v = YT.idFrom(u) ?? String(l.dropFirst(2).suffix(11))
            let tracks = await YT.mix(v)
            return ("Mix de \(tracks.first?.title ?? "YouTube")", "https://i.ytimg.com/vi/\(v)/hqdefault.jpg", tracks, "YouTube")
        }
        if let id = alb ?? list {
            let (title, cover, tracks) = await YT.fullPlaylist(id)
            if tracks.isEmpty { throw NSError(domain: "imp", code: 1, userInfo: [NSLocalizedDescriptionKey: "Essa playlist não abriu (ela é pública?)"]) }
            return (title.nilIfEmpty ?? "Playlist do YouTube", cover.nilIfEmpty ?? tracks[0].art, tracks, "YouTube")
        }
        throw NSError(domain: "imp", code: 2, userInfo: [NSLocalizedDescriptionKey: "Cola um link de playlist do Spotify, YouTube ou YouTube Music"])
    }
    static func spotify(_ u: String) async throws -> (String, String, [Track], String) {
        guard let r = u.range(of: "open\\.spotify\\.com/(?:intl-[\\w-]+/)?(playlist|album|track)/([A-Za-z0-9]+)", options: .regularExpression) else { throw NSError(domain: "imp", code: 3) }
        let parts = u[r].split(separator: "/"); let kind = String(parts[parts.count - 2]), id = String(parts[parts.count - 1])
        let html = try await Net.text("https://open.spotify.com/embed/\(kind)/\(id)")
        guard let a = html.range(of: "<script id=\"__NEXT_DATA__\""), let s = html[a.upperBound...].firstIndex(of: ">"), let e = html[s...].range(of: "</script>"),
              let j = try? JSONSerialization.jsonObject(with: Data(html[html.index(after: s)..<e.lowerBound].utf8)), let ent = findKey(j, "entity") as? JObj else {
            throw NSError(domain: "imp", code: 4, userInfo: [NSLocalizedDescriptionKey: "O Spotify não mostrou essa playlist (ela é pública?)"]) }
        let items: [(String, String)] = kind == "track" ? [(ent.s("name"), (ent.a("artists").first as? JObj)?.s("name") ?? ent.s("subtitle"))]
            : ent.a("trackList").compactMap { $0 as? JObj }.map { ($0.s("title"), $0.s("subtitle")) }
        let cover = ((ent.o("coverArt")?.a("sources").first as? JObj)?.s("url")) ?? ""
        let found = await pool(items, 6) { await YT.find(cleanArtist($0.1), $0.0) }
        return (ent.s("name").nilIfEmpty ?? "Playlist do Spotify", cover, found.compactMap { $0 }, "Spotify")
    }
}
