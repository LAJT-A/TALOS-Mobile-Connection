package com.lajt.talos

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Servicio con "acceso a notificaciones". Sirve para dos cosas:
 *  - Android lo exige para elegir que cancion suena en otra app (lo usa LinkService).
 *  - Ver los mensajes de WhatsApp que llegan y responderlos desde su notificacion.
 * Solo mira las notificaciones de WhatsApp; las demas las ignora.
 */
class MediaAccess : NotificationListenerService() {

    companion object {
        private val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")

        private class Chat(val name: String, val action: Notification.Action)

        /** Chats con una notificacion viva que se puede responder, por nombre sin acentos. */
        private val chats = ConcurrentHashMap<String, Chat>()
        private val lastText = ConcurrentHashMap<String, String>()

        private fun plain(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)
                .trim()

        /** Responde al chat cuyo nombre coincide. Devuelve el nombre real, o null si no hay a quien. */
        fun reply(context: Context, who: String, message: String): String? {
            val wanted = plain(who)
            val chat = chats[wanted]
                ?: chats.entries.firstOrNull { it.key.contains(wanted) || wanted.contains(it.key) }?.value
                ?: return null
            val inputs = chat.action.remoteInputs ?: return null
            val results = Bundle()
            for (input in inputs) results.putCharSequence(input.resultKey, message)
            val intent = Intent()
            RemoteInput.addResultsToIntent(inputs, intent, results)
            chat.action.actionIntent.send(context, 0, intent)
            return chat.name
        }
    }

    override fun onListenerConnected() {
        try {
            activeNotifications?.forEach { remember(it, announce = false) }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        remember(sbn, announce = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP) return
        lastText.remove(sbn.key)
    }

    private fun remember(sbn: StatusBarNotification, announce: Boolean) {
        if (sbn.packageName !in WHATSAPP) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras ?: return
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) return // los grupos no se anuncian
        val name = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: return
        if (name.isEmpty() || text.isEmpty()) return
        val action = notification.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() } ?: return
        chats[plain(name)] = Chat(name, action)
        if (lastText.put(sbn.key, text) == text) return // la misma notificacion, repetida
        if (announce) Link.send("#WA $name|${text.replace("\n", " ")}")
    }
}
