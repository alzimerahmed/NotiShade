package app.sift.ui

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.WifiDeviceFilter
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sift.App
import app.sift.BuildConfig
import app.sift.backend.AccessState
import app.sift.service.NotifListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    access: AccessState,
    vm: MainViewModel,
    onBack: (() -> Unit)?,
    onContinue: (() -> Unit)?,
) {
    val ctx = LocalContext.current
    val pairLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.refreshAccess() }
    var showAdb by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = {},
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                )
            }
        },
        bottomBar = {
            if (onContinue != null) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = onContinue,
                        enabled = access.configured,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(if (access.configured) "Continue" else "Finish both steps to continue")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = if (onBack == null) 24.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onBack == null) {
                Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandMark(72.dp)
                    Wordmark(MaterialTheme.typography.headlineLarge, Modifier.padding(top = 18.dp))
                    Text(
                        "Sort your notifications by what they\u2019re about",
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text("Connect", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Android only lets trusted helpers change other apps\u2019 notification settings. Two quick steps, no root needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    if (access.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Step(1, "Allow notification access", access.listenerGranted) {
                        StepAction("Allow") { openListenerSettings(ctx) }
                    }
                    if (!access.listenerGranted) {
                        Hint("Switch greyed out? Open App info → ⋮ → Allow restricted settings, then try again.")
                        TextButton(onClick = { openAppInfo(ctx) }) { Text("App info") }
                    }
                    Step(2, "Pair with a nearby device", access.companionPaired) {
                        StepAction("Pair") { pair(ctx, pairLauncher, vm::say) }
                    }
                    if (!access.companionPaired) {
                        Hint("Choose any Bluetooth device or Wi-Fi network. It only unlocks Android's channel controls. Nothing is sent to it.")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "GOOD TO KNOW",
                    Modifier.padding(top = 8.dp),
                    style = OvertypeLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Bullet("Every notification is logged for 7 days. Blocked ones are hidden as they arrive and marked in Logs.")
                Bullet("Categories already off in Android Settings are taken over, so they're logged too.")
                Bullet("Apps whose main notification switch is off can't be logged. Turn it on and block their categories here instead.")
                Bullet("Rules remove matching notifications as they arrive, so they may appear for a moment.")
            }

            if (BuildConfig.DEBUG) {
                TextButton(onClick = { showAdb = !showAdb }) { Text(if (showAdb) "Hide emulator commands" else "Testing on an emulator?") }
            }
            if (BuildConfig.DEBUG && showAdb) {
                val p = ctx.packageName
                Hint("Run from your computer:")
                SelectionContainer {
                    Text(
                        "adb shell cmd notification allow_listener $p/${NotifListener::class.java.name}\n" +
                            "adb shell cmd companiondevice associate 0 $p 02:00:00:00:00:01",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("\u2014", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Step(number: Int, title: String, done: Boolean, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            if (done) {
                Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
            } else {
                Modifier.size(26.dp).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            },
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Default.Check, "Done", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(title, Modifier.weight(1f).padding(horizontal = 14.dp), style = MaterialTheme.typography.bodyLarge)
        if (!done) action()
    }
}

@Composable
private fun StepAction(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun openListenerSettings(ctx: Context) {
    val component = ComponentName(ctx, NotifListener::class.java).flattenToString()
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component),
        )
    } catch (e: ActivityNotFoundException) {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

private fun openAppInfo(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
}

private fun pair(ctx: Context, launcher: ActivityResultLauncher<IntentSenderRequest>, onError: (String) -> Unit) {
    val cdm = ctx.getSystemService(CompanionDeviceManager::class.java)
        ?: return onError("Device pairing isn't available on this phone")
    val request = AssociationRequest.Builder()
        .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
        .addDeviceFilter(WifiDeviceFilter.Builder().build())
        .setSingleDevice(false)
        .build()
    cdm.associate(request, ctx.mainExecutor, object : CompanionDeviceManager.Callback() {
        override fun onAssociationPending(intentSender: IntentSender) {
            launcher.launch(IntentSenderRequest.Builder(intentSender).build())
        }

        override fun onAssociationCreated(associationInfo: AssociationInfo) {
            App.of(ctx).access.refresh()
        }

        override fun onFailure(error: CharSequence?) {
            onError(error?.toString() ?: "Pairing failed")
        }
    })
}
