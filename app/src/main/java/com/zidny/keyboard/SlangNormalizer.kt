package com.zidny.keyboard

/**
 * Rewrites informal Indonesian chat language ("kamu udah dimana?"), including the Javanese and
 * Sundanese words people mix in ("piye kabare, wis mangan?"), into standard Indonesian
 * ("kamu sudah di mana?", "bagaimana kabarnya, sudah makan?") before translating, so the
 * translator (and its fallback model, which knows only formal text) gets the meaning right.
 */
object SlangNormalizer {

    /** Expressions of more than one word, replaced before single words. */
    private val phrases = mapOf(
        "matur nuwun" to "terima kasih", "matur suwun" to "terima kasih", "hatur nuhun" to "terima kasih",
        "ora popo" to "tidak apa-apa", "ora opo opo" to "tidak apa-apa", "teu nanaon" to "tidak apa-apa",
        "sami sami" to "sama-sama", "sami-sami" to "sama-sama",
    )

    private val words = mapOf(
        // Pronouns
        "gue" to "saya", "gua" to "saya", "gw" to "saya", "ane" to "saya", "sy" to "saya",
        "lo" to "kamu", "lu" to "kamu", "elo" to "kamu", "elu" to "kamu", "loe" to "kamu", "km" to "kamu",
        "ak" to "aku",
        // Time and state
        "udah" to "sudah", "udh" to "sudah", "dah" to "sudah", "sdh" to "sudah",
        "blm" to "belum", "belom" to "belum",
        "lg" to "lagi", "skrg" to "sekarang", "skg" to "sekarang", "td" to "tadi",
        "bsk" to "besok", "ntar" to "nanti", "ntr" to "nanti",
        // Negation
        "gak" to "tidak", "ga" to "tidak", "gk" to "tidak", "nggak" to "tidak", "ngga" to "tidak",
        "enggak" to "tidak", "engga" to "tidak", "tdk" to "tidak", "kagak" to "tidak",
        "gapapa" to "tidak apa-apa", "gpp" to "tidak apa-apa", "nggapapa" to "tidak apa-apa",
        // Places
        "dimana" to "di mana", "dmn" to "di mana", "kemana" to "ke mana", "kmn" to "ke mana",
        "darimana" to "dari mana", "otw" to "dalam perjalanan",
        // Questions
        "gimana" to "bagaimana", "gmn" to "bagaimana", "gmna" to "bagaimana",
        "kenapa" to "mengapa", "knp" to "mengapa", "ngapain" to "sedang apa", "apaan" to "apa",
        // Connectors and common words
        "yg" to "yang", "dgn" to "dengan", "utk" to "untuk", "tp" to "tapi", "tpi" to "tapi",
        "krn" to "karena", "karna" to "karena", "sm" to "sama", "jg" to "juga",
        "aja" to "saja", "aj" to "saja", "doang" to "saja", "cuma" to "hanya", "cuman" to "hanya",
        "bgt" to "sekali", "banget" to "sekali", "kayak" to "seperti", "kyk" to "seperti", "kek" to "seperti",
        "emang" to "memang", "emg" to "memang", "kalo" to "kalau", "klo" to "kalau", "kl" to "kalau",
        "pengen" to "ingin", "pgn" to "ingin", "pingin" to "ingin", "mo" to "mau",
        "tau" to "tahu", "bener" to "benar", "bnr" to "benar", "gede" to "besar", "dikit" to "sedikit",
        "liat" to "lihat", "nyampe" to "sampai", "sampe" to "sampai", "smp" to "sampai",
        "gitu" to "begitu", "gini" to "begini", "tuh" to "itu", "mulu" to "terus", "trs" to "terus",
        "bs" to "bisa", "org" to "orang", "dr" to "dari", "kpn" to "kapan",
        "makasih" to "terima kasih", "makasi" to "terima kasih", "mksh" to "terima kasih",
        "thx" to "terima kasih", "maaf" to "maaf", "sori" to "maaf", "sorry" to "maaf",
        // Javanese, as mixed into chat. Words that also mean something else in Indonesian are left out.
        "piye" to "bagaimana", "pripun" to "bagaimana", "kabare" to "kabarnya",
        "wis" to "sudah", "uwis" to "sudah", "durung" to "belum", "isih" to "masih",
        "ora" to "tidak", "ndak" to "tidak", "rapopo" to "tidak apa-apa", "gapopo" to "tidak apa-apa",
        "opo" to "apa", "sopo" to "siapa", "popo" to "apa-apa",
        "iki" to "ini", "kuwi" to "itu", "saiki" to "sekarang", "mengko" to "nanti", "sesuk" to "besok",
        "wingi" to "kemarin", "kowe" to "kamu", "awakmu" to "kamu", "sampeyan" to "Anda", "dewe" to "sendiri",
        "karo" to "dengan", "wae" to "saja", "bae" to "saja", "arep" to "mau", "jare" to "katanya",
        "pancen" to "memang", "tenan" to "sungguh", "ngerti" to "mengerti", "apik" to "bagus",
        "akeh" to "banyak", "sithik" to "sedikit", "sitik" to "sedikit",
        "mangan" to "makan", "turu" to "tidur", "ngombe" to "minum", "mulih" to "pulang", "lunga" to "pergi",
        "nggih" to "iya", "inggih" to "iya", "suwun" to "terima kasih", "nuwun" to "terima kasih",
        "monggo" to "silakan",
        // Sundanese, as mixed into chat.
        "kumaha" to "bagaimana", "damang" to "sehat", "naon" to "apa", "saha" to "siapa", "kamana" to "ke mana",
        "abdi" to "saya", "maneh" to "kamu", "anjeun" to "kamu", "teu" to "tidak", "henteu" to "tidak",
        "acan" to "belum", "atos" to "sudah", "parantos" to "sudah", "geus" to "sudah", "keur" to "sedang",
        "hayang" to "ingin", "ayeuna" to "sekarang", "engke" to "nanti", "isukan" to "besok", "kamari" to "kemarin",
        "dahar" to "makan", "sare" to "tidur", "nuhun" to "terima kasih", "pisan" to "sekali", "bade" to "mau",
        "ieu" to "ini", "dieu" to "sini", "ditu" to "sana", "jeung" to "dan", "sareng" to "dengan",
        "geulis" to "cantik", "kasep" to "tampan", "lieur" to "pusing",
        // Particles that add tone but confuse the translator
        "sih" to "", "dong" to "", "deh" to "", "kok" to "", "nih" to "", "lho" to "", "loh" to "",
        "kan" to "", "yah" to "", "tah" to "", "rek" to "", "atuh" to "", "euy" to "",
    )

    private val wordPattern = Regex("\\p{L}+")

    private val phrasePattern = Regex(
        phrases.keys.sortedByDescending { it.length }.joinToString("|") { "(?<![\\p{L}])" + Regex.escape(it) + "(?![\\p{L}])" },
        RegexOption.IGNORE_CASE,
    )

    fun normalize(text: String): String {
        val withPhrases = phrasePattern.replace(text) { match ->
            val replacement = phrases.getValue(match.value.lowercase())
            if (match.value[0].isUpperCase()) replacement.replaceFirstChar { it.uppercase() } else replacement
        }
        val replaced = wordPattern.replace(withPhrases) { match ->
            val word = match.value
            val replacement = words[word.lowercase()] ?: return@replace word
            if (replacement.isNotEmpty() && word[0].isUpperCase()) {
                replacement.replaceFirstChar { it.uppercase() }
            } else {
                replacement
            }
        }
        // Tidy the spaces left behind by dropped particles, e.g. "ayo dong ." -> "ayo."
        return replaced
            .replace(Regex(" {2,}"), " ")
            .replace(Regex(" +([?!.,])"), "$1")
            .trim()
    }
}
