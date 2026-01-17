package com.spamstopper.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import com.spamstopper.app.R
import com.spamstopper.app.data.repository.ContactsRepository
import com.spamstopper.app.presentation.incall.InCallActivity
import com.spamstopper.app.services.ai.SecretaryModeManager
import com.spamstopper.app.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class SpamInCallService : InCallService() {

    @Inject lateinit var secretaryModeManager: SecretaryModeManager
    @Inject lateinit var contactsRepository: ContactsRepository

    companion object {
        private const val TAG = "SpamInCallService"
        private const val CHANNEL_ID = "incoming_call_channel"
        private const val CHANNEL_SECRETARY = "secretary_mode_channel"
        private const val NOTIFICATION_ID = 2001

        @Volatile var instance: SpamInCallService? = null
            private set
        @Volatile var currentCall: Call? = null
            private set
        @Volatile var lastAnalysisResult: SecretaryModeManager.AnalysisResult? = null
            private set

        fun getCallState(): Int = currentCall?.state ?: Call.STATE_DISCONNECTED
        fun getPhoneNumber(): String = currentCall?.details?.handle?.schemeSpecificPart ?: ""
    }

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var audioManager: AudioManager? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isRinging = false
    private var isInSecretaryMode = false

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            android.util.Log.d(TAG, "📞 Estado: ${getStateName(state)}")
            when (state) {
                Call.STATE_DIALING, Call.STATE_CONNECTING -> { stopRinging() }
                Call.STATE_ACTIVE -> onCallActive()
                Call.STATE_DISCONNECTED -> onCallDisconnected()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        createNotificationChannels()
        android.util.Log.d(TAG, "🚀 SpamInCallService CREADO")
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Desconocido"
        val isIncoming = call.details?.callDirection == Call.Details.DIRECTION_INCOMING

        android.util.Log.d(TAG, "═══════════════════════════════════════")
        android.util.Log.d(TAG, "📲 LLAMADA ${if (isIncoming) "ENTRANTE" else "SALIENTE"}")
        android.util.Log.d(TAG, "   Número: $phoneNumber")
        android.util.Log.d(TAG, "═══════════════════════════════════════")

        currentCall = call
        lastAnalysisResult = null
        call.registerCallback(callCallback)

        // LLAMADAS SALIENTES: Solo mostrar UI
        if (!isIncoming) {
            launchInCallUI(phoneNumber, false, null)
            return
        }

        // LLAMADAS ENTRANTES: Procesar con Secretary Mode
        serviceScope.launch { handleIncomingCall(call, phoneNumber) }
    }

    private suspend fun handleIncomingCall(call: Call, phoneNumber: String) {
        // 1. VERIFICAR SI ES CONTACTO GUARDADO
        val isContact = withContext(Dispatchers.IO) {
            contactsRepository.isContact(phoneNumber)
        }

        if (isContact) {
            android.util.Log.d(TAG, "✅ CONTACTO GUARDADO - Pasar llamada directamente")
            android.util.Log.d(TAG, "   No se activa Secretary Mode")
            startRinging()
            launchInCallUI(phoneNumber, true, null)
            return
        }

        // 2. VERIFICAR SI SECRETARY MODE ESTÁ ACTIVADO
        val secretaryModeEnabled = isSecretaryModeEnabled()

        android.util.Log.d(TAG, "═══════════════════════════════════════")
        android.util.Log.d(TAG, "⚙️ VERIFICANDO CONFIGURACIÓN")
        android.util.Log.d(TAG, "   Secretary Mode: ${if (secretaryModeEnabled) "ACTIVADO ✅" else "DESACTIVADO ❌"}")
        android.util.Log.d(TAG, "═══════════════════════════════════════")

        if (!secretaryModeEnabled) {
            android.util.Log.d(TAG, "📞 Modo normal - Sonar y esperar usuario")
            startRinging()
            launchInCallUI(phoneNumber, true, null)
            return
        }

        // 3. ACTIVAR SECRETARY MODE
        android.util.Log.d(TAG, "🤖 ═══════════════════════════════════════")
        android.util.Log.d(TAG, "🤖 ACTIVANDO MODO SECRETARIA")
        android.util.Log.d(TAG, "🤖 Número desconocido: $phoneNumber")
        android.util.Log.d(TAG, "🤖 ═══════════════════════════════════════")

        isInSecretaryMode = true

        // CONTESTAR INMEDIATAMENTE EN SILENCIO
        handler.postDelayed({
            answerCallSilently(call)
        }, 300) // 300ms = primer tono

        // INICIAR ANÁLISIS
        secretaryModeManager.startAnalysis(phoneNumber) { result ->
            handleAnalysisResult(call, phoneNumber, result)
        }
    }

    /**
     * ⭐ CLAVE: Contestar en SILENCIO para Secretary Mode
     */
    private fun answerCallSilently(call: Call) {
        try {
            android.util.Log.d(TAG, "🔇 ═══════════════════════════════════════")
            android.util.Log.d(TAG, "🔇 CONTESTANDO AUTOMÁTICAMENTE EN SILENCIO")
            android.util.Log.d(TAG, "🔇 ═══════════════════════════════════════")

            // Configurar audio para llamada silenciosa
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = false
            audioManager?.isMicrophoneMute = false // NO silenciar micrófono (necesitamos capturar audio)

            // Bajar volumen del auricular a 0 para que el usuario no escuche nada
            audioManager?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0)

            // CONTESTAR LLAMADA
            call.answer(VideoProfile.STATE_AUDIO_ONLY)

            android.util.Log.d(TAG, "✅ Llamada contestada en modo silencioso")
            android.util.Log.d(TAG, "   Audio mode: MODE_IN_COMMUNICATION")
            android.util.Log.d(TAG, "   Speaker: OFF")
            android.util.Log.d(TAG, "   Mic mute: OFF (capturando)")
            android.util.Log.d(TAG, "   Volume: 0 (usuario no escucha)")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error contestando automáticamente", e)
        }
    }

    private fun handleAnalysisResult(
        call: Call,
        phoneNumber: String,
        result: SecretaryModeManager.AnalysisResult
    ) {
        lastAnalysisResult = result
        isInSecretaryMode = false

        android.util.Log.d(TAG, "═══════════════════════════════════════")
        android.util.Log.d(TAG, "📊 RESULTADO DEL ANÁLISIS")
        android.util.Log.d(TAG, "   Decisión: ${result.decision}")
        android.util.Log.d(TAG, "   Categoría: ${result.category?.displayName ?: result.legitimacyReason?.displayName}")
        android.util.Log.d(TAG, "   Confianza: ${(result.confidence * 100).toInt()}%")
        android.util.Log.d(TAG, "   Keywords: ${result.detectedKeywords.joinToString()}")
        android.util.Log.d(TAG, "═══════════════════════════════════════")

        if (result.shouldHangUp()) {
            // SPAM/ROBOT DETECTADO
            android.util.Log.d(TAG, "🚫 SPAM/ROBOT - Colgando y bloqueando")
            hangUp()
            showBlockedCallNotification(phoneNumber, result)
            // TODO: Guardar en lista negra
        } else {
            // LLAMADA LEGÍTIMA
            android.util.Log.d(TAG, "✅ LLAMADA LEGÍTIMA - Alertando al usuario")

            // Restaurar volumen
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) ?: 7
            audioManager?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)

            // Sonar para alertar al usuario
            startRinging()

            // Mostrar UI
            launchInCallUI(phoneNumber, true, result)
            showLegitimateCallNotification(phoneNumber, result)
        }
    }

    private fun onCallActive() {
        android.util.Log.d(TAG, "📞 Llamada ACTIVA")

        if (!isInSecretaryMode) {
            stopRinging()
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

            // Restaurar volumen normal si no estamos en secretary mode
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) ?: 7
            audioManager?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
        }
        // Si estamos en secretary mode, mantener volumen en 0
    }

    private fun onCallDisconnected() {
        android.util.Log.d(TAG, "📞 Llamada DESCONECTADA")
        isInSecretaryMode = false
        secretaryModeManager.stopAnalysis()
        stopRinging()
        cancelNotification()
        restoreAudio()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        android.util.Log.d(TAG, "📞 Llamada REMOVIDA")

        call.unregisterCallback(callCallback)
        if (currentCall == call) currentCall = null
        isInSecretaryMode = false
        secretaryModeManager.stopAnalysis()
        stopRinging()
        cancelNotification()
    }

    private fun launchInCallUI(
        phoneNumber: String,
        isIncoming: Boolean,
        result: SecretaryModeManager.AnalysisResult?
    ) {
        try {
            val intent = Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(InCallActivity.EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra("is_incoming", isIncoming)
                result?.let {
                    putExtra("analysis_decision", it.decision.name)
                    putExtra("analysis_category", it.category?.name)
                    putExtra("analysis_reason", it.legitimacyReason?.name)
                    putExtra("analysis_confidence", it.confidence)
                    putExtra("analysis_keywords", it.detectedKeywords.toTypedArray())
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error lanzando UI", e)
        }
    }

    private fun startRinging() {
        if (isRinging) return
        isRinging = true

        try {
            ringtone = RingtoneManager.getRingtone(
                this,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error iniciando ringtone", e)
        }

        try {
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error iniciando vibración", e)
        }
    }

    private fun stopRinging() {
        if (!isRinging) return
        isRinging = false

        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {}

        try {
            vibrator?.cancel()
        } catch (e: Exception) {}
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Llamadas",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                }
            )

            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SECRETARY,
                    "Modo Secretaria",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private fun showBlockedCallNotification(
        phoneNumber: String,
        result: SecretaryModeManager.AnalysisResult
    ) {
        val text = result.category?.let { "${it.emoji} ${it.displayName}" } ?: "Spam"
        val notification = NotificationCompat.Builder(this, CHANNEL_SECRETARY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🛡️ Llamada bloqueada")
            .setContentText("$phoneNumber - $text")
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)?.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }

    private fun showLegitimateCallNotification(
        phoneNumber: String,
        result: SecretaryModeManager.AnalysisResult
    ) {
        val text = result.legitimacyReason?.let {
            "${it.emoji} ${it.displayName}"
        } ?: "Verificada"

        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(InCallActivity.EXTRA_PHONE_NUMBER, phoneNumber)
        }

        val pi = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📞 Llamada entrante")
            .setContentText("$phoneNumber - $text")
            .setFullScreenIntent(pi, true)
            .setOngoing(true)
            .build()

        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /**
     * ⭐ Verifica si Secretary Mode está activado
     */
    private fun isSecretaryModeEnabled(): Boolean {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(Constants.PREF_AUTO_ANSWER_ENABLED, false)

        android.util.Log.d(TAG, "📋 Leyendo configuración:")
        android.util.Log.d(TAG, "   SharedPrefs: ${Constants.PREFS_NAME}")
        android.util.Log.d(TAG, "   Key: ${Constants.PREF_AUTO_ANSWER_ENABLED}")
        android.util.Log.d(TAG, "   Valor: $enabled")

        return enabled
    }

    private fun restoreAudio() {
        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager?.isSpeakerphoneOn = false
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) ?: 7
        audioManager?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
    }

    // Funciones públicas para InCallActivity
    fun answerCall() {
        try {
            currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
            stopRinging()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error contestando", e)
        }
    }

    fun rejectCall() {
        try {
            currentCall?.reject(false, null)
            stopRinging()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error rechazando", e)
        }
    }

    fun hangUp() {
        try {
            currentCall?.disconnect()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error colgando", e)
        }
    }

    fun setSpeakerOn(on: Boolean) {
        audioManager?.isSpeakerphoneOn = on
    }

    fun setMuteOn(on: Boolean) {
        audioManager?.isMicrophoneMute = on
    }

    private fun getStateName(s: Int) = when(s) {
        Call.STATE_RINGING -> "RINGING"
        Call.STATE_ACTIVE -> "ACTIVE"
        Call.STATE_DISCONNECTED -> "DISCONNECTED"
        Call.STATE_DIALING -> "DIALING"
        Call.STATE_CONNECTING -> "CONNECTING"
        else -> "OTHER($s)"
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d(TAG, "🔴 SpamInCallService DESTRUIDO")

        serviceScope.cancel()
        currentCall?.unregisterCallback(callCallback)
        currentCall = null
        instance = null
        lastAnalysisResult = null
        secretaryModeManager.stopAnalysis()
        stopRinging()
        cancelNotification()
        restoreAudio()
    }
}