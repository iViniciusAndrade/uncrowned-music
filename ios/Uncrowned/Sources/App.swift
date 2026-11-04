import SwiftUI
import UserNotifications

@main
struct UncrownedApp: App {
    @StateObject private var lib = Lib.shared
    @StateObject private var player = Player.shared
    @StateObject private var off = Offline.shared
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            Root()
                .environmentObject(lib).environmentObject(player).environmentObject(off)
                .tint(Skins.get(lib.d.settings.skin).color)
                .preferredColorScheme(Skins.get(lib.d.settings.skin).scheme)
                .task { await off.scan(); Nag.schedule() }
        }
        .onChange(of: phase) { p in
            if p == .background { player.didEnterBackground(); lib.saveNow() }
            if p == .active { Nag.schedule() }
        }
    }
}

struct Root: View {
    @EnvironmentObject var lib: Lib
    @EnvironmentObject var player: Player
    @State private var tab = 0
    @State private var full = false
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottom) {
                SkinBackground()
                TabView(selection: $tab) {
                    nav { DiscoverView() }.tabItem { Label("Descobrir", systemImage: "safari") }.tag(0)
                    nav { SearchView() }.tabItem { Label("Buscar", systemImage: "magnifyingglass") }.tag(1)
                    nav { RadioView() }.tabItem { Label("Rádio", systemImage: "dot.radiowaves.left.and.right") }.tag(2)
                    nav { LibraryView() }.tabItem { Label("Biblioteca", systemImage: "square.stack.fill") }.tag(3)
                    nav { SettingsView() }.tabItem { Label("Ajustes", systemImage: "gearshape.fill") }.tag(4)
                }
                if !full { MiniPlayer { withAnimation(.spring(response: 0.38)) { full = true } }.padding(.bottom, 54 + geo.safeAreaInsets.bottom).transition(.move(edge: .bottom)) }
                if full { FullPlayer { withAnimation(.spring(response: 0.38)) { full = false } }.transition(.move(edge: .bottom)).zIndex(5) }
                // o player do YouTube fica sempre montado; aparece de verdade só quando o vídeo está ligado no player aberto
                let showVid = full && player.engine == .web && lib.d.settings.showVideo
                YTHost(web: player.web.view)
                    // escondido ele continua com 240x135: o YouTube não toca em player menor que 200x200
                    .frame(width: showVid ? geo.size.width - 32 : 240, height: showVid ? (geo.size.width - 32) * 9 / 16 : 200)
                    .clipShape(RoundedRectangle(cornerRadius: showVid ? 14 : 0))
                    .opacity(showVid ? 1 : 0.011)
                    .allowsHitTesting(showVid)
                    .position(x: showVid ? geo.size.width / 2 : 120, y: showVid ? geo.safeAreaInsets.top + 150 + (geo.size.width - 32) * 9 / 32 : geo.size.height - 100)
                    .zIndex(6)
                if let t = player.toast {
                    Text(t).font(.footnote.weight(.semibold)).padding(.horizontal, 16).padding(.vertical, 10).background(.ultraThinMaterial, in: Capsule())
                        .padding(.bottom, 130 + geo.safeAreaInsets.bottom).transition(.opacity).zIndex(7)
                }
            }
            .ignoresSafeArea(.keyboard)
        }
        .animation(.easeOut(duration: 0.2), value: player.toast)
    }
    func nav<V: View>(@ViewBuilder _ v: () -> V) -> some View {
        NavigationStack {
            v().background(Color.clear)
                .navigationDestination(for: Route.self) { r in
                    switch r {
                    case .artist(let h): ArtistView(hit: h)
                    case .album(let h): AlbumView(hit: h)
                    case .list(let l): YtmListView(l: l)
                    case .playlist(let n): PlaylistView(name: n)
                    case .stats: StatsView()
                    case .imp: ImportView()
                    case .local: LocalView()
                    case .favs: TrackListView(title: "Favoritas", tracks: lib.d.favs, empty: "Toque no coração das músicas pra salvar aqui.")
                    case .history: TrackListView(title: "Ouvidas recentemente", tracks: lib.d.history)
                    }
                }
        }
    }
}

/* ---------- notificações zoeiras chamando pra ouvir música (igual Android) ---------- */
@MainActor
enum Nag {
    static let lines = ["Teu fone tá com saudade 🎧", "Silêncio demais por aí… bora uma música?", "As Descobertas da Semana tão te esperando 👀", "Sem música até agora? Isso é crime 💀",
                        "Tua playlist favorita perguntou de você", "Bota uma pra tocar e finge que tá num clipe", "O Uncrowned tá carente. Abre aí 🥺"]
    static func schedule() {
        let c = UNUserNotificationCenter.current()
        c.removePendingNotificationRequests(withIdentifiers: (0..<3).map { "nag\($0)" })
        guard Lib.shared.d.settings.nag else { return }
        c.requestAuthorization(options: [.alert, .sound]) { ok, _ in
            guard ok else { return }
            // daqui a 1, 2 e 3 dias sem abrir o app, no fim da tarde
            for d in 1...3 {
                let n = UNMutableNotificationContent(); n.title = "Uncrowned"; n.body = lines.randomElement() ?? lines[0]
                var dc = Calendar.current.dateComponents([.year, .month, .day], from: Date().addingTimeInterval(Double(d) * 86400)); dc.hour = 18; dc.minute = 40
                c.add(UNNotificationRequest(identifier: "nag\(d - 1)", content: n, trigger: UNCalendarNotificationTrigger(dateMatching: dc, repeats: false)))
            }
        }
    }
}
