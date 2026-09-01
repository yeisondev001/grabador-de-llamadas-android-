package com.papa.grabador_llamadas

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CallRecorderService : Service() {

    companion object {
        const val CHANNEL_ID = "grabador_channel"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.papa.grabador_llamadas.START"
        const val ACTION_STOP = "com.papa.grabador_llamadas.STOP"
        const val ACTION_RECORD_START = "com.papa.grabador_llamadas.RECORD_START"
        const val ACTION_RECORD_STOP = "com.papa.grabador_llamadas.RECORD_STOP"
        const val EXTRA_TYPE = "type"
        const val EXTRA_PENDING = "pending"
        const val TYPE_GSM = "LLAMADA"
        const val TYPE_WS = "WHATSAPP"
        const val TYPE_TEST = "PRUEBA"
        const val TYPE_MANUAL = "MANUAL"
        const val ACTION_ACCEPT = "com.papa.grabador_llamadas.ACCEPT_RECORD"
        const val ACTION_DECLINE = "com.papa.grabador_llamadas.DECLINE_RECORD"
        const val CHANNEL_PROMPT = "grabador_prompt"
        const val NOTIF_PROMPT_GSM = 1002
        const val NOTIF_PROMPT_WS = 1003

        @Volatile
        var instance: CallRecorderService? = null
            private set

        fun start(context: Context) {
            val i = Intent(context, CallRecorderService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallRecorderService::class.java))
        }
    }

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private var telephonyManager: TelephonyManager? = null
    private var phoneListener: Any? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var session: RecordingSession? = null
    private var silenceMs = 0L
    private var recordStartElapsed = 0L

    @Volatile var isRecording = false
        private set
    @Volatile var currentType: String? = null
        private set
    @Volatile var currentSource: String? = null
        private set
    @Volatile var currentFile: String? = null
        private set

    @Volatile private var callState = TelephonyManager.CALL_STATE_IDLE
    @Volatile private var pendingGsm = false
    @Volatile private var pendingWs = false
    @Volatile private var wsOngoing = false
    @Volatile private var promptShownGsm = false
    @Volatile private var promptShownWs = false
    @Volatile private var wsHandledAt = 0L
    private var lastPeak = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        startAsForeground()
        registerPhoneListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RECORD_START -> {
                val t = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_GSM
                if (intent.getBooleanExtra(EXTRA_PENDING, false)) {
                    if (t == TYPE_WS) pendingWs = true else pendingGsm = true
                } else {
                    startRecording(t)
                }
            }
            ACTION_RECORD_STOP -> stopRecording()
            ACTION_ACCEPT -> handleAccept(intent)
            ACTION_DECLINE -> handleDecline(intent)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopRecording()
        unregisterPhoneListener()
        instance = null
        super.onDestroy()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Grabador de llamadas", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val ch2 = NotificationChannel(CHANNEL_PROMPT, "Preguntar si grabar", NotificationManager.IMPORTANCE_HIGH)
        ch2.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch2)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pi)
        if (isRecording) {
            val secs = (session?.durationMs ?: 0L) / 1000
            b.setContentTitle("Grabando ${currentType ?: ""}")
            b.setContentText("Fuente: $currentSource · ${secs}s")
            b.addAction(
                0, "Detener",
                PendingIntent.getService(
                    this, 21,
                    Intent(this, CallRecorderService::class.java).setAction(ACTION_RECORD_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            b.setContentTitle("Grabador de Llamadas")
            b.setContentText("Vigilando llamadas entrantes")
            b.addAction(
                0, "Grabar ahora",
                PendingIntent.getService(
                    this, 20,
                    Intent(this, CallRecorderService::class.java).setAction(ACTION_ACCEPT).putExtra(EXTRA_TYPE, TYPE_MANUAL),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        return b.build()
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun registerPhoneListener() {
        try {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state)
                    }
                }
                phoneListener = cb
                telephonyManager?.registerTelephonyCallback(callbackExecutor, cb)
            } else {
                @Suppress("DEPRECATION")
                val pl = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state)
                    }
                }
                phoneListener = pl
                telephonyManager?.listen(pl, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (_: Exception) {
        }
    }

    private fun unregisterPhoneListener() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                (phoneListener as? TelephonyCallback)?.let { telephonyManager?.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(phoneListener as? PhoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        } catch (_: Exception) {
        }
        phoneListener = null
    }

    private fun handleCallState(state: Int) {
        callState = state
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> onGsmRinging()
            TelephonyManager.CALL_STATE_OFFHOOK -> onGsmOffhook()
            TelephonyManager.CALL_STATE_IDLE -> onGsmIdle()
        }
    }

    fun onGsmRinging() {
        if (!prefs.getBoolean("recordGsm", true)) return
        if (prefs.getBoolean("askBeforeRecord", true)) showPrompt(TYPE_GSM)
    }

    fun onGsmOffhook() {
        if (!prefs.getBoolean("recordGsm", true)) return
        if (pendingGsm) {
            pendingGsm = false
            startRecording(TYPE_GSM)
        } else if (prefs.getBoolean("askBeforeRecord", true)) {
            if (!isRecording) showPrompt(TYPE_GSM)
        } else {
            startRecording(TYPE_GSM)
        }
    }

    fun onGsmIdle() {
        cancelPrompt(TYPE_GSM)
        pendingGsm = false
        if (isRecording && (currentType == TYPE_GSM || currentType == TYPE_MANUAL)) stopRecording()
    }

    fun onGsmCall(active: Boolean) {
        if (active) onGsmOffhook() else onGsmIdle()
    }

    fun onWsPosted(isOngoing: Boolean) {
        if (!prefs.getBoolean("recordWs", true)) return
        val ask = prefs.getBoolean("askBeforeRecord", true)
        if (isOngoing) {
            wsOngoing = true
            if (ask) {
                if (pendingWs) {
                    pendingWs = false
                    cancelPrompt(TYPE_WS)
                    doStartWs()
                } else if (!isRecording && !promptShownWs) {
                    showPrompt(TYPE_WS)
                }
            } else {
                doStartWs()
            }
        } else {
            if (ask && !isRecording && !promptShownWs && System.currentTimeMillis() - wsHandledAt > 60000) {
                showPrompt(TYPE_WS)
            }
        }
    }

    fun onWsRemoved() {
        wsOngoing = false
        pendingWs = false
        cancelPrompt(TYPE_WS)
        if (isRecording && (currentType == TYPE_WS || currentType == TYPE_MANUAL)) {
            stopRecording()
        }
    }

    private fun doStartWs() {
        if (isRecording) return
        startRecording(TYPE_WS)
    }

    private fun handleAccept(intent: Intent?) {
        val type = intent?.getStringExtra(EXTRA_TYPE) ?: TYPE_GSM
        if (type == TYPE_GSM || type == TYPE_WS) {
            cancelPrompt(type)
            if (type == TYPE_WS) wsHandledAt = System.currentTimeMillis()
        }
        if (isRecording) return
        val pending = when (type) {
            TYPE_GSM -> callState != TelephonyManager.CALL_STATE_OFFHOOK
            TYPE_WS -> !wsOngoing
            else -> false
        }
        val i = Intent(applicationContext, CallRecorderService::class.java)
            .setAction(ACTION_RECORD_START)
            .putExtra(EXTRA_TYPE, type)
            .putExtra(EXTRA_PENDING, pending)
        Log.d("Grabador", "Aceptado tipo=$type pendiente=$pending reiniciando servicio para autorizar microfono")
        handler.postDelayed({
            try { ContextCompat.startForegroundService(applicationContext, i) } catch (_: Exception) {}
        }, 400)
        stopSelf()
    }

    private fun handleDecline(intent: Intent?) {
        val type = intent?.getStringExtra(EXTRA_TYPE) ?: TYPE_GSM
        if (type == TYPE_GSM) pendingGsm = false else pendingWs = false
        if (type == TYPE_WS) wsHandledAt = System.currentTimeMillis()
        cancelPrompt(type)
    }

    private fun showPrompt(type: String) {
        if (type == TYPE_GSM && promptShownGsm) return
        if (type == TYPE_WS && promptShownWs) return
        val acceptPi = PendingIntent.getService(
            this, if (type == TYPE_GSM) 1 else 2,
            Intent(this, CallRecorderService::class.java).setAction(ACTION_ACCEPT).putExtra(EXTRA_TYPE, type),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val declinePi = PendingIntent.getService(
            this, if (type == TYPE_GSM) 3 else 4,
            Intent(this, CallRecorderService::class.java).setAction(ACTION_DECLINE).putExtra(EXTRA_TYPE, type),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val label = if (type == TYPE_GSM) "Llamada entrante" else "Llamada de WhatsApp"
        val openPi = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL_PROMPT)
            .setContentTitle(label)
            .setContentText("¿Grabar esta llamada?")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(Notification.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(openPi)
            .addAction(0, "Grabar", acceptPi)
            .addAction(0, "No", declinePi)
            .setOngoing(true)
            .build()
        val id = if (type == TYPE_GSM) NOTIF_PROMPT_GSM else NOTIF_PROMPT_WS
        getSystemService(NotificationManager::class.java).notify(id, n)
        if (type == TYPE_GSM) promptShownGsm = true else promptShownWs = true
    }

    private fun cancelPrompt(type: String) {
        val id = if (type == TYPE_GSM) NOTIF_PROMPT_GSM else NOTIF_PROMPT_WS
        getSystemService(NotificationManager::class.java).cancel(id)
        if (type == TYPE_GSM) promptShownGsm = false else promptShownWs = false
    }

    fun manualStart() {
        startRecording(TYPE_TEST)
    }

    private val audioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun forceSpeaker() {
        try {
            audioManager.isSpeakerphoneOn = true
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            if (cur < max / 2) audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, max, 0)
        } catch (_: Exception) {
        }
    }

    private fun audioSourceFor(type: String): Int {
        val prefKey = if (type == TYPE_WS) "audioSourceWs" else "audioSourceGsm"
        return when (prefs.getString(prefKey, "MIC")) {
            "VOICE_CALL" -> MediaRecorder.AudioSource.VOICE_CALL
            "VOICE_COMMUNICATION" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            "CAMCORDER" -> MediaRecorder.AudioSource.CAMCORDER
            "DEFAULT" -> MediaRecorder.AudioSource.DEFAULT
            "VOICE_RECOGNITION" -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            "UNPROCESSED" -> MediaRecorder.AudioSource.UNPROCESSED
            else -> MediaRecorder.AudioSource.MIC
        }
    }

    private fun sourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
        MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        else -> "MIC"
    }

    private fun startRecording(type: String) {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val dir = File(getExternalMediaDirs().firstOrNull() ?: filesDir, "Grabaciones")
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "${type}_$stamp.m4a")
        val source = audioSourceFor(type)
        executor.execute { begin(type, file, source) }
    }

    private fun begin(type: String, file: File, source: Int) {
        if (isRecording) return
        val s = try {
            RecordingSession(source, file).also { it.start() }
        } catch (_: Exception) {
            if (source != MediaRecorder.AudioSource.MIC) begin(type, file, MediaRecorder.AudioSource.MIC)
            return
        }
        session = s
        isRecording = true
        currentType = type
        currentSource = sourceName(source)
        currentFile = file.absolutePath
        silenceMs = 0
        recordStartElapsed = SystemClock.elapsedRealtime()
        if (type != TYPE_TEST) {
            if (prefs.getBoolean("autoSpeaker", true)) forceSpeaker()
        }
        Log.d("Grabador", "INICIO grabacion tipo=$type fuente=$currentSource archivo=${file.name}")
        handler.postDelayed(watchdog, 800)
        acquireWake()
        updateNotification()
    }

    private val watchdog = object : Runnable {
        override fun run() {
            val s = session ?: return
            if (s.source == MediaRecorder.AudioSource.VOICE_CALL) {
                val elapsed = SystemClock.elapsedRealtime() - recordStartElapsed
                if (s.lastAmplitude <= 4) silenceMs += 500 else silenceMs = 0
                val strictSilence = silenceMs >= 2000
                val nearSilence = elapsed >= 6000 && s.peakAmplitude <= 30
                if (strictSilence || nearSilence) {
                    Log.d("Grabador", "Canal VOICE_CALL mudo, cambio automatico a MIC")
                    restartWithMic()
                    return
                }
            }
            Log.d("Grabador", "senal ultima=${s.lastAmplitude} pico=${s.peakAmplitude} ms=${s.durationMs}")
            handler.postDelayed(this, 500)
        }
    }

    private fun restartWithMic() {
        val s = session ?: return
        val path = currentFile ?: return
        val type = currentType ?: TYPE_GSM
        handler.removeCallbacks(watchdog)
        session = null
        val file = File(path)
        executor.execute {
            s.stop()
            file.delete()
        }
        executor.execute { begin(type, file, MediaRecorder.AudioSource.MIC) }
    }

    fun stopRecording() {
        val s = session ?: return
        val type = currentType
        val file = currentFile
        handler.removeCallbacks(watchdog)
        session = null
        isRecording = false
        currentType = null
        currentSource = null
        releaseWake()
        lastPeak = s.peakAmplitude
        Log.d("Grabador", "FIN grabacion pico=$lastPeak ms=${s.durationMs}")
        executor.execute { s.stop() }
        if (prefs.getBoolean("autoSpeaker", true) && (type == TYPE_GSM || type == TYPE_WS || type == TYPE_MANUAL)) {
            try { audioManager.isSpeakerphoneOn = false } catch (_: Exception) {}
        }
        updateNotification()
    }

    fun statusMap(): Map<String, Any?> = mapOf(
        "serviceRunning" to true,
        "recording" to isRecording,
        "type" to (currentType ?: ""),
        "source" to (currentSource ?: ""),
        "file" to (currentFile ?: ""),
        "durationMs" to (session?.durationMs ?: 0L),
        "amp" to (session?.lastAmplitude ?: 0),
        "peak" to (session?.peakAmplitude ?: 0),
        "lastPeak" to lastPeak,
        "speaker" to try { audioManager.isSpeakerphoneOn } catch (_: Exception) { false }
    )

    private fun acquireWake() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "papa:rec").also {
            it.acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWake() {
        try { wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }
}

class RecordingSession(val source: Int, private val outFile: File) {

    private val sampleRate = 48000
    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private lateinit var thread: Thread

    @Volatile var lastAmplitude = 0
        private set
    @Volatile var peakAmplitude = 0
        private set
    @Volatile var durationMs = 0L
        private set

    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 4, 65536))
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("AudioRecord no inicializado")
        }
        audioRecord = rec
        running.set(true)
        thread = Thread { runLoop(rec) }
        thread.start()
    }

    private fun runLoop(rec: AudioRecord) {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            rec.startRecording()
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1)
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val started = booleanArrayOf(false)
            val track = intArrayOf(-1)
            val pcm = ShortArray(8192)
            var totalSamples = 0L
            while (running.get()) {
                val n = rec.read(pcm, 0, pcm.size)
                if (n <= 0) continue
                var max = 0
                for (i in 0 until n) {
                    val v = if (pcm[i] < 0) -pcm[i].toInt() else pcm[i].toInt()
                    if (v > max) max = v
                }
                lastAmplitude = max
                if (max > peakAmplitude) peakAmplitude = max
                durationMs = totalSamples * 1000 / sampleRate
                val inIdx = codec.dequeueInputBuffer(20000)
                if (inIdx >= 0) {
                    val ib = codec.getInputBuffer(inIdx)
                    if (ib != null) {
                        ib.clear()
                        for (i in 0 until n) ib.putShort(pcm[i])
                        codec.queueInputBuffer(inIdx, 0, n * 2, totalSamples * 1000000L / sampleRate, 0)
                        totalSamples += n
                    }
                }
                drain(codec, muxer, started, track)
            }
            val eosIdx = codec.dequeueInputBuffer(50000)
            if (eosIdx >= 0) {
                codec.queueInputBuffer(eosIdx, 0, 0, totalSamples * 1000000L / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            var guard = 0
            while (!drain(codec, muxer, started, track) && guard < 50) {
                Thread.sleep(10)
                guard++
            }
        } catch (_: Exception) {
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
            codec?.let {
                try { it.stop() } catch (_: Exception) {}
                try { it.release() } catch (_: Exception) {}
            }
            if (muxer != null) {
                try { muxer.stop() } catch (_: Exception) {}
                try { muxer.release() } catch (_: Exception) {}
            }
            if (outFile.length() < 1024) outFile.delete()
        }
    }

    private fun drain(codec: MediaCodec, muxer: MediaMuxer, started: BooleanArray, track: IntArray): Boolean {
        val info = MediaCodec.BufferInfo()
        var eos = false
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, 0)
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track[0] = muxer.addTrack(codec.outputFormat)
                muxer.start()
                started[0] = true
            } else if (idx >= 0) {
                val ob = codec.getOutputBuffer(idx)
                if (ob != null && info.size > 0 && started[0]) muxer.writeSampleData(track[0], ob, info)
                codec.releaseOutputBuffer(idx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eos = true
            }
            if (eos) break
        }
        return eos
    }

    fun stop() {
        running.set(false)
        if (this::thread.isInitialized) thread.join(3000)
    }
}
