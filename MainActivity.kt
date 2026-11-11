package com.tapas.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var answer: TextView
    private lateinit var apiKey: EditText
    private lateinit var tts: TextToSpeech
    private val requestSpeech = 100
    private val requestMic = 101
    private val prefs by lazy { getSharedPreferences("jarvis_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        answer = findViewById(R.id.answer)
        apiKey = findViewById(R.id.apiKey)
        tts = TextToSpeech(this, this)
        apiKey.setText(prefs.getString("gemini_key", ""))

        findViewById<Button>(R.id.micButton).setOnClickListener { startListening() }
        findViewById<Button>(R.id.saveKey).setOnClickListener {
            prefs.edit().putString("gemini_key", apiKey.text.toString().trim()).apply()
            respond("Gemini key saved on this device. Keep it private.")
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) tts.language = Locale("hi", "IN")
    }

    private fun startListening() {
        if (!SpeechRecognizerSupport.available(this)) {
            respond("Speech recognition is not available on this phone.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), requestMic)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            status.text = "LISTENING..."
            startActivityForResult(intent, requestSpeech)
        } catch (_: Exception) {
            respond("Voice input could not start. Please check your speech services.")
        }
    }

    @Deprecated("Deprecated in Android API; kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestSpeech && resultCode == Activity.RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) handleCommand(spoken)
            else respond("I did not catch that. Please try again.")
        }
    }

    private fun handleCommand(raw: String) {
        val q = raw.lowercase(Locale.ROOT).trim()
        status.text = "YOU SAID: $raw"

        // Offline command handling: these actions do not need an AI API.
        when {
            q.contains("open whatsapp") || q.contains("व्हाट्सएप खोलो") || q.contains("व्हाट्सऐप खोलो") ->
                launchPackage("com.whatsapp", "WhatsApp is not installed.")
            q.contains("open youtube") || q.contains("यूट्यूब खोलो") ->
                launchPackage("com.google.android.youtube", "YouTube is not installed.")
            q.contains("open camera") || q.contains("कैमरा खोलो") ->
                launchIntent(Intent("android.media.action.IMAGE_CAPTURE"), "Camera could not be opened.")
            q.contains("open settings") || q.contains("सेटिंग खोलो") ->
                launchIntent(Intent(Settings.ACTION_SETTINGS), "Settings could not be opened.")
            q.contains("bluetooth") && (q.contains("setting") || q.contains("सेटिंग")) ->
                launchIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Bluetooth settings unavailable.")
            q.contains("wifi") && (q.contains("setting") || q.contains("सेटिंग")) ->
                launchIntent(Intent(Settings.ACTION_WIFI_SETTINGS), "Wi-Fi settings unavailable.")
            q.contains("battery") || q.contains("बैटरी") -> {
                val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
                respond("Battery level is ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent.")
            }
            q.contains("alarm") || q.contains("अलार्म") -> {
                val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                launchIntent(i, "Clock app could not be opened.")
            }
            q.startsWith("call ") || q.startsWith("कॉल ") -> {
                val name = raw.substringAfter(' ').trim()
                val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                launchIntent(i, "Phone app could not be opened. Select a contact to call.")
                respond("I opened the dialer. Please select $name from your contacts.")
            }
            q.contains("time") || q.contains("समय") ->
                respond("The time is " + java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(java.util.Date()))
            else -> askGemini(raw)
        }
    }

    private fun launchPackage(pkg: String, failure: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i) else respond(failure)
    }

    private fun launchIntent(intent: Intent, failure: String) {
        try {
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            else respond(failure)
        } catch (_: Exception) { respond(failure) }
    }

    private fun askGemini(question: String) {
        val key = prefs.getString("gemini_key", "").orEmpty()
        if (key.isBlank()) {
            respond("This is an offline basic command. To ask general questions, add your Gemini API key in the box below.")
            return
        }
        status.text = "THINKING..."
        CoroutineScope(Dispatchers.IO).launch {
            val result = try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject()
                    .put("system_instruction", JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", "You are JARVIS, a concise helpful Hindi and English Android voice assistant. Reply naturally in the user's language. You cannot directly control phone functions unless the app provides a command.")
                    )))
                    .put("contents", JSONArray().put(JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", question)))))
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val response = stream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(response)
                json.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")
                    ?.optJSONObject(0)?.optString("text")
                    ?: "Gemini could not provide a response. Check the API key and internet."
            } catch (e: Exception) {
                "AI connection failed. Check internet and API key."
            }
            withContext(Dispatchers.Main) { respond(result) }
        }
    }

    private fun respond(message: String) {
        answer.text = message
        status.text = "JARVIS • READY"
        if (::tts.isInitialized) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    override fun onDestroy() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestMic && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else if (requestCode == requestMic) respond("Microphone permission is needed for voice commands.")
    }
}

private object SpeechRecognizerSupport {
    fun available(activity: Activity): Boolean =
        android.speech.SpeechRecognizer.isRecognitionAvailable(activity)
}
