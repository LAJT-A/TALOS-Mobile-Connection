package com.lajt.talos

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Pantalla: boton para hablarle a Talos, lo que se ha dicho y el estado de la conexion. */
class MainActivity : Activity() {

    private val cyan = Color.parseColor("#35D6FF")
    private val dark = Color.parseColor("#0B1014")
    private val panel = Color.parseColor("#141C22")

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var talkButton: Button
    private lateinit var songsButton: Button
    private var appsAdapter: AppsAdapter? = null
    private var appsShown: List<String> = emptyList()
    private var appsFilter = ""
    private var appsEmpty: TextView? = null
    private var iconsSeen = 0

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        setContentView(buildUi())
        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        Link.listener = { refresh() }
        refresh()
        startLinkIfReady()
    }

    override fun onPause() {
        Link.listener = null
        super.onPause()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ interfaz

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != 0) setStroke(dp(2), stroke)
        }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(dark)
            setPadding(dp(16), dp(40), dp(16), dp(16))
        }

        val title = TextView(this).apply {
            text = "TALOS"
            setTextColor(cyan)
            textSize = 26f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.4f
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        statusView = TextView(this).apply {
            setTextColor(Color.parseColor("#8FA3AD"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(12))
            setOnClickListener { pickDevice() }
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        logView = TextView(this).apply {
            setTextColor(Color.parseColor("#D7E3E8"))
            textSize = 15f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextIsSelectable(true)
        }
        scroll = ScrollView(this).apply {
            background = rounded(panel, 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(logView)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            hint = "Escríbele a Talos"
            setHintTextColor(Color.parseColor("#5C6F78"))
            setTextColor(Color.WHITE)
            textSize = 15f
            background = rounded(panel, 24)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            setSingleLine(true)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) {
                    sendTyped()
                    true
                } else false
            }
        }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sendButton = Button(this).apply {
            text = "Enviar"
            isAllCaps = false
            setTextColor(cyan)
            background = rounded(panel, 24)
            setOnClickListener { sendTyped() }
        }
        row.addView(sendButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(8)
        })
        root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        talkButton = Button(this).apply {
            text = "HABLAR"
            textSize = 18f
            setTextColor(cyan)
            letterSpacing = 0.2f
            background = rounded(dark, 40, cyan)
            setOnClickListener { toggleListening() }
        }
        root.addView(talkButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)).apply {
            topMargin = dp(14)
        })

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val appsButton = Button(this).apply {
            text = "Apps de la PC"
            isAllCaps = false
            textSize = 14f
            setTextColor(cyan)
            background = rounded(panel, 24)
            setOnClickListener { showApps() }
        }
        bottom.addView(appsButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val pcButton = Button(this).apply {
            text = "Elegir PC"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#8FA3AD"))
            background = rounded(panel, 24)
            setOnClickListener { pickDevice() }
        }
        bottom.addView(pcButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
        })
        root.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        // Solo se ve mientras falte el permiso que deja elegir canciones en el telefono.
        songsButton = Button(this).apply {
            text = "Permitir canciones y WhatsApp en el teléfono"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.parseColor("#FFC857"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("Para poner una canción en Spotify del teléfono y para avisarte y responder mensajes de WhatsApp, Android pide activar el \"acceso a notificaciones\" para Talos. La app solo mira las notificaciones de WhatsApp.")
                    .setPositiveButton("Abrir ajustes") { _, _ ->
                        try {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (e: Exception) {
                            Link.addLog("(no pude abrir los ajustes: busca \"Acceso a notificaciones\" en Ajustes)")
                        }
                    }
                    .setNegativeButton("Ahora no", null)
                    .show()
            }
        }
        root.addView(songsButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun refresh() {
        val name = getSharedPreferences(LinkService.PREFS, MODE_PRIVATE).getString(LinkService.KEY_NAME, null)
        statusView.text = if (name == null) "Toca aquí para elegir tu PC" else "${Link.status} · $name"
        statusView.setTextColor(if (Link.connected) cyan else Color.parseColor("#8FA3AD"))
        songsButton.visibility = if (LinkService.mediaAccess(this)) View.GONE else View.VISIBLE
        val apps = Link.apps
        if (apps != appsShown) { // llego la lista mientras el menu estaba abierto
            appsShown = apps
            appsAdapter?.setItems(filteredApps())
            appsEmpty?.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        }
        if (Link.iconVersion != iconsSeen) { // llegaron logos nuevos
            iconsSeen = Link.iconVersion
            appsAdapter?.notifyDataSetChanged()
        }
        val text = Link.logText()
        if (logView.text.toString() != text) {
            logView.text = text
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    // ------------------------------------------------------------------ permisos y conexion

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        if (Build.VERSION.SDK_INT >= 31) wanted.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = wanted.filter { !granted(it) }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startLinkIfReady()
        refresh()
    }

    private fun bluetoothAllowed() = Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT)

    private fun startLinkIfReady() {
        val address = getSharedPreferences(LinkService.PREFS, MODE_PRIVATE).getString(LinkService.KEY_ADDRESS, null)
        if (address != null && bluetoothAllowed()) {
            try {
                LinkService.start(this)
            } catch (e: Exception) {
                Link.addLog("(no pude iniciar la conexión: ${e.message})")
            }
        }
    }

    private fun pickDevice() {
        if (!bluetoothAllowed()) {
            askPermissions()
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        if (adapter == null || !adapter.isEnabled) {
            AlertDialog.Builder(this).setMessage("Activa el Bluetooth y vuelve a intentarlo.")
                .setPositiveButton("OK", null).show()
            return
        }
        val devices = try {
            adapter.bondedDevices.toList()
        } catch (e: SecurityException) {
            emptyList()
        }
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("No hay dispositivos emparejados. Empareja primero el teléfono con tu PC en los ajustes de Bluetooth.")
                .setPositiveButton("OK", null).show()
            return
        }
        val names = devices.map {
            try {
                it.name ?: it.address
            } catch (e: SecurityException) {
                it.address
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("¿Cuál es tu PC?")
            .setItems(names) { _, which ->
                getSharedPreferences(LinkService.PREFS, MODE_PRIVATE).edit()
                    .putString(LinkService.KEY_ADDRESS, devices[which].address)
                    .putString(LinkService.KEY_NAME, names[which])
                    .apply()
                startLinkIfReady()
                refresh()
            }
            .show()
    }

    // ------------------------------------------------------------------ menu de apps de la PC

    /** "contiene", no solo "empieza por": escribir "code" encuentra "Visual Studio Code". */
    private fun filteredApps(): List<String> =
        Link.apps.filter { it.lowercase(Locale.ROOT).contains(appsFilter) }

    private fun showApps() {
        Link.send("#APPS") // refresca la lista por si instalaste algo
        appsFilter = ""
        appsShown = Link.apps
        val adapter = AppsAdapter(this, filteredApps())
        appsAdapter = adapter

        val search = EditText(this).apply {
            hint = "Buscar app"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val list = ListView(this).apply { this.adapter = adapter }
        val empty = TextView(this).apply {
            text = "Aún no llega la lista. Revisa que diga \"Conectado\" y que Talos esté abierto en la PC."
            setPadding(dp(8), dp(16), dp(8), dp(16))
            visibility = if (Link.apps.isEmpty()) View.VISIBLE else View.GONE
        }
        appsEmpty = empty
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(search)
            addView(empty)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Abrir en la PC")
            .setView(box)
            .setNegativeButton("Cerrar", null)
            .setOnDismissListener {
                appsAdapter = null
                appsEmpty = null
            }
            .create()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                appsFilter = s.toString().trim().lowercase(Locale.ROOT)
                adapter.setItems(filteredApps())
            }
        })
        list.setOnItemClickListener { _, _, position, _ ->
            val name = adapter.getItem(position)
            Link.addLog("Tú: abrir $name")
            Link.send("#OPEN $name")
            dialog.dismiss()
        }
        dialog.show()
    }

    // ------------------------------------------------------------------ hablar y escribir

    private fun sendPhrase(text: String) {
        val phrase = text.trim()
        if (phrase.isEmpty()) return
        Link.addLog("Tú: $phrase")
        Link.send(phrase)
    }

    private fun sendTyped() {
        sendPhrase(input.text.toString())
        input.setText("")
    }

    private fun toggleListening() {
        if (listening) {
            recognizer?.stopListening()
            return
        }
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            askPermissions()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Link.addLog("(este teléfono no tiene reconocimiento de voz; instala o activa la app de Google)")
            return
        }
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(speechListener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        setListening(true)
        rec.startListening(intent)
    }

    private fun setListening(on: Boolean) {
        listening = on
        talkButton.text = if (on) "ESCUCHANDO…" else "HABLAR"
        talkButton.background = if (on) rounded(Color.parseColor("#0E3340"), 40, cyan) else rounded(dark, 40, cyan)
    }

    private val speechListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            setListening(false)
            val reason = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no te escuché"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "sin internet: descarga el idioma sin conexión en los ajustes de voz de Google"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "falta el permiso del micrófono"
                else -> "error de voz $error"
            }
            Link.addLog("($reason)")
        }

        override fun onResults(results: Bundle?) {
            setListening(false)
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) Link.addLog("(no te escuché)") else sendPhrase(text)
        }
    }
}
