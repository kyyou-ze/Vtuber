# IceGirl VTuber App (Android)

App Android yang menampilkan avatar Live2D "IceGirl", bisa diajak ngobrol
lewat suara (Speech-to-Text bawaan Android), dijawab oleh **Gemini API**
(Google AI Studio), lalu jawabannya diucapkan lewat Text-to-Speech sambil
mulut avatar bergerak **mengikuti amplitudo suara TTS asli** (lipsync akurat)
dan mata berkedip otomatis.

## Arsitektur singkat
- **Kotlin native app** (bukan Flutter) berisi `WebView` yang merender
  avatar pakai [pixi-live2d-display](https://github.com/guansss/pixi-live2d-display)
  (open source, dimuat lewat CDN) + Cubism 4 Core resmi dari Live2D
  (sudah disertakan di `assets/web/js/live2dcubismcore.min.js`).
- Tombol mic → `SpeechRecognizer` Android → teks dikirim ke Gemini API →
  balasan disintesis ke file audio (`TextToSpeech.synthesizeToFile`) →
  file diputar lewat `MediaPlayer` sambil `Visualizer` membaca waveform
  audionya real-time → nilai amplitudo itu dikirim ke JS
  (`setMouthOpen(value)`) untuk menggerakkan `ParamMouthOpenY` avatar
  mengikuti suara asli, bukan animasi buka-tutup buatan.
  Kalau `Visualizer` gagal dipakai di suatu device, app otomatis jatuh
  ke animasi mulut fallback (sinus + noise) supaya tetap ada gerakan.
- Mata berkedip otomatis & napas halus dijalankan di JS (`live2d-bridge.js`),
  independen dari lipsync.
- File avatar ada di `app/src/main/assets/web/model/`.

## API Key Gemini — diisi LANGSUNG DI DALAM APP
Tidak perlu diset saat build lagi. Buka app → tap ikon ⚙ di pojok kanan
atas → tempel API key → **Simpan**. Key disimpan di `SharedPreferences`
lokal di HP (tidak pernah ikut ter-commit ke repo, tidak dikirim ke server
mana pun selain langsung ke endpoint Gemini). Ada juga tombol **Hapus key**
di dialog yang sama kalau mau menghapusnya.

Dapatkan API key gratis di: https://aistudio.google.com/apikey

---

## Build lewat GitHub Actions

1. Buat repo baru di GitHub, push semua isi folder ini ke repo tsb.
   ```bash
   cd VTuberApp
   git init
   git add .
   git commit -m "Init IceGirl VTuber app"
   git branch -M main
   git remote add origin https://github.com/USERNAME/NAMA_REPO.git
   git push -u origin main
   ```
2. Buka tab **Actions** di repo, workflow "Build Android APK" otomatis
   jalan tiap push ke `main` (atau klik "Run workflow" manual). Tidak perlu
   setting Secret apa pun lagi.
3. Setelah selesai (~3-5 menit), buka hasil run → bagian **Artifacts** →
   unduh `icegirl-vtuber-debug-apk` → install di HP Android (aktifkan
   "Install dari sumber tidak dikenal" saat pertama kali).
4. Buka app, tap ⚙, masukkan API key Gemini kamu.

## Build lokal pakai Android Studio
1. Buka folder ini di Android Studio (akan otomatis generate Gradle wrapper).
2. Run ke HP / emulator seperti biasa (tombol ▶ Run).
3. Di app, tap ⚙, masukkan API key.

---

## Model yang dipakai
Model `IceGirl` (Cubism 4, file `.moc3`) sudah otomatis punya parameter
standar sehingga langsung kompatibel dengan animasi di app ini:
- `ParamMouthOpenY` → digerakkan real-time oleh amplitudo audio TTS (lipsync)
- `ParamEyeLOpen` / `ParamEyeROpen` → kedip otomatis
- `ParamBreath` → napas halus (idle)

20 file ekspresi asli (`.exp3.json`) ikut disalin dengan nama aman
`exp_01.exp3.json` s/d `exp_20.exp3.json` — lihat
`app/src/main/assets/web/model/expressions_index.json` untuk tahu nama
ekspresi asli tiap file, lalu panggil dari JS dengan
`window.setExpression("exp_01")` kalau mau dipakai (belum ditrigger
otomatis dari mana pun).

## Cara kerja lipsync akurat (detail teknis)
1. `TextToSpeech.synthesizeToFile()` merender jawaban AI jadi file `.wav`
   di cache app (bukan langsung diucapkan).
2. Setelah file selesai (`onDone`), file diputar dengan `MediaPlayer`.
3. `android.media.audiofx.Visualizer` dipasang ke `audioSessionId` player
   itu, membaca waveform ~20x/detik selama audio diputar.
4. Tiap capture, dihitung RMS (root-mean-square) dari waveform sebagai
   estimasi volume sesaat, dinormalisasi ke 0.0–1.0, lalu dihaluskan
   (exponential moving average) supaya mulut tidak "gemetar".
5. Nilai itu dikirim ke WebView lewat `evaluateJavascript("setMouthOpen(x)")`,
   yang langsung men-set parameter `ParamMouthOpenY` di model Live2D.

Ini bukan lipsync per-fonem (tidak membedakan bentuk mulut "a/i/u/e/o"),
tapi mulut membuka-menutup mengikuti keras-pelannya suara asli secara
real-time — jauh lebih natural dibanding animasi acak.

## Ide pengembangan lanjutan
- Lipsync per-fonem (viseme) kalau mau lebih presisi lagi — perlu analisis
  frekuensi (FFT, sudah tersedia lewat `onFftDataCapture` yang belum
  dipakai) untuk membedakan bentuk mulut.
- Trigger ekspresi otomatis berdasar isi jawaban AI (mis. kata "senang" → ekspresi senyum).
- Ganti Gemini text-only jadi mode suara-ke-suara (Gemini Live API) biar
  latensinya lebih rendah dan suaranya lebih natural (bukan TTS Android).
- Tambah animasi kepala mengikuti sentuhan/geser layar (head tracking).
