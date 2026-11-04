import SwiftUI
import UniformTypeIdentifiers

/* ---------- Buscar: YouTube primeiro, depois SoundCloud, artistas e álbuns do YouTube Music, as suas offline e o que dá pra baixar ---------- */
struct SearchView: View {
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var player: Player
    @EnvironmentObject var off: Offline
    @State private var q = ""
    @State private var yt: [Track] = []
    @State private var sc: [Track] = []
    @State private var dl: [Track] = []
    @State private var ytm = YT.Search()
    @State private var loading = false
    @State private var linkTrack: Track? = nil
    static let browse: [(String, Double)] = [("Funk", 0), ("Trap", 280), ("Sertanejo", 35), ("Pagode", 150), ("Rap nacional", 10), ("Phonk", 260), ("Pop", 320), ("Rock", 5), ("Eletrônica", 190), ("MPB", 90), ("Gospel", 50), ("Lo-fi", 230), ("Anime", 300), ("Metal", 0), ("Reggae", 120), ("Forró", 25)]
    var body: some View {
        let query = q.trimmingCharacters(in: .whitespaces)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if query.isEmpty { home }
                else {
                    if let t = linkTrack { SectionTitle(text: "Do link"); TrackRow(t: t, wide: true) { player.play([t], 0) } }
                    if !ytm.artists.isEmpty {
                        SectionTitle(text: "Artistas")
                        ScrollView(.horizontal, showsIndicators: false) { LazyHStack(alignment: .top, spacing: 14) { ForEach(ytm.artists) { a in
                            NavigationLink(value: Route.artist(a)) { VStack(spacing: 4) { Art(a.art, radius: 52).frame(width: 104, height: 104); Text(a.name).font(.footnote.weight(.semibold)).lineLimit(1); Text("Artista").font(.caption2).foregroundStyle(.secondary) }.frame(width: 104) }.buttonStyle(.plain)
                        } }.padding(.horizontal, 16) }
                    }
                    if !ytm.albums.isEmpty {
                        SectionTitle(text: "Álbuns")
                        ScrollView(.horizontal, showsIndicators: false) { LazyHStack(alignment: .top, spacing: 12) { ForEach(ytm.albums) { a in
                            NavigationLink(value: Route.album(a)) { VStack(alignment: .leading, spacing: 3) { Art(a.art, radius: 10).frame(width: 130, height: 130); Text(a.title).font(.footnote.weight(.semibold)).lineLimit(1); Text([a.year, a.artist.isEmpty ? a.type : a.artist].filter { !$0.isEmpty }.joined(separator: " · ")).font(.caption2).foregroundStyle(.secondary).lineLimit(1) }.frame(width: 130) }.buttonStyle(.plain)
                        } }.padding(.horizontal, 16) }
                    }
                    let local = off.songs.filter { ($0.title + " " + $0.artist + " " + $0.album).localizedCaseInsensitiveContains(query) }
                    if !local.isEmpty { SectionTitle(text: "No iPhone"); ForEach(Array(local.prefix(20).enumerated()), id: \.offset) { i, t in TrackRow(t: t) { player.play(local, i) } } }
                    HStack { SectionTitle(text: "YouTube"); if loading { ProgressView() } }
                    ForEach(Array(yt.enumerated()), id: \.offset) { i, t in TrackRow(t: t, wide: true) { lib.addRecent(query); player.play(yt, i) } }
                    if !sc.isEmpty { SectionTitle(text: "SoundCloud"); ForEach(Array(sc.prefix(15).enumerated()), id: \.offset) { i, t in TrackRow(t: t) { lib.addRecent(query); player.play(sc, i) } } }
                    if !dl.isEmpty { SectionTitle(text: "Pra baixar"); Text("Músicas liberadas pelos próprios artistas (Audius e Internet Archive).").font(.caption).foregroundStyle(.secondary).padding(.horizontal, 16)
                        ForEach(Array(dl.enumerated()), id: \.offset) { i, t in TrackRow(t: t) { player.play(dl, i) } } }
                    if !loading && yt.isEmpty && sc.isEmpty && linkTrack == nil { Text("Nada encontrado pra \"\(query)\".").foregroundStyle(.secondary).frame(maxWidth: .infinity).padding(30) }
                }
                BottomSpace()
            }
        }
        .navigationTitle("Buscar")
        .searchable(text: $q, placement: .navigationBarDrawer(displayMode: .always), prompt: "Músicas, artistas, álbuns ou link")
        .onSubmit(of: .search) { lib.addRecent(q.trimmingCharacters(in: .whitespaces)) }
        .task(id: query) { await run(query) }
    }
    @ViewBuilder var home: some View {
        if !lib.d.recent.isEmpty {
            HStack { SectionTitle(text: "Buscas recentes"); Button("Limpar") { lib.d.recent = []; lib.save() }.padding(.trailing, 16).padding(.top, 14) }
            ScrollView(.horizontal, showsIndicators: false) { HStack { ForEach(lib.d.recent, id: \.self) { r in Chip(text: r) { q = r } } }.padding(.horizontal, 16) }
        }
        if !lib.d.history.isEmpty { SectionTitle(text: "Ouvidas recentemente"); TileRow(items: Array(lib.d.history.prefix(14)), size: 120) { player.play(Array(lib.d.history.prefix(14)), $0) } }
        SectionTitle(text: "Navegar por gênero")
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            ForEach(Self.browse, id: \.0) { g, hue in
                Button { q = g } label: {
                    Text(g).font(.system(size: 18, weight: .black)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 80, alignment: .topLeading).padding(12)
                        .background(LinearGradient(colors: [Color(hue: hue / 360, saturation: 0.7, brightness: 0.75), Color(hue: (hue + 40).truncatingRemainder(dividingBy: 360) / 360, saturation: 0.8, brightness: 0.35)], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 14))
                }.buttonStyle(.plain)
            }
        }.padding(.horizontal, 16)
    }
    func run(_ query: String) async {
        yt = []; sc = []; dl = []; ytm = YT.Search(); linkTrack = nil
        guard query.count >= 2 else { return }
        try? await Task.sleep(nanoseconds: 300_000_000); if Task.isCancelled { return }
        loading = true; defer { loading = false }
        // link colado: YouTube / SoundCloud
        if let id = YT.idFrom(query) {
            let m = await YT.oembed(id); linkTrack = Track.yt(id, m?.0 ?? "Vídeo do YouTube", m?.1 ?? "YouTube"); return
        }
        if query.contains("soundcloud.com/") {
            let path = (query.components(separatedBy: "soundcloud.com/").last ?? "").components(separatedBy: "?")[0]
            linkTrack = Track(src: "sc", id: path, title: path.components(separatedBy: "/").last ?? path, artist: path.components(separatedBy: "/").first ?? ""); return
        }
        async let a = YT.search(query); async let b = SC.search(query); async let c = YT.ytmSearch(query); async let d = Audius.search(query); async let e = Archive.search(query)
        let ra = await a; if Task.isCancelled { return }; yt = ra
        let rc = await c; if Task.isCancelled { return }; ytm = rc
        let rb = await b; if Task.isCancelled { return }; sc = rb
        let rd = await d, re = await e; if Task.isCancelled { return }
        dl = Array((rd.filter { !$0.download.isEmpty } + re).prefix(15))
    }
}

/* ---------- Rádio ao vivo ---------- */
struct RadioView: View {
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var player: Player
    @State private var q = ""
    @State private var tag = ""
    @State private var list: [Station]? = nil
    static let tags = ["", "pop", "rock", "sertanejo", "funk", "gospel", "news", "jazz", "eletronica", "metal", "mpb", "pagode"]
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                HStack { Chip(text: "Brasil", on: lib.d.settings.country == "BR") { lib.d.settings.country = "BR"; lib.save() }; Chip(text: "Mundo", on: lib.d.settings.country == "ZZ") { lib.d.settings.country = "ZZ"; lib.save() } }.padding(.horizontal, 16).padding(.top, 8)
                ScrollView(.horizontal, showsIndicators: false) { HStack { ForEach(Self.tags, id: \.self) { t in Chip(text: t.isEmpty ? "Mais ouvidas" : t.capitalized, on: tag == t) { tag = t } } }.padding(.horizontal, 16).padding(.vertical, 8) }
                if !lib.d.radioRecent.isEmpty && q.isEmpty {
                    SectionTitle(text: "Ouvidas por último")
                    ForEach(lib.d.radioRecent) { s in row(s) }
                }
                SectionTitle(text: q.isEmpty ? "Rádios" : "Resultados")
                if let list { ForEach(list) { s in row(s) }; if list.isEmpty { Text("Nenhuma rádio achada.").foregroundStyle(.secondary).padding(20) } }
                else { ProgressView().frame(maxWidth: .infinity).padding(30) }
                BottomSpace()
            }
        }
        .navigationTitle("Rádio")
        .searchable(text: $q, prompt: "Nome da rádio")
        .task(id: "\(q)|\(tag)|\(lib.d.settings.country)") { list = nil; try? await Task.sleep(nanoseconds: 250_000_000); list = await Radio.search(q: q, tag: tag, country: lib.d.settings.country) }
    }
    func row(_ s: Station) -> some View {
        let cur = player.current?.key == s.track.key
        return HStack(spacing: 12) {
            Art(s.art, radius: 8).frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 2) { Text(s.name).font(.body.weight(.semibold)).lineLimit(1).foregroundStyle(cur ? Color.accentColor : .primary); Text(cur && !player.nowTitle.isEmpty ? "♪ " + player.nowTitle : s.info).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
            Spacer()
            if cur && player.playing { Image(systemName: "dot.radiowaves.left.and.right").foregroundStyle(Color.accentColor) }
        }
        .padding(.horizontal, 16).padding(.vertical, 6).contentShape(Rectangle())
        .onTapGesture {
            lib.d.radioRecent.removeAll { $0.id == s.id }; lib.d.radioRecent.insert(s, at: 0); if lib.d.radioRecent.count > 8 { lib.d.radioRecent.removeLast() }; lib.save()
            player.play([s.track], 0)
        }
    }
}

/* ---------- Biblioteca ---------- */
struct LibraryView: View {
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var player: Player
    @EnvironmentObject var off: Offline
    @State private var newPl = false
    @State private var name = ""
    var body: some View {
        List {
            Section("No iPhone") {
                NavigationLink(value: Route.local) { tile("music.note", "Músicas offline", "\(off.songs.count) no iPhone") }
            }
            Section("Pra você") {
                NavigationLink(value: Route.favs) { tile("heart.fill", "Favoritas", "\(lib.d.favs.count) músicas") }
                NavigationLink(value: Route.history) { tile("clock.arrow.circlepath", "Ouvidas recentemente", "Histórico") }
                NavigationLink(value: Route.stats) { tile("chart.bar.fill", "Mais ouvidas", "Semana, mês, ano e sempre") }
                NavigationLink(value: Route.imp) { tile("square.and.arrow.down", "Importar playlists", "Spotify e YouTube") }
            }
            Section {
                ForEach(lib.d.playlists) { p in
                    NavigationLink(value: Route.playlist(p.name)) {
                        HStack(spacing: 12) { PlThumb(p: p).frame(width: 52, height: 52); VStack(alignment: .leading) { Text(p.name).lineLimit(1); Text(p.look.desc.isEmpty ? "\(p.tracks.count) músicas\(p.from.isEmpty ? "" : " · \(p.from)")" : p.look.desc).font(.caption).foregroundStyle(.secondary).lineLimit(1) } }
                    }
                }
                .onDelete { s in for i in s.sorted(by: >) { lib.deletePlaylist(lib.d.playlists[i].name) } }
                Button { newPl = true } label: { Label("Nova playlist", systemImage: "plus") }
            } header: { Text("Playlists") }
            Section { BottomSpace() }.listRowBackground(Color.clear)
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Biblioteca")
        .alert("Nova playlist", isPresented: $newPl) { TextField("Nome", text: $name); Button("Criar") { lib.createPlaylist(name); name = "" }; Button("Cancelar", role: .cancel) { name = "" } }
    }
    func tile(_ icon: String, _ t: String, _ s: String) -> some View {
        HStack(spacing: 12) { Image(systemName: icon).foregroundStyle(.white).frame(width: 40, height: 40).background(Color.accentColor, in: RoundedRectangle(cornerRadius: 9)); VStack(alignment: .leading) { Text(t); Text(s).font(.caption).foregroundStyle(.secondary) } }
    }
}

struct TrackListView: View {
    @EnvironmentObject var player: Player
    let title: String; let tracks: [Track]; var empty = "Nada por aqui ainda."
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if !tracks.isEmpty { HStack(spacing: 12) {
                    Button { player.play(tracks, 0) } label: { Label("Tocar", systemImage: "play.fill").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
                    Button { player.play(tracks.shuffled(), 0) } label: { Label("Aleatório", systemImage: "shuffle").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
                }.padding(16) } else { Text(empty).foregroundStyle(.secondary).padding(24) }
                ForEach(Array(tracks.enumerated()), id: \.offset) { i, t in TrackRow(t: t) { player.play(tracks, i) } }
                BottomSpace()
            }
        }.navigationTitle(title)
    }
}

struct LocalView: View {
    @EnvironmentObject var off: Offline
    @EnvironmentObject var player: Player
    @State private var importing = false
    @State private var sort = "title"
    var body: some View {
        let l: [Track] = sort == "artist" ? off.songs.sorted { $0.artist.localizedCaseInsensitiveCompare($1.artist) == .orderedAscending } : off.songs
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                Text("O iPhone não deixa ler as músicas soltas do aparelho. Jogue músicas na pasta \"Uncrowned\" do app Arquivos (ou do iTunes/Finder no PC) ou importe aqui.").font(.footnote).foregroundStyle(.secondary).padding(16)
                HStack(spacing: 10) {
                    Button { importing = true } label: { Label("Importar dos Arquivos", systemImage: "folder.badge.plus") }.buttonStyle(.borderedProminent)
                    Button { Task { await off.scan() } } label: { Label("Reler", systemImage: "arrow.clockwise") }.buttonStyle(.bordered)
                }.padding(.horizontal, 16)
                HStack { Chip(text: "A-Z", on: sort == "title") { sort = "title" }; Chip(text: "Artista", on: sort == "artist") { sort = "artist" } }.padding(16)
                if !l.isEmpty { HStack(spacing: 12) {
                    Button { player.play(l, 0) } label: { Label("Tocar", systemImage: "play.fill").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
                    Button { player.play(Offline.localRadio(seed: nil), 0) } label: { Label("Mix offline", systemImage: "waveform").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
                }.padding(.horizontal, 16) }
                if off.scanning { ProgressView().frame(maxWidth: .infinity).padding() }
                ForEach(Array(l.enumerated()), id: \.offset) { i, t in TrackRow(t: t) { player.play(l, i) } }
                BottomSpace()
            }
        }
        .navigationTitle("Músicas offline")
        .fileImporter(isPresented: $importing, allowedContentTypes: [.audio], allowsMultipleSelection: true) { r in
            if case .success(let urls) = r { Task { let n = await off.importFiles(urls); player.say("\(n) \(n == 1 ? "música importada" : "músicas importadas")") } }
        }
    }
}

struct StatsView: View {
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var player: Player
    @State private var p = "week"
    var body: some View {
        let st = lib.stats(p)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                Picker("", selection: $p) { Text("Semana").tag("week"); Text("Mês").tag("month"); Text("Ano").tag("year"); Text("Sempre").tag("all") }.pickerStyle(.segmented).padding(16)
                VStack(alignment: .leading) { Text("Tempo ouvindo").font(.caption).foregroundStyle(.secondary); Text(hours(st.total)).font(.system(size: 40, weight: .black)) }.padding(.horizontal, 16)
                if !st.artists.isEmpty {
                    SectionTitle(text: "Artistas")
                    ScrollView(.horizontal, showsIndicators: false) { LazyHStack(alignment: .top, spacing: 14) { ForEach(Array(st.artists.prefix(12).enumerated()), id: \.offset) { i, a in
                        VStack(spacing: 4) { Art(a.2, radius: 48).frame(width: 96, height: 96); Text("\(i + 1). \(a.0)").font(.footnote.weight(.semibold)).lineLimit(1); Text(hours(a.1)).font(.caption2).foregroundStyle(.secondary) }.frame(width: 100)
                    } }.padding(.horizontal, 16) }
                }
                SectionTitle(text: "Músicas")
                let songs = st.songs.map { $0.0 }
                ForEach(Array(st.songs.prefix(50).enumerated()), id: \.offset) { i, s in TrackRow(t: s.0, rank: i + 1) { player.play(songs, i) } }
                if st.songs.isEmpty { Text("Ouça umas músicas que elas aparecem aqui.").foregroundStyle(.secondary).padding(20) }
                BottomSpace()
            }
        }.navigationTitle("Mais ouvidas")
    }
    func hours(_ s: Int) -> String { s >= 3600 ? String(format: "%.1f h", Double(s) / 3600).replacingOccurrences(of: ".", with: ",") : "\(max(1, s / 60)) min" }
}

struct ImportView: View {
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var player: Player
    @State private var link = ""
    @State private var busy = false
    @State private var status = ""
    var body: some View {
        Form {
            Section(footer: Text("Playlist, álbum ou Mix do YouTube / YouTube Music, ou playlist e álbum públicos do Spotify. As músicas do Spotify são procuradas no YouTube.")) {
                TextField("Cola o link aqui", text: $link).textInputAutocapitalization(.never).autocorrectionDisabled()
                Button { Task { await go() } } label: { HStack { Text("Importar"); if busy { Spacer(); ProgressView() } } }.disabled(busy || link.isEmpty)
                if !status.isEmpty { Text(status).font(.footnote).foregroundStyle(.secondary) }
            }
        }.navigationTitle("Importar playlists")
    }
    func go() async {
        busy = true; status = "Lendo a playlist…"; defer { busy = false }
        do { let (n, c, t, f) = try await Importer.link(link); let name = lib.createPlaylist(n, tracks: t, cover: c, from: f); t.forEach { lib.bumpTaste($0.artist, 0.5) }; lib.save(); status = "Pronto: \"\(name)\" com \(t.count) músicas na Biblioteca"; link = "" }
        catch { status = error.localizedDescription }
    }
}

/* ---------- Ajustes ---------- */
struct SettingsView: View {
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var player: Player
    @State private var taps = 0
    var body: some View {
        Form {
            Section("Tema") {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: 12) { ForEach(Skins.all) { sk in
                        VStack(spacing: 6) {
                            Image("skin-\(sk.id)").resizable().scaledToFill().frame(width: 74, height: 74).clipShape(RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).stroke(lib.d.settings.skin == sk.id ? sk.color : .clear, lineWidth: 3))
                            Text(sk.name).font(.caption2.weight(.semibold)).lineLimit(1)
                            HStack(spacing: 3) { ForEach(sk.dots, id: \.self) { Circle().fill(Color(hex: $0)).frame(width: 9, height: 9) } }
                        }.frame(width: 84).onTapGesture { pickSkin(sk) }
                    } }.padding(.vertical, 6)
                }
                Text(Skins.get(lib.d.settings.skin).desc).font(.footnote).foregroundStyle(.secondary)
            }
            Section(header: Text("Reprodução")) {
                VStack(alignment: .leading) { Text("Velocidade: " + String(format: "%.2fx", lib.d.settings.speed)); Slider(value: Binding(get: { lib.d.settings.speed }, set: { player.setSpeed((($0 * 20).rounded()) / 20) }), in: 0.5...1.5) }
                Toggle("Mudar o tom junto com a velocidade", isOn: bind(\.pitchWithSpeed))
                Toggle("Tocar o próximo sozinho", isOn: bind(\.autoNext))
                Toggle("Fila acabou: tocar parecidas", isOn: bind(\.radioWhenEnds))
                Toggle("Letra sincronizada primeiro", isOn: bind(\.lyricsSynced))
            }
            Section(header: Text("Online"), footer: Text("O YouTube é o motor principal: busca, paradas, artistas e álbuns vêm dele e as músicas tocam no player do YouTube. O iPhone corta o som do YouTube com a tela apagada, então nessa hora a mesma música continua pelo SoundCloud, do mesmo ponto (quando ela existe lá).")) {
                Toggle("Tela apagada: continuar pelo SoundCloud", isOn: bind(\.bgSwitch))
                Toggle("Tocar pelo SoundCloud sempre que der", isOn: bind(\.scFirst))
                Toggle("Mostrar vídeo no player", isOn: bind(\.showVideo))
            }
            Section(header: Text("Notificações")) {
                Toggle("Lembretes zoeiros pra ouvir música", isOn: Binding(get: { lib.d.settings.nag }, set: { lib.d.settings.nag = $0; lib.save(); Nag.schedule() }))
            }
            if lib.d.settings.secret {
                Section(header: Text("Secreto"), footer: Text("Músicas do Audius e do Internet Archive que o artista liberou podem ser guardadas no app (menu … da música) e tocam sem internet.")) {
                    Button("Esconder as opções secretas") { lib.d.settings.secret = false; lib.save() }
                }
            }
            Section {
                HStack(spacing: 14) {
                    Image("logo").resizable().frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 14))
                    VStack(alignment: .leading) { Text("Uncrowned Music").font(.headline); Text("Criado por Vinicius Andrade · build \(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?")").font(.caption).foregroundStyle(.secondary) }
                }
                Link("Ver o LinkedIn", destination: URL(string: "https://www.linkedin.com/in/devviniciusandrade/")!)
            }
            Section { BottomSpace() }.listRowBackground(Color.clear)
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Ajustes")
    }
    func bind(_ k: WritableKeyPath<Settings, Bool>) -> Binding<Bool> { Binding(get: { lib.d.settings[keyPath: k] }, set: { lib.d.settings[keyPath: k] = $0; lib.save() }) }
    func pickSkin(_ sk: Skin) {
        if lib.d.settings.skin == sk.id {
            // 5 toques no tema atual liberam as opções secretas
            taps += 1
            if taps >= 5 { taps = 0; lib.d.settings.secret.toggle(); lib.save(); player.say(lib.d.settings.secret ? "Opções secretas liberadas 🤫" : "Opções secretas escondidas") }
            return
        }
        taps = 0
        lib.d.settings.skin = sk.id; lib.save(); Skins.applyIcon(sk.id); player.say("Tema \(sk.name) aplicado")
    }
}
