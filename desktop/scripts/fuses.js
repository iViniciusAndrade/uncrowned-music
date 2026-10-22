// Trava o Electron do app depois de empacotar: não roda como Node, não aceita
// opções/inspeção pela linha de comando e só carrega o código de dentro do app.
const path = require('path');
const { flipFuses, FuseVersion, FuseV1Options } = require('@electron/fuses');
exports.default = async ctx => {
  const exe = path.join(ctx.appOutDir, `${ctx.packager.appInfo.productFilename}.exe`);
  await flipFuses(exe, {
    version: FuseVersion.V1,
    [FuseV1Options.RunAsNode]: false,
    [FuseV1Options.EnableNodeOptionsEnvironmentVariable]: false,
    [FuseV1Options.EnableNodeCliInspectArguments]: false,
    [FuseV1Options.OnlyLoadAppFromAsar]: true
  });
};
