// Uncrowned Music — processo principal (janela, arquivos, busca online, letras, downloads)
const { app, BrowserWindow, ipcMain, dialog, shell, nativeTheme, session } = require('electron');
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
      duration: t.duration || 0, genre: t.genre || ''
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
async function ytm(body) {
  const txt = await getText('https://music.youtube.com/youtubei/v1/browse?prettyPrint=false',
    { 'Content-Type': 'application/json', Origin: 'https://music.youtube.com', Referer: 'https://music.youtube.com/', 'X-Youtube-Client-Name': '67', 'X-Youtube-Client-Version': YTM_VER },
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
      af.push('alimiter=limit=0.95');
      const base = `${it.artist ? it.artist + ' - ' : ''}${it.title} (${label})`.replace(/[\\/:*?"<>|]/g, '').slice(0, 150);
      let out = path.join(dir, base + '.mp3'); let n = 2; while (fs.existsSync(out)) out = path.join(dir, `${base} ${n++}.mp3`);
      const run = withCover => new Promise((ok, bad) => {
        const args = ['-hide_banner', '-y', '-i', it.path, '-map', '0:a:0'];
        if (withCover) args.push('-map', '0:v?', '-c:v', 'copy', '-disposition:v', 'attached_pic');
        args.push('-af', af.join(','), '-c:a', 'libmp3lame', '-q:a', '2', '-map_metadata', '0', '-id3v2_version', '3',
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
  const id = await scClientId(false).catch(() => null); if (!id) return [];
  const reg = region === 'ZZ' ? '' : `&region=soundcloud:regions:${region}`;
  const tries = [
    `https://api-v2.soundcloud.com/charts?kind=top&genre=soundcloud:genres:all-music${reg}&limit=50&client_id=${id}`,
    `https://api-v2.soundcloud.com/charts?kind=trending&genre=soundcloud:genres:all-music${reg}&limit=50&client_id=${id}`,
    `https://api-v2.soundcloud.com/charts?kind=top&genre=soundcloud:genres:all-music&limit=50&client_id=${id}`
  ];
  let out = [];
  for (const u of tries) {
    try { const j = await getJSON(u); out = (j.collection || []).map(x => x.track || x).filter(t => t && t.permalink_url).map(scMap); } catch {}
    if (out.length >= 10) break;
  }
  if (out.length < 10) {
    try {
      const j = await getJSON(`https://api-v2.soundcloud.com/mixed-selections?limit=10&client_id=${id}`);
      const found = [];
      (function walk(x) { if (Array.isArray(x)) return x.forEach(walk); if (x && typeof x === 'object') { if (x.kind === 'track' && x.permalink_url && x.title) found.push(x); else Object.values(x).forEach(walk); } })(j);
      out = found.sort((a, b) => (b.playback_count || 0) - (a.playback_count || 0)).map(scMap);
    } catch {}
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
  setTimeout(warmUp, 1500);
});
app.on('window-all-closed', () => { try { fs.writeFileSync(storeFile(), JSON.stringify(store)); } catch {} app.quit(); });
