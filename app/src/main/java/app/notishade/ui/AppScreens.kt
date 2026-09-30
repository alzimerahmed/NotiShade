package app.notishade.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.notishade.R
import app.notishade.data.AppInfo
import app.notishade.data.Category
import app.notishade.data.ChannelAction
import app.notishade.data.ChannelInfo
import app.notishade.data.HistoryEntry
import app.notishade.data.Outcome

private enum class AppSort { NAME, MOST, BLOCKED }

@Composable
private fun AppSort.label(): String = when (this) {
    AppSort.NAME -> stringResource(R.string.sort_az)
    AppSort.MOST -> stringResource(R.string.sort_most)
    AppSort.BLOCKED -> stringResource(R.string.sort_most_blocked)
}

@Composable
fun AppsScreen(
    apps: List<AppInfo>,
    history: List<HistoryEntry>,
    progress: Pair<Int, Int>?,
    onTab: (Tab) -> Unit,
    onOpen: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(AppSort.NAME) }
    var onlyBlocked by rememberSaveable { mutableStateOf(false) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var showEmpty by rememberSaveable { mutableStateOf(false) }
    val counts = remember(history) { history.groupingBy { it.pkg }.eachCount() }
    val blockedCounts = remember(history) { history.filter { it.outcome != Outcome.SHOWN }.groupingBy { it.pkg }.eachCount() }
    val shown = remember(apps, query, sort, onlyBlocked, showSystem, showEmpty, counts) {
        val matching = if (query.isNotBlank()) {
            apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
        } else {
            apps.filter {
                (showSystem || !it.system) && (showEmpty || it.channels.isNotEmpty()) &&
                    (!onlyBlocked || it.channels.any { c -> c.status() == Status.BLOCKED })
            }
        }
        when (sort) {
            AppSort.NAME -> matching
            AppSort.MOST -> matching.sortedByDescending { counts[it.pkg] ?: 0 }
            AppSort.BLOCKED -> matching.sortedByDescending { blockedCounts[it.pkg] ?: 0 }
        }
    }

    TabScaffold(
        Tab.APPS, onTab, progress) {
        Column {
            SearchField(
                query, { query = it }, pluralResource(R.plurals.search_apps, shown.size),
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MenuChip(sort.label(), sort != AppSort.NAME, AppSort.entries.map { it to it.label() }) { sort = it }
                QuietChip(stringResource(R.string.chip_has_blocked), onlyBlocked) { onlyBlocked = !onlyBlocked }
                QuietChip(stringResource(R.string.chip_system_apps), showSystem) { showSystem = !showSystem }
                QuietChip(stringResource(R.string.chip_no_channels), showEmpty) { showEmpty = !showEmpty }
            }
            if (shown.isEmpty()) {
                EmptyState(stringResource(R.string.empty_no_apps_title), if (apps.isEmpty()) stringResource(R.string.empty_no_apps_body) else stringResource(R.string.empty_no_apps_search))
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(shown, key = { it.pkg }) { a ->
                    val count = counts[a.pkg] ?: 0
                    ListItem(
                        headlineContent = { Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(appSummary(a), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { AppIcon(a.pkg) },
                        trailingContent = if (count > 0) {
                            { WeekCount(count, blockedCounts[a.pkg] ?: 0) }
                        } else {
                            null
                        },
                        colors = clearListItem(),
                        modifier = Modifier.clickable { onOpen(a.pkg) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekCount(count: Int, blocked: Int) {
    Column(horizontalAlignment = Alignment.End) {
        Text(count.toString(), style = MaterialTheme.typography.titleMedium.merge(TabularFigures))
        Text(
            if (blocked > 0) pluralResource(R.plurals.blocked_count, blocked) else stringResource(R.string.label_this_week),
            style = MaterialTheme.typography.labelSmall,
            color = if (blocked > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun appSummary(a: AppInfo): String = when {
    a.error != null -> stringResource(R.string.err_read_channels)
    a.channels.isEmpty() -> stringResource(R.string.no_channels_yet)
    else -> pluralResource(R.plurals.channels_count, a.channels.size) + " · " + statusSummary(a.channels)
}

@Composable
fun AppDetailScreen(app: AppInfo?, appDefault: ChannelAction?, nav: Nav, vm: MainViewModel) {
    var sheetKey by rememberSaveable { mutableStateOf<String?>(null) }

    DetailScaffold(app?.label ?: stringResource(R.string.label_app), nav::back) {
        if (app == null) {
            EmptyState(stringResource(R.string.app_not_found), stringResource(R.string.app_not_found_body))
            return@DetailScaffold
        }
        val sections = remember(app) { app.channels.groupBy { it.category }.toSortedMap() }
        val duplicateNames = remember(app) {
            app.channels.groupingBy { it.channel.name.toString() }.eachCount().filterValues { it > 1 }.keys
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AppIcon(app.pkg, 52.dp)
                    Column {
                        Text(app.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.pkg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (app.channels.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BulkButton(stringResource(R.string.allow_all)) { vm.bulk(app.channels, ChannelAction.ALERT, app.label) }
                        BulkButton(stringResource(R.string.silence_all)) { vm.bulk(app.channels, ChannelAction.SILENT, app.label) }
                        BulkButton(stringResource(R.string.block_all), danger = true) { vm.bulk(app.channels, ChannelAction.BLOCK, app.label) }
                    }
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.new_channels)) },
                        supportingContent = { Text(stringResource(R.string.new_channels_body)) },
                        trailingContent = {
                            PickerButton(stringResource(appDefaultLabel(appDefault)), appDefaultOptions, { stringResource(appDefaultLabel(it)) }) {
                                vm.setAppDefault(app.pkg, app.label, it, app.channels)
                            }
                        },
                        colors = clearListItem(),
                    )
                }
            }
            app.error?.let { item { Text(stringResource(R.string.err_read_channels_fmt, it), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
            if (app.channels.isEmpty() && app.error == null) {
                item { EmptyState(stringResource(R.string.no_channels_yet), stringResource(R.string.no_channels_body)) }
            }
            sections.forEach { (cat, channels) ->
                item(key = "section-${cat.name}") { SectionLabel(stringResource(cat.labelRes)) }
                items(channels, key = { it.key }) { c ->
                    val detail = c.channel.description?.takeIf { it.isNotBlank() }
                        ?: c.channel.id.takeIf { c.channel.name.toString() in duplicateNames }
                    ListItem(
                        headlineContent = { Text(c.channel.name.toString()) },
                        supportingContent = detail?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                        trailingContent = { StatusText(c.behaviorLabel(), c.status()) },
                        colors = clearListItem(),
                        modifier = Modifier.clickable { sheetKey = c.key },
                    )
                }
            }
        }
        app.channels.firstOrNull { it.key == sheetKey }?.let { c ->
            ChannelSheet(
                c,
                onDismiss = { sheetKey = null },
                onAction = { vm.setChannel(c, it); sheetKey = null },
                onCategory = { vm.setOverride(c, it); sheetKey = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSheet(
    c: ChannelInfo,
    onDismiss: () -> Unit,
    onAction: (ChannelAction) -> Unit,
    onCategory: (Category?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                c.channel.name.toString(),
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(c.appLabel, c.channel.description?.takeIf { it.isNotBlank() }).joinToString(" · "),
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionLabel(stringResource(R.string.section_behaviour))
            ChannelAction.entries.forEach { a ->
                val current = c.isCurrent(a)
                ListItem(
                    headlineContent = { Text(stringResource(a.labelRes)) },
                    supportingContent = { Text(stringResource(a.descRes)) },
                    leadingContent = { RadioButton(selected = current, onClick = null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable { onAction(a) },
                )
            }
            SectionLabel(stringResource(R.string.section_category))
            ListItem(
                headlineContent = { Text(stringResource(c.category.labelRes)) },
                supportingContent = { Text(if (c.overridden) stringResource(R.string.override_set_by_you) else stringResource(R.string.detected_auto_fmt, c.confidence)) },
                leadingContent = { CategoryIcon(c.category) },
                trailingContent = {
                    PickerButton(stringResource(R.string.change), listOf<Category?>(null) + Category.entries, { it?.let { cat -> stringResource(cat.labelRes) } ?: stringResource(R.string.automatic) }, onCategory)
                },
                colors = clearListItem(),
            )
        }
    }
}

private val appDefaultOptions = listOf<ChannelAction?>(null, ChannelAction.ALERT, ChannelAction.SILENT, ChannelAction.BLOCK)

private fun appDefaultLabel(a: ChannelAction?) = when (a) {
    null -> R.string.appdefault_none
    ChannelAction.ALERT -> R.string.action_allow
    ChannelAction.SILENT -> R.string.action_silence
    else -> R.string.action_block
}

/** Low-emphasis bulk action; only the destructive one takes colour. */
@Composable
private fun BulkButton(label: String, danger: Boolean = false, onClick: () -> Unit) {    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}
