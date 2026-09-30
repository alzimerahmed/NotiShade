package app.notishade.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.notishade.R
import app.notishade.data.AppInfo

@Composable
fun LogExclusionsScreen(
    apps: List<AppInfo>,
    excluded: Set<String>,
    nav: Nav,
    vm: MainViewModel,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var excludedOnly by rememberSaveable { mutableStateOf(false) }
    val shown = apps.filter { app ->
        (!excludedOnly || app.pkg in excluded) &&
            (query.isBlank() || app.label.contains(query, ignoreCase = true) || app.pkg.contains(query, ignoreCase = true))
    }

    DetailScaffold(stringResource(R.string.apps_excluded_from_logs), nav::back) {
        Column {
            Text(
                stringResource(R.string.exclusions_body),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SearchField(
                query, { query = it }, stringResource(R.string.search_installed_apps),
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietChip(stringResource(R.string.chip_excluded_only), excludedOnly) { excludedOnly = !excludedOnly }
                Text(
                    pluralResource(R.plurals.excluded_count, excluded.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (apps.isEmpty()) {
                EmptyState(stringResource(R.string.apps_not_loaded), stringResource(R.string.apps_not_loaded_body))
                return@Column
            }
            if (shown.isEmpty()) {
                EmptyState(stringResource(R.string.no_apps_found), stringResource(R.string.no_apps_found_body))
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(shown, key = { it.pkg }) { app ->
                    val checked = app.pkg in excluded
                    ListItem(
                        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(app.pkg, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { AppIcon(app.pkg) },
                        trailingContent = {
                            Checkbox(checked = checked, onCheckedChange = { vm.setLogExcluded(app.pkg, it) }, colors = quietCheckboxColors())
                        },
                        colors = clearListItem(),
                        modifier = Modifier.clickable { vm.setLogExcluded(app.pkg, !checked) },
                    )
                }
            }
        }
    }
}
