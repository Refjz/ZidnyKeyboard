package com.zidny.keyboard

/**
 * Unicode letter styles for bios and captions. They are ordinary characters, so the styled text works in
 * any app. [sample] is how the style's name looks in the picker.
 */
enum class FancyFont(val sample: String) {
    NORMAL("Normal"),
    BOLD("𝐁𝐨𝐥𝐝"),
    ITALIC("𝘐𝘵𝘢𝘭𝘪𝘤"),
    BOLD_ITALIC("𝙱𝚘𝚕𝚍 𝚒𝚝𝚊𝚕𝚒𝚌"),
    SCRIPT("𝓢𝓪𝓶𝓫𝓾𝓷𝓰"),
    DOUBLE("𝔻𝕠𝕦𝕓𝕝𝕖"),
    BUBBLE("ⓑⓤⓑⓑⓛⓔ"),
    SQUARE("🅺🅾🆃🅰🅺"),
    SMALL_CAPS("ᴋᴀᴘɪᴛᴀʟ ᴋᴇᴄɪʟ"),
    TINY("ᵏᵉᶜⁱˡ"),
    STRIKE("c̶o̶r̶e̶t̶"),
    UNDERLINE("g̲a̲r̲i̲s̲"),
    WIDE("ｌｅｂａｒ");

    /** [text] written in this style; characters the style has no form for stay as they are. */
    fun apply(text: String): String {
        if (this == NORMAL) return text
        val out = StringBuilder()
        for (c in text) out.append(convert(c))
        return out.toString()
    }

    private fun convert(c: Char): String {
        val upper = c in 'A'..'Z'
        val lower = c in 'a'..'z'
        val digit = c in '0'..'9'
        fun math(upperStart: Int, lowerStart: Int, digitStart: Int? = null): String = when {
            upper -> String(Character.toChars(upperStart + (c - 'A')))
            lower -> String(Character.toChars(lowerStart + (c - 'a')))
            digit && digitStart != null -> String(Character.toChars(digitStart + (c - '0')))
            else -> c.toString()
        }
        return when (this) {
            NORMAL -> c.toString()
            BOLD -> math(0x1D400, 0x1D41A, 0x1D7CE)
            ITALIC -> when (c) {
                // The italic h sits in an older Unicode block.
                'h' -> "ℎ"
                else -> math(0x1D608, 0x1D622)
            }
            BOLD_ITALIC -> math(0x1D63C, 0x1D656)
            SCRIPT -> math(0x1D4D0, 0x1D4EA)
            DOUBLE -> when (c) {
                'C' -> "ℂ"; 'H' -> "ℍ"; 'N' -> "ℕ"; 'P' -> "ℙ"; 'Q' -> "ℚ"; 'R' -> "ℝ"; 'Z' -> "ℤ"
                else -> math(0x1D538, 0x1D552, 0x1D7D8)
            }
            BUBBLE -> when {
                upper -> String(Character.toChars(0x24B6 + (c - 'A')))
                lower -> String(Character.toChars(0x24D0 + (c - 'a')))
                c == '0' -> "⓪"
                c in '1'..'9' -> String(Character.toChars(0x2460 + (c - '1')))
                else -> c.toString()
            }
            SQUARE -> if (upper || lower) String(Character.toChars(0x1F170 + (c.uppercaseChar() - 'A'))) else c.toString()
            SMALL_CAPS -> SMALL_CAPS_MAP[c.lowercaseChar()]?.takeIf { upper || lower }?.toString() ?: c.toString()
            TINY -> TINY_MAP[c.lowercaseChar()]?.takeIf { upper || lower || digit }?.toString() ?: c.toString()
            STRIKE -> if (c.isWhitespace()) c.toString() else "$c\u0336"
            UNDERLINE -> if (c.isWhitespace()) c.toString() else "$c\u0332"
            WIDE -> when {
                c == ' ' -> "\u3000"
                c in '!'..'~' -> (c + 0xFEE0).toString()
                else -> c.toString()
            }
        }
    }

    private companion object {
        val SMALL_CAPS_MAP = "abcdefghijklmnopqrstuvwxyz".zip("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ").toMap()
        val TINY_MAP = "abcdefghijklmnopqrstuvwxyz0123456789".zip("ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖqʳˢᵗᵘᵛʷˣʸᶻ⁰¹²³⁴⁵⁶⁷⁸⁹").toMap()
    }
}
