package org.bibletranslationtools.kotlinscripturealignment

import java.util.regex.Pattern


val CUE_HEADER_PATTERN: Pattern = Pattern.compile("^(\\S+)\\s+-->\\s+(\\S+)(.*)?$")
private val CUE_SETTING_PATTERN: Pattern = Pattern.compile("(\\S+?):(\\S+)")

data class BurritoCue(
    var startTimeUs: Int,
    var endTimeUs: Int,
)

/**
 * Parses a WebVTT timestamp.
 *
 * @param timestamp The timestamp string.
 * @return The parsed timestamp in microseconds.
 * @throws NumberFormatException If the timestamp could not be parsed.
 */
@Throws(java.lang.NumberFormatException::class)
fun parseTimestampUs(timestamp: String): Long {
    var value: Long = 0
    val parts: Array<String> = splitAtFirst(timestamp, "\\.")
    val subparts: Array<String> = split(parts[0], ":")
    for (subpart in subparts) {
        value = (value * 60) + subpart.toLong()
    }
    value *= 1000
    if (parts.size == 2) {
        value += parts[1].toLong()
    }
    return value * 1000
}

class Cues {

    private fun timestamp(timeUs: Long): String {
        val hours = (timeUs / (3600L * 1_000_000)).toInt()
        val minutes = ((timeUs % (3600L * 1_000_000)) / (60L * 1_000_000)).toInt()
        val seconds = ((timeUs % (60L * 1_000_000)) / 1_000_000).toInt()


        val microseconds = (timeUs % 1_000_000).toInt() / 1000

        return "%03d:%02d:%02d.%03d".format(hours, minutes, seconds, microseconds)
    }
}

fun isLinebreak(c: Int): Boolean {
    return c == '\n'.code || c == '\r'.code
}

fun fromUtf8Bytes(bytes: ByteArray): String {
    return String(bytes, Charsets.UTF_8)
}

fun fromUtf8Bytes(bytes: ByteArray, offset: Int, length: Int): String {
    return String(bytes, offset, length, Charsets.UTF_8)
}

fun splitAtFirst(value: String, regex: String): Array<String> {
    return value.split(regex.toRegex(), limit = 2).toTypedArray()
}

fun split(value: String, regex: String): Array<String> {
    return value.split(regex.toRegex()).toTypedArray()
}