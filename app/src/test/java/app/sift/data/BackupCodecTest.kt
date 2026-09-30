package app.sift.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCodecTest {
    @Test fun roundTrips() {
        val d = StoreData(
            overrides = mapOf("com.a|ch" to Category.PROMO),
            policies = mapOf(Category.PROMO to ChannelAction.BLOCK),
            known = setOf("com.a|ch"),
            hints = mapOf("com.a|ch" to setOf("promo")),
            history = listOf(Batch(1, 42, "title", emptyList())),
            rules = listOf(Rule(1, "Ads", listOf("offer"))),
            logBlocked = setOf("com.a|ch"),
            logExcludedApps = setOf("com.private"),
            theme = ThemeMode.DARK,
            materialYou = true,
        )
        assertEquals(d, BackupCodec.decode(BackupCodec.encode(d)))
    }

    @Test fun defaultsFillMissingFields() {
        assertEquals(StoreData(), BackupCodec.decode("{}"))
    }

    @Test fun unknownKeysAreIgnored() {
        val decoded = BackupCodec.decode("""{"futureField":1,"theme":"DARK"}""")
        assertEquals(ThemeMode.DARK, decoded?.theme)
    }

    @Test fun garbageReturnsNull() {
        org.junit.Assert.assertNull(BackupCodec.decode("not json"))
        org.junit.Assert.assertNull(BackupCodec.decode("""{"theme":"NEON"}"""))
    }
}
