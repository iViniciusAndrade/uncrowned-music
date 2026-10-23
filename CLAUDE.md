# Uncrowned Music — contexto pra continuar

App de música pessoal do Vinicius (Android em `app/`, Kotlin + Compose + Media3). Também existe uma versão de PC (Electron). Mudanças pedidas "só no mobile" não mexem no PC. Celular de teste: Poco X7 Pro. Tudo em português do Brasil, textos de UI descontraídos.

## Build e entrega
- O APK sai pelo GitHub Actions (`.github/workflows/build.yml`) a cada push na `main`. Cada build vira uma release `build-N` com o `.apk` (até agora o último foi o build-95).
- Pra baixar o asset use a API REST (`gh api ... -H "Accept: application/octet-stream"`). O GraphQL do `gh` não funciona nesse ambiente.
- Assinado com a chave debug. Existe código pra chave própria (`KEYSTORE_FILE`/`KEYSTORE_PASS`), mas o dono decidiu não usar por enquanto. O repositório é público, então nunca coloque chave ou segredo no código.
- O build gera só `arm64-v8a` (a IA local traz biblioteca nativa). R8 ligado; as regras ficam em `app/proguard-rules.pro`.
- Os commits terminam com as linhas de atribuição do Claude.

## Arquitetura (app/src/main/java/com/uncrowned/music)
- `MainActivity.kt`: `PlayerUi` (MediaController), tema, rede caiu → `swapLocal()` toca a cópia local, `Crash.install`.
- `PlaybackService.kt`: ExoPlayer + `RetroProcessor`, watchdog e tique das estatísticas (`Listen`).
- `ScPlay.kt`: dono da fila online. Toca stream aberto do SoundCloud no ExoPlayer, faixa protegida no widget do SC (WebView) e o que não está no SC no embed do YouTube. Usa cópia local quando existe e pula rápido quando está offline.
- `YouTube.kt`: `YtPlayer` (WebView do embed/página, anti-pausa, velocidade, recortes) e `YtPlayerBar`. Origem `https://uncrowned.app` (o base youtube.com dá erro 152).
- `WebPlayService.kt`: serviço em primeiro plano + notificação do player web (sempre chama `startForeground`, senão o Android fecha o app).
- `AdBlock.kt`, `GlobalFx.kt` (efeitos no player web), `Retro.kt` (K7, Vinil e Black Metal 1ª onda: DSP em Kotlin + Web Audio).
- `Sync.kt`/`SyncUi.kt`: "Salvar" só pega música com download liberado (SoundCloud downloadable, Audius, Archive, ccMixter, Wikimedia). Também tem "Guardadas no app" (Audius). Nada de extrair áudio do YouTube/SoundCloud: isso foi recusado várias vezes e continua fora.
- `Eco.kt`: modos de economia (auto/sempre/extremo/nunca) e `Secret`: as opções de salvar ficam escondidas e liberam com 5 toques no tema atual em Ajustes.
- `Assist.kt`: tela Assistente (Buscar → Assistente), microfone (`rememberVoice`) e comandos por regra. A IA local roda via MediaPipe `tasks-genai` com modelos opcionais de baixar/apagar: Qwen2.5 0.5B, Qwen2.5 1.5B e Phi-4 mini. O assistente faz pesquisa na web (DuckDuckGo HTML + Wikipédia), gera imagem via Pollinations (chave do usuário, salva só no celular) e baixa arquivo de link direto (bloqueia sites de streaming). Os pedidos rodam num escopo do app, então continuam fora da tela.
- `Browser.kt`: navegador privado (opção secreta, Ajustes → Secreto). `BrowserActivity` roda no processo `:nav` com `WebView.setDataDirectorySuffix("nav")`; a pasta é apagada ao abrir e ao fechar e o processo é encerrado. DNS via DoH da Cloudflare (1.1.1.1/1.0.0.1, JSON) num proxy local (`DohProxy`) ligado pelo `ProxyController` do androidx.webkit. Sem cache, sem downloads, sem permissões.
- `Nag.kt`: notificações de cobrança (WorkManager). `Cuts.kt`: recortes. `Listen.kt`/`StatsUi.kt`: estatísticas.

## Pendências pedidas
- Navegador simples dentro da aba Descobrir, com página inicial personalizável (não precisa ser privado nem escondido).
- Opção de excluir músicas locais do celular (menu "…" da música, usando `MediaStore.createDeleteRequest` com confirmação do Android).
