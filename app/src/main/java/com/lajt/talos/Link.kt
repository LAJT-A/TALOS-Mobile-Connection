package com.lajt.talos

import android.os.Handler
import android.os.Looper
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

/** Estado compartido entre la pantalla y el servicio que mantiene el Bluetooth. */
object Link {
    /** Puerto serie estandar de Bluetooth: el "puerto COM entrante" de Windows. */
    val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @Volatile var connected = false
        private set
    @Volatile var status = "Sin conexión"
        private set

    /** La pantalla se apunta aqui para redibujarse cuando algo cambia. */
    @Volatile var listener: (() -> Unit)? = null

    /** Apps de la PC, para el menu. Las manda la PC cuando se le pide con #APPS. */
    @Volatile var apps: List<String> = emptyList()
        private set

    fun setApps(list: List<String>) {
        apps = list
        notifyUi()
    }

    private val log = StringBuilder()
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var out: OutputStream? = null

    fun logText(): String = synchronized(log) { log.toString() }

    fun addLog(line: String) {
        synchronized(log) {
            log.append(line).append('\n')
            if (log.length > 20000) log.delete(0, log.length - 15000)
        }
        notifyUi()
    }

    fun setStatus(text: String, isConnected: Boolean) {
        status = text
        connected = isConnected
        notifyUi()
    }

    fun setOutput(stream: OutputStream?) {
        out = stream
    }

    /** Manda una linea a la PC. Si no hay conexion lo dice en el registro. */
    fun send(text: String) {
        val stream = out
        if (stream == null) {
            addLog("(sin conexión con la PC)")
            return
        }
        writer.execute {
            try {
                stream.write((text.replace("\n", " ") + "\n").toByteArray(Charsets.UTF_8))
                stream.flush()
            } catch (e: Exception) {
                addLog("(no se pudo enviar: ${e.message})")
            }
        }
    }

    private fun notifyUi() {
        main.post { listener?.invoke() }
    }
}
