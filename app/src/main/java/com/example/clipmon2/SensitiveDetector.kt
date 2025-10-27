package com.example.clipmon2

object SensitiveDetector {
    private val phone = Regex("(?<!\\d)(1[3-9]\\d{9})(?!\\d)")
    private val idcn  = Regex("(?i)\\b[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])" +
            "(0[1-9]|[12]\\d|3[01])\\d{3}[0-9xX]\\b")

    fun analyze(text: String): Triple<Boolean, Boolean, String> {
        val hasPhone = phone.containsMatchIn(text)
        val hasId = idcn.containsMatchIn(text)
        var masked = text.replace(phone) { m -> m.value.replaceRange(3, 7, "****") }
        masked = masked.replace(idcn) { m ->
            val v = m.value
            if (v.length >= 14) v.replaceRange(6, 14, "********") else v
        }
        return Triple(hasPhone, hasId, masked)
    }
}
