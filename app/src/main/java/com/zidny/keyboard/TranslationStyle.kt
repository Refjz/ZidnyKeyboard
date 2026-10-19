package com.zidny.keyboard

/** How the translation should sound. [code] is what the translation server expects. */
enum class TranslationStyle(val code: String, val emoji: String, val displayName: String) {
    CASUAL("casual", "😎", "Santai"),
    POLITE("polite", "🙂", "Sopan"),
    FORMAL("formal", "👔", "Formal"),
}
