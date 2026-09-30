package app.sift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleMatcherTest {
    private val rule = Rule(id = 1, name = "Ads", keywords = listOf("offer", " sale "))

    private fun match(r: List<Rule>, pkg: String, vararg fields: String?) =
        RuleMatcher.match(r, pkg, fields.toList())

    @Test fun matchesAnyFieldCaseInsensitively() {
        assertEquals(rule, match(listOf(rule), "com.x", "Big OFFER", null))
        assertEquals(rule, match(listOf(rule), "com.x", null, "everything must go, SALE"))
    }

    @Test fun disabledRulesAreIgnored() {
        val disabled = rule.copy(enabled = false)
        assertNull(match(listOf(disabled), "com.x", "offer"))
    }

    @Test fun appScopedRuleOnlyMatchesItsApp() {
        val scoped = rule.copy(pkg = "com.target")
        assertNull(match(listOf(scoped), "com.other", "offer"))
        assertEquals(scoped, match(listOf(scoped), "com.target", "offer"))
    }

    @Test fun blankKeywordsAndEmptyTextNeverMatch() {
        assertNull(match(listOf(rule.copy(keywords = listOf("  ", ""))), "com.x", "anything"))
        assertNull(match(listOf(rule), "com.x"))
        assertNull(match(listOf(rule), "com.x", "  ", ""))
    }

    @Test fun firstMatchingRuleWins() {
        val second = Rule(id = 2, name = "Other", keywords = listOf("offer"))
        assertEquals(rule, match(listOf(rule, second), "com.x", "offer"))
    }

    @Test fun noRulesMeansNoMatch() {
        assertNull(match(emptyList(), "com.x", "offer"))
    }
}
