import Foundation
import AVFoundation
import MediaPlayer
import UIKit
import WebKit

/* ---------- o player ----------
   Dois motores numa fila só:
   - nativo (AVPlayer): SoundCloud, Audius, Archive, rádio e arquivos do iPhone. Toca com a tela apagada,
     aparece na tela de bloqueio e aceita velocidade com o tom junto (slowed / nightcore).
   - YouTube (player oficial dentro do app). O iOS corta o som dele com a tela apagada, então, quando isso
     acontece, a mesma música continua pelo SoundCloud no player nativo, do mesmo ponto (se existir lá). */
@MainActor
final class Player: NSObject, ObservableObject {
    static let shared = Player()
    enum Engine { case none, native, web }
    @Published var queue: [Track] = []
    @Published var idx = -1
    @Published var playing = false
    @Published var time: Double = 0
    @Published var dur: Double = 0
    @Published var loading: String? = nil
    @Published var engine: Engine = .none
    @Published var nowTitle = ""            // música tocando na rádio
    @Published var sleepAt: Date? = nil
    @Published var repeatMode = 0           // 0 não, 1 a fila, 2 esta
    @Published var shuffle = false
    @Published var showingVideo = false
    @Published var toast: String? = nil
    var current: Track? { queue.indices.contains(idx) ? queue[idx] : nil }

    let av = AVPlayer()
    let web = YTWeb()
    private var timeObs: Any?
    private var gen = 0
    private var startedAt = Date()
    private var lastTick = Date()
    private var scCache: [String: Track] = [:]     // vídeo do YouTube -> mesma música no SoundCloud
    private var meta: AVPlayerItemMetadataOutput?
    private var userPaused = false

    override init() {
        super.init()
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [])
        try? AVAudioSession.sharedInstance().setActive(true)
        av.automaticallyWaitsToMinimizeStalling = true
        timeObs = av.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] t in
            Task { @MainActor in self?.nativeTick(t.seconds) }
        }
        NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main) { [weak self] n in
            let it = n.object as? AVPlayerItem
            Task { @MainActor in if let it, it === self?.av.currentItem { self?.ended() } }
        }
        NotificationCenter.default.addObserver(forName: .AVPlayerItemFailedToPlayToEndTime, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.say("Não deu pra tocar essa. Pulando…"); self?.next() }
        }
        NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] n in
            guard let raw = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt, AVAudioSession.InterruptionType(rawValue: raw) == .ended,
                  let o = n.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt, AVAudioSession.InterruptionOptions(rawValue: o).contains(.shouldResume) else { return }
            Task { @MainActor in if self?.userPaused == false { self?.resume() } }
        }
        web.onState = { [weak self] s in self?.webState(s) }
        web.onTime = { [weak self] t, d in self?.webTime(t, d) }
        web.onError = { [weak self] _ in self?.say("O YouTube não liberou esse vídeo. Pulando…"); self?.next() }
        remote()
    }

    func say(_ s: String) { toast = s; let k = s; DispatchQueue.main.asyncAfter(deadline: .now() + 2.6) { [weak self] in if self?.toast == k { self?.toast = nil } } }

    // ---------- fila ----------
    func play(_ list: [Track], _ i: Int) {
        guard list.indices.contains(i) else { return }
        var l = list; var k = i
        if shuffle { let t = l.remove(at: i); l.shuffle(); l.insert(t, at: 0); k = 0 }
        queue = Array(l.prefix(500)); playAt(k)
    }
    func playNext(_ t: Track) { if queue.isEmpty { play([t], 0) } else { queue.insert(t, at: idx + 1); say("Vai tocar a seguir") } }
    func enqueue(_ t: Track) { if queue.isEmpty { play([t], 0) } else { queue.append(t); say("Adicionada à fila") } }

    func playAt(_ i: Int, startAt: Double = 0) {
        guard queue.indices.contains(i) else { return }
        // gosto: pulou rápido = não curtiu
        if let prev = current, prev.src != "radio", Date().timeIntervalSince(startedAt) < 30, idx != i { Lib.shared.onSkip(prev) }
        idx = i; gen += 1; let g = gen
        let t = queue[i]
        userPaused = false; time = startAt; dur = Double(t.duration) / 1000; nowTitle = ""; startedAt = Date()
        Lib.shared.onPlay(t)
        nowPlaying()
        Task { await start(t, g, startAt) }
    }
    private func start(_ t: Track, _ g: Int, _ at: Double) async {
        loading = t.title
        defer { if g == gen { loading = nil } }
        // guardada no iPhone: toca do arquivo (sem internet)
        if let f = Offline.file(for: t) { playNative(f, t, at); return }
        switch t.src {
        case "local": playNative(URL(fileURLWithPath: t.stream), t, at)
        case "radio": playNative(URL(string: t.stream), t, 0); Radio.click(t.id); watchMeta()
        case "au", "ia": playNative(URL(string: t.stream), t, at)
        case "sc":
            if let s = await SC.stream(t.id) { guard g == gen else { return }; if dur == 0 { dur = Double(s.duration) / 1000 }; playNative(URL(string: s.url), t, at) }
            else { guard g == gen else { return }; say("\"\(t.title)\" não toca inteira no SoundCloud. Indo pra próxima"); next() }
        default: // YouTube
            let s = Lib.shared.s
            if s.scFirst && !s.showVideo, let m = await scFor(t), let st = await SC.stream(m.id) {
                guard g == gen else { return }; playNative(URL(string: st.url), t, at); return
            }
            guard g == gen else { return }
            av.pause(); av.replaceCurrentItem(with: nil)
            engine = .web; web.load(t.id, start: at, speed: s.speed)
            // já procura a versão do SoundCloud: se a tela apagar, troca na hora
            if s.bgSwitch { Task { _ = await scFor(t) } }
        }
    }
    private func scFor(_ t: Track) async -> Track? {
        if let c = scCache[t.id] { return c.id.isEmpty ? nil : c }
        let m = await SC.match(t)
        scCache[t.id] = m ?? Track(src: "sc", id: "", title: "", artist: "")
        if scCache.count > 300 { scCache.removeAll() }
        return m
    }
    private func playNative(_ url: URL?, _ t: Track, _ at: Double) {
        guard let url else { next(); return }
        web.pause()
        engine = .native
        let item = AVPlayerItem(url: url)
        item.audioTimePitchAlgorithm = Lib.shared.s.pitchWithSpeed ? .varispeed : .timeDomain
        av.replaceCurrentItem(with: item)
        if at > 1 { av.seek(to: CMTime(seconds: at, preferredTimescale: 600)) }
        try? AVAudioSession.sharedInstance().setActive(true)
        av.playImmediately(atRate: Float(t.src == "radio" ? 1 : Lib.shared.s.speed))
        playing = true; nowPlaying()
    }
    private func watchMeta() {
        guard let item = av.currentItem else { return }
        let m = AVPlayerItemMetadataOutput(identifiers: nil)
        m.setDelegate(self, queue: .main)
        item.add(m); meta = m
    }

    // ---------- controles ----------
    func toggle() {
        guard current != nil else { return }
        if engine == .none { playAt(idx); return }
        if playing { pause() } else { resume() }
    }
    func pause() {
        userPaused = true
        if engine == .web { web.pause() } else { av.pause() }
        playing = false; nowPlaying()
    }
    func resume() {
        userPaused = false
        if engine == .web { web.play() }
        else if current?.src == "radio", let t = current { playNative(URL(string: t.stream), t, 0); watchMeta() }
        else { av.rate = Float(Lib.shared.s.speed) }
        playing = true; nowPlaying()
    }
    func next() {
        if repeatMode == 1 && idx >= queue.count - 1 { playAt(0); return }
        if idx < queue.count - 1 { playAt(idx + 1) } else if let t = current, Lib.shared.s.radioWhenEnds { Task { await radio(from: t, append: true) } } else { pause() }
    }
    func prev() { if time > 4 { seek(0) } else if idx > 0 { playAt(idx - 1) } else { seek(0) } }
    func seek(_ s: Double) {
        if engine == .web { web.seek(s) } else { av.seek(to: CMTime(seconds: s, preferredTimescale: 600)) }
        time = s; nowPlaying()
    }
    private func ended() {
        if let t = current, Date().timeIntervalSince(startedAt) > 30 { Lib.shared.onFinished(t) }
        if repeatMode == 2 { playAt(idx); return }
        if !Lib.shared.s.autoNext && current?.src == "yt" { pause(); return }
        next()
    }
    func setSpeed(_ v: Double) {
        Lib.shared.d.settings.speed = v; Lib.shared.save()
        if engine == .native && playing && current?.src != "radio" { av.currentItem?.audioTimePitchAlgorithm = Lib.shared.s.pitchWithSpeed ? .varispeed : .timeDomain; av.rate = Float(v) }
        if engine == .web { web.setSpeed(v) }
    }
    func toggleShuffle() {
        shuffle.toggle()
        if shuffle, queue.count > 2, let t = current { var rest = queue; rest.remove(at: idx); rest.shuffle(); queue = [t] + rest; idx = 0 }
    }
    func sleep(minutes: Int?) { sleepAt = minutes.map { Date().addingTimeInterval(Double($0) * 60) }; say(minutes.map { "Desliga em \($0) min" } ?? "Timer desligado") }

    /* rádio: quando a fila acaba (ou você pede), continua com parecidas pelo Mix do YouTube */
    func radio(from t: Track, append: Bool) async {
        if !append { say("Montando rádio de \(t.artist.isEmpty ? t.title : t.artist)…") }
        var vid = t.src == "yt" ? t.id : nil
        if vid == nil { vid = await YT.search("\(t.artist) \(t.title)").first?.id }
        let recent = Set(queue.suffix(60).map { $0.key })
        var l = vid == nil ? [] : await YT.mix(vid!).filter { !recent.contains($0.key) && $0.id != vid }
        if l.isEmpty { l = Offline.localRadio(seed: t) }
        guard !l.isEmpty else { if append { pause() } else { say("Não achei parecidas agora") }; return }
        if append { queue += Array(l.prefix(25)); playAt(idx + 1); say("Fila acabou: tocando parecidas") }
        else { queue.insert(contentsOf: l.prefix(25), at: idx + 1); say("Rádio: \(min(25, l.count)) parecidas na fila") }
    }

    // ---------- tela apagou / voltou ----------
    func didEnterBackground() {
        guard engine == .web, playing, let t = current, Lib.shared.s.bgSwitch else { return }
        let at = time, g = gen
        var bg: UIBackgroundTaskIdentifier = .invalid
        bg = UIApplication.shared.beginBackgroundTask { UIApplication.shared.endBackgroundTask(bg) }
        Task {
            defer { UIApplication.shared.endBackgroundTask(bg) }
            guard let m = await scFor(t), let st = await SC.stream(m.id), g == gen else { return }
            playNative(URL(string: st.url), t, at)
        }
    }

    // ---------- tempo ----------
    private func nativeTick(_ t: Double) {
        guard engine == .native else { return }
        if t.isFinite { time = t }
        if let d = av.currentItem?.duration.seconds, d.isFinite, d > 0 { dur = d }
        let p = av.rate > 0; if p != playing && !(p == false && av.timeControlStatus == .waitingToPlayAtSpecifiedRate) { playing = p; nowPlaying() }
        everySecond()
    }
    private func webTime(_ t: Double, _ d: Double) {
        guard engine == .web else { return }
        time = t; if d > 0 { dur = d }
        everySecond()
    }
    private func webState(_ s: Int) {
        guard engine == .web else { return }
        switch s {
        case 1: playing = true
        case 2: playing = false
        case 0: playing = false; ended()
        default: break
        }
        nowPlaying()
    }
    private func everySecond() {
        let now = Date()
        if now.timeIntervalSince(lastTick) >= 5 { if playing, let t = current { Lib.shared.tick(t, Int(now.timeIntervalSince(lastTick).rounded())) }; lastTick = now }
        if let s = sleepAt, now >= s { sleepAt = nil; pause(); say("Timer: música pausada") }
        if Int(time) % 5 == 0 { nowPlaying() }
    }

    // ---------- tela de bloqueio / central de controle ----------
    private func remote() {
        let c = MPRemoteCommandCenter.shared()
        c.playCommand.addTarget { [weak self] _ in self?.resume(); return .success }
        c.pauseCommand.addTarget { [weak self] _ in self?.pause(); return .success }
        c.togglePlayPauseCommand.addTarget { [weak self] _ in self?.toggle(); return .success }
        c.nextTrackCommand.addTarget { [weak self] _ in self?.next(); return .success }
        c.previousTrackCommand.addTarget { [weak self] _ in self?.prev(); return .success }
        c.changePlaybackPositionCommand.addTarget { [weak self] e in if let e = e as? MPChangePlaybackPositionCommandEvent { self?.seek(e.positionTime) }; return .success }
    }
    private var artKey = ""
    private var artImage: MPMediaItemArtwork?
    func nowPlaying() {
        guard let t = current else { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil; return }
        var i: [String: Any] = [MPMediaItemPropertyTitle: t.src == "radio" && !nowTitle.isEmpty ? nowTitle : t.title,
                                MPMediaItemPropertyArtist: t.src == "radio" ? t.title : t.artist,
                                MPNowPlayingInfoPropertyElapsedPlaybackTime: time,
                                MPNowPlayingInfoPropertyPlaybackRate: playing ? Lib.shared.s.speed : 0]
        if dur > 0 && t.src != "radio" { i[MPMediaItemPropertyPlaybackDuration] = dur }
        if t.src == "radio" { i[MPNowPlayingInfoPropertyIsLiveStream] = true }
        if artKey == t.key, let a = artImage { i[MPMediaItemPropertyArtwork] = a }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = i
        if artKey != t.key, let u = t.artURL {
            artKey = t.key; artImage = nil
            Task { [weak self] in
                let data: Data? = u.isFileURL ? try? Data(contentsOf: u) : try? await Net.data(u.absoluteString)
                guard let data, let img = UIImage(data: data), let self, self.artKey == t.key else { return }
                self.artImage = MPMediaItemArtwork(boundsSize: img.size) { _ in img }
                self.nowPlaying()
            }
        }
    }
}

/* nome da música tocando na rádio (vem misturado no áudio) */
extension Player: AVPlayerItemMetadataOutputPushDelegate {
    nonisolated func metadataOutput(_ output: AVPlayerItemMetadataOutput, didOutputTimedMetadataGroups groups: [AVTimedMetadataGroup], from track: AVPlayerItemTrack?) {
        let title = groups.flatMap { $0.items }.first { $0.commonKey == .commonKeyTitle || ($0.identifier?.rawValue.contains("StreamTitle") ?? false) }?.stringValue
        Task { @MainActor in if let title, !title.isEmpty { self.nowTitle = title; self.nowPlaying() } }
    }
}

/* ---------- player do YouTube: o player oficial (iframe) numa página do próprio app ---------- */
@MainActor
final class YTWeb: NSObject, WKScriptMessageHandler {
    let view: WKWebView
    var onState: (@MainActor (Int) -> Void)?
    var onTime: (@MainActor (Double, Double) -> Void)?
    var onError: (@MainActor (Int) -> Void)?
    private var ready = false
    private var pending: String?

    override init() {
        let cfg = WKWebViewConfiguration()
        cfg.allowsInlineMediaPlayback = true
        cfg.mediaTypesRequiringUserActionForPlayback = []
        cfg.allowsPictureInPictureMediaPlayback = true
        view = WKWebView(frame: CGRect(x: 0, y: 0, width: 320, height: 180), configuration: cfg)
        super.init()
        cfg.userContentController.add(WeakHandler(self), name: "unc")
        view.isOpaque = false; view.backgroundColor = .black; view.scrollView.isScrollEnabled = false
        view.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        // o YouTube recusa (erro 152) player que diz ser o próprio youtube.com: o app se apresenta como uncrowned.app
        view.loadHTMLString(Self.html, baseURL: URL(string: "https://uncrowned.app"))
    }
    func load(_ id: String, start: Double, speed: Double) {
        let cmd = "player.loadVideoById({videoId:'\(id)',startSeconds:\(Int(start))});player.setPlaybackRate(\(speed))"
        if ready { js(cmd) } else { pending = cmd }
    }
    func play() { js("player.playVideo()") }
    func pause() { js("player&&player.pauseVideo&&player.pauseVideo()") }
    func seek(_ s: Double) { js("player.seekTo(\(s),true)") }
    func setSpeed(_ v: Double) { js("player.setPlaybackRate(\(v))") }
    private func js(_ c: String) { view.evaluateJavaScript("try{\(c)}catch(e){}", completionHandler: nil) }

    func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
        guard let o = m.body as? [String: Any], let e = o["e"] as? String else { return }
        switch e {
        case "ready": ready = true; if let p = pending { pending = nil; js(p) }
        case "state": onState?((o["v"] as? NSNumber)?.intValue ?? -1)
        case "time": onTime?((o["t"] as? NSNumber)?.doubleValue ?? 0, (o["d"] as? NSNumber)?.doubleValue ?? 0)
        case "err": onError?((o["v"] as? NSNumber)?.intValue ?? 0)
        default: break
        }
    }
    static let html = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style></head><body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script><script>
var player;function post(o){try{window.webkit.messageHandlers.unc.postMessage(o)}catch(e){}}
function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',playerVars:{autoplay:1,playsinline:1,rel:0,modestbranding:1,origin:location.origin},
events:{onReady:function(){post({e:'ready'})},onStateChange:function(e){post({e:'state',v:e.data})},onError:function(e){post({e:'err',v:e.data})}}});}
setInterval(function(){if(player&&player.getCurrentTime&&player.getPlayerState&&player.getPlayerState()===1){post({e:'time',t:player.getCurrentTime()||0,d:player.getDuration()||0})}},500);
</script></body></html>
"""
}
/* evita o WKWebView segurar o player pra sempre na memória */
final class WeakHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    init(_ t: WKScriptMessageHandler) { target = t }
    func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) { target?.userContentController(c, didReceive: m) }
}
