package app.sift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreMigrationsTest {
    @Test fun decodesCurrentFormat() {
        val d = StoreData(appDefaults = mapOf("com.a" to ChannelAction.SILENT))
        assertEquals(d, StoreMigrations.decode(StoreMigrations.json.encodeToString(StoreData.serializer(), d)))
    }

    @Test fun missingAppDefaultsFallsBackToEmpty() {
        assertEquals(StoreData(), StoreMigrations.decode("{}"))
        assertNull(StoreMigrations.decode("{}").appDefaults["com.a"])
    }

    @Test fun migratesBlockLog() {
        val decoded = StoreMigrations.decode("""{"policies":{"PROMO":"BLOCK_LOG"}}""")
        assertEquals(ChannelAction.BLOCK, decoded.policies[Category.PROMO])
    }

    @Test fun unknownKeysAreIgnored() {
        assertEquals(ThemeMode.DARK, StoreMigrations.decode("""{"futureField":1,"theme":"DARK"}""").theme)
    }
}
