package com.icegirl.vtuber

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.icegirl.vtuber.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private var tts: TextToSpeech? = null

    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var smoothedMouth = 0.0
    private var pendingTtsFile: File? = null

    // Ganti nama model kalau Google merilis versi baru di AI Studio (ai.google.dev)
    private val geminiModel = "gemini-2.5-flash"

    private val systemPersona = """
        Kamu adalah IceGirl, seorang VTuber perempuan yang ceria, ramah, sedikit jahil,
        dan berbicara dalam Bahasa Indonesia yang santai. Jawab dengan singkat (1-3 kalimat)
        seperti sedang ngobrol langsung dengan penonton, jangan gunakan format markdown.
    """.trimIndent()

    private val requestMicPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupTts()
        setupSpeechRecognizer()

        binding.btnSettings.setOnClickListener { showApiKeyDialog() }

        binding.btnMic.setOnClickListener {
            if (!ApiKeyStore.hasKey(this)) {
                Toast.makeText(this, "Masukkan API key Gemini dulu (tombol gear ⚙)", Toast.LENGTH_LONG).show()
                showApiKeyDialog()
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startListening()
            } else {
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        if (!ApiKeyStore.hasKey(this)) {
            binding.txtSubtitle.text = "Halo! Masukkan API key Gemini dulu lewat tombol ⚙ di pojok kanan atas."
        }
    }

    // ===================== Pengaturan API Key =====================

    private fun showApiKeyDialog() {
        val input = EditText(this).apply {
            hint = "Tempel API key Gemini di sini"
            setText(ApiKeyStore.get(this@MainActivity))
            setSingleLine()
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)

        AlertDialog.Builder(this)
            .setTitle("API Key Gemini (AI Studio)")
            .setMessage("Dapatkan gratis di aistudio.google.com/apikey. Key disimpan di HP ini saja.")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotBlank()) {
                    ApiKeyStore.save(this, key)
                    Toast.makeText(this, "API key disimpan.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Hapus key") { _, _ ->
                ApiKeyStore.clear(this)
                Toast.makeText(this, "API key dihapus.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ===================== WebView / Live2D =====================

    private fun setupWebView() {
        binding.webViewLive2D.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.webViewLive2D.settings.javaScriptEnabled = true
        binding.webViewLive2D.settings.domStorageEnabled = true
        binding.webViewLive2D.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        binding.webViewLive2D.settings.allowFileAccess = true
        binding.webViewLive2D.settings.mixedContentMode =
            android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        binding.webViewLive2D.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    runOnUiThread {
                        binding.txtSubtitle.text = "Gagal load halaman avatar: ${error?.description}"
                    }
                }
            }

            override fun onReceivedHttpError(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                android.util.Log.w(
                    "IceGirlWebView",
                    "HTTP error ${errorResponse?.statusCode} loading ${request?.url}"
                )
            }
        }
        binding.webViewLive2D.addJavascriptInterface(JsBridge(), "Android")
        binding.webViewLive2D.loadUrl("file:///android_asset/web/index.html")
    }

    // ===================== TTS setup =====================

    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ind", "IDN")
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                // Sintesis ke file selesai -> putar file itu sambil analisa amplitudo untuk lipsync.
                val file = pendingTtsFile
                pendingTtsFile = null
                if (file != null) {
                    runOnUiThread { playWithLipSync(file) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread { binding.webViewLive2D.evaluateJavascript("stopTalking()", null) }
            }
        })
    }

    /** Sintesis teks ke file WAV dulu (bukan langsung speak), supaya audionya bisa
     *  dianalisa dengan Visualizer untuk lipsync yang mengikuti suara asli. */
    private fun speak(text: String) {
        val ttsEngine = tts ?: return
        val utteranceId = "icegirl_${System.currentTimeMillis()}"
        val outFile = File(cacheDir, "$utteranceId.wav")
        pendingTtsFile = outFile
        val result = ttsEngine.synthesizeToFile(text, null, outFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            pendingTtsFile = null
            // fallback: bicara langsung tanpa lipsync presisi
            binding.webViewLive2D.evaluateJavascript("setExternalLipSyncMode(false)", null)
            binding.webViewLive2D.evaluateJavascript("startTalking()", null)
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fallback_$utteranceId")
        }
    }

    /** Putar file TTS sambil membaca amplitudo audionya lewat Visualizer,
     *  lalu kirim nilai buka-mulut real-time ke avatar (lipsync akurat). */
    private fun playWithLipSync(file: File) {
        releasePlaybackResources()

        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener {
                attachVisualizer(player.audioSessionId)
                binding.webViewLive2D.evaluateJavascript("setExternalLipSyncMode(true)", null)
                player.start()
            }
            player.setOnCompletionListener {
                onPlaybackFinished(file)
            }
            player.setOnErrorListener { _, _, _ ->
                onPlaybackFinished(file)
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            onPlaybackFinished(file)
        }
    }

    private fun onPlaybackFinished(file: File) {
        binding.webViewLive2D.evaluateJavascript("setExternalLipSyncMode(false)", null)
        binding.webViewLive2D.evaluateJavascript("stopTalking()", null)
        releasePlaybackResources()
        file.delete()
    }

    private fun attachVisualizer(audioSessionId: Int) {
        try {
            val viz = Visualizer(audioSessionId)
            val captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            viz.captureSize = captureSize
            val rate = Visualizer.getMaxCaptureRate().coerceAtMost(20000)
            viz.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (waveform == null || waveform.isEmpty()) return
                    val mouthValue = amplitudeToMouthValue(waveform)
                    binding.webViewLive2D.evaluateJavascript("setMouthOpen($mouthValue)", null)
                }

                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
            }, rate, true, false)
            viz.enabled = true
            visualizer = viz
        } catch (e: Exception) {
            // Visualizer tidak tersedia di device ini -> pakai animasi fallback saja
            binding.webViewLive2D.evaluateJavascript("setExternalLipSyncMode(false)", null)
            binding.webViewLive2D.evaluateJavascript("startTalking()", null)
        }
    }

    /** Ubah data waveform (byte tak bertanda, 128 = diam) jadi nilai buka-mulut 0.0-1.0,
     *  dengan sedikit smoothing supaya gerakannya tidak gemetar. */
    private fun amplitudeToMouthValue(waveform: ByteArray): Double {
        var sumSquares = 0.0
        for (b in waveform) {
            val v = (b.toInt() and 0xFF) - 128
            sumSquares += (v * v).toDouble()
        }
        val rms = sqrt(sumSquares / waveform.size)
        val normalized = (rms / 28.0).coerceIn(0.0, 1.0)
        // exponential moving average biar transisi mulut halus, bukan patah-patah
        smoothedMouth = smoothedMouth * 0.45 + normalized * 0.55
        return smoothedMouth
    }

    private fun releasePlaybackResources() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {}
        visualizer = null

        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        smoothedMouth = 0.0
    }

    // ===================== Speech recognition =====================

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    binding.txtSubtitle.text = "Kamu: $text"
                    askGemini(text)
                }
            }

            override fun onError(error: Int) {
                binding.txtSubtitle.text = "Maaf, tidak dengar. Coba lagi ya."
            }

            override fun onReadyForSpeech(params: Bundle?) {
                binding.txtSubtitle.text = "Mendengarkan..."
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer.startListening(intent)
    }

    // ===================== Gemini API =====================

    private fun askGemini(userText: String) {
        val apiKey = ApiKeyStore.get(this)
        if (apiKey.isBlank()) {
            binding.txtSubtitle.text = "API key belum diisi. Tap tombol ⚙ dulu."
            showApiKeyDialog()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            binding.txtSubtitle.text = "IceGirl sedang mikir..."
            val reply = withContext(Dispatchers.IO) {
                callGeminiApi(apiKey, userText)
            }
            binding.txtSubtitle.text = "IceGirl: $reply"
            speak(reply)
        }
    }

    private fun callGeminiApi(apiKey: String, userText: String): String {
        return try {
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent?key=$apiKey"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPersona)))
                })
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userText)))
                }))
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream.bufferedReader().use { it.readText() }

            if (responseCode !in 200..299) {
                if (responseCode == 400 || responseCode == 403) {
                    runOnUiThread {
                        Toast.makeText(this, "API key ditolak server. Cek lagi lewat tombol ⚙.", Toast.LENGTH_LONG).show()
                    }
                }
                return "Waduh, ada error dari server (kode $responseCode)."
            }

            val json = JSONObject(responseText)
            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } catch (e: Exception) {
            "Maaf, koneksi ke server bermasalah: ${e.message}"
        }
    }

    /** Dipanggil dari JavaScript (live2d-bridge.js) lewat window.Android.onJsEvent(...) */
    inner class JsBridge {
        @JavascriptInterface
        fun onJsEvent(event: String, dataJson: String) {
            when (event) {
                "onModelError" -> runOnUiThread {
                    binding.txtSubtitle.text = "Gagal load model: $dataJson"
                }
                "onJsError" -> runOnUiThread {
                    binding.txtSubtitle.text = "Error di avatar: $dataJson"
                }
                else -> runOnUiThread {
                    binding.txtSubtitle.text = "[$event] $dataJson"
                }
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        releasePlaybackResources()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
