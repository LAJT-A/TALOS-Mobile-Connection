package com.lajt.talos

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.view.KeyEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

/**
 * Mantiene la conexion Bluetooth con la PC aunque la pantalla este apagada.
 *
 * Lineas normales que llegan = lo que dice Talos (se lee en voz alta).
 * Lineas con # = ordenes para el telefono (musica y llamadas).
 * Lo que el telefono le avisa a la PC tambien va con # (por ejemplo "#CALL Mama").
 */
class LinkService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val PREFS = "talos"
        const val KEY_ADDRESS = "address"
        const val KEY_NAME = "name"
        private const val CHANNEL = "talos_link"

        fun start(context: Context) {
            val intent = Intent(context, LinkService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }

    @Volatile private var running = false
    @Volatile private var address: String? = null
    @Volatile private var socket: BluetoothSocket? = null
    private var worker: Thread? = null
    private var lastError = ""

    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    private val main = Handler(Looper.getMainLooper())
    private var ringing = false
    private var ringSent = false
    private var inCall = false

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            @Suppress("DEPRECATION")
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            onPhoneState(state, number)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Conexión con la PC", NotificationManager.IMPORTANCE_LOW)
        )
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, notification("Conectando…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(1, notification("Conectando…"))
            }
        } catch (e: Exception) {
            Link.addLog("(no pude quedarme en segundo plano: ${e.message})")
        }
        tts = TextToSpeech(this, this)
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(phoneReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(phoneReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val wanted = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ADDRESS, null)
        if (wanted != address || worker?.isAlive != true) {
            address = wanted
            closeSocket() // si habia otra PC elegida, corta y vuelve a conectar
            if (worker?.isAlive != true) {
                running = true
                worker = Thread({ loop() }, "talos-link").also { it.start() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        closeSocket()
        try {
            unregisterReceiver(phoneReceiver)
        } catch (_: Exception) {
        }
        tts?.shutdown()
        Link.setOutput(null)
        Link.setStatus("Sin conexión", false)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Bluetooth

    private fun loop() {
        while (running) {
            val target = address
            if (target == null) {
                setStatus("Elige tu PC", false)
                sleep(2000)
                continue
            }
            try {
                val adapter = getSystemService(BluetoothManager::class.java).adapter
                if (adapter == null || !adapter.isEnabled) {
                    setStatus("Activa el Bluetooth", false)
                    sleep(4000)
                    continue
                }
                setStatus("Conectando…", false)
                val device = adapter.getRemoteDevice(target)
                val sock = device.createRfcommSocketToServiceRecord(Link.SPP)
                socket = sock
                sock.connect()
                Link.setOutput(sock.outputStream)
                setStatus("Conectado", true)
                val reader = BufferedReader(InputStreamReader(sock.inputStream, Charsets.UTF_8))
                while (running) {
                    val line = reader.readLine() ?: break
                    val text = line.trim()
                    if (text.isNotEmpty()) handle(text)
                }
                lastError = ""
            } catch (e: SecurityException) {
                setStatus("Falta el permiso de Bluetooth", false)
                report("permiso: ${e.message}")
            } catch (e: Exception) {
                // PC apagada, Talos cerrado o fuera de alcance: se reintenta solo
                report("no pude conectar: ${e.message}")
            } finally {
                Link.setOutput(null)
                closeSocket()
            }
            if (running) {
                if (Link.status != "Falta el permiso de Bluetooth") setStatus("Buscando la PC…", false)
                sleep(5000)
            }
        }
    }

    /** Escribe el motivo del fallo una sola vez, no en cada reintento. */
    private fun report(reason: String) {
        if (reason != lastError) {
            lastError = reason
            Link.addLog("($reason)")
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    private fun setStatus(text: String, connected: Boolean) {
        Link.setStatus(text, connected)
        try {
            getSystemService(NotificationManager::class.java).notify(1, notification(text))
        } catch (_: Exception) {
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Talos")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    // ------------------------------------------------------------------ lo que llega de la PC

    private fun handle(line: String) {
        if (line.startsWith("#")) {
            command(line.substring(1).trim())
        } else {
            Link.addLog("Talos: $line")
            speak(line)
        }
    }

    private fun command(text: String) {
        val parts = text.split(" ", limit = 2)
        val arg = if (parts.size > 1) parts[1] else ""
        when (parts[0].uppercase(Locale.ROOT)) {
            "PLAY" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            "PAUSE" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            "PLAYPAUSE" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "NEXT" -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            "PREV" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "VOLUP" -> volume(AudioManager.ADJUST_RAISE)
            "VOLDOWN" -> volume(AudioManager.ADJUST_LOWER)
            "ANSWER" -> answerCall()
            "HANGUP" -> endCall()
            "SAY" -> speak(arg)
            else -> Link.addLog("(orden desconocida: $text)")
        }
    }

    private fun mediaKey(code: Int) {
        val audio = getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun volume(direction: Int) {
        val audio = getSystemService(AudioManager::class.java)
        repeat(2) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI) }
    }

    private fun has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun answerCall() {
        if (!has(Manifest.permission.ANSWER_PHONE_CALLS)) {
            Link.send("#ERROR falta el permiso de llamadas en el telefono")
            return
        }
        try {
            @Suppress("DEPRECATION")
            getSystemService(TelecomManager::class.java).acceptRingingCall()
            Link.addLog("(llamada contestada)")
        } catch (e: Exception) {
            Link.send("#ERROR no pude contestar: ${e.message}")
        }
    }

    private fun endCall() {
        if (!has(Manifest.permission.ANSWER_PHONE_CALLS)) {
            Link.send("#ERROR falta el permiso de llamadas en el telefono")
            return
        }
        try {
            @Suppress("DEPRECATION")
            val done = getSystemService(TelecomManager::class.java).endCall()
            Link.addLog(if (done) "(llamada colgada)" else "(no habia llamada)")
        } catch (e: Exception) {
            Link.send("#ERROR no pude colgar: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ llamadas -> PC

    private fun onPhoneState(state: String, number: String?) {
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!ringing) {
                    ringing = true
                    ringSent = false
                    // El numero a veces llega en un segundo aviso: si no llega, se avisa igual.
                    main.postDelayed({ if (ringing && !ringSent) announce(null) }, 1500)
                }
                if (number != null && !ringSent) announce(number)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                ringing = false
                inCall = true
                Link.send("#CALLSTART")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (ringing || inCall) Link.send("#CALLEND")
                ringing = false
                inCall = false
            }
        }
    }

    private fun announce(number: String?) {
        ringSent = true
        val who = if (number.isNullOrBlank()) "unknown" else (contactName(number) ?: number)
        Link.addLog("(llamada de $who)")
        Link.send("#CALL $who")
    }

    private fun contactName(number: String): String? {
        if (!has(Manifest.permission.READ_CONTACTS)) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ voz

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        val result = engine.setLanguage(Locale.UK) // Talos responde en ingles
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        ttsReady = true
    }

    private fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "talos-" + System.nanoTime())
    }
}
