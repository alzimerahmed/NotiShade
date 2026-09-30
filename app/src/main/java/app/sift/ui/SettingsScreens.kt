package app.sift.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sift.BuildConfig
import app.sift.backend.AccessState
import app.sift.data.AppInfo
import app.sift.data.Batch
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
