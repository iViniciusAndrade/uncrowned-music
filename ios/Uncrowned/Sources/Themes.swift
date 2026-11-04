import SwiftUI
import UIKit

/* ---------- temas (os mesmos do Android e do PC): cor, claro/escuro, fundo e ícone do app ---------- */
struct Skin: Identifiable, Hashable {
    let id: String; let name: String; let desc: String; let accent: String; let mode: String; let dots: [String]
    var color: Color { Color(hex: accent) }
    var scheme: ColorScheme { mode == "light" ? .light : .dark }
    var hasBg: Bool { id != "apagao" }
}
enum Skins {
    static let all: [Skin] = [
        Skin(id: "edgy", name: "Edgy", desc: "Dark, vermelho sangue e glitch", accent: "#ff1f3d", mode: "dark", dots: ["#050505", "#ff1f3d", "#f2f2f2"]),
        Skin(id: "fadinha", name: "Fadinha", desc: "Pastel, brilhinho e lacinho", accent: "#ff5fa8", mode: "light", dots: ["#ffd6ec", "#e3c9ff", "#ff5fa8"]),
        Skin(id: "otaku", name: "Otaku", desc: "Neon, sakura e anime", accent: "#ff3fa4", mode: "dark", dots: ["#1a1240", "#ff3fa4", "#25f4ee"]),
        Skin(id: "oldmoney", name: "Old Money", desc: "Linho, marinho, caramelo e cartola", accent: "#a8763e", mode: "light", dots: ["#f4eee1", "#1d2b44", "#b5895a"]),
        Skin(id: "alternativo", name: "Indie", desc: "Terroso, granulado e fone", accent: "#e76f51", mode: "dark", dots: ["#1d2b2a", "#e9c46a", "#e76f51"]),
        Skin(id: "punk", name: "Punk", desc: "Preto, verde neon e retícula", accent: "#39ff14", mode: "dark", dots: ["#0b0b0b", "#39ff14", "#ff2fa0"]),
        Skin(id: "pulseirinhas", name: "Pulseirinhas", desc: "Glitter colorido e miçangas", accent: "#c77dff", mode: "dark", dots: ["#5b2a86", "#b8336a", "#2b59c3"]),
        Skin(id: "ninja", name: "Ninja Renegado", desc: "Preto, carmesim, shurikens e kunais", accent: "#d62839", mode: "dark", dots: ["#0a0809", "#8c0f1c", "#f3efe9"]),
        Skin(id: "deserto", name: "Deserto Estelar", desc: "Noite no deserto, ouro e turquesa", accent: "#2ec4b6", mode: "dark", dots: ["#0f1a3a", "#c8923a", "#2ec4b6"]),
        Skin(id: "ouro", name: "Ouro Italiano", desc: "Dourado, magenta e linhas de mangá", accent: "#f2b632", mode: "dark", dots: ["#3a0b2e", "#ff4fa3", "#f2b632"]),
        Skin(id: "cela", name: "Cela Esmeralda", desc: "Verde-pedra, correntes e jade", accent: "#3ddc97", mode: "dark", dots: ["#0e231c", "#2f6b55", "#3ddc97"]),
        Skin(id: "rock", name: "Rock Tóquio", desc: "Xadrez vermelho, renda e rosas", accent: "#e01e37", mode: "dark", dots: ["#120607", "#7a0b16", "#e8c547"]),
        Skin(id: "pavor", name: "Pavor", desc: "Terror pesado: olhos no escuro e arame", accent: "#c0001a", mode: "dark", dots: ["#050406", "#2a2433", "#c0001a"]),
        Skin(id: "apagao", name: "Apagão", desc: "Preto total, minimalista, sem cor", accent: "#e6e6e6", mode: "dark", dots: ["#000000", "#1a1a1a", "#e6e6e6"]),
        Skin(id: "julie", name: "Julie", desc: "Lilás fofinho, fitinha e florzinha", accent: "#9b6fd6", mode: "light", dots: ["#efe0ff", "#b48ef0", "#7d4fb0"]),
        Skin(id: "nino", name: "Nino", desc: "O gatinho vaquinha (milkmoo)", accent: "#ff7aa2", mode: "light", dots: ["#ffffff", "#1b1b1f", "#ff9bb8"]),
        Skin(id: "divino", name: "Divino", desc: "Todo branco com dourado", accent: "#b8902f", mode: "light", dots: ["#fffdf6", "#e8dcc0", "#c9a24a"]),
    ]
    static func get(_ id: String) -> Skin { all.first { $0.id == id } ?? all[0] }
    /* troca o ícone do app junto com o tema (o iPhone mostra um aviso rápido, é normal) */
    @MainActor static func applyIcon(_ id: String) {
        guard UIApplication.shared.supportsAlternateIcons else { return }
        UIApplication.shared.setAlternateIconName("Icon-\(id)") { _ in }
    }
}

extension Color {
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "#", with: "")
        if s.count == 3 { s = s.map { "\($0)\($0)" }.joined() }
        let v = UInt64(s, radix: 16) ?? 0xff1f3d
        self.init(red: Double((v >> 16) & 0xff) / 255, green: Double((v >> 8) & 0xff) / 255, blue: Double(v & 0xff) / 255)
    }
}

/* fundo do tema: a arte do tema bem escura atrás de tudo */
struct SkinBackground: View {
    @EnvironmentObject var lib: Lib
    var body: some View {
        let sk = Skins.get(lib.d.settings.skin)
        ZStack {
            (sk.scheme == .light ? Color(hex: sk.dots[0]) : Color.black).ignoresSafeArea()
            if sk.hasBg, UIImage(named: "bg-\(sk.id)") != nil {
                Image("bg-\(sk.id)").resizable().scaledToFill().ignoresSafeArea()
                    .opacity(sk.scheme == .light ? 0.22 : 0.18).blur(radius: 2)
            }
            LinearGradient(colors: [sk.color.opacity(0.22), .clear], startPoint: .top, endPoint: .center).ignoresSafeArea()
        }
    }
}
