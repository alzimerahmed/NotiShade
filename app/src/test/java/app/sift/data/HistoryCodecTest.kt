package app.sift.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCodecTest {
    private fun entry(
        title: String = "Hello",
        text: String = "World",
        app: String = "Mail",
        outcome: Outcome = Outcome.BLOCKED,
        reason: String? = null,
    ) = HistoryEntry(
        time = 1_700_000_000_000,
        key = "key-1",
        pkg = "com.example.mail",
        app = app,
        channelId = "promo",
        channelName = "Promotions",
        category = Category.PROMO,
        title = title,
        text = text,
        outcome = outcome,
        reason = reason,
    )

    @Test
    fun `csv empty list is just the header`() {
        val csv = HistoryCodec.encodeCsv(emptyList())
        assertEquals(
            "time,key,package,app,channelId,channelName,category,title,text,outcome,reason",
            csv.trim().lines().single(),
        )
    }

    /** The data row after the header; may itself contain newlines inside quoted fields. */
    private fun dataRow(csv: String) = csv.substringAfter("\r\n").trimEnd()

    @Test
    fun `csv row matches field order and epoch time becomes ISO instant`() {
        val fields = dataRow(HistoryCodec.encodeCsv(listOf(entry()))).split(",")

        assertEquals("2023-11-14T22:13:20Z", fields[0])
        assertEquals("key-1", fields[1])
        assertEquals("com.example.mail", fields[2])
        assertEquals("Mail", fields[3])
        assertEquals("promo", fields[4])
        assertEquals("Promotions", fields[5])
        assertEquals("PROMO", fields[6])
        assertEquals("Hello", fields[7])
        assertEquals("World", fields[8])
        assertEquals("BLOCKED", fields[9])
        assertEquals("", fields[10])
    }

    @Test
    fun `csv quotes fields with commas quotes and newlines`() {
        val row = dataRow(HistoryCodec.encodeCsv(listOf(entry(title = "a,b", text = "say \"hi\"\nbye"))))
        assertTrue(row.contains("\"a,b\""))
        assertTrue(row.contains("\"say \"\"hi\"\"\nbye\""))
    }

    @Test
    fun `csv quoting survives carriage returns and trailing commas`() {
        val tricky = "line1\r\nline2, with \"quotes\" and, commas"
        val row = dataRow(HistoryCodec.encodeCsv(listOf(entry(text = tricky))))
        assertTrue(row.startsWith("2023-11-14T22:13:20Z,key-1,com.example.mail,Mail,promo,Promotions,PROMO,Hello,"))
        assertTrue(row.endsWith("\"line1\r\nline2, with \"\"quotes\"\" and, commas\",BLOCKED,"))
    }

    @Test
    fun `json round trip preserves entries`() {
        val entries = listOf(entry(reason = "Deals"), entry(outcome = Outcome.SHOWN, title = "x,y"))
        val json = HistoryCodec.encodeJson(entries)
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(HistoryEntry.serializer()), json)
        assertEquals(entries, decoded)
    }

    @Test
    fun `json empty list is an empty array`() {
        assertEquals("[]", HistoryCodec.encodeJson(emptyList()))
    }
}
