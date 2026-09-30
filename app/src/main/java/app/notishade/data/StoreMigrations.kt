package app.notishade.data

import kotlinx.serialization.json.Json

/** Pure decode of the persisted store text, applying inline migrations. Unit-tested. */
object StoreMigrations {
    val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): StoreData = json.decodeFromString(
        StoreData.serializer(),
        // "Block & log" was merged into "Block".
        text.replace("\"BLOCK_LOG\"", "\"BLOCK\""),
    )
}
