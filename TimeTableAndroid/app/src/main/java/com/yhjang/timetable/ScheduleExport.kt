package com.yhjang.timetable

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 학사일정을 폰 캘린더로 — 오늘 이후 학사·하나 일정을 iCalendar(.ics) 파일로 만들어 캘린더 앱에 넘깁니다(가져오기 화면이 뜸).
 * 면학감독·시설사용은 학생 개인 일정이 아니라 빼고, 같은 날 같은 이름은 한 번만. 시각이 있으면 그 시각, 없으면 종일 일정.
 */
object ScheduleExport {
    private val day = DateTimeFormatter.BASIC_ISO_DATE
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun share(context: Context, entries: List<HanaScheduleEntry>): Boolean {
        val today = PlanStore.today()
        val events = entries
            .filter { !it.date.isBefore(today) && it.code != SUPERVISION_CODE && it.code != "SG02020000" }
            .distinctBy { it.date to it.name.trim() }
            .sortedBy { it.date }
        if (events.isEmpty()) return false
        val now = ZonedDateTime.now(PlanStore.seoulZone).withZoneSameInstant(java.time.ZoneOffset.UTC).format(stamp) + "Z"
        val text = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//hihana-neo//schedule//KO")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("X-WR-CALNAME:하나고 학사일정")
            events.forEach { e ->
                appendLine("BEGIN:VEVENT")
                appendLine("UID:hihana-${e.date.format(day)}-${e.name.hashCode().toUInt()}@hihana-neo")
                appendLine("DTSTAMP:$now")
                val start = time(e.date, e.startTime)
                val end = time(e.date, e.endTime)
                if (e.allDay || start == null) {
                    appendLine("DTSTART;VALUE=DATE:${e.date.format(day)}")
                    appendLine("DTEND;VALUE=DATE:${e.date.plusDays(1).format(day)}")
                } else {
                    appendLine("DTSTART;TZID=Asia/Seoul:${start.format(stamp)}")
                    appendLine("DTEND;TZID=Asia/Seoul:${(end?.takeIf { it.isAfter(start) } ?: start.plusHours(1)).format(stamp)}")
                }
                appendLine("SUMMARY:${escape(e.name.trim())}")
                e.place?.takeIf { it.isNotBlank() }?.let { appendLine("LOCATION:${escape(it)}") }
                appendLine("END:VEVENT")
            }
            appendLine("END:VCALENDAR")
        }.replace("\n", "\r\n")
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "hihana-schedule.ics").apply { writeText(text) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val chooser = Intent.createChooser(view, "캘린더에 학사일정 추가").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    /** "2026-10-20 17:30" 이나 "17:30" 에서 그날 시각을 뽑습니다. */
    private fun time(date: LocalDate, text: String?): LocalDateTime? {
        val hm = text?.trim()?.substringAfter(' ')?.takeIf { Regex("""\d{1,2}:\d{2}""").matches(it) } ?: return null
        val (h, m) = hm.split(':').map { it.toInt() }
        return date.atTime(h.coerceIn(0, 23), m.coerceIn(0, 59))
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
