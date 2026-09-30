package app.sift.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import app.sift.BuildConfig
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
            item { SectionLabel("Access") }
            item {
                ListItem(
                    headlineContent = { Text(if (access.ready) "Connected" else "Not connected") },
                    supportingContent = { Text("Notification access and device pairing") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable { nav.push("setup") },
                )
            }

            item { SectionLabel("Data") }
            item {
                ListItem(
                    headlineContent = { Text("Apps excluded from Logs") },
                    supportingContent = {
                        val count = store.logExcludedApps.size
                        Text(if (count == 0) "All apps are included" else "$count ${if (count == 1) "app" else "apps"} excluded")
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable { nav.push("log-exclusions") },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Change history") },
                    supportingContent = {
                        Text(
                            when (historyCount) {
                                0 -> "No changes yet"
                                1 -> "1 change you can undo"
                                else -> "$historyCount changes you can undo"
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
                    headlineContent = { Text("Rescan apps") },
                    supportingContent = { Text("${apps.size} apps scanned") },
                    trailingContent = { Icon(Icons.Default.Refresh, null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable(enabled = access.ready) { vm.scan() },
                )
            }

            item { SectionLabel("Backup") }
            item {
                ListItem(
                    headlineContent = { Text("Export settings") },
                    supportingContent = { Text("Save categories, rules and exclusions to a file") },
                    trailingContent = { Icon(Icons.Default.SaveAlt, null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable(onClick = onExport),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Import settings") },
                    supportingContent = { Text("Restore from a backup file") },
                    trailingContent = { Icon(Icons.Default.FolderOpen, null) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable { confirmImport = true },
                )
            }

            item { SectionLabel("Appearance") }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = store.theme == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(mode.label) }
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Material You") },
                    supportingContent = { Text("Use colours from your wallpaper instead of the app\u2019s own palette") },
                    trailingContent = { Switch(checked = store.materialYou, onCheckedChange = vm::setMaterialYou, colors = quietSwitchColors()) },
                    colors = clearListItem(),
                    modifier = Modifier.clickable { vm.setMaterialYou(!store.materialYou) },
                )
            }

            item { SectionLabel("About") }
            item {
                ListItem(
                    headlineContent = { Wordmark(MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text("Version ${BuildConfig.VERSION_NAME} · Everything stays on this device") },
                    leadingContent = { BrandMark(38.dp) },
                    colors = clearListItem(),
                )
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Import settings?") },
            text = { Text("Replaces your current categories, rules, exclusions and appearance with the ones in the backup file.") },
            confirmButton = { TextButton(onClick = { confirmImport = false; onImport() }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
        )
    }
}

/** "22:00 – 07:00 · Mon Tue Fri", or "Off". */
fun scheduleSummary(s: Schedule?): String = when {
    s == null || !s.enabled -> "Off"
    else -> "${fmtTime(s.startMinutes)} – ${fmtTime(s.endMinutes)}" + when {
        s.days.isEmpty() || s.days.size == 7 -> ""
        else -> " · " + s.days.sorted().joinToString(" ") { dayNames[it - 1] }
    }
}

private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun fmtTime(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

/** A sensible default: 22:00 to 07:00, every day. */
private val defaultSchedule = Schedule(startMinutes = 22 * 60, endMinutes = 7 * 60)

/**
 * Quiet-hours editor: enable switch, start/end pickers and day-of-week chips. [schedule] null
 * means off; edits flow through [onChange] immediately and the caller decides when to persist.
 */
@Composable
fun ScheduleEditor(schedule: Schedule?, onChange: (Schedule?) -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }

    ListItem(
        headlineContent = { Text("Use quiet hours") },
        supportingContent = { Text("Only apply this inside the time window below") },
        trailingContent = {
            Switch(checked = schedule != null, onCheckedChange = { on -> onChange(if (on) defaultSchedule else null) }, colors = quietSwitchColors())
        },
        colors = clearListItem(),
    )
    if (schedule != null) {
        ListItem(
            headlineContent = { Text("Starts") },
            trailingContent = { TextButton(onClick = { picking = "start" }) { Text(fmtTime(schedule.startMinutes)) } },
            colors = clearListItem(),
        )
        ListItem(
            headlineContent = { Text("Ends") },
            trailingContent = { TextButton(onClick = { picking = "end" }) { Text(fmtTime(schedule.endMinutes)) } },
            colors = clearListItem(),
        )
        if (schedule.startMinutes > schedule.endMinutes) {
            Text(
                "Runs overnight, past midnight.",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "DAYS",
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
                QuietChip(name, isoDay in schedule.days) {
                    val days = if (isoDay in schedule.days) schedule.days - isoDay else schedule.days + isoDay
                    onChange(schedule.copy(days = days))
                }
            }
        }
        Text(
            if (schedule.days.isEmpty()) "No days picked means every day." else "Only on the days picked above.",
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
                title = { Text(if (start) "Starts at" else "Ends at") },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = {
                        val value = state.hour * 60 + state.minute
                        onChange(if (start) schedule.copy(startMinutes = value) else schedule.copy(endMinutes = value))
                        picking = null
                    }) { Text("Set") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
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
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(enabled = draft != null, onClick = { onSave(draft) }) { Text("Save") }
            }
        }
    }
}

@Composable
fun HistoryScreen(history: List<Batch>, nav: Nav, vm: MainViewModel) {
    DetailScaffold("Change history", nav::back) {
        if (history.isEmpty()) {
            EmptyState("No changes yet", "Every change you make shows up here and can be undone.")
            return@DetailScaffold
        }
        LazyColumn {
            items(history, key = { it.id }) { b ->
                ListItem(
                    headlineContent = { Text(b.title) },
                    supportingContent = {
                        Text("${b.changes.size} ${if (b.changes.size == 1) "channel" else "channels"} · ${DateUtils.getRelativeTimeSpanString(b.time)}")
                    },
                    trailingContent = { TextButton(onClick = { vm.undo(b) }) { Text("Undo") } },
                    colors = clearListItem(),
                )
            }
        }
    }
}
