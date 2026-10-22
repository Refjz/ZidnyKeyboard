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
            /** Translated on the phone, without the AI. */
            val offline: Boolean = false,
            /** The AI's translation is still on its way and will be passed on next. */
            val pending: Boolean = false,
        ) : Result()
        data class AlreadyInTarget(val language: Language) : Result()
        data class Error(val message: String) : Result()
    }

    /** A suggested reply, with its Indonesian [meaning] unless it is already in Indonesian. */
    data class Reply(val text: String, val meaning: String?)

    sealed class ReplyResult {
        /** [language] is the language of the received message, which the replies are written in. */
        data class Success(val replies: List<Reply>, val language: Language) : ReplyResult()
        data class Error(val message: String) : ReplyResult()
    }

    private val identifier = LanguageIdentification.getClient()
    private val integrity = IntegrityChecker(context)
    private val offline = OfflineTranslator()

    /** Use the on-device translation for an instant first result. */
    var offlineEnabled = true

    /** Prepares what the next translation into [target] needs, so it doesn't wait: Google Play and the offline models. */
    fun warmUp(target: Language) {
        integrity.warmUp()
        if (!offlineEnabled) return
        offline.prepare(Language.INDONESIAN, target)
        offline.prepare(target, Language.INDONESIAN)
    }
    private val main = Handler(Looper.getMainLooper())

    /** What to ask the server for, besides the text and languages. */
    data class Options(
        val style: TranslationStyle = TranslationStyle.CASUAL,
        /** Also return the result's meaning in Indonesian. */
        val explain: Boolean = false,
        /** If the text is already in the target language, fix its grammar instead of giving up. */
        val polishSameLanguage: Boolean = false,
    )

    /**
     * Translates [text] into [target] and calls [onResult] on the main thread. With the offline models on the
     * phone it is called twice: first at once with the on-device result (offline and pending), then with the
     * AI's. If the server can't be reached, the second call repeats the offline result, no longer pending.
     */
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
                val request = Request(input, source, target, options.style, polish, options.explain)
                if (input.length > SERVER_MAX_CHARS && !polish && offlineEnabled) {
                    // Too long for the AI: the phone's own translation is all there is.
                    offline.translate(input, source, target) { quick ->
                        onResult(if (quick != null) Result.Success(quick, source, offline = true) else TOO_LONG)
                    }
                    return@addOnSuccessListener
                }
                if (polish || !offlineEnabled) return@addOnSuccessListener translateOnServer(request, onResult)
                var serverDone = false
                var quickResult: String? = null
                offline.translate(input, source, target) { quick ->
                    // The server can win the race on a fast connection; its result is the better one.
                    if (quick == null || serverDone) return@translate
                    quickResult = quick
                    onResult(Result.Success(quick, source, offline = true, pending = true))
                }
                translateOnServer(request) { result ->
                    serverDone = true
                    val quick = quickResult
                    onResult(if (result is Result.Error && quick != null) Result.Success(quick, source, offline = true) else result)
                }
            }
            .addOnFailureListener { onResult(Result.Error("Gagal mengenali bahasa")) }
    }

    /** The outcome of [translateQuick]. */
    sealed class Quick {
        data class Done(val text: String) : Quick()
        /** Nothing to translate, e.g. the text is already in the target language. */
        object Same : Quick()
        /** The offline models for this language are still downloading (about 30 MB, once). */
        object Downloading : Quick()
        /** Downloading the models failed, e.g. without internet; the next try downloads again. */
        object DownloadFailed : Quick()
        object Off : Quick()
    }

    /** Lets the caller translate again once offline models finish downloading. */
    var onOfflineReady: (() -> Unit)?
        get() = offline.onReady
        set(value) { offline.onReady = value }

    /**
     * A fast translation for live translation while typing, made only on the phone: asking the server at every
     * pause would use up the daily Play Integrity quota. Calls [onResult] on the main thread.
     *
     * The user writes their own Indonesian while typing, and the first few words are too short to detect a
     * language reliably (they come out as Filipino, Malay and so on), so the text is taken as Indonesian unless
     * the target is Indonesian itself.
     */
    fun translateQuick(text: String, target: Language, onResult: (Quick) -> Unit) {
        if (!offlineEnabled) return onResult(Quick.Off)
        if (target != Language.INDONESIAN) return translateOffline(SlangNormalizer.normalize(text), Language.INDONESIAN, target, onResult)
        identifier.identifyLanguage(text)
            .addOnSuccessListener { tag ->
                val source = if (tag in INDONESIAN_LOOKALIKES) Language.INDONESIAN else Language.fromCode(tag) ?: Language.ENGLISH
                if (source == target) onResult(Quick.Same) else translateOffline(text, source, target, onResult)
            }
            .addOnFailureListener { translateOffline(text, Language.ENGLISH, target, onResult) }
    }

    private fun translateOffline(text: String, source: Language, target: Language, onResult: (Quick) -> Unit) {
        offline.translate(text, source, target) { result ->
            onResult(
                when {
                    result != null -> Quick.Done(result)
                    else -> when (offline.state(source, target)) {
                        OfflineTranslator.State.DOWNLOADING -> Quick.Downloading
                        OfflineTranslator.State.FAILED -> Quick.DownloadFailed
                        // Not a language ML Kit knows, or it gave up on this text: show it as typed.
                        else -> Quick.Same
                    }
                }
            )
        }
    }

    /**
     * Suggests short replies to [message], a message the user received, written in the message's own
     * language so the user can send one straight back. Calls [onResult] on the main thread.
     */
    fun suggestReplies(message: String, style: TranslationStyle, onResult: (ReplyResult) -> Unit) {
        if (BuildConfig.TRANSLATE_URL.isEmpty()) {
            onResult(ReplyResult.Error("Server terjemahan belum diatur"))
            return
        }
        identifier.identifyLanguage(message)
            .addOnSuccessListener { tag ->
                val language = if (tag in INDONESIAN_LOOKALIKES) Language.INDONESIAN else Language.fromCode(tag) ?: Language.INDONESIAN
                val request = Request(message, language, language, style, polish = false, explain = false, special = "reply")
                fetchReplies(request, language, "Saran balasan belum aktif di server", onResult)
            }
            .addOnFailureListener { onResult(ReplyResult.Error("Gagal mengenali bahasa")) }
    }

    /**
     * Writes ready-to-send lines in [mood] (e.g. a sweet pick-up line or a friendly roast) in [language],
     * about [topic] when given, each with its Indonesian meaning. Calls [onResult] on the main thread.
     */
    fun suggestLines(mood: LineMood, topic: String, language: Language, style: TranslationStyle, onResult: (ReplyResult) -> Unit) {
        if (BuildConfig.TRANSLATE_URL.isEmpty()) {
            onResult(ReplyResult.Error("Server terjemahan belum diatur"))
            return
        }
        // The mood goes on the first line of the text, so it is covered by the request hash like the rest.
        val request = Request("${mood.code}\n${topic.trim()}", language, language, style, polish = false, explain = false, special = "line")
        fetchReplies(request, language, "Fitur AI ini belum aktif di server", onResult)
    }

    /** Fixes the spelling and grammar of [text] in its own language, e.g. "km dmn" → "Kamu di mana?". */
    fun tidy(text: String, style: TranslationStyle, onResult: (Result) -> Unit) {
        identifier.identifyLanguage(text)
            .addOnSuccessListener { tag ->
                val language = if (tag in INDONESIAN_LOOKALIKES) Language.INDONESIAN else Language.fromCode(tag) ?: Language.INDONESIAN
                translateOnServer(Request(text, language, language, style, polish = true, explain = false), onResult)
            }
            .addOnFailureListener { onResult(Result.Error("Gagal mengenali bahasa")) }
    }

    private fun fetchReplies(request: Request, language: Language, notOnServer: String, onResult: (ReplyResult) -> Unit) {
        integrity.token(sha256Hex(request.hashInput)) { token ->
            thread(name = "replies") {
                val result = try {
                    val replies = postJson(request, token).getJSONArray("replies")
                    val list = (0 until replies.length()).map { i ->
                        val item = replies.getJSONObject(i)
                        Reply(item.getString("text"), item.optString("meaning").takeIf { it.isNotBlank() })
                    }
                    if (list.isEmpty()) ReplyResult.Error("Belum ada hasil, coba lagi")
                    else ReplyResult.Success(list, language)
                } catch (e: ServerError) {
                    // A server from before this feature turns the request down as invalid.
                    ReplyResult.Error(if (e.status == 400) notOnServer else e.error.message)
                } catch (e: org.json.JSONException) {
                    ReplyResult.Error("Server terjemahan sedang bermasalah, coba lagi")
                }
                main.post { onResult(result) }
            }
        }
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
        /** A mode other than translating or polishing: "reply" for replies to [text], "line" for AI lines. */
        val special: String? = null,
    ) {
        val mode get() = when {
            special != null -> special
            polish -> "polish"
            else -> "translate"
        }

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
        // The server turns longer texts down, which would otherwise show up as a server problem.
        if (request.text.length > SERVER_MAX_CHARS) return onResult(TOO_LONG)
        integrity.token(sha256Hex(request.hashInput)) { token ->
            thread(name = "translate") {
                val result = try {
                    val reply = postJson(request, token)
                    val meaning = reply.optString("meaning").takeIf { it.isNotBlank() }
                    Result.Success(reply.getString("text"), request.source, meaning, request.polish)
                } catch (e: ServerError) {
                    e.error
                } catch (e: org.json.JSONException) {
                    SERVER_PROBLEM
                }
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

    /** The server could not answer: [status] is the HTTP status, or 0 when it could not be reached. */
    private class ServerError(val status: Int, val error: Result.Error) : Exception()

    /** Sends [request] and returns the server's JSON answer, or throws [ServerError]. */
    private fun postJson(request: Request, token: String?): JSONObject {
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
            when (val status = connection.responseCode) {
                200 -> JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                403 -> throw ServerError(status, NOT_GENUINE)
                else -> throw ServerError(status, SERVER_PROBLEM)
            }
        } catch (e: IOException) {
            throw ServerError(0, Result.Error("Tidak ada koneksi internet"))
        } finally {
            connection.disconnect()
        }
    }

    fun close() {
        identifier.close()
        offline.close()
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        /** Must match MAX_CHARS in server/src/index.js. */
        const val SERVER_MAX_CHARS = 2000
        val TOO_LONG = Result.Error("Teksnya kepanjangan buat AI, maksimal $SERVER_MAX_CHARS huruf")
        val INDONESIAN_LOOKALIKES = setOf("ms", "jv", "su")
        val NOT_GENUINE = Result.Error("Terjemahan hanya untuk aplikasi resmi dari Google Play")
        val SERVER_PROBLEM = Result.Error("Server terjemahan sedang bermasalah, coba lagi")

        fun sha256Hex(input: String): String =
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
