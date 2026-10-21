/* Uncrowned Music — PC */
'use strict';
const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = ms => { const s = Math.max(0, Math.floor((ms || 0) / 1000)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };
const toast = msg => { const t = $('#toast'); t.textContent = msg; t.classList.add('show'); clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove('show'), 2600); };

/* ---------------- ícones (Material Rounded) ---------------- */
const P = {
  play: 'M8 6.82v10.36c0 .79.87 1.27 1.54.84l8.14-5.18a1 1 0 0 0 0-1.69L9.54 5.98A.998.998 0 0 0 8 6.82z',
  pause: 'M8 19c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2s-2 .9-2 2v10c0 1.1.9 2 2 2zm6-12v10c0 1.1.9 2 2 2s2-.9 2-2V7c0-1.1-.9-2-2-2s-2 .9-2 2z',
  next: 'M5.58 16.89 11.17 13c.69-.48.69-1.51 0-1.99L5.58 7.11C4.89 6.62 4 7.12 4 7.92v7.15c0 .82.89 1.31 1.58.82zm7.42-.82v7.15M13 7.93v8.14c0 .8.89 1.29 1.58.82l5.59-3.89c.69-.48.69-1.51 0-1.99l-5.59-3.89c-.69-.48-1.58.01-1.58.81z',
  prev: 'M11 16.07V7.93c0-.81-.91-1.28-1.58-.82l-5.59 3.89c-.69.48-.69 1.51 0 1.99l5.59 3.89c.67.47 1.58 0 1.58-.81zm1.66-3.07 5.59 3.89c.67.46 1.58-.01 1.58-.82V7.93c0-.81-.91-1.28-1.58-.82L12.66 11c-.69.48-.69 1.52 0 2z',
  shuffle: 'M10.59 9.17 5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z',
  repeat: 'M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z',
  repeat1: 'M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z',
  heart: 'M13.35 20.13c-.76.69-1.93.69-2.69-.01l-.11-.1C5.3 15.27 1.87 12.16 2 8.28c.06-1.7.93-3.33 2.34-4.29 2.64-1.8 5.9-.96 7.66 1.1 1.76-2.06 5.02-2.91 7.66-1.1 1.41.96 2.28 2.59 2.34 4.29.14 3.88-3.3 6.99-8.55 11.76l-.1.09z',
  heartO: 'M19.66 3.99c-2.64-1.8-5.9-.96-7.66 1.1-1.76-2.06-5.02-2.91-7.66-1.1-1.4.96-2.28 2.58-2.34 4.29-.14 3.88 3.3 6.99 8.55 11.76l.1.09c.76.69 1.93.69 2.69-.01l.11-.1c5.25-4.76 8.68-7.87 8.55-11.75-.06-1.7-.94-3.32-2.34-4.28zM12.1 18.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z',
  lyrics: 'M14 17H4v2h10v-2zm6-8H4v2h16V9zM4 15h16v-2H4v2zM4 5v2h16V5H4z',
  info: 'M11 7h2v2h-2zm0 4h2v6h-2zm1-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z',
  speed: 'm20.38 8.57-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z',
  timer: 'M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61 1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42A8.962 8.962 0 0 0 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z',
  queue: 'M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z',
  video: 'M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z',
  vol: 'M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z',
  mute: 'M16.5 12A4.5 4.5 0 0 0 14 7.97v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51A8.796 8.796 0 0 0 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3 3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06a8.99 8.99 0 0 0 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4 9.91 6.09 12 8.18V4z',
  note: 'M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z',
  album: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 14.5c-2.49 0-4.5-2.01-4.5-4.5S9.51 7.5 12 7.5s4.5 2.01 4.5 4.5-2.01 4.5-4.5 4.5zm0-5.5c-.55 0-1 .45-1 1s.45 1 1 1 1-.45 1-1-.45-1-1-1z',
  person: 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v1c0 .55.45 1 1 1h14c.55 0 1-.45 1-1v-1c0-2.66-5.33-4-8-4z',
  explore: 'M12 10.9c-.61 0-1.1.49-1.1 1.1s.49 1.1 1.1 1.1c.61 0 1.1-.49 1.1-1.1s-.49-1.1-1.1-1.1zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm2.19 12.19L6 18l3.81-8.19L18 6l-3.81 8.19z',
  gear: 'M19.5 12c0-.23-.01-.45-.03-.68l1.86-1.41c.4-.3.51-.86.26-1.3l-1.87-3.23a.987.987 0 0 0-1.25-.42l-2.15.91c-.37-.26-.76-.49-1.17-.68l-.29-2.31c-.06-.5-.49-.88-.99-.88h-3.73c-.51 0-.94.38-1 .88l-.29 2.31c-.41.19-.8.42-1.17.68l-2.15-.91c-.46-.2-1-.02-1.25.42L2.41 8.62c-.25.44-.14.99.26 1.3l1.86 1.41a7.343 7.343 0 0 0 0 1.35l-1.86 1.41c-.4.3-.51.86-.26 1.3l1.87 3.23c.25.44.79.62 1.25.42l2.15-.91c.37.26.76.49 1.17.68l.29 2.31c.06.5.49.88.99.88h3.73c.5 0 .93-.38.99-.88l.29-2.31c.41-.19.8-.42 1.17-.68l2.15.91c.46.2 1 .02 1.25-.42l1.87-3.23c.25-.44.14-.99-.26-1.3l-1.86-1.41c.03-.23.04-.45.04-.68zm-7.46 3.5c-1.93 0-3.5-1.57-3.5-3.5s1.57-3.5 3.5-3.5 3.5 1.57 3.5 3.5-1.57 3.5-3.5 3.5z',
  list: 'M14 10H3v2h11v-2zm0-4H3v2h11V6zM3 16h7v-2H3v2zm11.41 6L17 19.41 19.59 22 21 20.59 18.41 18 21 15.41 19.59 14 17 16.59 14.41 14 13 15.41 15.59 18 13 20.59 14.41 22z',
  plist: 'M3 10h11v2H3v-2zm0-2h11V6H3v2zm0 8h7v-2H3v2zm15.01-3.13.71-.71a.996.996 0 0 1 1.41 0l.71.71c.39.39.39 1.02 0 1.41l-.71.71-2.12-2.12zm-.71.71-5.3 5.3V21h2.12l5.3-5.3-2.12-2.12z',
  cloud: 'M19.35 10.04A7.49 7.49 0 0 0 12 4C9.11 4 6.6 5.64 5.35 8.04A5.994 5.994 0 0 0 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z',
  history: 'M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z',
  more: 'M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  dl: 'M16.59 9H15V4c0-.55-.45-1-1-1h-4c-.55 0-1 .45-1 1v5H7.41c-.89 0-1.34 1.08-.71 1.71l4.59 4.59c.39.39 1.02.39 1.41 0l4.59-4.59c.63-.63.19-1.71-.7-1.71zM5 19c0 .55.45 1 1 1h12c.55 0 1-.45 1-1s-.45-1-1-1H6c-.55 0-1 .45-1 1z',
  plus: 'M18 13h-5v5c0 .55-.45 1-1 1s-1-.45-1-1v-5H6c-.55 0-1-.45-1-1s.45-1 1-1h5V6c0-.55.45-1 1-1s1 .45 1 1v5h5c.55 0 1 .45 1 1s-.45 1-1 1z',
  eq: 'M10 20h4V4h-4v16zm-6 0h4v-8H4v8zM16 9v11h4V9h-4z'
};
const ico = (n, cls = '') => `<svg viewBox="0 0 24 24" class="${cls}"><path d="${P[n]}"/></svg>`;

/* ---------------- estado e persistência ---------------- */
const DEF = {
  theme: 'dark', accent: '#fa2d48', radius: 12, fontScale: 1, density: 'normal', tile: 180,
  artShape: 'rounded', npBg: 'blur', blur: 70, artPulse: true, lyricsSize: 28, lyricsAlign: 'left', lyricsSource: 'letras', showLyricsNp: true,
  hideVoice: true, minDur: 30, speed: 1, pitchWithSpeed: false, volume: 0.8, autoNext: true, showVideo: true, videoSize: 360,
  adblock: true, caseStyle: 'random', eqOn: false, eq: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0], bass: 0, gainBoost: 0, vimeoToken: '', startView: 'songs', notifications: true
};
let D = { settings: { ...DEF }, folders: [], playlists: {}, favs: {}, history: [], taste: { g: {}, a: {} }, heard: [], weekly: null, lastQueue: null };
const S = () => D.settings;
let saveT;
function save() { clearTimeout(saveT); saveT = setTimeout(() => U.storeSet(D), 400); }

let LIB = [];          // músicas do PC
let view = { name: 'songs' };
const st = { queue: [], idx: -1, shuffle: false, repeat: 0, playing: false, kind: null, time: 0, dur: 0, sleepAt: 0 };
const cur = () => st.queue[st.idx];
const keyOf = t => t ? `${t.src}:${t.id}` : '';
const artOf = t => t ? (t.src === 'local' ? `/cover?p=${encodeURIComponent(t.path)}` : (t.art || '')) : '';
const isFav = t => !!(t && D.favs[keyOf(t)]);

/* ---------------- aparência ---------------- */
function applyLook() {
  const s = S(), r = document.documentElement;
  const sys = matchMedia('(prefers-color-scheme: light)').matches;
  r.classList.toggle('light', s.theme === 'light' || (s.theme === 'system' && sys));
  r.classList.toggle('black', s.theme === 'black');
  r.classList.toggle('compact', s.density === 'compact');
  r.style.setProperty('--accent', s.accent);
  r.style.setProperty('--r', s.radius + 'px');
  r.style.setProperty('--fs', (14 * s.fontScale) + 'px');
  r.style.setProperty('--tile', s.tile + 'px');
  r.style.setProperty('--blur', s.blur + 'px');
  r.style.setProperty('--lyr', s.lyricsSize + 'px');
  r.style.setProperty('--lyrAlign', s.lyricsAlign);
  r.style.setProperty('--vidW', s.videoSize + 'px');
  r.style.setProperty('--artR', s.artShape === 'square' ? '3px' : s.artShape === 'rounded' ? '14px' : '50%');
  U.titleColor(r.classList.contains('light') ? '#000000' : '#ffffff');
}

/* ---------------- motor de áudio (arquivos + streams) com equalizador ---------------- */
const audio = $('#audio');
const FREQS = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
let ctx, filters = [], bassF, gainN;
function initGraph() {
  if (ctx) return;
  try { buildGraph(); } catch (e) { console.warn('equalizador indisponível', e); ctx = { state: 'running', resume() {} }; filters = []; }
}
function buildGraph() {
  ctx = new AudioContext();
  const src = ctx.createMediaElementSource(audio);
  filters = FREQS.map((f, i) => { const b = ctx.createBiquadFilter(); b.type = i === 0 ? 'lowshelf' : i === 9 ? 'highshelf' : 'peaking'; b.frequency.value = f; b.Q.value = 1.1; return b; });
  bassF = ctx.createBiquadFilter(); bassF.type = 'lowshelf'; bassF.frequency.value = 90;
  gainN = ctx.createGain();
  let n = src; for (const f of [...filters, bassF, gainN]) { n.connect(f); n = f; }
  n.connect(ctx.destination);
  applyEq();
}
function applyEq() {
  if (!ctx || !filters.length) return;
  const s = S();
  filters.forEach((f, i) => f.gain.value = s.eqOn ? s.eq[i] : 0);
  bassF.gain.value = s.eqOn ? s.bass : 0;
  gainN.gain.value = Math.pow(10, (s.eqOn ? s.gainBoost : 0) / 20);
}
/* velocidade: no arquivo/stream muda de verdade (com ou sem o tom junto = slowed/nightcore).
   YouTube só aceita alguns valores e mantém o tom; SoundCloud não deixa mudar. */
let rateWarned = '';
function applyRate(user) {
  const s = S();
  const keep = !s.pitchWithSpeed;
  audio.preservesPitch = keep; audio.mozPreservesPitch = keep; audio.webkitPreservesPitch = keep;
  audio.defaultPlaybackRate = s.speed;
  if (audio.playbackRate !== s.speed) audio.playbackRate = s.speed;
  if (st.kind === 'yt' && yt.wv) ytExec(`(v=>{if(v){v.playbackRate=${s.speed};v.preservesPitch=${!s.pitchWithSpeed}}})(${V})`);
  if (st.kind === 'vm' && vm.player) vm.player.setPlaybackRate(s.speed).catch(() => {});
  if (st.kind === 'sc') scApplyRate();
}
audio.addEventListener('loadedmetadata', () => applyRate());
audio.addEventListener('playing', () => { if (audio.playbackRate !== S().speed) applyRate(); });
audio.addEventListener('timeupdate', () => { if (st.kind === 'audio') { st.time = audio.currentTime * 1000; st.dur = (audio.duration || 0) * 1000; tick(); } });
audio.addEventListener('play', () => setPlaying(true));
audio.addEventListener('pause', () => setPlaying(false));
audio.addEventListener('ended', () => ended());
audio.addEventListener('error', () => { if (st.kind === 'audio' && audio.src) { toast('Não deu pra tocar essa faixa. Pulando…'); setTimeout(next, 800); } });
// recupera sozinho se o áudio travar (tocando, mas o tempo não anda)
let lastT = -1, stuck = 0;
setInterval(() => {
  if (st.kind === 'audio' && !audio.paused && !audio.seeking) {
    stuck = audio.currentTime === lastT ? stuck + 1 : 0; lastT = audio.currentTime;
    if (stuck >= 4) { stuck = 0; const t = audio.currentTime; audio.load(); audio.currentTime = t; audio.play().catch(() => {}); applyRate(); }
  } else stuck = 0;
  if (st.sleepAt && Date.now() >= st.sleepAt) { st.sleepAt = 0; pause(); toast('Timer: música pausada'); }
}, 1000);

/* ---------------- players oficiais: YouTube / SoundCloud / Vimeo ---------------- */
const loadScript = src => new Promise((ok, bad) => { if (document.querySelector(`script[src="${src}"]`)) return ok(); const s = document.createElement('script'); s.src = src; s.onload = ok; s.onerror = bad; document.head.appendChild(s); });
const yt = { wv: null }, sc = { w: null }, vm = { player: null };
let pollT = null;
function startPoll(fn) { clearInterval(pollT); pollT = setInterval(fn, 400); }
function stopEmbeds(except) {
  if (except !== 'yt' && yt.wv) ytExec(`${V}&&${V}.pause()`);
  if (except !== 'sc' && sc.w) try { sc.w.pause(); } catch {}
  if (except !== 'vm' && vm.player) vm.player.pause().catch(() => {});
}
function embedHost(kind) {
  const inner = $('#embedInner');
  if (inner.dataset.kind !== kind) { inner.innerHTML = '<div id="embedTarget"></div>'; inner.dataset.kind = kind; yt.wv = null; yt.ready = false; sc.hooked = false; scRetry = 0; sc.w = null; vm.player = null; }
  $('#embed').classList.remove('hidden');
  layoutEmbed();
}
/* YouTube: página oficial do YouTube dentro do app, mostrando só o player.
   Toca tudo que toca no site (com login, inclusive vídeo +18) e aceita velocidade/tom. */
const YT_CSS = `html,body{overflow:hidden!important;background:#000!important}
#masthead-container,ytd-masthead,#secondary,#below,#comments,#related,ytd-watch-metadata,#chat,tp-yt-app-drawer,ytd-mini-guide-renderer,#guide,ytd-popup-container,tp-yt-iron-overlay-backdrop,ytd-consent-bump-v2-lightbox{display:none!important}
#movie_player,.html5-video-player{position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;z-index:2147483647!important;background:#000!important}
video.html5-main-video{width:100vw!important;height:100vh!important;left:0!important;top:0!important;object-fit:contain!important}
.ytp-chrome-top,.ytp-pause-overlay,.ytp-ce-element,.ytp-endscreen-content,.ytp-autonav-endscreen,.ytp-paid-content-overlay{display:none!important}`;
const ytExec = code => { try { return yt.wv && yt.ready ? yt.wv.executeJavaScript(code).catch(() => null) : Promise.resolve(null); } catch { return Promise.resolve(null); } };
const V = `(document.querySelector('video.html5-main-video')||document.querySelector('video'))`;
let ytEnded = false, ytWarned = '';
function ytWebview() {
  const inner = $('#embedInner');
  if (inner.dataset.kind !== 'yt') {
    inner.innerHTML = '<webview id="ytw" partition="persist:youtube" webpreferences="autoplayPolicy=no-user-gesture-required,backgroundThrottling=no" src="about:blank"></webview>';
    inner.dataset.kind = 'yt'; sc.w = null; vm.player = null;
    yt.wv = $('#ytw');
    yt.ready = false;
    yt.wv.addEventListener('dom-ready', () => { yt.ready = true; yt.wv.insertCSS(YT_CSS); yt.wv.setAudioMuted(false); ytExec("window.dispatchEvent(new Event('resize'))"); });
  }
  $('#embed').classList.remove('hidden');
  layoutEmbed();
}
async function playYT(t) {
  ytWebview();
  ytEnded = false; ytWarned = '';
  const url = `https://www.youtube.com/watch?v=${t.id}`;
  try { if (yt.ready) yt.wv.loadURL(url).catch(() => {}); else yt.wv.src = url; } catch { yt.wv.src = url; }
  startPoll(async () => {
    if (st.kind !== 'yt') return;
    const s = S();
    const r = await ytExec(`(()=>{const v=${V};
      const err=document.querySelector('.ytp-error, yt-playability-error-supported-renderers, #player-error-message-container');
      const errTxt=err&&err.offsetParent!==null?err.innerText.slice(0,160):'';
      if(!v)return {none:1,err:errTxt};
      const ad=!!document.querySelector('.ad-showing');
      const skip=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern'); if(skip) skip.click();
      if(${S().adblock} && ad){ v.muted=true; window.__uncAdMuted=true; if(isFinite(v.duration)&&v.duration>0) v.currentTime=Math.max(0,v.duration-0.05); try{v.playbackRate=16}catch(e){} }
      else if(window.__uncAdMuted){ v.muted=false; window.__uncAdMuted=false; }
      document.querySelectorAll('ytd-enforcement-message-view-model,tp-yt-paper-dialog:has(#dismiss-button)').forEach(d=>{const b=d.querySelector('#dismiss-button button,button');if(b)b.click();});
      if(!ad){ if(Math.abs(v.playbackRate-${s.speed})>0.01) v.playbackRate=${s.speed}; v.preservesPitch=${!s.pitchWithSpeed}; }
      if(Math.abs(v.volume-${S().volume})>0.02) v.volume=${S().volume};
      return {t:v.currentTime,d:v.duration||0,p:v.paused,e:v.ended,ad,err:errTxt,id:new URLSearchParams(location.search).get('v')};})()`);
    if (!r) return;
    if (r.err && ytWarned !== r.err) {
      ytWarned = r.err;
      const age = /idade|age|sign in|faça login|inapropriado|inappropriate/i.test(r.err);
      toast(age ? 'Vídeo +18: entre na sua conta do YouTube em Ajustes → YouTube pra liberar' : 'O YouTube não liberou esse vídeo. Pulando…');
      if (!age) setTimeout(next, 1500);
      return;
    }
    if (r.none) return;
    if (r.id && r.id !== t.id && !ytEnded) { ytEnded = true; return ended(); }   // o YouTube tentou pular sozinho
    if (!r.ad) { st.time = r.t * 1000; st.dur = r.d * 1000; tick(); }
    if (st.playing === r.p) setPlaying(!r.p);
    if (r.e && !ytEnded) { ytEnded = true; ended(); }
  });
}
async function playSC(t) {
  embedHost('sc');
  await loadScript('https://w.soundcloud.com/player/api.js');
  const url = 'https://soundcloud.com/' + t.id;
  if (sc.w) sc.w.load(url, { auto_play: true, visual: true, show_comments: false });
  else {
    $('#embedTarget').outerHTML = `<iframe id="scFrame" allow="autoplay" src="https://w.soundcloud.com/player/?url=${encodeURIComponent(url)}&auto_play=true&visual=true&show_comments=false"></iframe>`;
    sc.w = SC.Widget('scFrame');
    sc.w.bind(SC.Widget.Events.PLAY, async () => {
      setPlaying(true);
      // na 1ª vez, "pega" o áudio do player: aplica, pausa e toca de novo pra capturar o elemento de áudio
      if (!sc.hooked) { sc.hooked = true; if (S().speed !== 1) scApplyRate(); } else if (S().speed !== 1) scApplyRate();
    });
    sc.w.bind(SC.Widget.Events.PAUSE, () => setPlaying(false));
    sc.w.bind(SC.Widget.Events.FINISH, () => ended());
    sc.w.bind(SC.Widget.Events.READY, () => sc.w.setVolume(S().volume * 100));
  }
  startPoll(() => sc.w && sc.w.getPosition(p => sc.w.getDuration(d => { st.time = p; st.dur = d; tick(); })));
}
let scRetry = 0;
async function scApplyRate() {
  if (st.kind !== 'sc') return;
  const n = await U.scRate(S().speed, !S().pitchWithSpeed);
  // ainda não achou o áudio do player: pausa e toca de novo pra ele aparecer (uma vez)
  if (!n && sc.w && st.playing && scRetry < 2) { scRetry++; sc.w.pause(); setTimeout(() => { sc.w.play(); setTimeout(scApplyRate, 400); }, 150); }
  else if (n) scRetry = 0;
}
async function playVM(t) {
  embedHost('vm');
  await loadScript('https://player.vimeo.com/api/player.js');
  if (vm.player) await vm.player.loadVideo(t.id).then(() => vm.player.play()).catch(() => {});
  else {
    $('#embedTarget').outerHTML = `<iframe id="vmFrame" allow="autoplay; fullscreen" src="https://player.vimeo.com/video/${t.id}?autoplay=1"></iframe>`;
    vm.player = new Vimeo.Player($('#vmFrame'));
    vm.player.on('play', () => setPlaying(true)); vm.player.on('pause', () => setPlaying(false)); vm.player.on('ended', () => ended());
    vm.player.on('timeupdate', d => { st.time = d.seconds * 1000; st.dur = d.duration * 1000; tick(); });
    vm.player.setVolume(S().volume);
  }
  applyRate();
}

/* ---------------- controle unificado ---------------- */
async function playAt(i) {
  if (i < 0 || i >= st.queue.length) return;
  st.idx = i; const t = cur();
  st.time = 0; st.dur = t.duration || 0; lastT = -1;
  // SoundCloud: tenta tocar no player do app (velocidade/tom/equalizador funcionam); se não der, usa o player oficial
  let scUrl = null;
  if (t.src === 'sc') {
    const r = await U.scStream(t.id).catch(() => null);
    if (cur() !== t) return;
    if (r) { scUrl = r.url; if (!t.duration) t.duration = r.duration; if (!t.genre) t.genre = r.genre; if (!t.art && r.art) t.art = r.art; }
  }
  const embed = ['yt', 'vm'].includes(t.src) || (t.src === 'sc' && !scUrl);
  stopEmbeds(embed ? t.src : null);
  if (embed) {
    audio.pause(); st.kind = t.src;
    try { if (t.src === 'yt') await playYT(t); else if (t.src === 'sc') await playSC(t); else await playVM(t); }
    catch { toast('Sem conexão com o player online'); }
  } else {
    clearInterval(pollT); st.kind = 'audio';
    $('#embed').classList.add('hidden');
    initGraph(); if (ctx.state === 'suspended') ctx.resume();
    audio.src = t.src === 'local' ? `/media?p=${encodeURIComponent(t.path)}` : `/proxy?u=${encodeURIComponent(scUrl || t.stream)}`;
    applyRate();
    audio.play().catch(() => {});
  }
  onTrackChange(t);
}
function play(list, i) { st.queue = list.slice(); if (st.shuffle) { const t = st.queue.splice(i, 1)[0]; shuffleArr(st.queue); st.queue.unshift(t); i = 0; } playAt(i); }
function shuffleArr(a) { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; }
function toggle() {
  if (!cur()) return;
  if (!st.kind) return playAt(st.idx);
  if (st.kind === 'audio') audio.paused ? audio.play() : audio.pause();
  else if (st.kind === 'yt' && yt.wv) ytExec(`(v=>{if(v){v.paused?v.play():v.pause()}})(${V})`).then(() => setPlaying(!st.playing));
  else if (st.kind === 'sc' && sc.w) sc.w.toggle();
  else if (st.kind === 'vm' && vm.player) st.playing ? vm.player.pause() : vm.player.play();
}
function pause() { if (st.playing) toggle(); }
function seek(ms) {
  if (st.kind === 'audio') audio.currentTime = ms / 1000;
  else if (st.kind === 'yt' && yt.wv) ytExec(`${V}&&(${V}.currentTime=${ms / 1000})`);
  else if (st.kind === 'sc' && sc.w) sc.w.seekTo(ms);
  else if (st.kind === 'vm' && vm.player) vm.player.setCurrentTime(ms / 1000);
  st.time = ms; tick();
}
function next() { if (st.repeat === 1 && st.idx === st.queue.length - 1) return playAt(0); if (st.idx < st.queue.length - 1) playAt(st.idx + 1); }
function prev() { if (st.time > 4000) return seek(0); if (st.idx > 0) playAt(st.idx - 1); else seek(0); }
function ended() {
  if (st.repeat === 2) return playAt(st.idx);
  const online = ['yt', 'sc', 'vm'].includes(st.kind);
  if (online && !S().autoNext) return setPlaying(false);
  if (st.idx < st.queue.length - 1 || st.repeat === 1) next(); else setPlaying(false);
}
function setVolume(v) {
  S().volume = v; save();
  audio.volume = v;
  if (yt.wv) ytExec(`${V}&&(${V}.volume=${v})`);
  if (sc.w) sc.w.setVolume(v * 100);
  if (vm.player) vm.player.setVolume(v).catch(() => {});
  $('#vol').value = v; $('#bVol').innerHTML = ico(v === 0 ? 'mute' : 'vol');
}
function setPlaying(p) {
  st.playing = p;
  $('#bPlay').innerHTML = ico(p ? 'pause' : 'play');
  $('#np').classList.toggle('paused', !p);
  if ('mediaSession' in navigator) navigator.mediaSession.playbackState = p ? 'playing' : 'paused';
  markCurrent();
}

/* ---------------- gosto musical ---------------- */
let startedAt = 0, lastKey = null, lastMeta = null;
function bump(map, k, v) { if (!k) return; map[k] = (map[k] || 0) + v; }
function tasteDecay() {
  const w = weekKey();
  if (D.tasteWeek && D.tasteWeek !== w) for (const m of [D.taste.g, D.taste.a]) for (const k in m) m[k] *= 0.9;
  D.tasteWeek = w;
}
function onTrackChange(t) {
  if (lastKey && Date.now() - startedAt < 30000 && lastMeta) { bump(D.taste.g, lastMeta.genre, -0.5); bump(D.taste.a, lastMeta.artist, -1); }
  tasteDecay();
  bump(D.taste.g, t.genre, 1); bump(D.taste.a, t.artist, 1);
  const k = keyOf(t); if (!D.heard.includes(k)) { D.heard.push(k); if (D.heard.length > 3000) D.heard.shift(); }
  if (t.src !== 'local') { D.history = [t, ...D.history.filter(x => keyOf(x) !== k)].slice(0, 80); }
  lastKey = k; lastMeta = t; startedAt = Date.now();
  D.lastQueue = { q: st.queue.slice(0, 300), i: st.idx };
  save();
  updateNowPlaying();
}
function toggleFav(t) {
  const k = keyOf(t);
  if (D.favs[k]) delete D.favs[k]; else { D.favs[k] = t; bump(D.taste.g, t.genre, 3); bump(D.taste.a, t.artist, 3); }
  save(); updateNowPlaying(); if (['favs'].includes(view.name)) render();
}
function weekKey() { const d = new Date(); const j = new Date(d.getFullYear(), 0, 1); return d.getFullYear() * 100 + Math.ceil(((d - j) / 864e5 + j.getDay() + 1) / 7); }
const topOf = (m, n) => Object.entries(m).filter(e => e[1] > 0.5).sort((a, b) => b[1] - a[1]).slice(0, n);

/* ---------------- tocando agora ---------------- */
function updateNowPlaying() {
  const t = cur(); if (!t) return;
  const art = artOf(t);
  $('#barArt').src = art || 'logo.png'; $('#barArt').onerror = function () { this.src = 'logo.png'; };
  $('#barTitle').textContent = t.title; $('#barArtist').textContent = t.artist;
  $('#barFav').innerHTML = ico(isFav(t) ? 'heart' : 'heartO'); $('#barFav').classList.toggle('on', isFav(t));
  $('#npArt').src = art || 'logo.png'; $('#npArt').onerror = function () { this.src = 'logo.png'; };
  $('#npBg').style.backgroundImage = art ? `url("${art}")` : 'none';
  $('#npTitle').textContent = t.title; $('#npArtist').textContent = t.artist;
  // moldura da capa (sorteada por música, sempre a mesma pra mesma faixa)
  const k = keyOf(t); let h = 0; for (const c of k) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  const cs = S().artShape === 'disc' || S().artShape === 'circle' ? 'none' : S().caseStyle === 'random' ? 'c' + (1 + h % 5) : S().caseStyle;
  $('#npCase').className = cs;
  $('#npCase .caseNum').textContent = { c2: '2', c3: '3', c4: '4', c5: '5' }[cs] || '';
  $('#npCase .caseLabel').textContent = SRCNAME[t.src].toUpperCase();
  $('#npSrc').innerHTML = `<i></i>${SRCNAME[t.src]}${t.year ? ' · ' + t.year : ''}${t.genre ? ' · ' + esc(t.genre) : ''}`;
  const nx = st.queue[st.idx + 1];
  $('#npNext').innerHTML = nx ? `<img src="${esc(artOf(nx))}" onerror="this.src='logo.png'"><div style="min-width:0"><small>A seguir</small><b>${esc(nx.title)} — ${esc(nx.artist)}</b></div>` : '';
  artColor(art);
  $('#npFav').innerHTML = ico(isFav(t) ? 'heart' : 'heartO');
  document.title = `${t.title} — ${t.artist}`;
  const np = $('#np');
  np.classList.toggle('video', ['yt', 'vm'].includes(t.src) && !np.classList.contains('hidden'));
  np.classList.toggle('spin', S().artShape === 'disc');
  np.classList.toggle('pulse', S().artPulse);
  np.classList.remove('blur', 'gradient', 'solid'); np.classList.add(S().npBg);
  if ('mediaSession' in navigator) {
    navigator.mediaSession.metadata = new MediaMetadata({ title: t.title, artist: t.artist, album: t.album || '', artwork: art ? [{ src: art.startsWith('/') ? location.origin + art : art, sizes: '512x512' }] : [] });
  }
  if (S().notifications && document.hidden) try { new Notification(t.title, { body: t.artist, icon: art || 'logo.png', silent: true }); } catch {}
  if (!$('#np').classList.contains('hidden') && S().showLyricsNp) loadLyrics();
  layoutEmbed(); markCurrent();
  if (!$('#panel').classList.contains('hidden') && $('#panel').dataset.kind === 'queue') openQueue();
}
const SRCNAME = { local: 'No seu PC', yt: 'YouTube', sc: 'SoundCloud', vm: 'Vimeo', au: 'Audius', ia: 'Internet Archive' };
/* cor dominante da capa pro fundo animado */
let colorKey = '';
function artColor(url) {
  if (!url || url === colorKey) return; colorKey = url;
  const img = new Image();
  img.onload = () => {
    try {
      const c = document.createElement('canvas'); c.width = c.height = 24; const x = c.getContext('2d');
      x.drawImage(img, 0, 0, 24, 24); const d = x.getImageData(0, 0, 24, 24).data;
      let r = 0, g = 0, b = 0, w = 0;
      for (let i = 0; i < d.length; i += 4) { const mx = Math.max(d[i], d[i + 1], d[i + 2]), mn = Math.min(d[i], d[i + 1], d[i + 2]); const s = mx - mn + 8; r += d[i] * s; g += d[i + 1] * s; b += d[i + 2] * s; w += s; }
      $('#np').style.setProperty('--npc', `rgb(${r / w | 0},${g / w | 0},${b / w | 0})`);
    } catch { $('#np').style.removeProperty('--npc'); }
  };
  img.src = url.startsWith('/') ? url : `/proxy?u=${encodeURIComponent(url)}`;
}
$('#npNext').onclick = () => next();
function tick() {
  const p = st.dur ? Math.min(1, st.time / st.dur) : 0;
  if (!seeking) { $('#seekFill').style.width = (p * 100) + '%'; $('#seekKnob').style.left = (p * 100) + '%'; $('#tCur').textContent = fmt(st.time); }
  $('#tDur').textContent = '-' + fmt(st.dur - st.time);
  syncLyrics();
  if ('mediaSession' in navigator && st.dur && navigator.mediaSession.setPositionState) try { navigator.mediaSession.setPositionState({ duration: st.dur / 1000, position: Math.min(st.time, st.dur) / 1000, playbackRate: 1 }); } catch {}
}
function openNp(open = true) {
  const np = $('#np');
  np.classList.toggle('hidden', !open);
  np.classList.toggle('nolyrics', !S().showLyricsNp);
  if (open) { updateNowPlaying(); if (S().showLyricsNp) loadLyrics(); }
  layoutEmbed();
}
// posiciona o player oficial: grande dentro do "Tocando agora" ou mini flutuante
function layoutEmbed() {
  const e = $('#embed'); const t = cur();
  const embed = t && ['yt', 'sc', 'vm'].includes(st.kind);
  if (!embed) { e.classList.add('hidden'); return; }
  e.classList.remove('hidden');
  const npOpen = !$('#np').classList.contains('hidden');
  const slot = $('#npVideoSlot');
  $('#np').classList.toggle('video', npOpen && t.src !== 'sc');
  if (npOpen && t.src !== 'sc') {
    const r = slot.getBoundingClientRect();
    e.className = 'big';
    Object.assign(e.style, { left: r.left + 'px', top: r.top + 'px', width: r.width + 'px', height: r.height + 'px', right: 'auto', bottom: 'auto' });
  } else {
    e.className = 'mini' + (S().showVideo && t.src !== 'sc' ? '' : ' off');
    Object.assign(e.style, { left: '', top: '', width: '', height: '', right: '', bottom: '' });
  }
}
addEventListener('resize', layoutEmbed);

/* ---------------- letras ---------------- */
let LY = null, lyKey = '', lyLines = [], lyCur = -1;
async function loadLyrics() {
  const t = cur(); if (!t) return;
  const k = keyOf(t) + S().lyricsSource; if (k === lyKey) return; lyKey = k;
  const box = $('#lyrics'); $('#lyricsSrc').textContent = 'Procurando a letra…';
  box.innerHTML = `<div class="lyLoad">${[82, 64, 90, 48, 72, 58, 86].map(w => `<i style="width:${w}%"></i>`).join('')}</div>`;
  LY = await U.lyrics(t.artist, t.title, t.duration || st.dur, S().lyricsSource);
  if (keyOf(cur()) + S().lyricsSource !== k) return;
  lyLines = []; lyCur = -1;
  if (!LY) { noLyrics(t); return; }
  showLyrics(LY);
}
function noLyrics(t) {
  const box = $('#lyrics'); $('#lyricsSrc').textContent = '';
  const other = S().lyricsSource === 'letras' ? ['synced', 'LRCLIB'] : ['letras', 'Letras.mus.br'];
  const notes = ['♪', '♫', '♬', '♩', '♪', '♫'].map((n, i) => `<span class="note" style="left:${10 + i * 15}%;bottom:${10 + (i % 3) * 8}%;animation-delay:${i * 1.1}s">${n}</span>`).join('');
  box.innerHTML = `<div class="noLy">${notes}
    <div class="eqv"><b></b><b></b><b></b><b></b><b></b></div>
    <h3>Sem letra por aqui</h3>
    <p>Não conseguimos carregar a letra de <b>${esc(t.title)}</b>. Pode ser instrumental, muito nova ou ainda não ter letra publicada.</p>
    <div class="acts"><button id="lyOther">Tentar no ${other[1]}</button><button id="lyRetry">Tentar de novo</button><button id="lyWeb">Procurar na internet</button></div></div>`;
  $('#lyOther').onclick = async () => {
    box.innerHTML = `<div class="lyLoad">${[70, 52, 84, 60].map(w => `<i style="width:${w}%"></i>`).join('')}</div>`;
    const r = await U.lyrics(t.artist, t.title, t.duration || st.dur, other[0]);
    if (keyOf(cur()) !== keyOf(t)) return;
    if (r) showLyrics(r); else { noLyrics(t); toast(`Também não achei no ${other[1]}`); }
  };
  $('#lyRetry').onclick = () => { lyKey = ''; loadLyrics(); };
  $('#lyWeb').onclick = () => U.openUrl(`https://www.google.com/search?q=${encodeURIComponent(t.artist + ' ' + t.title + ' letra')}`);
}
function showLyrics(LY) {
  const box = $('#lyrics'); lyLines = []; lyCur = -1;
  if (LY.synced) {
    lyLines = LY.synced.split('\n').map(l => { const m = /\[(\d+):(\d+)(?:[.:](\d+))?](.*)/.exec(l); return m ? { t: (+m[1]) * 60000 + (+m[2]) * 1000 + (m[3] ? +(m[3] + '00').slice(0, 3) : 0), s: m[4].trim() } : null; }).filter(Boolean);
    box.innerHTML = lyLines.map((l, i) => `<p data-i="${i}">${esc(l.s || '♪')}</p>`).join('');
    box.querySelectorAll('p').forEach(p => p.onclick = () => seek(lyLines[+p.dataset.i].t));
  } else box.innerHTML = `<div class="plain">${esc(LY.plain)}</div>`;
  box.scrollTop = 0;
  $('#lyricsSrc').textContent = 'Letra: ' + LY.source;
}
function syncLyrics() {
  if (!lyLines.length || $('#np').classList.contains('hidden')) return;
  let i = -1; for (let k = 0; k < lyLines.length; k++) if (lyLines[k].t <= st.time + 250) i = k; else break;
  if (i === lyCur) return; lyCur = i;
  const ps = $('#lyrics').querySelectorAll('p');
  ps.forEach((p, k) => p.classList.toggle('on', k === i));
  if (ps[i]) ps[i].scrollIntoView({ block: 'center', behavior: 'smooth' });
}

/* ---------------- linhas de música ---------------- */
let rowsCtx = {};
function rows(list, ctxName, opts = {}) {
  rowsCtx[ctxName] = list;
  if (!list.length) return `<div class="empty">${opts.empty || 'Nada por aqui ainda.'}</div>`;
  return `<div class="list">${list.map((t, i) => `
    <div class="row" data-ctx="${ctxName}" data-i="${i}" data-k="${esc(keyOf(t))}">
      <span class="n"><b>${i + 1}</b>${ico('play', 'pl')}</span>
      ${artOf(t) ? `<img loading="lazy" src="${esc(artOf(t))}" onerror="this.outerHTML='<div class=ph></div>'">` : '<div class="ph"></div>'}
      <span class="t">${t.src !== 'local' ? `<span class="badge ${t.src}">${{ yt: 'YOUTUBE', sc: 'SOUNDCLOUD', vm: 'VIMEO', au: 'AUDIUS', ia: 'ARCHIVE' }[t.src]}</span>` : ''}${esc(t.title)}</span>
      <span class="a">${esc(t.artist)}</span>
      <span class="al">${esc(t.album || t.genre || '')}</span>
      <span class="d">${t.duration ? fmt(t.duration) : ''}</span>
      <span class="acts">
        ${t.download ? `<button data-act="dl" title="Baixar">${ico('dl')}</button>` : ''}
        <button data-act="fav" title="Favoritar">${ico(isFav(t) ? 'heart' : 'heartO')}</button>
        <button data-act="more" title="Mais">${ico('more')}</button>
      </span>
    </div>`).join('')}</div>`;
}
function markCurrent() { const k = keyOf(cur()); document.querySelectorAll('.row').forEach(r => r.classList.toggle('cur', r.dataset.k === k)); }
document.addEventListener('dblclick', e => { const r = e.target.closest('.row'); if (r && !e.target.closest('[data-act]')) play(rowsCtx[r.dataset.ctx], +r.dataset.i); });
document.addEventListener('click', e => { const c = e.target.closest('.row .n, .row img, .row .ph'); if (c) { const r = c.closest('.row'); play(rowsCtx[r.dataset.ctx], +r.dataset.i); } });
document.addEventListener('click', async e => {
  const b = e.target.closest('.row [data-act]'); if (!b) return;
  e.stopPropagation();
  const r = b.closest('.row'); const list = rowsCtx[r.dataset.ctx]; const t = list[+r.dataset.i];
  if (b.dataset.act === 'fav') { toggleFav(t); b.innerHTML = ico(isFav(t) ? 'heart' : 'heartO'); }
  if (b.dataset.act === 'dl') { toast(`Baixando "${t.title}"…`); const res = await U.download(t.download, `${t.artist} - ${t.title}`); toast(res.ok ? 'Baixado na pasta Música/Uncrowned' : 'Falhou: ' + res.error); if (res.ok) rescan(); }
  if (b.dataset.act === 'more') menu(t, list, +r.dataset.i, r.dataset.ctx);
});
function menu(t, list, i, ctxName) {
  const pls = Object.keys(D.playlists);
  modal(`<h3 style="margin:0 0 4px">${esc(t.title)}</h3><div style="color:var(--dim);margin-bottom:14px">${esc(t.artist)}</div>
    <div class="group">
      <div class="opt" data-m="next" style="cursor:pointer">Tocar a seguir</div>
      <div class="opt" data-m="queue" style="cursor:pointer">Adicionar à fila</div>
      <div class="opt" data-m="info" style="cursor:pointer">Informações</div>
      ${t.src === 'local' ? '<div class="opt" data-m="show" style="cursor:pointer">Mostrar no Explorador</div>' : ''}
      ${t.url || ['yt', 'vm'].includes(t.src) ? '<div class="opt" data-m="web" style="cursor:pointer">Abrir no navegador</div>' : ''}
      ${view.name === 'playlist' ? '<div class="opt" data-m="rm" style="cursor:pointer;color:#ff453a">Remover desta playlist</div>' : ''}
    </div>
    <div class="gtitle">Adicionar à playlist</div>
    <div class="group">${pls.map(p => `<div class="opt" data-pl="${esc(p)}" style="cursor:pointer">${esc(p)}</div>`).join('')}
      <div class="opt" data-m="newpl" style="cursor:pointer;color:var(--accent)">+ Nova playlist</div></div>`);
  $('#modalBox').onclick = e => {
    const o = e.target.closest('[data-m],[data-pl]'); if (!o) return;
    const m = o.dataset.m;
    if (o.dataset.pl) { addToPl(o.dataset.pl, t); }
    if (m === 'next') { st.queue.splice(st.idx + 1, 0, t); toast('Vai tocar a seguir'); }
    if (m === 'queue') { st.queue.push(t); toast('Adicionada à fila'); }
    if (m === 'info') return showInfo(t);
    if (m === 'show') U.openPath(t.path);
    if (m === 'web') U.openUrl(t.url || (t.src === 'yt' ? `https://youtu.be/${t.id}` : `https://vimeo.com/${t.id}`));
    if (m === 'rm') { D.playlists[view.pl] = D.playlists[view.pl].filter(x => keyOf(x) !== keyOf(t)); save(); render(); }
    if (m === 'newpl') { const n = prompt2('Nome da playlist', name => { if (name) { D.playlists[name] = D.playlists[name] || []; addToPl(name, t); renderPlNav(); } }); return; }
    closeModal();
  };
}
function addToPl(name, t) { const l = D.playlists[name] || (D.playlists[name] = []); if (!l.some(x => keyOf(x) === keyOf(t))) l.push(t); save(); toast(`Adicionada em "${name}"`); }
function modal(html) { $('#modalBox').innerHTML = html; $('#modal').classList.remove('hidden'); }
function closeModal() { $('#modal').classList.add('hidden'); $('#modalBox').onclick = null; }
$('#modal').onclick = e => { if (e.target.id === 'modal') closeModal(); };
function prompt2(label, cb) {
  modal(`<h3 style="margin:0 0 14px">${esc(label)}</h3><input class="txt" id="pIn" style="width:100%" autofocus>
    <div class="btns" style="margin:16px 0 0;justify-content:flex-end"><button class="btn" id="pNo">Cancelar</button><button class="btn fill" id="pOk">Criar</button></div>`);
  const ok = () => { const v = $('#pIn').value.trim(); closeModal(); cb(v); };
  $('#pOk').onclick = ok; $('#pNo').onclick = closeModal; $('#pIn').onkeydown = e => { if (e.key === 'Enter') ok(); };
  setTimeout(() => $('#pIn').focus(), 50);
}

/* ---------------- navegação ---------------- */
const NAV = [['search', 'Buscar', 'explore'], ['songs', 'Músicas', 'note'], ['albums', 'Álbuns', 'album'], ['artists', 'Artistas', 'person'], ['discover', 'Descobrir', 'explore'], ['settings', 'Ajustes', 'gear']];
function renderNav() {
  $('#nav').innerHTML = NAV.filter(n => n[0] !== 'search').map(([k, l, i]) => `<a data-v="${k}" class="${view.name === k ? 'on' : ''}">${ico(i)}<span>${l}</span></a>`).join('');
  renderPlNav();
}
function renderPlNav() {
  const items = [['favs', 'Favoritas', 'heart'], ['history', 'Ouvidas online', 'history'], ...Object.keys(D.playlists).map(p => ['pl:' + p, p, 'plist'])];
  $('#plnav').innerHTML = items.map(([k, l, i]) => `<a data-v="${esc(k)}" class="${(view.name === 'playlist' ? 'pl:' + view.pl : view.name) === k ? 'on' : ''}">${ico(i)}<span>${esc(l)}</span></a>`).join('');
}
document.addEventListener('click', e => {
  const a = e.target.closest('nav a'); if (!a) return;
  const v = a.dataset.v;
  go(v.startsWith('pl:') ? { name: 'playlist', pl: v.slice(3) } : { name: v });
});
$('#newPl').onclick = () => prompt2('Nova playlist', n => { if (n) { D.playlists[n] = D.playlists[n] || []; save(); renderPlNav(); go({ name: 'playlist', pl: n }); } });
function go(v) { view = v; openNp(false); renderNav(); render(); $('#main').scrollTop = 0; }

/* ---------------- telas ---------------- */
const views = {};
function render() { (views[view.name] || views.songs)(); markCurrent(); }
const playBtns = ctxName => `<div class="btns"><button class="btn" data-play="${ctxName}">${ico('play')} Tocar</button><button class="btn" data-shuf="${ctxName}">${ico('shuffle')} Aleatório</button></div>`;
document.addEventListener('click', e => {
  const p = e.target.closest('[data-play]'); if (p) { st.shuffle = false; updateModeBtns(); play(rowsCtx[p.dataset.play], 0); }
  const s = e.target.closest('[data-shuf]'); if (s) { const l = rowsCtx[s.dataset.shuf]; if (l.length) { st.queue = shuffleArr(l.slice()); playAt(0); } }
});

let songSort = 'title', favOnly = false;
views.songs = () => {
  let l = LIB.slice();
  if (songSort === 'artist') l.sort((a, b) => a.artist.localeCompare(b.artist)); else if (songSort === 'recent') l.sort((a, b) => b.added - a.added);
  else if (songSort === 'album') l.sort((a, b) => a.album.localeCompare(b.album) || a.track - b.track); else l.sort((a, b) => a.title.localeCompare(b.title));
  $('#view').innerHTML = `<h1>Músicas</h1><div class="sub">${LIB.length} músicas no seu PC${scanning ? '<span class="spinner"></span>' : ''}</div>
    <div class="chips">${[['title', 'A-Z'], ['artist', 'Artista'], ['album', 'Álbum'], ['recent', 'Recentes']].map(([k, n]) => `<button class="chip ${songSort === k ? 'on' : ''}" data-sort="${k}">${n}</button>`).join('')}</div>
    ${l.length ? playBtns('songs') : ''}
    ${rows(l, 'songs', { empty: D.folders.length ? 'Nenhuma música encontrada nas pastas escolhidas.' : 'Escolha as pastas de música em Ajustes → Biblioteca. Ou use a busca pra ouvir online.' })}`;
  $('#view').querySelectorAll('[data-sort]').forEach(b => b.onclick = () => { songSort = b.dataset.sort; render(); });
};
const groupBy = (l, f) => l.reduce((m, t) => ((m[f(t)] = m[f(t)] || []).push(t), m), {});
views.albums = () => {
  const g = groupBy(LIB, t => t.album + '\u0000' + t.artist);
  const keys = Object.keys(g).sort((a, b) => a.localeCompare(b));
  $('#view').innerHTML = `<h1>Álbuns</h1><div class="grid">${keys.map(k => { const t = g[k][0]; return `<div class="tile" data-al="${esc(k)}"><img loading="lazy" src="${esc(artOf(t))}" onerror="this.src='logo.png'"><div class="tt">${esc(t.album)}</div><div class="ta">${esc(t.artist)}${t.year ? ' · ' + t.year : ''}</div></div>`; }).join('') || '<div class="empty">Nada por aqui.</div>'}</div>`;
  $('#view').querySelectorAll('[data-al]').forEach(d => d.onclick = () => go({ name: 'group', title: g[d.dataset.al][0].album, list: g[d.dataset.al].sort((a, b) => a.track - b.track), sub: g[d.dataset.al][0].artist }));
};
views.artists = () => {
  const g = groupBy(LIB, t => t.artist);
  const keys = Object.keys(g).sort((a, b) => a.localeCompare(b));
  $('#view').innerHTML = `<h1>Artistas</h1><div class="grid">${keys.map(k => `<div class="tile round" data-ar="${esc(k)}"><img loading="lazy" src="${esc(artOf(g[k][0]))}" onerror="this.src='logo.png'"><div class="tt" style="text-align:center">${esc(k)}</div><div class="ta" style="text-align:center">${g[k].length} músicas</div></div>`).join('') || '<div class="empty">Nada por aqui.</div>'}</div>`;
  $('#view').querySelectorAll('[data-ar]').forEach(d => d.onclick = () => go({ name: 'group', title: d.dataset.ar, list: g[d.dataset.ar], sub: `${g[d.dataset.ar].length} músicas` }));
};
views.group = () => { $('#view').innerHTML = `<h1>${esc(view.title)}</h1><div class="sub">${esc(view.sub || '')}</div>${playBtns('group')}${rows(view.list, 'group')}`; };
views.favs = () => { const l = Object.values(D.favs); $('#view').innerHTML = `<h1>Favoritas</h1>${l.length ? playBtns('favs') : ''}${rows(l, 'favs', { empty: 'Toque no coração das músicas pra salvar aqui.' })}`; };
views.history = () => { $('#view').innerHTML = `<h1>Ouvidas online</h1>${rows(D.history, 'history')}`; };
views.playlist = () => {
  const l = D.playlists[view.pl] || [];
  $('#view').innerHTML = `<h1>${esc(view.pl)}</h1><div class="btns">${l.length ? `<button class="btn" data-play="pl">${ico('play')} Tocar</button><button class="btn" data-shuf="pl">${ico('shuffle')} Aleatório</button>` : ''}<button class="btn" id="delPl" style="color:#ff453a">Apagar playlist</button></div>${rows(l, 'pl', { empty: 'Use o botão ••• numa música pra adicionar aqui.' })}`;
  $('#delPl').onclick = () => { delete D.playlists[view.pl]; save(); go({ name: 'songs' }); };
};

/* busca única: no PC + online (SoundCloud → YouTube → Vimeo) + pra baixar */
let searchQ = '', searchRes = null, searchT;
$('#q').addEventListener('keydown', e => { if (e.key === 'Enter') { clearTimeout(searchT); e.target.dispatchEvent(new Event('input')); } });
$('#q').addEventListener('paste', () => setTimeout(() => { clearTimeout(searchT); $('#q').dispatchEvent(new Event('input')); }, 0));
$('#q').addEventListener('input', e => {
  searchQ = e.target.value.trim();
  clearTimeout(searchT);
  if (!searchQ) { if (view.name === 'search') go({ name: S().startView }); return; }
  if (view.name !== 'search') { view = { name: 'search' }; openNp(false); renderNav(); }
  searchRes = null; render();
  const isLink = /^(https?:\/\/|www\.|youtu\.be|youtube\.com|m\.youtube|music\.youtube|soundcloud\.com|vimeo\.com)/i.test(searchQ);
  searchT = setTimeout(async () => { const q = searchQ; const r = await U.search(q); if (q === searchQ) { searchRes = r; render(); } }, isLink ? 50 : 600);
});
views.search = () => {
  const q = searchQ.toLowerCase();
  const local = q && !/^(https?:|www\.)/.test(q) ? LIB.filter(t => (t.title + ' ' + t.artist + ' ' + t.album).toLowerCase().includes(q)).slice(0, 40) : [];
  if (searchRes && searchRes.link) {
    const t = searchRes.stream[0]; rowsCtx.link = searchRes.stream;
    $('#view').innerHTML = `<h1>Do link</h1>
      <div style="display:flex;gap:24px;align-items:flex-end;margin-bottom:22px">
        <img src="${esc(t.art)}" onerror="this.src='logo.png'" style="width:${t.src === 'sc' ? 220 : 360}px;aspect-ratio:${t.src === 'sc' ? '1' : '16/9'};object-fit:cover;border-radius:var(--r);box-shadow:0 20px 40px rgba(0,0,0,.5)">
        <div><div class="badge ${t.src}" style="display:inline-block;margin-bottom:8px">${{ yt: 'YOUTUBE', sc: 'SOUNDCLOUD', vm: 'VIMEO' }[t.src]}</div>
          <div style="font-size:24px;font-weight:700">${esc(t.title)}</div><div style="color:var(--dim);margin:4px 0 16px">${esc(t.artist)}</div>
          <div class="btns" style="margin:0"><button class="btn fill" data-play="link">${ico('play')} Tocar</button><button class="btn" id="lnkAdd">${ico('plus')} Adicionar à fila</button><button class="btn" id="lnkMore">${ico('more')}</button></div></div>
      </div>`;
    $('#lnkAdd').onclick = () => { st.queue.push(t); toast('Adicionado à fila'); if (st.idx < 0) playAt(0); };
    $('#lnkMore').onclick = () => menu(t, searchRes.stream, 0, 'link');
    return;
  }
  $('#view').innerHTML = `<h1>Resultados</h1>
    ${local.length ? `<h2>No seu PC</h2>${rows(local, 'sLocal')}` : ''}
    <h2>Online ${searchRes ? '' : '<span class="spinner"></span>'}</h2>
    ${searchRes ? rows(searchRes.stream, 'sOnline', { empty: `Nada online pra "${esc(searchQ)}".` }) : ''}
    ${searchRes && searchRes.downloadable.length ? `<h2>Pra baixar</h2><div class="sub">Músicas liberadas pelos próprios artistas (Audius e Internet Archive).</div>${rows(searchRes.downloadable, 'sDl')}` : ''}`;
};

/* descobrir: gosto musical + descobertas da semana + em alta */
const GENRES = ['Electronic', 'Hip-Hop/Rap', 'Pop', 'R&B/Soul', 'Rock', 'Alternative', 'Latin', 'Lo-Fi', 'House', 'Trap', 'Deep House', 'Techno', 'Dubstep', 'Jazz', 'Acoustic', 'Funk', 'Reggae', 'Metal', 'Ambient', 'Soundtrack'];
let trendGenre = null, trendList = null;
async function weekly(force) {
  const w = weekKey();
  if (!force && D.weekly && D.weekly.w === w) return D.weekly.t;
  const gs = topOf(D.taste.g, 3).map(e => e[0]); const genres = gs.length ? gs : ['Pop', 'Electronic', 'Hip-Hop/Rap'];
  const artists = topOf(D.taste.a, 4).map(e => e[0]);
  let pool = [];
  for (const [i, g] of genres.entries()) pool.push(...(await U.trending(g, 'week')).slice(0, 25 - i * 5));
  for (const a of artists) pool.push(...(await U.audiusSearch(a)).filter(t => t.artist.toLowerCase() !== a.toLowerCase()).slice(0, 6));
  pool.push(...(await U.trending(null, 'week')).slice(0, 10));
  let seed = w; const rnd = () => (seed = (seed * 9301 + 49297) % 233280) / 233280;
  pool = pool.filter((t, i, a) => a.findIndex(x => x.id === t.id) === i && !D.heard.includes(keyOf(t)));
  for (let i = pool.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [pool[i], pool[j]] = [pool[j], pool[i]]; }
  const per = {}; const out = pool.filter(t => (per[t.artist] = (per[t.artist] || 0) + 1) <= 2).slice(0, 30);
  D.weekly = { w, t: out }; save();
  return out;
}
views.discover = async () => {
  if (!topOf(D.taste.g, 1).length && !D.pickedGenres) {
    const picked = new Set();
    $('#view').innerHTML = `<h1>Do que você curte?</h1><div class="sub">Escolhe pelo menos 3. O app aprende o resto com o que você ouve, curte e pula.</div>
      <div class="chips">${GENRES.map(g => `<button class="chip" data-g="${g}">${g}</button>`).join('')}</div><button class="btn fill" id="gOk" disabled>Pronto</button>`;
    $('#view').querySelectorAll('[data-g]').forEach(b => b.onclick = () => { const g = b.dataset.g; picked.has(g) ? picked.delete(g) : picked.add(g); b.classList.toggle('on'); $('#gOk').disabled = picked.size < 3; });
    $('#gOk').onclick = () => { picked.forEach(g => bump(D.taste.g, g, 5)); D.pickedGenres = true; D.weekly = null; save(); render(); };
    return;
  }
  const g = topOf(D.taste.g, 6), a = topOf(D.taste.a, 5), max = g.length ? g[0][1] : 1;
  $('#view').innerHTML = `<h1>Descobrir</h1>
    <div class="hero" id="wkPlay"><b>Descobertas da Semana</b><span id="wkSub">Montando sua lista…</span></div>
    <div class="btns"><button class="btn" id="wkNew">Gerar de novo</button><button class="btn" id="wkShuf">${ico('shuffle')} Aleatório</button><button class="btn" id="tasteReset">Ajustar gosto</button></div>
    <div class="hscroll" id="wkRow"></div>
    <h2>Seu gosto musical</h2><div class="bars">${g.map(([n, v]) => `<div class="b"><span>${esc(n)}</span><i style="width:${Math.max(4, v / max * 100)}%"></i></div>`).join('') || 'Ouça umas músicas que aparece aqui.'}
      ${a.length ? `<div style="color:var(--dim);font-size:13px;margin-top:10px">Mais ouvidos: ${a.map(x => esc(x[0])).join(', ')}</div>` : ''}</div>
    <h2>Em alta na semana</h2>
    <div class="chips">${[null, ...new Set([...g.map(x => x[0]), ...GENRES])].slice(0, 13).map(x => `<button class="chip ${trendGenre === x ? 'on' : ''}" data-tg="${x ?? ''}">${x ?? 'Tudo'}</button>`).join('')}</div>
    <div id="trend"><span class="spinner"></span></div>`;
  $('#view').querySelectorAll('[data-tg]').forEach(b => b.onclick = () => { trendGenre = b.dataset.tg || null; trendList = null; render(); });
  $('#tasteReset').onclick = () => { D.taste = { g: {}, a: {} }; D.pickedGenres = false; D.weekly = null; save(); render(); };
  const fill = l => {
    rowsCtx.weekly = l;
    $('#wkSub').textContent = `${l.length} músicas novas pra você · renova toda semana`;
    $('#wkRow').innerHTML = l.map((t, i) => `<div class="tile" data-wi="${i}"><img loading="lazy" src="${esc(t.art)}" onerror="this.src='logo.png'"><div class="tt">${esc(t.title)}</div><div class="ta">${esc(t.artist)}</div></div>`).join('');
    $('#wkRow').querySelectorAll('[data-wi]').forEach(d => d.onclick = () => play(l, +d.dataset.wi));
  };
  $('#wkPlay').onclick = () => rowsCtx.weekly && rowsCtx.weekly.length && play(rowsCtx.weekly, 0);
  $('#wkShuf').onclick = () => rowsCtx.weekly && (st.queue = shuffleArr(rowsCtx.weekly.slice()), playAt(0));
  $('#wkNew').onclick = async () => { $('#wkSub').textContent = 'Montando…'; fill(await weekly(true)); };
  weekly(false).then(l => { if (view.name === 'discover') fill(l); });
  if (!trendList) trendList = await U.trending(trendGenre, 'week');
  if (view.name === 'discover' && $('#trend')) $('#trend').innerHTML = rows(trendList.slice(0, 30), 'trend');
};

/* ---------------- ajustes ---------------- */
const seg = (k, opts) => `<div class="seg" data-k="${k}">${opts.map(([v, l]) => `<button data-v="${v}" class="${String(S()[k]) === String(v) ? 'on' : ''}">${l}</button>`).join('')}</div>`;
const sw = k => `<div class="sw ${S()[k] ? 'on' : ''}" data-sw="${k}"></div>`;
const rng = (k, min, max, step, f = v => v) => `<div class="rng"><input type="range" data-r="${k}" min="${min}" max="${max}" step="${step}" value="${S()[k]}"><span>${f(S()[k])}</span></div>`;
const opt = (l, c, sub) => `<div class="opt"><div class="l">${l}${sub ? `<small>${sub}</small>` : ''}</div>${c}</div>`;
const ACC = ['#fa2d48', '#ff9500', '#ffcc00', '#34c759', '#00c7be', '#0a84ff', '#5e5ce6', '#bf5af2', '#ff375f', '#ac8e68', '#8e8e93', '#ffffff'];
const EQP = { 'Normal': [0, 0, 0, 0, 0, 0, 0, 0, 0, 0], 'Graves': [7, 6, 4, 2, 0, 0, 0, 0, 0, 0], 'Rock': [4, 3, -1, -2, -1, 1, 3, 4, 4, 4], 'Pop': [-1, 1, 3, 4, 3, 0, -1, -1, 0, 1],
  'Voz': [-2, -2, 0, 2, 4, 4, 3, 1, 0, -1], 'Eletrônica': [5, 4, 1, 0, -2, 1, 0, 2, 4, 5], 'Agudos': [0, 0, 0, 0, 0, 1, 3, 5, 6, 7], 'Funk/Trap': [8, 7, 3, 0, -1, 0, 1, 2, 2, 3] };
views.settings = () => {
  const s = S();
  $('#view').innerHTML = `<h1>Ajustes</h1>
  <div class="gtitle">Aparência</div><div class="group">
    ${opt('Tema', seg('theme', [['system', 'Sistema'], ['dark', 'Escuro'], ['black', 'Preto total'], ['light', 'Claro']]))}
    ${opt('Cor de destaque', `<div class="dots">${ACC.map(c => `<div class="dot ${s.accent === c ? 'on' : ''}" data-acc="${c}" style="background:${c}"></div>`).join('')}<input type="color" id="accPick" value="${s.accent}" style="width:30px;height:28px;border:0;background:none"></div>`)}
    ${opt('Arredondamento', rng('radius', 0, 28, 1))}
    ${opt('Tamanho do texto', rng('fontScale', 0.85, 1.3, 0.05, v => (+v).toFixed(2) + 'x'))}
    ${opt('Tamanho das capas na grade', rng('tile', 130, 260, 10))}
    ${opt('Lista de músicas', seg('density', [['normal', 'Confortável'], ['compact', 'Compacta']]))}
    ${opt('Tela inicial', seg('startView', [['songs', 'Músicas'], ['albums', 'Álbuns'], ['artists', 'Artistas'], ['discover', 'Descobrir']]))}
  </div>
  <div class="gtitle">Tocando agora</div><div class="group">
    ${opt('Capa', seg('artShape', [['square', 'Quadrada'], ['rounded', 'Arredondada'], ['circle', 'Círculo'], ['disc', 'Disco girando']]))}
    ${opt('Moldura da capa', seg('caseStyle', [['random', 'Aleatória'], ['c1', 'CD clássico'], ['c2', 'Preta faixa azul'], ['c3', 'Preta prata'], ['c4', 'Azul'], ['c5', 'Branca'], ['none', 'Sem moldura']]), 'Caixinhas no estilo dos jogos de console antigos')}
    ${opt('Fundo', seg('npBg', [['blur', 'Capa desfocada'], ['gradient', 'Degradê'], ['solid', 'Sólido']]))}
    ${opt('Desfoque', rng('blur', 10, 140, 5))}
    ${opt('Capa encolhe ao pausar', sw('artPulse'), 'Igual ao Apple Music')}
    ${opt('Mostrar letra ao lado', sw('showLyricsNp'))}
  </div>
  <div class="gtitle">Letras</div><div class="group">
    ${opt('Fonte', seg('lyricsSource', [['letras', 'Letras.mus.br'], ['synced', 'Sincronizada (LRCLIB)']]))}
    ${opt('Tamanho', rng('lyricsSize', 16, 44, 1))}
    ${opt('Alinhamento', seg('lyricsAlign', [['left', 'Esquerda'], ['center', 'Centro'], ['right', 'Direita']]))}
  </div>
  <div class="gtitle">Vídeo (YouTube / Vimeo)</div><div class="group">
    ${opt('Mostrar vídeo flutuante', sw('showVideo'), 'Quando o "Tocando agora" está fechado')}
    ${opt('Tamanho do vídeo flutuante', rng('videoSize', 240, 640, 20))}
    ${opt('Tocar o próximo sozinho', sw('autoNext'))}
  </div>
  <div class="gtitle">Biblioteca</div><div class="group">
    ${opt('Pastas de música', `<button class="btn" id="addF">Adicionar pasta</button>`, D.folders.map(f => `${esc(f)} <a data-rmf="${esc(f)}" style="color:#ff453a;cursor:pointer">remover</a>`).join('<br>') || 'Nenhuma pasta')}
    ${opt('Mostrar só músicas', sw('hideVoice'), 'Esconde áudios do WhatsApp, gravações e notas de voz')}
    ${opt('Ignorar áudios menores que', rng('minDur', 0, 120, 5, v => v + ' s'))}
    ${opt('Reescanear agora', `<button class="btn" id="rescan">Reescanear</button>`)}
  </div>
  <div class="gtitle">Reprodução</div><div class="group">
    ${opt('Velocidade', rng('speed', 0.5, 2, 0.05, v => (+v).toFixed(2) + 'x'))}
    ${opt('Mudar o tom junto com a velocidade', sw('pitchWithSpeed'), 'Ligado = efeito slowed/nightcore; desligado = só fica mais rápido/lento')}
    ${opt('Avisar quando trocar de música', sw('notifications'), 'Notificação do Windows quando o app está minimizado')}
  </div>
  <div class="gtitle">Equalizador</div><div class="group">
    ${opt('Ativar equalizador', sw('eqOn'), 'Funciona nas músicas do PC, Audius e Archive')}
    ${opt('Presets', `<div class="seg">${Object.keys(EQP).map(p => `<button data-eqp="${p}">${p}</button>`).join('')}</div>`)}
    ${FREQS.map((f, i) => opt(f >= 1000 ? f / 1000 + ' kHz' : f + ' Hz', `<div class="rng"><input type="range" data-eq="${i}" min="-12" max="12" step="1" value="${s.eq[i]}"><span>${s.eq[i]} dB</span></div>`)).join('')}
    ${opt('Graves extra', rng('bass', 0, 15, 1, v => v + ' dB'))}
    ${opt('Volume extra', rng('gainBoost', 0, 9, 1, v => '+' + v + ' dB'))}
  </div>
  <div class="gtitle">YouTube</div><div class="group">
    ${opt('Bloquear anúncios', sw('adblock'), 'Bloqueador com as listas do uBlock Origin + pula automaticamente o que passar')}
    ${opt('Conta do YouTube', `<span id="ytAcc"><span class="spinner"></span></span>`, 'Entre pra tocar vídeos +18 e ter suas preferências. O login fica só neste PC.')}
  </div>
  <div class="gtitle">Online</div><div class="group">
    ${opt('Token do Vimeo', `<input class="txt" id="vmTok" placeholder="opcional" value="${esc(s.vimeoToken)}">`, 'Grátis em developer.vimeo.com → Create app → Generate token')}
  </div>
  <div class="gfoot">A busca procura no seu PC, YouTube, SoundCloud, Vimeo, Audius e Internet Archive — nessa ordem.</div>
  <div class="gfoot" style="text-align:center;margin-top:30px">Uncrowned Music para PC 1.0</div>`;
  const v = $('#view');
  v.querySelectorAll('.seg[data-k] button').forEach(b => b.onclick = () => { const k = b.parentElement.dataset.k; S()[k] = b.dataset.v; changed(k); });
  v.querySelectorAll('[data-sw]').forEach(b => b.onclick = () => { const k = b.dataset.sw; S()[k] = !S()[k]; changed(k); });
  v.querySelectorAll('[data-r]').forEach(r => { r.oninput = () => { S()[r.dataset.r] = +r.value; live(r.dataset.r); r.nextElementSibling.textContent = { speed: (+r.value).toFixed(2) + 'x', fontScale: (+r.value).toFixed(2) + 'x', minDur: r.value + ' s', bass: r.value + ' dB', gainBoost: '+' + r.value + ' dB' }[r.dataset.r] || r.value; }; r.onchange = () => changed(r.dataset.r, true); });
  v.querySelectorAll('[data-eq]').forEach(r => r.oninput = () => { S().eq[+r.dataset.eq] = +r.value; r.nextElementSibling.textContent = r.value + ' dB'; applyEq(); save(); });
  v.querySelectorAll('[data-eqp]').forEach(b => b.onclick = () => { S().eq = EQP[b.dataset.eqp].slice(); S().eqOn = true; applyEq(); save(); render(); });
  v.querySelectorAll('[data-acc]').forEach(d => d.onclick = () => { S().accent = d.dataset.acc; changed('accent'); });
  $('#accPick').oninput = e => { S().accent = e.target.value; applyLook(); save(); };
  $('#addF').onclick = async () => { const f = await U.pickFolder(); if (f.length) { D.folders = [...new Set([...D.folders, ...f])]; save(); rescan(); render(); } };
  v.querySelectorAll('[data-rmf]').forEach(a => a.onclick = () => { D.folders = D.folders.filter(f => f !== a.dataset.rmf); save(); rescan(); render(); });
  $('#rescan').onclick = () => rescan();
  const ytAcc = async () => {
    const logged = await U.ytLogged(); if (!$('#ytAcc')) return;
    $('#ytAcc').innerHTML = logged ? '<span style="color:#34c759;margin-right:10px">Conectado</span><button class="btn" id="ytOut">Sair</button>' : '<button class="btn fill" id="ytIn">Entrar no YouTube</button>';
    if ($('#ytIn')) $('#ytIn').onclick = async () => { const ok = await U.ytLogin(); toast(ok ? 'YouTube conectado' : 'Login não concluído'); ytAcc(); };
    if ($('#ytOut')) $('#ytOut').onclick = async () => { await U.ytLogout(); toast('Saiu da conta do YouTube'); ytAcc(); };
  };
  ytAcc();
  $('#vmTok').onchange = e => { S().vimeoToken = e.target.value.trim(); save(); };
};
function live(k) { if (['radius', 'fontScale', 'tile', 'blur', 'lyricsSize', 'videoSize'].includes(k)) applyLook(); if (k === 'speed') applyRate(true); if (['bass', 'gainBoost'].includes(k)) applyEq(); if (k === 'videoSize') layoutEmbed(); }
function changed(k, keepView) {
  save(); applyLook(); applyRate(); applyEq(); updateNowPlaying(); layoutEmbed();
  if (['hideVoice', 'minDur'].includes(k)) rescan();
  if (k === 'adblock') U.setAdblock(S().adblock).then(on => toast(S().adblock ? (on ? 'Bloqueador de anúncios ligado' : 'Bloqueador ligado (lista carrega na próxima abertura)') : 'Bloqueador de anúncios desligado'));
  if (k === 'lyricsSource') { lyKey = ''; }
  if (!keepView) render();
}

/* ---------------- painéis: fila, velocidade, timer, info ---------------- */
function panel(kind, html) {
  const p = $('#panel');
  if (!p.classList.contains('hidden') && p.dataset.kind === kind && !html.keep) { p.classList.add('hidden'); return false; }
  p.dataset.kind = kind; p.innerHTML = html; p.classList.remove('hidden'); return true;
}
function openQueue() {
  const html = `<h3>A seguir</h3>${st.queue.map((t, i) => `<div class="qrow ${i === st.idx ? 'cur' : ''}" data-qi="${i}"><img src="${esc(artOf(t))}" onerror="this.src='logo.png'"><div><b>${esc(t.title)}</b><small>${esc(t.artist)}</small></div><button class="icon small" data-qrm="${i}">✕</button></div>`).join('') || '<div class="empty">Fila vazia</div>'}`;
  const p = $('#panel'); p.dataset.kind = 'queue'; p.innerHTML = html; p.classList.remove('hidden');
  p.querySelectorAll('[data-qi]').forEach(r => r.onclick = e => { if (e.target.closest('[data-qrm]')) { const i = +e.target.closest('[data-qrm]').dataset.qrm; st.queue.splice(i, 1); if (i < st.idx) st.idx--; openQueue(); return; } playAt(+r.dataset.qi); });
  const c = p.querySelector('.cur'); if (c) c.scrollIntoView({ block: 'center' });
}
$('#bQueue').onclick = () => { const p = $('#panel'); if (!p.classList.contains('hidden') && p.dataset.kind === 'queue') p.classList.add('hidden'); else openQueue(); };
$('#bSpeed').onclick = () => {
  if (!panel('speed', `<h3>Velocidade</h3>
    <div class="rng" style="margin:6px 4px"><input type="range" id="spR" min="0.5" max="2" step="0.05" value="${S().speed}" style="flex:1"><span id="spV">${S().speed.toFixed(2)}x</span></div>
    <div class="opt" style="padding:10px 4px"><div class="l">Mudar o tom junto<small>slowed / nightcore</small></div>${sw('pitchWithSpeed')}</div>
    <div class="chips">${[['Slowed', 0.85, true], ['Normal', 1, false], ['Nightcore', 1.25, true], ['Sped up', 1.2, false], ['Lento', 0.75, false]].map(([n, v, p]) => `<button class="chip ${Math.abs(S().speed - v) < 0.001 && (v === 1 || S().pitchWithSpeed === p) ? 'on' : ''}" data-sp="${v}" data-pp="${p}">${n}</button>`).join('')}</div>`)) return;
  const p = $('#panel');
  const syncChips = () => p.querySelectorAll('[data-sp]').forEach(x => x.classList.toggle('on', Math.abs(S().speed - +x.dataset.sp) < 0.001 && (+x.dataset.sp === 1 || S().pitchWithSpeed === (x.dataset.pp === 'true'))));
  $('#spR').oninput = e => { p.querySelectorAll('[data-sp]').forEach(x => x.classList.remove('on')); S().speed = +e.target.value; $('#spV').textContent = S().speed.toFixed(2) + 'x'; applyRate(true); save(); updateModeBtns(); syncChips(); };
  p.querySelector('[data-sw]').onclick = e => { S().pitchWithSpeed = !S().pitchWithSpeed; e.target.classList.toggle('on', S().pitchWithSpeed); applyRate(true); save(); syncChips(); };
  p.querySelectorAll('[data-sp]').forEach(b => b.onclick = () => {
    S().speed = +b.dataset.sp; S().pitchWithSpeed = b.dataset.pp === 'true'; rateWarned = ''; applyRate(true); save(); updateModeBtns();
    $('#spR').value = S().speed; $('#spV').textContent = S().speed.toFixed(2) + 'x'; p.querySelector('[data-sw]').classList.toggle('on', S().pitchWithSpeed);
    syncChips(); toast(b.textContent + ' ativado');
  });
};
$('#bTimer').onclick = () => {
  if (!panel('timer', `<h3>Timer pra parar a música</h3><div style="color:var(--dim);margin:0 4px 12px">${st.sleepAt ? 'Para em ' + fmt(st.sleepAt - Date.now()) : 'A música pausa sozinha depois do tempo escolhido.'}</div>
    <div class="chips">${[5, 10, 15, 30, 45, 60, 90, 120].map(m => `<button class="chip" data-tm="${m}">${m} min</button>`).join('')}${st.sleepAt ? '<button class="chip" data-tm="0">Desligar</button>' : ''}</div>`)) return;
  $('#panel').querySelectorAll('[data-tm]').forEach(b => b.onclick = () => { const m = +b.dataset.tm; st.sleepAt = m ? Date.now() + m * 60000 : 0; toast(m ? `Para em ${m} min` : 'Timer desligado'); $('#panel').classList.add('hidden'); updateModeBtns(); });
};
async function showInfo(t = cur()) {
  if (!t) return;
  modal(`<div style="display:flex;gap:14px;align-items:center"><img id="iArt" src="${esc(artOf(t))}" onerror="this.src='logo.png'" style="width:76px;height:76px;border-radius:8px;object-fit:cover"><div><b style="font-size:17px">${esc(t.title)}</b><div style="color:var(--dim)">${esc(t.artist)}</div></div></div><div id="iBody" style="margin-top:14px">Buscando informações<span class="spinner"></span></div>`);
  const i = await U.info(t.title, t.artist) || {};
  if (!$('#iBody')) return;
  if (i.art) $('#iArt').src = i.art;
  const rowsI = [['Artista', i.artist || t.artist], ['Álbum', i.album || t.album], ['Lançamento', i.year || (t.year || '')], ['Gênero', i.genre || t.genre], ['Faixa', i.track], ['Duração', fmt(t.duration || st.dur)], ['Origem', { local: 'Arquivo no PC', yt: 'YouTube', sc: 'SoundCloud', vm: 'Vimeo', au: 'Audius', ia: 'Internet Archive' }[t.src]], ['Dados', i.source]].filter(r => r[1]);
  $('#iBody').innerHTML = `<div class="info">${rowsI.map(([l, v]) => `<div><span>${l}</span><b style="font-weight:500;text-align:right">${esc(v)}</b></div>`).join('')}</div>`;
}
$('#bInfo').onclick = () => showInfo();
$('#bLyrics').onclick = () => { const np = $('#np'); if (np.classList.contains('hidden')) { S().showLyricsNp = true; openNp(true); } else openNp(false); updateModeBtns(); };
$('#bVideo').onclick = () => { S().showVideo = !S().showVideo; save(); layoutEmbed(); updateModeBtns(); };
$('#embedExpand').onclick = () => openNp(true);
document.addEventListener('mousedown', e => { const p = $('#panel'); if (!p.classList.contains('hidden') && !e.target.closest('#panel') && !e.target.closest('#barRight')) p.classList.add('hidden'); });

/* ---------------- barra do player ---------------- */
function updateModeBtns() {
  $('#bShuffle').innerHTML = ico('shuffle'); $('#bShuffle').classList.toggle('on', st.shuffle);
  $('#bRepeat').innerHTML = ico(st.repeat === 2 ? 'repeat1' : 'repeat'); $('#bRepeat').classList.toggle('on', st.repeat > 0);
  $('#bPrev').innerHTML = ico('prev'); $('#bNext').innerHTML = ico('next');
  $('#bLyrics').innerHTML = ico('lyrics'); $('#bLyrics').classList.toggle('on', !$('#np').classList.contains('hidden'));
  $('#bInfo').innerHTML = ico('info'); $('#bQueue').innerHTML = ico('queue');
  $('#bSpeed').innerHTML = ico('speed'); $('#bSpeed').classList.toggle('on', S().speed !== 1);
  $('#bTimer').innerHTML = ico('timer'); $('#bTimer').classList.toggle('on', !!st.sleepAt);
  $('#bVideo').innerHTML = ico('video'); $('#bVideo').classList.toggle('on', S().showVideo);
}
$('#bPlay').onclick = toggle; $('#bNext').onclick = next; $('#bPrev').onclick = prev;
$('#bShuffle').onclick = () => {
  st.shuffle = !st.shuffle;
  if (st.shuffle && st.queue.length > 1) { const t = cur(); const rest = st.queue.filter((_, i) => i !== st.idx); st.queue = [t, ...shuffleArr(rest)]; st.idx = 0; }
  updateModeBtns();
};
$('#bRepeat').onclick = () => { st.repeat = (st.repeat + 1) % 3; updateModeBtns(); };
$('#barFav').onclick = e => { e.stopPropagation(); if (cur()) toggleFav(cur()); };
$('#npFav').onclick = () => cur() && toggleFav(cur());
$('#barLeft').onclick = () => cur() && openNp($('#np').classList.contains('hidden'));
$('#npClose').onclick = () => { openNp(false); updateModeBtns(); };
$('#vol').oninput = e => setVolume(+e.target.value);
let preMute = 0.8; $('#bVol').onclick = () => { if (S().volume > 0) { preMute = S().volume; setVolume(0); } else setVolume(preMute || 0.8); };
let seeking = false;
const seekAt = e => { const r = $('#seek').getBoundingClientRect(); return Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)); };
$('#seek').onmousedown = e => {
  if (!st.dur) return; seeking = true;
  const move = ev => { const p = seekAt(ev); $('#seekFill').style.width = p * 100 + '%'; $('#seekKnob').style.left = p * 100 + '%'; $('#tCur').textContent = fmt(p * st.dur); };
  const up = ev => { seeking = false; seek(seekAt(ev) * st.dur); removeEventListener('mousemove', move); removeEventListener('mouseup', up); };
  move(e); addEventListener('mousemove', move); addEventListener('mouseup', up);
};

/* teclado e teclas de mídia */
addEventListener('keydown', e => {
  if (e.target.tagName === 'INPUT') { if (e.key === 'Escape') e.target.blur(); return; }
  if (e.code === 'Space') { e.preventDefault(); toggle(); }
  else if (e.ctrlKey && e.key === 'ArrowRight') next();
  else if (e.ctrlKey && e.key === 'ArrowLeft') prev();
  else if (e.key === 'ArrowRight') seek(st.time + 5000);
  else if (e.key === 'ArrowLeft') seek(Math.max(0, st.time - 5000));
  else if (e.key === 'ArrowUp') setVolume(Math.min(1, S().volume + 0.05));
  else if (e.key === 'ArrowDown') setVolume(Math.max(0, S().volume - 0.05));
  else if (e.ctrlKey && e.key.toLowerCase() === 'f') { $('#q').focus(); $('#q').select(); }
  else if (e.ctrlKey && e.key.toLowerCase() === 'l') $('#bLyrics').click();
  else if (e.ctrlKey && e.key.toLowerCase() === 'i') showInfo();
  else if (e.key === 'Escape') { closeModal(); $('#panel').classList.add('hidden'); openNp(false); }
});
if ('mediaSession' in navigator) {
  navigator.mediaSession.setActionHandler('play', toggle); navigator.mediaSession.setActionHandler('pause', toggle);
  navigator.mediaSession.setActionHandler('nexttrack', next); navigator.mediaSession.setActionHandler('previoustrack', prev);
  try { navigator.mediaSession.setActionHandler('seekto', d => seek(d.seekTime * 1000)); } catch {}
}

/* ---------------- biblioteca ---------------- */
let scanning = false;
U.onScanProgress(p => { if (view.name === 'songs') { const s = $('.sub'); if (s) s.innerHTML = `Lendo músicas… ${p.done}/${p.total}<span class="spinner"></span>`; } });
async function rescan() {
  if (!D.folders.length) { LIB = []; if (['songs', 'albums', 'artists'].includes(view.name)) render(); return; }
  scanning = true; if (view.name === 'songs') render();
  LIB = (await U.scan(D.folders, { hideVoice: S().hideVoice, minDurationSec: S().minDur })).map(t => ({ ...t, src: 'local' }));
  scanning = false;
  if (['songs', 'albums', 'artists'].includes(view.name)) render();
}

/* ---------------- início ---------------- */
(async () => {
  const saved = await U.storeGet();
  if (saved && saved.settings) D = { ...D, ...saved, settings: { ...DEF, ...saved.settings } };
  if (!D.folders.length && !D.foldersInit) { D.folders = await U.defaultFolders(); D.foldersInit = true; save(); }
  applyLook(); updateModeBtns(); setPlaying(false); setVolume(S().volume);
  view = { name: S().startView };
  renderNav(); render();
  if (D.lastQueue && D.lastQueue.q && D.lastQueue.q.length) { st.queue = D.lastQueue.q; st.idx = Math.min(D.lastQueue.i, st.queue.length - 1); updateNowPlaying(); }
  if (S().notifications && Notification.permission === 'default') Notification.requestPermission();
  rescan();
})();
