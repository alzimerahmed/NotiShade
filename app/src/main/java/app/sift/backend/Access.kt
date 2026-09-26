package app.sift.backend

import android.app.NotificationManager
import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import app.sift.service.NotifListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Notification access + a companion pairing is what unlocks other apps' channels. */
data class AccessState(
    val listenerGranted: Boolean = false,
    val listenerConnected: Boolean = false,
    val companionPaired: Boolean = false,
) {
    val ready get() = listenerConnected && companionPaired

    // Listener binding is async after process start, so "configured" doesn't wait for the connection.
    val configured get() = listenerGranted && companionPaired
}

class Access(private val context: Context) {
    private val _state = MutableStateFlow(AccessState())
    val state: StateFlow<AccessState> = _state

    init {
        refresh()
    }

    fun refresh() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val listener = ComponentName(context, NotifListener::class.java)
        val granted = nm.isNotificationListenerAccessGranted(listener)
        // The system doesn't rebind the listener after a force-stop or crash.
        if (granted && NotifListener.instance == null) NotificationListenerService.requestRebind(listener)
        _state.value = AccessState(
            listenerGranted = granted,
            listenerConnected = NotifListener.instance != null,
            companionPaired = runCatching {
                context.getSystemService(CompanionDeviceManager::class.java)?.myAssociations?.isNotEmpty() == true
            }.getOrDefault(false),
        )
    }

    fun requireReady() {
        if (!state.value.ready) error("Not connected yet. Finish setup in Settings → Access.")
    }
}
