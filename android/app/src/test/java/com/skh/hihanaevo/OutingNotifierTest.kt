package com.skh.hihanaevo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class OutingNotifierTest {
    @Test fun sameDayShowsEndTimeOnly() {
        assertEquals("11.24(화) 18:00 ~ 21:00",
            OutingNotifier.span(LocalDateTime.of(2026, 11, 24, 18, 0), LocalDateTime.of(2026, 11, 24, 21, 0)))
    }

    @Test fun overnightShowsBothDates() {
        assertEquals("11.27(금) 17:50 ~ 11.29(일) 21:00",
            OutingNotifier.span(LocalDateTime.of(2026, 11, 27, 17, 50), LocalDateTime.of(2026, 11, 29, 21, 0)))
    }
}
