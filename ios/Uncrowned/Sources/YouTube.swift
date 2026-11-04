import Foundation

/* YouTube e YouTube Music (mesma lógica do PC e do Android): busca, Mix, paradas, playlists, artistas e álbuns */
enum YT {
    static let webVer = "2.20250101.00.00"
    static let ytmVer = "1.20250101.01.00"
    private static var cache: [String: (Date, Any)] = [:]
    private static let lock = NSLock()
    static func cached<T>(_ k: String, _ ttl: TimeInterval, _ f: () async throws -> T) async rethrows -> T {
        lock.lock(); let hit = cache[k]; lock.unlock()
        if let hit, Date().timeIntervalSince(hit.0) < ttl, let v = hit.1 as? T { return v }
        let v = try await f()
        if let l = v as? [Any], l.isEmpty { return v }
        lock.lock(); cache[k] = (Date(), v); if cache.count > 300 { cache.removeAll() }; lock.unlock()
        return v
    }

    // ---------- YouTube (site) ----------
    private static func web(_ endpoint: String, _ body: JObj) async throws -> Any {
        var b = body
        b["context"] = ["client": ["clientName": "WEB", "clientVersion": webVer, "hl": "pt", "gl": "BR"]]
        return try await Net.json("https://www.youtube.com/youtubei/v1/\(endpoint)?prettyPrint=false", method: "POST",
            headers: ["Origin": "https://www.youtube.com", "X-Youtube-Client-Name": "1", "X-Youtube-Client-Version": webVer], body: b)
    }
    static func videos(_ data: Any) -> [Track] {
        var out: [Track] = []; var seen = Set<String>()
        func walk(_ x: Any) {
            if let a = x as? [Any] { a.forEach(walk); return }
            guard let o = x as? JObj else { return }
            if let v = o.o("videoRenderer") {
                let id = v.s("videoId"); if id.isEmpty || seen.contains(id) { return }; seen.insert(id)
                out.append(Track.yt(id, runsText(v["title"]), runsText(v["ownerText"] ?? v["longBylineText"]), duration: durText(runsText(v["lengthText"]))))
                return
            }
            o.values.forEach(walk)
        }
        walk(data)
        return out
    }
    static func search(_ q: String) async -> [Track] {
        await cached("yts|\(q.lowercased())", 900) {
            if let j = try? await web("search", ["query": q, "params": "EgIQAQ%3D%3D"]) { let r = videos(j); if !r.isEmpty { return Array(r.prefix(30)) } }
            // reserva: lê a página de resultados
            guard let html = try? await Net.text("https://www.youtube.com/results?search_query=\(Net.enc(q))&sp=EgIQAQ%253D%253D&hl=pt-BR", headers: ["Cookie": "CONSENT=YES+1"]),
                  let r = html.range(of: "ytInitialData"), let b = html[r.upperBound...].firstIndex(of: "{"), let js = jsonAt(html, from: b),
                  let j = try? JSONSerialization.jsonObject(with: Data(js.utf8)) else { return [] }
            return Array(videos(j).prefix(30))
        }
    }
    /* Mix do YouTube: fila "parecidas" montada pelo próprio YouTube a partir de um vídeo */
    static func mix(_ videoId: String) async -> [Track] {
        await cached("mix|\(videoId)", 3600) {
            guard let j = try? await web("next", ["videoId": videoId, "playlistId": "RD" + videoId]) else { return [] }
            var out: [Track] = []; var seen = Set<String>()
            func walk(_ x: Any) {
                if let a = x as? [Any] { a.forEach(walk); return }
                guard let o = x as? JObj else { return }
                if let v = o.o("playlistPanelVideoRenderer") {
                    let id = v.s("videoId"); if !id.isEmpty && !seen.contains(id) { seen.insert(id)
                        out.append(Track.yt(id, runsText(v["title"]), runsText(v["longBylineText"] ?? v["shortBylineText"]).replacingOccurrences(of: " - Topic", with: ""), duration: durText(runsText(v["lengthText"])))) }
                    return
                }
                o.values.forEach(walk)
            }
            walk(j); return out
        }
    }
    static func oembed(_ id: String) async -> (String, String)? {
        guard let j = try? await Net.json("https://www.youtube.com/oembed?url=\(Net.enc("https://www.youtube.com/watch?v=" + id))&format=json") as? JObj else { return nil }
        return (j.s("title"), j.s("author_name"))
    }
    static func idFrom(_ u: String) -> String? {
        guard let r = u.range(of: "(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|live/|v/))([A-Za-z0-9_-]{11})", options: .regularExpression) else { return nil }
        return String(u[r].suffix(11))
    }

    // ---------- YouTube Music ----------
    private static func ytm(_ endpoint: String, _ body: JObj) async throws -> Any {
        var b = body
        b["context"] = ["client": ["clientName": "WEB_REMIX", "clientVersion": ytmVer, "hl": "pt", "gl": "BR"]]
        return try await Net.json("https://music.youtube.com/youtubei/v1/\(endpoint)?prettyPrint=false", method: "POST",
            headers: ["Origin": "https://music.youtube.com", "Referer": "https://music.youtube.com/", "X-Youtube-Client-Name": "67", "X-Youtube-Client-Version": ytmVer], body: b)
    }
    static func thumb(_ x: Any?) -> String {
        guard let t = findKey(x, "thumbnails") as? [Any], let last = t.last as? JObj else { return "" }
        return last.s("url").replacingOccurrences(of: "=w\\d+-h\\d+", with: "=w544-h544", options: .regularExpression)
    }
    static func pageType(_ x: Any?) -> String { (findKey(x, "browseEndpointContextMusicConfig") as? JObj)?.s("pageType") ?? "" }
    private static func cols(_ r: JObj) -> [String] {
        r.a("flexColumns").map { runsText(($0 as? JObj)?.o("musicResponsiveListItemFlexColumnRenderer")?["text"]) }
    }
    /* músicas e playlists de qualquer página */
    static func parse(_ j: Any) -> (songs: [(Track, String)], lists: [YtmList]) {
        var songs: [(Track, String)] = []; var lists: [YtmList] = []; var seenS = Set<String>(); var seenL = Set<String>()
        func walk(_ x: Any, _ shelf: String) {
            if let a = x as? [Any] { a.forEach { walk($0, shelf) }; return }
            guard let o = x as? JObj else { return }
            if let sh = o.o("musicCarouselShelfRenderer") ?? o.o("musicShelfRenderer") ?? o.o("musicPlaylistShelfRenderer") {
                var t = runsText(sh.o("header")?.o("musicCarouselShelfBasicHeaderRenderer")?["title"] ?? sh["title"]); if t.isEmpty { t = shelf }
                walk(sh["contents"] ?? [Any](), t); return
            }
            if let r = o.o("musicResponsiveListItemRenderer") {
                let c = cols(r)
                let id = r.o("playlistItemData")?.s("videoId").nilIfEmpty ?? (findKey(r, "watchEndpoint") as? JObj)?.s("videoId")
                let len = r.a("fixedColumns").map { runsText(($0 as? JObj)?.o("musicResponsiveListItemFixedColumnRenderer")?["text"]) }.first { durText($0) > 0 } ?? ""
                if let id, !id.isEmpty, let t0 = c.first, !t0.isEmpty {
                    if !seenS.contains(id) { seenS.insert(id); songs.append((Track.yt(id, t0, (c.count > 1 ? c[1] : "").components(separatedBy: " • ").first ?? "", duration: durText(len)), shelf)) }
                } else if let be = findKey(r, "browseEndpoint") as? JObj, be.s("browseId").range(of: "^(VL|PL|RD|OL)", options: .regularExpression) != nil {
                    let bid = be.s("browseId"); if !seenL.contains(bid) { seenL.insert(bid); lists.append(YtmList(id: bid, title: c.first ?? "", sub: c.count > 1 ? c[1] : "", art: thumb(r["thumbnail"]), shelf: shelf)) }
                }
                return
            }
            if let r = o.o("musicTwoRowItemRenderer") {
                let ne = r.o("navigationEndpoint"); let title = runsText(r["title"]); let sub = runsText(r["subtitle"])
                if let w = ne?.o("watchEndpoint")?.s("videoId"), !w.isEmpty {
                    if !seenS.contains(w) { seenS.insert(w); songs.append((Track.yt(w, title, sub.components(separatedBy: " • ").first ?? ""), shelf)) }
                } else if let b = ne?.o("browseEndpoint")?.s("browseId"), !b.isEmpty {
                    if !seenL.contains(b) { seenL.insert(b); lists.append(YtmList(id: b, title: title, sub: sub, art: thumb(r["thumbnailRenderer"]), shelf: shelf)) }
                }
                return
            }
            o.values.forEach { walk($0, shelf) }
        }
        walk(j, "")
        return (songs, lists)
    }
    static func browseId(_ id: String) -> String { id.range(of: "^(VL|MPRE|OLAK|UC|FE)", options: .regularExpression) != nil ? id : "VL" + id }
    static func playlist(_ id: String) async -> [Track] {
        await cached("pl|\(id)", 3 * 3600) { (try? await ytm("browse", ["browseId": browseId(id)])).map { parse($0).songs.map { $0.0 } } ?? [] }
    }
    struct Charts { var title: String; var top: [Track]; var videos: [Track]; var lists: [YtmList] }
    static func charts(_ gl: String) async -> Charts {
        await cached("charts|\(gl)", 3 * 3600) {
            guard let j = try? await ytm("browse", ["browseId": "FEmusic_charts", "formData": ["selectedValues": [gl]]]) else { return Charts(title: "", top: [], videos: [], lists: []) }
            let p = parse(j)
            let pl = p.lists.first { $0.title.range(of: "(?i)top|100|mais", options: .regularExpression) != nil && ($0.title + " " + $0.shelf).range(of: "(?i)m[uú]sica|song|faixa", options: .regularExpression) != nil }
                ?? p.lists.first { $0.title.range(of: "(?i)top|100", options: .regularExpression) != nil } ?? p.lists.first
            var top: [Track] = []
            if let pl { top = await playlist(pl.id) }
            if top.isEmpty { top = p.songs.map { $0.0 } }
            let vids = p.songs.filter { $0.1.range(of: "(?i)v[ií]deo", options: .regularExpression) != nil }.map { $0.0 }
            return Charts(title: pl?.title ?? "Mais ouvidas da semana", top: Array(top.prefix(50)), videos: Array(vids.prefix(20)), lists: p.lists.filter { $0.id != pl?.id })
        }
    }
    static func popularLists(_ gl: String) async -> [(String, [YtmList])] {
        let v: [[Any]] = await cached("lists|\(gl)", 6 * 3600) {
            let ok = { (l: YtmList) in l.id.range(of: "^(VL)?(PL|RDCLAK|OLAK)", options: .regularExpression) != nil }
            async let home = try? ytm("browse", ["browseId": "FEmusic_home"])
            async let ch = try? ytm("browse", ["browseId": "FEmusic_charts", "formData": ["selectedValues": [gl]]])
            var groups: [(String, [YtmList])] = []
            func add(_ shelf: String, _ l: YtmList) { if let i = groups.firstIndex(where: { $0.0 == shelf }) { if groups[i].1.count < 20 && !groups[i].1.contains(l) { groups[i].1.append(l) } } else { groups.append((shelf, [l])) } }
            if let c = await ch { parse(c).lists.filter(ok).forEach { add("Paradas do YouTube Music", $0) } }
            if let h = await home { parse(h).lists.filter(ok).forEach { add($0.shelf.isEmpty ? "Playlists em destaque" : $0.shelf, $0) } }
            var seen = Set<String>()
            return groups.map { g -> [Any] in [g.0, g.1.filter { seen.insert($0.id).inserted }] }.filter { (($0[1] as? [YtmList]) ?? []).count >= 2 }.prefix(6).map { $0 }
        }
        return v.compactMap { x in (x.first as? String).flatMap { s in (x.last as? [YtmList]).map { (s, $0) } } }
    }
    struct Search { var artists: [YtmArtistHit] = []; var albums: [YtmAlbumHit] = []; var songs: [Track] = [] }
    static func ytmSearch(_ q: String) async -> Search {
        await cached("ytms|\(q.lowercased())", 900) {
            guard let j = try? await ytm("search", ["query": q]) else { return Search() }
            var s = Search(); var sa = Set<String>(), sb = Set<String>(), ss = Set<String>()
            func walk(_ x: Any) {
                if let a = x as? [Any] { a.forEach(walk); return }
                guard let o = x as? JObj else { return }
                let card = o.o("musicCardShelfRenderer")
                if let r = o.o("musicResponsiveListItemRenderer") ?? card {
                    let c: [String] = card != nil ? [runsText(r["title"]), runsText(r["subtitle"])] : cols(r)
                    let nav: JObj? = card != nil ? ((r.o("title")?.a("runs").first as? JObj)?.o("navigationEndpoint")) : r.o("navigationEndpoint")
                    let sid = r.o("playlistItemData")?.s("videoId").nilIfEmpty ?? nav?.o("watchEndpoint")?.s("videoId").nilIfEmpty
                    let be = nav?.o("browseEndpoint"); let pt = be.map { pageType($0) } ?? ""
                    let c1 = (c.count > 1 ? c[1] : "").components(separatedBy: " • ").map { $0.trimmingCharacters(in: .whitespaces) }
                    if let sid, let t0 = c.first, !t0.isEmpty {
                        let artist = c1.first { x in !x.isEmpty && x.range(of: "(?i)^(m[uú]sica|song|v[ií]deo|video|single|ep|[aá]lbum)$", options: .regularExpression) == nil && durText(x) == 0 && x.range(of: "(?i)(visualiza|views|plays|reprodu|ouvintes)", options: .regularExpression) == nil } ?? c1.first ?? ""
                        if ss.insert(sid).inserted { s.songs.append(Track.yt(sid, t0, artist)) }
                    } else if let be, pt.contains("ARTIST") {
                        if sa.insert(be.s("browseId")).inserted { s.artists.append(YtmArtistHit(id: be.s("browseId"), name: c.first ?? "", sub: c1.dropFirst().joined(separator: " • ").nilIfEmpty ?? "Artista", art: thumb(r["thumbnail"] ?? r))) }
                    } else if let be, pt.contains("ALBUM") {
                        if sb.insert(be.s("browseId")).inserted { s.albums.append(YtmAlbumHit(id: be.s("browseId"), title: c.first ?? "", type: c1.first ?? "Álbum", artist: c1.count > 1 ? c1[1] : "", year: c1.first { $0.range(of: "^\\d{4}$", options: .regularExpression) != nil } ?? "", art: thumb(r["thumbnail"] ?? r))) }
                    }
                    if card != nil, let cc = r["contents"] { walk(cc) }
                    return
                }
                o.values.forEach(walk)
            }
            walk(j)
            s.artists = Array(s.artists.prefix(10)); s.albums = Array(s.albums.prefix(12)); s.songs = Array(s.songs.prefix(10))
            return s
        }
    }
    static func artist(_ id: String) async -> YtmArtistPage? {
        guard let j = try? await ytm("browse", ["browseId": id]) else { return nil }
        let head = (findKey(j, "musicImmersiveHeaderRenderer") ?? findKey(j, "musicVisualHeaderRenderer") ?? findKey(j, "musicHeaderRenderer")) as? JObj ?? [:]
        let name = runsText(head["title"])
        let art = thumb(head["thumbnail"] ?? head["foregroundThumbnail"] ?? head)
        let subs = runsText(findKey(head["subscriptionButton"], "subscriberCountText"))
        struct Sh { var title: String; var more: String?; var songs: [Track]; var items: [YtmList] }
        var shelves: [Sh] = []
        func walk(_ x: Any) {
            if let a = x as? [Any] { a.forEach(walk); return }
            guard let o = x as? JObj else { return }
            if let sh = o.o("musicShelfRenderer") ?? o.o("musicCarouselShelfRenderer") {
                let hdr = sh.o("header")?.o("musicCarouselShelfBasicHeaderRenderer")
                let title = runsText(hdr?["title"] ?? sh["title"])
                let more = (findKey(sh["bottomEndpoint"] ?? hdr?["moreContentButton"], "browseEndpoint") as? JObj)?.s("browseId")
                let p = parse(["contents": sh["contents"] ?? [Any]()] as JObj)
                shelves.append(Sh(title: title, more: more, songs: p.songs.map { var t = $0.0; if t.artist.isEmpty { t.artist = name }; return t }, items: p.lists))
                return
            }
            o.values.forEach(walk)
        }
        walk(j)
        let songShelf = shelves.first { !$0.songs.isEmpty && $0.title.range(of: "(?i)v[ií]deo", options: .regularExpression) == nil } ?? shelves.first { !$0.songs.isEmpty }
        var top = songShelf?.songs ?? []
        if let m = songShelf?.more { let all = await playlist(m); if all.count > top.count { top = all } }
        func pick(_ re: String) -> [YtmList] { shelves.filter { $0.title.range(of: re, options: .regularExpression) != nil }.flatMap { $0.items } }
        return YtmArtistPage(name: name, art: art, subs: subs, top: Array(top.prefix(50)), albums: pick("(?i)[áa]lbu|album"), singles: pick("(?i)single|ep"),
                             videos: Array(shelves.filter { $0.title.range(of: "(?i)v[ií]deo", options: .regularExpression) != nil }.flatMap { $0.songs }.prefix(12)))
    }
    static func album(_ id: String) async -> YtmAlbumPage? {
        guard let j = try? await ytm("browse", ["browseId": id]) else { return nil }
        let head = (findKey(j, "musicResponsiveHeaderRenderer") ?? findKey(j, "musicDetailHeaderRenderer")) as? JObj ?? [:]
        let title = runsText(head["title"]), sub = runsText(head["subtitle"]), strap = runsText(head["straplineTextOne"])
        let artist = strap.isEmpty ? (sub.components(separatedBy: " • ").dropFirst().first ?? "") : strap
        let art = thumb(head["thumbnail"] ?? head)
        let tracks = parse(j).songs.map { (t, _) -> Track in var x = t; if x.artist.isEmpty || x.artist.first?.isNumber == true { x.artist = artist }; if !art.isEmpty { x.art = art }; x.album = title; return x }
        let year = sub.range(of: "\\b(19|20)\\d{2}\\b", options: .regularExpression).map { String(sub[$0]) } ?? ""
        return YtmAlbumPage(title: title, artist: artist, year: year, type: sub.components(separatedBy: " • ").first ?? "Álbum", art: art, tracks: tracks)
    }
    /* playlist inteira (segue as páginas) — usado no Importar */
    static func fullPlaylist(_ id: String) async -> (String, String, [Track]) {
        guard let j = try? await ytm("browse", ["browseId": browseId(id)]) else { return ("", "", []) }
        let head = (findKey(j, "musicResponsiveHeaderRenderer") ?? findKey(j, "musicDetailHeaderRenderer") ?? findKey(j, "musicEditablePlaylistDetailHeaderRenderer")) as? JObj
        var title = runsText(head?["title"]); if title.isEmpty { title = runsText(findKey(head, "title")) }
        let cover = thumb(head?["thumbnail"] ?? head)
        var songs = parse(j).songs.map { $0.0 }
        func tok(_ x: Any) -> String? { (findKey(x, "continuationCommand") as? JObj)?.s("token").nilIfEmpty ?? (findKey(x, "nextContinuationData") as? JObj)?.s("continuation").nilIfEmpty }
        var t = tok(j); var n = 0
        while let tk = t, n < 40 {
            n += 1
            guard let k = try? await ytm("browse", ["continuation": tk]) else { break }
            let more = parse(k).songs.map { $0.0 }; if more.isEmpty { break }
            songs += more; t = tok(k)
        }
        var seen = Set<String>()
        return (title, cover, songs.filter { seen.insert($0.id).inserted })
    }
    /* acha a versão do YouTube de "artista - música" (usado nas Descobertas e na importação) */
    static func find(_ artist: String, _ title: String) async -> Track? {
        let want = norm(title).split(separator: " ").prefix(3).joined(separator: " ")
        let a0 = norm(artist).split(separator: " ").first.map(String.init) ?? ""
        let r = await ytmSearch("\(artist) \(title)")
        if var hit = r.songs.first(where: { norm($0.title).contains(want) && (a0.isEmpty || norm($0.artist).contains(a0)) }) { hit.title = title; hit.artist = artist; return hit }
        if var hit = await search("\(artist) \(title)").first(where: { norm($0.title).contains(want) }) { hit.artist = artist; return hit }
        return nil
    }
}

extension String { var nilIfEmpty: String? { isEmpty ? nil : self } }
