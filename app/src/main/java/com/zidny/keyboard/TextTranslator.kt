package com.zidny.keyboard

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Detects the language of a text and translates it on the device with ML Kit.
 * Language packs are downloaded the first time a language is used.
 */
class TextTranslator {

    sealed class Result {
        data class Success(val text: String, val source: Language) : Result()
        data class AlreadyInTarget(val language: Language) : Result()
        data class Error(val message: String) : Result()
    }

    private val identifier = LanguageIdentification.getClient()
    private val translators = mutableMapOf<String, Translator>()

    /**
     * Translates [text] into [target]. [onDownloading] is called when a language pack
     * has to be downloaded first, so the UI can tell the user why it is slow.
     */
    fun translate(text: String, target: Language, onDownloading: () -> Unit, onResult: (Result) -> Unit) {
        identifier.identifyLanguage(text)
            .addOnSuccessListener { tag ->
                // Unknown or unsupported text falls back to Indonesian (or English when translating into Indonesian).
                val source = Language.fromCode(tag)
                    ?: if (target == Language.INDONESIAN) Language.ENGLISH else Language.INDONESIAN
                if (source == target) {
                    onResult(Result.AlreadyInTarget(target))
                    return@addOnSuccessListener
                }
                translateFrom(source, text, target, onDownloading, onResult)
            }
            .addOnFailureListener { onResult(Result.Error("Gagal mengenali bahasa")) }
    }

    private fun translateFrom(
        source: Language,
        text: String,
        target: Language,
        onDownloading: () -> Unit,
        onResult: (Result) -> Unit,
    ) {
        val translator = translators.getOrPut("${source.code}-${target.code}") {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source.code)
                    .setTargetLanguage(target.code)
                    .build()
            )
        }
        isDownloaded(source, target) { ready ->
            if (!ready) onDownloading()
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .onSuccessTask { translator.translate(text) }
                .addOnSuccessListener { onResult(Result.Success(it, source)) }
                .addOnFailureListener {
                    onResult(
                        Result.Error(
                            if (ready) "Gagal menerjemahkan" else "Gagal mengunduh paket bahasa, cek internet"
                        )
                    )
                }
        }
    }

    private fun isDownloaded(source: Language, target: Language, callback: (Boolean) -> Unit) {
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                val codes = models.map { it.language }.toSet()
                callback(source.code in codes && target.code in codes)
            }
            .addOnFailureListener { callback(false) }
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
        identifier.close()
    }

    companion object {
        /** Downloads every language pack so translating later works offline. */
        fun downloadAll(onDone: (Boolean) -> Unit) {
            val manager = RemoteModelManager.getInstance()
            val conditions = DownloadConditions.Builder().build()
            val tasks = Language.entries.map {
                manager.download(TranslateRemoteModel.Builder(it.code).build(), conditions)
            }
            com.google.android.gms.tasks.Tasks.whenAll(tasks)
                .addOnSuccessListener { onDone(true) }
                .addOnFailureListener { onDone(false) }
        }
    }
}
