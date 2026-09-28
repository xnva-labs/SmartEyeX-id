package com.xnvalabs.smarteyex.data.translation

import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.data.xnai.XnaiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Feature #9 (Translation) — same "model lives server-side" backend as
 * Tahap 3's XNAI chat and Tahap 4's Computer Vision: POSTs text to
 * "$endpointUrl/translate" and displays whatever "translated" string
 * comes back. Reuses [XnaiRepository.endpointUrl] — one backend, one
 * place to point it at, same as [com.xnvalabs.smarteyex.data.vision.VisionRepository].
 *
 * Gated by cloudProcessingEnabled only (translation has no camera/mic
 * component, so there's nothing else to check) — same enforcement
 * pattern as every other backend-calling repository here.
 */
object TranslationRepository {
    suspend fun translate(text: String, targetLanguage: String): Result<String> = withContext(Dispatchers.IO) {
        if (!PrivacyRepository.settings.value.cloudProcessingEnabled) {
            return@withContext Result.failure(
                IllegalStateException("Cloud Processing OFF — aktifkan di Privacy Control."),
            )
        }
        if (XnaiRepository.endpointUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Endpoint XNAI belum diisi — set XnaiRepository.endpointUrl."),
            )
        }

        runCatching {
            val connection = URL("${XnaiRepository.endpointUrl}/translate").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000

            val body = JSONObject().apply {
                put("text", text)
                put("targetLanguage", targetLanguage)
            }
            OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (code !in 200..299) {
                error("Translation backend error ($code): $responseText")
            }

            JSONObject(responseText).getString("translated")
        }
    }
}
