package com.attaproductions.secretary

import java.util.Calendar
import java.util.Locale

object SpokenCommandParser {
    data class Parsed(val title: String, val dueAt: Long?, val hasTemporal: Boolean)

    private val numberWords = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "два" to 2, "две" to 2, "три" to 3,
        "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9,
        "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14,
        "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19,
        "двадцать" to 20, "двадцать один" to 21, "двадцать два" to 22, "двадцать три" to 23,
        "нуль" to 0, "одна" to 1, "дві" to 2, "три" to 3, "чотири" to 4, "п'ять" to 5, "шість" to 6,
        "сім" to 7, "вісім" to 8, "дев'ять" to 9, "десять" to 10, "одинадцять" to 11, "дванадцять" to 12
    )

    fun parse(textRaw: String, now: Long = System.currentTimeMillis()): Parsed {
        var text = textRaw.trim().lowercase(Locale.getDefault())
        val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        var hasTemporal = false

        // Relative durations: через 15 минут / через два часа / через 3 дня
        val rel = Regex("через\\s+([\\p{L}'’ -]+|\\d+)\\s*(минут[уы]?|мин|час(?:а|ов)?|дн(?:я|ей)?|хвилин(?:у|и)?|годин(?:у|и)?|дн(?:і|я|ів)?)").find(text)
        if (rel != null) {
            val n = parseNumber(rel.groupValues[1].trim()) ?: 1
            val unit = rel.groupValues[2]
            val millis = when {
                unit.startsWith("мин") || unit.startsWith("хвилин") -> n * 60_000L
                unit.startsWith("час") || unit.startsWith("годин") -> n * 3_600_000L
                else -> n * 86_400_000L
            }
            val title = cleanTitle(text.removeRange(rel.range))
            return Parsed(title.ifBlank { "Напоминание" }, now + millis, true)
        }

        when {
            Regex("\\b(послезавтра|післязавтра)\\b").containsMatchIn(text) -> { cal.add(Calendar.DAY_OF_YEAR, 2); hasTemporal = true; text = text.replace(Regex("\\b(послезавтра|післязавтра)\\b"), " ") }
            Regex("\\b(завтра)\\b").containsMatchIn(text) -> { cal.add(Calendar.DAY_OF_YEAR, 1); hasTemporal = true; text = text.replace(Regex("\\bзавтра\\b"), " ") }
            Regex("\\b(сегодня|сьогодні)\\b").containsMatchIn(text) -> { hasTemporal = true; text = text.replace(Regex("\\b(сегодня|сьогодні)\\b"), " ") }
        }

        // Explicit time: в 10, в 10:30, о 9:15, "в десять"
        var hour: Int? = null
        var minute = 0
        val numericTime = Regex("(?:\\bв\\b|\\bо\\b)?\\s*(?:в\\s*)?(\\d{1,2})(?:[:.]([0-5]\\d))?(?:\\s*(?:час(?:а|ов)?|годин(?:а|і)?))?").findAll(text)
            .firstOrNull { m ->
                val h = m.groupValues[1].toIntOrNull()
                h != null && h in 0..23 && (m.groupValues[2].isNotBlank() || text.substring(maxOf(0, m.range.first - 3), minOf(text.length, m.range.last + 2)).contains("в") || text.substring(maxOf(0, m.range.first - 3), minOf(text.length, m.range.last + 2)).contains("о"))
            }
        if (numericTime != null) {
            hour = numericTime.groupValues[1].toInt()
            minute = numericTime.groupValues[2].toIntOrNull() ?: 0
            text = text.removeRange(numericTime.range)
            hasTemporal = true
        } else {
            val wordTime = Regex("(?:\\bв\\b|\\bо\\b)\\s+([\\p{L}'’ ]+?)(?=\\s+(?:утра|утром|вечера|вечером|дня|ночью|ранку|вранці|вечора|увечері|дня|ночі)\\b|$)").find(text)
            if (wordTime != null) {
                parseNumber(wordTime.groupValues[1].trim())?.let { if (it in 0..23) { hour = it; hasTemporal = true; text = text.removeRange(wordTime.range) } }
            }
        }

        // Dayparts adjust ambiguous hours.
        val evening = Regex("\\b(вечером|вечера|увечері|вечора)\\b").containsMatchIn(text)
        val morning = Regex("\\b(утром|утра|вранці|ранку)\\b").containsMatchIn(text)
        val day = Regex("\\b(днем|дня)\\b").containsMatchIn(text)
        val night = Regex("\\b(ночью|ночі)\\b").containsMatchIn(text)
        if (evening || morning || day || night) {
            hasTemporal = true
            text = text.replace(Regex("\\b(вечером|вечера|увечері|вечора|утром|утра|вранці|ранку|днем|дня|ночью|ночі)\\b"), " ")
        }
        if (hour != null) {
            if (evening && hour in 1..11) hour += 12
            if (day && hour in 1..5) hour += 12
            if (night && hour == 12) hour = 0
            cal.set(Calendar.HOUR_OF_DAY, hour!!)
            cal.set(Calendar.MINUTE, minute)
        } else if (hasTemporal) {
            when {
                morning -> { cal.set(Calendar.HOUR_OF_DAY, 9); cal.set(Calendar.MINUTE, 0) }
                day -> { cal.set(Calendar.HOUR_OF_DAY, 14); cal.set(Calendar.MINUTE, 0) }
                evening -> { cal.set(Calendar.HOUR_OF_DAY, 19); cal.set(Calendar.MINUTE, 0) }
                night -> { cal.set(Calendar.HOUR_OF_DAY, 22); cal.set(Calendar.MINUTE, 0) }
                else -> { cal.set(Calendar.HOUR_OF_DAY, 9); cal.set(Calendar.MINUTE, 0) }
            }
        }

        if (hasTemporal && cal.timeInMillis <= now) {
            // "сегодня в 10" in the past should not silently fire immediately. If no explicit today marker, roll to tomorrow.
            if (!Regex("\\b(сегодня|сьогодні)\\b").containsMatchIn(textRaw.lowercase(Locale.getDefault()))) cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val title = cleanTitle(text)
        return Parsed(title.ifBlank { "Напоминание" }, if (hasTemporal) cal.timeInMillis else null, hasTemporal)
    }

    fun parseTimeOnly(textRaw: String, now: Long = System.currentTimeMillis()): Long? = parse("напомнить $textRaw", now).dueAt

    private fun parseNumber(s: String): Int? {
        val normalized = s.trim().replace("ё", "е").replace("’", "'")
        normalized.toIntOrNull()?.let { return it }
        numberWords[normalized]?.let { return it }
        for ((k, v) in numberWords.entries.sortedByDescending { it.key.length }) if (normalized == k) return v
        return null
    }

    private fun cleanTitle(s: String): String = s
        .replace(Regex("\\b(напомни|напомнить|нагадай|нагадати|мне|мені|пожалуйста|будь ласка)\\b"), " ")
        .replace(Regex("\\s+"), " ").trim(' ', ',', '.', '-')
}
