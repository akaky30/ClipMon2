package com.example.clipmon2

/**
 * 输入敏感信息检测与打码
 * - 手机号：大陆 11 位，以 1[3-9] 开头
 * - 身份证：18 位，包含出生日期格式校验，末位可为 X/x
 * - 银行卡：19 位纯数字
 */
object SensitiveDetector {

    // 11位手机号（更严格：1[3-9] 开头）
    private val REGEX_PHONE11 = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")

    // 18位二代身份证（地区码6 + 年4 + 月2 + 日2 + 顺序码3 + 校验位），简单格式校验
    private val REGEX_ID18 = Regex(
        "(?<!\\d)" +
                "[1-9]\\d{5}" +                 // 地区码
                "(18|19|20)\\d{2}" +            // 年：1800-2099
                "(0[1-9]|1[0-2])" +             // 月：01-12
                "(0[1-9]|[12]\\d|3[01])" +      // 日：01-31
                "\\d{3}" +                      // 顺序码
                "[\\dXx]" +                     // 校验位
                "(?!\\d)"
    )

    // 19位银行卡号（按你的需求限定为 19 位；如需 16-19 可改为 \\d{16,19}）
    private val REGEX_BANK19 = Regex("(?<!\\d)\\d{19}(?!\\d)")

    /** 单项判断 */
    fun containsPhone11(text: String) = REGEX_PHONE11.containsMatchIn(text)
    fun containsId18(text: String) = REGEX_ID18.containsMatchIn(text)
    fun containsBank19(text: String) = REGEX_BANK19.containsMatchIn(text)

    /** 组合判断（输入时提醒用它即可） */
    fun containsSensitive(text: String): Boolean =
        containsPhone11(text) || containsId18(text) || containsBank19(text)

    /** 将匹配到的片段打码替换 */
    fun maskWithin(text: String): String {
        var s = text
        // 手机号：保留 3+4
        s = s.replace(REGEX_PHONE11) { maskRun(it.value, keepLeft = 3, keepRight = 4) }
        // 身份证：保留 3+2（可按需调整为 6+4 等）
        s = s.replace(REGEX_ID18) { maskRun(it.value, keepLeft = 3, keepRight = 2) }
        // 银行卡：保留 6+4（常见展示方式）
        s = s.replace(REGEX_BANK19) { maskRun(it.value, keepLeft = 6, keepRight = 4) }
        return s
    }

    private fun maskRun(run: String, keepLeft: Int, keepRight: Int): String {
        val n = run.length
        if (n <= keepLeft + keepRight) return "*".repeat(n)
        val mid = n - keepLeft - keepRight
        return run.take(keepLeft) + "*".repeat(mid) + run.takeLast(keepRight)
    }
}
