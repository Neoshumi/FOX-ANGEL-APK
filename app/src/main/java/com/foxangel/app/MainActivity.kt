
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
