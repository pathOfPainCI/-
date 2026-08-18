package com.jizhang.app.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CsvUtilTest {

    @Test
    fun normalDateTimeParsed() {
        val ms = CsvUtil.parseDateTime("2026-01-15 12:30:45")
        assertNotNull(ms)
        val dt = java.time.Instant.ofEpochMilli(ms!!)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        assertEquals(2026, dt.year)
        assertEquals(1, dt.monthValue)
        assertEquals(15, dt.dayOfMonth)
        assertEquals(12, dt.hour)
    }

    @Test
    fun excelSerialDateParsed() {
        // 46000 ≈ 2025-12-11（1899-12-30 起 46000 天）；0.5 = 中午 12 点
        val ms = CsvUtil.parseDateTime("46000.5")
        assertNotNull(ms)
        val dt = java.time.Instant.ofEpochMilli(ms!!)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        assertEquals(2025, dt.year)
        assertEquals(12, dt.hour)
    }

    @Test
    fun smallNumberNotTreatedAsSerial() {
        // 金额类数字不应被当作日期
        assertNull(CsvUtil.parseDateTime("15.00"))
    }

    @Test
    fun invalidDateReturnsNull() {
        assertNull(CsvUtil.parseDateTime("不是日期"))
        assertNull(CsvUtil.parseDateTime(""))
    }
}
