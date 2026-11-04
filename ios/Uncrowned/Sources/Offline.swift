import Foundation
import AVFoundation
import UIKit

/* ---------- músicas no iPhone ----------
   O iPhone não deixa um app ler as músicas soltas do aparelho. Então:
   - a pasta "Uncrowned" aparece no app Arquivos (e no PC pelo iTunes/Finder): o que você jogar lá aparece aqui
   - "Importar dos Arquivos" copia músicas de qualquer lugar (iCloud Drive, downloads, WhatsApp...)
   - faixas liberadas pelo artista (Audius / Archive) podem ser guardadas no app (opção secreta) */
@MainActor
final class Offline: ObservableObject {
    static let shared = Offline()
    @Published var songs: [Track] = []
    @Published var scanning = false
    static let exts: Set<String> = ["mp3", "m4a", "aac", "flac", "wav", "aiff", "aif", "caf", "alac", "ogg", "opus"]
    static var musicDir: URL { let u = Lib.docs.appendingPathComponent("Músicas", isDirectory: true); try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true); return u }
    static var savedDir: URL { let u = Lib.docs.appendingPathComponent("Guardadas", isDirectory: true); try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true); return u }
    static var artDir: URL { let u = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("arts"); try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true); return u }

    func scan() async {
        scanning = true; defer { scanning = false }
        var files: [URL] = []
        if let e = FileManager.default.enumerator(at: Lib.docs, includingPropertiesForKeys: [.isRegularFileKey], options: [.skipsHiddenFiles]) {
            for case let u as URL in e where Offline.exts.contains(u.pathExtension.lowercased()) { files.append(u) }
        }
        var out: [Track] = []
        for f in files { out.append(await Offline.read(f)) }
        songs = out.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
    }
    static func read(_ f: URL) async -> Track {
        let asset = AVURLAsset(url: f)
        var title = f.deletingPathExtension().lastPathComponent, artist = "Artista desconhecido", album = "", art = ""
        var dur = 0
        if let d = try? await asset.load(.duration), d.seconds.isFinite { dur = Int(d.seconds * 1000) }
        if let md = try? await asset.load(.commonMetadata) {
            for it in md {
                switch it.commonKey {
                case .commonKeyTitle?: if let v = try? await it.load(.stringValue), !v.isEmpty { title = v }
                case .commonKeyArtist?: if let v = try? await it.load(.stringValue), !v.isEmpty { artist = v }
                case .commonKeyAlbumName?: if let v = try? await it.load(.stringValue) { album = v }
                case .commonKeyArtwork?:
                    if let data = try? await it.load(.dataValue) {
                        let a = artDir.appendingPathComponent(stableHash(f.path) + ".jpg")
                        if !FileManager.default.fileExists(atPath: a.path), let img = UIImage(data: data), let jpg = img.jpegData(compressionQuality: 0.8) { try? jpg.write(to: a) }
                        art = a.path
                    }
                default: break
                }
            }
        }
        let rel = f.path.replacingOccurrences(of: Lib.docs.path, with: "")
        return Track(src: "local", id: rel, title: title, artist: artist, art: art, duration: dur, stream: f.path, album: album)
    }
    /* copia arquivos escolhidos no app Arquivos pra pasta do app */
    func importFiles(_ urls: [URL]) async -> Int {
        var n = 0
        for u in urls {
            let ok = u.startAccessingSecurityScopedResource(); defer { if ok { u.stopAccessingSecurityScopedResource() } }
            var dest = Offline.musicDir.appendingPathComponent(u.lastPathComponent); var i = 2
            while FileManager.default.fileExists(atPath: dest.path) { dest = Offline.musicDir.appendingPathComponent("\(u.deletingPathExtension().lastPathComponent) (\(i)).\(u.pathExtension)"); i += 1 }
            if (try? FileManager.default.copyItem(at: u, to: dest)) != nil { n += 1 }
        }
        await scan(); return n
    }
    func delete(_ t: Track) { try? FileManager.default.removeItem(atPath: t.stream); songs.removeAll { $0.key == t.key } }

    /* guardar no app só o que o artista liberou (Audius / Archive) */
    static func savedName(_ t: Track) -> String { stableHash(t.key) }
    static func file(for t: Track) -> URL? {
        guard t.src == "au" || t.src == "ia" else { return nil }
        let base = savedDir.appendingPathComponent(savedName(t))
        for e in ["mp3", "m4a", "flac", "wav", "ogg"] { let u = base.appendingPathExtension(e); if FileManager.default.fileExists(atPath: u.path) { return u } }
        return nil
    }
    func save(_ t: Track) async -> Bool {
        guard !t.download.isEmpty, t.download.hasPrefix("https://"), let data = try? await Net.data(t.download), data.count > 60_000 else { return false }
        let h = [UInt8](data.prefix(12)); var ext = ""
        if h.starts(with: [0x49, 0x44, 0x33]) || (h[0] == 0xFF && (h[1] & 0xE0) == 0xE0) { ext = "mp3" }
        else if Array(h[4..<8]) == Array("ftyp".utf8) { ext = "m4a" } else if h.starts(with: Array("fLaC".utf8)) { ext = "flac" }
        else if h.starts(with: Array("RIFF".utf8)) { ext = "wav" } else if h.starts(with: Array("OggS".utf8)) { ext = "ogg" }
        guard !ext.isEmpty else { return false }      // não é áudio: descarta
        let u = Offline.savedDir.appendingPathComponent(Offline.savedName(t)).appendingPathExtension(ext)
        do { try data.write(to: u); return true } catch { return false }
    }

    /* rádio offline: parecidas das suas músicas do iPhone (artista, álbum e o que você mais ouve) */
    static func localRadio(seed: Track?) -> [Track] {
        let lib = Lib.shared
        let pool = shared.songs.filter { $0.key != seed?.key }
        let sa = norm(seed?.artist ?? ""), sal = norm(seed?.album ?? "")
        let scored = pool.map { t -> (Track, Double) in
            var s = Double.random(in: 0.3...1.5)
            if !sa.isEmpty && norm(t.artist) == sa { s += 2.5 }
            if !sal.isEmpty && norm(t.album) == sal { s += 0.8 }
            s += min(1.5, (lib.d.plays[t.key]?.n ?? 0) * 0.15) + (lib.isFav(t) ? 1 : 0)
            return (t, s)
        }.sorted { $0.1 > $1.1 }
        var per: [String: Int] = [:]
        return scored.map { $0.0 }.filter { per[$0.artist, default: 0] += 1; return per[$0.artist]! <= 3 }.prefix(30).shuffled()
    }
}

/* hash que não muda entre aberturas do app (o hashValue do Swift muda) */
func stableHash(_ s: String) -> String {
    var h: UInt64 = 0xcbf29ce484222325
    for b in s.utf8 { h ^= UInt64(b); h = h &* 0x100000001b3 }
    return String(h, radix: 16)
}
