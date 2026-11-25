package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodalFilingSearchParserTest {
    @Test
    fun parsesSupportedArrayAndKeepsOnlyExactNormalizedSymbol() {
        val json = """
            {
              "TotalCount": 9,
              "Letters": [
                {
                  "Title": "صورت‌های مالی سالانه",
                  "Symbol": "نماد",
                  "PublishDateTime": "1404/07/01",
                  "HtmlUrl": "/Reports/123",
                  "ExcelUrl": "https://excel.codal.ir/Reports/123.xls"
                },
                {
                  "Title": "اطلاعیه نماد دیگر",
                  "Symbol": "نماد۲",
                  "HtmlUrl": "https://www.codal.ir/Reports/999"
                },
                {
                  "Title": "بدون نماد",
                  "PublishDate": "1404/06/01",
                  "HtmlUrl": "https://evil.example/report"
                }
              ]
            }
        """.trimIndent()

        val parsed = CodalFilingSearchParser.parse(json, "نماد")!!

        assertEquals(9, parsed.filingsFound)
        assertEquals(listOf("صورت‌های مالی سالانه", "بدون نماد"), parsed.recentFilingTitles)
        assertEquals(2, parsed.recentFilings.size)
        assertEquals("صورت‌های مالی", parsed.recentFilings[0].category)
        assertEquals("https://www.codal.ir/Reports/123", parsed.recentFilings[0].url)
        assertEquals("https://excel.codal.ir/Reports/123.xls", parsed.recentFilings[0].excelUrl)
        assertNull(parsed.recentFilings[1].url)
    }

    @Test
    fun supportsAlternateArrayAndFieldNames() {
        val json = """
            {
              "total": 1,
              "items": [
                {
                  "letterTitle": "گزارش فعالیت ماهانه",
                  "ticker": "شرکت",
                  "sentDateTime": "1404/05/01",
                  "link": "https://codal.ir/Reports/1"
                }
              ]
            }
        """.trimIndent()

        val parsed = CodalFilingSearchParser.parse(json, "شرکت")!!

        assertEquals(1, parsed.filingsFound)
        assertEquals("گزارش فعالیت ماهانه", parsed.recentFilings.single().title)
        assertEquals("گزارش فعالیت ماهانه", parsed.recentFilings.single().category)
        assertEquals("1404/05/01", parsed.recentFilings.single().publishDate)
    }

    @Test
    fun returnsNullForUnknownResponseShapeAndEmptySnapshotForKnownEmptyArray() {
        assertNull(CodalFilingSearchParser.parse("""{"result":[]}""", "نماد"))

        val empty = CodalFilingSearchParser.parse("""{"Letters":[],"Total":0}""", "نماد")!!
        assertEquals(0, empty.filingsFound)
        assertEquals(emptyList<String>(), empty.recentFilingTitles)
        assertEquals(emptyList<CodalFilingReference>(), empty.recentFilings)
    }

    @Test
    fun rejectsNegativeReportedTotalAndUsesActualRowCount() {
        val parsed = CodalFilingSearchParser.parse(
            """{"Letters":[{"Title":"اطلاعیه","Symbol":"نماد"}],"Total":-5}""",
            "نماد"
        )!!
        assertEquals(1, parsed.filingsFound)
    }
}
