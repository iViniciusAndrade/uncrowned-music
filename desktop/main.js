// Uncrowned Music — processo principal (janela, arquivos, busca online, letras, downloads)
const { app, BrowserWindow, ipcMain, dialog, shell, nativeTheme, session, nativeImage, powerMonitor } = require('electron');
const path = require('path');
const fs = require('fs');
const http = require('http');
const https = require('https');
const { URL } = require('url');

let win = null;
let PORT = 0;
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36';
const AUDIO_EXT = new Set(['.mp3', '.m4a', '.aac', '.flac', '.ogg', '.oga', '.opus', '.wav', '.wma', '.alac', '.aiff', '.webm']);
// pastas que não são música (WhatsApp, gravações, Telegram...)
const VOICE = /(whatsapp|telegram|recordings?|grava[cç][oõ]es|voice|notas de voz|ptt|call recorder|chamadas|ringtones|notifications|alarms|podcasts?)/i;

/* ---------------- armazenamento simples em JSON ---------------- */
const storeFile = () => path.join(app.getPath('userData'), 'uncrowned.json');
let store = {};
function loadStore() { try { store = JSON.parse(fs.readFileSync(storeFile(), 'utf8')); } catch { store = {}; } }
let saveTimer = null;
function saveStore() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => { try { fs.writeFileSync(storeFile(), JSON.stringify(store)); } catch {} }, 300);
}

/* ---------------- HTTP helpers ---------------- */
// conexões ficam abertas e são reaproveitadas: cada busca não precisa "apertar a mão" do servidor de novo
const agentS = new https.Agent({ keepAlive: true, keepAliveMsecs: 30000, maxSockets: 24 });
const agentH = new http.Agent({ keepAlive: true, maxSockets: 24 });
function request(url, { headers = {}, method = 'GET', redirects = 5, body = null } = {}) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const lib = u.protocol === 'http:' ? http : https;
    const req = lib.request(u, { method, agent: u.protocol === 'http:' ? agentH : agentS, headers: { 'User-Agent': UA, 'Accept-Language': 'pt-BR,pt;q=0.9,en;q=0.8', ...headers }, timeout: 15000 }, res => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location && redirects > 0) {
        res.resume();
        return resolve(request(new URL(res.headers.location, u).toString(), { headers, method, redirects: redirects - 1 }));
      }
      resolve(res);
    });
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}
async function getText(url, headers, opts = {}) {
  const res = await request(url, { headers, ...opts });
  const chunks = [];
  for await (const c of res) chunks.push(c);
  const body = Buffer.concat(chunks).toString('utf8');
  if (res.statusCode < 200 || res.statusCode >= 300) { const e = new Error('HTTP ' + res.statusCode); e.status = res.statusCode; e.body = body; throw e; }
  return body;
}
const getJSON = async (url, headers) => JSON.parse(await getText(url, headers));

/* ---------------- servidor local: interface + streaming de arquivos/áudio ---------------- */
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml',
  '.mp3': 'audio/mpeg', '.m4a': 'audio/mp4', '.aac': 'audio/aac', '.flac': 'audio/flac', '.ogg': 'audio/ogg', '.oga': 'audio/ogg', '.opus': 'audio/ogg', '.wav': 'audio/wav', '.webm': 'audio/webm', '.wma': 'audio/x-ms-wma', '.aiff': 'audio/aiff' };

function serveFile(req, res, file) {
  fs.stat(file, (err, st) => {
    if (err || !st.isFile()) { res.writeHead(404); return res.end(); }
    const type = MIME[path.extname(file).toLowerCase()] || 'application/octet-stream';
    const range = req.headers.range;
    if (range) {
      const m = /bytes=(\d*)-(\d*)/.exec(range);
      let start = m && m[1] ? parseInt(m[1]) : 0;
      let end = m && m[2] ? parseInt(m[2]) : st.size - 1;
      if (start >= st.size) { res.writeHead(416, { 'Content-Range': `bytes */${st.size}` }); return res.end(); }
      end = Math.min(end, st.size - 1);
      res.writeHead(206, { 'Content-Type': type, 'Accept-Ranges': 'bytes', 'Content-Range': `bytes ${start}-${end}/${st.size}`, 'Content-Length': end - start + 1 });
      fs.createReadStream(file, { start, end }).pipe(res);
    } else {
      res.writeHead(200, { 'Content-Type': type, 'Accept-Ranges': 'bytes', 'Content-Length': st.size });
      fs.createReadStream(file).pipe(res);
    }
  });
}

async function proxy(req, res, target) {
  try {
    const headers = {};
    if (req.headers.range) headers.Range = req.headers.range;
    const up = await request(target, { headers });
    const out = { 'Access-Control-Allow-Origin': '*' };
    for (const h of ['content-type', 'content-length', 'content-range', 'accept-ranges']) if (up.headers[h]) out[h] = up.headers[h];
    res.writeHead(up.statusCode, out);
    up.pipe(res);
  } catch (e) { res.writeHead(502); res.end(String(e)); }
}

function startServer() {
  return new Promise(resolve => {
    const root = path.join(__dirname, 'renderer');
    const srv = http.createServer((req, res) => {
      const u = new URL(req.url, 'http://127.0.0.1');
      if (u.pathname === '/media') return serveFile(req, res, u.searchParams.get('p') || '');
      if (u.pathname === '/proxy') return proxy(req, res, u.searchParams.get('u') || '');
      if (u.pathname === '/cover') return cover(res, u.searchParams.get('p') || '');
      // logo do app = caveira do tema escolhido
      if (u.pathname === '/logo.png') {
        const qs = u.searchParams.get('s'); const f = path.join(root, 'themes', (SKINS.includes(qs) ? qs : skinNow()) + '.png');
        return fs.readFile(fs.existsSync(f) ? f : path.join(root, 'logo.png'), (e, b) => { if (e) { res.writeHead(404); return res.end(); } res.writeHead(200, { 'Content-Type': 'image/png', 'Cache-Control': 'no-store' }); res.end(b); });
      }
      let p = path.normalize(path.join(root, decodeURIComponent(u.pathname === '/' ? '/index.html' : u.pathname)));
      if (!p.startsWith(root)) { res.writeHead(403); return res.end(); }
      serveFile(req, res, p);
    });
    srv.listen(0, '127.0.0.1', () => { PORT = srv.address().port; resolve(); });
  });
}

/* ---------------- biblioteca local ---------------- */
let mm = null;
const coverCache = new Map();
async function cover(res, file) {
  try {
    mm = mm || require('music-metadata');
    let pic = coverCache.get(file);
    if (pic === undefined) {
      const meta = await mm.parseFile(file, { skipCovers: false, duration: false });
      pic = meta.common.picture && meta.common.picture[0] || null;
      if (coverCache.size > 300) coverCache.clear();
      coverCache.set(file, pic);
    }
    if (!pic) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { 'Content-Type': pic.format || 'image/jpeg', 'Cache-Control': 'max-age=86400' });
    res.end(pic.data);
  } catch { res.writeHead(404); res.end(); }
}

function walk(dir, out, depth = 0) {
  if (depth > 12) return;
  let ents = [];
  try { ents = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
  for (const e of ents) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) { if (!e.name.startsWith('.')) walk(p, out, depth + 1); }
    else if (AUDIO_EXT.has(path.extname(e.name).toLowerCase())) out.push(p);
  }
}

async function scan(folders, opts) {
  mm = mm || require('music-metadata');
  const files = [];
  for (const f of folders) walk(f, files);
  const cache = store.libCache || {};
  const result = [];
  let i = 0;
  for (const file of files) {
    i++;
    if (opts.hideVoice && VOICE.test(path.dirname(file))) continue;
    let st; try { st = fs.statSync(file); } catch { continue; }
    let t = cache[file];
    if (!t || t.mtime !== st.mtimeMs) {
      try {
        const meta = await mm.parseFile(file, { skipCovers: true, duration: true });
        const c = meta.common;
        t = {
          id: 'f:' + require('crypto').createHash('sha1').update(file).digest('hex').slice(0, 16),
          path: file, title: c.title || path.basename(file, path.extname(file)),
          artist: c.artist || c.albumartist || 'Artista desconhecido', album: c.album || 'Álbum desconhecido',
          year: c.year || 0, genre: (c.genre && c.genre[0]) || '', track: c.track && c.track.no || 0,
          duration: Math.round((meta.format.duration || 0) * 1000), added: st.birthtimeMs || st.mtimeMs, mtime: st.mtimeMs
        };
      } catch { continue; }
      cache[file] = t;
    }
    if (t.duration < (opts.minDurationSec || 0) * 1000) continue;
    result.push(t);
    if (win && i % 50 === 0) win.webContents.send('scan-progress', { done: i, total: files.length });
  }
  store.libCache = cache; saveStore();
  return result;
}

/* ---------------- busca online ---------------- */
function jsonAt(s, start) {
  let depth = 0, inStr = false, esc = false;
  for (let i = start; i < s.length; i++) {
    const ch = s[i];
    if (inStr) { if (esc) esc = false; else if (ch === '\\') esc = true; else if (ch === '"') inStr = false; continue; }
    if (ch === '"') inStr = true; else if (ch === '{') depth++; else if (ch === '}') { depth--; if (depth === 0) return s.slice(start, i + 1); }
  }
  throw new Error('json');
}

/* YouTube: usa a mesma API interna que o site usa pra buscar (mais estável); se falhar, lê a página */
const txtYT = o => o ? (o.simpleText || (o.runs || []).map(r => r.text).join('')) : '';
function ytVideos(data) {
  const out = [];
  (function walkJ(x) {
    if (Array.isArray(x)) return x.forEach(walkJ);
    if (x && typeof x === 'object') {
      if (x.videoRenderer) {
        const v = x.videoRenderer; const th = (v.thumbnail && v.thumbnail.thumbnails) || [];
        const len = txtYT(v.lengthText);
        const secs = len ? len.split(':').reduce((a, b) => a * 60 + (+b), 0) : 0;
        out.push({ src: 'yt', id: v.videoId, title: txtYT(v.title), artist: txtYT(v.ownerText || v.longBylineText), art: `https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg`, duration: secs * 1000 });
      } else Object.values(x).forEach(walkJ);
    }
  })(data);
  return out.filter((v, i, a) => v.id && a.findIndex(w => w.id === v.id) === i).slice(0, 25);
}
async function youtube(q) {
  try {
    const body = JSON.stringify({ context: { client: { clientName: 'WEB', clientVersion: '2.20250101.00.00', hl: 'pt', gl: 'BR' } }, query: q, params: 'EgIQAQ%3D%3D' });
    const j = JSON.parse(await getText('https://www.youtube.com/youtubei/v1/search?prettyPrint=false', { 'Content-Type': 'application/json', Origin: 'https://www.youtube.com', 'X-Youtube-Client-Name': '1', 'X-Youtube-Client-Version': '2.20250101.00.00' }, { method: 'POST', body }));
    const r = ytVideos(j);
    if (r.length) return r;
  } catch {}
  return youtubeHtml(q);
}
async function youtubeHtml(q) {
  const html = await getText(`https://www.youtube.com/results?search_query=${encodeURIComponent(q)}&sp=EgIQAQ%253D%253D&hl=pt-BR`, { Cookie: 'CONSENT=YES+1' });
  const i = html.indexOf('ytInitialData');
  if (i < 0) return [];
  return ytVideos(JSON.parse(jsonAt(html, html.indexOf('{', i))));
}

/* links colados na busca: YouTube, SoundCloud e Vimeo (via oEmbed oficial) */
function ytIdFrom(u) {
  const m = /(?:youtu\.be\/|youtube\.com\/(?:watch\?(?:.*&)?v=|shorts\/|embed\/|live\/|v\/))([A-Za-z0-9_-]{11})/.exec(u);
  return m ? m[1] : null;
}
async function resolveLink(raw) {
  const u = raw.trim();
  const id = ytIdFrom(u);
  if (id) {
    let meta = {};
    try { meta = await getJSON(`https://www.youtube.com/oembed?url=${encodeURIComponent('https://www.youtube.com/watch?v=' + id)}&format=json`); } catch {}
    const list = /[?&]list=([A-Za-z0-9_-]+)/.exec(u);
    return [{ src: 'yt', id, title: meta.title || 'Vídeo do YouTube', artist: meta.author_name || 'YouTube', art: `https://i.ytimg.com/vi/${id}/hqdefault.jpg`, duration: 0, list: list ? list[1] : null }];
  }
  if (/soundcloud\.com\//i.test(u)) {
    const clean = u.split('?')[0].replace(/^https?:\/\/(m\.|www\.)?soundcloud\.com\//i, '').replace(/\/$/, '');
    let meta = {};
    try { meta = await getJSON(`https://soundcloud.com/oembed?format=json&url=${encodeURIComponent('https://soundcloud.com/' + clean)}`); } catch {}
    return [{ src: 'sc', id: clean, url: 'https://soundcloud.com/' + clean, title: (meta.title || clean).replace(/ by .*$/, ''), artist: meta.author_name || clean.split('/')[0], art: meta.thumbnail_url || '', duration: 0 }];
  }
  const vmM = /vimeo\.com\/(?:video\/)?(\d+)/i.exec(u);
  if (vmM) {
    let meta = {};
    try { meta = await getJSON(`https://vimeo.com/api/oembed.json?url=${encodeURIComponent('https://vimeo.com/' + vmM[1])}`); } catch {}
    return [{ src: 'vm', id: vmM[1], title: meta.title || 'Vídeo do Vimeo', artist: meta.author_name || 'Vimeo', art: meta.thumbnail_url || '', duration: (meta.duration || 0) * 1000 }];
  }
  return null;
}

let scId = null;
async function scClientId(force) {
  if (!force && (scId || store.scid)) return scId = scId || store.scid;
  const home = await getText('https://soundcloud.com/');
  const scripts = [...home.matchAll(/<script[^>]+src="(https:\/\/a-v2\.sndcdn\.com\/assets\/[^"]+\.js)"/g)].map(m => m[1]).reverse();
  for (const s of scripts) {
    try { const m = /client_id\s*[:=]\s*"?([a-zA-Z0-9]{32})/.exec(await getText(s)); if (m) { scId = store.scid = m[1]; saveStore(); return scId; } } catch {}
  }
  return null;
}
async function soundcloud(q, retry = true) {
  try {
    const id = await scClientId(false);
    if (!id) throw new Error('no id');
    const j = await getJSON(`https://api-v2.soundcloud.com/search/tracks?q=${encodeURIComponent(q)}&client_id=${id}&limit=25`);
    return (j.collection || []).filter(t => t.permalink_url).map(t => ({
      src: 'sc', id: t.permalink_url.replace(/^https:\/\/soundcloud\.com\//, ''), url: t.permalink_url, title: t.title,
      artist: (t.user && t.user.username) || '', art: (t.artwork_url || (t.user && t.user.avatar_url) || '').replace('-large.', '-t500x500.'),
      duration: t.duration || 0, genre: t.genre || '', downloadable: !!t.downloadable
    }));
  } catch (e) {
    if (retry && (e.status === 401 || e.status === 403)) { scId = null; store.scid = null; return soundcloud(q, false); }
    try {
      const html = await getText(`https://soundcloud.com/search/sounds?q=${encodeURIComponent(q)}`);
      return [...html.matchAll(/<h2>\s*<a href="\/([^"\/]+)\/([^"]+)"[^>]*>([^<]+)<\/a>\s*<\/h2>/g)]
        .map(m => ({ src: 'sc', id: `${m[1]}/${m[2]}`, url: `https://soundcloud.com/${m[1]}/${m[2]}`, title: m[3], artist: m[1], art: '', duration: 0 }))
        .filter(t => !t.id.includes('/sets/'));
    } catch { return []; }
  }
}

/* SoundCloud tocando no player do próprio app (o mesmo stream que o site do SoundCloud usa pra tocar),
   assim velocidade, tom e equalizador funcionam. Não baixa nada. */
async function scStream(id) {
  let cid = await scClientId(false); if (!cid) return null;
  const resolve = c => getJSON(`https://api-v2.soundcloud.com/resolve?url=${encodeURIComponent('https://soundcloud.com/' + id)}&client_id=${c}`);
  let tr;
  try { tr = await resolve(cid); }
  catch (e) { if (e.status === 401 || e.status === 403) { scId = null; store.scid = null; cid = await scClientId(true); if (!cid) return null; tr = await resolve(cid); } else throw e; }
  if (!tr || tr.kind !== 'track' || tr.policy === 'SNIP' || tr.policy === 'BLOCK') return null;
  const tcs = (tr.media && tr.media.transcodings) || [];
  const tc = tcs.find(x => x.format && x.format.protocol === 'progressive');
  if (!tc) return null;
  const j = await getJSON(`${tc.url}?client_id=${cid}${tr.track_authorization ? '&track_authorization=' + tr.track_authorization : ''}`);
  if (!j.url) return null;
  return { url: j.url, duration: tr.duration || 0, genre: tr.genre || '', art: (tr.artwork_url || '').replace('-large.', '-t500x500.') };
}

async function vimeo(q, token) {
  if (!token) return [];
  const j = await getJSON(`https://api.vimeo.com/videos?per_page=20&fields=uri,name,user.name,pictures.base_link,duration&query=${encodeURIComponent(q)}`,
    { Authorization: 'bearer ' + token, Accept: 'application/vnd.vimeo.*+json;version=3.4' });
  return (j.data || []).map(v => ({ src: 'vm', id: v.uri.split('/').pop(), title: v.name, artist: v.user && v.user.name || '',
    art: v.pictures && v.pictures.base_link ? v.pictures.base_link + '_640x360' : '', duration: (v.duration || 0) * 1000 }));
}

const AUDIUS = 'https://api.audius.co/v1';
const audiusMap = t => ({ src: 'au', id: t.id, title: t.title, artist: t.user && t.user.name || '', genre: t.genre || '',
  art: t.artwork && (t.artwork['480x480'] || t.artwork['150x150']) || '', duration: (t.duration || 0) * 1000,
  stream: `${AUDIUS}/tracks/${t.id}/stream?app_name=UncrownedMusic`,
  download: (t.is_downloadable || (t.download && t.download.is_downloadable)) ? `${AUDIUS}/tracks/${t.id}/download?app_name=UncrownedMusic` : null });
async function audiusSearch(q) { const j = await getJSON(`${AUDIUS}/tracks/search?query=${encodeURIComponent(q)}&app_name=UncrownedMusic`); return (j.data || []).map(audiusMap); }
async function audiusTrending(genre, time = 'week') {
  const j = await getJSON(`${AUDIUS}/tracks/trending?time=${time}${genre ? '&genre=' + encodeURIComponent(genre) : ''}&app_name=UncrownedMusic`);
  return (j.data || []).map(audiusMap);
}

async function archive(q) {
  const query = encodeURIComponent(`(${q}) AND mediatype:audio AND collection:(opensource_audio OR netlabels OR audio_music)`);
  const j = await getJSON(`https://archive.org/advancedsearch.php?q=${query}&fl[]=identifier&fl[]=title&fl[]=creator&rows=8&sort[]=downloads+desc&output=json`);
  const out = [];
  await Promise.all((j.response.docs || []).map(async d => {
    try {
      const meta = await getJSON(`https://archive.org/metadata/${d.identifier}`);
      const creator = Array.isArray(d.creator) ? d.creator[0] : (d.creator || '');
      (meta.files || []).filter(f => /\.mp3$/i.test(f.name)).slice(0, 3).forEach(f => {
        const url = `https://archive.org/download/${d.identifier}/${encodeURIComponent(f.name)}`;
        out.push({ src: 'ia', id: d.identifier + '/' + f.name, title: f.title || f.name.replace(/\.mp3$/i, ''), artist: f.creator || creator, genre: 'Archive',
          art: `https://archive.org/services/img/${d.identifier}`, duration: Math.round(parseFloat(f.length || 0) * 1000), stream: url, download: url });
      });
    } catch {}
  }));
  return out;
}

async function searchAll(q) {
  if (/^(https?:\/\/|www\.|youtu\.be|youtube\.com|m\.youtube|music\.youtube|soundcloud\.com|vimeo\.com)/i.test(q.trim())) {
    const r = await resolveLink(q).catch(() => null);
    if (r) return { stream: r, downloadable: [], link: true };
  }
  const s = (store.data && store.data.settings) || {};
  const safe = p => p.catch(() => []);
  const [sc, yt, vm, au, ia] = await Promise.all([safe(soundcloud(q)), safe(youtube(q)), safe(vimeo(q, s.vimeoToken)), safe(audiusSearch(q)), safe(archive(q))]);
  return { stream: [...yt.slice(0, 25), ...sc.slice(0, 20), ...vm.slice(0, 10)], downloadable: [...au.filter(t => t.download), ...ia].slice(0, 15), audius: au };
}

/* busca ao vivo: cada fonte manda o resultado assim que responde (YouTube primeiro na tela),
   com cache de 15 min e tempo máximo por fonte, então uma fonte lenta não segura as outras */
const sCache = new Map();
function cachedSearch(src, q, fn) {
  const k = src + '|' + q.toLowerCase(); const c = sCache.get(k);
  if (c && Date.now() - c.at < 15 * 60e3) return c.v;
  const v = fn().then(r => { if (!r || !r.length) sCache.delete(k); return r || []; }).catch(() => { sCache.delete(k); return []; });
  sCache.set(k, { at: Date.now(), v }); if (sCache.size > 400) sCache.delete(sCache.keys().next().value);
  return v;
}
const within = (p, ms) => Promise.race([p, new Promise(r => setTimeout(() => r([]), ms))]);
ipcMain.on('search-live', async (e, q, rid) => {
  const send = (src, items, done) => { try { if (!e.sender.isDestroyed()) e.sender.send('search-part', { rid, src, items, done }); } catch {} };
  q = (q || '').trim(); if (!q) return;
  if (/^(https?:\/\/|www\.|youtu\.be|youtube\.com|m\.youtube|music\.youtube|soundcloud\.com|vimeo\.com)/i.test(q)) {
    const r = await resolveLink(q).catch(() => null);
    if (r) return send('link', r, true);
  }
  const s = (store.data && store.data.settings) || {};
  const jobs = [['yt', () => youtube(q)], ['sc', () => soundcloud(q)], ['vm', () => vimeo(q, s.vimeoToken)], ['au', () => audiusSearch(q)], ['ia', () => archive(q)]];
  let left = jobs.length;
  jobs.forEach(([src, fn]) => within(cachedSearch(src, q, fn), 9000).then(items => send(src, items || [], --left === 0)));
});
// deixa as conexões com YouTube e SoundCloud já abertas pra primeira busca sair rápida
function warmUp() { cachedSearch('yt', 'musica', () => youtube('musica')); soundcloud('a').catch(() => {}); }

/* ---------------- letras ---------------- */
const cleanName = s => (s || '').replace(/\s*[\(\[][^\)\]]*(official|oficial|video|v[ií]deo|lyric|audio|áudio|visualizer|prod|ft\.|feat)[^\)\]]*[\)\]]/gi, '').replace(/\s*-\s*topic$/i, '').trim();
const slug = s => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/&/g, 'e').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
function htmlToText(h) {
  return h.replace(/<br\s*\/?>/gi, '\n').replace(/<\/p>/gi, '\n\n').replace(/<[^>]+>/g, '')
    .replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&nbsp;/g, ' ')
    .replace(/\n{3,}/g, '\n\n').trim();
}
async function letrasPage(url) {
  const html = await getText(url);
  const i = html.indexOf('lyric-original'); if (i < 0) return null;
  const start = html.indexOf('>', i) + 1, end = html.indexOf('</div>', start);
  const t = htmlToText(html.slice(start, end));
  return t.length > 20 ? t : null;
}
async function letras(artist, title) {
  artist = cleanName(artist); title = cleanName(title);
  if (title.includes(' - ')) { const [a, b] = title.split(' - ', 2); if (!artist || /youtube/i.test(artist)) artist = a; title = b; }
  try {
    const body = await getText(`https://solr.sscdn.co/letras/m1/?q=${encodeURIComponent(artist + ' ' + title)}&wt=json&callback=LetrasSug`);
    const j = JSON.parse(body.slice(body.indexOf('{'), body.lastIndexOf('}') + 1));
    for (const d of (j.response && j.response.docs) || []) {
      if (d.dns && d.url) { try { const t = await letrasPage(`https://www.letras.mus.br/${d.dns}/${d.url}/`); if (t) return t; } catch {} }
    }
  } catch {}
  try { return await letrasPage(`https://www.letras.mus.br/${slug(artist)}/${slug(title)}/`); } catch { return null; }
}
async function lrclib(artist, title, durMs) {
  try {
    let j;
    try { j = await getJSON(`https://lrclib.net/api/get?artist_name=${encodeURIComponent(artist)}&track_name=${encodeURIComponent(title)}&duration=${Math.round(durMs / 1000)}`); }
    catch { const a = await getJSON(`https://lrclib.net/api/search?q=${encodeURIComponent(cleanName(artist) + ' ' + cleanName(title))}`); j = a[0]; }
    if (!j) return null;
    return { synced: j.syncedLyrics || null, plain: j.plainLyrics || null };
  } catch { return null; }
}
async function lyrics(artist, title, dur, source) {
  if (source !== 'synced') { const t = await letras(artist, title); if (t) return { plain: t, source: 'Letras.mus.br' }; }
  const l = await lrclib(artist, title, dur);
  if (l && (l.synced || l.plain)) return { ...l, source: 'LRCLIB' };
  if (source === 'synced') { const t = await letras(artist, title); if (t) return { plain: t, source: 'Letras.mus.br' }; }
  return null;
}

/* ---------------- info (iTunes) ---------------- */
async function songInfo(title, artist) {
  const t = cleanName(title).replace(/^.* - /, ''), a = title.includes(' - ') && (!artist || /topic/i.test(artist)) ? title.split(' - ')[0] : cleanName(artist);
  try {
    const j = await getJSON(`https://itunes.apple.com/search?term=${encodeURIComponent(a + ' ' + t)}&entity=song&limit=5&country=BR`);
    const r = (j.results || []).find(x => (x.trackName || '').toLowerCase().includes(t.slice(0, 12).toLowerCase())) || (j.results || [])[0];
    if (!r) return null;
    return { title: r.trackName, artist: r.artistName, album: r.collectionName, year: (r.releaseDate || '').slice(0, 4), genre: r.primaryGenreName,
      art: (r.artworkUrl100 || '').replace('100x100', '600x600'), track: r.trackNumber ? `${r.trackNumber} de ${r.trackCount}` : '', source: 'Apple Music / iTunes' };
  } catch { return null; }
}

/* ---------------- download ---------------- */
async function download(url, name) {
  const dir = path.join(app.getPath('music'), 'Uncrowned');
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, name.replace(/[\\/:*?"<>|]/g, '').slice(0, 150) + '.mp3');
  const res = await request(url);
  if (res.statusCode >= 400) throw new Error('HTTP ' + res.statusCode);
  await new Promise((ok, bad) => { const w = fs.createWriteStream(file); res.pipe(w); w.on('finish', ok); w.on('error', bad); });
  return file;
}

/* ---------------- janela ---------------- */
function createWindow() {
  const s = store.win || {};
  win = new BrowserWindow({
    width: s.w || 1280, height: s.h || 800, minWidth: 960, minHeight: 620, x: s.x, y: s.y,
    backgroundColor: '#000000', title: 'Uncrowned Music',
    icon: path.join(__dirname, 'build', process.platform === 'win32' ? 'icon.ico' : 'icon.png'),
    titleBarStyle: 'hidden', titleBarOverlay: { color: '#00000000', symbolColor: '#ffffff', height: 40 },
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: false, autoplayPolicy: 'no-user-gesture-required', webviewTag: true, backgroundThrottling: false, spellcheck: false }
  });
  // o player do YouTube recebe um script que tira os anúncios da resposta do próprio YouTube antes de tocar
  // botões voltar/avançar do mouse no Windows
  win.on('app-command', (_, cmd) => { if (cmd === 'browser-backward') win.webContents.send('nav', 'back'); if (cmd === 'browser-forward') win.webContents.send('nav', 'fwd'); });
  win.webContents.on('will-attach-webview', (_, wp) => { wp.preload = path.join(__dirname, 'yt-preload.js'); wp.spellcheck = false; delete wp.preloadURL; });
  if (s.max) win.maximize();
  win.loadURL(`http://127.0.0.1:${PORT}/`);
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
  const keep = () => { if (!win.isMaximized() && !win.isMinimized()) { const b = win.getBounds(); store.win = { w: b.width, h: b.height, x: b.x, y: b.y, max: false }; } else store.win = { ...(store.win || {}), max: win.isMaximized() }; saveStore(); };
  win.on('resize', keep); win.on('move', keep);
}

/* Bloqueador de anúncios (motor do Ghostery, mesmas listas do uBlock Origin / Brave) na sessão do YouTube */
let blocker = null, blockerOn = false;
async function setupAdblock(on) {
  const ses = ytSession();
  try {
    if (on && !blocker) {
      const { ElectronBlocker } = require('@ghostery/adblocker-electron');
      const cache = path.join(app.getPath('userData'), 'adblock-engine.bin');
      // a lista se renova sozinha: se a cópia salva tiver mais de 1 dia, baixa a versão nova
      try { if (Date.now() - fs.statSync(cache).mtimeMs > 24 * 3600e3) fs.unlinkSync(cache); } catch {}
      blocker = await ElectronBlocker.fromPrebuiltAdsOnly(fetch, { path: cache, read: fs.promises.readFile, write: fs.promises.writeFile });
    }
    if (on && blocker && !blockerOn) { blocker.enableBlockingInSession(ses); blockerOn = true; }
    if (!on && blocker && blockerOn) { blocker.disableBlockingInSession(ses); blockerOn = false; }
  } catch (e) { console.error('adblock', e); }
  return blockerOn;
}
// renova a lista uma vez por dia mesmo com o app aberto direto
setInterval(async () => {
  if (!blockerOn) return;
  try { blocker.disableBlockingInSession(ytSession()); } catch {}
  blocker = null; blockerOn = false;
  try { fs.unlinkSync(path.join(app.getPath('userData'), 'adblock-engine.bin')); } catch {}
  setupAdblock(true);
}, 24 * 3600e3);
ipcMain.handle('adblock', (_, on) => setupAdblock(!!on));

/* YouTube de verdade (página oficial dentro do app), com login opcional pra liberar vídeos +18 */
function ytSession() {
  const s = session.fromPartition('persist:youtube');
  s.setUserAgent(UA);
  return s;
}
ipcMain.handle('yt-login', () => new Promise(resolve => {
  const w = new BrowserWindow({ parent: win, modal: false, width: 520, height: 760, title: 'Entrar no YouTube', autoHideMenuBar: true,
    webPreferences: { partition: 'persist:youtube' } });
  w.webContents.setUserAgent(UA);
  w.loadURL('https://accounts.google.com/ServiceLogin?service=youtube&hl=pt-BR&continue=https%3A%2F%2Fwww.youtube.com%2F');
  w.webContents.on('did-navigate', (_, url) => { if (/^https:\/\/(www|m)\.youtube\.com\/?(\?|$)/.test(url)) w.close(); });
  w.on('closed', async () => { const c = await ytSession().cookies.get({ domain: '.youtube.com', name: 'SAPISID' }).catch(() => []); resolve(c.length > 0); });
}));
ipcMain.handle('yt-logged', async () => (await ytSession().cookies.get({ domain: '.youtube.com', name: 'SAPISID' }).catch(() => [])).length > 0);
ipcMain.handle('yt-logout', async () => { await ytSession().clearStorageData(); return true; });
app.on('web-contents-created', (_, wc) => {
  if (wc.getType() === 'webview') {
    wc.setUserAgent(UA);
    wc.setWindowOpenHandler(() => ({ action: 'deny' }));
    wc.setBackgroundThrottling(false);
  }
});

/* SoundCloud: o player oficial não tem controle de velocidade, então ajustamos direto no áudio dele */
ipcMain.handle('sc-rate', async (_, rate, keepPitch) => {
  if (!win) return 0;
  const code = `(()=>{window.__uncRate=${Number(rate) || 1};window.__uncKeep=${!!keepPitch};
    const apply=a=>{try{a.preservesPitch=window.__uncKeep;a.webkitPreservesPitch=window.__uncKeep;if(Math.abs(a.playbackRate-window.__uncRate)>0.001)a.playbackRate=window.__uncRate;}catch(e){}};
    if(!window.__uncPatched){window.__uncPatched=true;window.__uncEls=window.__uncEls||new Set();
      const op=HTMLMediaElement.prototype.play;
      HTMLMediaElement.prototype.play=function(){window.__uncEls.add(this);apply(this);return op.apply(this,arguments);};
      setInterval(()=>{document.querySelectorAll('audio,video').forEach(a=>window.__uncEls.add(a));window.__uncEls.forEach(apply);},300);}
    document.querySelectorAll('audio,video').forEach(a=>window.__uncEls.add(a));window.__uncEls.forEach(apply);
    return window.__uncEls.size;})()`;
  let n = 0;
  for (const f of win.webContents.mainFrame.framesInSubtree) {
    if (/w\.soundcloud\.com\/player/.test(f.url)) { try { n += await f.executeJavaScript(code); } catch {} }
  }
  return n;
});

ipcMain.handle('frame-eq', async (_, code) => {
  if (!win) return 0; let n = 0;
  for (const f of win.webContents.mainFrame.framesInSubtree) {
    if (/w\.soundcloud\.com\/player/.test(f.url)) { try { n += (await f.executeJavaScript(code)) || 0; } catch {} }
  }
  return n;
});

/* ---------------- recomendações (motor v2) ----------------
   Deezer (grátis, sem chave) diz quais artistas são parecidos com os que você ouve;
   o "Mix" do próprio YouTube completa; tudo vira faixa tocável do YouTube. */
const dz = p => getJSON('https://api.deezer.com' + p);
const dzArtistCache = new Map();
async function dzArtist(name) {
  const k = name.toLowerCase(); if (dzArtistCache.has(k)) return dzArtistCache.get(k);
  const j = await dz(`/search/artist?q=${encodeURIComponent(name)}&limit=3`).catch(() => ({}));
  const a = (j.data || []).find(x => x.name.toLowerCase() === k) || (j.data || [])[0] || null;
  dzArtistCache.set(k, a); return a;
}
async function dzRelated(name) {
  const a = await dzArtist(name); if (!a) return [];
  const j = await dz(`/artist/${a.id}/related?limit=15`).catch(() => ({}));
  return (j.data || []).map(x => ({ id: x.id, name: x.name, pic: x.picture_medium || x.picture, fans: x.nb_fan || 0 }));
}
async function dzTop(id, n = 6) {
  const j = await dz(`/artist/${id}/top?limit=${n}`).catch(() => ({}));
  return (j.data || []).map(x => ({ title: x.title_short || x.title, artist: (x.artist && x.artist.name) || '', dur: (x.duration || 0) * 1000, art: x.album && (x.album.cover_xl || x.album.cover_big) }));
}
const norm = x => (x || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, ' ').trim();
async function toYT(t) {
  const r = await youtube(`${t.artist} ${t.title}`).catch(() => []);
  const okDur = v => !t.dur || !v.duration || Math.abs(v.duration - t.dur) < 25000;
  const hit = r.find(v => okDur(v) && norm(v.title).includes(norm(t.title).split(' ').slice(0, 3).join(' '))) || r.find(okDur);
  return hit ? { ...hit, title: t.title, artist: t.artist, ytTitle: hit.title } : null;
}
async function ytMix(videoId) {
  const body = JSON.stringify({ context: { client: { clientName: 'WEB', clientVersion: '2.20250101.00.00', hl: 'pt', gl: 'BR' } }, videoId, playlistId: 'RD' + videoId });
  const j = JSON.parse(await getText('https://www.youtube.com/youtubei/v1/next?prettyPrint=false', { 'Content-Type': 'application/json', Origin: 'https://www.youtube.com', 'X-Youtube-Client-Name': '1', 'X-Youtube-Client-Version': '2.20250101.00.00' }, { method: 'POST', body }));
  const out = [];
  (function walk(x) {
    if (Array.isArray(x)) return x.forEach(walk);
    if (x && typeof x === 'object') {
      if (x.playlistPanelVideoRenderer) {
        const v = x.playlistPanelVideoRenderer; const len = txtYT(v.lengthText);
        out.push({ src: 'yt', id: v.videoId, title: txtYT(v.title), artist: txtYT(v.longBylineText || v.shortBylineText).replace(/ - Topic$/, ''), art: `https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg`, duration: len ? len.split(':').reduce((a, b) => a * 60 + (+b), 0) * 1000 : 0 });
      } else Object.values(x).forEach(walk);
    }
  })(j);
  return out.filter((v, i, a) => v.id && a.findIndex(w => w.id === v.id) === i);
}
async function pool(items, n, fn) { const out = []; let i = 0; await Promise.all(Array.from({ length: n }, async () => { while (i < items.length) { const k = i++; out[k] = await fn(items[k]).catch(() => null); } })); return out; }

/* Descobertas da Semana: artistas parecidos com os seus (que você ainda não ouve) + mixes do YouTube das suas mais tocadas */
async function recommend({ seeds = [], known = [], heard = [], mixSeeds = [], week = 0, size = 30 }) {
  const knownSet = new Set(known.map(norm)), heardSet = new Set(heard);
  let seed = week || 1; const rnd = () => (seed = (seed * 9301 + 49297) % 233280) / 233280;
  // 1) artistas candidatos, pontuados por quantos dos seus artistas apontam pra eles
  const cand = new Map();
  const rel = await pool(seeds.slice(0, 8), 4, ([name]) => dzRelated(name));
  rel.forEach((list, si) => (list || []).forEach((a, r) => {
    if (knownSet.has(norm(a.name))) return;
    const c = cand.get(a.id) || { ...a, score: 0, because: [] };
    c.score += seeds[si][1] * (1 - r / 16); if (c.because.length < 2) c.because.push(seeds[si][0]);
    cand.set(a.id, c);
  }));
  const artists = [...cand.values()].map(c => ({ ...c, score: c.score * (0.75 + rnd() * 0.5) })).sort((a, b) => b.score - a.score).slice(0, 14);
  // 2) duas músicas de cada artista novo, achadas no YouTube
  const tops = await pool(artists, 4, a => dzTop(a.id, 4));
  const wanted = [];
  artists.forEach((a, i) => (tops[i] || []).sort(() => rnd() - 0.5).slice(0, 2).forEach(t => wanted.push({ ...t, because: a.because })));
  const fromArtists = (await pool(wanted, 5, toYT)).map((t, i) => t && { ...t, art: wanted[i].art || t.art, because: wanted[i].because }).filter(Boolean);
  // 3) mixes do YouTube a partir das suas mais tocadas
  const mixes = await pool(mixSeeds.slice(0, 4), 2, ytMix);
  const fromMix = mixes.flat().filter(t => t && !knownSet.has(norm(t.artist)));
  // junta, tira repetidas e já ouvidas, no máximo 2 por artista
  const out = [], per = {};
  const all = []; const a1 = fromArtists, a2 = fromMix.sort(() => rnd() - 0.5);
  for (let i = 0; i < Math.max(a1.length, a2.length); i++) { if (a1[i]) all.push(a1[i]); if (i % 2 === 0 && a2[i / 2]) all.push(a2[i / 2]); }
  for (const t of all) {
    const k = `${t.src}:${t.id}`, ak = norm(t.artist);
    if (heardSet.has(k) || out.some(x => x.id === t.id)) continue;
    if ((per[ak] = (per[ak] || 0) + 1) > 2) continue;
    out.push(t); if (out.length >= size) break;
  }
  return { tracks: out, artists: artists.slice(0, 10).map(a => ({ name: a.name, pic: a.pic, because: a.because })) };
}
/* rádio: quando a fila acaba, continua com músicas parecidas com a última */
async function radio(t) {
  let id = t.src === 'yt' ? t.id : null;
  if (!id) { const r = await youtube(`${t.artist} ${t.title}`).catch(() => []); id = r[0] && r[0].id; }
  if (!id) return [];
  return (await ytMix(id)).filter(x => x.id !== id);
}
ipcMain.handle('recommend', (_, o) => recommend(o || {}).catch(e => ({ tracks: [], artists: [], err: String(e) })));
ipcMain.handle('radio', (_, t) => radio(t).catch(() => []));

/* ---------------- YouTube Music: mais ouvidas da semana (só pras recomendações) ---------------- */
const YTM_VER = '1.20250101.01.00';
async function ytm(body, auth) {
  const txt = await getText('https://music.youtube.com/youtubei/v1/browse?prettyPrint=false',
    { 'Content-Type': 'application/json', Origin: 'https://music.youtube.com', Referer: 'https://music.youtube.com/', 'X-Youtube-Client-Name': '67', 'X-Youtube-Client-Version': YTM_VER, ...(auth || {}) },
    { method: 'POST', body: JSON.stringify({ context: { client: { clientName: 'WEB_REMIX', clientVersion: YTM_VER, hl: 'pt', gl: 'BR' } }, ...body }) });
  return JSON.parse(txt);
}
const runsTxt = o => !o ? '' : o.runs ? o.runs.map(r => r.text).join('') : (o.simpleText || '');
function findKey(o, key, depth = 0) {
  if (!o || typeof o !== 'object' || depth > 12) return undefined;
  if (key in o) return o[key];
  for (const v of Object.values(o)) { const r = findKey(v, key, depth + 1); if (r !== undefined) return r; }
}
function ytmParse(j) {
  const songs = [], lists = [];
  (function walk(x, shelf) {
    if (Array.isArray(x)) return x.forEach(y => walk(y, shelf));
    if (!x || typeof x !== 'object') return;
    const sh = x.musicCarouselShelfRenderer || x.musicShelfRenderer || x.musicPlaylistShelfRenderer;
    if (sh) {
      const t = runsTxt((sh.header && sh.header.musicCarouselShelfBasicHeaderRenderer && sh.header.musicCarouselShelfBasicHeaderRenderer.title) || sh.title) || shelf;
      return walk(sh.contents, t);
    }
    if (x.musicResponsiveListItemRenderer) {
      const r = x.musicResponsiveListItemRenderer;
      const cols = (r.flexColumns || []).map(c => runsTxt(c.musicResponsiveListItemFlexColumnRenderer && c.musicResponsiveListItemFlexColumnRenderer.text));
      const we = findKey(r, 'watchEndpoint');
      const id = (r.playlistItemData && r.playlistItemData.videoId) || (we && we.videoId);
      const len = (r.fixedColumns || []).map(c => runsTxt(c.musicResponsiveListItemFixedColumnRenderer && c.musicResponsiveListItemFixedColumnRenderer.text)).find(t => /^\d+:\d+/.test(t));
      if (id && cols[0]) songs.push({ src: 'yt', id, title: cols[0], artist: (cols[1] || '').split(' • ')[0], art: `https://i.ytimg.com/vi/${id}/hqdefault.jpg`, duration: len ? len.split(':').reduce((a, b) => a * 60 + (+b), 0) * 1000 : 0, shelf });
      else { const be = findKey(r, 'browseEndpoint'); if (be && /^(VL|PL|RD|OL)/.test(be.browseId || '')) lists.push({ id: be.browseId, title: cols[0], sub: cols[1] || '', shelf }); }
      return;
    }
    if (x.musicTwoRowItemRenderer) {
      const r = x.musicTwoRowItemRenderer, ne = r.navigationEndpoint || {};
      const title = runsTxt(r.title), sub = runsTxt(r.subtitle);
      const thumbs = findKey(r.thumbnailRenderer, 'thumbnails') || [];
      const art = thumbs.length ? thumbs[thumbs.length - 1].url : '';
      if (ne.watchEndpoint && ne.watchEndpoint.videoId) { const id = ne.watchEndpoint.videoId; songs.push({ src: 'yt', id, title, artist: sub.split(' • ')[0], art: `https://i.ytimg.com/vi/${id}/hqdefault.jpg`, duration: 0, shelf, video: true }); }
      else if (ne.browseEndpoint && ne.browseEndpoint.browseId) lists.push({ id: ne.browseEndpoint.browseId, title, sub, art, shelf });
      return;
    }
    Object.values(x).forEach(y => walk(y, shelf));
  })(j, '');
  const uniq = l => l.filter((v, i, a) => a.findIndex(w => w.id === v.id) === i);
  return { songs: uniq(songs), lists: uniq(lists) };
}
async function ytmPlaylist(id) {
  const k = 'ytmpl|' + id; const c = sCache.get(k); if (c && Date.now() - c.at < 3 * 3600e3) return c.v;
  const r = ytmParse(await ytm({ browseId: id.startsWith('VL') || !/^(PL|RD|OL)/.test(id) ? id : 'VL' + id })).songs.map(({ shelf, ...t }) => t);
  if (r.length) sCache.set(k, { at: Date.now(), v: r });
  return r;
}
async function ytmCharts(gl = 'BR') {
  const k = 'charts|' + gl; const c = sCache.get(k); if (c && Date.now() - c.at < 3 * 3600e3) return c.v;
  const j = await ytm({ browseId: 'FEmusic_charts', formData: { selectedValues: [gl] } });
  const { songs, lists } = ytmParse(j);
  const pl = lists.find(l => /top|100|mais/i.test(l.title) && /m[uú]sica|song|faixa/i.test(l.title + ' ' + l.shelf)) || lists.find(l => /top|100/i.test(l.title)) || lists[0];
  let top = pl ? await ytmPlaylist(pl.id).catch(() => []) : [];
  const videos = songs.filter(s => s.video || /v[ií]deo/i.test(s.shelf)).map(({ shelf, ...t }) => t);
  if (!top.length) top = songs.map(({ shelf, ...t }) => t);
  const v = { top: top.slice(0, 50), videos: videos.slice(0, 20), lists: lists.filter(l => l !== pl).slice(0, 12), title: (pl && pl.title) || 'Mais ouvidas da semana' };
  if (v.top.length) sCache.set(k, { at: Date.now(), v });
  return v;
}
ipcMain.handle('ytm-charts', (_, gl) => ytmCharts(gl).catch(e => ({ top: [], videos: [], lists: [], err: String(e) })));
ipcMain.handle('ytm-playlist', (_, id) => ytmPlaylist(id).catch(() => []));

/* ---------------- exportar slowed / nightcore (ffmpeg) ---------------- */
let ffmpegBin = null;
function ffmpegPath() {
  if (ffmpegBin) return ffmpegBin;
  try { ffmpegBin = require('ffmpeg-static').replace('app.asar', 'app.asar.unpacked'); } catch { ffmpegBin = null; }
  return ffmpegBin;
}
let exportJob = null;
async function exportFx(sender, items, fx) {
  const bin = ffmpegPath(); if (!bin || !fs.existsSync(bin)) throw new Error('ffmpeg não encontrado');
  const { spawn } = require('child_process');
  mm = mm || require('music-metadata');
  const dir = app.getPath('music');
  const rate = Math.min(2, Math.max(0.5, Number(fx.rate) || 1));
  const label = fx.label || (rate < 1 ? 'Slowed' : 'Nightcore');
  const job = exportJob = { stop: false, proc: null };
  const send = d => { try { if (!sender.isDestroyed()) sender.send('export-progress', d); } catch {} };
  const done = [];
  for (let i = 0; i < items.length && !job.stop; i++) {
    const it = items[i];
    try {
      let sr = 44100, dur = 0;
      try { const m = await mm.parseFile(it.path, { duration: true, skipCovers: true }); sr = m.format.sampleRate || sr; dur = m.format.duration || 0; } catch {}
      const af = [`asetrate=${Math.round(sr * rate)}`, `aresample=${sr}`];
      if (fx.bass) af.push('bass=g=5:f=90');
      if (fx.reverb) af.push('aecho=0.8:0.88:60|110|170:0.32|0.22|0.12');
      if (fx.retro === 'k7') af.push('highpass=f=70', 'equalizer=f=180:t=q:w=1:g=-4', 'equalizer=f=1500:t=q:w=0.7:g=2', 'lowpass=f=7000', 'vibrato=f=0.55:d=0.05', 'vibrato=f=6.5:d=0.012');
      if (fx.retro === 'vinil') af.push('highpass=f=35', 'equalizer=f=180:t=q:w=1:g=-2', 'lowpass=f=11000', 'vibrato=f=0.55:d=0.02');
      af.push('alimiter=limit=0.95');
      // chiado da fita / estalos do vinil misturados por baixo
      const noise = fx.retro === 'k7' ? `anoisesrc=color=pink:amplitude=0.03:sample_rate=${sr},bandpass=f=4000:w=6000,volume=0.6`
        : fx.retro === 'vinil' ? `aevalsrc='lt(random(0)\,0.0004)*(random(1)-0.5)*1.4+(random(2)-0.5)*0.03':s=${sr},highpass=f=1200` : '';
      const base = `${it.artist ? it.artist + ' - ' : ''}${it.title} (${label})`.replace(/[\\/:*?"<>|]/g, '').slice(0, 150);
      let out = path.join(dir, base + '.mp3'); let n = 2; while (fs.existsSync(out)) out = path.join(dir, `${base} ${n++}.mp3`);
      const run = withCover => new Promise((ok, bad) => {
        const args = ['-hide_banner', '-y', '-i', it.path];
        if (!noise) args.push('-map', '0:a:0');
        if (withCover) args.push('-map', '0:v?', '-c:v', 'copy', '-disposition:v', 'attached_pic');
        if (noise) args.push('-filter_complex', `[0:a:0]${af.join(',')}[m];${noise}[n];[m][n]amix=inputs=2:duration=first:normalize=0[o]`, '-map', '[o]');
        else args.push('-af', af.join(','));
        args.push('-c:a', 'libmp3lame', '-q:a', '2', '-map_metadata', '0', '-id3v2_version', '3',
          '-metadata', `title=${it.title} (${label})`, '-metadata', `artist=${it.artist || ''}`, out);
        const p = job.proc = spawn(bin, args, { windowsHide: true });
        let err = '';
        p.stderr.on('data', d => {
          const s = d.toString(); err = (err + s).slice(-2000);
          const m = /time=(\d+):(\d+):(\d+\.?\d*)/.exec(s);
          if (m && dur) send({ i, n: items.length, pct: Math.min(99, ((+m[1] * 3600 + +m[2] * 60 + +m[3]) * rate) / dur * 100) });
        });
        p.on('close', code => code === 0 ? ok() : bad(new Error(err.split('\n').filter(Boolean).pop() || 'erro ' + code)));
        p.on('error', bad);
      });
      try { await run(true); } catch (e) { if (job.stop) throw e; try { fs.unlinkSync(out); } catch {} await run(false); }
      done.push(out); send({ i, n: items.length, pct: 100, file: out });
    } catch (e) { if (job.stop) break; send({ i, n: items.length, err: String(e.message || e) }); }
  }
  exportJob = null;
  return { files: done, dir, stopped: job.stop };
}
ipcMain.handle('export-fx', (e, items, fx) => exportFx(e.sender, items || [], fx || {}).catch(err => ({ files: [], err: String(err.message || err) })));
ipcMain.handle('export-stop', () => { if (exportJob) { exportJob.stop = true; try { exportJob.proc && exportJob.proc.kill(); } catch {} } return true; });
ipcMain.handle('music-dir', () => app.getPath('music'));

/* SoundCloud: mais tocadas da semana (charts oficiais; se o SoundCloud não entregar, usa a vitrine de descobertas dele) */
const scMap = t => ({
  src: 'sc', id: t.permalink_url.replace(/^https:\/\/soundcloud\.com\//, ''), url: t.permalink_url, title: t.title,
  artist: (t.publisher_metadata && t.publisher_metadata.artist) || (t.user && t.user.username) || '', art: (t.artwork_url || (t.user && t.user.avatar_url) || '').replace('-large.', '-t500x500.'),
  duration: t.duration || 0, genre: t.genre || '', plays: t.playback_count || 0
});
async function scCharts(region = 'BR') {
  const k = 'sccharts|' + region; const c = sCache.get(k); if (c && Date.now() - c.at < 3 * 3600e3) return c.v;
  let id = await scClientId(false).catch(() => null);
  const reg = region === 'ZZ' ? '' : `&region=soundcloud:regions:${region}`;
  const tries = [
    `/charts?kind=top&genre=soundcloud:genres:all-music${reg}&high_tier_only=false&limit=50`,
    `/charts?kind=trending&genre=soundcloud:genres:all-music${reg}&high_tier_only=false&limit=50`,
    `/charts?kind=top&genre=soundcloud:genres:all-music&high_tier_only=false&limit=50`,
    `/charts?kind=trending&genre=soundcloud:genres:all-music&high_tier_only=false&limit=50`
  ];
  let out = [], renewed = false;
  for (const t of tries) {
    if (!id) break;
    try { const j = await getJSON(`https://api-v2.soundcloud.com${t}&client_id=${id}`); out = (j.collection || []).map(x => x.track || x).filter(x => x && x.permalink_url).map(scMap); }
    catch (e) { if ((e.status === 401 || e.status === 403) && !renewed) { renewed = true; scId = null; store.scid = null; id = await scClientId(true).catch(() => null); try { const j = await getJSON(`https://api-v2.soundcloud.com${t}&client_id=${id}`); out = (j.collection || []).map(x => x.track || x).filter(x => x && x.permalink_url).map(scMap); } catch {} } }
    if (out.length >= 10) break;
  }
  // plano B: as mais tocadas no SoundCloud dos artistas que estão em alta agora
  if (out.length < 10) {
    let names = [];
    try { const yc = await ytmCharts(region === 'ZZ' ? 'ZZ' : region); names = [...new Set((yc.top || []).map(t => (t.artist || '').split(/,| & | e | x /i)[0].trim()).filter(Boolean))].slice(0, 12); } catch {}
    if (names.length < 5) { try { names = [...new Set([...names, ...(await audiusTrending(null, 'week')).map(t => t.artist)])].slice(0, 12); } catch {} }
    const found = (await pool(names, 4, n => soundcloud(n).catch(() => []))).flat().filter(Boolean);
    const byArtist = {};
    out = found.filter(t => (byArtist[t.artist] = (byArtist[t.artist] || 0) + 1) <= 3);
    sCache.set(k + '|fallback', { at: Date.now(), v: 1 });
  }
  out = out.filter((v, i, a) => a.findIndex(w => w.id === v.id) === i).slice(0, 50);
  if (out.length) sCache.set(k, { at: Date.now(), v: out });
  return out;
}
ipcMain.handle('sc-charts', (_, region) => scCharts(region).catch(() => []));

/* playlists mais populares do YouTube Music (página inicial + paradas), agrupadas pela prateleira */
async function ytmPopularLists(gl = 'BR') {
  const k = 'ytmlists|' + gl; const c = sCache.get(k); if (c && Date.now() - c.at < 6 * 3600e3) return c.v;
  const ok = l => /^(VL)?(PL|RDCLAK|OLAK)/.test(l.id || '');
  const groups = new Map();
  const add = (shelf, l) => { if (!ok(l)) return; const g = groups.get(shelf) || []; if (!g.some(x => x.id === l.id) && g.length < 20) g.push(l); groups.set(shelf, g); };
  const [home, charts] = await Promise.all([ytm({ browseId: 'FEmusic_home' }).catch(() => null), ytm({ browseId: 'FEmusic_charts', formData: { selectedValues: [gl] } }).catch(() => null)]);
  if (charts) ytmParse(charts).lists.forEach(l => add('Paradas do YouTube Music', l));
  if (home) ytmParse(home).lists.forEach(l => add(l.shelf || 'Playlists em destaque', l));
  const seen = new Set();
  const v = [...groups.entries()].map(([shelf, items]) => ({ shelf, items: items.filter(l => !seen.has(l.id) && seen.add(l.id)) })).filter(g => g.items.length >= 2).slice(0, 6);
  if (v.length) sCache.set(k, { at: Date.now(), v });
  return v;
}
ipcMain.handle('ytm-lists', (_, gl) => ytmPopularLists(gl).catch(() => []));

/* ---------------- rádio ao vivo (Radio Browser: catálogo aberto e gratuito de rádios do mundo todo) ---------------- */
const RB = ['https://de1.api.radio-browser.info', 'https://nl1.api.radio-browser.info', 'https://at1.api.radio-browser.info', 'https://fi1.api.radio-browser.info'];
let rbi = 0;
async function rb(pathq) {
  for (let i = 0; i < RB.length; i++) {
    const base = RB[(rbi + i) % RB.length];
    try { const j = await getJSON(base + pathq, { 'User-Agent': 'UncrownedMusic/1.0' }); rbi = (rbi + i) % RB.length; return j; } catch {}
  }
  return [];
}
const rbMap = x => ({
  src: 'radio', id: x.stationuuid, title: (x.name || '').trim(), stream: x.url_resolved || x.url,
  artist: [x.state || x.country, (x.tags || '').split(',').filter(Boolean).slice(0, 2).join(', ')].filter(Boolean).join(' · '),
  art: x.favicon || '', codec: x.codec || '', bitrate: x.bitrate || 0, country: x.countrycode || '', votes: x.votes || 0
});
async function radios({ q = '', tag = '', country = 'BR', limit = 80 } = {}) {
  const k = `radio|${q}|${tag}|${country}`; const c = sCache.get(k); if (c && Date.now() - c.at < 30 * 60e3) return c.v;
  const p = new URLSearchParams({ hidebroken: 'true', order: 'clickcount', reverse: 'true', limit: String(limit) });
  if (q) p.set('name', q); if (tag) p.set('tag', tag); if (country && country !== 'ZZ') p.set('countrycode', country);
  const list = await rb('/json/stations/search?' + p.toString());
  const v = (Array.isArray(list) ? list : []).filter(x => !x.hls && (x.url_resolved || x.url) && x.name).map(rbMap)
    .filter((s, i, a) => a.findIndex(o => o.title.toLowerCase() === s.title.toLowerCase()) === i);
  if (v.length) sCache.set(k, { at: Date.now(), v });
  return v;
}
ipcMain.handle('radios', (_, o) => radios(o || {}).catch(() => []));
ipcMain.handle('radio-click', (_, id) => rb('/json/url/' + encodeURIComponent(id)).then(() => true).catch(() => false));


/* sincronizar do SoundCloud: só quando o artista liberou o download oficial */
async function scDownload(id) {
  try {
    const cid = await scClientId(false); if (!cid) throw new Error('SoundCloud indisponível agora');
    const tr = await getJSON(`https://api-v2.soundcloud.com/resolve?url=${encodeURIComponent('https://soundcloud.com/' + id)}&client_id=${cid}`);
    if (!tr || !tr.downloadable) return { ok: false, error: 'o artista não liberou o download dessa música no SoundCloud' };
    const j = await getJSON(`https://api-v2.soundcloud.com/tracks/${tr.id}/download?client_id=${cid}`);
    if (!j || !j.redirectUri) return { ok: false, error: 'o artista não liberou o download dessa música no SoundCloud' };
    const artist = (tr.publisher_metadata && tr.publisher_metadata.artist) || (tr.user && tr.user.username) || '';
    const f = await download(j.redirectUri, `${artist ? artist + ' - ' : ''}${tr.title}`);
    return { ok: true, file: f };
  } catch (e) { return { ok: false, error: e.status === 404 || e.status === 401 ? 'o artista não liberou o download dessa música no SoundCloud' : String(e.message || e) }; }
}
ipcMain.handle('sc-download', (_, id) => scDownload(id));

/* ---------------- sinal ICY das rádios: lê o nome da música que está tocando ----------------
   As rádios mandam, no meio do áudio, um pedacinho com "StreamTitle='Artista - Música'".
   Conexão feita na mão porque muitas rádios respondem "ICY 200 OK", que o Node não entende. */
const net = require('net'), tls = require('tls');
function icyConnect(url, onHead, redirects = 4) {
  return new Promise((resolve, reject) => {
    let u; try { u = new URL(url); } catch (e) { return reject(e); }
    const secure = u.protocol === 'https:';
    const port = +u.port || (secure ? 443 : 80);
    const sock = secure ? tls.connect({ host: u.hostname, port, servername: u.hostname }) : net.connect({ host: u.hostname, port });
    sock.setTimeout(12000, () => sock.destroy(new Error('tempo esgotado')));
    sock.once('error', reject);
    sock.once(secure ? 'secureConnect' : 'connect', () => {
      sock.write(`GET ${u.pathname || '/'}${u.search} HTTP/1.0\r\nHost: ${u.host}\r\nUser-Agent: ${UA}\r\nIcy-MetaData: 1\r\nAccept: */*\r\nConnection: close\r\n\r\n`);
    });
    let head = Buffer.alloc(0);
    const onData = d => {
      head = Buffer.concat([head, d]);
      const i = head.indexOf('\r\n\r\n'); if (i < 0) { if (head.length > 16384) sock.destroy(new Error('cabeçalho inválido')); return; }
      sock.removeListener('data', onData);
      const lines = head.slice(0, i).toString('latin1').split('\r\n');
      const code = +((lines[0].match(/\s(\d{3})/) || [])[1] || 0);
      const h = {}; lines.slice(1).forEach(l => { const k = l.indexOf(':'); if (k > 0) h[l.slice(0, k).trim().toLowerCase()] = l.slice(k + 1).trim(); });
      const rest = head.slice(i + 4);
      if (code >= 300 && code < 400 && h.location && redirects > 0) { sock.destroy(); return resolve(icyConnect(new URL(h.location, u).toString(), onHead, redirects - 1)); }
      if (code < 200 || code >= 300) { sock.destroy(); return reject(new Error('a rádio respondeu ' + code)); }
      resolve({ sock, headers: h, rest });
    };
    sock.on('data', onData);
  });
}
// separa o áudio dos pedacinhos de informação; chama onAudio(bytes) e onTitle(título)
function icyReader(metaint, onAudio, onTitle) {
  let left = metaint, metaLen = -1, meta = [];
  return chunk => {
    let i = 0;
    while (i < chunk.length) {
      if (!metaint) { onAudio(chunk.slice(i)); return; }
      if (metaLen < 0 && left > 0) { const n = Math.min(left, chunk.length - i); onAudio(chunk.slice(i, i + n)); i += n; left -= n; continue; }
      if (metaLen < 0) { metaLen = chunk[i++] * 16; meta = []; if (metaLen === 0) { metaLen = -1; left = metaint; } continue; }
      const n = Math.min(metaLen, chunk.length - i); meta.push(chunk.slice(i, i + n)); i += n; metaLen -= n;
      if (metaLen === 0) {
        metaLen = -1; left = metaint;
        const txt = Buffer.concat(meta).toString('utf8').replace(/\0+$/, '');
        const m = /StreamTitle='(.*?)';/s.exec(txt);
        if (m) onTitle(fixMojibake(m[1].trim()));
      }
    }
  };
}
const fixMojibake = s => /Ã.|Â./.test(s) ? Buffer.from(s, 'latin1').toString('utf8') : s;
// só pega o título atual e desconecta (usado pelo Caçador de rádio)
async function icyNow(stream) {
  const { sock, headers, rest } = await icyConnect(stream);
  const metaint = +headers['icy-metaint'] || 0;
  if (!metaint) { sock.destroy(); return null; }
  return new Promise(resolve => {
    let done = false, got = 0;
    const finish = v => { if (done) return; done = true; sock.destroy(); resolve(v); };
    const rd = icyReader(metaint, b => { got += b.length; if (got > metaint * 3) finish(null); }, t => finish(t || null));
    sock.on('data', rd); sock.on('close', () => finish(null)); sock.on('error', () => finish(null));
    setTimeout(() => finish(null), 15000);
    if (rest.length) rd(rest);
  });
}
ipcMain.handle('icy-now', (_, stream) => icyNow(stream).catch(() => null));

/* ---------------- Caçador de rádio: vigia várias rádios e avisa quando tocar quem você curte ---------------- */
const hunt = { on: false, artists: [], stations: [], timer: null, seen: new Map(), sender: null, now: {} };
const normT = x => (x || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, ' ').trim();
async function huntCycle() {
  if (!hunt.on || !hunt.stations.length || !hunt.artists.length) return;
  const arts = hunt.artists.map(a => ({ a, n: normT(a) })).filter(x => x.n.length >= 3);
  let i = 0;
  const worker = async () => {
    while (i < hunt.stations.length && hunt.on) {
      const stn = hunt.stations[i++];
      const title = await icyNow(stn.stream).catch(() => null);
      if (!title) continue;
      hunt.now[stn.id] = { title, at: Date.now() };
      const nt = ' ' + normT(title) + ' ';
      const hit = arts.find(x => nt.includes(' ' + x.n + ' '));
      if (!hit) continue;
      const key = stn.id + '|' + normT(title);
      if (Date.now() - (hunt.seen.get(key) || 0) < 40 * 60e3) continue;
      hunt.seen.set(key, Date.now());
      try { if (hunt.sender && !hunt.sender.isDestroyed()) hunt.sender.send('hunt-hit', { station: stn, title, artist: hit.a, at: Date.now() }); } catch {}
    }
  };
  await Promise.all([worker(), worker(), worker(), worker()]);
  try { if (hunt.sender && !hunt.sender.isDestroyed()) hunt.sender.send('hunt-now', hunt.now); } catch {}
}
ipcMain.handle('hunt-config', async (e, cfg) => {
  hunt.sender = e.sender; hunt.on = !!cfg.on; hunt.artists = (cfg.artists || []).slice(0, 40);
  let st = (cfg.stations || []).filter(s => s && s.stream);
  if (cfg.useTop) { const top = await radios({ country: cfg.country || 'BR', limit: 60 }).catch(() => []); st = [...st, ...top]; }
  hunt.stations = st.filter((s, i, a) => a.findIndex(o => o.id === s.id) === i).slice(0, cfg.max || 40);
  clearInterval(hunt.timer);
  if (hunt.on) { hunt.timer = setInterval(huntCycle, (cfg.eco ? 10 : 2) * 60e3); setTimeout(huntCycle, 3000); }
  return { stations: hunt.stations.length };
});
ipcMain.handle('hunt-scan', () => huntCycle().then(() => hunt.now));

/* ---------------- gravar a rádio, cortando um arquivo por música ---------------- */
let rec = null;
ipcMain.handle('rec-start', async (e, station, opts = {}) => {
  if (rec) return { err: 'Já está gravando' };
  const sender = e.sender;
  const send = (c, d) => { try { if (!sender.isDestroyed()) sender.send(c, d); } catch {} };
  let conn;
  try { conn = await icyConnect(station.stream); } catch (er) { return { err: String(er.message || er) }; }
  const { sock, headers, rest } = conn;
  const ct = (headers['content-type'] || 'audio/mpeg').toLowerCase();
  const ext = /aac|mp4/.test(ct) ? 'aac' : /ogg/.test(ct) ? 'ogg' : 'mp3';
  const d = new Date(), pad = n => String(n).padStart(2, '0');
  const stamp = `${pad(d.getDate())}-${pad(d.getMonth() + 1)}-${d.getFullYear()} ${pad(d.getHours())}h${pad(d.getMinutes())}`;
  const safeName = x => x.replace(/[\\/:*?"<>|]/g, '').replace(/\s+/g, ' ').trim().slice(0, 140);
  const split = opts.split !== false && +headers['icy-metaint'] > 0;
  const dir = split ? path.join(app.getPath('music'), safeName(`Rádio ${station.title} - ${stamp}`)) : app.getPath('music');
  fs.mkdirSync(dir, { recursive: true });
  const files = [];
  let cur = null, part = 0;
  const openFile = title => {
    if (cur) { cur.ws.end(); if (cur.bytes < 16000) { const f = cur.file; cur.ws.on('close', () => { try { fs.unlinkSync(f); } catch {} }); } else files.push(cur.file); }
    part++;
    const base = split ? safeName(`${String(part).padStart(2, '0')} - ${title || station.title}`) : safeName(`${station.title} - ${stamp}`);
    let file = path.join(dir, base + '.' + ext), n = 2; while (fs.existsSync(file)) file = path.join(dir, `${base} ${n++}.${ext}`);
    cur = { file, ws: fs.createWriteStream(file), bytes: 0, title };
    send('rec-song', { title: title || '', part });
  };
  openFile(null);
  const rd = icyReader(+headers['icy-metaint'] || 0, b => { if (cur) { cur.ws.write(b); cur.bytes += b.length; } }, t => {
    if (rec) rec.title = t;
    if (split && t && (!cur || t !== cur.title)) openFile(t);
    else if (cur && !cur.title) cur.title = t;
  });
  rec = { sock, start: Date.now(), dir, title: '' };
  sock.on('data', rd);
  sock.on('close', () => {
    if (cur) { const c = cur; cur = null; c.ws.end(() => { if (c.bytes < 16000) { try { fs.unlinkSync(c.file); } catch {} } else files.push(c.file); rec = null; send('rec-done', { file: files[files.length - 1] || null, files, dir, split, err: files.length ? '' : 'a rádio não mandou áudio' }); }); }
    else { rec = null; send('rec-done', { files, dir, split }); }
  });
  if (rest.length) rd(rest);
  return { ok: true, split, dir };
});
ipcMain.handle('rec-stop', () => { if (!rec) return null; try { rec.sock.destroy(); } catch {} return rec.dir; });

ipcMain.handle('focus-win', () => { if (win) { if (win.isMinimized()) win.restore(); win.show(); win.focus(); } });
/* nome da rede em que o PC está (pra saber se é casa, trampo...). Funciona com Wi-Fi e cabo no Windows. */
let netCache = { at: 0, name: '' };
function netName() {
  if (Date.now() - netCache.at < 10 * 60e3) return Promise.resolve(netCache.name);
  if (process.platform !== 'win32') return Promise.resolve('');
  const { execFile } = require('child_process');
  const run = (cmd, args) => new Promise(r => execFile(cmd, args, { windowsHide: true, timeout: 6000 }, (e, out) => r(e ? '' : String(out || ''))));
  return (async () => {
    let name = (await run('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', '(Get-NetConnectionProfile | Where-Object { $_.IPv4Connectivity -eq "Internet" } | Select-Object -First 1).Name'])).trim();
    if (!name) { const w = await run('netsh', ['wlan', 'show', 'interfaces']); const m = /^\s*SSID\s*:\s*(.+)$/m.exec(w); if (m) name = m[1].trim(); }
    netCache = { at: Date.now(), name };
    return name;
  })();
}
ipcMain.handle('net-name', () => netName().catch(() => ''));

/* ---------------- YouTube Music: artistas e álbuns (busca, página do artista, página do álbum) ---------------- */
const lastThumb = o => { const t = findKey(o, 'thumbnails'); return Array.isArray(t) && t.length ? t[t.length - 1].url.replace(/=w\d+-h\d+/, '=w544-h544') : ''; };
const pageTypeOf = o => { const c = findKey(o, 'browseEndpointContextMusicConfig'); return (c && c.pageType) || ''; };
async function ytmSearch(q) {
  const k = 'ytms|' + q.toLowerCase(); const c = sCache.get(k); if (c && Date.now() - c.at < 15 * 60e3) return c.v;
  const txt = await getText('https://music.youtube.com/youtubei/v1/search?prettyPrint=false',
    { 'Content-Type': 'application/json', Origin: 'https://music.youtube.com', Referer: 'https://music.youtube.com/', 'X-Youtube-Client-Name': '67', 'X-Youtube-Client-Version': YTM_VER },
    { method: 'POST', body: JSON.stringify({ context: { client: { clientName: 'WEB_REMIX', clientVersion: YTM_VER, hl: 'pt', gl: 'BR' } }, query: q }) });
  const j = JSON.parse(txt);
  const artists = [], albums = [], songs = [];
  (function walk(x) {
    if (Array.isArray(x)) return x.forEach(walk);
    if (!x || typeof x !== 'object') return;
    const r = x.musicResponsiveListItemRenderer || x.musicCardShelfRenderer;
    if (r) {
      const isCard = !!x.musicCardShelfRenderer;
      const cols = isCard ? [runsTxt(r.title), runsTxt(r.subtitle)] : (r.flexColumns || []).map(c => runsTxt(c.musicResponsiveListItemFlexColumnRenderer && c.musicResponsiveListItemFlexColumnRenderer.text));
      const nav = isCard ? ((r.title && r.title.runs && r.title.runs[0] && r.title.runs[0].navigationEndpoint) || {}) : (r.navigationEndpoint || {});
      const art = lastThumb(r.thumbnail || r);
      const songId = (r.playlistItemData && r.playlistItemData.videoId) || (nav.watchEndpoint && nav.watchEndpoint.videoId)
        || (!nav.browseEndpoint && (() => { const we = findKey(r.overlay || {}, 'watchEndpoint'); return we && we.videoId; })());
      const be = nav.browseEndpoint, pt = be ? pageTypeOf(be) : '';
      if (songId && cols[0]) { const parts = (cols[1] || '').split(' • '); songs.push({ src: 'yt', id: songId, title: cols[0], artist: parts.length > 1 && /^(m[uú]sica|song|v[ií]deo|video)$/i.test(parts[0]) ? parts[1] : parts[0], art: `https://i.ytimg.com/vi/${songId}/hqdefault.jpg`, duration: 0 }); }
      else if (be && /ARTIST/.test(pt)) artists.push({ id: be.browseId, name: cols[0], sub: (cols[1] || '').split(' • ').slice(1).join(' • ') || 'Artista', art });
      else if (be && /ALBUM/.test(pt)) { const parts = (cols[1] || '').split(' • '); albums.push({ id: be.browseId, title: cols[0], type: parts[0] || 'Álbum', artist: parts[1] || '', year: parts.find(p => /^\d{4}$/.test(p)) || '', art }); }
      if (x.musicCardShelfRenderer) walk(r.contents);
      return;
    }
    Object.values(x).forEach(walk);
  })(j);
  const uniq = (l, f) => l.filter((v, i, a) => v[f] && a.findIndex(w => w[f] === v[f]) === i);
  const v = { artists: uniq(artists, 'id').slice(0, 10), albums: uniq(albums, 'id').slice(0, 12), songs: uniq(songs, 'id').slice(0, 10) };
  sCache.set(k, { at: Date.now(), v });
  return v;
}
async function ytmArtist(id) {
  const k = 'ytma|' + id; const c = sCache.get(k); if (c && Date.now() - c.at < 3 * 3600e3) return c.v;
  const j = await ytm({ browseId: id });
  const head = findKey(j, 'musicImmersiveHeaderRenderer') || findKey(j, 'musicVisualHeaderRenderer') || findKey(j, 'musicHeaderRenderer') || {};
  const name = runsTxt(head.title);
  const art = lastThumb(head.thumbnail || head.foregroundThumbnail || head);
  const subs = runsTxt((head.subscriptionButton && findKey(head.subscriptionButton, 'subscriberCountText')) || head.monthlyListenerCount || {});
  // prateleiras: músicas populares, álbuns, singles, vídeos
  const shelves = [];
  (function walk(x) {
    if (Array.isArray(x)) return x.forEach(walk);
    if (!x || typeof x !== 'object') return;
    const sh = x.musicShelfRenderer || x.musicCarouselShelfRenderer;
    if (sh) {
      const title = runsTxt((sh.header && sh.header.musicCarouselShelfBasicHeaderRenderer && sh.header.musicCarouselShelfBasicHeaderRenderer.title) || sh.title);
      const more = findKey(sh.bottomEndpoint || (sh.header && sh.header.musicCarouselShelfBasicHeaderRenderer && sh.header.musicCarouselShelfBasicHeaderRenderer.moreContentButton) || {}, 'browseEndpoint');
      const p = ytmParse({ contents: sh.contents });
      const items = (sh.contents || []).map(it => {
        const r = it.musicTwoRowItemRenderer; if (!r) return null;
        const be = r.navigationEndpoint && r.navigationEndpoint.browseEndpoint; if (!be) return null;
        return { id: be.browseId, title: runsTxt(r.title), sub: runsTxt(r.subtitle), art: lastThumb(r.thumbnailRenderer), type: pageTypeOf(r.navigationEndpoint) };
      }).filter(Boolean);
      shelves.push({ title, more: more && more.browseId, songs: p.songs.map(({ shelf, ...t }) => ({ ...t, artist: t.artist || name })), items });
      return;
    }
    Object.values(x).forEach(walk);
  })(j);
  const songShelf = shelves.find(s => s.songs.length && !/v[ií]deo/i.test(s.title)) || shelves.find(s => s.songs.length);
  let top = songShelf ? songShelf.songs : [];
  if (songShelf && songShelf.more) { const all = await ytmPlaylist(songShelf.more).catch(() => []); if (all.length > top.length) top = all; }
  const pick = re => shelves.filter(s => re.test(s.title)).flatMap(s => s.items).filter(i => /ALBUM|PLAYLIST/.test(i.type) || /^MPRE/.test(i.id));
  const albums = pick(/[áa]lbu|album/i), singles = pick(/single|ep/i);
  const videos = shelves.filter(s => /v[ií]deo/i.test(s.title)).flatMap(s => s.songs);
  const v = { id, name, art, subs, top: top.slice(0, 50), albums, singles, videos: videos.slice(0, 12) };
  if (name) sCache.set(k, { at: Date.now(), v });
  return v;
}
async function ytmAlbum(id) {
  const k = 'ytmal|' + id; const c = sCache.get(k); if (c && Date.now() - c.at < 6 * 3600e3) return c.v;
  const j = await ytm({ browseId: id });
  const head = findKey(j, 'musicResponsiveHeaderRenderer') || findKey(j, 'musicDetailHeaderRenderer') || {};
  const title = runsTxt(head.title), sub = runsTxt(head.subtitle), strap = runsTxt(head.straplineTextOne);
  const art = lastThumb(head.thumbnail || head);
  const artist = strap || (sub.split(' • ')[1] || '');
  const year = (sub.match(/\b(19|20)\d{2}\b/) || [''])[0];
  const tracks = ytmParse(j).songs.map(({ shelf, ...t }) => ({ ...t, artist: t.artist && !/^\d/.test(t.artist) ? t.artist : artist, album: title, art: art || t.art }));
  const v = { id, title, artist, year, art, type: sub.split(' • ')[0] || 'Álbum', tracks };
  if (tracks.length) sCache.set(k, { at: Date.now(), v });
  return v;
}
ipcMain.handle('ytm-search', (_, q) => ytmSearch(q).catch(() => ({ artists: [], albums: [], songs: [] })));
ipcMain.handle('ytm-artist', (_, id) => ytmArtist(id).catch(e => ({ id, err: String(e) })));
ipcMain.handle('ytm-album', (_, id) => ytmAlbum(id).catch(e => ({ id, err: String(e), tracks: [] })));

/* salva a imagem do card (Imagens/Uncrowned) */
ipcMain.handle('save-image', (_, data, name) => {
  const dir = path.join(app.getPath('pictures'), 'Uncrowned'); fs.mkdirSync(dir, { recursive: true });
  const base = String(name || 'card').replace(/[\\/:*?"<>|]/g, '').slice(0, 120);
  let f = path.join(dir, base + '.png'), n = 2; while (fs.existsSync(f)) f = path.join(dir, `${base} ${n++}.png`);
  fs.writeFileSync(f, Buffer.from(data)); return f;
});

/* ---------------- importar playlists: Spotify, YouTube / YouTube Music ---------------- */
function deepFind(o, key) {
  const stack = [o];
  while (stack.length) { const x = stack.pop(); if (!x || typeof x !== 'object') continue; if (Object.prototype.hasOwnProperty.call(x, key)) return x[key]; for (const v of Object.values(x)) if (v && typeof v === 'object') stack.push(v); }
}
// login do YouTube que já está no app: assina o pedido igual o site faz
async function ytmAuth() {
  const ck = await ytSession().cookies.get({ domain: '.youtube.com' }).catch(() => []);
  const sap = (ck.find(c => c.name === 'SAPISID') || ck.find(c => c.name === '__Secure-3PAPISID') || {}).value;
  if (!sap) return null;
  const ts = Math.floor(Date.now() / 1000), origin = 'https://music.youtube.com';
  const hash = require('crypto').createHash('sha1').update(`${ts} ${sap} ${origin}`).digest('hex');
  return { Cookie: ck.map(c => `${c.name}=${c.value}`).join('; '), Authorization: `SAPISIDHASH ${ts}_${hash}`, 'X-Origin': origin, 'X-Goog-AuthUser': '0' };
}
const contTok = j => { const c = deepFind(j, 'continuationCommand'); if (c && c.token) return c.token; const n = deepFind(j, 'nextContinuationData'); return n && n.continuation; };
async function ytmFullPlaylist(id, useAuth) {
  const auth = useAuth ? await ytmAuth() : null;
  const bid = /^(VL|MPRE|OLAK)/.test(id) ? id : 'VL' + id;
  const j = await ytm({ browseId: bid }, auth);
  const head = deepFind(j, 'musicResponsiveHeaderRenderer') || deepFind(j, 'musicDetailHeaderRenderer') || deepFind(j, 'musicEditablePlaylistDetailHeaderRenderer') || {};
  const title = runsTxt(head.title) || runsTxt(deepFind(head, 'title'));
  let songs = ytmParse(j).songs, tok = contTok(j);
  for (let n = 0; tok && n < 40; n++) { const k = await ytm({ continuation: tok }, auth).catch(() => null); if (!k) break; const more = ytmParse(k).songs; if (!more.length) break; songs = songs.concat(more); tok = contTok(k); }
  songs = songs.filter((v, i, a) => a.findIndex(w => w.id === v.id) === i).map(({ shelf, ...t }) => t);
  return { title, tracks: songs };
}
async function ytmLibrary() {
  const auth = await ytmAuth(); if (!auth) return { logged: false, lists: [] };
  const j = await ytm({ browseId: 'FEmusic_liked_playlists' }, auth);
  const lists = ytmParse(j).lists.filter(l => /^(VL)?(PL|LM|RDCLAK|OLAK|SE)/.test(l.id));
  if (!lists.some(l => /^(VL)?LM$/.test(l.id))) lists.unshift({ id: 'VLLM', title: 'Músicas curtidas', sub: 'YouTube Music' });
  return { logged: true, lists: lists.map(l => ({ ...l, title: /^(VL)?LM$/.test(l.id) ? 'Músicas curtidas' : l.title })) };
}
// link de playlist/álbum/música do Spotify: lê a página pública de "incorporar"
async function spotifyLink(url) {
  const m = /open\.spotify\.com\/(?:intl-[\w-]+\/)?(playlist|album|track)\/([A-Za-z0-9]+)/.exec(url); if (!m) return null;
  const html = await getText(`https://open.spotify.com/embed/${m[1]}/${m[2]}`);
  const mm = /<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/.exec(html); if (!mm) throw new Error('o Spotify não mostrou essa playlist (ela é pública?)');
  const ent = deepFind(JSON.parse(mm[1]), 'entity') || {};
  const items = m[1] === 'track' ? [{ title: ent.name || ent.title, artist: (ent.artists || []).map(a => a.name).join(', ') || ent.subtitle || '' }]
    : (ent.trackList || []).map(t => ({ title: t.title, artist: t.subtitle || '', dur: t.duration || 0 }));
  return { name: ent.name || ent.title || 'Playlist do Spotify', items: items.filter(i => i.title), from: 'Spotify' };
}
async function importLink(url) {
  url = (url || '').trim();
  if (/spotify\.com/.test(url)) return spotifyLink(url);
  const list = /[?&]list=([\w-]+)/.exec(url); const alb = /browse\/(MPRE[\w-]+)/.exec(url);
  if (list || alb) { const r = await ytmFullPlaylist(alb ? alb[1] : list[1], true); return { name: r.title || 'Playlist do YouTube', tracks: r.tracks, from: 'YouTube' }; }
  throw new Error('cole um link de playlist do Spotify, YouTube ou YouTube Music');
}
// arquivos de dados: Spotify (Baixar seus dados) e Google Takeout (YouTube e YouTube Music)
function csvRows(text) {
  const rows = []; let row = [], cur = '', q = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (q) { if (ch === '"') { if (text[i + 1] === '"') { cur += '"'; i++; } else q = false; } else cur += ch; }
    else if (ch === '"') q = true; else if (ch === ',') { row.push(cur); cur = ''; } else if (ch === '\n' || ch === '\r') { if (ch === '\r' && text[i + 1] === '\n') i++; row.push(cur); rows.push(row); row = []; cur = ''; } else cur += ch;
  }
  if (cur || row.length) { row.push(cur); rows.push(row); }
  return rows.filter(r => r.some(c => c.trim()));
}
function parseImportFiles(paths) {
  const out = { playlists: [], liked: [], history: {} };
  const handle = (name, text) => {
    const base = path.basename(name).replace(/\.(json|csv)$/i, '');
    if (/\.json$/i.test(name)) {
      let j; try { j = JSON.parse(text.replace(/^﻿/, '')); } catch { return; }
      if (j && Array.isArray(j.playlists)) j.playlists.forEach(pl => {
        const items = (pl.items || []).map(it => it.track || {}).filter(t => t.trackName).map(t => ({ title: t.trackName, artist: t.artistName || '' }));
        if (items.length) out.playlists.push({ name: pl.name || 'Playlist', items, from: 'Spotify' });
      });
      if (j && Array.isArray(j.tracks)) out.liked.push(...j.tracks.filter(t => t.track).map(t => ({ title: t.track, artist: t.artist || '' })));
      if (Array.isArray(j)) j.forEach(e => { const a = e.artistName || e.master_metadata_album_artist_name; const ms = e.msPlayed || e.ms_played || 0; if (a) out.history[a] = (out.history[a] || 0) + ms; });
      return;
    }
    const rows = csvRows(text.replace(/^﻿/, ''));
    const hi = rows.findIndex(r => r.some(c => /^(video id|id do v[ií]deo)$/i.test(c.trim())));
    if (hi < 0) return;
    const h = rows[hi].map(c => c.trim().toLowerCase());
    const vi = h.findIndex(c => /video id|id do v/.test(c)), ti = h.findIndex(c => /song title|t[ií]tulo da m|^title$|^t[ií]tulo$/.test(c)), ai = h.findIndex(c => /artist/.test(c));
    const items = rows.slice(hi + 1).map(r => ({ videoId: (r[vi] || '').trim(), title: ti >= 0 ? (r[ti] || '').trim() : '', artist: ai >= 0 ? (r[ai] || '').trim() : '' })).filter(x => /^[\w-]{11}$/.test(x.videoId));
    if (!items.length) return;
    const nm = base.replace(/-videos?$/i, '').replace(/^music library songs$/i, 'Biblioteca do YouTube Music');
    if (/liked|gostei|curtid|marquei como gostei/i.test(nm)) out.liked.push(...items);
    else out.playlists.push({ name: nm, items, from: 'YouTube' });
  };
  for (const p of paths) {
    try {
      if (/\.zip$/i.test(p)) { const Z = require('adm-zip'); new Z(p).getEntries().forEach(e => { if (!e.isDirectory && /\.(json|csv)$/i.test(e.entryName) && !/__MACOSX/.test(e.entryName)) handle(e.entryName, e.getData().toString('utf8')); }); }
      else handle(p, fs.readFileSync(p, 'utf8'));
    } catch (e) { console.error('import', p, e); }
  }
  return out;
}
ipcMain.handle('import-pick', async () => {
  const r = await dialog.showOpenDialog(win, { title: 'Escolha o arquivo de dados (Spotify ou Google Takeout)', properties: ['openFile', 'multiSelections'], filters: [{ name: 'Dados do Spotify / YouTube', extensions: ['zip', 'json', 'csv'] }] });
  if (r.canceled || !r.filePaths.length) return null;
  return parseImportFiles(r.filePaths);
});
ipcMain.handle('import-link', (_, url) => importLink(url).catch(e => ({ err: String(e.message || e) })));
ipcMain.handle('ytm-library', () => ytmLibrary().catch(e => ({ logged: false, lists: [], err: String(e.message || e) })));
ipcMain.handle('ytm-full-playlist', (_, id) => ytmFullPlaylist(id, true).catch(e => ({ title: '', tracks: [], err: String(e.message || e) })));
// transforma "artista - música" (ou id de vídeo) em música tocável do YouTube, com progresso
const resolveCache = new Map();
ipcMain.handle('import-resolve', async (e, items, tag) => {
  const send = d => { try { if (!e.sender.isDestroyed()) e.sender.send('import-progress', { tag, ...d }); } catch {} };
  let done = 0;
  const out = await pool(items, 6, async it => {
    const k = it.videoId || (norm(it.artist) + '|' + norm(it.title));
    let r = resolveCache.get(k);
    if (!r) {
      if (it.videoId) {
        let title = it.title, artist = it.artist;
        if (!title) { try { const o = await getJSON(`https://www.youtube.com/oembed?url=${encodeURIComponent('https://www.youtube.com/watch?v=' + it.videoId)}&format=json`); title = o.title; artist = artist || (o.author_name || '').replace(/ - Topic$/, ''); } catch {} }
        r = { src: 'yt', id: it.videoId, title: title || it.videoId, artist: artist || '', art: `https://i.ytimg.com/vi/${it.videoId}/hqdefault.jpg`, duration: 0 };
      } else r = await toYT({ title: it.title, artist: it.artist, dur: it.dur || 0 }).catch(() => null);
      if (r) resolveCache.set(k, r);
    }
    send({ done: ++done, total: items.length });
    return r;
  });
  return out.filter(Boolean);
});

/* temas: troca o ícone da janela/barra de tarefas junto */
const SKINS = ['edgy', 'fadinha', 'otaku', 'oldmoney', 'alternativo', 'punk', 'pulseirinhas', 'ninja', 'deserto', 'ouro', 'cela', 'rock', 'pavor', 'apagao', 'julie', 'nino', 'divino'];
function skinNow() { const k = store.data && store.data.settings && store.data.settings.skin; return SKINS.includes(k) ? k : 'edgy'; }
function applyWinSkin(k) { try { if (win && SKINS.includes(k)) win.setIcon(nativeImage.createFromPath(path.join(__dirname, 'renderer', 'themes', k + '.png'))); } catch {} }
ipcMain.handle('set-skin', (_, k) => { applyWinSkin(k); return true; });
/* bateria: avisa a tela quando o notebook sai/volta pra tomada */
ipcMain.handle('on-battery', () => { try { return powerMonitor.isOnBatteryPower(); } catch { return false; } });
app.whenReady().then(() => {
  const tell = v => { try { if (win && !win.isDestroyed()) win.webContents.send('power', v); } catch {} };
  powerMonitor.on('on-battery', () => tell(true)); powerMonitor.on('on-ac', () => tell(false));
});
/* sincronizar: acha a mesma música numa fonte que libera download e salva */
const nrm = x => (x || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/\(.*?\)|\[.*?\]/g, ' ').replace(/\b(official|oficial|video|audio|lyrics?|letra|clipe|hd|4k|visualizer|prod\.?.*|ft\.?.*|feat\.?.*)\b/g, ' ').replace(/[^a-z0-9]+/g, ' ').trim();
function sameSong(a, b) {
  const ta = nrm(a.title), tb = nrm(b.title), aa = nrm(a.artist), ab = nrm(b.artist);
  if (!ta || !tb) return 0;
  const tOk = ta === tb || ta.includes(tb) || tb.includes(ta);
  if (!tOk) return 0;
  const aOk = !aa || !ab || aa.includes(ab) || ab.includes(aa) || (nrm(b.title + ' ' + b.artist).includes(aa));
  return aOk ? (ta === tb ? 3 : 2) : 1;
}
async function syncFind(t) {
  const title = (t.title || '').replace(/\s*[\(\[].*?[\)\]]/g, '').replace(/^.*? - /, m => (t.artist && nrm(m).includes(nrm(t.artist)) ? '' : m)).trim();
  const q = `${t.artist || ''} ${title}`.trim();
  const ref = { title, artist: t.artist };
  const [au, ia, sc] = await Promise.all([audiusSearch(q).catch(() => []), archive(q).catch(() => []), soundcloud(q).catch(() => [])]);
  const cands = [...au.filter(x => x.download).map(x => ({ ...x, from: 'Audius' })), ...sc.filter(x => x.downloadable).map(x => ({ ...x, from: 'SoundCloud' })), ...ia.map(x => ({ ...x, from: 'Internet Archive' }))]
    .map(x => ({ x, s: sameSong(ref, x) })).filter(o => o.s >= 2).sort((a, b) => b.s - a.s);
  return cands.length ? cands[0].x : null;
}
async function syncOne(t) {
  if (t.src === 'sc') { const r = await scDownload(t.id); if (r.ok) return { ok: true, file: r.file, from: 'SoundCloud' }; }
  if (t.download) { try { return { ok: true, file: await download(t.download, `${t.artist} - ${t.title}`), from: t.src === 'au' ? 'Audius' : 'Internet Archive' }; } catch {} }
  const c = await syncFind(t);
  if (!c) return { ok: false, notFound: true };
  if (c.src === 'sc') { const r = await scDownload(c.id); return r.ok ? { ok: true, file: r.file, from: 'SoundCloud', alt: true } : { ok: false, notFound: true }; }
  try { return { ok: true, file: await download(c.download, `${c.artist} - ${c.title}`), from: c.from, alt: true }; } catch (e) { return { ok: false, error: String(e.message || e) }; }
}
ipcMain.handle('sync-one', (_, t) => syncOne(t).catch(e => ({ ok: false, error: String(e.message || e) })));
ipcMain.handle('sync-many', async (e, list, tag) => {
  const send = d => { try { if (!e.sender.isDestroyed()) e.sender.send('sync-progress', { tag, ...d }); } catch {} };
  let done = 0; const results = [];
  await pool(list, 3, async t => { const r = await syncOne(t).catch(er => ({ ok: false, error: String(er) })); results.push({ t, r }); send({ done: ++done, total: list.length, title: t.title, ok: r.ok }); });
  return results;
});
ipcMain.handle('store-get', () => store.data || {});
ipcMain.handle('store-set', (_, data) => { store.data = data; saveStore(); });
ipcMain.handle('pick-folder', async () => { const r = await dialog.showOpenDialog(win, { properties: ['openDirectory', 'multiSelections'] }); return r.canceled ? [] : r.filePaths; });
ipcMain.handle('default-folders', () => [app.getPath('music')]);
ipcMain.handle('scan', (_, folders, opts) => scan(folders, opts));
ipcMain.handle('search', (_, q) => searchAll(q));
ipcMain.handle('sc-stream', (_, id) => scStream(id).catch(() => null));
ipcMain.handle('trending', (_, genre, time) => audiusTrending(genre, time).catch(() => []));
ipcMain.handle('audius-search', (_, q) => audiusSearch(q).catch(() => []));
ipcMain.handle('lyrics', (_, a, t, d, s) => lyrics(a, t, d, s));
ipcMain.handle('info', (_, t, a) => songInfo(t, a));
ipcMain.handle('download', async (_, url, name) => { try { const f = await download(url, name); return { ok: true, file: f }; } catch (e) { return { ok: false, error: String(e.message || e) }; } });
ipcMain.handle('open-path', (_, p) => shell.showItemInFolder(p));
ipcMain.handle('open-url', (_, u) => shell.openExternal(u));
ipcMain.handle('port', () => PORT);
ipcMain.handle('title-color', (_, c) => { try { win.setTitleBarOverlay({ color: '#00000000', symbolColor: c, height: 40 }); } catch {} });

/* menos RAM: sem processo reserva ocioso, sem Cast, e coleta de lixo liberada pra quando o app vai pro fundo */
app.commandLine.appendSwitch('disable-features', 'SpareRendererForSitePerProcess,MediaRouter,DialMediaRouteProvider,AutofillServerCommunication');
app.commandLine.appendSwitch('js-flags', '--expose-gc');
app.setAppUserModelId('com.uncrowned.music.desktop');
app.whenReady().then(async () => {
  loadStore();
  nativeTheme.themeSource = 'dark';
  ytSession();
  const s0 = (store.data && store.data.settings) || {};
  setupAdblock(s0.adblock !== false);
  await startServer();
  createWindow();
  applyWinSkin(skinNow());
  setTimeout(warmUp, 1500);
});
app.on('window-all-closed', () => { try { fs.writeFileSync(storeFile(), JSON.stringify(store)); } catch {} app.quit(); });
