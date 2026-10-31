package com.radiocar.launcher

import android.service.notification.NotificationListenerService

/**
 * No hace nada por sí mismo: Android exige que el launcher tenga un "notification listener"
 * habilitado para poder leer y controlar las sesiones de música (MediaSessionManager).
 */
class MediaListenerService : NotificationListenerService()
