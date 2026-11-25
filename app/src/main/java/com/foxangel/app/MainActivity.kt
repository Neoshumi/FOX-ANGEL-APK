
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
