const { contextBridge, ipcRenderer, webFrame } = require('electron');

contextBridge.exposeInMainWorld('U', {
  storeGet: () => ipcRenderer.invoke('store-get'),
  storeSet: d => ipcRenderer.invoke('store-set', d),
  pickFolder: () => ipcRenderer.invoke('pick-folder'),
  defaultFolders: () => ipcRenderer.invoke('default-folders'),
  scan: (folders, opts) => ipcRenderer.invoke('scan', folders, opts),
  onScanProgress: cb => ipcRenderer.on('scan-progress', (_, p) => cb(p)),
  search: q => ipcRenderer.invoke('search', q),
  scStream: id => ipcRenderer.invoke('sc-stream', id),
  trending: (g, t) => ipcRenderer.invoke('trending', g, t),
  audiusSearch: q => ipcRenderer.invoke('audius-search', q),
  lyrics: (a, t, d, s) => ipcRenderer.invoke('lyrics', a, t, d, s),
  info: (t, a) => ipcRenderer.invoke('info', t, a),
  download: (u, n) => ipcRenderer.invoke('download', u, n),
  openPath: p => ipcRenderer.invoke('open-path', p),
  openUrl: u => ipcRenderer.invoke('open-url', u),
  titleColor: c => ipcRenderer.invoke('title-color', c),
  scRate: (r, k) => ipcRenderer.invoke('sc-rate', r, k),
  setAdblock: on => ipcRenderer.invoke('adblock', on),
  ytLogin: () => ipcRenderer.invoke('yt-login'),
  ytLogged: () => ipcRenderer.invoke('yt-logged'),
  ytLogout: () => ipcRenderer.invoke('yt-logout'),
  frameEq: code => ipcRenderer.invoke('frame-eq', code),
  // libera memória (cache de imagens + coleta de lixo) quando o app vai pro fundo
  trim: () => { try { webFrame.clearCache(); } catch {} try { if (global.gc) global.gc(); } catch {} }
});
