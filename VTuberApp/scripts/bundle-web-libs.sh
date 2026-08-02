#!/usr/bin/env bash
# Mengambil pixi.js & pixi-live2d-display lewat npm (bukan menebak link CDN),
# lalu menyalin file browser-nya ke assets Android supaya app TIDAK butuh
# internet/CDN sama sekali saat runtime untuk menampilkan avatar.
#
# Butuh Node.js + npm terpasang. Jalankan dari root repo:
#   bash scripts/bundle-web-libs.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST_DIR="$REPO_ROOT/app/src/main/assets/web/js"
WORK_DIR="$(mktemp -d)"

echo "Mengunduh pixi.js@6.5.9 dan pixi-live2d-display@0.4.0 lewat npm..."
cd "$WORK_DIR"
npm init -y >/dev/null 2>&1
npm install --no-save --silent pixi.js@6.5.9 pixi-live2d-display@0.4.0

echo "Menentukan file browser yang benar (lewat metadata package.json, bukan tebakan)..."
node -e "
const fs = require('fs');
const path = require('path');

function resolveEntry(pkgName) {
  const pkgJsonPath = require.resolve(pkgName + '/package.json', { paths: [process.cwd()] });
  const pkgDir = path.dirname(pkgJsonPath);
  const pkgJson = JSON.parse(fs.readFileSync(pkgJsonPath, 'utf8'));
  const entry = pkgJson.jsdelivr || pkgJson.unpkg || pkgJson.main;
  if (!entry) throw new Error('Tidak ketemu entry browser untuk ' + pkgName);
  return path.join(pkgDir, entry);
}

const destDir = '$DEST_DIR';
fs.mkdirSync(destDir, { recursive: true });

const pixiSrc = resolveEntry('pixi.js');
fs.copyFileSync(pixiSrc, path.join(destDir, 'pixi.min.js'));
console.log('pixi.js  <-', pixiSrc);

const live2dDir = path.dirname(require.resolve('pixi-live2d-display/package.json', { paths: [process.cwd()] }));
const cubism4Src = path.join(live2dDir, 'dist', 'cubism4.min.js');
fs.copyFileSync(cubism4Src, path.join(destDir, 'cubism4.min.js'));
console.log('cubism4  <-', cubism4Src);
"

rm -rf "$WORK_DIR"
echo "Selesai. File disalin ke $DEST_DIR"
