
package com.foxangel.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private val Bg = Color(0xFF080B12)
private val Panel = Color(0xFF151C29)
private val Blue = Color(0xFF38BDF8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoxAngelScreen()
        }
    }
}

@Composable
fun FoxAngelScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("fox_private", 0)
    }

    var ip by remember {
        mutableStateOf(prefs.getString("ip", "192.168.0.178") ?: "192.168.0.178")
    }
    var apiKey by remember {
        mutableStateOf(prefs.getString("key", "") ?: "")
    }

    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("DISCONNECTED") }
    var message by remember { mutableStateOf("Entrez l’IP et la clé API FOX") }
    var heardText by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    var speechRecognizer by remember {
        mutableStateOf<SpeechRecognizer?>(null)
    }

    val scope = rememberCoroutineScope()

    fun request(path: String): String {
        val encoded = URLEncoder.encode(apiKey, "UTF-8")
        val cleanIp = ip.trim()
            .removePrefix("http://")
            .removeSuffix("/")

        val url = URL("http://$cleanIp/$encoded/$path")
        val conn = url.openConnection() as HttpURLConnection

        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "GET"

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream
            }

            val body = stream?.bufferedReader()?.use {
                it.readText()
            } ?: "HTTP $code"

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: $body")
            }

            body
        } finally {
            conn.disconnect()
        }
    }

    fun runFox(path: String, label: String) {
        if (!connected || busy) return

        busy = true

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    request(path)
                }

                message = "$label: $result"

                if (path == "get_state/") {
                    status = result.take(80)
                } else {
                    val state = withContext(Dispatchers.IO) {
                        request("get_state/")
                    }
                    status = state.take(80)
                }
            } catch (e: Exception) {
                message = "Erreur FOX: ${e.message ?: "connexion impossible"}"
                connected = false
                status = "DISCONNECTED"
            } finally {
                busy = false
            }
        }
    }

    fun connect() {
        if (ip.isBlank() || apiKey.isBlank()) {
            message = "Veuillez saisir l’IP et la REST API KEY."
            return
        }

        busy = true

        scope.launch {
            try {
                prefs.edit()
                    .putString("ip", ip.trim())
                    .putString("key", apiKey)
                    .apply()

                val result = withContext(Dispatchers.IO) {
                    request("get_state/")
                }

                connected = true
                status = result.take(80)
                message = "Connexion FOX réussie"
            } catch (e: Exception) {
                connected = false
                status = "DISCONNECTED"
                message = "Échec connexion: ${e.message ?: "vérifiez IP / clé / Wi-Fi"}"
            } finally {
                busy = false
            }
        }
    }

    fun handleVoice(recognized: String) {
        heardText = recognized

        val s = recognized.lowercase(Locale.ROOT)
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")

        val hasAngel =
            s.contains("angel") ||
            s.contains("انجل") ||
            s.contains("انجيل")

        if (!hasAngel) {
            message = "ANGEL absent — phrase non exécutée"
            return
        }

        when {
            listOf(
                "طفي", "اطفي", "اطفئ", "سكر", "سكّر",
                "اطفاء", "طفّي", "eteins", "éteins",
                "eteindre", "off", "coupe"
            ).any { s.contains(it) } -> {
                runFox("set_state/?state=off", "OFF")
            }

            listOf(
                "شغل", "شغّل", "شعل", "ولع",
                "افتح", "allume", "allumer",
                "turn on", "on", "active"
            ).any { s.contains(it) } -> {
                runFox("set_state/?state=on", "ON")
            }

            listOf(
                "حالة", "الحالة", "status",
                "statut", "etat", "état"
            ).any { s.contains(it) } -> {
                runFox("get_state/", "STATE")
            }

            else -> {
                message = "ANGEL détecté, commande non reconnue"
            }
        }
    }

    fun speechError(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO ->
                "خطأ في الصوت أو الميكروفون"

            SpeechRecognizer.ERROR_CLIENT ->
                "خطأ في تشغيل خدمة التعرّف"

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "صلاحية الميكروفون غير كافية"

            SpeechRecognizer.ERROR_NETWORK ->
                "مشكلة في الشبكة"

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "انتهت مهلة الاتصال"

            SpeechRecognizer.ERROR_NO_MATCH ->
                "ما فهمتش الكلام — عاود المحاولة"

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "خدمة التعرّف مشغولة"

            SpeechRecognizer.ERROR_SERVER ->
                "خطأ في خادم التعرّف"

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "ما سمعت حتى كلام"

            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                "اللغة المطلوبة غير مدعومة"

            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "اللغة غير متوفرة حالياً"

            else ->
                "خطأ في التعرّف على الصوت: $error"
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            message = "خدمة التعرّف على الصوت غير متوفرة في الهاتف"
            listening = false
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-TN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-TN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            recognizer.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        listening = true
                        message = "ANGEL écoute… احكي توّة"
                    }

                    override fun onBeginningOfSpeech() {
                        message = "ANGEL تسمع في كلامك…"
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        message = "ANGEL تحلّل في الكلام…"
                    }

                    override fun onError(error: Int) {
                        listening = false
                        message = speechError(error)

                        speechRecognizer?.destroy()
                        speechRecognizer = null
                    }

                    override fun onResults(results: Bundle?) {
                        listening = false

                        val heard = results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            .orEmpty()

                        speechRecognizer?.destroy()
                        speechRecognizer = null

                        if (heard.isBlank()) {
                            message = "ما وصلني حتى كلام — عاود المحاولة"
                        } else {
                            handleVoice(heard)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )

            listening = true
            heardText = ""
            message = "تحضير الميكروفون…"

            recognizer.startListening(intent)

        } catch (e: Exception) {
            listening = false
            speechRecognizer?.destroy()
            speechRecognizer = null
            message = "خطأ تشغيل الصوت: ${e.message}"
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startListening()
            } else {
                message = "Autorisation microphone refusée"
            }
        }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Panel,
            primary = Blue,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                "FOX ANGEL",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )

            Text(
                "FOX WI-RS1P-P CONTROL",
                color = Color(0xFF8B9BB4)
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    connected = false
                },
                label = { Text("FOX REST API KEY") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = ip,
                onValueChange = {
                    ip = it
                    connected = false
                },
                label = { Text("FOX IP ADDRESS") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { connect() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "CONNECTING…" else "CONNECT")
            }

            Spacer(Modifier.height(14.dp))

            Text(
                if (connected) "CONNECTED" else "DISCONNECTED",
                color = if (connected) {
                    Color(0xFF4ADE80)
                } else {
                    Color(0xFFF87171)
                }
            )

            Text(
                "FOX STATE: $status",
                color = Color(0xFFB6C5D9)
            )

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ControlButton(
                    "A",
                    "ALLUMER",
                    Color(0xFF14532D),
                    connected && !busy
                ) {
                    runFox("set_state/?state=on", "ON")
                }

                ControlButton(
                    "0",
                    "ÉTEINDRE",
                    Color(0xFF7F1D1D),
                    connected && !busy
                ) {
                    runFox("set_state/?state=off", "OFF")
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ControlButton(
                    "STATE",
                    "ÉTAT FOX",
                    Color(0xFF1E3A8A),
                    connected && !busy
                ) {
                    runFox("get_state/", "STATE")
                }

                ControlButton(
                    "V",
                    if (listening) "ÉCOUTE…" else "VOICE ANGEL",
                    Color(0xFF4C1D95),
                    connected && !busy
                ) {
                    if (listening) {
                        speechRecognizer?.cancel()
                        speechRecognizer?.destroy()
                        speechRecognizer = null
                        listening = false
                        message = "Écoute arrêtée"
                    } else {
                        val permission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        )

                        if (permission == PackageManager.PERMISSION_GRANTED) {
                            startListening()
                        } else {
                            permissionLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                message,
                color = Color(0xFFCBD5E1)
            )

            if (heardText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))

                Text(
                    "ANGEL سمعت: $heardText",
                    color = Color(0xFF38BDF8)
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                "DARK THEME • ANDROID TEST 0.2",
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun RowScope.ControlButton(
    title: String,
    subtitle: String,
    color: Color,
    enabled: Boolean,
    action: () -> Unit
) {
    Button(
        onClick = action,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(104.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
