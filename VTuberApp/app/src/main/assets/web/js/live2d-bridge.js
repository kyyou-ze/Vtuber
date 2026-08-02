// live2d-bridge.js
// Merender model IceGirl dan menyediakan fungsi yang dipanggil dari Kotlin (Android)
// lewat WebView.evaluateJavascript(), untuk sinkronisasi mulut & ekspresi.

(function () {
  try {
    if (typeof PIXI === "undefined") {
      throw new Error("PIXI tidak terdefinisi (skrip pixi.js gagal dimuat, cek internet)");
    }
    if (typeof PIXI.live2d === "undefined" || typeof Live2DCubismCore === "undefined") {
      throw new Error("Live2D Cubism Core / pixi-live2d-display tidak terdefinisi");
    }
    runBridge();
  } catch (err) {
    if (window.Android && window.Android.onJsEvent) {
      window.Android.onJsEvent("onJsError", JSON.stringify({ message: String(err && err.message || err) }));
    }
  }

  function runBridge() {
  const Live2DModel = PIXI.live2d.Live2DModel;

  const app = new PIXI.Application({
    view: document.getElementById("canvas"),
    autoStart: true,
    resizeTo: window,
    backgroundAlpha: 0,
  });

  let model = null;

  // state untuk animasi bicara (lipsync sederhana berbasis amplitudo semu)
  let talking = false;
  let talkPhase = 0;

  // Saat true, mulut dikendalikan langsung dari Kotlin lewat setMouthOpen()
  // berdasarkan amplitudo audio TTS asli (lipsync akurat). Saat false,
  // dipakai animasi sinus buatan sebagai fallback (kalau Visualizer tidak
  // tersedia di device).
  let externalLipSync = false;

  // state untuk kedip otomatis
  let nextBlinkAt = performance.now() + randomBetween(2000, 5000);
  let blinking = false;
  let blinkStart = 0;
  const BLINK_DURATION = 160; // ms

  function randomBetween(min, max) {
    return min + Math.random() * (max - min);
  }

  async function loadModel() {
    try {
      model = await Live2DModel.from("model/IceGirl.model3.json", { autoInteract: false });
      app.stage.addChild(model);
      fitModel();
      window.addEventListener("resize", fitModel);
      app.ticker.add(onTick);
      notifyAndroid("onModelReady", {});
    } catch (err) {
      notifyAndroid("onModelError", { message: String(err) });
    }
  }

  function fitModel() {
    if (!model) return;
    const scaleX = app.renderer.width / model.internalModel.originalWidth;
    const scaleY = app.renderer.height / model.internalModel.originalHeight;
    const scale = Math.min(scaleX, scaleY) * 1.05;
    model.scale.set(scale);
    model.x = app.renderer.width / 2;
    model.y = app.renderer.height / 2 + app.renderer.height * 0.08;
    model.anchor.set(0.5, 0.5);
  }

  function setParam(id, value) {
    if (!model) return;
    try {
      model.internalModel.coreModel.setParameterValueById(id, value);
    } catch (e) {
      // parameter mungkin tidak ada di model ini, abaikan
    }
  }

  function onTick(deltaFrame) {
    if (!model) return;
    const now = performance.now();

    // --- napas halus (breathing) ---
    const breathe = (Math.sin(now / 1400) + 1) / 2;
    setParam("ParamBreath", breathe);

    // --- kedip mata otomatis ---
    if (!blinking && now >= nextBlinkAt) {
      blinking = true;
      blinkStart = now;
    }
    if (blinking) {
      const t = (now - blinkStart) / BLINK_DURATION;
      let eyeOpen;
      if (t < 0.5) {
        eyeOpen = 1 - Math.min(t / 0.5, 1);
      } else if (t < 1) {
        eyeOpen = (t - 0.5) / 0.5;
      } else {
        eyeOpen = 1;
        blinking = false;
        nextBlinkAt = now + randomBetween(2000, 6000);
      }
      setParam("ParamEyeLOpen", eyeOpen);
      setParam("ParamEyeROpen", eyeOpen);
    }

    // --- gerak mulut saat bicara ---
    if (externalLipSync) {
      // Nilai mulut sepenuhnya diatur dari Kotlin lewat setMouthOpen(),
      // jadi di sini tidak melakukan apa-apa (dibiarkan seperti apa
      // adanya dari panggilan terakhir setMouthOpen).
    } else if (talking) {
      // Fallback: animasi buka-tutup mulut buatan (bukan dari audio asli)
      talkPhase += 0.35 * (deltaFrame || 1);
      const mouth = Math.max(0, Math.sin(talkPhase)) * 0.85 + Math.random() * 0.1;
      setParam("ParamMouthOpenY", mouth);
    } else {
      setParam("ParamMouthOpenY", 0);
    }
  }

  function notifyAndroid(event, data) {
    if (window.Android && window.Android.onJsEvent) {
      window.Android.onJsEvent(event, JSON.stringify(data || {}));
    }
  }

  // ===== API yang dipanggil dari Kotlin =====

  // Dipanggil saat Android mulai bicara TANPA data amplitudo asli
  // (fallback, dipakai kalau Visualizer gagal / tidak tersedia).
  window.startTalking = function () {
    talking = true;
  };

  // Dipanggil saat selesai bicara (fallback mode).
  window.stopTalking = function () {
    talking = false;
    talkPhase = 0;
    setParam("ParamMouthOpenY", 0);
  };

  // Nyalakan/matikan mode lipsync akurat (dikendalikan Kotlin lewat setMouthOpen).
  window.setExternalLipSyncMode = function (enabled) {
    externalLipSync = !!enabled;
    if (!externalLipSync) setParam("ParamMouthOpenY", 0);
  };

  // Set nilai buka-mulut (0.0 - 1.0) langsung, dipanggil berkali-kali per
  // detik dari Kotlin berdasarkan amplitudo audio TTS asli (lipsync akurat).
  window.setMouthOpen = function (value) {
    setParam("ParamMouthOpenY", Math.max(0, Math.min(1, value)));
  };

  // Ganti ekspresi wajah, name = nama file .exp3.json tanpa ekstensi (jika ada)
  window.setExpression = function (name) {
    if (model && model.expression) {
      model.expression(name);
    }
  };

  loadModel();
  } // end runBridge
})();
