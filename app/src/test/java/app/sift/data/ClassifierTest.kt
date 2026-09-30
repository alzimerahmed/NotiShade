package app.sift.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifierTest {
    private fun category(
        name: String,
        id: String = name,
        description: String? = null,
        hints: Set<String> = emptySet(),
        conversation: Boolean = false,
        systemApp: Boolean = false,
    ) = Classifier.classify(id, name, description, null, conversation, hints, systemApp).first

    @Test fun promotionalChannels() {
        assertEquals(Category.PROMO, category("Promotions"))
        assertEquals(Category.PROMO, category("Deals for you"))
        assertEquals(Category.PROMO, category("Updates", id = "moe_default_channel"))
    }

    @Test fun genericChannelIsOther() {
        assertEquals(Category.OTHER, category("General"))
        assertEquals(Category.OTHER, category("Miscellaneous", id = "miscellaneous"))
    }

    @Test fun notificationHintsOutweighNeutralNames() {
        assertEquals(Category.PROMO, category("General", hints = setOf("promo")))
        assertEquals(Category.MESSAGES, category("General", hints = setOf("msg")))
    }

    @Test fun conversationsAreMessages() {
        assertEquals(Category.MESSAGES, category("Friends", conversation = true))
    }

    @Test fun systemChannelsAreNotSocial() {
        assertEquals(Category.SYSTEM, category("Background Activity", systemApp = true))
        assertEquals(Category.SYSTEM, category("USB connection", systemApp = true))
        assertEquals(Category.SYSTEM, category("Provide data connection", systemApp = true))
    }

    @Test fun realKeywordsBeatTheSystemAppPrior() {
        assertEquals(Category.PROMO, category("Data offers", systemApp = true))
        assertEquals(Category.SECURITY, category("Account alerts", systemApp = true))
    }

    @Test fun tiesPreferCategoriesRiskierToSilence() {
        assertEquals(Category.ORDERS, category("Order updates & offers"))
    }

    @Test fun shortKeywordsNeedWholeWords() {
        // "ads" must not match inside "downloads".
        assertEquals(Category.SYSTEM, category("Downloads"))
    }

    @Test fun camelCaseIdsAreSplit() {
        assertEquals(Category.PROMO, category("", id = "marketingPush"))
    }

    @Test fun longerKeywordsMatchWordPrefixes() {
        assertEquals(Category.PROMO, category("Offers from stores"))
        assertEquals(Category.ORDERS, category("Order tracking"))
    }

    @Test fun descriptionsAndGroupNamesAreConsidered() {
        assertEquals(
            Category.PAYMENTS,
            Classifier.classify("alerts", "Alerts", "Billing and invoices", "Money", conversation = false, hints = emptySet()).first,
        )
    }

    @Test fun nullNameWithNeutralIdIsOther() {
        assertEquals(Category.OTHER, Classifier.classify("general", null, null, null, conversation = false, hints = emptySet()).first)
    }
}
