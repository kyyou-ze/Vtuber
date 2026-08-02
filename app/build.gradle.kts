plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Catatan: API key Gemini TIDAK lagi disetel saat build. User memasukkan
// key-nya sendiri di dalam app (tombol pengaturan), disimpan di
// SharedPreferences lokal di HP. Lihat ApiKeyStore.kt.

android {
    namespace = "com.icegirl.vtuber"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.icegirl.vtuber"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Dipakai supaya WebView bisa serve file lokal lewat https://appassets.androidplatform.net/
    // (fetch() di JS tidak diizinkan Chromium mengakses file:// langsung).
    implementation("androidx.webkit:webkit:1.12.1")
}
