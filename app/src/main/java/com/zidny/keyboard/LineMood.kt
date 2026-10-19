package com.zidny.keyboard

/** What kind of ready-to-send line the ✨ AI panel writes. [code] is what the server expects. */
enum class LineMood(val code: String, val label: String) {
    ROMANTIC("romantic", "💘 Gombal"),
    FUNNY("funny", "😂 Lucu"),
    ROAST("roast", "🔥 Roasting"),
    MOTIVATE("motivate", "💪 Motivasi"),
    SORRY("sorry", "🙏 Minta maaf"),
    BIRTHDAY("birthday", "🎂 Ucapan ultah"),
}
