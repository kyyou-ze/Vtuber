package com.live2d.demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

class MainActivity : AppCompatActivity() {

    // ===================== Live2D (SDK asli, jangan diubah) =====================

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var glRenderer: GLRenderer

    // ===================== UI kita =====================

    private lateinit var txtSubtitle: TextView
    private var tts: TextToSpeech? = null
    private lateinit var speechRecognizer: SpeechRecognizer

    private val geminiModel = "gemini-2.5-flash"
    private val systemPersona = """
        Kamu adalah IceGirl, seorang VTuber perempuan yang ceria, ramah, sedikit jahil,
        dan berbicara dalam Bahasa Indonesia yang santai. Jawab dengan singkat (1-3 kalimat)
        seperti sedang ngobrol langsung dengan penonton, jangan gunakan format markdown.
    """.trimIndent()

    private var mediaPlayer: MediaPlayer? = null
    private var pendingTtsFile: File? = null

    private val requestMicPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JniBridgeJava.SetActivityInstance(this)
        JniBridgeJava.SetContext(this)

        setContentView(R.layout.activity_main)

        // --- Live2D GLSurfaceView, disisipkan ke container di layout kita ---
        glSurfaceView = GLSurfaceView(this)
        glSurfaceView.setEGLContextClientVersion(2)
        glRenderer = GLRenderer()
        glSurfaceView.setRenderer(glRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        findViewById<FrameLayout>(R.id.glContainer).addView(glSurfaceView)

        glSurfaceView.setOnTouchListener { _, event ->
            val pointX = event.x
            val pointY = event.y
            glSurfaceView.queueEvent {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> JniBridgeJava.nativeOnTouchesBegan(pointX, pointY)
                    MotionEvent.ACTION_UP -> JniBridgeJava.nativeOnTouchesEnded(pointX, pointY)
                    MotionEvent.ACTION_MOVE -> JniBridgeJava.nativeOnTouchesMoved(pointX, pointY)
                }
            }
            true
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        } else {
            window.insetsController?.hide(WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())
            window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // --- fitur kita ---
        txtSubtitle = findViewById(R.id.txtSubtitle)
        setupTts()
        setupSpeechRecognizer()

        findViewById<View>(R.id.btnSettings).setOnClickListener { showApiKeyDialog() }
        findViewById<View>(R.id.btnMic).setOnClickListener {
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
            txtSubtitle.text = "Halo! Masukkan API key Gemini dulu lewat tombol ⚙ di pojok kanan atas."
        }
    }

    override fun onStart() {
        super.onStart()
        JniBridgeJava.nativeOnStart()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        JniBridgeJava.nativeOnPause()
    }

    override fun onStop() {
        super.onStop()
        JniBridgeJava.nativeOnStop()
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        mediaPlayer?.release()
        tts?.stop()
        tts?.shutdown()
        JniBridgeJava.nativeOnDestroy()
        super.onDestroy()
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

    // ===================== TTS + lipsync native =====================

    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ind", "IDN")
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                val file = pendingTtsFile
                pendingTtsFile = null
                if (file != null) {
                    runOnUiThread { playWithNativeLipSync(file) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
    }

    /** Sintesis teks ke file wav dulu, supaya native lipsync (LAppWavFileHandler)
     *  bisa membacanya sambil MediaPlayer memutar suaranya. */
    private fun speak(text: String) {
        val ttsEngine = tts ?: return
        val utteranceId = "icegirl_${System.currentTimeMillis()}"
        val outFile = File(cacheDir, "$utteranceId.wav")
        pendingTtsFile = outFile
        val result = ttsEngine.synthesizeToFile(text, null, outFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            pendingTtsFile = null
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fallback_$utteranceId")
        }
    }

    private fun playWithNativeLipSync(file: File) {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }

        // Native (C++) yang analisa amplitudo file wav ini secara real-time
        // dan menggerakkan ParamMouthOpenY avatar -- lihat LAppModel::StartLipSync.
        glSurfaceView.queueEvent {
            JniBridgeJava.nativeStartLipSync(file.absolutePath)
        }

        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { player.start() }
            player.setOnCompletionListener { file.delete() }
            player.setOnErrorListener { _, _, _ -> file.delete(); true }
            player.prepareAsync()
        } catch (e: Exception) {
            file.delete()
        }
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
                    txtSubtitle.text = "Kamu: $text"
                    askGemini(text)
                }
            }

            override fun onError(error: Int) {
                txtSubtitle.text = "Maaf, tidak dengar. Coba lagi ya."
            }

            override fun onReadyForSpeech(params: Bundle?) {
                txtSubtitle.text = "Mendengarkan..."
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
            txtSubtitle.text = "API key belum diisi. Tap tombol ⚙ dulu."
            showApiKeyDialog()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            txtSubtitle.text = "IceGirl sedang mikir..."
            val reply = withContext(Dispatchers.IO) {
                callGeminiApi(apiKey, userText)
            }
            txtSubtitle.text = "IceGirl: $reply"
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
}
