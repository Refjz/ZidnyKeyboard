package com.zidny.keyboard

/**
 * Rewrites informal Indonesian chat language ("kamu udah dimana?") into standard
 * Indonesian ("kamu sudah di mana?") before translating, because the on-device
 * translator was trained on formal text and mistranslates slang.
 */
object SlangNormalizer {

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
        // Particles that add tone but confuse the translator
        "sih" to "", "dong" to "", "deh" to "", "kok" to "", "nih" to "", "lho" to "", "loh" to "",
        "kan" to "", "yah" to "", "tah" to "",
    )

    private val wordPattern = Regex("\\p{L}+")

    fun normalize(text: String): String {
        val replaced = wordPattern.replace(text) { match ->
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
