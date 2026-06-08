package com.smartclock.util

import java.nio.charset.Charset

object LegacyTextSanitizer {

    private val gb18030: Charset = Charset.forName("GB18030")

    private val suspiciousTokens = listOf(
        "鍚",
        "璁",
        "鏈€",
        "鎻",
        "闂",
        "鏉冮檺",
        "鐧诲綍",
        "绔嬪嵆",
        "鍚屾",
        "鏃ュ織",
        "鍗庝负",
        "鏈満",
        "浜戠"
    )

    private val readableSignals = listOf(
        "同步",
        "登录",
        "注册",
        "权限",
        "提醒",
        "日志",
        "闹钟",
        "云端",
        "本机",
        "网络",
        "成功",
        "失败",
        "用户",
        "账号",
        "系统",
        "设置",
        "通知",
        "时间"
    )

    fun sanitize(text: String?): String? {
        if (text.isNullOrBlank()) return text
        val raw = text.trim()
        if (!looksMojibake(raw)) return raw

        val repaired = runCatching {
            String(raw.toByteArray(gb18030), Charsets.UTF_8)
        }.getOrDefault(raw)

        return if (shouldUseRepaired(raw, repaired)) repaired else raw
    }

    private fun looksMojibake(text: String): Boolean =
        text.contains('\uFFFD') || suspiciousTokens.any(text::contains)

    private fun shouldUseRepaired(original: String, repaired: String): Boolean {
        if (repaired.isBlank() || repaired == original) return false
        val originalSuspicious = suspiciousScore(original)
        val repairedSuspicious = suspiciousScore(repaired)
        val originalReadable = readableScore(original)
        val repairedReadable = readableScore(repaired)
        return repairedSuspicious < originalSuspicious || repairedReadable > originalReadable
    }

    private fun suspiciousScore(text: String): Int =
        suspiciousTokens.count(text::contains) + text.count { it == '\uFFFD' } * 2

    private fun readableScore(text: String): Int =
        readableSignals.count(text::contains)
}
