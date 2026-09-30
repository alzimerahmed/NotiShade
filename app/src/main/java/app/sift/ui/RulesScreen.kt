package app.sift.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sift.R
import app.sift.backend.AccessState
import app.sift.data.AppInfo
import app.sift.data.HistoryEntry
import app.sift.data.Outcome
import app.sift.data.Rule
import app.sift.data.RuleAction
import app.sift.data.Schedule

private val ruleIdeas = listOf(
    R.string.idea_sales to listOf("sale", "% off", "discount", "deal"),
    R.string.idea_cashback to listOf("cashback", "reward", "coupon", "voucher"),
    R.string.idea_limited to listOf("limited time", "hurry", "last chance", "ends tonight"),
)

private fun newRule(name: String = "", keywords: List<String> = emptyList(), pkg: String? = null) =
    Rule(System.currentTimeMillis(), name, keywords, pkg)

/** Approximates the listener's matching against what Logs keeps (title and text). */
private fun Rule.matches(e: HistoryEntry): Boolean {
    if (pkg != null && pkg != e.pkg) return false
    val text = "${e.title} ${e.text}"
    return keywords.any { it.isNotBlank() && text.contains(it.trim(), ignoreCase = true) }
}

@Composable
fun RulesScreen(
    rules: List<Rule>,
    schedules: Map<String, Schedule>,
    apps: List<AppInfo>,
    history: List<HistoryEntry>,
    access: AccessState,
    onTab: (Tab) -> Unit,
    nav: Nav,
    vm: MainViewModel,
) {
    var editing by remember { mutableStateOf<Rule?>(null) }
    val labels = remember(apps) { apps.associate { it.pkg to it.label } }

    TabScaffold(
        Tab.RULES, onTab,
        fab = {
            if (rules.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { editing = newRule() },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(R.string.new_rule)) },
                )
            }
        },
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            if (!access.listenerConnected) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    ) {
                        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp)) {
                            Text(
                                stringResource(R.string.rules_paused),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                stringResource(R.string.rules_paused_body),
                                Modifier.padding(top = 2.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { nav.push("setup") },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                            ) { Text(stringResource(R.string.setup_access)) }
                        }
                    }
                }
            }
            if (rules.isEmpty()) {
                item { RulesEmpty { name, words -> editing = newRule(name, words) } }
            } else {
                item {
                    Text(
                        stringResource(R.string.rules_intro),
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(rules, key = { it.id }) { r ->
                    RuleCard(
                        r,
                        appLabel = r.pkg?.let { labels[it] ?: it },
                        caught = history.count { it.outcome == Outcome.RULE && it.reason == r.name },
                        onToggle = { vm.toggleRule(r) },
                        onClick = { editing = r },
                    )
                }
            }
        }
    }

    editing?.let { rule ->
        val isNew = rules.none { it.id == rule.id }
        RuleEditorSheet(
            rule,
            isNew = isNew,
            apps = apps,
            history = history,
            schedule = schedules[Schedule.keyFor(rule.id)],
            onSaveSchedule = { vm.setSchedule(Schedule.keyFor(rule.id), it) },
            onDismiss = { editing = null },
            onSave = { vm.saveRule(it); editing = null },
            onDelete = if (isNew) null else ({ vm.deleteRule(rule); editing = null }),
        )
    }
}

@Composable
private fun RulesEmpty(onCreate: (String, List<String>) -> Unit) {
    EmptyState(
        stringResource(R.string.empty_no_rules),
        stringResource(R.string.empty_no_rules_body),
        icon = Icons.Default.FilterAlt,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onCreate("", emptyList()) }, Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.create_rule))
            }
            Text(stringResource(R.string.or_start_from_idea), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                ruleIdeas.forEach { (name, words) ->
                    val idea = stringResource(name)
                    SuggestionChip(onClick = { onCreate(idea, words) }, label = { Text(idea) })
                }
            }
        }
    }
}

@Composable
private fun RuleCard(r: Rule, appLabel: String?, caught: Int, onToggle: () -> Unit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Switch(checked = r.enabled, onCheckedChange = { onToggle() }, colors = quietSwitchColors())
            }
            Column(Modifier.alpha(if (r.enabled) 1f else 0.55f).padding(end = 8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.keywords.forEach { Pill(it) }
                }
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (r.pkg != null) {
                        AppIcon(r.pkg, 18.dp)
                    } else {
                        Icon(Icons.Default.Apps, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        listOfNotNull(
                            appLabel ?: stringResource(R.string.all_apps),
                            stringResource(r.action.labelRes),
                            caught.takeIf { it > 0 }?.let { stringResource(R.string.caught_fmt, it) },
                        ).joinToString(" \u00b7 "),
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(top = 22.dp, bottom = 10.dp),
        style = OvertypeLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun splitKeywords(s: String) = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorSheet(
    rule: Rule,
    isNew: Boolean,
    apps: List<AppInfo>,
    history: List<HistoryEntry>,
    schedule: Schedule?,
    onSaveSchedule: (Schedule?) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var keywords by remember { mutableStateOf(rule.keywords) }
    var input by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf(rule.pkg) }
    var action by remember { mutableStateOf(rule.action) }
    var name by remember { mutableStateOf(rule.name) }
    val appChoices = remember(apps) { apps.filter { it.channels.isNotEmpty() }.sortedBy { it.label.lowercase() } }

    fun add(words: List<String>) {
        keywords = (keywords + words).distinctBy { it.lowercase() }
        input = ""
    }

    val allKeywords = (keywords + splitKeywords(input)).distinctBy { it.lowercase() }
    val draft = rule.copy(name = name.trim().ifBlank { allKeywords.firstOrNull().orEmpty() }, keywords = allKeywords, pkg = pkg, action = action)
    val matches = remember(draft, history) { if (allKeywords.isEmpty()) emptyList() else history.filter { draft.matches(it) } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(if (isNew) stringResource(R.string.new_rule) else stringResource(R.string.edit_rule), style = MaterialTheme.typography.headlineSmall)

            FieldLabel(stringResource(R.string.field_when_mentions))
            if (keywords.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keywords.forEach { k ->
                        InputChip(
                            selected = false,
                            onClick = { keywords = keywords - k },
                            label = { Text(k) },
                            trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.cd_remove_keyword, k), Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.endsWith(',')) add(splitKeywords(it)) else input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (keywords.isEmpty()) stringResource(R.string.keyword_placeholder) else stringResource(R.string.keyword_add_placeholder)) },
                trailingIcon = {
                    if (input.isNotBlank()) IconButton(onClick = { add(splitKeywords(input)) }) { Icon(Icons.Default.Add, stringResource(R.string.cd_add)) }
                },
                supportingText = { Text(stringResource(R.string.keyword_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(splitKeywords(input)) }),
            )

            FieldLabel(stringResource(R.string.field_from))
            MenuChip(
                label = pkg?.let { p -> apps.firstOrNull { it.pkg == p }?.label ?: history.firstOrNull { it.pkg == p }?.app ?: p } ?: stringResource(R.string.all_apps),
                selected = pkg != null,
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.all_apps)) + appChoices.map { it.pkg to it.label },
                onPick = { pkg = it },
            )

            FieldLabel(stringResource(R.string.field_then))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RuleAction.entries.forEachIndexed { i, a ->
                    SegmentedButton(
                        selected = action == a,
                        onClick = { action = a },
                        shape = SegmentedButtonDefaults.itemShape(i, RuleAction.entries.size),
                    ) { Text(stringResource(a.labelRes)) }
                }
            }
            Text(
                stringResource(action.descRes),
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FieldLabel(stringResource(R.string.field_name))
            OutlinedTextField(
                name, { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(allKeywords.firstOrNull() ?: stringResource(R.string.label_optional)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            if (allKeywords.isNotEmpty()) MatchPreview(matches)

            FieldLabel(stringResource(R.string.field_quiet_hours))
            ScheduleEditor(schedule, onSaveSchedule)

            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(enabled = allKeywords.isNotEmpty(), onClick = { onSave(draft) }) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
private fun MatchPreview(matches: List<HistoryEntry>) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (matches.size) {
                    0 -> stringResource(R.string.match_none)
                    else -> pluralResource(R.plurals.would_catch, matches.size)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            matches.take(3).forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(e.pkg, 20.dp)
                    Text(
                        listOf(e.title, e.text).filter { it.isNotBlank() }.joinToString(" \u00b7 ").ifBlank { e.app },
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
