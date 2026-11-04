import SwiftUI

/* ---------- Descobrir: Descobertas da Semana, artistas pra conhecer, mais tocadas, clipes, playlists populares e seu gosto ---------- */
struct DiscoverView: View {
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var player: Player
    @State private var wk: WeeklyCache? = nil
    @State private var wkLoading = false
    @State private var gl = "BR"
    @State private var src = "yt"
    @State private var all = false
    @State private var yt: YT.Charts? = nil
    @State private var sc: [Track]? = nil
    @State private var au: [Track]? = nil
    @State private var lists: [(String, [YtmList])] = []
    @State private var openArtist: YtmArtistHit? = nil
    var body: some View {
        if lib.d.genres.isEmpty && lib.d.taste.isEmpty && lib.d.plays.isEmpty { GenrePicker() }
        else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    WeeklyHero(items: wk?.tracks ?? [], loading: wkLoading) { force in Task { wkLoading = true; wk = await Weekly.get(force: force); wkLoading = false } }
                    if let w = wk, !w.tracks.isEmpty { TileRow(items: w.tracks) { player.play(w.tracks, $0) }.padding(.vertical, 8) }
                    if let a = wk?.artists, !a.isEmpty {
                        SectionTitle(text: "Artistas pra você conhecer")
                        ScrollView(.horizontal, showsIndicators: false) { LazyHStack(alignment: .top, spacing: 14) { ForEach(a) { x in
                            VStack(spacing: 4) {
                                Art(x.pic, radius: 52).frame(width: 104, height: 104)
                                Text(x.name).font(.footnote.weight(.semibold)).lineLimit(1)
                                if !x.because.isEmpty { Text("Porque você ouve \(x.because)").font(.caption2).foregroundStyle(.secondary).lineLimit(2).multilineTextAlignment(.center) }
                            }.frame(width: 104).onTapGesture { Task { if let h = await YT.ytmSearch(x.name).artists.first { openArtist = h } else { player.say("Não achei \(x.name) agora") } } }
                        } }.padding(.horizontal, 16) }
                    }
                    charts
                    if let v = yt?.videos, !v.isEmpty { SectionTitle(text: "Clipes em alta"); TileRow(items: v, size: 150) { player.play(v, $0) } }
                    if !lists.isEmpty {
                        SectionTitle(text: "Playlists mais populares do YouTube")
                        ForEach(lists, id: \.0) { shelf, items in
                            if lists.count > 1 { Text(shelf).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary).padding(.horizontal, 16).padding(.top, 6) }
                            ScrollView(.horizontal, showsIndicators: false) { LazyHStack(alignment: .top, spacing: 12) { ForEach(items) { l in
                                NavigationLink(value: Route.list(l)) { VStack(alignment: .leading, spacing: 3) { Art(l.art, radius: 10).frame(width: 140, height: 140); Text(l.title).font(.footnote.weight(.semibold)).lineLimit(1); Text(l.sub).font(.caption).foregroundStyle(.secondary).lineLimit(1) }.frame(width: 140) }.buttonStyle(.plain)
                            } }.padding(.horizontal, 16) }
                        }
                    }
                    TasteCard()
                    BottomSpace()
                }
            }
            .navigationTitle("Descobrir")
            .navigationDestination(isPresented: Binding(get: { openArtist != nil }, set: { if !$0 { openArtist = nil } })) { if let h = openArtist { ArtistView(hit: h) } }
            .task { if wk == nil { wk = lib.d.weekly; wkLoading = true; wk = await Weekly.get(); wkLoading = false } }
            .task(id: gl) { yt = nil; sc = nil; async let a = YT.charts(gl); async let b = SC.charts(gl); yt = await a; sc = await b; if au == nil { au = await Audius.trending() }; if lists.isEmpty { lists = await YT.popularLists(gl) } }
            .refreshable { wk = await Weekly.get(force: true) }
        }
    }
    @ViewBuilder var charts: some View {
        HStack { Text("Mais tocadas da semana").font(.title3.bold()); Spacer(); Chip(text: "Brasil", on: gl == "BR") { gl = "BR"; all = false }; Chip(text: "Mundo", on: gl == "ZZ") { gl = "ZZ"; all = false } }.padding(.horizontal, 16).padding(.top, 18)
        HStack { ForEach([("yt", "YouTube"), ("sc", "SoundCloud"), ("au", "Audius")], id: \.0) { k, n in Chip(text: n, on: src == k) { src = k; all = false } } }.padding(.horizontal, 16).padding(.vertical, 8)
        let list: [Track]? = src == "yt" ? yt?.top : src == "sc" ? sc : au
        Text(src == "yt" ? (yt?.title ?? "YouTube Music") : src == "sc" ? (gl == "BR" ? "Top no Brasil" : "Top no mundo") : "Em alta no mundo · artistas independentes").font(.caption).foregroundStyle(.secondary).padding(.horizontal, 16)
        if let list {
            if list.isEmpty { Text("Não deu pra carregar agora.").foregroundStyle(.secondary).padding(16) }
            ForEach(Array((all ? list : Array(list.prefix(10))).enumerated()), id: \.offset) { i, t in TrackRow(t: t, rank: i + 1) { player.play(list, i) } }
            if list.count > 10 { Button(all ? "Mostrar menos" : "Ver todas as \(list.count)") { all.toggle() }.padding(.horizontal, 16) }
        } else { ProgressView().frame(maxWidth: .infinity).padding() }
    }
}

struct WeeklyHero: View {
    @EnvironmentObject var player: Player
    let items: [Track]; let loading: Bool; let refresh: (Bool) -> Void
    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [Color.accentColor.opacity(0.95), Color.accentColor.opacity(0.4), Color(hex: "#0a0a0c")], startPoint: .topLeading, endPoint: .bottomTrailing)
            HStack { Spacer(); ZStack { ForEach(Array(items.prefix(3).enumerated()), id: \.offset) { i, t in
                Art(t.art, radius: 10).frame(width: i == 1 ? 96 : 82, height: i == 1 ? 96 : 82).rotationEffect(.degrees(Double(i - 1) * 9)).offset(x: CGFloat(i - 1) * 42, y: CGFloat(abs(i - 1)) * 6).zIndex(i == 1 ? 2 : 1).shadow(radius: 8)
            } }.frame(width: 180, height: 120) }.frame(maxHeight: .infinity, alignment: .top).padding(.top, 16)
            VStack(alignment: .leading, spacing: 4) {
                Text("FEITA PRA VOCÊ").font(.system(size: 10, weight: .heavy)).kerning(1.5).foregroundStyle(.white.opacity(0.8))
                Text("Descobertas da Semana").font(.system(size: 26, weight: .black)).foregroundStyle(.white)
                Text(loading && items.isEmpty ? "Montando sua lista pelo seu gosto…" : "\(items.count) músicas novas pra você · renova toda semana").font(.caption).foregroundStyle(.white.opacity(0.85))
                let arts = Array(NSOrderedSet(array: items.map { $0.artist }.filter { !$0.isEmpty }).array.compactMap { $0 as? String }.prefix(3))
                if !arts.isEmpty { Text("Com \(arts.joined(separator: ", ")) e mais").font(.caption2).foregroundStyle(.white.opacity(0.6)).lineLimit(1) }
                HStack(spacing: 10) {
                    Button { if !items.isEmpty { player.shuffle = false; player.play(items, 0) } } label: { Image(systemName: "play.fill").font(.title2).foregroundStyle(.black).frame(width: 52, height: 52).background(.white, in: Circle()) }
                    Button { if !items.isEmpty { player.play(items.shuffled(), 0) } } label: { Image(systemName: "shuffle").foregroundStyle(.white).frame(width: 40, height: 40).background(.white.opacity(0.14), in: Circle()) }
                    Button { refresh(true) } label: { Group { if loading { ProgressView().tint(.white) } else { Image(systemName: "arrow.clockwise") } }.foregroundStyle(.white).frame(width: 40, height: 40).background(.white.opacity(0.14), in: Circle()) }
                }.padding(.top, 8)
            }.padding(18)
        }
        .frame(height: 240)
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .padding(.horizontal, 16).padding(.top, 8)
    }
}

struct TasteCard: View {
    @EnvironmentObject var lib: Lib
    @State private var pick = false
    var body: some View {
        let a = lib.d.taste.filter { $0.value > 0.5 }.sorted { $0.value > $1.value }.prefix(8)
        let mx = a.first?.value ?? 1
        VStack(alignment: .leading, spacing: 8) {
            HStack { Text("Seu gosto musical").font(.headline); Spacer(); Button("Ajustar") { pick = true } }
            if a.isEmpty { Text("Ouça umas músicas que ele aparece aqui.").font(.footnote).foregroundStyle(.secondary) }
            ForEach(Array(a), id: \.key) { k, v in
                HStack { Text(k).font(.caption).lineLimit(1).frame(width: 120, alignment: .leading); GeometryReader { g in Capsule().fill(Color.accentColor).frame(width: max(6, g.size.width * CGFloat(v / mx))) }.frame(height: 8) }
            }
            if !lib.d.genres.isEmpty { Text("Estilos: " + lib.d.genres.joined(separator: ", ")).font(.caption).foregroundStyle(.secondary) }
        }
        .padding(16).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18)).padding(16)
        .sheet(isPresented: $pick) { NavigationStack { GenrePicker(sheet: true) } }
    }
}

struct GenrePicker: View {
    @EnvironmentObject var lib: Lib
    @Environment(\.dismiss) var dismiss
    var sheet = false
    @State private var picked: Set<String> = []
    static let all = ["Funk", "Trap", "Rap nacional", "Phonk", "Sertanejo", "Pagode", "Pop", "Rock", "Metal", "Eletrônica", "House", "Lo-fi", "R&B", "Reggaeton", "MPB", "Gospel", "Forró", "Anime", "Drill", "Indie", "Black Metal", "Jazz", "Reggae", "K-pop"]
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Do que você curte?").font(.largeTitle.bold())
                Text("Escolhe pelo menos 3. O app aprende o resto com o que você ouve, curte e pula.").foregroundStyle(.secondary)
                FlowLayout(spacing: 8) { ForEach(Self.all, id: \.self) { g in Chip(text: g, on: picked.contains(g)) { if picked.contains(g) { picked.remove(g) } else { picked.insert(g) } } } }
                HStack {
                    if sheet { Button("Zerar gosto", role: .destructive) { lib.d.taste = [:]; lib.d.weekly = nil; lib.save(); picked = [] } }
                    Spacer()
                    Button("Pronto") { lib.d.genres = Array(picked); lib.d.weekly = nil; lib.save(); dismiss() }.buttonStyle(.borderedProminent).disabled(picked.count < 3)
                }
            }.padding(20)
        }
        .onAppear { picked = Set(lib.d.genres) }
    }
}

/* fileira que quebra linha (chips) */
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = proposal.width ?? 360; var x: CGFloat = 0, y: CGFloat = 0, row: CGFloat = 0
        for s in subviews { let z = s.sizeThatFits(.unspecified); if x + z.width > w && x > 0 { x = 0; y += row + spacing; row = 0 }; x += z.width + spacing; row = max(row, z.height) }
        return CGSize(width: w, height: y + row)
    }
    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = b.minX, y = b.minY, row: CGFloat = 0
        for s in subviews { let z = s.sizeThatFits(.unspecified); if x + z.width > b.maxX && x > b.minX { x = b.minX; y += row + spacing; row = 0 }; s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(z)); x += z.width + spacing; row = max(row, z.height) }
    }
}

/* ---------- artista, álbum e playlist do YouTube Music ---------- */
enum Route: Hashable { case artist(YtmArtistHit), album(YtmAlbumHit), list(YtmList), playlist(String), stats, imp, local, favs, history }

struct ArtistView: View {
    @EnvironmentObject var player: Player
    let hit: YtmArtistHit
    @State private var a: YtmArtistPage? = nil
    @State private var failed = false
    @State private var all = false
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ZStack(alignment: .bottomLeading) {
                    Art(a?.art ?? hit.art, radius: 0).frame(height: 300).clipped()
                    LinearGradient(colors: [.clear, .black.opacity(0.85)], startPoint: .center, endPoint: .bottom)
                    VStack(alignment: .leading) { Text("ARTISTA").font(.caption2.weight(.heavy)).foregroundStyle(.white.opacity(0.8)); Text(a?.name ?? hit.name).font(.largeTitle.bold()).foregroundStyle(.white); if let s = a?.subs, !s.isEmpty { Text(s).font(.caption).foregroundStyle(.white.opacity(0.8)) } }.padding(18)
                }.frame(height: 300)
                if let a {
                    HStack(spacing: 12) {
                        Button { player.play(a.top, 0) } label: { Label("Tocar", systemImage: "play.fill").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
                        Button { player.play(a.top.shuffled(), 0) } label: { Label("Aleatório", systemImage: "shuffle").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
                    }.padding(16).disabled(a.top.isEmpty)
                    SectionTitle(text: "Populares")
                    ForEach(Array((all ? a.top : Array(a.top.prefix(10))).enumerated()), id: \.offset) { i, t in TrackRow(t: t, rank: i + 1) { player.play(a.top, i) } }
                    if a.top.count > 10 { Button(all ? "Mostrar menos" : "Ver todas as \(a.top.count)") { all.toggle() }.padding(.horizontal, 16) }
                    albums("Álbuns", a.albums); albums("Singles e EPs", a.singles)
                    if !a.videos.isEmpty { SectionTitle(text: "Vídeos"); TileRow(items: a.videos, size: 150) { player.play(a.videos, $0) } }
                } else if failed { Text("Não deu pra abrir esse artista agora.").foregroundStyle(.secondary).padding(24) }
                else { ProgressView().frame(maxWidth: .infinity).padding(30) }
                BottomSpace()
            }
        }
        .ignoresSafeArea(edges: .top)
        .task { a = await YT.artist(hit.id); failed = a == nil }
    }
    @ViewBuilder func albums(_ title: String, _ l: [YtmList]) -> some View {
        if !l.isEmpty {
            SectionTitle(text: title)
            ScrollView(.horizontal, showsIndicators: false) { LazyHStack(alignment: .top, spacing: 12) { ForEach(l) { x in
                NavigationLink(value: Route.album(YtmAlbumHit(id: x.id, title: x.title, type: "", artist: hit.name, year: "", art: x.art))) {
                    VStack(alignment: .leading, spacing: 3) { Art(x.art, radius: 10).frame(width: 140, height: 140); Text(x.title).font(.footnote.weight(.semibold)).lineLimit(1); Text(x.sub).font(.caption).foregroundStyle(.secondary).lineLimit(1) }.frame(width: 140)
                }.buttonStyle(.plain)
            } }.padding(.horizontal, 16) }
        }
    }
}

struct AlbumView: View {
    @EnvironmentObject var player: Player
    @EnvironmentObject var lib: Lib
    let hit: YtmAlbumHit
    @State private var a: YtmAlbumPage? = nil
    @State private var failed = false
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                VStack(spacing: 8) {
                    Art(a?.art ?? hit.art, radius: 14).frame(width: 220, height: 220).shadow(radius: 16)
                    Text(a?.title ?? hit.title).font(.title2.bold()).multilineTextAlignment(.center)
                    Text([a?.artist ?? hit.artist, a?.type ?? hit.type, a?.year ?? hit.year].filter { !$0.isEmpty }.joined(separator: " · ")).font(.subheadline).foregroundStyle(.secondary)
                }.frame(maxWidth: .infinity).padding(.top, 12)
                if let a {
                    HStack(spacing: 12) {
                        Button { player.play(a.tracks, 0) } label: { Label("Tocar", systemImage: "play.fill").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
                        Button { let n = lib.createPlaylist(a.title, tracks: a.tracks, cover: a.art, from: "YouTube Music"); player.say("Salvo em \(n)") } label: { Label("Salvar", systemImage: "plus").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
                    }.padding(16).disabled(a.tracks.isEmpty)
                    ForEach(Array(a.tracks.enumerated()), id: \.offset) { i, t in TrackRow(t: t, rank: i + 1) { player.play(a.tracks, i) } }
                } else if failed { Text("Não deu pra abrir esse álbum agora.").foregroundStyle(.secondary).padding(24) }
                else { ProgressView().frame(maxWidth: .infinity).padding(30) }
                BottomSpace()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .task { a = await YT.album(hit.id); failed = a == nil || a!.tracks.isEmpty }
    }
}

struct YtmListView: View {
    @EnvironmentObject var player: Player
    @EnvironmentObject var lib: Lib
    let l: YtmList
    @State private var tracks: [Track]? = nil
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 14) {
                    Art(l.art, radius: 12).frame(width: 120, height: 120)
                    VStack(alignment: .leading, spacing: 6) {
                        Text("PLAYLIST").font(.caption2.weight(.heavy)).foregroundStyle(.secondary)
                        Text(l.title).font(.title3.bold())
                        Text(tracks.map { "\($0.count) músicas · YouTube Music" } ?? "Carregando…").font(.caption).foregroundStyle(.secondary)
                        if let t = tracks, !t.isEmpty { HStack { Button { player.play(t, 0) } label: { Image(systemName: "play.fill") }.buttonStyle(.borderedProminent)
                            Button { let n = lib.createPlaylist(l.title, tracks: t, cover: l.art, from: "YouTube"); player.say("Salvo em \(n)") } label: { Image(systemName: "plus") }.buttonStyle(.bordered) } }
                    }
                }.padding(16)
                if let t = tracks { ForEach(Array(t.enumerated()), id: \.offset) { i, x in TrackRow(t: x, rank: i + 1) { player.play(t, i) } } } else { ProgressView().frame(maxWidth: .infinity) }
                BottomSpace()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .task { tracks = await YT.playlist(l.id) }
    }
}
