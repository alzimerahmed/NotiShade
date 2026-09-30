package app.sift.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sift.backend.AccessState
import app.sift.R
import app.sift.data.AppInfo
import app.sift.data.Category
import app.sift.data.ChannelAction
import app.sift.data.ChannelInfo
import app.sift.data.HistoryEntry
import app.sift.data.Outcome
import app.sift.data.Rule
import app.sift.data.Schedule

private enum class ShowFilter { ALL, SHOWN, BLOCKED }

@Composable
private fun ShowFilter.label(): String = when (this) {
    ALL -> stringResource(R.string.filter_all)
    SHOWN -> stringResource(R.string.filter_shown)
    BLOCKED -> stringResource(R.string.filter_blocked)
}

private val HistoryEntry.blocked get() = outcome != Outcome.SHOWN

/** Case-insensitive substring match over the fields a user sees on a log row. */
private fun matchQuery(e: HistoryEntry, q: String) =
    e.title.contains(q, ignoreCase = true) || e.text.contains(q, ignoreCase = true) ||
        e.app.contains(q, ignoreCase = true) || e.channelName.contains(q, ignoreCase = true)

@Composable
private fun HistoryEntry.blockLabel(): String = when (outcome) {
    Outcome.RULE -> stringResource(R.string.removed_by_rule_fmt, reason.orEmpty())
    else -> stringResource(R.string.status_blocked) + channelName.ifBlank { null }?.let { " \u00b7 $it" }.orEmpty()
}

@Composable
fun LogsScreen(
    entries: List<HistoryEntry>,
    apps: List<AppInfo>,
    access: AccessState,
    onTab: (Tab) -> Unit,
    nav: Nav,
    vm: MainViewModel,
    onExportCsv: () -> Unit = {},
    onExportJson: () -> Unit = {},
) {
    var show by rememberSaveable { mutableStateOf(ShowFilter.ALL) }
    var pkg by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<HistoryEntry?>(null) }
    var ruleDraft by remember { mutableStateOf<Rule?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }

    val filtered = remember(entries, show, pkg, category, query) {
        val q = query.trim()
        entries.filter {
            (show == ShowFilter.ALL || (show == ShowFilter.BLOCKED) == it.blocked) &&
                (pkg == null || it.pkg == pkg) && (category == null || it.category == category) &&
                (q.isEmpty() || matchQuery(it, q))
        }
    }

    TabScaffold(
        Tab.LOGS, onTab,
        actions = {
            if (entries.isNotEmpty()) {
                IconButton(onClick = { exportMenu = true }) { Icon(Icons.Default.SaveAlt, stringResource(R.string.cd_export_history)) }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_csv)) }, onClick = { exportMenu = false; onExportCsv() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_json)) }, onClick = { exportMenu = false; onExportJson() })
                }
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.Delete, stringResource(R.string.cd_clear_history)) }
            }
        },
    ) {
        Column {
            if (entries.isEmpty()) {
                EmptyState(
                    stringResource(R.string.empty_no_notifications),
                    if (access.listenerConnected) {
                        stringResource(R.string.empty_logs_body)
                    } else {
                        stringResource(R.string.logs_need_access)
                    },
                ) { if (!access.listenerConnected) FilledTonalButton(onClick = { nav.push("setup") }) { Text(stringResource(R.string.setup_access)) } }
                return@Column
            }
            Filters(entries, show, { show = it }, pkg, { pkg = it }, category, { category = it })
            SearchField(
                query,
                { query = it },
                placeholder = stringResource(R.string.search_logs),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (filtered.isEmpty()) {
                EmptyState(stringResource(R.string.empty_no_matches), stringResource(R.string.no_matches_body))
                return@Column
            }
            HistoryList(filtered) { open = it }
        }
    }

    open?.let { e ->
        EntrySheet(
            e,
            channel = vm.channelFor(e),
            filteredToApp = pkg == e.pkg,
            onDismiss = { open = null },
            onAction = { vm.setChannelFromLog(e, it); open = null },
            onRule = { words -> ruleDraft = Rule(System.currentTimeMillis(), "", words, e.pkg); open = null },
            onShowApp = { pkg = e.pkg; show = ShowFilter.ALL; category = null; open = null },
            onOpenApp = { open = null; nav.push("app/${e.pkg}") },
            onDelete = { vm.deleteEntry(e); open = null },
        )
    }
    ruleDraft?.let { rule ->
        val store by vm.store.collectAsStateWithLifecycle()
        val ruleCreatedFmt = stringResource(R.string.rule_created_fmt)
        RuleEditorSheet(
            rule,
            isNew = true,
            apps = apps,
            history = entries,
            schedule = store.schedules[Schedule.keyFor(rule.id)],
            onSaveSchedule = { vm.setSchedule(Schedule.keyFor(rule.id), it) },
            onDismiss = { ruleDraft = null },
            onSave = { vm.saveRule(it); vm.say(ruleCreatedFmt.format(it.name)); ruleDraft = null },
            onDelete = null,
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_body, entries.size)) },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Filters(
    entries: List<HistoryEntry>,
    show: ShowFilter,
    onShow: (ShowFilter) -> Unit,
    pkg: String?,
    onPkg: (String?) -> Unit,
    category: Category?,
    onCategory: (Category?) -> Unit,
) {
    val appsInLog = remember(entries) {
        entries.groupBy { it.pkg }.map { (p, list) -> Triple(p, list.first().app, list.size) }.sortedByDescending { it.third }
    }
    val categoriesInLog = remember(entries) {
        entries.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShowFilter.entries.forEach { f ->
            val count = when (f) {
                ShowFilter.ALL -> entries.size
                ShowFilter.SHOWN -> entries.count { !it.blocked }
                ShowFilter.BLOCKED -> entries.count { it.blocked }
            }
            QuietChip(f.label() + " $count", show == f) { onShow(f) }
        }
        MenuChip(
            label = pkg?.let { p -> appsInLog.firstOrNull { it.first == p }?.second ?: p } ?: stringResource(R.string.label_app),
            selected = pkg != null,
            options = listOf<Pair<String?, String>>(null to stringResource(R.string.all_apps)) + appsInLog.map { it.first to stringResource(R.string.app_option_fmt, it.second, it.third) },
            onPick = onPkg,
        )
        MenuChip(
            label = category?.let { stringResource(it.labelRes) } ?: stringResource(R.string.section_category),
            selected = category != null,
            options = listOf<Pair<Category?, String>>(null to stringResource(R.string.all_categories)) + categoriesInLog.map { it.first to stringResource(R.string.category_option_fmt, stringResource(it.first.labelRes), it.second) },
            onPick = onCategory,
        )
    }
}

@Composable
private fun HistoryList(entries: List<HistoryEntry>, onOpen: (HistoryEntry) -> Unit) {
    val ctx = LocalContext.current
    val byDay = remember(entries) { entries.groupBy { dayLabel(ctx, it.time) } }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            byDay.forEach { (day, list) ->
                item(key = "day-$day") {
                    val blocked = list.count { it.blocked }
                    SectionLabel(day + if (blocked > 0) stringResource(R.string.day_blocked_fmt, blocked) else "")
                }
            items(list, key = { "${it.key}@${it.time}" }) { e -> EntryRow(e) { onOpen(e) } }
        }
    }
}

@Composable
private fun EntryRow(e: HistoryEntry, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val blocked = e.blocked
    ListItem(
        headlineContent = {
            Text(
                e.title.ifBlank { e.app },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (blocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Column {
                if (e.text.isNotBlank()) {
                    Text(e.text, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (blocked) "${e.app} \u00b7 ${e.blockLabel()}" else listOf(e.app, e.channelName).filter { it.isNotBlank() }.joinToString(" \u00b7 "),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = {
            Box {
                Box(Modifier.alpha(if (blocked) 0.4f else 1f)) { AppIcon(e.pkg) }
                if (blocked) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(18.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Close, stringResource(R.string.cd_blocked), Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onError) }
                }
            }
        },
        trailingContent = {
            Text(
                DateUtils.formatDateTime(ctx, e.time, DateUtils.FORMAT_SHOW_TIME),
                style = MaterialTheme.typography.labelSmall.merge(TabularFigures),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = clearListItem(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private val stopWords = setOf(
    "the", "and", "for", "you", "your", "with", "this", "that", "from", "are", "was", "has", "have", "our", "not",
    "now", "get", "all", "just", "will", "can", "its", "into", "been", "more", "here", "there", "what", "when",
    "who", "how", "new", "one", "out", "they", "them", "their", "than", "then", "also", "via", "had", "but",
)

/** Words (and a short title as a phrase) the user can tap to build a rule. */
private fun keywordCandidates(e: HistoryEntry): List<String> {
    val title = e.title.trim().takeIf { it.split(' ').size in 2..5 }
    val words = Regex("[\\p{L}\\p{N}%\\p{Sc}']+").findAll("${e.title} ${e.text}")
        .map { it.value.trim('\'').lowercase() }
        .filter { it.length >= 3 && it.any(Char::isLetter) || it.endsWith('%') }
        .filter { it !in stopWords }
    return (listOfNotNull(title) + words).distinctBy { it.lowercase() }.take(20)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(
    e: HistoryEntry,
    channel: ChannelInfo?,
    filteredToApp: Boolean,
    onDismiss: () -> Unit,
    onAction: (ChannelAction) -> Unit,
    onRule: (List<String>) -> Unit,
    onShowApp: () -> Unit,
    onOpenApp: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val candidates = remember(e) { keywordCandidates(e) }
    var picked by remember(e) { mutableStateOf(emptyList<String>()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(e.pkg, 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(e.app, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        DateUtils.formatDateTime(ctx, e.time, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (e.outcome) {
                    Outcome.SHOWN -> Pill(stringResource(R.string.pill_shown))
                    Outcome.BLOCKED -> Pill(stringResource(R.string.status_blocked), color = MaterialTheme.colorScheme.error)
                    Outcome.RULE -> Pill(stringResource(R.string.pill_rule), color = MaterialTheme.colorScheme.error)
                }
            }

            Surface(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val category = channel?.category ?: e.category
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(category, 20.dp)
                        Text(
                            listOf(stringResource(category.labelRes), e.channelName).filter { it.isNotBlank() }.joinToString(" \u00b7 "),
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (e.title.isNotBlank()) {
                        Text(e.title, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (e.text.isNotBlank()) {
                        Text(e.text, Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (e.outcome == Outcome.RULE) {
                        Text(
                            stringResource(R.string.removed_by_rule_fmt, e.reason.orEmpty()),
                            Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            SheetLabel(
                if (channel != null) {
                    stringResource(R.string.all_channel_notifs_fmt, channel.channel.name.toString())
                } else {
                    stringResource(R.string.err_channel_unavailable)
                },
            )
            if (channel != null) {
                val status = channel.status()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTile(stringResource(R.string.action_allow), Icons.Default.NotificationsActive, status == Status.ALLOWED, Modifier.weight(1f)) {
                        onAction(ChannelAction.ALERT)
                    }
                    ActionTile(stringResource(R.string.status_silent), Icons.AutoMirrored.Filled.VolumeOff, status == Status.SILENT, Modifier.weight(1f)) {
                        onAction(ChannelAction.SILENT)
                    }
                    ActionTile(stringResource(R.string.action_block), Icons.Default.Block, status == Status.BLOCKED, Modifier.weight(1f), danger = true) {
                        onAction(ChannelAction.BLOCK)
                    }
                }
            }

            SheetLabel(stringResource(R.string.sheet_only_like))
            Text(
                if (candidates.isEmpty()) {
                    stringResource(R.string.rule_hint_empty)
                } else {
                    stringResource(R.string.rule_hint_candidates)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidates.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    candidates.forEach { w ->
                        QuietChip(w, w in picked) { picked = if (w in picked) picked - w else picked + w }
                    }
                }
            }
            FilledTonalButton(onClick = { onRule(picked) }, Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.FilterAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    when (picked.size) {
                        0 -> stringResource(R.string.create_rule)
                        else -> pluralResource(R.plurals.create_rule_words, picked.size)
                    },
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!filteredToApp) TextButton(onClick = onShowApp) { Text(stringResource(R.string.more_from_fmt, e.app), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                TextButton(onClick = onOpenApp) { Text(stringResource(R.string.app_settings)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.cd_delete_entry)) }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(top = 24.dp, bottom = 10.dp),
        style = OvertypeLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    current: Boolean,
    modifier: Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = !current,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = when {
            !current -> colors.surface
            danger -> colors.errorContainer
            else -> colors.primaryContainer
        },
        contentColor = when {
            !current -> colors.onSurface
            danger -> colors.onErrorContainer
            else -> colors.onPrimaryContainer
        },
        border = if (current) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge)
            Text(
                if (current) stringResource(R.string.label_current) else " ",
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        }
    }
}

private fun dayLabel(ctx: Context, time: Long): String = when {
    DateUtils.isToday(time) -> ctx.getString(R.string.today)
    DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> ctx.getString(R.string.yesterday)
    else -> DateUtils.formatDateTime(ctx, time, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR)
}
