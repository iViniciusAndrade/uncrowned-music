# Uncrowned Music — contexto pra continuar

App de música pessoal do Vinicius (Android em `app/`, Kotlin + Compose + Media3). Também existe uma versão de PC (Electron). Mudanças pedidas "só no mobile" não mexem no PC. Celular de teste: Poco X7 Pro. Tudo em português do Brasil, textos de UI descontraídos.

## Build e entrega
- O APK sai pelo GitHub Actions (`.github/workflows/build.yml`) a cada push na `main`. Cada build vira uma release `build-N` com o `.apk`. O EXE do PC sai pelo `desktop.yml` (push em `desktop/**`) como release `pc-N`.
- `versionCode` = `GITHUB_RUN_NUMBER` (o build N). No PC o número vai em `desktop/build.json`. A mensagem do commit vira o texto da release, que aparece no aviso de atualização dos apps: escreva commits com linhas curtas em português dizendo o que mudou pro usuário.
- Atualização automática: foi feita no build 101 e TIRADA a pedido do dono (04/11/2026). Os apps não procuram nem instalam versão nova sozinhos; ele baixa da página de Releases. Não colocar de volta sem ele pedir.
- Pra baixar o asset use a API REST (`gh api ... -H "Accept: application/octet-stream"`). O GraphQL do `gh` não funciona nesse ambiente.
- Assinatura: existe uma chave fixa criada em 02/11/2026 (o dono tem o .jks), mas ele ainda não colocou os segredos `KEYSTORE_B64`/`KEYSTORE_PASS` no GitHub; até lá cada build sai com chave debug aleatória e o Android não instala por cima. O repositório é público: nunca coloque chave ou segredo no código.
- O build gera só `arm64-v8a` (a IA local traz biblioteca nativa). R8 ligado; as regras ficam em `app/proguard-rules.pro`.
- Os commits terminam com as linhas de atribuição do Claude.

## iPhone (`ios/`)
- App nativo em SwiftUI (iOS 16+), projeto gerado pelo XcodeGen (`ios/project.yml`). Compila no GitHub Actions (`.github/workflows/ios.yml`, Mac na nuvem) sem assinatura e sai como release `ios-N` com `UncrownedMusic.ipa`. Instala por sideload (Sideloadly/AltStore com Apple ID grátis, reassinar a cada 7 dias). O iPhone é de outra pessoa (não do dono).
- Não dá pra compilar Swift aqui: os erros vêm do log do CI (o passo de build imprime as linhas `error:`).
- `Player.swift`: AVPlayer (SoundCloud, Audius, Archive, rádio com nome da música, arquivos) + YouTube no iframe oficial num WKWebView (`YTWeb`, origem uncrowned.app) que fica sempre montado no `Root`. Tela apagada com YouTube: troca pro SoundCloud nativo do mesmo ponto (`didEnterBackground`, match pré-buscado). Tela de bloqueio via MPNowPlayingInfoCenter/MPRemoteCommandCenter.
- `Store.swift` (`Lib`): tudo num JSON em Application Support. `Offline.swift`: pasta Documentos (aparece no app Arquivos), importar dos Arquivos, guardar Audius/Archive (secreto: 5 toques no tema atual).
- Mesma lógica portada: `YouTube.swift` (YT + YTM), `Sources.swift` (SoundCloud, Audius, Archive, Deezer, letras, iTunes, rádios, importar Spotify/YouTube), `Weekly.swift` (Descobertas v3), `PlaylistViews.swift` (playlists personalizáveis), temas com troca de ícone (ícones alternativos no Assets).
- Ficou de fora no iPhone (limite do iOS ou peso): IA local/assistente, navegador privado, efeitos K7/Vinil/Black Metal e equalizador, recortes, Momentos, exportar slowed.

## Arquitetura (app/src/main/java/com/uncrowned/music)
- `MainActivity.kt`: `PlayerUi` (MediaController), tema, rede caiu → `swapLocal()` toca a cópia local, `Crash.install`.
- `PlaybackService.kt`: ExoPlayer + `RetroProcessor`, watchdog e tique das estatísticas (`Listen`).
- `ScPlay.kt`: dono da fila online. Desde o build 101 o YouTube é o motor principal: música do YouTube toca no player do YouTube (`R.Web`). Só com `YtStore.scFirst` ligado (Ajustes → Online) ela é procurada no SoundCloud e toca no ExoPlayer. Busca (`Online.kt`) e paradas mostram YouTube primeiro.
- `Weekly.kt`: Descobertas da Semana v3 (perfil de gosto → artistas parecidos pelo Deezer + Mix do YouTube + lado B, com motivo por música). O PC tem a mesma lógica em `recommend()` no `main.js` + `tasteProfile()` no `app.js`.
- `PlLook.kt`: personalização das playlists (foto recortada em `filesDir/plcovers`, cor, enfeite, estampa, fonte, formato, descrição). Chave `op:nome` (online) / `lp:nome` (offline). No PC: `D.plLook` + fotos em `userData/plcovers` servidas em `/plc/`.
- `YouTube.kt`: `YtPlayer` (WebView do embed/página, anti-pausa, velocidade, recortes) e `YtPlayerBar`. Origem `https://uncrowned.app` (o base youtube.com dá erro 152).
- `WebPlayService.kt`: serviço em primeiro plano + notificação do player web (sempre chama `startForeground`, senão o Android fecha o app).
- `AdBlock.kt`, `GlobalFx.kt` (efeitos no player web), `Retro.kt` (K7, Vinil e Black Metal 1ª onda: DSP em Kotlin + Web Audio).
- `Sync.kt`/`SyncUi.kt`: "Salvar" só pega música com download liberado (SoundCloud downloadable, Audius, Archive, ccMixter, Wikimedia). Também tem "Guardadas no app" (Audius). Nada de extrair áudio do YouTube/SoundCloud: isso foi recusado várias vezes e continua fora.
- `Eco.kt`: modos de economia (auto/sempre/extremo/nunca) e `Secret`: as opções de salvar ficam escondidas e liberam com 5 toques no tema atual em Ajustes.
- `Assist.kt`: tela Assistente (Buscar → Assistente), microfone (`rememberVoice`) e comandos por regra. A IA local roda via MediaPipe `tasks-genai` com modelos opcionais de baixar/apagar: Qwen2.5 0.5B, Qwen2.5 1.5B e Phi-4 mini. O assistente faz pesquisa na web (DuckDuckGo HTML + Wikipédia), gera imagem via Pollinations (chave do usuário, salva só no celular) e baixa arquivo de link direto (bloqueia sites de streaming). Os pedidos rodam num escopo do app, então continuam fora da tela.
- `Browser.kt`: navegador privado (opção secreta, Ajustes → Secreto). `BrowserActivity` roda no processo `:nav` com `WebView.setDataDirectorySuffix("nav")`; a pasta é apagada ao abrir e ao fechar e o processo é encerrado. DNS via DoH da Cloudflare (1.1.1.1/1.0.0.1, JSON) num proxy local (`DohProxy`) ligado pelo `ProxyController` do androidx.webkit. Sem cache e sem permissões. Abas, página inicial fixa ou conjunto de páginas (prefs `nav_home`), sites no escuro (algorithmic darkening) e downloads (`NavDl`, pelo proxy, em Download/Uncrowned). Bloqueador bruto em `NavBlock.kt`: lista embutida + StevenBlack (semanal, `filesDir/navblock_hosts.txt`), corta no `shouldInterceptRequest` e no proxy, por caminho, CSS de esconder anúncio, anti pop-up e scripts de anúncio do YouTube.
- `Nag.kt`: notificações de cobrança (WorkManager). `Cuts.kt`: recortes. `Listen.kt`/`StatsUi.kt`: estatísticas.

## Histórico recente
- build-97: navegador privado nas opções secretas.
- build-98: revisão de bugs (fila do SoundCloud, caches entre threads, vazamentos, trava de tela do player web).
- build-99: navegador com abas, página inicial/conjunto, sites no escuro, downloads e bloqueador bruto; descrição do navegador removida de Ajustes.
- build-101 / PC: YouTube motor principal no celular, playlists personalizáveis (PC e celular), Descobertas da Semana v3, PC: player do YouTube toca com 1 clique e não trava mais depois de dias aberto (página do YouTube renovada a cada 30 trocas/3 h/quando pesa ou trava, vigia de janela travada, gravação do store assíncrona).

## Pendências pedidas
- Navegador simples dentro da aba Descobrir, com página inicial personalizável (não precisa ser privado nem escondido).
- Opção de excluir músicas locais do celular (menu "…" da música, usando `MediaStore.createDeleteRequest` com confirmação do Android).
