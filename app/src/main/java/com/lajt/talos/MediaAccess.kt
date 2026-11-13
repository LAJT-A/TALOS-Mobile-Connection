package com.lajt.talos

import android.service.notification.NotificationListenerService

/**
 * No lee notificaciones: existe solo porque Android exige el "acceso a notificaciones"
 * para dejar que una app elija que cancion suena en otra (Spotify, YouTube Music...).
 */
class MediaAccess : NotificationListenerService()
