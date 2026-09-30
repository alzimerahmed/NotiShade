package app.notishade.backend

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.os.UserHandle
import app.notishade.service.NotifListener

/** Other apps' channels via the listener's privileged APIs, which a companion device association unlocks. */
object Channels {
    private fun svc() = NotifListener.instance ?: error("Notification access is not connected")
    private fun user(uid: Int) = UserHandle.getUserHandleForUid(uid)

    fun list(pkg: String, uid: Int): List<NotificationChannel> = svc().getNotificationChannels(pkg, user(uid))
    fun groups(pkg: String, uid: Int): List<NotificationChannelGroup> = svc().getNotificationChannelGroups(pkg, user(uid))
    fun update(pkg: String, uid: Int, channel: NotificationChannel) = svc().updateNotificationChannel(pkg, user(uid), channel)
}
