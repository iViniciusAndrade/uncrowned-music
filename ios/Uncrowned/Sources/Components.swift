import SwiftUI
import UIKit

/* capa: imagem da internet ou do iPhone, com uma nota no lugar enquanto carrega */
struct Art: View {
    let url: URL?
    var radius: CGFloat = 8
    var body: some View {
        ZStack {
            Rectangle().fill(Color.gray.opacity(0.22))
            Image(systemName: "music.note").font(.system(size: 18)).foregroundStyle(.secondary)
            if let url, url.isFileURL, let img = UIImage(contentsOfFile: url.path) {
                Image(uiImage: img).resizable().scaledToFill()
            } else if let url {
                AsyncImage(url: url, transaction: Transaction(animation: .easeOut(duration: 0.2))) { ph in
                    if case .success(let i) = ph { i.resizable().scaledToFill() } else { Color.clear }
                }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}
extension Art { init(_ s: String, radius: CGFloat = 8) { self.url = s.isEmpty ? nil : (s.hasPrefix("/") ? URL(fileURLWithPath: s) : URL(string: s)); self.radius = radius } }

struct SectionTitle: View {
    let text: String; var trailing: AnyView? = nil
    var body: some View {
        HStack { Text(text).font(.title3.bold()); Spacer(); trailing }.padding(.horizontal, 16).padding(.top, 18).padding(.bottom, 6)
    }
}

/* linha de música com o menu "…" */
struct TrackRow: View {
    @EnvironmentObject var player: Player
    @EnvironmentObject var lib: Lib
    let t: Track
    var rank: Int? = nil
    var playlist: String? = nil
    var wide = false
    let onTap: () -> Void
    var body: some View {
        let cur = player.current?.key == t.key
        HStack(spacing: 12) {
            if let rank { Text("\(rank)").font(.subheadline.bold()).foregroundStyle(Color.accentColor).frame(width: 26) }
            Art(t.art, radius: 6).frame(width: wide ? 86 : 48, height: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(t.title).font(.body.weight(.semibold)).lineLimit(1).foregroundStyle(cur ? Color.accentColor : Color.primary)
                HStack(spacing: 4) {
                    if t.src != "local" { Text(t.srcName.uppercased()).font(.system(size: 9, weight: .heavy)).padding(.horizontal, 4).padding(.vertical, 1).background(badge.opacity(0.2), in: Capsule()).foregroundStyle(badge) }
                    Text(t.artist + (t.duration > 0 ? " · " + fmtTime(Double(t.duration) / 1000) : "")).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                if !t.why.isEmpty { Text(t.why).font(.caption2).foregroundStyle(Color.accentColor).lineLimit(1) }
            }
            Spacer(minLength: 0)
            if cur && player.playing { Image(systemName: "waveform").foregroundStyle(Color.accentColor).symbolEffectIfAvailable() }
            TrackMenu(t: t, playlist: playlist) { Image(systemName: "ellipsis").foregroundStyle(.secondary).frame(width: 34, height: 34).contentShape(Rectangle()) }
        }
        .padding(.horizontal, 16).padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }
    var badge: Color { ["yt": Color.red, "sc": Color.orange, "au": Color.purple, "ia": Color.blue][t.src] ?? .gray }
}
extension View {
    @ViewBuilder func symbolEffectIfAvailable() -> some View { if #available(iOS 17.0, *) { self.symbolEffect(.variableColor.iterative) } else { self } }
}

struct TrackMenu<Label: View>: View {
    @EnvironmentObject var player: Player
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var off: Offline
    let t: Track
    var playlist: String? = nil
    @ViewBuilder let label: () -> Label
    @State private var pick = false
    @State private var info = false
    var body: some View {
        Menu {
            Button { player.playNext(t) } label: { SwiftUI.Label("Tocar a seguir", systemImage: "text.insert") }
            Button { player.enqueue(t) } label: { SwiftUI.Label("Adicionar à fila", systemImage: "text.append") }
            Button { pick = true } label: { SwiftUI.Label("Adicionar à playlist", systemImage: "music.note.list") }
            Button { lib.toggleFav(t) } label: { SwiftUI.Label(lib.isFav(t) ? "Tirar dos favoritos" : "Favoritar", systemImage: lib.isFav(t) ? "heart.slash" : "heart") }
            if t.src != "radio" { Button { Task { await player.radio(from: t, append: false) } } label: { SwiftUI.Label("Tocar parecidas (rádio)", systemImage: "dot.radiowaves.left.and.right") } }
            Button { info = true } label: { SwiftUI.Label("Informações", systemImage: "info.circle") }
            if !t.link.isEmpty, let u = URL(string: t.link) { ShareLink(item: u, subject: Text(t.title)) { SwiftUI.Label("Compartilhar", systemImage: "square.and.arrow.up") } }
            if lib.d.settings.secret && !t.download.isEmpty && (t.src == "au" || t.src == "ia") && Offline.file(for: t) == nil {
                Button { Task { let ok = await off.save(t); player.say(ok ? "Guardada no app: toca sem internet" : "Não deu pra guardar essa") } } label: { SwiftUI.Label("Guardar no app", systemImage: "arrow.down.circle") }
            }
            if let playlist { Button(role: .destructive) { lib.remove(t, from: playlist) } label: { SwiftUI.Label("Tirar desta playlist", systemImage: "minus.circle") } }
            if t.src == "local" { Button(role: .destructive) { off.delete(t); player.say("Música apagada do iPhone") } label: { SwiftUI.Label("Apagar do iPhone", systemImage: "trash") } }
        } label: { label() }
        .sheet(isPresented: $pick) { PlaylistPicker(t: t) }
        .sheet(isPresented: $info) { InfoSheet(t: t) }
    }
}

struct PlaylistPicker: View {
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var player: Player
    @Environment(\.dismiss) var dismiss
    let t: Track
    @State private var name = ""
    var body: some View {
        NavigationStack {
            List {
                Section { HStack { TextField("Nova playlist", text: $name); Button("Criar") { let n = lib.createPlaylist(name); lib.add(t, to: n); player.say("Adicionada em \(n)"); dismiss() }.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty) } }
                Section("Suas playlists") {
                    ForEach(lib.d.playlists) { p in
                        Button { lib.add(t, to: p.name); player.say("Adicionada em \(p.name)"); dismiss() } label: { HStack { PlThumb(p: p).frame(width: 40, height: 40); Text(p.name).foregroundStyle(.primary) } }
                    }
                }
            }
            .navigationTitle("Adicionar à playlist").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fechar") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

struct InfoSheet: View {
    let t: Track
    @State private var i: Info.I? = nil
    @State private var done = false
    var body: some View {
        VStack(spacing: 14) {
            Art(i?.art ?? t.art, radius: 14).frame(width: 180, height: 180).padding(.top, 24)
            Text(i?.title ?? t.title).font(.title3.bold()).multilineTextAlignment(.center)
            Text(i?.artist ?? t.artist).foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 8) {
                row("Álbum", i?.album ?? t.album); row("Ano", i?.year ?? ""); row("Gênero", i?.genre ?? t.genre); row("Fonte", t.srcName)
            }.padding().background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14)).padding(.horizontal)
            if !done { ProgressView() } else if i != nil { Text("Dados: Apple Music / iTunes").font(.caption).foregroundStyle(.secondary) }
            Spacer()
        }
        .task { i = await Info.get(t); done = true }
        .presentationDetents([.medium, .large])
    }
    @ViewBuilder func row(_ k: String, _ v: String) -> some View { if !v.isEmpty { HStack { Text(k).foregroundStyle(.secondary); Spacer(); Text(v).multilineTextAlignment(.trailing) } } }
}

/* espaço no fim das listas pro mini player não cobrir a última música */
struct BottomSpace: View { var body: some View { Color.clear.frame(height: 90) } }

struct Chip: View {
    let text: String; var on = false; let tap: () -> Void
    var body: some View {
        Button(action: tap) { Text(text).font(.subheadline.weight(.semibold)).padding(.horizontal, 14).padding(.vertical, 8)
            .background(on ? Color.accentColor : Color.gray.opacity(0.18), in: Capsule()).foregroundStyle(on ? Color.white : Color.primary) }.buttonStyle(.plain)
    }
}

/* fila horizontal de capas */
struct TileRow: View {
    let items: [Track]; var size: CGFloat = 140; var round = false; let tap: (Int) -> Void
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: 12) {
                ForEach(Array(items.enumerated()), id: \.offset) { i, t in
                    VStack(alignment: .leading, spacing: 3) {
                        Art(t.art, radius: round ? size / 2 : 10).frame(width: size, height: size)
                        Text(t.title).font(.footnote.weight(.semibold)).lineLimit(1)
                        Text(t.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        if !t.why.isEmpty { Text(t.why).font(.caption2).foregroundStyle(Color.accentColor).lineLimit(2) }
                    }.frame(width: size).onTapGesture { tap(i) }
                }
            }.padding(.horizontal, 16)
        }
    }
}
