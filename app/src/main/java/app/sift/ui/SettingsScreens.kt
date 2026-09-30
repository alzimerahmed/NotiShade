package app.sift.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sift.BuildConfig
import app.sift.R
import app.sift.backend.AccessState
import app.sift.data.AppInfo
import app.sift.data.Batch
import app.sift.data.Schedule
import app.sift.data.StoreData
import app.sift.data.ThemeMode

@Composable
fun SettingsScreen(
    access: AccessState,
    store: StoreData,
    apps: List<AppInfo>,
    onTab: (Tab) -> Unit,
    nav: Nav,
    vm: MainViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val historyCount = store.history.size
    var confirmImport by remember { mutableStateOf(false) }
    TabScaffold(Tab.SETTINGS, onTab) {
        LazyColumn {
            item { SectionLabel(stringResource(R.string.section_access)) }
            item {
                ListItem(
                    headlineContent = { Text(if (access.ready) stringResource(R.string.connected) else stringResource(R.string.not_connected)) },
                    supportingContent = { Text(stringResource(R.string.access_body)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable { nav.push("setup") },
                )
            }

            item { SectionLabel(stringResource(R.string.section_blocking)) }
            item {
                ListItem(
                    headlineContent = { Text(if (store.paused) stringResource(R.string.paused) else stringResource(R.string.active)) },
                    supportingContent = { Text(stringResource(R.string.pause_body)) },
                    trailingContent = { Switch(checked = store.paused, onCheckedChange = vm::setPaused, colors = quietSwitchColors()) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable { vm.setPaused(!store.paused) },
                )
            }

            item { SectionLabel(stringResource(R.string.section_data)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.apps_excluded_from_logs)) },
                    supportingContent = {
                        val count = store.logExcludedApps.size
                        Text(if (count == 0) stringResource(R.string.all_apps_included) else pluralResource(R.plurals.excluded_apps_count, count))
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable { nav.push("log-exclusions") },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.change_history)) },
                    supportingContent = {
                        Text(
                            when (historyCount) {
                                0 -> stringResource(R.string.no_changes_yet)
                                else -> pluralResource(R.plurals.changes_count, historyCount)
                            },
                        )
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable { nav.push("history") },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.rescan_apps)) },
                    supportingContent = { Text(pluralResource(R.plurals.apps_scanned_count, apps.size)) },
                    trailingContent = { Icon(Icons.Default.Refresh, null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable(enabled = access.ready) { vm.scan() },
                )
            }

            item { SectionLabel(stringResource(R.string.section_backup)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.export_settings)) },
                    supportingContent = { Text(stringResource(R.string.export_settings_body)) },
                    trailingContent = { Icon(Icons.Default.SaveAlt, null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable(onClick = onExport),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.import_settings)) },
                    supportingContent = { Text(stringResource(R.string.import_settings_body)) },
                    trailingContent = { Icon(Icons.Default.FolderOpen, null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable { confirmImport = true },
                )
            }

            item { SectionLabel(stringResource(R.string.section_appearance)) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = store.theme == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(stringResource(mode.labelRes)) }
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.material_you)) },
                    supportingContent = { Text(stringResource(R.string.material_you_body)) },
                    trailingContent = { Switch(checked = store.materialYou, onCheckedChange = vm::setMaterialYou, colors = quietSwitchColors()) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable { vm.setMaterialYou(!store.materialYou) },
                )
            }

            item { SectionLabel(stringResource(R.string.section_about)) }
            item {
                ListItem(
                    headlineContent = { Wordmark(MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(stringResource(R.string.version_line_fmt, BuildConfig.VERSION_NAME)) },
                    leadingContent = { BrandMark(38.dp) },
                    colors = clearListItem(),
                )
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirmImport = false; onImport() }) { Text(stringResource(R.string.action_import)) } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** "22:00 – 07:00 · Mon Tue Fri", or "Off". Words are pre-resolved so callers outside
 *  composition (snackbar callbacks) can use it without querying resources late. */
fun scheduleSummary(off: String, dayLabels: List<String>, s: Schedule?): String = when {
    s == null || !s.enabled -> off
    else -> fmtTime(s.startMinutes) + " – " + fmtTime(s.endMinutes) + when {
        s.days.isEmpty() || s.days.size == 7 -> ""
        else -> " · " + s.days.sorted().joinToString(" ") { dayLabels[it - 1] }
    }
}

private val dayNames = listOf(R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun)

private fun fmtTime(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

/** A sensible default: 22:00 to 07:00, every day. */
private val defaultSchedule = Schedule(startMinutes = 22 * 60, endMinutes = 7 * 60)

/**
 * Quiet-hours editor: enable switch, start/end pickers and day-of-week chips. [schedule] null
 * means off; edits flow through [onChange] immediately and the caller decides when to persist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditor(schedule: Schedule?, onChange: (Schedule?) -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.use_quiet_hours)) },
        supportingContent = { Text(stringResource(R.string.use_quiet_hours_body)) },
        trailingContent = {
            Switch(checked = schedule != null, onCheckedChange = { on -> onChange(if (on) defaultSchedule else null) }, colors = quietSwitchColors())
        },
        colors = clearListItem(),
    )
    if (schedule != null) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.starts)) },
            trailingContent = { TextButton(onClick = { picking = "start" }) { Text(fmtTime(schedule.startMinutes)) } },
            colors = clearListItem(),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.ends)) },
            trailingContent = { TextButton(onClick = { picking = "end" }) { Text(fmtTime(schedule.endMinutes)) } },
            colors = clearListItem(),
        )
        if (schedule.startMinutes > schedule.endMinutes) {
            Text(
                stringResource(R.string.overnight),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.label_days),
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            style = OvertypeLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dayNames.forEachIndexed { i, name ->
                val isoDay = i + 1
                QuietChip(stringResource(name), isoDay in schedule.days) {
                    val days = if (isoDay in schedule.days) schedule.days - isoDay else schedule.days + isoDay
                    onChange(schedule.copy(days = days))
                }
            }
        }
        Text(
            if (schedule.days.isEmpty()) stringResource(R.string.no_days_hint) else stringResource(R.string.days_picked_hint),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        picking?.let { which ->
            val start = which == "start"
            val minutes = if (start) schedule.startMinutes else schedule.endMinutes
            val state = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
            AlertDialog(
                onDismissRequest = { picking = null },
                title = { Text(if (start) stringResource(R.string.starts_at) else stringResource(R.string.ends_at)) },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = {
                        val value = state.hour * 60 + state.minute
                        onChange(if (start) schedule.copy(startMinutes = value) else schedule.copy(endMinutes = value))
                        picking = null
                    }) { Text(stringResource(R.string.action_set)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

/** Bottom-sheet wrapper around [ScheduleEditor] with Save/Cancel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorSheet(
    title: String,
    schedule: Schedule?,
    onDismiss: () -> Unit,
    onSave: (Schedule?) -> Unit,
) {
    var draft by remember { mutableStateOf(schedule) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(title, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
            ScheduleEditor(draft) { draft = it }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.weight(1f))
                // Always enabled: saving a null draft is how a schedule gets cleared.
                Button(onClick = { onSave(draft) }) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
fun HistoryScreen(history: List<Batch>, nav: Nav, vm: MainViewModel) {
    DetailScaffold(stringResource(R.string.change_history), nav::back) {
        if (history.isEmpty()) {
            EmptyState(stringResource(R.string.no_changes_yet), stringResource(R.string.change_history_empty_body))
            return@DetailScaffold
        }
        LazyColumn {
            items(history, key = { it.id }) { b ->
                ListItem(
                    headlineContent = { Text(b.title) },
                    supportingContent = {
                        Text(pluralResource(R.plurals.channels_count, b.changes.size) + " · " + DateUtils.getRelativeTimeSpanString(b.time))
                    },
                    trailingContent = { TextButton(onClick = { vm.undo(b) }) { Text(stringResource(R.string.action_undo)) } },
                    colors = clearListItem(),
                )
            }
        }
    }
}
