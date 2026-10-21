// Roda dentro do player do YouTube antes da página carregar.
// Faz o mesmo que o uBlock Origin: apaga a lista de anúncios da resposta do YouTube,
// então o player nem fica sabendo que tinha propaganda pra mostrar.
const { webFrame } = require('electron');

const code = `(() => {
  if (window.__uncNoAds) return; window.__uncNoAds = true;
  const KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams', 'adBreakParams'];
  const strip = o => {
    try {
      if (!o || typeof o !== 'object') return o;
      for (const k of KEYS) if (k in o) delete o[k];
      if (o.playerResponse) strip(o.playerResponse);
      if (Array.isArray(o)) o.forEach(strip);
    } catch (e) {}
    return o;
  };
  const hasAds = o => o && typeof o === 'object' && (KEYS.some(k => k in o) || (o.playerResponse && KEYS.some(k => k in o.playerResponse)));

  // resposta que já vem embutida na página
  let ipr;
  try { Object.defineProperty(window, 'ytInitialPlayerResponse', { configurable: true, get: () => ipr, set: v => { ipr = strip(v); } }); } catch (e) {}

  // respostas lidas como texto
  const parse = JSON.parse;
  JSON.parse = function () { const r = parse.apply(this, arguments); return hasAds(r) ? strip(r) : r; };

  // respostas pedidas quando troca de vídeo
  const F = window.fetch;
  window.fetch = async function (input) {
    const res = await F.apply(this, arguments);
    try {
      const u = typeof input === 'string' ? input : (input && input.url) || '';
      if (/\\/youtubei\\/v1\\/(player|next|reel)/.test(u)) {
        const j = await res.clone().json();
        if (hasAds(j)) return new Response(JSON.stringify(strip(j)), { status: res.status, statusText: res.statusText, headers: res.headers });
      }
    } catch (e) {}
    return res;
  };
})();`;

try { webFrame.executeJavaScript(code); } catch (e) {}
