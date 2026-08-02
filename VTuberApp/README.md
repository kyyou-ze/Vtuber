# IceGirl VTuber App (Android)

App Android yang menampilkan avatar Live2D "IceGirl", bisa diajak ngobrol
lewat suara (Speech-to-Text bawaan Android), dijawab oleh **Gemini API**
(Google AI Studio), lalu jawabannya diucapkan lewat Text-to-Speech sambil
mulut avatar bergerak **mengikuti amplitudo suara TTS asli** (lipsync akurat)
dan mata berkedip otomatis.

## Arsitektur singkat
- **Kotlin native app** (bukan Flutter) berisi `WebView` yang merender
  avatar pakai [pixi-live2d-display](https://github.com/guansss/pixi-live2d-display)
  + Cubism 4 Core resmi dari Live2D.
- **Semua library web (pixi.js, pixi-live2d-display, Cubism Core) dibundel
  LOKAL ke dalam APK saat build** — TIDAK diambil dari CDN saat app jalan
  di HP. Ini penting: percobaan awal pakai CDN ternyata rapuh (gagal di
  jaringan yang kurang stabil, kena pembatasan CORS, dll). Sekarang app
  100% jalan offline untuk urusan avatar.
- WebView menyajikan file lokal lewat domain virtual
  `https://appassets.androidplatform.net/` (pakai `androidx.webkit`),
  bukan `file://` — karena `fetch()` di JavaScript (dipakai library
  Live2D untuk ambil file model) diblokir Chromium kalau lewat `file://`.
- Tombol mic → `SpeechRecognizer` Android → teks dikirim ke Gemini API →
  balasan disintesis ke file audio (`TextToSpeech.synthesizeToFile`) →
  file diputar lewat `MediaPlayer` sambil `Visualizer` membaca waveform
  audionya real-time → nilai amplitudo itu dikirim ke JS untuk
  menggerakkan mulut avatar mengikuti suara asli (lipsync akurat).
- Mata berkedip otomatis & napas halus dijalankan di JS, independen dari lipsync.

## API Key Gemini — diisi LANGSUNG DI DALAM APP
Tidak perlu diset saat build. Buka app → tap ikon ⚙ di pojok kanan atas →
tempel API key → **Simpan**. Ada juga tombol **Hapus key**. Disimpan di
`SharedPreferences` lokal di HP.

Dapatkan API key gratis di: https://aistudio.google.com/apikey

---

## Build lewat GitHub Actions

1. Push semua isi folder ini ke repo GitHub kamu.
2. Buka tab **Actions**, workflow "Build Android APK" otomatis jalan tiap
   push ke `main` (atau klik "Run workflow" manual). Workflow ini:
   - Mengambil `pixi.js` & `pixi-live2d-display` lewat `npm` (bukan CDN
     saat runtime) lewat `scripts/bundle-web-libs.sh`.
   - Build APK dengan Gradle.
3. Setelah selesai, buka hasil run → **Artifacts** → unduh
   `icegirl-vtuber-debug-apk` → install di HP.
4. Buka app, tap ⚙, masukkan API key Gemini kamu.

## Build lokal pakai Android Studio
Karena library web sekarang dibundel dari npm (bukan CDN), sebelum build
di Android Studio, jalankan dulu sekali (butuh Node.js terpasang):
```bash
bash scripts/bundle-web-libs.sh
```
Ini menyalin `pixi.min.js` dan `cubism4.min.js` ke
`app/src/main/assets/web/js/`. Setelah itu build seperti biasa lewat
Android Studio (tombol ▶ Run).

---

## Model yang dipakai
Model `IceGirl` (Cubism 4, file `.moc3`) punya parameter standar:
- `ParamMouthOpenY` → digerakkan real-time oleh amplitudo audio TTS (lipsync)
- `ParamEyeLOpen` / `ParamEyeROpen` → kedip otomatis
- `ParamBreath` → napas halus (idle)

20 file ekspresi asli (`.exp3.json`) disalin dengan nama aman
`exp_01.exp3.json` s/d `exp_20.exp3.json` — lihat
`app/src/main/assets/web/model/expressions_index.json` untuk nama asli
tiap file. Panggil dari JS dengan `window.setExpression("exp_01")` kalau
mau dipakai (belum ditrigger otomatis dari mana pun).

## Riwayat perbaikan (untuk konteks kalau ada bug baru)
1. Layar avatar putih kosong → WebView background di-set transparan.
2. `PIXI.Application is not a constructor` → CDN pixi.js gagal load sempurna.
3. `Network error` saat load model → `fetch()` diblokir di `file://`,
   pindah ke `WebViewAssetLoader` (domain virtual https).
4. `Script error.` generik → cross-origin script error disamarkan browser,
   ditambah `crossorigin="anonymous"`.
5. **Solusi permanen**: semua library web dibundel lokal lewat npm saat
   build, jadi tidak ada lagi ketergantungan CDN/internet saat runtime
   untuk urusan avatar sama sekali.

## Ide pengembangan lanjutan
- Lipsync per-fonem (viseme) — perlu analisis frekuensi (FFT).
- Trigger ekspresi otomatis berdasar isi jawaban AI.
- Mode suara-ke-suara pakai Gemini Live API (latensi lebih rendah).
- Animasi kepala mengikuti sentuhan/geser layar.
