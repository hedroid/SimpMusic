package com.maxrave.simpmusic.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale

/**
 * Compact engagement counts, the way YouTube prints them: at most three significant digits
 * followed by a magnitude unit, raw below the first unit's threshold.
 *
 * The unit ladder follows the app language. Myriad locales (zh / ja / ko) group by
 * ten-thousand — "1.2万", "3406万", "3.4亿" — starting at 10_000, matching YouTube's
 * Chinese/Japanese/Korean presentation. Every other language uses K/M/B starting at 1_000.
 *
 * Kept as finished-string output for the same reason as [formatCount] in WrappedFormat:
 * Compose Resources understands `%1$s` and nothing else, so string resources only ever
 * join pieces that are already formatted.
 */
@Composable
fun formatCompactCount(value: Long): String = Locale.current.let { formatCompactCount(value, it.language, it.script, it.region) }

@Composable
fun formatCompactCount(value: Int): String = formatCompactCount(value.toLong())

fun formatCompactCount(
    value: Long,
    language: String,
    script: String = "",
    region: String = "",
): String {
    val tiers = compactTiersFor(language, script, region)
    if (value < tiers.first().first) return value.toString()
    // A value close enough to the next tier that rounding would print "1000万" instead
    // of "1亿" jumps tiers up front, so the significand logic rounds to exactly "1".
    var index = 0
    while (index < tiers.size - 1 && value >= tiers[index + 1].first - tiers[index].first / 2L) index++
    val (divisor, suffix) = tiers[index]
    return compactSignificands(value, divisor) + suffix
}

/**
 * 万 vs 萬 depends on the Chinese variant, so all three locale pieces are needed for zh.
 */
private fun compactTiersFor(
    language: String,
    script: String,
    region: String,
): List<Pair<Long, String>> =
    when (language.lowercase()) {
        "zh" ->
            if (script.equals("Hant", true) || region.uppercase() in setOf("TW", "HK", "MO")) {
                listOf(10_000L to "萬", 100_000_000L to "億")
            } else {
                listOf(10_000L to "万", 100_000_000L to "亿")
            }
        "ja" -> listOf(10_000L to "万", 100_000_000L to "億")
        "ko" -> listOf(10_000L to "만", 100_000_000L to "억")
        else -> listOf(1_000L to "K", 1_000_000L to "M", 1_000_000_000L to "B")
    }

/**
 * The scaled number in front of the unit: an integer once three digits are enough ("123万"),
 * one decimal ("12.3万") or two ("1.23万") below that, trailing zeros stripped ("1.2万",
 * never "1.20万"). Round-half-up, like YouTube.
 */
private fun compactSignificands(
    value: Long,
    divisor: Long,
): String =
    when {
        value >= divisor * 100L -> ((value + divisor / 2L) / divisor).toString()
        value >= divisor * 10L -> {
            val tenths = ((value * 10L) + divisor / 2L) / divisor
            if (tenths % 10L == 0L) {
                (tenths / 10L).toString()
            } else {
                "${tenths / 10L}.${tenths % 10L}"
            }
        }
        else -> {
            val hundredths = ((value * 100L) + divisor / 2L) / divisor
            val fraction = (hundredths % 100L).toString().padStart(2, '0').trimEnd('0')
            if (fraction.isEmpty()) {
                (hundredths / 100L).toString()
            } else {
                "${hundredths / 100L}.$fraction"
            }
        }
    }
