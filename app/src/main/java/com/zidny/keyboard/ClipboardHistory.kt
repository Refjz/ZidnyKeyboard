package com.zidny.keyboard

import org.json.JSONArray
import java.io.File

/**
 * The texts the user copied recently, newest first, like the Samsung keyboard's clipboard panel.
 * Kept only on the device in [file].
 */
class ClipboardHistory(private val file: File? = null) {

    private val entries = ArrayList<String>()

    val items: List<String> get() = entries

    init {
        file?.takeIf { it.exists() }?.let { f ->
            try {
                val array = JSONArray(f.readText())
                for (i in 0 until array.length()) entries.add(array.getString(i))
            } catch (e: Exception) {
                // A damaged file only loses the history.
            }
        }
    }

    /** Puts [text] at the top, moving it there if it was copied before. Returns false if it was ignored. */
    fun add(text: String): Boolean {
        if (text.isBlank() || text.length > MAX_CHARS) return false
        if (entries.firstOrNull() == text) return false
        entries.remove(text)
        entries.add(0, text)
        while (entries.size > MAX_ITEMS) entries.removeAt(entries.size - 1)
        save()
        return true
    }

    fun remove(text: String) {
        if (entries.remove(text)) save()
    }

    fun clear() {
        entries.clear()
        save()
    }

    private fun save() {
        file?.writeText(JSONArray(entries).toString())
    }

    companion object {
        const val MAX_ITEMS = 20
        const val MAX_CHARS = 5000
    }
}
