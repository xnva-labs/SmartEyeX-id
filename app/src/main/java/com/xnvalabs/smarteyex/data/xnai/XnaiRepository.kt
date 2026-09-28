package com.xnvalabs.smarteyex.data.xnai

import com.xnvalabs.smarteyex.data.memory.MemoryRepository
import com.xnvalabs.smarteyex.data.memory.MemoryType
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tahap 3 (Backend XNAI) — talks to whatever XNAI backend ends up hosting
 * the actual model. Kept intentionally dumb on the client side: this app
 * sends the user's message, a short context string (profile fields +
 * preferences from Tahap 2/feature #31, see [buildContext]), the
 * conversation history, and the think mode, then just displays whatever
 * "reply" string comes back. Model choice, prompting, and any
 * multi-step reasoning all live server-side — swapping the model or the
 * whole backend later never touches this file's contract.
 *
 * Uses plain HttpURLConnection + org.json (both built into the Android
 * SDK) instead of Retrofit/OkHttp/Ktor — same "no dependency before it's
 * earned" rule as Privacy/Memory. Worth revisiting once streaming
 * responses or interceptors are actually needed.
 *
 * Every call checks [PrivacyRepository]'s cloud-processing toggle first
 * and returns a failure instead of ever hitting the network when it's
 * off — this is the one enforcement point for that toggle, same pattern
 * as PrivacyRepository/MemoryRepository.
 *
 * IMPORTANT — this is client-side wiring only. There is no XNAI backend
 * deployed yet: [endpointUrl] is blank by default, so every call fails
 * with a clear "endpoint belum diisi" message until an actual server
 * exists and this is pointed at it. No settings UI exists yet either —
 * that's a later refinement once a real endpoint exists to configure.
 */
object XnaiRepository {
    /** Expects a POST endpoint at "$endpointUrl/chat" — see [sendMessage]. */
    var endpointUrl: String = ""

    suspend fun sendMessage(
        userText: String,
        history: List<XnaiMessage>,
        thinkMode: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!PrivacyRepository.settings.value.cloudProcessingEnabled) {
            return@withContext Result.failure(
                IllegalStateException("Cloud Processing OFF — aktifkan di Privacy Control."),
            )
        }
        if (endpointUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Endpoint XNAI belum diisi — set XnaiRepository.endpointUrl."),
            )
        }

        runCatching {
            val connection = URL("$endpointUrl/chat").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000

            val body = JSONObject().apply {
                put("message", userText)
                put("thinkMode", thinkMode)
                put("context", buildContext())
                put(
                    "history",
                    JSONArray().apply {
                        history.forEach { msg ->
                            put(JSONObject().apply { put("role", msg.role); put("text", msg.text) })
                        }
                    },
                )
            }

            OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (code !in 200..299) {
                error("XNAI backend error ($code): $responseText")
            }

            JSONObject(responseText).getString("reply")
        }
    }

    /**
     * Context string sent with every message: profile fields (Tahap 2)
     * plus preferences (feature #31, "Gue lebih suka jawaban singkat"
     * style entries) — both live in MemoryRepository as different
     * MemoryTypes, concatenated here into one plain-text block the
     * backend's prompt can drop straight in.
     */
    private fun buildContext(): String {
        val entries = MemoryRepository.entries.value
        val profile = entries.filter { it.type == MemoryType.PROFILE }
        val preferences = entries.filter { it.type == MemoryType.PREFERENCE }

        val parts = mutableListOf<String>()
        if (profile.isNotEmpty()) {
            parts += "Profile: " + profile.joinToString("; ") { "${it.title}: ${it.content}" }
        }
        if (preferences.isNotEmpty()) {
            parts += "Preferences: " + preferences.joinToString("; ") { it.content }
        }
        return parts.joinToString(" | ")
    }
}
