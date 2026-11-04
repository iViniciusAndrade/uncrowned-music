import Foundation

/* Uma faixa de qualquer lugar.
   src: "yt" YouTube, "sc" SoundCloud (id = artista/faixa), "au" Audius, "ia" Internet Archive, "radio" rádio ao vivo, "local" arquivo do iPhone */
struct Track: Codable, Hashable, Identifiable {
    var src: String
    var id: String
    var title: String
    var artist: String
    var art: String = ""
    var duration: Int = 0          // ms
    var stream: String = ""        // endereço direto (Audius, Archive, rádio, arquivo)
    var download: String = ""      // link liberado pelo artista
    var genre: String = ""
    var album: String = ""
    var why: String = ""           // motivo nas Descobertas da Semana

    var key: String { "\(src):\(id)" }
    var artURL: URL? {
        if art.isEmpty { return nil }
        if art.hasPrefix("/") { return URL(fileURLWithPath: art) }
        return URL(string: art)
    }
    var srcName: String { ["yt": "YouTube", "sc": "SoundCloud", "au": "Audius", "ia": "Archive", "radio": "Rádio ao vivo", "local": "Offline"][src] ?? src }
    var link: String {
        switch src {
        case "yt": return "https://youtu.be/\(id)"
        case "sc": return "https://soundcloud.com/\(id)"
        default: return stream
        }
    }
    static func yt(_ id: String, _ title: String, _ artist: String, art: String? = nil, duration: Int = 0) -> Track {
        Track(src: "yt", id: id, title: title, artist: artist, art: art ?? "https://i.ytimg.com/vi/\(id)/hqdefault.jpg", duration: duration)
    }
}

struct YtmArtistHit: Codable, Hashable, Identifiable { var id: String; var name: String; var sub: String; var art: String }
struct YtmAlbumHit: Codable, Hashable, Identifiable { var id: String; var title: String; var type: String; var artist: String; var year: String; var art: String }
struct YtmList: Codable, Hashable, Identifiable { var id: String; var title: String; var sub: String; var art: String; var shelf: String }
struct YtmArtistPage { var name: String; var art: String; var subs: String; var top: [Track]; var albums: [YtmList]; var singles: [YtmList]; var videos: [Track] }
struct YtmAlbumPage { var title: String; var artist: String; var year: String; var type: String; var art: String; var tracks: [Track] }

/* visual de uma playlist (igual Android e PC) */
struct Look: Codable, Hashable {
    var img: String = ""       // arquivo da foto no iPhone
    var color: String = ""     // "#rrggbb" ou vazio = cor do tema
    var emoji: String = ""
    var pattern: String = ""
    var font: String = ""
    var shape: String = "rounded"
    var desc: String = ""
    var mosaic: Bool = false
}

struct Playlist: Codable, Hashable, Identifiable {
    var name: String
    var tracks: [Track]
    var cover: String = ""     // capa que veio na importação
    var from: String = ""
    var look: Look = Look()
    var created: Double = Date().timeIntervalSince1970
    var id: String { name }
}

struct Station: Codable, Hashable, Identifiable {
    var id: String; var name: String; var stream: String; var art: String; var info: String; var votes: Int = 0
    var track: Track { Track(src: "radio", id: id, title: name, artist: info, art: art, stream: stream) }
}
