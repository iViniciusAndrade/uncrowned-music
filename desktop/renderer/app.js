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
  eq: 'M10 20h4V4h-4v16zm-6 0h4v-8H4v8zM16 9v11h4V9h-4z',
  search: 'M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  wave: 'M7 18h2V6H7v12zm4 4h2V2h-2v20zm-8-8h2v-4H3v4zm12 4h2V6h-2v12zm4-8v4h2v-4h-2z',
  cassette: 'M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-1.4l-1.6-3H7l-1.6 3H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zm3.5 5a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm9 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM10 10.25v1.5h4v-1.5h-4z',
  cal: 'M19 4h-1V2h-2v2H8V2H6v2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2zm0 16H5V10h14v10zM7 12h5v5H7z',
  chart: 'M5 9.2h3V19H5V9.2zM10.6 5h2.8v14h-2.8V5zm5.6 8H19v6h-2.8v-6z',
  radio: 'M3.24 6.15C2.51 6.43 2 7.17 2 8v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8c0-1.11-.89-2-2-2H8.3l8.26-3.34L15.88 1 3.24 6.15zM7 20c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm13-8h-2v-2h-2v2H4V8h16v4z',
  close: 'M18.3 5.71a.996.996 0 0 0-1.41 0L12 10.59 7.11 5.7A.996.996 0 1 0 5.7 7.11L10.59 12 5.7 16.89a.996.996 0 1 0 1.41 1.41L12 13.41l4.89 4.89a.996.996 0 1 0 1.41-1.41L13.41 12l4.89-4.89c.38-.38.38-1.02 0-1.4z'
};
const ico = (n, cls = '') => `<svg viewBox="0 0 24 24" class="${cls}"><path d="${P[n]}"/></svg>`;

/* ---------------- estado e persistência ---------------- */
const DEF = {
  skin: 'edgy', theme: 'dark', accent: '#ff1f3d', radius: 12, fontScale: 1, density: 'normal', tile: 180,
  artShape: 'rounded', npBg: 'blur', blur: 70, artPulse: true, lyricsSize: 28, lyricsAlign: 'left', lyricsSource: 'letras', showLyricsNp: true,
  hideVoice: true, minDur: 30, speed: 1, pitchWithSpeed: true, volume: 0.8, autoNext: true, radio: true, showVideo: true, videoSize: 360,
  adblock: true, caseStyle: 'random', eqOn: false, eq: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0], bass: 0, gainBoost: 0, vimeoToken: '', startView: 'discover', notifications: true
};
let D = { settings: { ...DEF }, folders: [], playlists: {}, favs: {}, history: [], taste: { g: {}, a: {} }, heard: [], plays: {}, weekly: null, lastQueue: null };
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
/* ---------------- temas (estilos de turma): cores, fonte, textura e a caveira do ícone ---------------- */
const SKINS = {
  edgy: { name: 'Edgy', desc: 'Dark, vermelho sangue e glitch', accent: '#ff1f3d', mode: 'dark', dots: ['#050505', '#ff1f3d', '#f2f2f2'] },
  fadinha: { name: 'Fadinha', desc: 'Pastel, brilhinho e lacinho', accent: '#ff5fa8', mode: 'light', dots: ['#ffd6ec', '#e3c9ff', '#ff5fa8'] },
  otaku: { name: 'Otaku', desc: 'Neon, sakura e anime', accent: '#ff3fa4', mode: 'dark', dots: ['#1a1240', '#ff3fa4', '#25f4ee'] },
  oldmoney: { name: 'Old Money', desc: 'Linho, marinho, caramelo e cartola', accent: '#a8763e', mode: 'light', dots: ['#f4eee1', '#1d2b44', '#b5895a'] },
  alternativo: { name: 'Alternativo', desc: 'Terroso, granulado e fone', accent: '#e76f51', mode: 'dark', dots: ['#1d2b2a', '#e9c46a', '#e76f51'] },
  punk: { name: 'Punk', desc: 'Preto, verde neon e retícula', accent: '#39ff14', mode: 'dark', dots: ['#0b0b0b', '#39ff14', '#ff2fa0'] },
  pulseirinhas: { name: 'Pulseirinhas', desc: 'Glitter colorido e miçangas', accent: '#c77dff', mode: 'dark', dots: ['#5b2a86', '#b8336a', '#2b59c3'] },
  ninja: { name: 'Ninja Renegado', desc: 'Preto, carmesim e traço de pincel', accent: '#d62839', mode: 'dark', dots: ['#0a0809', '#8c0f1c', '#f3efe9'] },
  deserto: { name: 'Deserto Estelar', desc: 'Noite no deserto, ouro e turquesa', accent: '#2ec4b6', mode: 'dark', dots: ['#0f1a3a', '#c8923a', '#2ec4b6'] },
  ouro: { name: 'Ouro Italiano', desc: 'Dourado, magenta e linhas de mangá', accent: '#f2b632', mode: 'dark', dots: ['#3a0b2e', '#ff4fa3', '#f2b632'] },
  cela: { name: 'Cela Esmeralda', desc: 'Verde-pedra, correntes e jade', accent: '#3ddc97', mode: 'dark', dots: ['#0e231c', '#2f6b55', '#3ddc97'] },
  rock: { name: 'Rock Tóquio', desc: 'Xadrez vermelho, renda e rosas', accent: '#e01e37', mode: 'dark', dots: ['#120607', '#7a0b16', '#e8c547'] },
  pavor: { name: 'Pavor', desc: 'Terror pesado: olhos no escuro e arame', accent: '#c0001a', mode: 'dark', dots: ['#050406', '#2a2433', '#c0001a'] }
};
let logoV = Date.now();
function refreshLogos() { logoV = Date.now(); document.querySelectorAll('img').forEach(i => { const a = i.getAttribute('src') || ''; if (/^logo\.png/.test(a)) i.src = 'logo.png?v=' + logoV; }); }
function applySkin(k) {
  const sk = SKINS[k] || SKINS.edgy, s = S();
  s.skin = SKINS[k] ? k : 'edgy'; s.accent = sk.accent; s.theme = sk.mode;
  save(); applyLook(); U.setSkin(s.skin); refreshLogos(); render();
  toast(`Tema ${sk.name} aplicado`);
}
function applyLook() {
  const s = S(), r = document.documentElement;
  r.dataset.skin = SKINS[s.skin] ? s.skin : 'edgy';
  const sys = matchMedia('(prefers-color-scheme: light)').matches;
  r.classList.toggle('light', s.theme === 'light' || (s.theme === 'system' && sys));
  r.classList.toggle('black', s.theme === 'black');
  r.classList.toggle('compact', s.density === 'compact');
  r.style.setProperty('--accent', s.accent);
  // bolinha das chaves: escura quando a cor do tema é muito clara (ex: branco), pra continuar visível
  { const h = (s.accent || '#fa2d48').replace('#', ''); const v = h.length >= 6 ? [0, 2, 4].map(i => parseInt(h.substr(i, 2), 16) / 255) : [1, 0, 0];
    const L = 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2]; r.style.setProperty('--sw-knob', L > 0.75 ? '#1c1c1e' : '#fff'); }
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

/* modo retrô: fita K7 ou vinil, montado com Web Audio (o mesmo código roda no PC e dentro do YouTube/SoundCloud) */
const RETRO_SRC = `function(c, mode) {
  const inp = c.createGain(), out = c.createGain();
  if (!mode) { inp.connect(out); return { input: inp, output: out, mode: '', stop() {} }; }
  const tape = mode === 'k7';
  const bq = (type, f, g, q) => { const b = c.createBiquadFilter(); b.type = type; b.frequency.value = f; if (g != null) b.gain.value = g; if (q != null) b.Q.value = q; return b; };
  const hp = bq('highpass', tape ? 70 : 35), ls = bq('lowshelf', 180, tape ? -4 : -2), mid = bq('peaking', 1500, tape ? 2 : 1.5, 0.7), lp = bq('lowpass', tape ? 7000 : 11000, null, 0.5);
  const ws = c.createWaveShaper(), k = tape ? 2.2 : 1.4, n = 1024, cv = new Float32Array(n);
  for (let i = 0; i < n; i++) { const x = i / (n - 1) * 2 - 1; cv[i] = Math.tanh(k * x) / Math.tanh(k); } ws.curve = cv;
  const dl = c.createDelay(0.05); dl.delayTime.value = 0.012;
  const osc = (f, depth) => { const o = c.createOscillator(), g = c.createGain(); o.frequency.value = f; g.gain.value = depth; o.connect(g); g.connect(dl.delayTime); o.start(); return o; };
  const wow = osc(0.55, tape ? 0.0016 : 0.0006), flut = osc(6.5, tape ? 0.00018 : 0.00004);
  inp.connect(hp); hp.connect(ls); ls.connect(mid); mid.connect(ws); ws.connect(lp); lp.connect(dl); dl.connect(out);
  const len = c.sampleRate * 4, buf = c.createBuffer(1, len, c.sampleRate), d = buf.getChannelData(0);
  if (tape) { let b = 0; for (let i = 0; i < len; i++) { const w = Math.random() * 2 - 1; b = 0.97 * b + 0.03 * w; d[i] = (w * 0.6 + b * 2) * 0.5; } }
  else for (let i = 0; i < len; i++) {
    d[i] += (Math.random() * 2 - 1) * 0.06;
    if (Math.random() < 0.0003) d[i] += (Math.random() < 0.5 ? -1 : 1) * (0.4 + Math.random() * 0.6);
    if (Math.random() < 0.00002) for (let j = 0; j < 40 && i + j < len; j++) d[i + j] += (Math.random() * 2 - 1) * 0.6 * (1 - j / 40);
  }
  const ns = c.createBufferSource(); ns.buffer = buf; ns.loop = true;
  const nf = bq(tape ? 'bandpass' : 'highpass', tape ? 4000 : 1200), ng = c.createGain(); ng.gain.value = tape ? 0.022 : 0.05;
  ns.connect(nf); nf.connect(ng); ng.connect(out); ns.start();
  return { input: inp, output: out, mode, stop() { try { wow.stop(); flut.stop(); ns.stop(); } catch (e) {} } };
}`;
const makeRetro = new Function('return ' + RETRO_SRC)();
let retro = null;
function applyRetroLocal() {
  if (!ctx || !gainN || !ctx.createGain) return;
  const m = S().retro || '';
  if (retro && retro.mode === m) return;
  try { gainN.disconnect(); } catch {}
  if (retro) { try { retro.output.disconnect(); } catch {} retro.stop(); }
  retro = makeRetro(ctx, m); gainN.connect(retro.input); retro.output.connect(ctx.destination);
}
const RETRO_NAMES = { '': 'Normal', k7: 'Fita K7', vinil: 'Vinil' };
function setRetro(m) {
  S().retro = m; save(); applyRetroLocal(); try { embedEq(); } catch {}
  const b = $('#bRetro'); if (b) { b.classList.toggle('on', !!m); b.title = 'Modo retrô: ' + RETRO_NAMES[m]; }
  toast(m ? `Modo ${RETRO_NAMES[m]} ligado` : 'Modo retrô desligado');
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
  applyRetroLocal();
  applyEq();
}
/* equalizador também no YouTube e no SoundCloud: liga os mesmos filtros dentro do player deles */
const EQ_JS = () => { const s = S(); const cfg = JSON.stringify({ on: !!s.eqOn, g: s.eq, b: s.bass, k: s.gainBoost, fx: s.retro || '' }); return `(()=>{const C=${cfg};const R=${RETRO_SRC};
  window.__uncEls=window.__uncEls||new Set();
  if(!window.__uncEqPatched){window.__uncEqPatched=true;const op=HTMLMediaElement.prototype.play;HTMLMediaElement.prototype.play=function(){try{window.__uncEls.add(this)}catch(e){}return op.apply(this,arguments)};}
  const F=[31,62,125,250,500,1000,2000,4000,8000,16000];
  const els=new Set([...document.querySelectorAll('video,audio'),...(window.__uncEls||[])]); let n=0;
  els.forEach(m=>{
    if(!m.__uncEq){ if(!C.on && !C.fx) return; const src=m.currentSrc||m.src||''; if(!src.startsWith('blob:')) return;
      try{const c=window.__uncAC||(window.__uncAC=new AudioContext());const sr=c.createMediaElementSource(m);
        const fs=F.map((f,i)=>{const x=c.createBiquadFilter();x.type=i==0?'lowshelf':i==9?'highshelf':'peaking';x.frequency.value=f;x.Q.value=1.1;return x});
        const bf=c.createBiquadFilter();bf.type='lowshelf';bf.frequency.value=90;const gn=c.createGain();
        let x=sr;for(const y of [...fs,bf,gn]){x.connect(y);x=y}m.__uncEq={fs,bf,gn,c,rm:null};m.addEventListener('pause',()=>{if(m.__uncEq.rm&&m.__uncEq.rm.mode)c.suspend()});m.addEventListener('play',()=>c.resume());}catch(e){m.__uncEq={err:1};return}}
    const q=m.__uncEq; if(q.err) return; n++;
    if(q.rm===null||q.rm.mode!==C.fx){ try{q.gn.disconnect()}catch(e){} if(q.rm){try{q.rm.output.disconnect()}catch(e){} q.rm.stop()} q.rm=R(q.c,C.fx); q.gn.connect(q.rm.input); q.rm.output.connect(q.c.destination); }
    q.fs.forEach((f,i)=>f.gain.value=C.on?(C.g[i]||0):0); q.bf.gain.value=C.on?C.b:0; q.gn.gain.value=Math.pow(10,(C.on?C.k:0)/20);
  });
  if(window.__uncAC&&window.__uncAC.state==='suspended')window.__uncAC.resume();
  return n;})()`; };
function embedEq() {
  if (st.kind === 'yt' && yt.wv) ytExec(EQ_JS());
  else if (st.kind === 'sc') U.frameEq(EQ_JS()).catch(() => {});
}
let eqTick = 0;
function applyEq() {
  try { embedEq(); } catch {}
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
audio.addEventListener('play', () => { setPlaying(true); if (ctx && ctx.resume && ctx.state === 'suspended') ctx.resume(); });
audio.addEventListener('pause', () => { setPlaying(false); if (ctx && ctx.suspend && S().retro) ctx.suspend(); });
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
  countListen();
}, 1000);

/* ---------------- players oficiais: YouTube / SoundCloud / Vimeo ---------------- */
const loadScript = src => new Promise((ok, bad) => { if (document.querySelector(`script[src="${src}"]`)) return ok(); const s = document.createElement('script'); s.src = src; s.onload = ok; s.onerror = bad; document.head.appendChild(s); });
const yt = { wv: null }, sc = { w: null }, vm = { player: null };
let pollT = null;
function startPoll(fn) { clearInterval(pollT); pollT = setInterval(fn, 400); }
function stopEmbeds(except) {
  clearTimeout(yt.dropT);
  if (except !== 'yt' && yt.wv) {
    ytExec(`${V}&&${V}.pause()`);
    // sem usar o YouTube por 30 s: fecha o player dele inteiro pra liberar memória
    yt.dropT = setTimeout(() => {
      const inner = $('#embedInner');
      if (st.kind !== 'yt' && yt.wv && inner.dataset.kind === 'yt') { inner.innerHTML = ''; inner.dataset.kind = ''; yt.wv = null; yt.ready = false; setTimeout(() => U.trim(), 2000); }
    }, 30000);
  }
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
  // já tem uma página do YouTube aberta: só troca o vídeo no player, sem recarregar tudo (bem mais rápido)
  const fast = yt.ready && await ytExec(`(()=>{const p=document.querySelector('#movie_player');if(p&&p.loadVideoById&&location.pathname==='/watch'){p.loadVideoById('${t.id}');try{history.replaceState(null,'','/watch?v=${t.id}')}catch(e){}return true}return false})()`);
  yt.switchAt = Date.now(); yt.fast = !!fast;
  if (!fast) try { if (yt.ready) yt.wv.loadURL(url).catch(() => {}); else yt.wv.src = url; } catch { yt.wv.src = url; }
  startPoll(async () => {
    if (st.kind !== 'yt') return;
    const s = S();
    if ((s.eqOn || s.retro) && ++eqTick % 5 === 0) embedEq();
    const r = await ytExec(`(()=>{const v=${V};
      const err=document.querySelector('.ytp-error, yt-playability-error-supported-renderers, #player-error-message-container');
      const errTxt=err&&err.offsetParent!==null?err.innerText.slice(0,160):'';
      if(!v)return {none:1,err:errTxt};
      const ad=!!document.querySelector('.ad-showing');
      let skipAt=null;
      if(${S().adblock}){ const sk=[...document.querySelectorAll('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-ad-skip-button-container button,[id^="skip-button"] button,#movie_player button')]
          .find(b=>b.offsetParent!==null&&(/skip-ad|ad-skip/i.test(b.className)||/^\s*(pular|skip)/i.test(b.innerText||'')));
        if(sk){ try{sk.click()}catch(e){} const rc=sk.getBoundingClientRect(); if(rc.width) skipAt=[rc.left+rc.width/2,rc.top+rc.height/2]; } }
      if(${S().adblock} && ad){ v.muted=true; window.__uncAdMuted=true; if(isFinite(v.duration)&&v.duration>0&&v.duration<=180) v.currentTime=Math.max(0,v.duration-0.05); try{v.playbackRate=16}catch(e){} }
      else if(window.__uncAdMuted){ v.muted=false; window.__uncAdMuted=false; }
      document.querySelectorAll('ytd-enforcement-message-view-model,tp-yt-paper-dialog:has(#dismiss-button)').forEach(d=>{const b=d.querySelector('#dismiss-button button,button');if(b)b.click();});
      if(!ad){ if(Math.abs(v.playbackRate-${s.speed})>0.01) v.playbackRate=${s.speed}; v.preservesPitch=${!s.pitchWithSpeed}; }
      if(Math.abs(v.volume-${S().volume})>0.02) v.volume=${S().volume};
      return {t:v.currentTime,d:v.duration||0,p:v.paused,e:v.ended,ad,skipAt,err:errTxt,id:(()=>{try{return document.querySelector('#movie_player').getVideoData().video_id}catch(e){return new URLSearchParams(location.search).get('v')}})()};})()`);
    if (!r) return;
    // clique de verdade no "Pular" (o YouTube ignora clique simulado por script em alguns anúncios)
    if (r.skipAt && Date.now() - (yt.skipT || 0) > 1200) {
      yt.skipT = Date.now(); const [x, y] = r.skipAt.map(Math.round);
      try { yt.wv.sendInputEvent({ type: 'mouseDown', x, y, button: 'left', clickCount: 1 }); yt.wv.sendInputEvent({ type: 'mouseUp', x, y, button: 'left', clickCount: 1 }); } catch {}
    }
    if (r.err && ytWarned !== r.err) {
      ytWarned = r.err;
      const age = /idade|age|sign in|faça login|inapropriado|inappropriate/i.test(r.err);
      toast(age ? 'Vídeo +18: entre na sua conta do YouTube em Ajustes → YouTube pra liberar' : 'O YouTube não liberou esse vídeo. Pulando…');
      if (!age) setTimeout(next, 1500);
      return;
    }
    if (r.none) return;
    if (r.id && r.id !== t.id && Date.now() - yt.switchAt < 6000) {
      // troca rápida não pegou: abre a página do vídeo do jeito normal
      if (yt.fast && Date.now() - yt.switchAt > 3500) { yt.fast = false; yt.switchAt = Date.now(); yt.wv.loadURL(`https://www.youtube.com/watch?v=${t.id}`).catch(() => {}); }
      return;
    }
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
  startPoll(() => { if ((S().eqOn || S().retro) && ++eqTick % 5 === 0) embedEq(); if (sc.w) sc.w.getPosition(p => sc.w.getDuration(d => { st.time = p; st.dur = d; tick(); })); });
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
/* rádio ao vivo: tocador próprio, toca o endereço da estação direto */
const radioEl = new Audio(); radioEl.preload = 'none';
radioEl.addEventListener('play', () => { if (st.kind === 'radio') { setPlaying(true); renderNav(); } });
radioEl.addEventListener('pause', () => { if (st.kind === 'radio') { setPlaying(false); renderNav(); } });
radioEl.addEventListener('playing', () => { if (st.kind === 'radio') { $('#tDur').textContent = 'AO VIVO'; } });
radioEl.addEventListener('error', () => { if (st.kind === 'radio' && radioEl.getAttribute('src')) { toast('Essa rádio está fora do ar agora. Indo pra próxima…'); setTimeout(next, 1200); } });
function stopRadio() { if (radioEl.getAttribute('src')) { radioEl.pause(); radioEl.removeAttribute('src'); radioEl.load(); } }
async function playAt(i) {
  if (i < 0 || i >= st.queue.length) return;
  st.idx = i; const t = cur();
  if (t.src === 'radio') {
    if (recState && recState.id !== t.id) { recStop(true); toast('Gravação anterior salva. Trocou de rádio.'); }
    stopEmbeds(null); audio.pause(); clearInterval(pollT); $('#embed').classList.add('hidden');
    st.kind = 'radio'; st.time = 0; st.dur = 0;
    radioEl.src = t.stream; radioEl.volume = S().volume;
    radioEl.play().catch(() => {});
    U.radioClick(t.id);
    D.radioRecent = [t, ...(D.radioRecent || []).filter(x => x.id !== t.id)].slice(0, 12); save();
    updateNowPlaying(); markCurrent(); $('#tDur').textContent = 'AO VIVO'; $('#tCur').textContent = '';
    if (view.name === 'radio') render(); else paintRec();
    renderNav(); radioNow = ''; setTimeout(pollRadioNow, 2500);
    return;
  }
  if (recState) recStop(true);
  stopRadio();
  if (st.kind === 'radio') { st.kind = null; }
  setTimeout(renderNav, 0);
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
  if (st.kind === 'radio') { if (radioEl.paused) { radioEl.src = cur().stream; radioEl.play().catch(() => {}); } else { stopRadio(); setPlaying(false); renderNav(); } return; }
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
  if (st.idx < st.queue.length - 1 || st.repeat === 1) next();
  else if (S().radio && st.queue[st.idx]) startRadio(st.queue[st.idx], true);
  else setPlaying(false);
}
/* rádio: músicas parecidas com a atual (Mix do YouTube) */
/* rádio offline: monta a fila só com músicas do seu PC, parecidas com a atual (artista, gênero, álbum,
   época) e puxando pro que você mais ouve. Funciona sem internet. */
function localRadio(seed, n = 25) {
  const recent = new Set(st.queue.slice(-80).map(keyOf));
  const norm = x => (x || '').toLowerCase().trim();
  const sa = norm(seed && seed.artist), sg = norm(seed && seed.genre), sal = norm(seed && seed.album), sy = (seed && +seed.year) || 0;
  const maxA = Math.max(1, ...Object.values(D.taste.a || {}));
  const scored = LIB.filter(t => keyOf(t) !== keyOf(seed) && !recent.has(keyOf(t))).map(t => {
    let sc = 0.3 + Math.random() * 1.2;
    if (seed) {
      if (sa && norm(t.artist) === sa) sc += 2.5;
      if (sg && norm(t.genre) === sg) sc += 2;
      if (sal && norm(t.album) === sal) sc += 0.8;
      if (sy && t.year && Math.abs(t.year - sy) <= 3) sc += 0.8;
    }
    sc += Math.max(0, (D.taste.a[t.artist] || 0) / maxA) * 1.5 + Math.max(0, (D.taste.g[t.genre] || 0) > 0 ? 0.6 : 0);
    const p = D.plays && D.plays[keyOf(t)]; if (p) sc += Math.min(1.5, p.n * 0.15);
    if (D.favs[keyOf(t)]) sc += 1;
    return [t, sc];
  }).sort((a, b) => b[1] - a[1]);
  const per = {}, out = [];
  for (const [t] of scored) { const a = norm(t.artist); if ((per[a] = (per[a] || 0) + 1) > 3) continue; out.push(t); if (out.length >= n) break; }
  return shuffleArr(out.slice(0, Math.ceil(n * 0.4))).concat(shuffleArr(out.slice(Math.ceil(n * 0.4))));
}
function startLocalRadio(seed) {
  const l = localRadio(seed, 40);
  if (!l.length) return toast('Precisa de músicas no PC pra montar o mix offline');
  if (seed) play([seed, ...l], 0); else play(l, 0);
  toast(seed ? `Mix offline de ${seed.artist || seed.title}` : 'Mix offline: mistura das suas músicas');
}
async function startRadio(t, append) {
  if (!append) toast('Montando rádio de ' + (t.artist || t.title) + '…');
  const recent = new Set(st.queue.slice(-60).map(keyOf));
  // música do PC ou sem internet: rádio com as músicas do PC
  let l = [];
  if (t.src !== 'local' && navigator.onLine) l = (await U.radio({ src: t.src, id: t.id, title: t.title, artist: t.artist })).filter(x => !recent.has(keyOf(x))).slice(0, 25);
  if (!l.length) l = localRadio(t.src === 'local' ? t : null, 25);
  if (!l.length) { if (append) setPlaying(false); else toast('Não achei músicas parecidas agora'); return; }
  if (append) { st.queue.push(...l); next(); toast('Fila acabou: tocando parecidas'); }
  else { st.queue.splice(st.idx + 1, 0, ...l); toast(`Rádio: ${l.length} músicas parecidas na fila`); if (st.idx < 0) playAt(0); }
}
function setVolume(v) {
  S().volume = v; save();
  audio.volume = v; radioEl.volume = v;
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
// contagem por música (as mais tocadas viram ponto de partida das recomendações)
function bumpPlay(t, v) {
  D.plays = D.plays || {}; const k = keyOf(t); if (!k) return;
  const p = D.plays[k] || (D.plays[k] = { t: { src: t.src, id: t.id, title: t.title, artist: t.artist, art: t.art, duration: t.duration, path: t.path, stream: t.stream, url: t.url, genre: t.genre }, n: 0 });
  p.n += v; p.last = Date.now();
  const ks = Object.keys(D.plays); if (ks.length > 600) { ks.sort((a, b) => D.plays[a].n - D.plays[b].n).slice(0, 100).forEach(x => delete D.plays[x]); }
}
function tasteDecay() {
  const w = weekKey();
  if (D.tasteWeek && D.tasteWeek !== w) for (const m of [D.taste.g, D.taste.a]) for (const k in m) m[k] *= 0.9;
  D.tasteWeek = w;
}
function onTrackChange(t) {
  if (t && t.src === 'radio') return;
  logMoment(t);
  // quanto da música anterior você ouviu: pulou rápido = não curtiu; ouviu quase inteira = curtiu
  if (lastKey && lastMeta) {
    const el = Date.now() - startedAt, dur = lastMeta.duration || 0;
    if (el < 30000) { bump(D.taste.g, lastMeta.genre, -0.5); bump(D.taste.a, lastMeta.artist, -1); }
    else if (dur && el > dur * 0.8) { bump(D.taste.g, lastMeta.genre, 0.5); bump(D.taste.a, lastMeta.artist, 1); bumpPlay(lastMeta, 1); }
  }
  tasteDecay();
  bump(D.taste.g, t.genre, 1); bump(D.taste.a, t.artist, 1); bumpPlay(t, 1);
  const k = keyOf(t); if (!D.heard.includes(k)) { D.heard.push(k); if (D.heard.length > 3000) D.heard.shift(); }
  D.history = [t, ...D.history.filter(x => keyOf(x) !== k)].slice(0, 150);
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
  $('#npCase .caseLabel').textContent = (SRCNAME[t.src] || '').toUpperCase();
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
const SRCNAME = { local: 'No seu PC', radio: 'Rádio ao vivo', yt: 'YouTube', sc: 'SoundCloud', vm: 'Vimeo', au: 'Audius', ia: 'Internet Archive' };
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
  if (st.kind === 'radio') { $('#seekFill').style.width = '0%'; $('#tDur').textContent = 'AO VIVO'; return; }
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
  if (t.src === 'radio') { lyKey = ''; $('#lyricsSrc').textContent = ''; $('#lyrics').innerHTML = `<div class="noLy"><div class="noLyT">Rádio ao vivo</div><div class="noLyS">${esc(t.title)}${t.artist ? ' · ' + esc(t.artist) : ''}</div></div>`; return; }
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
// um clique em qualquer lugar da linha toca (menos nos botões de favoritar/baixar/mais)
let lastRowClick = 0;
document.addEventListener('click', e => {
  const r = e.target.closest('.row'); if (!r || e.target.closest('[data-act], .acts, button, a, input')) return;
  if (Date.now() - lastRowClick < 400) return; lastRowClick = Date.now();
  if (view.name === 'search' && searchQ) addRecent(searchQ);
  play(rowsCtx[r.dataset.ctx], +r.dataset.i);
});
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
      <div class="opt" data-m="radio" style="cursor:pointer">${t.src === 'local' ? 'Mix offline (parecidas do seu PC)' : 'Tocar parecidas (rádio)'}</div>
      ${!['local', 'radio'].includes(t.src) ? '<div class="opt" data-m="sync" style="cursor:pointer">Sincronizar (salvar no PC)</div>' : ''}
      <div class="opt" data-m="info" style="cursor:pointer">Informações</div>
      ${t.src === 'local' ? '<div class="opt" data-m="show" style="cursor:pointer">Mostrar no Explorador</div><div class="opt" data-m="fx" style="cursor:pointer">Fazer versão slowed / nightcore</div>' : ''}
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
    if (m === 'radio') startRadio(t, false);
    if (m === 'sync') syncTrack(t);
    if (m === 'info') return showInfo(t);
    if (m === 'show') U.openPath(t.path);
    if (m === 'fx') { exSel = new Set([t.path]); exFilter = ''; closeModal(); go({ name: 'export' }); return; }
    if (m === 'web') U.openUrl(t.url || (t.src === 'yt' ? `https://youtu.be/${t.id}` : `https://vimeo.com/${t.id}`));
    if (m === 'rm') { D.playlists[view.pl] = D.playlists[view.pl].filter(x => keyOf(x) !== keyOf(t)); save(); render(); }
    if (m === 'newpl') { const n = prompt2('Nome da playlist', name => { if (name) { D.playlists[name] = D.playlists[name] || []; addToPl(name, t); renderPlNav(); } }); return; }
    closeModal();
  };
}
function addToPl(name, t) { const l = D.playlists[name] || (D.playlists[name] = []); if (!l.some(x => keyOf(x) === keyOf(t))) { l.push(t); bump(D.taste.g, t.genre, 2); bump(D.taste.a, t.artist, 2); bumpPlay(t, 2); } save(); toast(`Adicionada em "${name}"`); }
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
const NAV = [['discover', 'Descobrir', 'explore'], ['search', 'Buscar', 'search'], ['radio', 'Rádio', 'radio'], ['moments', 'Momentos', 'cal'], ['stats', 'Mais ouvidas', 'chart']];
const NAV_OFF = [['songs', 'Músicas', 'note'], ['albums', 'Álbuns', 'album'], ['artists', 'Artistas', 'person'], ['export', 'Slowed / Nightcore', 'wave']];
function renderNav() {
  const radioOn = st.kind === 'radio' && st.playing;
  // na rádio: velocidade e sincronizar ficam desligados
  for (const id of ['#bSpeed', '#bSync', '#bRetro']) { const b = $(id); if (b) { b.disabled = st.kind === 'radio'; b.title = st.kind === 'radio' ? 'Desligado na rádio ao vivo' : id === '#bSpeed' ? 'Velocidade' : id === '#bRetro' ? 'Modo retrô: ' + RETRO_NAMES[S().retro || ''] : 'Sincronizar (salvar no PC)'; } }
  const a = ([k, l, i]) => `<a data-v="${k}" class="${view.name === k ? 'on' : ''} ${k === 'export' && radioOn ? 'off' : ''}" ${k === 'export' && radioOn ? 'title="Desativado enquanto a rádio toca"' : ''}>${ico(i)}<span>${l}</span></a>`;
  $('#nav').innerHTML = NAV.map(a).join('') + `<div class="navlabel">Offline · no seu PC</div>` + NAV_OFF.map(a).join('') + `<div class="navsep"></div>` + a(['settings', 'Ajustes', 'gear']);
  renderPlNav();
}
function renderPlNav() {
  const items = [['favs', 'Favoritas', 'heart'], ['history', 'Ouvidas recentemente', 'history'], ['import', 'Importar playlists', 'plus'], ...Object.keys(D.playlists).map(p => ['pl:' + p, p, 'plist'])];
  $('#plnav').innerHTML = items.map(([k, l, i]) => `<a data-v="${esc(k)}" class="${(view.name === 'playlist' ? 'pl:' + view.pl : view.name) === k ? 'on' : ''}">${ico(i)}<span>${esc(l)}</span></a>`).join('');
}
document.addEventListener('click', e => {
  const a = e.target.closest('nav a'); if (!a) return;
  const v = a.dataset.v;
  if (a.classList.contains('off')) return toast('Slowed / Nightcore fica desligado enquanto a rádio toca. Pause a rádio ou toque outra música.');
  if (v === 'search') { go({ name: 'search' }); $('#q').focus(); return; }
  go(v.startsWith('pl:') ? { name: 'playlist', pl: v.slice(3) } : { name: v });
});
$('#newPl').onclick = () => prompt2('Nova playlist', n => { if (n) { D.playlists[n] = D.playlists[n] || []; save(); renderPlNav(); go({ name: 'playlist', pl: n }); } });
function go(v) { if (v.name !== 'export' && typeof stopPrev === 'function') stopPrev(); view = v; openNp(false); renderNav(); render(); $('#main').scrollTop = 0; }

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
    ${l.length ? playBtns('songs').replace('</div>', `<button class="btn" id="offRadio">${ico('wave')} Mix offline</button></div>`) : ''}
    ${rows(l, 'songs', { empty: D.folders.length ? 'Nenhuma música encontrada nas pastas escolhidas.' : 'Escolha as pastas de música em Ajustes → Biblioteca. Ou use a busca pra ouvir online.' })}`;
  $('#view').querySelectorAll('[data-sort]').forEach(b => b.onclick = () => { songSort = b.dataset.sort; render(); });
  if ($('#offRadio')) $('#offRadio').onclick = () => startLocalRadio(null);
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
views.group = () => {
  const local = view.list.length && view.list.every(t => t.src === 'local');
  $('#view').innerHTML = `<h1>${esc(view.title)}</h1><div class="sub">${esc(view.sub || '')}</div>${playBtns('group').replace('</div>', local ? `<button class="btn" id="grpRadio">${ico('wave')} Mix offline</button></div>` : '</div>')}${rows(view.list, 'group')}`;
  if ($('#grpRadio')) $('#grpRadio').onclick = () => startLocalRadio(view.list[Math.floor(Math.random() * view.list.length)]);
};
views.favs = () => { const l = Object.values(D.favs); $('#view').innerHTML = `<h1>Favoritas</h1>${l.length ? playBtns('favs') : ''}${rows(l, 'favs', { empty: 'Toque no coração das músicas pra salvar aqui.' })}`; };
views.history = () => {
  const l = D.history;
  const SRC = { local: 'No PC', yt: 'YouTube', sc: 'SoundCloud', vm: 'Vimeo', au: 'Audius', ia: 'Archive' };
  rowsCtx.history = l;
  $('#view').innerHTML = `<h1>Ouvidas recentemente</h1><div class="sub">${l.length} músicas que você ouviu por último, do PC e da internet</div>
    ${l.length ? `<div class="btns"><button class="btn" data-play="history">${ico('play')} Tocar</button><button class="btn" data-shuf="history">${ico('shuffle')} Aleatório</button><button class="btn" id="histClr">Limpar histórico</button></div>` : ''}
    ${l.length ? `<div class="grid hgrid">${l.map((t, i) => `<div class="htile ${keyOf(t) === keyOf(cur()) ? 'cur' : ''}" data-hi="${i}">
      <div class="hart"><img loading="lazy" src="${esc(artOf(t))}" onerror="this.src='logo.png'"><span class="hsrc ${t.src}">${SRC[t.src] || ''}</span>
        <button class="hplay" title="Tocar">${ico('play')}</button><button class="hmore" title="Mais">${ico('more')}</button></div>
      <div class="tt">${esc(t.title)}</div><div class="ta">${esc(t.artist)}</div></div>`).join('')}</div>` : '<div class="empty">As músicas que você ouvir aparecem aqui.</div>'}`;
  $('#view').querySelectorAll('[data-hi]').forEach(d => d.onclick = e => {
    const i = +d.dataset.hi;
    if (e.target.closest('.hmore')) return menu(l[i], l, i, 'history');
    play(l, i);
  });
  if ($('#histClr')) $('#histClr').onclick = () => { D.history = []; save(); render(); };
};
views.playlist = () => {
  const l = D.playlists[view.pl] || [];
  $('#view').innerHTML = `<h1>${esc(view.pl)}</h1><div class="btns">${l.length ? `<button class="btn" data-play="pl">${ico('play')} Tocar</button><button class="btn" data-shuf="pl">${ico('shuffle')} Aleatório</button>` : ''}<button class="btn" id="delPl" style="color:#ff453a">Apagar playlist</button></div>${rows(l, 'pl', { empty: 'Use o botão ••• numa música pra adicionar aqui.' })}`;
  $('#delPl').onclick = () => { delete D.playlists[view.pl]; save(); go({ name: 'songs' }); };
};

/* busca única: no PC + online (SoundCloud → YouTube → Vimeo) + pra baixar */
let searchQ = '', searchRes = null, searchT, sFilter = 'all';
$('#q').addEventListener('focus', () => { if (view.name !== 'search' && !$('#q').value.trim()) go({ name: 'search' }); });
$('#q').addEventListener('keydown', e => { if (e.key === 'Enter') { addRecent($('#q').value.trim()); clearTimeout(searchT); e.target.dispatchEvent(new Event('input')); } });
$('#q').addEventListener('paste', () => setTimeout(() => { clearTimeout(searchT); $('#q').dispatchEvent(new Event('input')); }, 0));
$('#q').addEventListener('input', e => {
  searchQ = e.target.value.trim();
  clearTimeout(searchT);
  if (!searchQ) { searchRes = null; if (view.name === 'search') render(); return; }
  if (view.name !== 'search') { view = { name: 'search' }; openNp(false); renderNav(); }
  searchRes = null; render();
  const isLink = /^(https?:\/\/|www\.|youtu\.be|youtube\.com|m\.youtube|music\.youtube|soundcloud\.com|vimeo\.com)/i.test(searchQ);
  searchT = setTimeout(() => startLiveSearch(searchQ), isLink ? 0 : 220);
});
/* resultados entram na tela conforme cada fonte responde (YouTube primeiro) */
let searchRid = 0, sParts = {}, sShowT = null, sRaf = 0;
function startLiveSearch(q) {
  searchRid++; sFilter = 'all'; sParts = { q }; clearTimeout(sShowT);
  // espera o YouTube até 1,2 s pra lista não "pular" quando ele chegar por cima
  sShowT = setTimeout(() => { sParts.show = true; paintSearch(); }, 1200);
  U.searchLive(q, searchRid);
  const rid = searchRid;
  U.ytmSearch(q).then(r => { if (rid !== searchRid) return; sParts.ytm = r; paintSearch(); });
}
U.onSearchPart(p => {
  if (p.rid !== searchRid) return;
  sParts[p.src] = p.items; if (p.done) sParts.done = true;
  if (p.src === 'yt' || p.src === 'link' || p.done) sParts.show = true;
  paintSearch();
});
function paintSearch() {
  if (!sParts.show || sParts.q !== searchQ) return;
  const P = sParts;
  searchRes = P.link ? { stream: P.link, downloadable: [], link: true }
    : { ytm: P.ytm, stream: [...(P.yt || []).slice(0, 25), ...(P.sc || []).slice(0, 20), ...(P.vm || []).slice(0, 10)], downloadable: [...(P.au || []).filter(t => t.download), ...(P.ia || [])].slice(0, 15), audius: P.au || [], loading: !P.done };
  cancelAnimationFrame(sRaf); sRaf = requestAnimationFrame(() => { if (view.name === 'search') { const y = $('#main').scrollTop; render(); $('#main').scrollTop = y; } });
}
views.search = () => {
  if (!searchQ) return views.browse();
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
    const R = searchRes || { stream: [], downloadable: [], loading: true };
  const by = src => R.stream.filter(t => t.src === src);
  const yt = by('yt'), sc = by('sc'), vm = by('vm');
  const Y = R.ytm && Array.isArray(R.ytm.artists) ? { albums: [], songs: [], ...R.ytm } : { artists: [], albums: [], songs: [] };
  const F = [['all', 'Tudo'], ['art', 'Artistas'], ['alb', 'Álbuns'], ['yt', 'YouTube'], ['sc', 'SoundCloud'], ['local', 'No PC'], ['dl', 'Pra baixar']].filter(([k]) => k === 'all' || (k === 'art' ? Y.artists.length : k === 'alb' ? Y.albums.length : k === 'local' ? local.length : k === 'dl' ? R.downloadable.length : by(k).length) || sFilter === k);
  const nq = x => (x || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, ' ').trim();
  const artTiles = l => `<div class="hscroll">${l.map((a, i) => `<div class="tile round" data-sart="${i}"><img loading="lazy" src="${esc(a.art || 'logo.png')}" onerror="this.src='logo.png'"><div class="tt" style="text-align:center">${esc(a.name)}</div><div class="ta" style="text-align:center">Artista</div></div>`).join('')}</div>`;
  const albTiles = l => `<div class="hscroll">${l.map((a, i) => `<div class="tile" data-salb="${i}"><img loading="lazy" src="${esc(a.art || 'logo.png')}" onerror="this.src='logo.png'"><div class="tt">${esc(a.title)}</div><div class="ta">${esc([a.year, a.artist || a.type].filter(Boolean).join(' • '))}</div></div>`).join('')}</div>`;
  const chips = `<div class="chips sticky">${F.map(([k, n]) => `<button class="chip ${sFilter === k ? 'on' : ''}" data-sf="${k}">${n}</button>`).join('')}${R.loading ? '<span class="spinner"></span>' : ''}</div>`;
  let body = '';
  if (sFilter === 'all') {
    const pool = [...local, ...R.stream];
    const ql = q.toLowerCase();
    const best = local.find(t => (t.title + ' ' + t.artist).toLowerCase().includes(ql)) || R.stream[0] || local[0];
    const top4 = pool.filter(t => t !== best).slice(0, 4);
    const bestArt = Y.artists[0] && (nq(Y.artists[0].name) === nq(q) || nq(q).startsWith(nq(Y.artists[0].name))) ? Y.artists[0] : null;
    if (bestArt) {
      rowsCtx.best = [...(Y.songs || []), ...top4].slice(0, 4);
      body += `<div class="sTop">
        <div><h2>Melhor resultado</h2><div class="best" data-sart="0">
          <img class="round" src="${esc(bestArt.art || 'logo.png')}" onerror="this.src='logo.png'"><div class="bt">${esc(bestArt.name)}</div>
          <div class="ba"><span class="badge yt">ARTISTA</span>${esc(bestArt.sub || '')}</div>
          <button class="bplay" title="Abrir">${ico('play')}</button></div></div>
        <div class="sTopList"><h2>Músicas</h2>${rows(rowsCtx.best, 'best')}</div></div>`;
    } else if (best) {
      rowsCtx.best = [best, ...top4];
      body += `<div class="sTop">
        <div><h2>Melhor resultado</h2><div class="best" id="bestCard">
          <img src="${esc(artOf(best))}" onerror="this.src='logo.png'"><div class="bt">${esc(best.title)}</div>
          <div class="ba"><span class="badge ${best.src}">${{ local: 'NO PC', yt: 'YOUTUBE', sc: 'SOUNDCLOUD', vm: 'VIMEO', au: 'AUDIUS', ia: 'ARCHIVE' }[best.src] || ''}</span>${esc(best.artist)}</div>
          <button class="bplay" title="Tocar">${ico('play')}</button></div></div>
        <div class="sTopList"><h2>Músicas</h2>${rows(top4, 'sTop4')}</div></div>`;
    } else if (!R.loading) body += `<div class="empty">Nada encontrado pra "${esc(q)}".</div>`;
    if (Y.artists.length) body += `<h2>Artistas</h2>${artTiles(Y.artists)}`;
    if (Y.albums.length) body += `<h2>Álbuns</h2>${albTiles(Y.albums)}`;
    if (local.length) body += `<h2>No seu PC</h2>${rows(local.slice(0, 10), 'sLocal')}`;
    if (yt.length) body += `<h2>YouTube</h2>${rows(yt.slice(0, 10), 'sYt')}`;
    if (sc.length) body += `<h2>SoundCloud</h2>${rows(sc.slice(0, 8), 'sSc')}`;
    if (vm.length) body += `<h2>Vimeo</h2>${rows(vm.slice(0, 6), 'sVm')}`;
    if (R.downloadable.length) body += `<h2>Pra baixar</h2><div class="sub">Músicas liberadas pelos próprios artistas (Audius e Internet Archive).</div>${rows(R.downloadable, 'sDl')}`;
  } else if (sFilter === 'art') body = `<div class="grid">${Y.artists.map((a, i) => `<div class="tile round" data-sart="${i}"><img loading="lazy" src="${esc(a.art || 'logo.png')}" onerror="this.src='logo.png'"><div class="tt" style="text-align:center">${esc(a.name)}</div><div class="ta" style="text-align:center">${esc(a.sub || 'Artista')}</div></div>`).join('')}</div>`;
  else if (sFilter === 'alb') body = `<div class="grid">${Y.albums.map((a, i) => `<div class="tile" data-salb="${i}"><img loading="lazy" src="${esc(a.art || 'logo.png')}" onerror="this.src='logo.png'"><div class="tt">${esc(a.title)}</div><div class="ta">${esc([a.type, a.year, a.artist].filter(Boolean).join(' • '))}</div></div>`).join('')}</div>`;
  else {
    const l = sFilter === 'local' ? local : sFilter === 'dl' ? R.downloadable : by(sFilter);
    body = rows(l, 's_' + sFilter, { empty: R.loading ? 'Procurando…' : 'Nada aqui.' });
  }
  $('#view').innerHTML = chips + body;
  $('#view').querySelectorAll('[data-sf]').forEach(b => b.onclick = () => { sFilter = b.dataset.sf; render(); });
  if ($('#bestCard')) $('#bestCard').onclick = () => { addRecent(searchQ); play(rowsCtx.best, 0); };
  $('#view').querySelectorAll('[data-sart]').forEach(d => d.onclick = () => { addRecent(searchQ); const a = Y.artists[+d.dataset.sart]; go({ name: 'artist', id: a.id, title: a.name, art: a.art }); });
  $('#view').querySelectorAll('[data-salb]').forEach(d => d.onclick = () => { addRecent(searchQ); const a = Y.albums[+d.dataset.salb]; go({ name: 'album', id: a.id, title: a.title, art: a.art, artist: a.artist }); });
};
/* busca vazia: buscas recentes + navegar por estilo (igual Spotify) */
const BROWSE = [['Funk', '#e8115b'], ['Trap', '#8d67ab'], ['Phonk', '#1e3264'], ['Rap', '#ba5d07'], ['Sertanejo', '#e1118c'], ['Pagode', '#148a08'], ['Pop', '#477d95'], ['Rock', '#e91429'], ['Eletrônica', '#0d73ec'], ['Lo-fi', '#509bf5'], ['Drill', '#503750'], ['R&B', '#dc148c'], ['Reggaeton', '#27856a'], ['Metal', '#777777'], ['Gospel', '#8c1932'], ['MPB', '#af2896'], ['Slowed + Reverb', '#283ea8'], ['Nightcore', '#e8115b'], ['Anos 2000', '#a56752'], ['Para treinar', '#777700']];
function addRecent(q) { if (!q || q.length < 2 || /^https?:/i.test(q)) return; D.recentSearches = [q, ...(D.recentSearches || []).filter(x => x.toLowerCase() !== q.toLowerCase())].slice(0, 12); save(); }
function searchFor(q) { const i = $('#q'); i.value = q; i.dispatchEvent(new Event('input')); }
views.browse = () => {
  const rec = D.recentSearches || [];
  $('#view').innerHTML = `<h1>Buscar</h1>
    ${rec.length ? `<h2>Buscas recentes <button class="lnk" id="recClr">Limpar</button></h2><div class="chips">${rec.map((r, i) => `<span class="chip rec" data-rq="${i}">${esc(r)}<i data-rx="${i}">${ico('close')}</i></span>`).join('')}</div>` : ''}
    <h2>Navegar por tudo</h2><div class="browse">${BROWSE.map(([n, c]) => `<div class="bcard" data-bq="${esc(n)}" style="background:${c}"><b>${esc(n)}</b></div>`).join('')}</div>`;
  $('#view').querySelectorAll('[data-bq]').forEach(d => d.onclick = () => searchFor(d.dataset.bq));
  $('#view').querySelectorAll('[data-rq]').forEach(d => d.onclick = e => { const i = +d.dataset.rq; if (e.target.closest('[data-rx]')) { D.recentSearches.splice(i, 1); save(); render(); } else searchFor(rec[i]); });
  if ($('#recClr')) $('#recClr').onclick = () => { D.recentSearches = []; save(); render(); };
};


/* descobrir: gosto musical + descobertas da semana + em alta */
const GENRES = ['Electronic', 'Hip-Hop/Rap', 'Pop', 'R&B/Soul', 'Rock', 'Alternative', 'Latin', 'Lo-Fi', 'House', 'Trap', 'Deep House', 'Techno', 'Dubstep', 'Jazz', 'Acoustic', 'Funk', 'Reggae', 'Metal', 'Ambient', 'Soundtrack'];
let trendGenre = null, trendList = null;
async function weeklyOld(w) {
  const gs = topOf(D.taste.g, 3).map(e => e[0]); const genres = gs.length ? gs : ['Pop', 'Electronic', 'Hip-Hop/Rap'];
  const artists = topOf(D.taste.a, 4).map(e => e[0]);
  let pool = [];
  for (const [i, g] of genres.entries()) pool.push(...(await U.trending(g, 'week')).slice(0, 25 - i * 5));
  for (const a of artists) pool.push(...(await U.audiusSearch(a)).filter(t => t.artist.toLowerCase() !== a.toLowerCase()).slice(0, 6));
  pool.push(...(await U.trending(null, 'week')).slice(0, 10));
  let seed = w; const rnd = () => (seed = (seed * 9301 + 49297) % 233280) / 233280;
  pool = pool.filter((t, i, a) => a.findIndex(x => x.id === t.id) === i && !D.heard.includes(keyOf(t)));
  for (let i = pool.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [pool[i], pool[j]] = [pool[j], pool[i]]; }
  const per = {}; return pool.filter(t => (per[t.artist] = (per[t.artist] || 0) + 1) <= 2).slice(0, 30);
}
/* Descobertas da Semana v2: parte dos artistas que você mais ouve/curte/pula e procura artistas
   parecidos que você ainda não conhece, mais o Mix do YouTube das suas mais tocadas */
async function weekly(force) {
  const w = weekKey();
  if (!force && D.weekly && D.weekly.w === w && D.weekly.v === 2) return D.weekly.t;
  const seeds = topOf(D.taste.a, 8);
  const known = Object.entries(D.taste.a).filter(e => e[1] >= 2).map(e => e[0]);
  const plays = Object.values(D.plays || {}).sort((a, b) => b.n - a.n);
  const mixSeeds = plays.filter(p => p.t.src === 'yt').slice(0, 6).map(p => p.t.id);
  // sem muita coisa do YouTube ainda: usa as mais tocadas de qualquer lugar como ponto de partida (o app acha no YouTube)
  const salt = force ? Math.floor(Math.random() * 1e4) : 0;
  let r = { tracks: [], artists: [] };
  if (seeds.length) r = await U.recommend({ seeds, known, heard: D.heard.slice(-2000), mixSeeds, week: w + salt });
  let t = r.tracks || [];
  if (t.length < 10) t = [...t, ...(await weeklyOld(w + salt))].slice(0, 30);
  D.weekly = { w, v: 2, t, artists: r.artists || [] }; save();
  return t;
}
let chartsGl = 'BR', chartsData = {}, listsData = {}, chartsOpen = {}, auTrend = null, scData = {};
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
    <div id="wkArt"></div>
    <h2 class="chH">Mais tocadas da semana <span class="chips inline">${[['BR', 'Brasil'], ['ZZ', 'Mundo']].map(([k, n]) => `<button class="chip ${chartsGl === k ? 'on' : ''}" data-gl="${k}">${n}</button>`).join('')}</span></h2>
    <div class="chartCols">
      <div class="chartBox au" id="chAu"><span class="spinner"></span></div>
      <div class="chartBox yt" id="chYt"><span class="spinner"></span></div>
      <div class="chartBox sc" id="chSc"><span class="spinner"></span></div>
    </div>
    <div id="chVideos"></div>
    <div id="chLists"></div>
    <h2>Seu gosto musical</h2><div class="bars">${g.map(([n, v]) => `<div class="b"><span>${esc(n)}</span><i style="width:${Math.max(4, v / max * 100)}%"></i></div>`).join('') || 'Ouça umas músicas que aparece aqui.'}
      ${a.length ? `<div style="color:var(--dim);font-size:13px;margin-top:10px">Mais ouvidos: ${a.map(x => esc(x[0])).join(', ')}</div>` : ''}</div>`;
  $('#view').querySelectorAll('[data-gl]').forEach(b => b.onclick = () => { chartsGl = b.dataset.gl; chartsOpen = {}; render(); });
  $('#tasteReset').onclick = () => { D.taste = { g: {}, a: {} }; D.pickedGenres = false; D.weekly = null; save(); render(); };
  const fill = l => {
    if (!$('#wkRow')) return;
    rowsCtx.weekly = l;
    $('#wkSub').textContent = `${l.length} músicas novas pra você · renova toda semana`;
    $('#wkRow').innerHTML = l.map((t, i) => `<div class="tile" data-wi="${i}"><img loading="lazy" src="${esc(t.art)}" onerror="this.src='logo.png'"><div class="tt">${esc(t.title)}</div><div class="ta">${esc(t.artist)}</div></div>`).join('');
    $('#wkRow').querySelectorAll('[data-wi]').forEach(d => d.onclick = () => play(l, +d.dataset.wi));
    const ar = (D.weekly && D.weekly.artists) || [];
    $('#wkArt').innerHTML = ar.length ? `<h2>Artistas pra você conhecer</h2><div class="hscroll">${ar.map((a, i) => `<div class="tile round" data-ar="${i}"><img loading="lazy" src="${esc(a.pic || 'logo.png')}" onerror="this.src='logo.png'"><div class="tt">${esc(a.name)}</div><div class="ta">${a.because && a.because.length ? 'Porque você ouve ' + esc(a.because[0]) : ''}</div></div>`).join('')}</div>` : '';
    $('#wkArt').querySelectorAll('[data-ar]').forEach(d => d.onclick = () => searchFor(ar[+d.dataset.ar].name));
  };
  $('#wkPlay').onclick = () => rowsCtx.weekly && rowsCtx.weekly.length && play(rowsCtx.weekly, 0);
  $('#wkShuf').onclick = () => rowsCtx.weekly && (st.queue = shuffleArr(rowsCtx.weekly.slice()), playAt(0));
  $('#wkNew').onclick = async () => { $('#wkSub').textContent = 'Montando…'; fill(await weekly(true)); };
  weekly(false).then(l => { if (view.name === 'discover') fill(l); });
  // mais tocadas da semana: Audius, YouTube Music e SoundCloud, cada um carrega sozinho
  const gl = chartsGl;
  const block = (el, key, name, sub, list) => {
    const box = $(el); if (!box || view.name !== 'discover' || gl !== chartsGl) return;
    const open = chartsOpen[key]; const shown = open ? list : list.slice(0, 10);
    rowsCtx['ch_' + key] = list;
    box.innerHTML = `<div class="chHead"><div><b>${name}</b><span>${esc(sub)}</span></div>${list.length ? `<button class="chPlay" data-play="ch_${key}" title="Tocar tudo">${ico('play')}</button>` : ''}</div>`
      + rows(shown, 'ch_' + key, { empty: 'Não deu pra carregar agora.' })
      + (list.length > 10 ? `<button class="lnk more" data-chm="${key}">${open ? 'Mostrar menos' : `Ver todas as ${list.length}`}</button>` : '');
    const m = box.querySelector('[data-chm]'); if (m) m.onclick = () => { chartsOpen[key] = !open; block(el, key, name, sub, list); };
  };
  (async () => { if (!auTrend || !auTrend.length) auTrend = await U.trending(null, 'week'); block('#chAu', 'au', 'Audius', 'Em alta no mundo · artistas independentes', auTrend || []); })();
  (async () => { const k = gl; if (!scData[k]) { const r = await U.scCharts(k); if (r.length) scData[k] = r; } block('#chSc', 'sc', 'SoundCloud', k === 'BR' ? 'Top no Brasil' : 'Top no mundo', scData[k] || []); })();
  let c = chartsData[gl];
  if (!c) { c = await U.ytmCharts(gl); if (c.top && c.top.length) chartsData[gl] = c; }
  if (view.name !== 'discover' || !$('#chYt') || gl !== chartsGl) return;
  block('#chYt', 'yt', 'YouTube', (c.top && c.top.length ? c.title : 'O YouTube Music não respondeu agora') + '', c.top || []);
  const vids = c.videos || [];
  $('#chVideos').innerHTML = vids.length ? `<h2>Clipes em alta</h2><div class="hscroll wide">${vids.map((t, i) => `<div class="tile" data-cv="${i}"><img loading="lazy" src="${esc(t.art)}" onerror="this.src='logo.png'"><div class="tt">${esc(t.title)}</div><div class="ta">${esc(t.artist)}</div></div>`).join('')}</div>` : '';
  $('#chVideos').querySelectorAll('[data-cv]').forEach(d => d.onclick = () => play(vids, +d.dataset.cv));
  // playlists mais populares do YouTube (abre dentro do app)
  const openList = async l => {
    toast('Abrindo ' + l.title + '…');
    const t = await U.ytmPlaylist(l.id);
    if (!t.length) return toast('Não deu pra abrir essa playlist agora');
    go({ name: 'group', title: l.title, list: t, sub: `${t.length} músicas · playlist do YouTube` });
  };
  let groups = listsData[gl];
  if (!groups) { groups = await U.ytmLists(gl); if (groups.length) listsData[gl] = groups; }
  if (!groups.length && (c.lists || []).length) groups = [{ shelf: 'Playlists em alta', items: c.lists }];
  if (view.name !== 'discover' || !$('#chLists') || gl !== chartsGl) return;
  $('#chLists').innerHTML = groups.length ? `<h2>Playlists mais populares do YouTube</h2>` + groups.map((g, gi) => `<div class="plShelf">${groups.length > 1 ? `<h3>${esc(g.shelf)}</h3>` : ''}<div class="hscroll">${g.items.map((l, i) => `<div class="tile" data-cl="${gi}:${i}"><img loading="lazy" src="${esc(l.art || 'logo.png')}" onerror="this.src='logo.png'"><div class="tt">${esc(l.title)}</div><div class="ta">${esc(l.sub)}</div></div>`).join('')}</div></div>`).join('') : '';
  $('#chLists').querySelectorAll('[data-cl]').forEach(d => d.onclick = () => { const [gi, i] = d.dataset.cl.split(':').map(Number); openList(groups[gi].items[i]); });
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
  <div class="gtitle">Tema</div>
  <div class="skins">${Object.entries(SKINS).map(([k, x]) => `<div class="skin ${s.skin === k ? 'on' : ''}" data-skin="${k}"><img src="themes/${k}.png"><div><b>${x.name}</b><span>${x.desc}</span><i>${x.dots.map(c => `<em style="background:${c}"></em>`).join('')}</i></div></div>`).join('')}</div>
  <div class="gtitle">Aparência</div><div class="group">
    ${opt('Tema', seg('theme', [['system', 'Sistema'], ['dark', 'Escuro'], ['black', 'Preto total'], ['light', 'Claro']]))}
    ${opt('Cor de destaque', `<div class="dots">${ACC.map(c => `<div class="dot ${s.accent === c ? 'on' : ''}" data-acc="${c}" style="background:${c}"></div>`).join('')}<input type="color" id="accPick" value="${s.accent}" style="width:30px;height:28px;border:0;background:none"></div>`)}
    ${opt('Arredondamento', rng('radius', 0, 28, 1))}
    ${opt('Tamanho do texto', rng('fontScale', 0.85, 1.3, 0.05, v => (+v).toFixed(2) + 'x'))}
    ${opt('Tamanho das capas na grade', rng('tile', 130, 260, 10))}
    ${opt('Lista de músicas', seg('density', [['normal', 'Confortável'], ['compact', 'Compacta']]))}
    ${opt('Tela inicial', seg('startView', [['discover', 'Descobrir'], ['search', 'Buscar'], ['songs', 'Offline']]))}
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
    ${opt('Quando a fila acabar, tocar parecidas', sw('radio'), 'Igual o rádio do Spotify: continua com músicas no mesmo estilo')}
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
    ${opt('Ativar equalizador', sw('eqOn'), 'Funciona nas músicas do PC, YouTube, SoundCloud, Audius e Archive')}
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
  v.querySelectorAll('[data-skin]').forEach(d => d.onclick = () => applySkin(d.dataset.skin));
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
function live(k) { if (['radius', 'fontScale', 'tile', 'blur', 'lyricsSize', 'videoSize'].includes(k)) applyLook(); if (k === 'speed') { S().pitchWithSpeed = true; const t = document.querySelector('[data-sw="pitchWithSpeed"]'); if (t) t.classList.add('on'); applyRate(true); } if (['bass', 'gainBoost'].includes(k)) applyEq(); if (k === 'videoSize') layoutEmbed(); }
function changed(k, keepView) {
  save(); applyLook(); applyRate(); applyEq(); updateNowPlaying(); layoutEmbed();
  if (['hideVoice', 'minDur'].includes(k)) rescan();
  if (k === 'adblock') U.setAdblock(S().adblock).then(on => toast(S().adblock ? (on ? 'Bloqueador de anúncios ligado' : 'Bloqueador ligado (lista carrega na próxima abertura)') : 'Bloqueador de anúncios desligado'));
  if (k === 'lyricsSource') { lyKey = ''; }
  if (!keepView) render();
}

/* ---------------- página do artista e do álbum (YouTube Music) ---------------- */
const artistCache = {}, albumCache = {};
let artAll = false;
views.artist = async () => {
  const v0 = view;
  const a = artistCache[view.id];
  const hero = (name, art, sub) => `<div class="aHero" style="--img:url('${esc(art || 'logo.png')}')"><div class="aBg"></div>
    <img class="aImg" src="${esc(art || 'logo.png')}" onerror="this.src='logo.png'"><div class="aTxt"><span>Artista</span><h1>${esc(name)}</h1><small>${esc(sub || '')}</small></div></div>`;
  if (!a) {
    $('#view').innerHTML = hero(view.title, view.art, '') + '<span class="spinner"></span>';
    const r = await U.ytmArtist(view.id);
    if (view !== v0) return;
    if (!r.name && r.err) { $('#view').innerHTML = hero(view.title, view.art, '') + '<div class="empty">Não deu pra abrir esse artista agora.</div>'; return; }
    artistCache[view.id] = r; artAll = false; return render();
  }
  const top = a.top || [], shown = artAll ? top : top.slice(0, 10);
  rowsCtx.artTop = top;
  const tiles = (l, k) => `<div class="hscroll">${l.map((x, i) => `<div class="tile" data-${k}="${i}"><img loading="lazy" src="${esc(x.art || 'logo.png')}" onerror="this.src='logo.png'"><div class="tt">${esc(x.title)}</div><div class="ta">${esc(x.sub || '')}</div></div>`).join('')}</div>`;
  $('#view').innerHTML = hero(a.name || view.title, a.art || view.art, a.subs) + `
    <div class="btns aBtns">${top.length ? `<button class="bigPlay" data-play="artTop" title="Tocar">${ico('play')}</button><button class="btn" data-shuf="artTop">${ico('shuffle')} Aleatório</button><button class="btn" id="artRadio">${ico('radio')} Rádio do artista</button>` : ''}</div>
    ${top.length ? `<h2>Populares</h2>${rows(shown, 'artTopV')}${top.length > 10 ? `<button class="lnk more" id="artMore">${artAll ? 'Mostrar menos' : `Ver mais (${top.length})`}</button>` : ''}` : ''}
    ${(a.albums || []).length ? `<h2>Álbuns</h2>${tiles(a.albums, 'aal')}` : ''}
    ${(a.singles || []).length ? `<h2>Singles e EPs</h2>${tiles(a.singles, 'asg')}` : ''}
    ${(a.videos || []).length ? `<h2>Vídeos</h2><div class="hscroll wide">${a.videos.map((t, i) => `<div class="tile" data-avd="${i}"><img loading="lazy" src="${esc(t.art)}" onerror="this.src='logo.png'"><div class="tt">${esc(t.title)}</div></div>`).join('')}</div>` : ''}`;
  rowsCtx.artTopV = top;
  if ($('#artMore')) $('#artMore').onclick = () => { artAll = !artAll; render(); };
  if ($('#artRadio')) $('#artRadio').onclick = () => top[0] && startRadio(top[0], false);
  const openAlb = x => go({ name: 'album', id: x.id, title: x.title, art: x.art, artist: a.name });
  $('#view').querySelectorAll('[data-aal]').forEach(d => d.onclick = () => openAlb(a.albums[+d.dataset.aal]));
  $('#view').querySelectorAll('[data-asg]').forEach(d => d.onclick = () => openAlb(a.singles[+d.dataset.asg]));
  $('#view').querySelectorAll('[data-avd]').forEach(d => d.onclick = () => play(a.videos, +d.dataset.avd));
};
views.album = async () => {
  const v0 = view;
  let al = albumCache[view.id];
  const head = (x, n) => `<div class="alHero"><img src="${esc(x.art || view.art || 'logo.png')}" onerror="this.src='logo.png'"><div><span>${esc(x.type || 'Álbum')}</span><h1>${esc(x.title || view.title)}</h1>
    <small>${esc([x.artist || view.artist, x.year, n != null ? n + ' músicas' : ''].filter(Boolean).join(' • '))}</small></div></div>`;
  if (!al) {
    $('#view').innerHTML = head({}, null) + '<span class="spinner"></span>';
    const r = await U.ytmAlbum(view.id);
    if (view !== v0) return;
    if (!r.tracks || !r.tracks.length) { $('#view').innerHTML = head({}, null) + '<div class="empty">Não deu pra abrir esse álbum agora.</div>'; return; }
    albumCache[view.id] = r; return render();
  }
  $('#view').innerHTML = head(al, al.tracks.length) + playBtns('alb') + rows(al.tracks, 'alb');
};
/* ---------------- mais ouvidas: tempo de verdade ouvindo cada música e artista ---------------- */
let listenSaveN = 0;
const dayKey = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
function countListen() {
  const t = cur(); if (!t || !st.playing) return;
  const L = D.listen || (D.listen = { days: {}, tr: {} }), dk = dayKey(new Date());
  const day = L.days[dk] || (L.days[dk] = { t: 0, a: {}, k: {} });
  day.t++;
  if (t.src !== 'radio') {
    const k = keyOf(t);
    if (t.artist) day.a[t.artist] = (day.a[t.artist] || 0) + 1;
    day.k[k] = (day.k[k] || 0) + 1;
    if (!L.tr[k]) L.tr[k] = { src: t.src, id: t.id, title: t.title, artist: t.artist, art: t.art, duration: t.duration, path: t.path, stream: t.stream, url: t.url, genre: t.genre, album: t.album, first: Date.now() };
  }
  const days = Object.keys(L.days); if (days.length > 420) days.sort().slice(0, days.length - 420).forEach(x => delete L.days[x]);
  if (++listenSaveN % 30 === 0) save();
}
const PERIODS_ST = [['week', 'Esta semana'], ['month', 'Este mês'], ['year', 'Este ano'], ['all', 'Sempre']];
let statsP = 'month';
function statsFor(p) {
  const L = D.listen || { days: {}, tr: {} }, now = new Date();
  const from = p === 'week' ? new Date(now.getFullYear(), now.getMonth(), now.getDate() - ((now.getDay() + 6) % 7))
    : p === 'month' ? new Date(now.getFullYear(), now.getMonth(), 1) : p === 'year' ? new Date(now.getFullYear(), 0, 1) : new Date(2000, 0, 1);
  const fk = dayKey(from);
  let total = 0; const a = {}, k = {}; let firstDay = null;
  for (const [dk, d] of Object.entries(L.days)) {
    if (dk < fk) continue;
    if (!firstDay || dk < firstDay) firstDay = dk;
    total += d.t;
    for (const [x, v] of Object.entries(d.a)) a[x] = (a[x] || 0) + v;
    for (const [x, v] of Object.entries(d.k)) k[x] = (k[x] || 0) + v;
  }
  const songs = Object.entries(k).filter(([x]) => L.tr[x]).sort((x, y) => y[1] - x[1]).map(([x, v]) => ({ ...L.tr[x], _s: v }));
  const artists = Object.entries(a).sort((x, y) => y[1] - x[1]).map(([name, v]) => ({ name, s: v, art: artOf(songs.find(t => t.artist === name) || {}) }));
  return { total, songs, artists, firstDay, label: PERIODS_ST.find(x => x[0] === p)[1] };
}
const fmtH = s => s >= 3600 ? `${(s / 3600).toFixed(s >= 36000 ? 0 : 1).replace('.', ',')} h` : `${Math.max(1, Math.round(s / 60))} min`;
views.stats = () => {
  const S2 = statsFor(statsP);
  const top = S2.songs.slice(0, 10), arts = S2.artists.slice(0, 10);
  rowsCtx.statsTop = S2.songs.slice(0, 50);
  const maxA = arts[0] ? arts[0].s : 1;
  $('#view').innerHTML = `<h1>Mais ouvidas</h1>
    <div class="chips">${PERIODS_ST.map(([k, n]) => `<button class="chip ${statsP === k ? 'on' : ''}" data-stp="${k}">${n}</button>`).join('')}</div>
    <div class="stNums"><div><b>${fmtH(S2.total)}</b><span>ouvindo música</span></div><div><b>${S2.songs.length}</b><span>músicas diferentes</span></div><div><b>${S2.artists.length}</b><span>artistas</span></div></div>
    ${S2.total < 60 ? '<div class="empty">Ainda não deu tempo de contar. Ouça umas músicas que seus números aparecem aqui.</div>' : `
    <div class="btns"><button class="btn fill" id="cardFan" ${arts.length ? '' : 'disabled'}>${ico('person')} Meu card de fã</button><button class="btn fill" id="cardWrap">${ico('chart')} Card da retrospectiva</button>${top.length ? `<button class="btn" data-play="statsTop">${ico('play')} Tocar minhas mais ouvidas</button>` : ''}</div>
    <div class="stCols"><div><h2>Top artistas</h2>${arts.map((x, i) => `<div class="stArt"><i>${i + 1}</i><img src="${esc(x.art || 'logo.png')}" onerror="this.src='logo.png'"><div><b>${esc(x.name)}</b><span class="bar"><em style="width:${Math.max(4, x.s / maxA * 100)}%"></em></span></div><small>${fmtH(x.s)}</small><button class="btn mini" data-fan="${i}" title="Card de fã">${ico('person')}</button></div>`).join('')}</div>
      <div><h2>Top músicas</h2>${rows(top, 'statsTop')}</div></div>`}`;
  $('#view').querySelectorAll('[data-stp]').forEach(b => b.onclick = () => { statsP = b.dataset.stp; render(); });
  if ($('#cardFan')) $('#cardFan').onclick = () => shareCard('fan', S2, arts[0]);
  if ($('#cardWrap')) $('#cardWrap').onclick = () => shareCard('wrap', S2);
  $('#view').querySelectorAll('[data-fan]').forEach(b => b.onclick = () => shareCard('fan', S2, arts[+b.dataset.fan]));
};
/* ---------------- card pra compartilhar (imagem 1080x1920, formato de story) ---------------- */
function loadImg(src) {
  return new Promise(res => {
    if (!src) return res(null);
    const i = new Image(); i.crossOrigin = 'anonymous';
    i.onload = () => res(i); i.onerror = () => res(null);
    i.src = /^https?:/.test(src) ? `/proxy?u=${encodeURIComponent(src)}` : src;
  });
}
function domColor(img) {
  try { const c = document.createElement('canvas'); c.width = c.height = 24; const x = c.getContext('2d'); x.drawImage(img, 0, 0, 24, 24); const d = x.getImageData(0, 0, 24, 24).data;
    let r = 0, g = 0, b = 0, w = 0; for (let i = 0; i < d.length; i += 4) { const s = Math.max(d[i], d[i + 1], d[i + 2]) - Math.min(d[i], d[i + 1], d[i + 2]) + 8; r += d[i] * s; g += d[i + 1] * s; b += d[i + 2] * s; w += s; }
    return [r / w, g / w, b / w].map(Math.round); } catch { return [250, 45, 72]; }
}
function coverDraw(x, img, dx, dy, dw, dh) { const s = Math.max(dw / img.width, dh / img.height), w = img.width * s, h = img.height * s; x.drawImage(img, dx + (dw - w) / 2, dy + (dh - h) / 2, w, h); }
function fitText(x, txt, max, size, weight = 900) { let s = size; do { x.font = `${weight} ${s}px Inter, "Segoe UI", Arial`; s -= 4; } while (x.measureText(txt).width > max && s > 30); return s + 4; }
function ellip(x, t, max) { if (x.measureText(t).width <= max) return t; while (t.length > 1 && x.measureText(t + '…').width > max) t = t.slice(0, -1); return t + '…'; }
async function makeCard(kind, S2, artist) {
  const W = 1080, H = 1920, c = document.createElement('canvas'); c.width = W; c.height = H; const x = c.getContext('2d');
  const mainArt = await loadImg(kind === 'fan' ? artist && artist.art : (S2.songs[0] && artOf(S2.songs[0])) || (S2.artists[0] && S2.artists[0].art));
  const logo = await loadImg('logo.png');
  const [r, g, b] = mainArt ? domColor(mainArt) : [250, 45, 72];
  const acc = `rgb(${r},${g},${b})`, accSoft = `rgba(${r},${g},${b},.35)`;
  // fundo: capa gigante desfocada + gradiente + granulado
  x.fillStyle = '#07070a'; x.fillRect(0, 0, W, H);
  if (mainArt) { x.save(); x.filter = 'blur(70px) saturate(1.4) brightness(.55)'; coverDraw(x, mainArt, -200, -200, W + 400, H + 400); x.restore(); }
  let gr = x.createLinearGradient(0, 0, 0, H); gr.addColorStop(0, `rgba(${r},${g},${b},.35)`); gr.addColorStop(.45, 'rgba(8,8,12,.55)'); gr.addColorStop(1, 'rgba(5,5,8,.96)'); x.fillStyle = gr; x.fillRect(0, 0, W, H);
  const id = x.getImageData(0, 0, W, H), d = id.data; for (let i = 0; i < d.length; i += 4) { const n = (Math.random() - 0.5) * 14; d[i] += n; d[i + 1] += n; d[i + 2] += n; } x.putImageData(id, 0, 0);
  // topo
  x.fillStyle = '#fff'; x.font = '800 34px Inter, "Segoe UI", Arial'; x.globalAlpha = .9;
  x.fillText(kind === 'fan' ? 'CARD DE FÃ' : 'MINHA RETROSPECTIVA', 80, 130); x.globalAlpha = .6; x.font = '600 30px Inter, "Segoe UI", Arial';
  x.fillText(S2.label.toUpperCase(), 80, 175); x.globalAlpha = 1;
  if (kind === 'fan') {
    const sz = 660, ax = (W - sz) / 2, ay = 260;
    x.save(); x.shadowColor = acc; x.shadowBlur = 120; x.beginPath(); x.arc(W / 2, ay + sz / 2, sz / 2, 0, Math.PI * 2); x.fillStyle = acc; x.fill(); x.restore();
    x.save(); x.beginPath(); x.arc(W / 2, ay + sz / 2, sz / 2, 0, Math.PI * 2); x.clip(); if (mainArt) coverDraw(x, mainArt, ax, ay, sz, sz); else { x.fillStyle = '#222'; x.fillRect(ax, ay, sz, sz); } x.restore();
    x.lineWidth = 10; x.strokeStyle = 'rgba(255,255,255,.9)'; x.beginPath(); x.arc(W / 2, ay + sz / 2, sz / 2 + 4, 0, Math.PI * 2); x.stroke();
    x.textAlign = 'center'; x.fillStyle = '#fff';
    const fs = fitText(x, artist.name, W - 140, 130); x.font = `900 ${fs}px Inter, "Segoe UI", Arial`; x.fillText(artist.name, W / 2, 1060);
    const songsA = S2.songs.filter(t => t.artist === artist.name);
    const stats = [[fmtH(artist.s), 'ouvindo ' + (artist.name.length > 16 ? 'esse artista' : artist.name)], [String(songsA.length), songsA.length === 1 ? 'música dele no seu top' : 'músicas diferentes'], ['#' + (S2.artists.indexOf(artist) + 1), 'no seu ranking']];
    stats.forEach(([big, small], i) => { const cx = 180 + i * 360; x.font = '900 86px Inter, "Segoe UI", Arial'; x.fillStyle = '#fff'; x.fillText(big, cx, 1220); x.font = '600 28px Inter, "Segoe UI", Arial'; x.fillStyle = 'rgba(255,255,255,.65)'; x.fillText(ellip(x, small, 320), cx, 1268); });
    if (songsA[0]) {
      x.fillStyle = 'rgba(255,255,255,.08)'; x.beginPath(); x.roundRect(80, 1350, W - 160, 200, 36); x.fill();
      const sArt = await loadImg(artOf(songsA[0])); if (sArt) { x.save(); x.beginPath(); x.roundRect(110, 1380, 140, 140, 20); x.clip(); coverDraw(x, sArt, 110, 1380, 140, 140); x.restore(); }
      x.textAlign = 'left'; x.fillStyle = 'rgba(255,255,255,.6)'; x.font = '700 26px Inter, "Segoe UI", Arial'; x.fillText('A QUE VOCÊ MAIS OUVIU', 280, 1430);
      x.fillStyle = '#fff'; x.font = '800 46px Inter, "Segoe UI", Arial'; x.fillText(ellip(x, songsA[0].title, W - 400), 280, 1490);
      x.textAlign = 'center';
    }
  } else {
    x.textAlign = 'left'; x.fillStyle = '#fff'; x.font = '900 190px Inter, "Segoe UI", Arial'; x.fillText(fmtH(S2.total), 72, 420);
    x.font = '600 38px Inter, "Segoe UI", Arial'; x.fillStyle = 'rgba(255,255,255,.7)'; x.fillText('ouvindo música', 82, 480);
    const sec = async (title, list, y0, isArt) => {
      x.fillStyle = acc; x.font = '800 30px Inter, "Segoe UI", Arial'; x.fillText(title, 82, y0);
      for (let i = 0; i < list.length; i++) {
        const it = list[i], y = y0 + 40 + i * 104;
        x.fillStyle = 'rgba(255,255,255,.35)'; x.font = '900 52px Inter, "Segoe UI", Arial'; x.fillText(String(i + 1), 82, y + 70);
        const im = await loadImg(isArt ? it.art : artOf(it));
        x.save(); x.beginPath(); if (isArt) x.arc(205, y + 50, 48, 0, Math.PI * 2); else x.roundRect(157, y + 2, 96, 96, 14); x.clip(); if (im) coverDraw(x, im, 157, y + 2, 96, 96); else { x.fillStyle = '#333'; x.fillRect(157, y, 96, 96); } x.restore();
        x.fillStyle = '#fff'; x.font = '800 40px Inter, "Segoe UI", Arial'; x.fillText(ellip(x, isArt ? it.name : it.title, 600), 280, y + 45);
        x.fillStyle = 'rgba(255,255,255,.6)'; x.font = '600 28px Inter, "Segoe UI", Arial'; x.fillText(ellip(x, isArt ? fmtH(it.s) : `${it.artist} · ${fmtH(it._s)}`, 600), 280, y + 88);
      }
    };
    await sec('TOP MÚSICAS', S2.songs.slice(0, 5), 600, false);
    await sec('TOP ARTISTAS', S2.artists.slice(0, 4), 1200, true);
  }
  // rodapé com a marca do app
  x.textAlign = 'left';
  if (logo) { x.save(); x.beginPath(); x.roundRect(80, H - 170, 84, 84, 20); x.clip(); x.drawImage(logo, 80, H - 170, 84, 84); x.restore(); }
  x.fillStyle = '#fff'; x.font = '900 40px Inter, "Segoe UI", Arial'; x.fillText('Uncrowned Music', 186, H - 122);
  x.fillStyle = 'rgba(255,255,255,.55)'; x.font = '600 26px Inter, "Segoe UI", Arial'; x.fillText(new Date().toLocaleDateString('pt-BR', { day: '2-digit', month: 'long', year: 'numeric' }), 186, H - 84);
  x.fillStyle = acc; x.fillRect(W - 80 - 160, H - 132, 160, 10);
  return c;
}
async function shareCard(kind, S2, artist) {
  if (kind === 'fan' && !artist) return toast('Ouça mais um pouco pra ter um artista no topo');
  toast('Montando o card…');
  const c = await makeCard(kind, S2, artist);
  const blob = await new Promise(r => c.toBlob(r, 'image/png'));
  const url = URL.createObjectURL(blob);
  const name = kind === 'fan' ? `Card de fã - ${artist.name}` : `Retrospectiva - ${S2.label}`;
  modal(`<div class="cardPrev"><img src="${url}"><div class="btns" style="justify-content:center"><button class="btn fill" id="cpSave">${ico('dl')} Salvar imagem</button><button class="btn" id="cpCopy">Copiar</button></div>
    <div class="sub" style="text-align:center">Formato de story (Instagram, TikTok, WhatsApp). Salva em Imagens/Uncrowned.</div></div>`);
  $('#cpSave').onclick = async () => { const f = await U.saveImage(new Uint8Array(await blob.arrayBuffer()), name); toast('Card salvo em Imagens/Uncrowned'); U.openPath(f); };
  $('#cpCopy').onclick = async () => { try { await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]); toast('Card copiado. É só colar na rede social'); } catch { toast('Não deu pra copiar. Use Salvar imagem'); } };
}

/* ---------------- importar do Spotify / YouTube Music ---------------- */
let impBusy = false, impLib = null, impSel = new Set();
U.onImportProgress(p => { const el = $('#impProg'); if (el) { el.querySelector('span').textContent = `${p.tag}: ${p.done} de ${p.total} músicas`; el.querySelector('i').style.width = (p.done / p.total * 100).toFixed(1) + '%'; } });
function impStatus(txt) { const el = $('#impProg'); if (el) { el.classList.remove('hidden'); el.querySelector('span').textContent = txt; } }
function learnTaste(tracks, w = 0.4) { tracks.forEach(t => { if (t.artist) bump(D.taste.a, t.artist, w); if (t.genre) bump(D.taste.g, t.genre, w); }); }
function savePlaylist(name, tracks) { let n = name || 'Playlist importada', i = 2; while (D.playlists[n]) n = `${name} (${i++})`; D.playlists[n] = tracks; return n; }
async function importSets(sets, liked, history) {
  if (impBusy) return toast('Já tem uma importação rodando');
  impBusy = true; let lists = 0, songs = 0;
  try {
    for (const set of sets) {
      impStatus(`Importando "${set.name}"…`);
      const tracks = set.tracks && set.tracks.length ? set.tracks : await U.importResolve(set.items || [], set.name);
      if (!tracks.length) continue;
      savePlaylist(set.name, tracks); learnTaste(tracks); lists++; songs += tracks.length; renderPlNav(); save();
    }
    if (liked && liked.length) {
      impStatus('Importando suas curtidas…');
      const tr = liked[0].src ? liked : await U.importResolve(liked, 'Curtidas');
      tr.forEach(t => { D.favs[keyOf(t)] = t; }); learnTaste(tr, 1); songs += tr.length;
    }
    if (history) for (const [a, ms] of Object.entries(history)) bump(D.taste.a, a, Math.min(15, ms / 3.6e6 * 0.6));
    D.weekly = null; save(); renderPlNav();
    impStatus(`Pronto: ${lists} ${lists === 1 ? 'playlist' : 'playlists'} e ${songs} músicas importadas. Suas Descobertas da Semana já vão usar esse gosto.`);
    toast('Importação concluída');
  } catch (e) { impStatus('A importação parou: ' + (e.message || e)); }
  impBusy = false;
}
views.import = () => {
  $('#view').innerHTML = `<h1>Importar suas músicas</h1><div class="sub">Traga suas playlists, curtidas e seu gosto do Spotify e do YouTube Music. Tudo vira playlist aqui e o app já aprende o que você curte.</div>
    <div id="impProg" class="impProg ${impBusy ? '' : 'hidden'}"><span>Importando…</span><b><i></i></b></div>
    <div class="impGrid">
      <div class="impCard"><div class="impH"><span class="dot sp"></span><b>Colar link</b></div>
        <p>Link de uma playlist (ou álbum) pública do <b>Spotify</b>, <b>YouTube</b> ou <b>YouTube Music</b>.</p>
        <div class="impRow"><input class="txt" id="impUrl" placeholder="https://open.spotify.com/playlist/…"><button class="btn fill" id="impGo">Importar</button></div></div>
      <div class="impCard"><div class="impH"><span class="dot yt"></span><b>Minha conta do YouTube Music</b></div>
        <p>Puxa todas as suas playlists e as músicas curtidas da conta que está logada no app.</p>
        <div id="impLib">${impLib ? '' : '<button class="btn fill" id="impLibGo">Ver minhas playlists</button>'}</div></div>
      <div class="impCard wide"><div class="impH"><span class="dot fl"></span><b>Arquivo com todos os seus dados</b></div>
        <p>Traz <b>tudo</b>: todas as playlists, curtidas e o histórico de quem você mais ouve.</p>
        <ol><li><b>Spotify:</b> spotify.com → Conta → Privacidade → <i>Baixar seus dados</i>. O Spotify manda um .zip por e-mail (pode levar alguns dias).</li>
          <li><b>YouTube Music:</b> takeout.google.com → marque só <i>YouTube e YouTube Music</i> → exportar. Chega um .zip.</li></ol>
        <button class="btn fill" id="impFile">Escolher arquivo (.zip, .json ou .csv)</button></div>
    </div>`;
  $('#impGo').onclick = async () => {
    const u = $('#impUrl').value.trim(); if (!u) return;
    impStatus('Lendo o link…');
    const r = await U.importLink(u);
    if (!r || r.err) return impStatus('Não deu: ' + ((r && r.err) || 'link não reconhecido'));
    importSets([{ name: r.name, items: r.items, tracks: r.tracks }]);
  };
  const paintLib = () => {
    const box = $('#impLib'); if (!box || !impLib) return;
    if (!impLib.logged) { box.innerHTML = `<p class="warn">Você ainda não entrou no YouTube pelo app.</p><button class="btn fill" id="impLogin">Entrar no YouTube</button>`; $('#impLogin').onclick = async () => { const ok = await U.ytLogin(); if (ok) { impLib = await U.ytmLibrary(); paintLib(); } }; return; }
    box.innerHTML = `<div class="impList">${impLib.lists.map((l, i) => `<label class="ck"><input type="checkbox" data-il="${i}" ${impSel.has(i) ? 'checked' : ''}> ${esc(l.title)} <small>${esc(l.sub || '')}</small></label>`).join('') || '<p>Nenhuma playlist encontrada.</p>'}</div>
      <div class="impRow"><button class="btn" id="impAll">Marcar todas</button><button class="btn fill" id="impLibImp">Importar selecionadas</button></div>`;
    box.querySelectorAll('[data-il]').forEach(c => c.onchange = () => c.checked ? impSel.add(+c.dataset.il) : impSel.delete(+c.dataset.il));
    $('#impAll').onclick = () => { impLib.lists.forEach((_, i) => impSel.add(i)); paintLib(); };
    $('#impLibImp').onclick = async () => {
      const pick = [...impSel].map(i => impLib.lists[i]); if (!pick.length) return toast('Marque pelo menos uma');
      const sets = [], liked = [];
      for (const l of pick) {
        impStatus(`Lendo "${l.title}"…`);
        const r = await U.ytmFullPlaylist(l.id);
        if (/^(VL)?LM$/.test(l.id)) liked.push(...r.tracks); else if (r.tracks.length) sets.push({ name: l.title || r.title, tracks: r.tracks });
      }
      importSets(sets, liked);
    };
  };
  if ($('#impLibGo')) $('#impLibGo').onclick = async () => { $('#impLib').innerHTML = '<span class="spinner"></span>'; impLib = await U.ytmLibrary(); paintLib(); };
  paintLib();
  $('#impFile').onclick = async () => {
    const r = await U.importPick(); if (!r) return;
    const n = r.playlists.length;
    if (!n && !r.liked.length && !Object.keys(r.history).length) return impStatus('Não achei playlists nesse arquivo. Use o .zip que o Spotify ou o Google mandaram.');
    impStatus(`Achei ${n} ${n === 1 ? 'playlist' : 'playlists'}${r.liked.length ? ` e ${r.liked.length} curtidas` : ''}. Importando…`);
    importSets(r.playlists, r.liked, r.history);
  };
};

/* ---------------- música do momento: quando e onde você ouviu cada música ---------------- */
let netNow = '';
async function refreshNet() { netNow = await U.netName().catch(() => '') || ''; }
setInterval(refreshNet, 10 * 60e3); setTimeout(refreshNet, 2000);
function logMoment(t) {
  if (!t) return;
  const k = keyOf(t); if (!k) return;
  D.moments = D.moments || []; D.mtr = D.mtr || {};
  D.mtr[k] = { src: t.src, id: t.id, title: t.title, artist: t.artist, art: t.art, duration: t.duration, path: t.path, stream: t.stream, url: t.url, genre: t.genre, album: t.album, download: t.download };
  D.moments.push([Math.floor(Date.now() / 1000), k, netNow]);
  if (D.moments.length > 30000) D.moments.splice(0, D.moments.length - 30000);
}
const placeOf = net => (D.places || {})[net] || '';
const PERIODS = [['madrugada', 'de madrugada', 0, 5], ['manha', 'de manhã', 6, 11], ['tarde', 'à tarde', 12, 17], ['noite', 'à noite', 18, 23]];
const DIAS = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];
const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
const periodOf = h => PERIODS.find(p => h >= p[2] && h <= p[3]);
// monta a playlist com as mais ouvidas no recorte escolhido
function momentList(filter) {
  const cnt = new Map();
  for (const [ts, k, net] of D.moments || []) { const d = new Date(ts * 1000); if (filter(d, net) && D.mtr[k]) cnt.set(k, (cnt.get(k) || 0) + 1); }
  return [...cnt.entries()].sort((a, b) => b[1] - a[1]).slice(0, 60).map(([k, n]) => ({ ...D.mtr[k], _n: n }));
}
function openMoment(title, filter) {
  const l = momentList(filter);
  if (!l.length) return toast('Ainda não tem músicas nesse momento');
  const total = l.reduce((a, t) => a + t._n, 0);
  go({ name: 'group', title, list: l, sub: `${l.length} músicas · ouvidas ${total} vezes nesse momento` });
}
views.moments = () => {
  const M = D.moments || [];
  const now = new Date(), per = periodOf(now.getHours());
const seenM = new Set(M.map(m => { const d = new Date(m[0] * 1000); return d.getFullYear() * 100 + d.getMonth(); }));
  const months = [...seenM].sort((a, b) => b - a).slice(0, 12).map(k => [Math.floor(k / 100), k % 100]);
  const nets = [...new Set(M.map(m => m[2]).filter(Boolean))];
  const places = [...new Set(nets.map(placeOf).filter(Boolean))];
  const grid = Array.from({ length: 7 }, () => [0, 0, 0, 0]); let gmax = 1;
  for (const [ts] of M) { const d = new Date(ts * 1000); const pi = PERIODS.indexOf(periodOf(d.getHours())); grid[d.getDay()][pi]++; gmax = Math.max(gmax, grid[d.getDay()][pi]); }
  const unknown = netNow && !placeOf(netNow);
  const cards = [
    ['agora', `Suas músicas de ${DIAS[now.getDay()]} ${per[1]}`, 'O que você costuma ouvir nesse dia e horário'],
    ['mes', `O que eu ouvia em ${MESES[now.getMonth()]}`, 'As mais ouvidas deste mês'],
    ['mespass', `O que eu ouvia em ${MESES[(now.getMonth() + 11) % 12]}`, 'As mais ouvidas do mês passado'],
    ['fds', 'Fins de semana', 'Sábados e domingos'],
    ['madru', 'Madrugadas', 'Da meia-noite às 6h'],
    ...places.map(p => ['pl:' + p, p === 'Casa' ? 'Em casa' : p === 'Trampo' ? 'No trampo' : p, 'Quando você estava ' + (p === 'Casa' ? 'em casa' : p === 'Trampo' ? 'no trampo' : 'em ' + p)])
  ];
  $('#view').innerHTML = `<h1>Música do momento</h1><div class="sub">O app lembra quando e onde você ouviu cada música e monta playlists dos seus momentos. ${M.length ? `${M.length} músicas registradas.` : 'Começa a contar a partir de agora.'}</div>
    ${unknown ? `<div class="placeAsk"><div><b>Você está na rede “${esc(netNow)}”.</b><span>Que lugar é esse? Assim o app separa suas músicas por lugar.</span></div>
      <div class="btns">${['Casa', 'Trampo', 'Na rua'].map(p => `<button class="btn" data-place="${p}">${p}</button>`).join('')}<input class="txt" id="placeOther" placeholder="Outro (ex: Academia)"></div></div>` : ''}
    <div class="momCards">${cards.map(([k, t, sub], i) => `<div class="momCard c${i % 6}" data-mom="${esc(k)}"><b>${esc(t)}</b><span>${esc(sub)}</span>${ico('play')}</div>`).join('')}</div>
    ${months.length ? `<h2>Por mês</h2><div class="chips">${months.map(([y, m]) => `<button class="chip" data-mm="${y}-${m}">${MESES[m][0].toUpperCase() + MESES[m].slice(1)}${y !== now.getFullYear() ? ' ' + y : ''}</button>`).join('')}</div>` : ''}
    <h2>Dia e horário</h2><div class="sub">Quanto mais forte a cor, mais música você ouve nesse horário. Clique pra tocar.</div>
    <div class="heat"><span></span>${PERIODS.map(p => `<span class="hh">${p[0] === 'manha' ? 'manhã' : p[0]}</span>`).join('')}
      ${grid.map((row, d) => `<span class="hd">${DIAS[d].slice(0, 3)}</span>${row.map((v, pi) => `<div class="hc" data-hc="${d}:${pi}" style="--a:${v ? 0.15 + 0.85 * v / gmax : 0}" title="${v} músicas">${v || ''}</div>`).join('')}`).join('')}</div>
    ${nets.length ? `<h2>Lugares</h2><div class="sub">Redes em que você ouviu música. Dê um nome pra cada uma.</div><div class="nets">${nets.map(n => `<div class="netRow"><span>${esc(n)}</span><input class="txt" data-net="${esc(n)}" value="${esc(placeOf(n))}" placeholder="Nome do lugar"></div>`).join('')}</div>` : ''}`;
  const v = $('#view');
  v.querySelectorAll('[data-place]').forEach(b => b.onclick = () => { D.places = D.places || {}; D.places[netNow] = b.dataset.place; save(); render(); });
  const po = $('#placeOther'); if (po) po.onkeydown = e => { if (e.key === 'Enter' && po.value.trim()) { D.places = D.places || {}; D.places[netNow] = po.value.trim(); save(); render(); } };
  v.querySelectorAll('[data-net]').forEach(i => i.onchange = () => { D.places = D.places || {}; if (i.value.trim()) D.places[i.dataset.net] = i.value.trim(); else delete D.places[i.dataset.net]; save(); render(); });
  v.querySelectorAll('[data-mom]').forEach(c => c.onclick = () => {
    const k = c.dataset.mom, t = c.querySelector('b').textContent;
    const ym = (d, y, m) => d.getFullYear() === y && d.getMonth() === m;
    const f = k === 'agora' ? d => d.getDay() === now.getDay() && periodOf(d.getHours()) === per
      : k === 'mes' ? d => ym(d, now.getFullYear(), now.getMonth())
      : k === 'mespass' ? (() => { const p = new Date(now.getFullYear(), now.getMonth() - 1, 1); return d => ym(d, p.getFullYear(), p.getMonth()); })()
      : k === 'fds' ? d => d.getDay() === 0 || d.getDay() === 6
      : k === 'madru' ? d => d.getHours() < 6
      : k.startsWith('pl:') ? (d, net) => placeOf(net) === k.slice(3) : () => false;
    openMoment(t, f);
  });
  v.querySelectorAll('[data-mm]').forEach(b => b.onclick = () => { const [y, m] = b.dataset.mm.split('-').map(Number); openMoment(`O que eu ouvia em ${MESES[m]}${y !== now.getFullYear() ? ' de ' + y : ''}`, d => d.getFullYear() === y && d.getMonth() === m); });
  v.querySelectorAll('[data-hc]').forEach(c => c.onclick = () => { const [d, pi] = c.dataset.hc.split(':').map(Number); const p = PERIODS[pi]; openMoment(`Suas músicas de ${DIAS[d]} ${p[1]}`, x => x.getDay() === d && periodOf(x.getHours()) === p); });
};

/* ---------------- sincronizar: salvar a música no PC (pasta Música/Uncrowned) ---------------- */
async function syncTrack(t) {
  if (!t) return;
  if (t.src === 'radio') return toast('Na rádio use o botão Gravar');
  if (t.src === 'local') return toast('Essa já está no seu PC');
  if (t.src === 'yt' || t.src === 'vm') return toast(`${t.src === 'yt' ? 'YouTube' : 'Vimeo'} não permite salvar as músicas. Dá pra sincronizar do Audius, Internet Archive e do SoundCloud quando o artista libera.`);
  toast(`Sincronizando "${t.title}"…`);
  const res = t.src === 'sc' ? await U.scDownload(t.id) : t.download ? await U.download(t.download, `${t.artist} - ${t.title}`) : { ok: false, error: 'o artista não liberou o download dessa música' };
  toast(res.ok ? `"${t.title}" salva na pasta Música/Uncrowned` : 'Não deu pra sincronizar: ' + res.error);
  if (res.ok) rescan();
}
/* ---------------- rádio ---------------- */
let recState = null, recTimer = null, radioNow = '', radioNowT = null, huntNow = {};
/* nome da música que está passando na rádio (lido do sinal da própria rádio) */
async function pollRadioNow() {
  clearTimeout(radioNowT);
  const t = cur(); if (!t || t.src !== 'radio' || st.kind !== 'radio') { radioNow = ''; return; }
  const title = await U.icyNow(t.stream);
  if (cur() !== t) return;
  if (title !== null && title !== radioNow) {
    radioNow = title || '';
    $('#barArtist').textContent = radioNow ? '♪ ' + radioNow : t.artist;
    if (view.name === 'radio' && !recState) paintRec();
  }
  radioNowT = setTimeout(pollRadioNow, 25000);
}
/* Caçador de rádio */
function huntArtists() {
  const h = D.hunt || {};
  const auto = h.auto !== false ? topOf(D.taste.a, 15).map(e => e[0]) : [];
  return [...new Set([...(h.extra || []), ...auto])].filter(a => !(h.off || []).includes(a));
}
async function huntSync() {
  const h = D.hunt || (D.hunt = { on: false });
  const r = await U.huntConfig({ on: !!h.on, artists: huntArtists(), stations: D.radioFavs || [], useTop: true, country: h.country || 'BR', max: 45 });
  h.count = r.stations; if (view.name === 'radio') { const c = $('#huntCount'); if (c) c.textContent = r.stations; }
}
U.onHuntNow(m => { huntNow = m || {}; if (view.name === 'radio') document.querySelectorAll('[data-now]').forEach(el => { const n = huntNow[el.dataset.now]; el.textContent = n ? '♪ ' + n.title : ''; }); });
U.onHuntHit(hit => {
  D.huntHits = [hit, ...(D.huntHits || [])].slice(0, 30); save();
  const go2 = () => { U.focusWin(); play([hit.station], 0); };
  try { const n = new Notification(`Tocando agora na ${hit.station.title}`, { body: hit.title, icon: 'logo.png', silent: false }); n.onclick = go2; } catch {}
  toast(`🎯 ${hit.station.title} está tocando ${hit.title}`);
  if (view.name === 'radio') render();
});
function huntCard() {
  const h = D.hunt || {}, arts = huntArtists(), hits = (D.huntHits || []).slice(0, 6);
  return `<div class="hunt ${h.on ? 'on' : ''}">
    <div class="huntTop"><div><b>🎯 Caçador de rádio</b><span>${h.on ? `Vigiando <i id="huntCount">${h.count || '…'}</i> rádios a cada 2 minutos. Quando tocar quem você curte, chega um aviso.` : 'Liga e o app fica de olho nas rádios pra você. Quando tocar um artista que você curte, ele avisa.'}</span></div><div class="sw ${h.on ? 'on' : ''}" id="huntSw"></div></div>
    ${h.on ? `<div class="huntArts">${arts.map(a => `<span class="chip">${esc(a)}<i data-hx="${esc(a)}">${ico('close')}</i></span>`).join('') || '<span class="sub">Ouça umas músicas ou adicione artistas.</span>'}
      <input id="huntAdd" class="txt" placeholder="+ artista"></div>
      ${hits.length ? `<div class="huntHits"><small>Achados recentes</small>${hits.map((x, i) => `<div class="hhit" data-hh="${i}"><b>${esc(x.title)}</b><span>${esc(x.station.title)} · ${new Date(x.at).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</span></div>`).join('')}</div>` : ''}` : ''}
  </div>`;
}
function bindHunt() {
  const h = D.hunt || (D.hunt = { on: false });
  const sw2 = $('#huntSw'); if (sw2) sw2.onclick = () => { h.on = !h.on; save(); huntSync(); render(); if (h.on && Notification.permission === 'default') Notification.requestPermission(); };
  document.querySelectorAll('[data-hx]').forEach(x => x.onclick = () => { const a = x.dataset.hx; h.extra = (h.extra || []).filter(e => e !== a); if (!(h.extra || []).includes(a)) h.off = [...new Set([...(h.off || []), a])]; save(); huntSync(); render(); });
  const add = $('#huntAdd'); if (add) add.onkeydown = e => { if (e.key === 'Enter' && add.value.trim()) { const a = add.value.trim(); h.extra = [...new Set([a, ...(h.extra || [])])]; h.off = (h.off || []).filter(x => x !== a); save(); huntSync(); render(); } };
  document.querySelectorAll('[data-hh]').forEach(x => x.onclick = () => play([(D.huntHits || [])[+x.dataset.hh].station], 0));
}
function recLabel() { if (!recState) return ''; const s = Math.floor((Date.now() - recState.start) / 1000); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; }
async function recStart() {
  const t = cur(); if (!t || t.src !== 'radio' || st.kind !== 'radio') return toast('Toque uma rádio primeiro');
  const r = await U.recStart({ title: t.title, stream: t.stream }, { split: D.recSplit !== false });
  if (r.err) return toast('Não deu pra gravar: ' + r.err);
  recState = { id: t.id, title: t.title, start: Date.now(), split: r.split, song: '', parts: 0 };
  clearInterval(recTimer); recTimer = setInterval(() => { const el = $('#recTime'); if (el) el.textContent = recLabel(); const b = $('#barRec'); if (b) b.textContent = '● REC ' + recLabel(); }, 1000);
  toast('Gravando ' + t.title + '…'); paintRec();
}
async function recStop(quiet) { if (!recState) return; await U.recStop(); if (!quiet) toast('Salvando a gravação…'); }
U.onRecDone(r => {
  recState = null; clearInterval(recTimer); paintRec();
  const n = (r.files || []).length;
  if (n) { toast(r.split ? `${n} ${n === 1 ? 'música salva' : 'músicas salvas'}, uma por arquivo, na pasta Música` : 'Gravação salva na pasta Música'); rescan(); }
  else toast('A gravação não deu certo: ' + (r.err || 'sem áudio').slice(0, 90));
});
U.onRecSong(r => { if (!recState) return; recState.song = r.title; recState.parts = r.part; const el = $('#recSong'); if (el) el.textContent = r.title ? '♪ ' + r.title : ''; });
function paintRec() {
  const box = $('#recBox'); if (box) box.outerHTML = recBoxHtml();
  bindRec();
  let b = $('#barRec');
  if (recState && !b) { b = document.createElement('span'); b.id = 'barRec'; $('#barLeft .barText').appendChild(b); }
  if (b) { if (recState) b.textContent = '● REC ' + recLabel(); else b.remove(); }
}
function recBoxHtml() {
  const t = cur(); const live = t && t.src === 'radio' && st.kind === 'radio';
  if (!live && !recState) return '<div id="recBox"></div>';
  return `<div id="recBox" class="recBox ${recState ? 'on' : ''}">
    <div class="recInfo"><b>${recState ? 'Gravando' : 'Tocando agora'}: ${esc(recState ? recState.title : t.title)}</b>
      <span>${recState ? `<i id="recTime">${recLabel()}</i> · <em id="recSong">${recState.song ? '♪ ' + esc(recState.song) : ''}</em>` : (radioNow ? '♪ ' + esc(radioNow) : 'Grave o que está passando e ouça depois, até sem internet')}</span>
      ${recState ? '' : `<label class="ck small"><input type="checkbox" id="recSplit" ${D.recSplit !== false ? 'checked' : ''}> Separar um arquivo por música (quando a rádio informa o nome)</label>`}</div>
    <button class="btn ${recState ? '' : 'fill'} recBtn" id="recBtn">${recState ? '■ Parar e salvar' : '● Gravar'}</button></div>`;
}
function bindRec() { const b = $('#recBtn'); if (b) b.onclick = () => recState ? recStop() : recStart(); const c = $('#recSplit'); if (c) c.onchange = () => { D.recSplit = c.checked; save(); }; }
const RTAGS = [['', 'Mais ouvidas'], ['funk', 'Funk'], ['sertanejo', 'Sertanejo'], ['pagode', 'Pagode'], ['pop', 'Pop'], ['rock', 'Rock'], ['hip hop', 'Rap / Hip Hop'], ['electronic', 'Eletrônica'], ['gospel', 'Gospel'], ['mpb', 'MPB'], ['news', 'Notícias'], ['sports', 'Esportes'], ['80s', 'Anos 80'], ['lofi', 'Lo-fi']];
let rCountry = 'BR', rTag = '', rQ = '', rList = null, rLoadKey = '', rQT = null;
function stationCard(t, i, ctx) {
  const on = cur() && cur().src === 'radio' && cur().id === t.id;
  const fav = (D.radioFavs || []).some(x => x.id === t.id);
  return `<div class="stn ${on ? 'on' : ''}" data-st="${ctx}:${i}">
    <div class="stnArt">${t.art ? `<img loading="lazy" src="${esc(t.art)}" onerror="this.remove()">` : ''}${ico('radio')}</div>
    <div class="stnTxt"><b>${esc(t.title)}</b><span>${esc(t.artist || '')}${t.bitrate ? ` · ${t.bitrate} kbps` : ''}</span><span class="stnNow" data-now="${esc(t.id)}">${huntNow[t.id] ? '♪ ' + esc(huntNow[t.id].title) : ''}</span></div>
    ${on ? '<i class="live">AO VIVO</i>' : ''}
    <button class="stnFav" data-sf="${ctx}:${i}" title="Favoritar">${ico(fav ? 'heart' : 'heartO')}</button></div>`;
}
views.radio = () => {
  const favs = D.radioFavs || [], rec = (D.radioRecent || []).filter(x => !favs.some(f => f.id === x.id)).slice(0, 8);
  const key = `${rCountry}|${rTag}|${rQ}`;
  $('#view').innerHTML = `<h1>Rádio</h1><div class="sub">Estações ao vivo pela internet, do Brasil e do mundo.</div>
    ${recBoxHtml()}
    ${huntCard()}
    <div class="exTools"><div class="search mini">${ico('search')}<input id="rQ" placeholder="Procurar rádio (ex: Jovem Pan, Antena 1, 89 FM)" value="${esc(rQ)}"></div>
      <div class="seg">${[['BR', 'Brasil'], ['ZZ', 'Mundo']].map(([k, n]) => `<button class="${rCountry === k ? 'on' : ''}" data-rc="${k}">${n}</button>`).join('')}</div></div>
    <div class="chips">${RTAGS.map(([k, n]) => `<button class="chip ${rTag === k ? 'on' : ''}" data-rt="${k}">${n}</button>`).join('')}</div>
    ${favs.length ? `<h2>Suas rádios</h2><div class="stns">${favs.map((t, i) => stationCard(t, i, 'f')).join('')}</div>` : ''}
    ${rec.length ? `<h2>Ouvidas recentemente</h2><div class="stns">${rec.map((t, i) => stationCard(t, i, 'r')).join('')}</div>` : ''}
    <h2>${rQ ? `Resultados pra "${esc(rQ)}"` : (RTAGS.find(x => x[0] === rTag) || ['', ''])[1] + (rCountry === 'BR' ? ' no Brasil' : ' no mundo')}</h2>
    <div class="stns" id="rList">${rList && rLoadKey === key ? (rList.length ? rList.map((t, i) => stationCard(t, i, 'l')).join('') : '<div class="empty">Nenhuma rádio encontrada.</div>') : '<span class="spinner"></span>'}</div>`;
  const lists = { f: favs, r: rec, l: rList || [] };
  bindRec(); bindHunt();
  $('#view').querySelectorAll('[data-rc]').forEach(b => b.onclick = () => { rCountry = b.dataset.rc; render(); });
  $('#view').querySelectorAll('[data-rt]').forEach(b => b.onclick = () => { rTag = b.dataset.rt; rQ = ''; render(); });
  $('#rQ').oninput = e => { clearTimeout(rQT); rQT = setTimeout(() => { rQ = e.target.value.trim(); render(); const i = $('#rQ'); i.focus(); i.setSelectionRange(i.value.length, i.value.length); }, 350); };
  $('#view').querySelectorAll('[data-st]').forEach(d => d.onclick = e => {
    const [c, i] = d.dataset.st.split(':'); const l = lists[c], t = l[+i];
    if (e.target.closest('[data-sf]')) {
      const f = D.radioFavs || (D.radioFavs = []); const k = f.findIndex(x => x.id === t.id);
      if (k >= 0) f.splice(k, 1); else f.unshift(t); save(); render(); return;
    }
    if (cur() && cur().id === t.id && st.kind === 'radio') return toggle();
    play(l, +i);
  });
  if (!(rList && rLoadKey === key)) U.radios({ q: rQ, tag: rTag, country: rCountry }).then(l => { rList = l; rLoadKey = key; if (view.name === 'radio') render(); });
};

/* ---------------- exportar slowed / nightcore ---------------- */
const EX_PRE = { slowed: ['Slowed', 0.85, false], slowrev: ['Slowed + Reverb', 0.8, true], nightcore: ['Nightcore', 1.25, false], custom: ['Personalizado', null, null] };
let exRetro = '', exPreset = 'slowrev', exRate = 0.8, exReverb = true, exBass = false, exFilter = '', exSel = new Set(), exRun = null, exPrev = null;
function exLabel() {
  if (exPreset !== 'custom') return EX_PRE[exPreset][0];
  return (exRate < 1 ? 'Slowed' : exRate > 1 ? 'Nightcore' : 'Edit') + (exReverb ? ' + Reverb' : '');
}
function stopPrev() { if (exPrev) { exPrev.pause(); exPrev = null; } const b = $('#exPrev'); if (b) b.innerHTML = `${ico('play')} Ouvir prévia`; }
views.export = () => {
  if (st.kind === 'radio' && st.playing) { $('#view').innerHTML = `<h1>Slowed / Nightcore</h1><div class="empty">Desligado enquanto a rádio toca.<br>Pause a rádio ou toque uma música pra usar.</div>`; return; }
  const q = exFilter.toLowerCase();
  const list = LIB.filter(t => !q || (t.title + ' ' + t.artist + ' ' + t.album).toLowerCase().includes(q)).sort((a, b) => a.title.localeCompare(b.title));
  const vis = list.slice(0, 400);
  $('#view').innerHTML = `<h1>Slowed / Nightcore</h1>
    <div class="sub">Cria versões das músicas do seu PC com outra velocidade e tom, e salva direto na sua pasta Música. Elas aparecem em Músicas depois.</div>
    <div class="exBox">
      <div class="seg exSeg">${Object.entries(EX_PRE).map(([k, [n]]) => `<button class="${exPreset === k ? 'on' : ''}" data-ep="${k}">${n}</button>`).join('')}</div>
      <div class="exRow"><span>Velocidade e tom</span><input type="range" id="exRate" min="0.5" max="1.5" step="0.01" value="${exRate}"><b id="exRateV"></b></div>
      <div class="exRow"><label class="ck"><input type="checkbox" id="exRev" ${exReverb ? 'checked' : ''}> Reverb (eco de ambiente)</label><label class="ck"><input type="checkbox" id="exBass" ${exBass ? 'checked' : ''}> Graves extra</label>
        <span class="ck">Som: <span class="seg exRetro">${[['', 'Normal'], ['k7', 'Fita K7'], ['vinil', 'Vinil']].map(([k, n]) => `<button class="${exRetro === k ? 'on' : ''}" data-er="${k}">${n}</button>`).join('')}</span></span>
        <button class="btn" id="exPrev">${ico('play')} Ouvir prévia</button></div>
    </div>
    <div class="exTools"><div class="search mini">${ico('search')}<input id="exQ" placeholder="Filtrar músicas do PC" value="${esc(exFilter)}"></div>
      <button class="btn" id="exAll">Marcar ${vis.length === list.length ? 'todas' : 'visíveis'}</button><button class="btn" id="exNone">Desmarcar</button></div>
    <div class="list exList">${vis.map(t => `<label class="row exr ${exSel.has(t.path) ? 'sel' : ''}"><input type="checkbox" data-ex="${esc(t.path)}" ${exSel.has(t.path) ? 'checked' : ''}>
      <img loading="lazy" src="${esc(artOf(t))}" onerror="this.outerHTML='<div class=ph></div>'"><span class="t">${esc(t.title)}</span><span class="a">${esc(t.artist)}</span><span class="d">${t.duration ? fmt(t.duration) : ''}</span></label>`).join('') || `<div class="empty">${LIB.length ? 'Nada com esse nome.' : 'Nenhuma música do PC ainda. Escolha as pastas em Ajustes → Biblioteca.'}</div>`}</div>
    ${list.length > vis.length ? `<div class="sub">Mostrando 400 de ${list.length}. Use o filtro pra achar as outras.</div>` : ''}
    <div class="exBar"><div class="exInfo" id="exInfo"></div><div class="exProg"><i id="exPg"></i></div>
      <button class="btn" id="exOpen">Abrir pasta Música</button><button class="btn fill" id="exGo"></button></div>`;
  const upd = () => {
    $('#exRateV').textContent = `${exRate.toFixed(2)}x · ${exRate < 1 ? 'mais lento e grave' : exRate > 1 ? 'mais rápido e agudo' : 'normal'}`;
    if (!exRun) { $('#exGo').textContent = exSel.size ? `Exportar ${exSel.size} como "${exLabel()}"` : 'Marque as músicas'; $('#exGo').disabled = !exSel.size; }
  };
  upd();
  $('#view').querySelectorAll('[data-ep]').forEach(b => b.onclick = () => { exPreset = b.dataset.ep; const p = EX_PRE[exPreset]; if (p[1] != null) { exRate = p[1]; exReverb = p[2]; } render(); });
  $('#exRate').oninput = e => { exRate = +e.target.value; exPreset = 'custom'; $('#view').querySelectorAll('[data-ep]').forEach(b => b.classList.toggle('on', b.dataset.ep === 'custom')); if (exPrev) { exPrev.playbackRate = exRate; } upd(); };
  $('#exRev').onchange = e => { exReverb = e.target.checked; if (exPreset !== 'custom' && exReverb !== EX_PRE[exPreset][2]) { exPreset = 'custom'; render(); } else upd(); };
  $('#exBass').onchange = e => { exBass = e.target.checked; };
  $('#view').querySelectorAll('[data-er]').forEach(b => b.onclick = () => { exRetro = b.dataset.er; render(); });
  $('#exQ').oninput = e => { exFilter = e.target.value; clearTimeout(exQT); exQT = setTimeout(() => { render(); const i = $('#exQ'); i.focus(); i.setSelectionRange(i.value.length, i.value.length); }, 200); };
  $('#view').querySelectorAll('[data-ex]').forEach(c => c.onchange = () => { c.checked ? exSel.add(c.dataset.ex) : exSel.delete(c.dataset.ex); c.closest('.exr').classList.toggle('sel', c.checked); upd(); });
  $('#exAll').onclick = () => { vis.forEach(t => exSel.add(t.path)); render(); };
  $('#exNone').onclick = () => { exSel.clear(); render(); };
  $('#exOpen').onclick = async () => U.openPath(await U.musicDir());
  $('#exPrev').onclick = () => {
    if (exPrev) return stopPrev();
    const t = LIB.find(x => exSel.has(x.path)) || cur() && cur().src === 'local' && cur() || vis[0];
    if (!t) return toast('Marque uma música pra ouvir');
    pause();
    exPrev = new Audio(`/media?p=${encodeURIComponent(t.path)}`);
    exPrev.preservesPitch = false; exPrev.webkitPreservesPitch = false; exPrev.playbackRate = exRate; exPrev.volume = S().volume;
    exPrev.currentTime = 30; exPrev.play().catch(() => {});
    $('#exPrev').innerHTML = `${ico('pause')} Parar prévia`;
    toast('Prévia de ' + t.title + (exReverb ? ' (o reverb só entra no arquivo final)' : ''));
  };
  $('#exGo').onclick = async () => {
    if (exRun) { U.exportStop(); return; }
    stopPrev();
    const items = LIB.filter(t => exSel.has(t.path)).map(t => ({ path: t.path, title: t.title, artist: t.artist }));
    if (!items.length) return;
    exRun = { n: items.length, ok: 0, err: 0 };
    $('#exGo').textContent = 'Cancelar'; $('#exGo').disabled = false;
    const r = await U.exportFx(items, { rate: exRate, reverb: exReverb, bass: exBass, retro: exRetro, label: exLabel() + (exRetro ? ' ' + RETRO_NAMES[exRetro] : '') });
    const run = exRun; exRun = null;
    if (r.err) toast('Não deu pra exportar: ' + r.err);
    else toast(`${r.files.length} ${r.files.length === 1 ? 'música exportada' : 'músicas exportadas'} pra pasta Música${r.stopped ? ' (cancelado)' : ''}`);
    if (r.files.length) { exSel.clear(); rescan(); }
    if (view.name === 'export') { render(); const i = $('#exInfo'); if (i) i.textContent = `Pronto: ${r.files.length} de ${run.n}${run.err ? ` · ${run.err} com erro` : ''}`; }
  };
};
let exQT = null;
U.onExportProgress(p => {
  if (!exRun) return;
  if (p.err) exRun.err++; if (p.file) exRun.ok++;
  const total = ((p.i + (p.pct || 0) / 100) / p.n) * 100;
  const pg = $('#exPg'); if (pg) pg.style.width = total.toFixed(1) + '%';
  const inf = $('#exInfo'); if (inf) inf.textContent = p.err ? `Erro numa música: ${p.err.slice(0, 80)}` : `Exportando ${Math.min(p.i + 1, p.n)} de ${p.n}… ${Math.round(p.pct || 0)}%`;
});

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
$('#bSync').innerHTML = ico('dl');
$('#bRetro').innerHTML = ico('cassette');
$('#bRetro').onclick = () => { if (st.kind === 'radio') return toast('O modo retrô não funciona na rádio ao vivo'); const order = ['', 'k7', 'vinil']; setRetro(order[(order.indexOf(S().retro || '') + 1) % 3]); };
$('#bSync').onclick = () => syncTrack(cur());
$('#bSpeed').onclick = () => {
  if (st.kind === 'radio') return toast('A velocidade fica desligada na rádio ao vivo');
  if (!panel('speed', `<h3>Velocidade</h3>
    <div class="rng" style="margin:6px 4px"><input type="range" id="spR" min="0.5" max="2" step="0.05" value="${S().speed}" style="flex:1"><span id="spV">${S().speed.toFixed(2)}x</span></div>
    <div class="opt" style="padding:10px 4px"><div class="l">Mudar o tom junto<small>slowed / nightcore</small></div>${sw('pitchWithSpeed')}</div>
    <div class="chips">${[['Slowed', 0.85, true], ['Normal', 1, true], ['Nightcore', 1.25, true], ['Sped up', 1.15, true], ['Lento', 0.75, true]].map(([n, v, p]) => `<button class="chip ${Math.abs(S().speed - v) < 0.001 && (v === 1 || S().pitchWithSpeed === p) ? 'on' : ''}" data-sp="${v}" data-pp="${p}">${n}</button>`).join('')}</div>`)) return;
  const p = $('#panel');
  const syncChips = () => p.querySelectorAll('[data-sp]').forEach(x => x.classList.toggle('on', Math.abs(S().speed - +x.dataset.sp) < 0.001 && (+x.dataset.sp === 1 || S().pitchWithSpeed === (x.dataset.pp === 'true'))));
  $('#spR').oninput = e => { p.querySelectorAll('[data-sp]').forEach(x => x.classList.remove('on')); S().speed = +e.target.value; S().pitchWithSpeed = true; p.querySelector('[data-sw]').classList.add('on'); $('#spV').textContent = S().speed.toFixed(2) + 'x'; applyRate(true); save(); updateModeBtns(); syncChips(); };
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
  if (!D.startMig) { S().startView = 'discover'; D.startMig = 1; save(); }
  setTimeout(() => { const b = $('#bRetro'); if (b) { b.classList.toggle('on', !!S().retro); b.title = 'Modo retrô: ' + RETRO_NAMES[S().retro || '']; } }, 0);
  if (!D.skinMig) { D.skinMig = 1; S().skin = 'edgy'; S().accent = SKINS.edgy.accent; S().theme = 'dark'; save(); applyLook(); U.setSkin('edgy'); }
  if (!D.pitchMig) { S().pitchWithSpeed = true; D.pitchMig = 1; save(); }
  view = { name: S().startView };
  renderNav(); render();
  if (D.lastQueue && D.lastQueue.q && D.lastQueue.q.length) { st.queue = D.lastQueue.q; st.idx = Math.min(D.lastQueue.i, st.queue.length - 1); updateNowPlaying(); }
  if (S().notifications && Notification.permission === 'default') Notification.requestPermission();
  rescan();
  setTimeout(huntSync, 4000);
})();

/* libera memória quando o app fica minimizado ou no fundo */
document.addEventListener('visibilitychange', () => { if (document.hidden) setTimeout(() => { if (document.hidden) U.trim(); }, 5000); });
setInterval(() => U.trim(), 10 * 60e3);

/* clicar no ícone/nome do app abre "Criado por" */
function showAbout() {
  modal(`<div class="about">
    <img class="aboutApp" src="logo.png" alt="">
    <div class="aboutName">Uncrowned Music</div>
    <div class="aboutVer">para PC · 1.0</div>
    <div class="aboutBy">
      <div class="aboutPic"><img src="creator.jpg" alt="" onerror="this.remove()"><span>VA</span></div>
      <div><div class="aboutLbl">Criado por</div><div class="aboutWho">Vinicius Andrade</div><div class="aboutRole">Analista de TI e desenvolvedor</div></div>
    </div>
    <p class="aboutTxt">Um player feito do meu jeito: suas músicas, YouTube e SoundCloud num lugar só, sem anúncio e personalizado do começo ao fim.</p>
    <button class="btn fill aboutLi" id="aboutLi"><svg viewBox="0 0 24 24"><path d="M20.45 20.45h-3.56v-5.57c0-1.33-.02-3.04-1.85-3.04-1.85 0-2.14 1.45-2.14 2.94v5.67H9.35V9h3.41v1.56h.05c.48-.9 1.64-1.85 3.37-1.85 3.6 0 4.27 2.37 4.27 5.46v6.28zM5.34 7.43a2.06 2.06 0 1 1 0-4.13 2.06 2.06 0 0 1 0 4.13zM7.12 20.45H3.56V9h3.56v11.45zM22.22 0H1.77C.79 0 0 .77 0 1.73v20.54C0 23.23.79 24 1.77 24h20.45c.98 0 1.78-.77 1.78-1.73V1.73C24 .77 23.2 0 22.22 0z"/></svg>Ver meu LinkedIn</button>
  </div>`);
  $('#aboutLi').onclick = () => U.openUrl('https://www.linkedin.com/in/devviniciusandrade/');
}
document.querySelector('#sidebar .brand').onclick = showAbout;
