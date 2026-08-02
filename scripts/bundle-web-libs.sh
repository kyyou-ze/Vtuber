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

echo "Menentukan & memvalidasi file browser yang benar..."
node -e "
const fs = require('fs');
const path = require('path');

function pkgDirOf(pkgName) {
  return path.join(process.cwd(), 'node_modules', pkgName);
}

// PENTING: untuk pixi.js v6.3+, path yang benar untuk <script> tag biasa
// (bukan ESM/CJS) adalah 'dist/browser/pixi.min.js'. Field 'jsdelivr'/'unpkg'
// di package.json TIDAK selalu bisa dipercaya (kadang menunjuk ke varian
// CJS/ESM yang tidak mendefinisikan variabel global PIXI kalau dimuat lewat
// <script> biasa) -- makanya path pasti dicoba DULUAN, baru field
// package.json dipakai sebagai cadangan terakhir.
function resolveEntry(pkgName, knownGoodPaths, pkgJsonFieldsAsLastResort) {
  const pkgDir = pkgDirOf(pkgName);
  const pkgJson = JSON.parse(fs.readFileSync(path.join(pkgDir, 'package.json'), 'utf8'));
  const declared = pkgJsonFieldsAsLastResort
    ? (pkgJson.jsdelivr || pkgJson.unpkg || pkgJson.main)
    : null;
  const candidates = [...knownGoodPaths, declared].filter(Boolean);

  for (const rel of candidates) {
    const full = path.join(pkgDir, rel);
    if (fs.existsSync(full)) return full;
  }
  throw new Error('Tidak ketemu file browser untuk ' + pkgName + '. Dicoba: ' + candidates.join(', '));
}

// Validasi sederhana: file harus cukup besar & benar-benar mendefinisikan
// class Application secara global, biar kalau salah file, GAGAL SEKARANG
// (saat build) -- bukan nanti pas app sudah di-install di HP.
function assertLooksLikePixiBundle(filePath) {
  const stat = fs.statSync(filePath);
  if (stat.size < 100 * 1024) {
    throw new Error(filePath + ' kekecilan (' + stat.size + ' bytes) buat jadi pixi.js browser bundle yang valid.');
  }
  const content = fs.readFileSync(filePath, 'utf8');
  if (!content.includes('Application')) {
    throw new Error(filePath + ' sepertinya bukan pixi.js browser bundle yang benar (tidak ada class Application).');
  }
}

const destDir = '$DEST_DIR';
fs.mkdirSync(destDir, { recursive: true });

const pixiSrc = resolveEntry(
  'pixi.js',
  ['dist/browser/pixi.min.js', 'dist/browser/pixi.js', 'dist/pixi.min.js'],
  true
);
const pixiDest = path.join(destDir, 'pixi.min.js');
fs.copyFileSync(pixiSrc, pixiDest);
assertLooksLikePixiBundle(pixiDest);
console.log('pixi.js  <-', pixiSrc, '(' + fs.statSync(pixiDest).size + ' bytes, tervalidasi)');

const cubism4Src = path.join(pkgDirOf('pixi-live2d-display'), 'dist', 'cubism4.min.js');
fs.copyFileSync(cubism4Src, path.join(destDir, 'cubism4.min.js'));
console.log('cubism4  <-', cubism4Src);
"

rm -rf "$WORK_DIR"
echo "Selesai. File disalin ke $DEST_DIR"
