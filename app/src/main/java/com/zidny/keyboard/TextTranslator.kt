package com.zidny.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.mlkit.nl.languageid.LanguageIdentification
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * Detects the language of a text on the device, then translates it on the ZidnyKeyboard server
 * (a Cloudflare Worker). Each request carries a Play Integrity token so the server only answers
 * genuine copies of the app installed from Google Play.
 */
class TextTranslator(context: Context) {

    sealed class Result {
        data class Success(val text: String, val source: Language) : Result()
        data class AlreadyInTarget(val language: Language) : Result()
        data class Error(val message: String) : Result()
    }

    private val identifier = LanguageIdentification.getClient()
    private val integrity = IntegrityChecker(context)
    private val main = Handler(Looper.getMainLooper())

    /** Translates [text] into [target] and calls [onResult] on the main thread. */
    fun translate(text: String, target: Language, onResult: (Result) -> Unit) {
        identifier.identifyLanguage(text)
            .addOnSuccessListener { tag ->
                // Casual Indonesian is often detected as Malay or not at all, so treat both as Indonesian
                // (unless translating into Indonesian, where unknown text is most likely English).
                val source = when {
                    tag == "ms" -> Language.INDONESIAN
                    else -> Language.fromCode(tag)
                        ?: if (target == Language.INDONESIAN) Language.ENGLISH else Language.INDONESIAN
                }
                if (source == target) {
                    onResult(Result.AlreadyInTarget(target))
                    return@addOnSuccessListener
                }
                val input = if (source == Language.INDONESIAN) SlangNormalizer.normalize(text) else text
                translateOnServer(source, input, target, onResult)
            }
            .addOnFailureListener { onResult(Result.Error("Gagal mengenali bahasa")) }
    }

    private fun translateOnServer(source: Language, text: String, target: Language, onResult: (Result) -> Unit) {
        if (BuildConfig.TRANSLATE_URL.isEmpty()) {
            onResult(Result.Error("Server terjemahan belum diatur"))
            return
        }
        // Must match requestHashInput() in server/src/index.js.
        val requestHash = sha256Hex("${source.code}|${target.code}|$text")
        integrity.token(requestHash) { token ->
            thread(name = "translate") {
                val result = post(source, text, target, token)
                main.post { onResult(result) }
            }
        }
    }

    private fun post(source: Language, text: String, target: Language, token: String?): Result {
        val connection = URL(BuildConfig.TRANSLATE_URL.trimEnd('/') + "/translate").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (token != null) connection.setRequestProperty("X-Integrity-Token", token)
            val body = JSONObject().put("text", text).put("source", source.code).put("target", target.code)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            when (connection.responseCode) {
                200 -> {
                    val reply = connection.inputStream.bufferedReader().use { it.readText() }
                    Result.Success(JSONObject(reply).getString("text"), source)
                }
                403 -> Result.Error("Terjemahan hanya untuk aplikasi resmi dari Google Play")
                else -> Result.Error("Server terjemahan sedang bermasalah, coba lagi")
            }
        } catch (e: IOException) {
            Result.Error("Tidak ada koneksi internet")
        } catch (e: org.json.JSONException) {
            Result.Error("Server terjemahan sedang bermasalah, coba lagi")
        } finally {
            connection.disconnect()
        }
    }

    fun close() {
        identifier.close()
    }

    private companion object {
        const val TIMEOUT_MS = 15_000

        fun sha256Hex(input: String): String =
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
