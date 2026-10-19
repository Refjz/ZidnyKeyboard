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
        /** [meaning] is the result in Indonesian, when asked for; [polished] means the text was fixed rather than translated. */
        data class Success(
            val text: String,
            val source: Language,
            val meaning: String? = null,
            val polished: Boolean = false,
        ) : Result()
        data class AlreadyInTarget(val language: Language) : Result()
        data class Error(val message: String) : Result()
    }

    private val identifier = LanguageIdentification.getClient()
    private val integrity = IntegrityChecker(context)
    private val main = Handler(Looper.getMainLooper())

    /** What to ask the server for, besides the text and languages. */
    data class Options(
        val style: TranslationStyle = TranslationStyle.CASUAL,
        /** Also return the result's meaning in Indonesian. */
        val explain: Boolean = false,
        /** If the text is already in the target language, fix its grammar instead of giving up. */
        val polishSameLanguage: Boolean = false,
    )

    /** Translates [text] into [target] and calls [onResult] on the main thread. */
    fun translate(text: String, target: Language, options: Options = Options(), onResult: (Result) -> Unit) {
        identifier.identifyLanguage(text)
            .addOnSuccessListener { tag ->
                // Casual Indonesian is often detected as Malay, Javanese, Sundanese or not at all, so treat
                // those as Indonesian (unless translating into Indonesian, where unknown text is most likely English).
                val source = when (tag) {
                    in INDONESIAN_LOOKALIKES -> Language.INDONESIAN
                    else -> Language.fromCode(tag)
                        ?: if (target == Language.INDONESIAN) Language.ENGLISH else Language.INDONESIAN
                }
                if (source == target && !options.polishSameLanguage) {
                    onResult(Result.AlreadyInTarget(target))
                    return@addOnSuccessListener
                }
                val polish = source == target
                // Polishing keeps the user's own words, so slang is only normalised before translating.
                val input = if (source == Language.INDONESIAN && !polish) SlangNormalizer.normalize(text) else text
                translateOnServer(Request(input, source, target, options.style, polish, options.explain), onResult)
            }
            .addOnFailureListener { onResult(Result.Error("Gagal mengenali bahasa")) }
    }

    private data class Request(
        val text: String,
        val source: Language,
        val target: Language,
        val style: TranslationStyle,
        val polish: Boolean,
        val explain: Boolean,
        /** Sends only text, source and target, like app versions before styles, for a server not yet updated. */
        val legacy: Boolean = false,
    ) {
        val mode get() = if (polish) "polish" else "translate"

        /** Must match requestHashInput() in server/src/index.js. */
        val hashInput get() =
            if (legacy) "${source.code}|${target.code}|$text"
            else "v2|${source.code}|${target.code}|${style.code}|$mode|$explain|$text"
    }

    private fun translateOnServer(request: Request, onResult: (Result) -> Unit) {
        if (BuildConfig.TRANSLATE_URL.isEmpty()) {
            onResult(Result.Error("Server terjemahan belum diatur"))
            return
        }
        integrity.token(sha256Hex(request.hashInput)) { token ->
            thread(name = "translate") {
                val result = post(request, token)
                // A server still running the old version rejects the new request format as not genuine.
                // Plain translation still works there, so ask again the old way (without style or meaning).
                if (result == NOT_GENUINE && !request.legacy && !request.polish) {
                    main.post { translateOnServer(request.copy(legacy = true), onResult) }
                } else {
                    main.post { onResult(result) }
                }
            }
        }
    }

    private fun post(request: Request, token: String?): Result {
        val connection = URL(BuildConfig.TRANSLATE_URL.trimEnd('/') + "/translate").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (token != null) connection.setRequestProperty("X-Integrity-Token", token)
            val body = JSONObject()
                .put("text", request.text)
                .put("source", request.source.code)
                .put("target", request.target.code)
            if (!request.legacy) {
                body.put("style", request.style.code).put("mode", request.mode).put("explain", request.explain)
            }
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            when (connection.responseCode) {
                200 -> {
                    val reply = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val meaning = reply.optString("meaning").takeIf { it.isNotBlank() }
                    Result.Success(reply.getString("text"), request.source, meaning, request.polish)
                }
                403 -> NOT_GENUINE
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
        val INDONESIAN_LOOKALIKES = setOf("ms", "jv", "su")
        val NOT_GENUINE = Result.Error("Terjemahan hanya untuk aplikasi resmi dari Google Play")

        fun sha256Hex(input: String): String =
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
