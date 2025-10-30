package com.example.clipmon2

object SensitiveDetector {
    // 简化：大陆手机号 1[3-9] + 9 位；根据需要可扩展更多地区
    private val phoneCN = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""")

    // 将文本中的数字提取，用 Luhn 算法校验银行卡（13-19 位）
    fun containsSensitive(s: String): Boolean = isPhone(s) || isCard(s)

    fun isPhone(s: String): Boolean =
        phoneCN.containsMatchIn(s.replace(" ", "").replace("-", ""))

    fun isCard(s: String): Boolean {
        val digits = s.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        return luhn(digits)
    }

    private fun luhn(num: String): Boolean {
        var sum = 0
        var alt = false
        for (i in num.length - 1 downTo 0) {
            var n = num[i] - '0'
            if (alt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alt = !alt
        }
        return sum % 10 == 0
    }

    // 将文本中的手机号/卡号片段替换为打码版本
    fun maskWithin(s: String): String {
        // 先打码手机号
        var out = phoneCN.replace(s) { m ->
            maskNumber(m.value)
        }
        // 再打码疑似卡号（对每个连续数字段尝试 Luhn）
        val numChunk = Regex("""\d{13,19}""")
        out = numChunk.replace(out) { m ->
            if (luhn(m.value)) maskNumber(m.value) else m.value
        }
        return out
    }

    private fun maskNumber(num: String): String {
        val clean = num.filter { it.isDigit() }
        val n = clean.length
        if (n <= 6) return "*".repeat(n)
        val head = clean.take(3)
        val tail = clean.takeLast(4)
        val masked = head + "*".repeat(n - 7) + tail
        // 尽量保留原格式的空格/连字符，这里做简化直接无分隔返回
        return masked
    }
}
