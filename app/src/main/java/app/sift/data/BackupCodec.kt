package app.sift.data

import kotlinx.serialization.json.Json

/**
 * Settings backup format: the [StoreData] JSON itself. `ignoreUnknownKeys` keeps newer backups
 * readable by older app versions; unknown/missing fields fall back to defaults.
 */
object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(d: StoreData): String = json.encodeToString(StoreData.serializer(), d)

    /** Returns null when the text isn't a readable Sift settings file. */
    fun decode(text: String): StoreData? = runCatching {
        json.decodeFromString(StoreData.serializer(), text)
    }.getOrNull()
}
