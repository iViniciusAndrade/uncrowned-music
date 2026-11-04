import Foundation
import SwiftUI

struct Settings: Codable {
    var skin = "edgy"
    var speed: Double = 1
    var pitchWithSpeed = true       // tom muda junto (slowed / nightcore)
    var autoNext = true
    var radioWhenEnds = true        // fila acabou: continua com parecidas
    var showVideo = false
    var scFirst = false             // tocar pelo SoundCloud sempre que der
    var bgSwitch = true             // tela apagou com YouTube tocando: continua pelo SoundCloud
    var lyricsSynced = false
    var country = "BR"
    var secret = false              // opções escondidas (5 toques no tema)
    var eco = false
    var nag = true                  // notificação zoeira chamando pra ouvir
    var artShape = "rounded"
}

struct PlayCount: Codable { var t: Track; var n: Double; var last: Double }
struct Day: Codable { var t: Int = 0; var a: [String: Int] = [:]; var k: [String: Int] = [:] }
struct WeeklyCache: Codable { var w: Int; var at: Double; var tracks: [Track]; var artists: [WeeklyArtist] }
struct WeeklyArtist: Codable, Hashable, Identifiable { var name: String; var pic: String; var because: String; var id: String { name } }

struct LibData: Codable {
    var settings = Settings()
    var favs: [Track] = []
    var history: [Track] = []
    var playlists: [Playlist] = []
    var plays: [String: PlayCount] = [:]
    var taste: [String: Double] = [:]           // artista -> pontos (pula = negativo)
    var genres: [String] = []                   // estilos escolhidos no começo
    var heard: [String] = []
    var days: [String: Day] = [:]
    var tracks: [String: Track] = [:]           // faixas que aparecem nas estatísticas
    var recent: [String] = []
    var weekly: WeeklyCache? = nil
    var radioRecent: [Station] = []
    var tasteWeek = 0
}

/* Tudo que o app guarda fica num arquivo só, gravado em segundo plano */
@MainActor
final class Lib: ObservableObject {
    static let shared = Lib()
    @Published var d = LibData()
    private var saveTask: Task<Void, Never>?
    private let file: URL = {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("uncrowned.json")
    }()
    static var docs: URL { FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0] }
    static var covers: URL { let u = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("plcovers"); try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true); return u }

    init() {
        if let data = try? Data(contentsOf: file), let v = try? JSONDecoder().decode(LibData.self, from: data) { d = v }
    }
    func save() {
        saveTask?.cancel()
        let snapshot = d
        let f = file
        saveTask = Task.detached(priority: .utility) {
            try? await Task.sleep(nanoseconds: 800_000_000)
            if Task.isCancelled { return }
            if let data = try? JSONEncoder().encode(snapshot) { try? data.write(to: f, options: .atomic) }
        }
    }
    func saveNow() { if let data = try? JSONEncoder().encode(d) { try? data.write(to: file, options: .atomic) } }
    var s: Settings { get { d.settings } set { d.settings = newValue; save() } }

    // favoritos
    func isFav(_ t: Track) -> Bool { d.favs.contains { $0.key == t.key } }
    func toggleFav(_ t: Track) {
        if let i = d.favs.firstIndex(where: { $0.key == t.key }) { d.favs.remove(at: i) }
        else { d.favs.insert(t, at: 0); bumpTaste(t.artist, 3) }
        save()
    }
    // histórico, gosto e contagem
    func onPlay(_ t: Track) {
        guard t.src != "radio" else { return }
        weeklyDecay()
        d.history.removeAll { $0.key == t.key }; d.history.insert(t, at: 0); if d.history.count > 120 { d.history.removeLast(d.history.count - 120) }
        if !d.heard.contains(t.key) { d.heard.append(t.key); if d.heard.count > 3000 { d.heard.removeFirst(d.heard.count - 3000) } }
        var p = d.plays[t.key] ?? PlayCount(t: t, n: 0, last: 0); p.n += 1; p.last = Date().timeIntervalSince1970; p.t = t; d.plays[t.key] = p
        if d.plays.count > 700 { for k in d.plays.sorted(by: { $0.value.n < $1.value.n }).prefix(100).map({ $0.key }) { d.plays.removeValue(forKey: k) } }
        bumpTaste(t.artist, 1)
        save()
    }
    func onSkip(_ t: Track) { bumpTaste(t.artist, -1); save() }
    func onFinished(_ t: Track) { bumpTaste(t.artist, 1); if var p = d.plays[t.key] { p.n += 0.5; d.plays[t.key] = p }; save() }
    func bumpTaste(_ artist: String, _ v: Double) { let a = cleanArtist(artist); guard !a.isEmpty else { return }; d.taste[a, default: 0] += v }
    private func weeklyDecay() { let w = weekKey(); if d.tasteWeek != 0 && d.tasteWeek != w { for k in d.taste.keys { d.taste[k]! *= 0.9 } }; d.tasteWeek = w }

    // estatísticas: segundos ouvidos por dia, artista e faixa
    func tick(_ t: Track, _ sec: Int) {
        let k = dayKey(Date())
        var day = d.days[k] ?? Day(); day.t += sec
        if t.src != "radio" { if !t.artist.isEmpty { day.a[t.artist, default: 0] += sec }; day.k[t.key, default: 0] += sec; d.tracks[t.key] = t }
        d.days[k] = day
        if d.days.count > 430 { for x in d.days.keys.sorted().prefix(d.days.count - 430) { d.days.removeValue(forKey: x) } }
    }
    struct Stats { var total: Int; var songs: [(Track, Int)]; var artists: [(String, Int, String)] }
    func stats(_ period: String) -> Stats {
        let cal = Calendar(identifier: .gregorian); let now = Date()
        var from = now
        switch period {
        case "week": from = cal.date(byAdding: .day, value: -((cal.component(.weekday, from: now) + 5) % 7), to: now) ?? now
        case "month": from = cal.date(from: cal.dateComponents([.year, .month], from: now)) ?? now
        case "year": from = cal.date(from: cal.dateComponents([.year], from: now)) ?? now
        default: from = Date(timeIntervalSince1970: 0)
        }
        let fk = dayKey(from)
        var total = 0; var a: [String: Int] = [:]; var k: [String: Int] = [:]
        for (dk, day) in d.days where dk >= fk { total += day.t; for (x, v) in day.a { a[x, default: 0] += v }; for (x, v) in day.k { k[x, default: 0] += v } }
        let songs = k.compactMap { e -> (Track, Int)? in d.tracks[e.key].map { ($0, e.value) } }.sorted { $0.1 > $1.1 }
        let artists = a.sorted { $0.value > $1.value }.map { e in (e.key, e.value, songs.first { $0.0.artist == e.key }?.0.art ?? "") }
        return Stats(total: total, songs: songs, artists: artists)
    }

    // playlists
    func playlist(_ name: String) -> Playlist? { d.playlists.first { $0.name == name } }
    @discardableResult func createPlaylist(_ name: String, tracks: [Track] = [], cover: String = "", from: String = "") -> String {
        var n = name.trimmingCharacters(in: .whitespaces); if n.isEmpty { n = "Playlist" }
        let base = n; var i = 2; while d.playlists.contains(where: { $0.name == n }) { n = "\(base) (\(i))"; i += 1 }
        d.playlists.insert(Playlist(name: n, tracks: tracks, cover: cover, from: from), at: 0); save(); return n
    }
    func add(_ t: Track, to name: String) {
        guard let i = d.playlists.firstIndex(where: { $0.name == name }) else { return }
        if !d.playlists[i].tracks.contains(where: { $0.key == t.key }) { d.playlists[i].tracks.append(t); bumpTaste(t.artist, 2) }
        save()
    }
    func remove(_ t: Track, from name: String) { if let i = d.playlists.firstIndex(where: { $0.name == name }) { d.playlists[i].tracks.removeAll { $0.key == t.key }; save() } }
    func deletePlaylist(_ name: String) {
        if let p = playlist(name), !p.look.img.isEmpty { try? FileManager.default.removeItem(atPath: p.look.img) }
        d.playlists.removeAll { $0.name == name }; save()
    }
    func rename(_ from: String, _ to: String) -> Bool {
        let t = to.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty, !d.playlists.contains(where: { $0.name == t }), let i = d.playlists.firstIndex(where: { $0.name == from }) else { return false }
        d.playlists[i].name = t; save(); return true
    }
    func setLook(_ name: String, _ f: (inout Look) -> Void) { if let i = d.playlists.firstIndex(where: { $0.name == name }) { f(&d.playlists[i].look); save() } }
    func addRecent(_ q: String) { guard q.count >= 2, !q.hasPrefix("http") else { return }; d.recent.removeAll { $0.lowercased() == q.lowercased() }; d.recent.insert(q, at: 0); if d.recent.count > 12 { d.recent.removeLast() }; save() }
}

func weekKey(_ date: Date = Date()) -> Int { let c = Calendar(identifier: .iso8601); return c.component(.yearForWeekOfYear, from: date) * 100 + c.component(.weekOfYear, from: date) }
func dayKey(_ date: Date) -> String { let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX"); return f.string(from: date) }
