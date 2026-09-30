package app.sift.data

import java.time.Instant
import kotlinx.serialization.builtins.ListSerializer

/**
 * History export formats (Gap #6). Pure string encoding — the caller writes the result
 * wherever it wants (SAF, share sheet). CSV follows RFC 4180: fields containing commas,
 * quotes, newlines or carriage returns are quoted, and embedded quotes are doubled.
 */
object HistoryCodec {
    private val json = kotlinx.serialization.json.Json

    val csvHeader = listOf("time", "key", "package", "app", "channelId", "channelName", "category", "title", "text", "outcome", "reason")

    fun encodeCsv(entries: List<HistoryEntry>): String =
        (listOf(csvHeader) + entries.map { it.csvRow() }).joinToString("\r\n") { row ->
            row.joinToString(",") { field ->
                if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                    "\"" + field.replace("\"", "\"\"") + "\""
                } else {
                    field
                }
            }
        } + "\r\n"

    fun encodeJson(entries: List<HistoryEntry>): String =
        json.encodeToString(ListSerializer(HistoryEntry.serializer()), entries)

    private fun HistoryEntry.csvRow() = listOf(
        Instant.ofEpochMilli(time).toString(),
        key,
        pkg,
        app,
        channelId,
        channelName,
        category.name,
        title,
        text,
        outcome.name,
        reason.orEmpty(),
    )
}
