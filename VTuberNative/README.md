# IceGirl VTuber App — Native (Cubism SDK for Native, tanpa WebView)

Versi rombak total dari app sebelumnya. Kali ini avatar dirender **langsung
lewat OpenGL ES via JNI/C++** memakai Cubism SDK for Native resmi dari
Live2D — **tidak ada WebView, tidak ada JavaScript, tidak ada CDN/npm sama
sekali.** Ini pondasi yang sama dipakai app VTuber Android production
sungguhan.

## Kenapa direstrukturisasi
Versi WebView sebelumnya rapuh: gagal load CDN, `fetch()` diblokir di
`file://`, error disamarkan browser, dll — semua akar masalahnya adalah
lapisan browser/JS itu sendiri. Versi ini menghapus lapisan itu sepenuhnya.

## Struktur proyek
Struktur folder ini SENGAJA dibuat mengikuti persis struktur asli SDK Cubism
(supaya semua path relatif di `CMakeLists.txt` & `build.gradle` bawaan Live2D
tetap benar, tidak perlu saya tebak-tebak ulang):

```
VTuberNative/
├── Core/                      ← library native resmi Live2D (Android only, sudah dipangkas)
├── Framework/                 ← source C++ Cubism Framework resmi
├── Samples/
│   ├── Common/                ← utilitas resmi (termasuk LAppWavFileHandler, dipakai utk lipsync)
│   ├── OpenGL/
│   │   ├── Shaders/, thirdParty/
│   │   ├── Resources/IceGirl/ ← MODEL KAMU ditaruh di sini
│   │   └── Demo/proj.android.cmake/Full/  ← INI PROYEK ANDROID STUDIO-NYA
│   │       ├── app/
│   │       │   ├── src/main/cpp/          ← C++ (LAppModel, LAppDelegate, JniBridgeC, dll)
│   │       │   └── src/main/java/com/live2d/demo/
│   │       │       ├── MainActivity.kt    ← LOGIKA KITA: mic, Gemini, TTS, API key, lipsync
│   │       │       ├── ApiKeyStore.kt
│   │       │       ├── JniBridgeJava.java ← jembatan ke native (sudah ditambah nativeStartLipSync)
│   │       │       └── GLRenderer.java
│   │       ├── build.gradle, gradlew, ...
├── .github/workflows/android-native-build.yml
└── README.md (file ini)
```

**Package Java/native tetap `com.live2d.demo`** (bukan `com.icegirl.vtuber`)
— sengaja tidak diganti karena nama JNI (`Java_com_live2d_demo_...`) sangat
sensitif terhadap kecocokan nama persis; App ID (`applicationId`) tetap
`com.icegirl.vtuber` untuk branding di Play Store/HP.

## Apa yang saya ubah dari SDK resmi
1. **`Resources/IceGirl/IceGirl.model3.json`** — grup `LipSync` dan
   `EyeBlink` yang tadinya kosong (`"Ids": []`) sekarang diisi
   `ParamMouthOpenY` / `ParamEyeLOpen` / `ParamEyeROpen`, supaya mekanisme
   auto-blink & lipsync bawaan SDK bisa mengenali parameter yang benar.
2. **`LAppModel.hpp/.cpp`** — ditambah `_wavFileHandler` (pakai utility
   resmi `LAppWavFileHandler_Common` yang sudah ada di SDK tapi belum
   disambungkan di contoh Android-nya) + method `StartLipSync()`, dipanggil
   tiap frame di `Update()` untuk mendorong nilai RMS audio ke parameter
   mulut.
3. **`LAppDelegate.hpp/.cpp`** — method `StartLipSync()` yang meneruskan ke
   model yang sedang aktif.
4. **`JniBridgeC.cpp` + `JniBridgeJava.java`** — fungsi native baru
   `nativeStartLipSync(String filePath)`, dipanggil dari Kotlin tiap kali
   ada audio TTS baru.
5. **`JniBridgeJava.LoadFile()`** — awalnya cuma bisa baca dari assets;
   ditambah supaya juga bisa baca file dari path absolut (dibutuhkan karena
   file wav hasil TTS ada di cache folder app, bukan di assets).
6. **`MainActivity.kt`** (baru, gantikan `MainActivity.java` bawaan) — semua
   siklus hidup `GLSurfaceView`/JNI asli dipertahankan persis, ditambah:
   tombol mic → `SpeechRecognizer` → Gemini API → `TextToSpeech.synthesizeToFile`
   → `MediaPlayer` muter audionya BARENGAN native lipsync baca file wav yang
   sama → dialog pengaturan API key (⚙, simpan/hapus, tersimpan lokal di HP).

## Cara build

### Lewat GitHub Actions
Push semua folder ini ke repo, workflow **"Build Android APK (Native Cubism
SDK)"** otomatis jalan. Beda dari versi WebView: kali ini butuh **NDK +
CMake**, jadi ada step tambahan `sdkmanager --install "ndk;..." "cmake;..."`
sebelum build — sudah saya siapkan di workflow-nya.

APK hasil build ada di tab **Actions → Artifacts** setelah selesai (build
native lebih lama dari versi WebView, wajar, karena compile C++).

### Lewat Android Studio
Buka folder `Samples/OpenGL/Demo/proj.android.cmake/Full/` (INI folder yang
dibuka, bukan root repo) sebagai proyek. Android Studio akan otomatis minta
download NDK 26.3.11579264 kalau belum ada.

## API Key Gemini
Sama seperti sebelumnya — isi lewat tombol ⚙ di app, tersimpan lokal.
Dapatkan gratis di https://aistudio.google.com/apikey

## PENTING — soal status build ini
Saya tidak punya toolchain NDK/CMake/Android di sandbox untuk benar-benar
mengompilasi C++ ini sebelum dikirim ke kamu (beda dengan script bash/JS
sebelumnya yang bisa saya validasi sendiri). Jadi ada kemungkinan realistis
ada error kompilasi C++ di percobaan build pertama. Kalau itu terjadi:
**kirim saya log errornya dari tab Actions** (bukan screenshot HP) — error
compile C++ biasanya sangat spesifik (nama file + nomor baris), jauh lebih
cepat saya perbaiki dibanding kemarin nebak-nebak error runtime di WebView.

## Lisensi
`Core/` dan `Framework/` tetap di bawah Live2D Open Software License asli
(lihat `Core/LICENSE.md`, `LICENSE.md`) — jangan didistribusikan ulang di
luar proyek pribadimu tanpa mengikuti ketentuan lisensi itu.
