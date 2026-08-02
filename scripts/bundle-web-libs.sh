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

// Tidak pakai require.resolve(pkg + '/package.json') karena paket seperti
// pixi.js versi baru membatasi akses subpath itu lewat field 'exports'.
// Cukup baca langsung dari node_modules/<pkg>/package.json.
function pkgDirOf(pkgName) {
  return path.join(process.cwd(), 'node_modules', pkgName);
}

function resolveEntry(pkgName, fallbacks) {
  const pkgDir = pkgDirOf(pkgName);
  const pkgJsonPath = path.join(pkgDir, 'package.json');
  const pkgJson = JSON.parse(fs.readFileSync(pkgJsonPath, 'utf8'));
  const declared = pkgJson.jsdelivr || pkgJson.unpkg || pkgJson.main;

  const candidates = [declared, ...fallbacks].filter(Boolean);
  for (const rel of candidates) {
    const full = path.join(pkgDir, rel);
    if (fs.existsSync(full)) return full;
  }
  throw new Error('Tidak ketemu file browser untuk ' + pkgName + '. Dicoba: ' + candidates.join(', '));
}

const destDir = '$DEST_DIR';
fs.mkdirSync(destDir, { recursive: true });

const pixiSrc = resolveEntry('pixi.js', ['dist/browser/pixi.min.js', 'dist/pixi.min.js']);
fs.copyFileSync(pixiSrc, path.join(destDir, 'pixi.min.js'));
console.log('pixi.js  <-', pixiSrc);

const cubism4Src = path.join(pkgDirOf('pixi-live2d-display'), 'dist', 'cubism4.min.js');
fs.copyFileSync(cubism4Src, path.join(destDir, 'cubism4.min.js'));
console.log('cubism4  <-', cubism4Src);
"

rm -rf "$WORK_DIR"
echo "Selesai. File disalin ke $DEST_DIR"
