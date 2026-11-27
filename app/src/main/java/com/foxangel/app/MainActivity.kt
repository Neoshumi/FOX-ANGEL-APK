package com.foxangel.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
        setContent { FoxAngelScreen() }
    }
}

@Composable
fun FoxAngelScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("fox_private", 0) }
    var ip by remember { mutableStateOf(prefs.getString("ip", "192.168.0.178") ?: "192.168.0.178") }
    var apiKey by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("DISCONNECTED") }
    var message by remember { mutableStateOf("Entrez l’IP et la clé API FOX") }
    var listening by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun request(path: String): String {
        val encoded = URLEncoder.encode(apiKey, "UTF-8")
        val cleanIp = ip.trim().removePrefix("http://").removeSuffix("/")
        val url = URL("http://$cleanIp/$encoded/$path")
        val conn = (url.openConnection() as HttpURLConnection)
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "GET"
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            if (code !in 200..299) throw IllegalStateException("HTTP $code: $body")
            body
        } finally { conn.disconnect() }
    }

    fun runFox(path: String, label: String) {
        if (!connected || busy) return
        busy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { request(path) }
                message = "$label: $result"
                if (path == "get_state/") status = result.take(80)
                else {
                    val state = withContext(Dispatchers.IO) { request("get_state/") }
                    status = state.take(80)
                }
            } catch (e: Exception) {
                message = "Erreur FOX: ${e.message ?: "connexion impossible"}"
                connected = false
                status = "DISCONNECTED"
            } finally { busy = false }
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
                    prefs.edit().putString("ip", ip.trim()).putString("key", apiKey).apply()
                    request("get_state/")
                }
                connected = true
                status = result.take(80)
                message = "Connexion FOX réussie"
            } catch (e: Exception) {
                connected = false
                status = "DISCONNECTED"
                message = "Échec connexion: ${e.message ?: "vérifiez IP / clé / Wi-Fi"}"
            } finally { busy = false }
        }
    }

    fun handleVoice(text: String) {
        val s = text.lowercase(Locale.ROOT)
        val hasAngel = s.contains("angel") || s.contains("انجل") || s.contains("أنجل") || s.contains("انجيل")
        if (!hasAngel) { message = "Entendu: $text — mot ANGEL non détecté"; return }
        when {
            listOf("طف", "طفي", "اطفي", "سكر", "eteins", "éteins", "eteindre", "off", "coupe").any { s.contains(it) } ->
                runFox("set_state/?state=off", "OFF")
            listOf("شغل", "شعل", "ولع", "افتح", "allume", "allumer", "on", "active").any { s.contains(it) } ->
                runFox("set_state/?state=on", "ON")
            listOf("حالة", "الحالة", "status", "statut", "etat", "état").any { s.contains(it) } ->
                runFox("get_state/", "STATE")
            else -> message = "Entendu: $text — commande ANGEL non reconnue"
        }
    }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listening = false
            message = "Service de reconnaissance vocale indisponible sur ce téléphone"
            return
        }
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }

            recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    message = "ANGEL écoute… قول الأمر الآن"
                }
                override fun onBeginningOfSpeech() {
                    message = "صوتك وصل — نحلّل الكلام…"
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    message = "Fin de parole — analyse…"
                }
                override fun onError(error: Int) {
                    listening = false
                    message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Erreur audio (3)"
                        SpeechRecognizer.ERROR_CLIENT -> "Erreur du service vocal (5)"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission microphone refusée"
                        SpeechRecognizer.ERROR_NETWORK -> "Erreur réseau du service vocal (2)"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Délai réseau dépassé (1)"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Aucune parole reconnue (7) — جرّب مرة أخرى"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Service vocal مشغول (8)"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ما سمعناش كلام (6) — عاود اضغط V"
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "طلبات صوتية كثيرة — استنّى شوية (10)"
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "انقطع اتصال خدمة التعرف على الصوت (11)"
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "لغة التعرف غير مدعومة (12)"
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "حزمة اللغة العربية غير متوفرة (13)"
                        else -> "Erreur reconnaissance vocale : code $error"
                    }
                    if (speechRecognizer === recognizer) {
                        speechRecognizer = null
                    }
                    recognizer.destroy()
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (speechRecognizer === recognizer) {
                        speechRecognizer = null
                    }
                    recognizer.destroy()
                    if (heard.isBlank()) message = "Aucune parole reconnue"
                    else {
                        message = "Entendu: $heard"
                        handleVoice(heard)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) message = "J'entends: $partial"
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            listening = true
            message = "Démarrage du service vocal…"
            recognizer.startListening(intent)
        } catch (e: Exception) {
            listening = false
            speechRecognizer?.destroy()
            speechRecognizer = null
            message = "Impossible de démarrer la reconnaissance: ${e.message ?: "erreur inconnue"}"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceRecognition()
        else {
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

    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = Panel, primary = Blue,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Column(
            Modifier.fillMaxSize().background(Bg).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("FOX ANGEL", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text("FOX WI-RS1P-P CONTROL", color = Color(0xFF8B9BB4))
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; connected = false },
                label = { Text("FOX REST API KEY") }, visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = ip, onValueChange = { ip = it; connected = false },
                label = { Text("FOX IP ADDRESS") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Button(onClick = { connect() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "CONNECTING…" else "CONNECT")
            }
            Spacer(Modifier.height(14.dp))
            Text(if (connected) "CONNECTED" else "DISCONNECTED",
                color = if (connected) Color(0xFF4ADE80) else Color(0xFFF87171))
            Text("FOX STATE: $status", color = Color(0xFFB6C5D9))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlButton("A", "ALLUMER", Color(0xFF14532D), connected && !busy) { runFox("set_state/?state=on", "ON") }
                ControlButton("0", "ÉTEINDRE", Color(0xFF7F1D1D), connected && !busy) { runFox("set_state/?state=off", "OFF") }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlButton("STATE", "ÉTAT FOX", Color(0xFF1E3A8A), connected && !busy) { runFox("get_state/", "STATE") }
                ControlButton("V", if (listening) "ÉCOUTE…" else "VOICE ANGEL", Color(0xFF4C1D95), connected && !busy) {
                    if (listening) {
                        speechRecognizer?.cancel()
                        speechRecognizer?.destroy()
                        speechRecognizer = null
                        listening = false
                        message = "Écoute arrêtée"
                    } else {
                        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
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
            Text("DARK THEME • ANDROID TEST 0.1", color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun RowScope.ControlButton(
    title: String, subtitle: String, color: Color, enabled: Boolean, action: () -> Unit
) {
    Button(
        onClick = action, enabled = enabled,
        modifier = Modifier.weight(1f).height(104.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall)
        }
    }
}
