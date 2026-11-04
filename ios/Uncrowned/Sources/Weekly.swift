import Foundation

/* ---------- Descobertas da Semana v3 (mesma lógica do Android e do PC) ----------
   ~60% artistas NOVOS parecidos com os seus · ~25% achados nos Mixes do YouTube das suas mais tocadas ·
   ~15% "lado B" de quem você já curte. Nada que você já ouviu, no máximo 2 por artista, cada música com o motivo. */
@MainActor
enum Weekly {
    struct Profile { var seeds: [(String, Double)]; var known: Set<String>; var dislikes: Set<String>; var heardIds: Set<String>; var heardT: Set<String>; var mixFrom: [String] }
    static func tKey(_ a: String, _ t: String) -> String { norm(cleanArtist(a)) + "|" + norm(t) }

    static func profile() -> Profile {
        let lib = Lib.shared
        var w: [String: Double] = [:]
        func add(_ a: String, _ v: Double) { let c = cleanArtist(a); if c.isEmpty || c.range(of: "(?i)desconhecido|unknown|youtube", options: .regularExpression) != nil { return }; w[c, default: 0] += v }
        for (a, sec, _) in lib.stats("year").artists { add(a, Double(sec) / 900) }
        for (a, sec, _) in lib.stats("month").artists { add(a, Double(sec) / 450) }
        let now = Date().timeIntervalSince1970
        for p in lib.d.plays.values { add(p.t.artist, min(p.n, 40) * pow(0.5, (now - p.last) / 86400 / 45)) }
        lib.d.favs.forEach { add($0.artist, 4) }
        lib.d.playlists.forEach { $0.tracks.forEach { add($0.artist, 1.2) } }
        for (a, v) in lib.d.taste { add(a, v * 0.6) }
        let list = w.sorted { $0.value > $1.value }
        var heardIds = Set(lib.d.heard), heardT = Set<String>()
        for p in lib.d.plays.values { heardIds.insert(p.t.key); heardT.insert(tKey(p.t.artist, p.t.title)) }
        for t in lib.d.history + lib.d.favs { heardIds.insert(t.key); heardT.insert(tKey(t.artist, t.title)) }
        let mixFrom = lib.d.plays.values.filter { $0.t.src == "yt" }.sorted { $0.n > $1.n }.prefix(15).map { $0.t.id }
        return Profile(seeds: list.filter { $0.value > 0.8 }.prefix(12).map { ($0.key, $0.value) }, known: Set(list.filter { $0.value >= 1.5 }.map { norm($0.key) }),
                       dislikes: Set(lib.d.taste.filter { $0.value <= -2 }.map { norm($0.key) }), heardIds: heardIds, heardT: heardT, mixFrom: mixFrom)
    }

    static func get(force: Bool = false) async -> WeeklyCache {
        let w = weekKey(), lib = Lib.shared
        if !force, let c = lib.d.weekly, c.w == w, c.tracks.count >= 12 || Date().timeIntervalSince1970 - c.at < 3600 { return c }
        let salt = force ? Int.random(in: 0..<10000) : 0
        var r = await build(profile(), seed: w + salt)
        if r.tracks.count < 10 {
            // quase sem histórico: o que está em alta nos estilos escolhidos
            let gs = lib.d.genres.isEmpty ? ["Pop", "Funk", "Trap"] : Array(lib.d.genres.prefix(3))
            for g in gs { for var t in await YT.ytmSearch("\(g) em alta").songs.prefix(8) where !r.tracks.contains(where: { $0.id == t.id }) { t.why = "Em alta em \(g)"; r.tracks.append(t) } }
        }
        let c = WeeklyCache(w: w, at: Date().timeIntervalSince1970, tracks: Array(r.tracks.prefix(30)), artists: r.artists)
        if !c.tracks.isEmpty { lib.d.weekly = c; lib.save() }
        return c
    }

    private final class Cand { let a: Deezer.Artist; var score: Double = 0; var hits = 0; var because: [String] = []; init(_ a: Deezer.Artist) { self.a = a } }
    struct Rng { var s: UInt64; mutating func next() -> Double { s = s &* 6364136223846793005 &+ 1442695040888963407; return Double(s >> 11) / Double(1 << 53) } }

    static func build(_ p: Profile, seed: Int) async -> (tracks: [Track], artists: [WeeklyArtist]) {
        if p.seeds.isEmpty { return ([], []) }
        var rng = Rng(s: UInt64(seed) &+ 99)
        func fresh(_ t: Track) -> Bool { !p.heardIds.contains(t.key) && !p.heardT.contains(tKey(t.artist, t.title)) && !p.dislikes.contains(norm(cleanArtist(t.artist))) }
        // 1) artistas parecidos
        let rel = await pool(p.seeds, 4) { await Deezer.related($0.0) }
        var cand: [Int: Cand] = [:]
        for (si, list) in rel.enumerated() {
            for (r, a) in (list ?? []).enumerated() {
                let n = norm(a.name); if p.known.contains(n) || p.dislikes.contains(n) { continue }
                let c = cand[a.id] ?? Cand(a); cand[a.id] = c
                c.score += p.seeds[si].1 * (1 - Double(r) / 22); c.hits += 1
                if c.because.count < 2 && !c.because.contains(p.seeds[si].0) { c.because.append(p.seeds[si].0) }
            }
        }
        let artists = cand.values.map { c -> (Cand, Double) in
            let pop = c.a.fans > 8_000_000 ? 0.8 : (c.a.fans < 3000 ? 0.75 : 1)
            return (c, c.score * (1 + 0.45 * Double(c.hits - 1)) * pop * (0.8 + rng.next() * 0.4))
        }.sorted { $0.1 > $1.1 }.prefix(18).map { $0.0 }
        var wanted: [(Deezer.Song, String, Bool)] = []
        let tops = await pool(Array(artists), 4) { await Deezer.top($0.a.id, 6) }
        for (i, c) in artists.enumerated() {
            var l = (tops[i] ?? []).filter { !p.heardT.contains(tKey($0.artist, $0.title)) }
            l.sort { _, _ in rng.next() < 0.5 }
            for s in l.prefix(i < 8 ? 2 : 1) { wanted.append((s, "Porque você ouve \(c.because.first ?? "")", true)) }
        }
        // lado B de quem você já curte
        let deep = await pool(Array(p.seeds.prefix(5)), 3) { s -> (String, [Deezer.Song])? in
            guard let a = await Deezer.artist(s.0) else { return nil }; return (s.0, await Deezer.top(a.id, 15)) }
        for d in deep.compactMap({ $0 }) {
            for s in d.1.dropFirst(3).filter({ !p.heardT.contains(tKey($0.artist, $0.title)) }).prefix(2) { wanted.append((s, "Mais de \(d.0) que você ainda não ouviu", false)) }
        }
        let resolved = await pool(wanted, 5) { w -> Track? in
            guard var t = await YT.find(w.0.artist, w.0.title) else { return nil }
            if !w.0.art.isEmpty { t.art = w.0.art }; t.why = w.1; t.genre = w.2 ? "new" : "deep"; return t
        }.compactMap { $0 }.filter(fresh)
        // Mix do YouTube das suas mais tocadas (só artistas novos)
        var mixIds = p.mixFrom; mixIds.sort { _, _ in rng.next() < 0.5 }
        let mixes = await pool(Array(mixIds.prefix(5)), 2) { await YT.mix($0) }
        var fromMix = mixes.compactMap { $0 }.flatMap { $0.dropFirst().prefix(25) }.filter { fresh($0) && !p.known.contains(norm(cleanArtist($0.artist))) }
        fromMix.sort { _, _ in rng.next() < 0.5 }
        fromMix = fromMix.map { var t = $0; t.why = "Achado no Mix do YouTube das suas mais tocadas"; return t }

        let nw = resolved.filter { $0.genre == "new" }, dp = resolved.filter { $0.genre == "deep" }
        var out: [Track] = [], per: [String: Int] = [:], seen = Set<String>()
        func push(_ x: Track) -> Bool {
            if out.count >= 30 { return false }
            let ak = norm(cleanArtist(x.artist)), k = tKey(x.artist, x.title)
            if seen.contains(x.id) || seen.contains(k) || per[ak, default: 0] >= 2 { return false }
            seen.insert(x.id); seen.insert(k); per[ak, default: 0] += 1
            var t = x; t.genre = ""; out.append(t); return true
        }
        var a = 0, b = 0, c = 0, nNew = 0, nMix = 0
        for round in 0..<200 {
            if out.count >= 30 { break }
            var moved = false
            for _ in 0..<2 { while a < nw.count && nNew < 18 { moved = true; let x = nw[a]; a += 1; if push(x) { nNew += 1; break } } }
            while b < fromMix.count && nMix < 8 { moved = true; let x = fromMix[b]; b += 1; if push(x) { nMix += 1; break } }
            if round % 3 == 2 { while c < dp.count { moved = true; let x = dp[c]; c += 1; if push(x) { break } } }
            if !moved { break }
        }
        for x in Array(nw.dropFirst(a)) + Array(fromMix.dropFirst(b)) + Array(dp.dropFirst(c)) { _ = push(x) }
        return (out, artists.prefix(12).map { WeeklyArtist(name: $0.a.name, pic: $0.a.pic, because: $0.because.first ?? "") })
    }
}
