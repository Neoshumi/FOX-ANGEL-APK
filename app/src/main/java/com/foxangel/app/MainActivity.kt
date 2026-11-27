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
import java.text.Normalizer
import java.util.Locale

private val Bg = Color(0xFF080B12)
private val Panel = Color(0xFF151C29)
private val Blue = Color(0xFF38BDF8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FoxAngelScreen() }
    }
}

@Composable
fun FoxAngelScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("fox_private", 0) }

    var ip by remember {
        mutableStateOf(prefs.getString("ip", "192.168.0.178") ?: "192.168.0.178")
    }
    var apiKey by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("DISCONNECTED") }
    var message by remember { mutableStateOf("Entrez l’IP et la clé API FOX") }
    var listening by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val scope = rememberCoroutineScope()

    fun request(path: String): String {
        val encoded = URLEncoder.encode(apiKey, "UTF-8")
        val cleanIp = ip.trim().removePrefix("http://").removeSuffix("/")
        val url = URL("http://$cleanIp/$encoded/$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "GET"

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
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
                val result = withContext(Dispatchers.IO) { request(path) }
                message = "$label: $result"

                if (path == "get_state/") {
                    status = result.take(80)
                } else {
                    val currentState = withContext(Dispatchers.IO) { request("get_state/") }
                    status = currentState.take(80)
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
                val result = withContext(Dispatchers.IO) {
                    prefs.edit()
                        .putString("ip", ip.trim())
                        .putString("key", apiKey)
                        .apply()
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

    fun handleVoice(recognizedText: String) {
        // إزالة اللكنات الفرنسية وعلامات الترقيم لتسهيل مقارنة الكلمات
        val normalized = Normalizer.normalize(
            recognizedText.lowercase(Locale.FRENCH),
            Normalizer.Form.NFD
        ).replace("\\p{Mn}+".toRegex(), "")

        val words = normalized
            .replace("’", " ")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

        val hasAngel = words.any { it == "angel" || it == "angeul" }

        if (!hasAngel) {
            message = "Entendu: $recognizedText — mot ANGEL non détecté"
            return
        }

        when {
            words.any { it in setOf("off", "eteins", "eteindre", "coupe", "arrete") } -> {
                runFox("set_state/?state=off", "OFF")
            }

            words.any { it in setOf("on", "allume", "allumer", "active", "demarre") } -> {
                runFox("set_state/?state=on", "ON")
            }

            words.any { it in setOf("status", "statut", "etat") } -> {
                runFox("get_state/", "STATE")
            }

            else -> {
                message = "Entendu: $recognizedText — commande ANGEL non reconnue"
            }
        }
    }

    fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listening = false
            message = "Service de reconnaissance vocale indisponible sur ce téléphone"
            return
        }

        var newRecognizer: SpeechRecognizer? = null

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            newRecognizer = recognizer
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                // الأوامر المطلوبة بالفرنسية
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (speechRecognizer === recognizer) {
                        listening = true
                        message = "ANGEL écoute… dites « Angel ON » ou « Angel OFF »"
                    }
                }

                override fun onBeginningOfSpeech() {
                    if (speechRecognizer === recognizer) {
                        message = "Voix détectée — analyse…"
                    }
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    if (speechRecognizer === recognizer) {
                        message = "Fin de parole — reconnaissance…"
                    }
                }

                override fun onError(error: Int) {
                    if (speechRecognizer === recognizer) {
                        speechRecognizer = null
                        listening = false
                        message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO ->
                                "Erreur audio (3)"
                            SpeechRecognizer.ERROR_CLIENT ->
                                "Erreur du service vocal (5)"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Permission microphone refusée"
                            SpeechRecognizer.ERROR_NETWORK ->
                                "Erreur réseau du service vocal (2)"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Délai réseau dépassé (1)"
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Aucune parole reconnue (7) — réessayez en français"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                "Service vocal occupé (8)"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "Aucune parole entendue (6) — appuyez sur V et parlez"
                            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                                "Trop de demandes vocales (10) — attendez un instant"
                            SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                                "Service de reconnaissance déconnecté (11)"
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                                "La reconnaissance en français n’est pas prise en charge (12)"
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                                "La langue française n’est pas disponible (13)"
                            else ->
                                "Erreur de reconnaissance vocale : code $error"
                        }
                    }
                    recognizer.destroy()
                }

                override fun onResults(results: Bundle?) {
                    if (speechRecognizer === recognizer) {
                        speechRecognizer = null
                        listening = false

                        val heard = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()

                        if (heard.isBlank()) {
                            message = "Aucune parole reconnue"
                        } else {
                            message = "Entendu: $heard"
                            handleVoice(heard)
                        }
                    }
                    recognizer.destroy()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (speechRecognizer === recognizer) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()

                        if (!partial.isNullOrBlank()) {
                            message = "J'entends: $partial"
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            listening = true
            message = "Démarrage du service vocal…"
            recognizer.startListening(intent)
        } catch (e: Exception) {
            newRecognizer?.destroy()
            if (speechRecognizer === newRecognizer) {
                speechRecognizer = null
            }
            listening = false
            message = "Impossible de démarrer la reconnaissance: ${e.message ?: "erreur inconnue"}"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition()
        } else {
            listening = false
            message = "Autorisation microphone refusée"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
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
            modifier = Modifier
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
            Text("FOX WI-RS1P-P CONTROL", color = Color(0xFF8B9BB4))

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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
                color = if (connected) Color(0xFF4ADE80) else Color(0xFFF87171)
            )
            Text("FOX STATE: $status", color = Color(0xFFB6C5D9))

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
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
                        // إلغاء الجلسة فقط؛ لا نوقفها ثم نمسح النتيجة النهائية
                        speechRecognizer?.cancel()
                        speechRecognizer?.destroy()
                        speechRecognizer = null
                        listening = false
                        message = "Écoute annulée"
                    } else {
                        val permission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        )

                        if (permission == PackageManager.PERMISSION_GRANTED) {
                            startVoiceRecognition()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(message, color = Color(0xFFCBD5E1))
            Spacer(Modifier.weight(1f))
            Text("DARK THEME • ANDROID TEST 0.2", color = Color(0xFF64748B))
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
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall)
        }
    }
}
