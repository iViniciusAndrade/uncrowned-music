import SwiftUI
import WebKit

/* o player do YouTube fica sempre montado (escondido): o iOS não toca vídeo de página que não está na tela */
struct YTHost: UIViewRepresentable {
    let web: WKWebView
    func makeUIView(context: Context) -> UIView { let v = UIView(); v.backgroundColor = .clear; web.frame = v.bounds; web.autoresizingMask = [.flexibleWidth, .flexibleHeight]; v.addSubview(web); return v }
    func updateUIView(_ v: UIView, context: Context) { if web.superview !== v { web.removeFromSuperview(); web.frame = v.bounds; v.addSubview(web) } }
}

struct MiniPlayer: View {
    @EnvironmentObject var player: Player
    let open: () -> Void
    var body: some View {
        if let t = player.current {
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Art(t.art, radius: 6).frame(width: 44, height: 44)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(t.src == "radio" && !player.nowTitle.isEmpty ? player.nowTitle : t.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                        Text(player.loading != nil ? "Carregando…" : (t.src == "radio" ? t.title : t.artist)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    Button { player.toggle() } label: { Image(systemName: player.playing ? "pause.fill" : "play.fill").font(.title2).frame(width: 40, height: 40) }
                    Button { player.next() } label: { Image(systemName: "forward.fill").font(.title3).frame(width: 36, height: 40) }
                }.padding(.horizontal, 10).padding(.vertical, 7)
                GeometryReader { g in Rectangle().fill(Color.accentColor).frame(width: player.dur > 0 ? g.size.width * min(1, player.time / player.dur) : 0) }.frame(height: 2)
            }
            .foregroundStyle(.primary)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .shadow(color: .black.opacity(0.25), radius: 10, y: 4)
            .padding(.horizontal, 8)
            .contentShape(Rectangle())
            .onTapGesture(perform: open)
        }
    }
}

struct FullPlayer: View {
    @EnvironmentObject var player: Player
    @EnvironmentObject var lib: Lib
    let close: () -> Void
    @State private var drag: Double? = nil
    @State private var tab = 0          // 0 capa, 1 letra, 2 fila
    @State private var speedOpen = false
    @State private var timerOpen = false
    @State private var pick = false
    var body: some View {
        let t = player.current
        ZStack {
            Color.black.ignoresSafeArea()
            if let t { Art(t.art, radius: 0).scaledToFill().blur(radius: 60).opacity(0.55).ignoresSafeArea() }
            LinearGradient(colors: [.clear, .black.opacity(0.7)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 14) {
                HStack {
                    Button(action: close) { Image(systemName: "chevron.down").font(.title2.weight(.semibold)).frame(width: 44, height: 44) }
                    Spacer()
                    Text(t?.srcName.uppercased() ?? "").font(.caption.weight(.heavy)).kerning(1.2).foregroundStyle(.white.opacity(0.8))
                    Spacer()
                    if t?.src == "yt" { Button { lib.d.settings.showVideo.toggle(); lib.save() } label: { Image(systemName: lib.d.settings.showVideo ? "video.fill" : "video.slash").frame(width: 44, height: 44) } }
                    else { Color.clear.frame(width: 44, height: 44) }
                }
                Picker("", selection: $tab) { Text("Tocando").tag(0); Text("Letra").tag(1); Text("Fila").tag(2) }.pickerStyle(.segmented).padding(.horizontal, 30)
                Group {
                    if tab == 1, let t { LyricsView(t: t).frame(maxHeight: .infinity) }
                    else if tab == 2 { QueueView().frame(maxHeight: .infinity) }
                    else if let t {
                        if player.engine == .web && lib.d.settings.showVideo { Color.clear.aspectRatio(16 / 9, contentMode: .fit).frame(maxHeight: .infinity) }
                        else { Art(t.art, radius: 18).aspectRatio(1, contentMode: .fit).scaleEffect(player.playing ? 1 : 0.86).animation(.spring(response: 0.4), value: player.playing).shadow(radius: 24).padding(.horizontal, 26).frame(maxHeight: .infinity) }
                    }
                }
                if let t {
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(t.src == "radio" && !player.nowTitle.isEmpty ? player.nowTitle : t.title).font(.title3.bold()).lineLimit(1)
                            Text(t.src == "radio" ? t.title : t.artist).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
                        }
                        Spacer()
                        Button { lib.toggleFav(t) } label: { Image(systemName: lib.isFav(t) ? "heart.fill" : "heart").font(.title2).foregroundStyle(lib.isFav(t) ? Color.accentColor : .white) }
                    }.padding(.horizontal, 24)
                    if t.src == "radio" { Text("AO VIVO").font(.caption.weight(.heavy)).foregroundStyle(.red).frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 24) }
                    else {
                        VStack(spacing: 2) {
                            Slider(value: Binding(get: { drag ?? min(player.time, max(player.dur, 1)) }, set: { drag = $0 }), in: 0...max(player.dur, 1)) { e in if !e, let d = drag { player.seek(d); drag = nil } }
                            HStack { Text(fmtTime(drag ?? player.time)); Spacer(); Text("-" + fmtTime(max(0, player.dur - (drag ?? player.time)))) }.font(.caption.monospacedDigit()).foregroundStyle(.white.opacity(0.7))
                        }.padding(.horizontal, 24)
                    }
                    HStack {
                        Button { player.toggleShuffle() } label: { Image(systemName: "shuffle").foregroundStyle(player.shuffle ? Color.accentColor : .white.opacity(0.7)) }
                        Spacer()
                        Button { player.prev() } label: { Image(systemName: "backward.fill").font(.system(size: 30)) }
                        Spacer()
                        Button { player.toggle() } label: { Image(systemName: player.playing ? "pause.circle.fill" : "play.circle.fill").font(.system(size: 72)) }
                        Spacer()
                        Button { player.next() } label: { Image(systemName: "forward.fill").font(.system(size: 30)) }
                        Spacer()
                        Button { player.repeatMode = (player.repeatMode + 1) % 3 } label: { Image(systemName: player.repeatMode == 2 ? "repeat.1" : "repeat").foregroundStyle(player.repeatMode > 0 ? Color.accentColor : .white.opacity(0.7)) }
                    }.font(.title3).padding(.horizontal, 26)
                    HStack(spacing: 0) {
                        tool("speedometer", String(format: "%.2fx", lib.d.settings.speed), lib.d.settings.speed != 1) { speedOpen = true }
                        tool("moon.zzz", player.sleepAt.map { "\(max(1, Int($0.timeIntervalSinceNow / 60))) min" } ?? "Timer", player.sleepAt != nil) { timerOpen = true }
                        tool("text.badge.plus", "Playlist", false) { pick = true }
                        tool("dot.radiowaves.left.and.right", "Parecidas", false) { Task { await player.radio(from: t, append: false) } }
                        if let u = URL(string: t.link), !t.link.isEmpty { ShareLink(item: u) { VStack(spacing: 4) { Image(systemName: "square.and.arrow.up").font(.title3); Text("Enviar").font(.caption2) }.frame(maxWidth: .infinity).foregroundStyle(.white.opacity(0.8)) } }
                    }.padding(.horizontal, 12).padding(.bottom, 8)
                }
            }
            .foregroundStyle(.white)
        }
        .preferredColorScheme(.dark)
        .gesture(DragGesture().onEnded { v in if v.translation.height > 120 { close() } })
        .sheet(isPresented: $speedOpen) { SpeedSheet().presentationDetents([.height(300)]) }
        .confirmationDialog("Desligar a música em…", isPresented: $timerOpen, titleVisibility: .visible) {
            ForEach([5, 15, 30, 45, 60, 90], id: \.self) { m in Button("\(m) minutos") { player.sleep(minutes: m) } }
            if player.sleepAt != nil { Button("Desligar o timer", role: .destructive) { player.sleep(minutes: nil) } }
        }
        .sheet(isPresented: $pick) { if let t { PlaylistPicker(t: t) } }
    }
    func tool(_ icon: String, _ label: String, _ on: Bool, _ tap: @escaping () -> Void) -> some View {
        Button(action: tap) { VStack(spacing: 4) { Image(systemName: icon).font(.title3); Text(label).font(.caption2).lineLimit(1) }.frame(maxWidth: .infinity).foregroundStyle(on ? Color.accentColor : .white.opacity(0.8)) }
    }
}

struct SpeedSheet: View {
    @EnvironmentObject var player: Player
    @EnvironmentObject var lib: Lib
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Velocidade e tom").font(.title2.bold())
            Text(lib.d.settings.pitchWithSpeed ? "O tom muda junto (slowed / nightcore)" : "Só fica mais rápido ou mais lento").font(.footnote).foregroundStyle(.secondary)
            Slider(value: Binding(get: { lib.d.settings.speed }, set: { player.setSpeed((($0 * 20).rounded()) / 20) }), in: 0.5...1.5)
            ScrollView(.horizontal, showsIndicators: false) { HStack {
                ForEach([(0.75, "Lento"), (0.85, "Slowed"), (1.0, "Normal"), (1.15, "Sped up"), (1.25, "Nightcore")], id: \.0) { v, n in Chip(text: n, on: abs(lib.d.settings.speed - v) < 0.01) { player.setSpeed(v) } }
            } }
            Toggle("Mudar o tom junto", isOn: Binding(get: { lib.d.settings.pitchWithSpeed }, set: { lib.d.settings.pitchWithSpeed = $0; lib.save(); player.setSpeed(lib.d.settings.speed) }))
            Text("No YouTube o tom fica igual (o player dele não deixa mudar).").font(.caption).foregroundStyle(.secondary)
        }.padding(22)
    }
}

struct LyricsView: View {
    @EnvironmentObject var player: Player
    @EnvironmentObject var lib: Lib
    let t: Track
    @State private var ly: Lyrics.L? = nil
    @State private var loaded = ""
    @State private var busy = false
    var body: some View {
        Group {
            if busy { ProgressView().tint(.white) }
            else if let ly, !ly.synced.isEmpty {
                ScrollViewReader { sv in
                    ScrollView {
                        let cur = ly.synced.lastIndex { $0.0 <= player.time + 0.25 } ?? -1
                        VStack(alignment: .leading, spacing: 14) {
                            ForEach(Array(ly.synced.enumerated()), id: \.offset) { i, l in
                                Text(l.1.isEmpty ? "♪" : l.1).font(.system(size: 24, weight: .bold)).foregroundStyle(i == cur ? .white : .white.opacity(0.35)).id(i)
                                    .onTapGesture { player.seek(l.0) }
                            }
                            Text(ly.source).font(.caption).foregroundStyle(.white.opacity(0.5))
                        }.padding(.horizontal, 24).padding(.vertical, 40)
                        .onChange(of: cur) { c in withAnimation { sv.scrollTo(max(0, c), anchor: .center) } }
                    }
                }
            } else if let p = ly?.plain {
                ScrollView { VStack(alignment: .leading, spacing: 10) { Text(p).font(.system(size: 20, weight: .semibold)); Text("Letra: \(ly?.source ?? "")").font(.caption).foregroundStyle(.white.opacity(0.5)) }.padding(24) }
            } else {
                VStack(spacing: 12) {
                    Text("Sem letra por aqui").font(.title3.bold())
                    Text("Pode ser instrumental, muito nova ou ainda não ter letra publicada.").font(.footnote).foregroundStyle(.white.opacity(0.7)).multilineTextAlignment(.center)
                    Button("Tentar a sincronizada") { Task { busy = true; ly = await Lyrics.get(t, synced: true); busy = false } }.buttonStyle(.bordered)
                }.padding(30)
            }
        }
        .task(id: t.key) { if loaded != t.key { loaded = t.key; busy = true; ly = await Lyrics.get(t, synced: lib.d.settings.lyricsSynced); busy = false } }
    }
}

struct QueueView: View {
    @EnvironmentObject var player: Player
    var body: some View {
        ScrollViewReader { sv in
            List {
                ForEach(Array(player.queue.enumerated()), id: \.offset) { i, t in
                    HStack(spacing: 10) {
                        Art(t.art, radius: 5).frame(width: 40, height: 40)
                        VStack(alignment: .leading) { Text(t.title).lineLimit(1).foregroundStyle(i == player.idx ? Color.accentColor : .white); Text(t.artist).font(.caption).foregroundStyle(.white.opacity(0.6)).lineLimit(1) }
                    }.id(i).listRowBackground(Color.clear).contentShape(Rectangle()).onTapGesture { player.playAt(i) }
                }
                .onDelete { s in for i in s.sorted(by: >) where i != player.idx { player.queue.remove(at: i); if i < player.idx { player.idx -= 1 } } }
                .onMove { from, to in let cur = player.current; player.queue.move(fromOffsets: from, toOffset: to); if let cur, let k = player.queue.firstIndex(of: cur) { player.idx = k } }
            }
            .listStyle(.plain).scrollContentBackground(.hidden)
            .onAppear { sv.scrollTo(max(0, player.idx), anchor: .top) }
        }
    }
}
