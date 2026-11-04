import SwiftUI
import PhotosUI
import UIKit

/* ---------- playlists personalizadas (igual Android e PC): foto, cor, enfeite, estampa, fonte, formato e descrição ---------- */
enum PlStyle {
    static let colors = ["#ff1f3d", "#ff5fa8", "#c77dff", "#7b5cff", "#2b6bff", "#25c4f4", "#2ec4b6", "#39d353", "#c6f432", "#ffd23f", "#ff9f1c", "#ff6b35", "#a8763e", "#8e8e93", "#f2f2f2", "#111111"]
    static let emojis = ["✨", "💖", "🖤", "💀", "🔥", "🌙", "⭐", "🌸", "🦋", "🍒", "🎀", "👑", "⚡", "🌈", "💿", "🎧", "🎸", "🥀", "🌹", "🍓", "🐱", "🐮", "👻", "🕷️", "🗡️", "⛓️", "🩸", "🌊", "🌴", "☁️", "🍀", "🍄", "💎", "🪐", "🌻", "🍑", "🧸", "😈", "🤍", "💜"]
    static let patterns = [("", "Nenhuma"), ("sparkle", "Brilhinhos"), ("hearts", "Corações"), ("stars", "Estrelas"), ("flowers", "Florzinhas"), ("skulls", "Caveirinhas"), ("dots", "Bolinhas"), ("stripes", "Listras"), ("checker", "Xadrez"), ("waves", "Ondas"), ("glitter", "Glitter")]
    static let fonts = [("", "Padrão"), ("black", "Grossona"), ("serif", "Elegante"), ("hand", "Escrita à mão"), ("mono", "Máquina"), ("retro", "Retrô"), ("gothic", "Gótica")]
    static let shapes = [("rounded", "Arredondada"), ("square", "Quadrada"), ("circle", "Círculo"), ("vinyl", "Vinil"), ("polaroid", "Polaroid"), ("tilt", "Torta")]
    static func font(_ f: String, size: CGFloat) -> Font {
        switch f {
        case "black": return .system(size: size, weight: .black)
        case "serif": return .system(size: size, weight: .bold, design: .serif).italic()
        case "hand": return .custom("Noteworthy-Bold", size: size)
        case "mono", "retro": return .system(size: size, weight: .black, design: .monospaced)
        case "gothic": return .custom("Didot-Bold", size: size)
        default: return .system(size: size, weight: .bold)
        }
    }
}

/* capa: foto escolhida > capa que veio na importação > mosaico das 4 primeiras capas */
struct PlCover: View {
    let p: Playlist; let size: CGFloat
    var body: some View {
        let L = p.look
        let col = L.color.isEmpty ? Color.accentColor : Color(hex: L.color)
        let arts = Array(NSOrderedSet(array: p.tracks.map { $0.art }.filter { !$0.isEmpty }).array.compactMap { $0 as? String }.prefix(4))
        let img = !L.img.isEmpty ? L.img : (!L.mosaic ? p.cover : "")
        let r: CGFloat = L.shape == "square" ? 3 : (L.shape == "circle" || L.shape == "vinyl") ? size / 2 : size * 0.09
        let inner = Group {
            if !img.isEmpty { Art(img, radius: r) }
            else if arts.count >= 4 {
                VStack(spacing: 0) { HStack(spacing: 0) { Art(arts[0], radius: 0); Art(arts[1], radius: 0) }; HStack(spacing: 0) { Art(arts[2], radius: 0); Art(arts[3], radius: 0) } }
                    .clipShape(RoundedRectangle(cornerRadius: r, style: .continuous))
            } else if let a = arts.first { Art(a, radius: r) }
            else {
                ZStack { RoundedRectangle(cornerRadius: r, style: .continuous).fill(col.opacity(0.45))
                    if L.emoji.isEmpty { Image(systemName: "music.note.list").font(.system(size: size * 0.32)).foregroundStyle(.white.opacity(0.85)) } else { Text(L.emoji).font(.system(size: size * 0.42)) } }
            }
        }
        ZStack(alignment: .bottomTrailing) {
            Group {
                if L.shape == "polaroid" {
                    inner.frame(width: size * 0.88, height: size * 0.88).padding(.horizontal, size * 0.06).padding(.top, size * 0.06).padding(.bottom, size * 0.2)
                        .background(Color(hex: "#fbfaf6"), in: RoundedRectangle(cornerRadius: 4)).frame(width: size, height: size * 1.14).rotationEffect(.degrees(-3))
                } else if L.shape == "vinyl" {
                    ZStack { Circle().fill(Color(hex: "#0b0b0b")); inner.frame(width: size * 0.86, height: size * 0.86); Circle().fill(Color(hex: "#0b0b0b")).frame(width: size * 0.14).overlay(Circle().stroke(.white.opacity(0.15), lineWidth: 2)) }
                        .frame(width: size, height: size)
                } else {
                    inner.frame(width: size, height: size).rotationEffect(.degrees(L.shape == "tilt" ? -6 : 0))
                }
            }.shadow(color: .black.opacity(0.4), radius: size * 0.08, y: size * 0.04)
            if !L.emoji.isEmpty && (!img.isEmpty || !arts.isEmpty) { Text(L.emoji).font(.system(size: max(12, min(44, size * 0.22)))).rotationEffect(.degrees(12)).offset(x: size * 0.08, y: size * 0.08) }
        }
    }
}
struct PlThumb: View { let p: Playlist; var body: some View { GeometryReader { g in PlCover(p: { var x = p; x.look.shape = ["circle", "vinyl"].contains(p.look.shape) ? "circle" : "rounded"; return x }(), size: min(g.size.width, g.size.height)) } } }

/* estampa desenhada por cima da cor */
struct PatternLayer: View {
    let pattern: String; let tint: Color
    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            switch pattern {
            case "dots":
                var y: CGFloat = 11; while y < h { var x: CGFloat = 11; while x < w { ctx.fill(Path(ellipseIn: CGRect(x: x - 2.5, y: y - 2.5, width: 5, height: 5)), with: .color(.white.opacity(0.4))); x += 22 }; y += 22 }
            case "stripes":
                var x: CGFloat = -h; while x < w { var p = Path(); p.move(to: CGPoint(x: x, y: h)); p.addLine(to: CGPoint(x: x + h, y: 0)); ctx.stroke(p, with: .color(.white.opacity(0.28)), lineWidth: 9); x += 26 }
            case "checker":
                let s: CGFloat = 18; var y: CGFloat = 0; var row = 0
                while y < h { var x: CGFloat = row % 2 == 0 ? 0 : s; while x < w { ctx.fill(Path(CGRect(x: x, y: y, width: s, height: s)), with: .color(.white.opacity(0.13))); x += s * 2 }; y += s; row += 1 }
            case "waves":
                var y: CGFloat = 24; while y < h { var p = Path(); p.move(to: CGPoint(x: 0, y: y)); var x: CGFloat = 0
                    while x < w { p.addQuadCurve(to: CGPoint(x: x + 30, y: y), control: CGPoint(x: x + 15, y: y - 12)); p.addQuadCurve(to: CGPoint(x: x + 60, y: y), control: CGPoint(x: x + 45, y: y + 12)); x += 60 }
                    ctx.stroke(p, with: .color(.white.opacity(0.3)), lineWidth: 2); y += 24 }
            case "glitter":
                for i in 0..<160 { let fx = CGFloat((i * 7919) % 1000) / 1000, fy = CGFloat((i * 104729) % 1000) / 1000, r = CGFloat(1 + (i % 3))
                    ctx.fill(Path(ellipseIn: CGRect(x: fx * w, y: fy * h, width: r, height: r)), with: .color((i % 2 == 0 ? Color.white : tint).opacity(0.6))) }
            case "": break
            default:
                let glyphs: [String] = ["sparkle": ["✨", "✦", "✧"], "hearts": ["💖", "🤍"], "stars": ["★", "☆"], "flowers": ["🌸", "🌼"], "skulls": ["💀", "✝"]][pattern] ?? []
                guard !glyphs.isEmpty else { return }
                var y: CGFloat = 26; var row = 0
                while y < h + 40 { var x: CGFloat = row % 2 == 0 ? 8 : 30; var i = row
                    while x < w { ctx.draw(Text(glyphs[i % glyphs.count]).font(.system(size: i % 2 == 0 ? 20 : 13)).foregroundColor(.white.opacity(0.5)), at: CGPoint(x: x, y: y)); x += 46; i += 1 }
                    y += 34; row += 1 }
            }
        }
        .allowsHitTesting(false)
    }
}

/* topo da playlist */
struct PlHero: View {
    let p: Playlist; var sub: String; var onEdit: (() -> Void)? = nil
    var body: some View {
        let L = p.look
        let col = L.color.isEmpty ? Color.accentColor : Color(hex: L.color)
        ZStack(alignment: .topTrailing) {
            LinearGradient(colors: [col.opacity(0.95), col.opacity(0.5), Color.black.opacity(0.9)], startPoint: .topLeading, endPoint: .bottomTrailing)
            PatternLayer(pattern: L.pattern, tint: col)
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .bottom, spacing: 20) {
                    PlCover(p: p, size: 128)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("PLAYLIST").font(.system(size: 10, weight: .heavy)).kerning(1.5).foregroundStyle(.white.opacity(0.8))
                        Text(L.font == "retro" ? p.name.uppercased() : p.name).font(PlStyle.font(L.font, size: 25)).kerning(L.font == "retro" ? 2 : 0).foregroundStyle(.white).lineLimit(3).minimumScaleFactor(0.6)
                        Text(sub).font(.caption).foregroundStyle(.white.opacity(0.8))
                    }
                    Spacer(minLength: 0)
                }
                if !L.desc.isEmpty { Text(L.desc).font(.subheadline).foregroundStyle(.white.opacity(0.9)) }
            }.padding(18).padding(.top, onEdit == nil ? 0 : 22)
            if let onEdit { Button(action: onEdit) { Text("✨ Personalizar").font(.caption.bold()).padding(.horizontal, 12).padding(.vertical, 7).background(.black.opacity(0.35), in: Capsule()).foregroundStyle(.white) }.padding(10) }
        }
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .padding(.horizontal, 16)
    }
}

/* tela de personalizar (muda tudo na hora) */
struct PlEditSheet: View {
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var player: Player
    @Environment(\.dismiss) var dismiss
    @Binding var name: String
    @State private var newName = ""
    @State private var desc = ""
    @State private var emo = ""
    @State private var photo: PhotosPickerItem? = nil
    var p: Playlist? { lib.playlist(name) }
    var body: some View {
        NavigationStack {
            ScrollView {
                if let p {
                    VStack(alignment: .leading, spacing: 4) {
                        PlHero(p: { var x = p; x.look.desc = desc; return x }(), sub: "Prévia").padding(.vertical, 8)
                        T("Nome")
                        HStack { TextField("Nome", text: $newName).textFieldStyle(.roundedBorder)
                            Button("Renomear") { if lib.rename(name, newName) { name = newName.trimmingCharacters(in: .whitespaces) } else { player.say("Já tem uma playlist com esse nome") } }.disabled(newName.trimmingCharacters(in: .whitespaces).isEmpty || newName == name) }.padding(.horizontal, 16)
                        T("Foto da capa")
                        HStack(spacing: 8) {
                            PhotosPicker(selection: $photo, matching: .images) { Label("Escolher foto", systemImage: "photo.badge.plus") }.buttonStyle(.borderedProminent)
                            Button("Mosaico") { dropImg(); lib.setLook(name) { $0.img = ""; $0.mosaic = true } }.buttonStyle(.bordered)
                            if !p.look.img.isEmpty || (!p.cover.isEmpty && !p.look.mosaic) { Button("Tirar foto") { dropImg(); lib.setLook(name) { $0.img = ""; $0.mosaic = !p.cover.isEmpty } }.buttonStyle(.bordered) }
                        }.padding(.horizontal, 16)
                        T("Cor")
                        LazyVGrid(columns: Array(repeating: GridItem(.fixed(36)), count: 8), spacing: 10) {
                            ForEach(PlStyle.colors, id: \.self) { c in Circle().fill(Color(hex: c)).frame(width: 32, height: 32).overlay(Circle().stroke(p.look.color == c ? Color.primary : .white.opacity(0.2), lineWidth: p.look.color == c ? 3 : 1)).onTapGesture { lib.setLook(name) { $0.color = c } } }
                        }.padding(.horizontal, 16)
                        Button("Cor do tema") { lib.setLook(name) { $0.color = "" } }.font(.caption).padding(.horizontal, 16)
                        T("Enfeite")
                        LazyVGrid(columns: Array(repeating: GridItem(.fixed(40)), count: 8), spacing: 4) {
                            ForEach(PlStyle.emojis + ["∅"], id: \.self) { e in
                                let on = e == "∅" ? p.look.emoji.isEmpty : p.look.emoji == e
                                Text(e).font(.system(size: 20)).frame(width: 40, height: 40).background(on ? Color.accentColor.opacity(0.3) : Color.gray.opacity(0.15), in: RoundedRectangle(cornerRadius: 10))
                                    .onTapGesture { lib.setLook(name) { $0.emoji = e == "∅" ? "" : e } }
                            }
                        }.padding(.horizontal, 16)
                        HStack { TextField("ou cola qualquer emoji", text: $emo).textFieldStyle(.roundedBorder).frame(width: 220); Button("Usar") { lib.setLook(name) { $0.emoji = String(emo.trimmingCharacters(in: .whitespaces).prefix(8)) } }.disabled(emo.isEmpty) }.padding(.horizontal, 16).padding(.top, 6)
                        T("Estampa do fundo"); chips(PlStyle.patterns, p.look.pattern) { v in lib.setLook(name) { $0.pattern = v } }
                        T("Fonte do nome")
                        ScrollView(.horizontal, showsIndicators: false) { HStack { ForEach(PlStyle.fonts, id: \.0) { k, n in
                            Button { lib.setLook(name) { $0.font = k } } label: { Text(n).font(PlStyle.font(k, size: 15)).padding(.horizontal, 12).padding(.vertical, 7).background(p.look.font == k ? Color.accentColor : Color.gray.opacity(0.18), in: Capsule()).foregroundStyle(p.look.font == k ? .white : .primary) }.buttonStyle(.plain)
                        } }.padding(.horizontal, 16) }
                        T("Formato da capa"); chips(PlStyle.shapes, p.look.shape) { v in lib.setLook(name) { $0.shape = v } }
                        T("Descrição")
                        TextField("Uma frase sobre essa playlist", text: $desc, axis: .vertical).lineLimit(2...5).textFieldStyle(.roundedBorder).padding(.horizontal, 16)
                        HStack { Button("Voltar ao padrão") { dropImg(); lib.setLook(name) { $0 = Look() }; desc = "" }.buttonStyle(.bordered); Spacer() }.padding(16)
                    }
                }
            }
            .navigationTitle("Personalizar").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Pronto") { lib.setLook(name) { $0.desc = desc.trimmingCharacters(in: .whitespacesAndNewlines) }; dismiss() } } }
            .onAppear { newName = name; desc = p?.look.desc ?? ""; let e = p?.look.emoji ?? ""; emo = PlStyle.emojis.contains(e) ? "" : e }
            .onChange(of: photo) { item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self), let path = saveCover(data) { dropImg(); lib.setLook(name) { $0.img = path; $0.mosaic = false } }
                    else { player.say("Não consegui abrir essa imagem") }
                    photo = nil
                }
            }
        }
    }
    @ViewBuilder func T(_ s: String) -> some View { Text(s).font(.footnote.weight(.semibold)).foregroundStyle(.secondary).padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 4) }
    func chips(_ opts: [(String, String)], _ cur: String, _ set: @escaping (String) -> Void) -> some View {
        ScrollView(.horizontal, showsIndicators: false) { HStack { ForEach(opts, id: \.0) { k, n in Chip(text: n, on: cur == k) { set(k) } } }.padding(.horizontal, 16) }
    }
    func dropImg() { if let i = p?.look.img, !i.isEmpty { try? FileManager.default.removeItem(atPath: i) } }
    /* recorta quadrada no meio, no máximo 900 px */
    func saveCover(_ data: Data) -> String? {
        guard let img = UIImage(data: data) else { return nil }
        let side = min(img.size.width, img.size.height), out = min(900, side)
        let r = UIGraphicsImageRenderer(size: CGSize(width: out, height: out))
        let sq = r.image { _ in img.draw(in: CGRect(x: -(img.size.width - side) / 2 * out / side, y: -(img.size.height - side) / 2 * out / side, width: img.size.width * out / side, height: img.size.height * out / side)) }
        let u = Lib.covers.appendingPathComponent(stableHash(UUID().uuidString) + ".jpg")
        guard let jpg = sq.jpegData(compressionQuality: 0.9), (try? jpg.write(to: u)) != nil else { return nil }
        return u.path
    }
}

/* tela de uma playlist */
struct PlaylistView: View {
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var player: Player
    @Environment(\.dismiss) var dismiss
    @State var name: String
    @State private var edit = false
    @State private var ask = false
    var body: some View {
        ScrollView {
            if let p = lib.playlist(name) {
                LazyVStack(alignment: .leading, spacing: 0) {
                    PlHero(p: p, sub: "\(p.tracks.count) músicas\(p.from.isEmpty ? "" : " · de \(p.from)")") { edit = true }.padding(.top, 8)
                    if !p.tracks.isEmpty {
                        HStack(spacing: 12) {
                            Button { player.shuffle = false; player.play(p.tracks, 0) } label: { Label("Tocar", systemImage: "play.fill").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
                            Button { player.play(p.tracks.shuffled(), 0) } label: { Label("Aleatório", systemImage: "shuffle").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
                        }.padding(16)
                    } else { Text("Playlist vazia. No menu … de qualquer música, toque em Adicionar à playlist.").foregroundStyle(.secondary).padding(24) }
                    ForEach(Array(p.tracks.enumerated()), id: \.element.key) { i, t in TrackRow(t: t, playlist: name) { player.play(p.tracks, i) } }
                    BottomSpace()
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItemGroup(placement: .navigationBarTrailing) {
            Button { edit = true } label: { Image(systemName: "paintbrush") }
            Button(role: .destructive) { ask = true } label: { Image(systemName: "trash") }
        } }
        .sheet(isPresented: $edit) { PlEditSheet(name: $name) }
        .confirmationDialog("Apagar \"\(name)\"?", isPresented: $ask, titleVisibility: .visible) { Button("Apagar", role: .destructive) { lib.deletePlaylist(name); dismiss() } }
    }
}
