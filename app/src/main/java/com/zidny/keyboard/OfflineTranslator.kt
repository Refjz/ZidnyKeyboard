package com.zidny.keyboard

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Google's on-device translation (ML Kit). It answers in a blink and works without internet, so its result
 * shows first while the AI on the server writes a more natural one. Nothing is sent anywhere, so it can't
 * be abused the way an open server could. Each language needs a model of about 30 MB, downloaded once the
 * first time that language is used; until then only the server translates.
 */
class OfflineTranslator {

    private val translators = mutableMapOf<Pair<Language, Language>, Translator>()
    /** Pairs whose models are on the phone, so translating them doesn't wait for a download. */
    private val ready = mutableSetOf<Pair<Language, Language>>()
    private val downloading = mutableSetOf<Pair<Language, Language>>()

    private fun client(source: Language, target: Language): Translator? {
        val from = TranslateLanguage.fromLanguageTag(source.code) ?: return null
        val to = TranslateLanguage.fromLanguageTag(target.code) ?: return null
        return translators.getOrPut(source to target) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
        }
    }

    /** Downloads the models for [source] → [target] in the background if they aren't on the phone yet. */
    fun prepare(source: Language, target: Language) {
        val pair = source to target
        if (source == target || pair in ready || pair in downloading) return
        val translator = client(source, target) ?: return
        downloading.add(pair)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { ready.add(pair) }
            .addOnCompleteListener { downloading.remove(pair) }
    }

    /**
     * Translates [text] on the phone and calls [onResult] on the main thread with the result, or with null
     * when the models aren't downloaded yet (their download then starts) or translating failed.
     */
    fun translate(text: String, source: Language, target: Language, onResult: (String?) -> Unit) {
        val pair = source to target
        val translator = client(source, target)
        if (translator == null || pair !in ready) {
            prepare(source, target)
            onResult(null)
            return
        }
        translator.translate(text)
            .addOnSuccessListener { onResult(it.takeIf { result -> result.isNotBlank() }) }
            .addOnFailureListener { onResult(null) }
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
