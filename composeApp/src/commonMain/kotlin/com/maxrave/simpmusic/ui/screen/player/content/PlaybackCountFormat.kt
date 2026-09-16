package com.maxrave.simpmusic.ui.screen.player.content

/**
 * Matches YouTube's subscriber-count presentation: three significant digits at most, followed by
 * K/M/B. Engagement figures stay full-width elsewhere, just like YouTube's view/like rows.
 */
internal fun Long.toPlaybackCompactCount(): String {
    val (divisor, suffix) =
        when {
            this >= 999_500_000L -> 1_000_000_000L to "B"
            this >= 999_500L -> 1_000_000L to "M"
            this >= 1_000L -> 1_000L to "K"
            else -> return toString()
        }
    val number =
        when {
            this >= divisor * 100L -> ((this + divisor / 2L) / divisor).toString()
            this >= divisor * 10L -> {
                val tenths = ((this * 10L) + divisor / 2L) / divisor
                if (tenths % 10L == 0L) {
                    (tenths / 10L).toString()
                } else {
                    "${tenths / 10L}.${tenths % 10L}"
                }
            }
            else -> {
                val hundredths = ((this * 100L) + divisor / 2L) / divisor
                val fraction = (hundredths % 100L).toString().padStart(2, '0').trimEnd('0')
                if (fraction.isEmpty()) {
                    (hundredths / 100L).toString()
                } else {
                    "${hundredths / 100L}.$fraction"
                }
            }
        }
    return "$number$suffix"
}
