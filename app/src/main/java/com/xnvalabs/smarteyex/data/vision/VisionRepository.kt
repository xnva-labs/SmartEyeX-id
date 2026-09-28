package com.xnvalabs.smarteyex.data.vision

import android.util.Base64
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
 * Tahap 4 (Camera / Computer Vision) — sends a captured frame to the
 * same backend [XnaiRepository] talks to (feature #7 Computer Vision,
 * #8 Reading Assistant/OCR). Same "model lives server-side" contract as
 * Tahap 3: this app never runs OCR or object recognition itself, it
 * just POSTs a JPEG to "$endpointUrl/vision" and displays whatever
 * "description" string comes back. Reuses [XnaiRepository.endpointUrl]
 * rather than a separate config value — one backend, one place to point
 * it at.
 *
 * Gated by TWO privacy toggles, not one: cameraEnabled (capturing at
 * all — VisionScreen checks this before ever opening the camera) and
 * cloudProcessingEnabled (sending the frame off-device — checked here,
 * same enforcement pattern as XnaiRepository). A frame is never
 * analyzed if either is off.
 */
object VisionRepository {
    suspend fun analyzeFrame(jpegBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        if (!PrivacyRepository.settings.value.cameraEnabled) {
            return@withContext Result.failure(
                IllegalStateException("Camera OFF — aktifkan di Privacy Control."),
            )
        }
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
            val connection = URL("${XnaiRepository.endpointUrl}/vision").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000

            val body = JSONObject().apply {
                put("imageBase64", Base64.encodeToString(jpegBytes, Base64.NO_WRAP))
            }
            OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (code !in 200..299) {
                error("Vision backend error ($code): $responseText")
            }

            JSONObject(responseText).getString("description")
        }
    }
}
