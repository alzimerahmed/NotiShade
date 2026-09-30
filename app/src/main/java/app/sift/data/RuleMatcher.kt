package app.sift.data

/** Pure keyword-rule matching; the caller extracts the notification's text fields. */
object RuleMatcher {
    fun match(rules: List<Rule>, pkg: String, fields: List<CharSequence?>): Rule? {
        if (rules.none { it.enabled }) return null
        val haystack = fields.filterNotNull().joinToString(" ")
        if (haystack.isBlank()) return null
        return rules.firstOrNull { r ->
            r.enabled && (r.pkg.isNullOrBlank() || r.pkg == pkg) &&
                r.keywords.any { it.isNotBlank() && haystack.contains(it.trim(), ignoreCase = true) }
        }
    }
}
