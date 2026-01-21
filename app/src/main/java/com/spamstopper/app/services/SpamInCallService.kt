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
import android.net.Uri
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
import com.spamstopper.app.data.database.BlockedCallDao
import com.spamstopper.app.data.database.entities.BlockedCall
import com.spamstopper.app.data.model.CallCategory
import com.spamstopper.app.data.repository.ContactsRepository
import com.spamstopper.app.presentation.incall.InCallActivity
import com.spamstopper.app.services.ai.SecretaryModeManager
import com.spamstopper.app.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * ============================================================================
 * SpamInCallService.kt - Servicio principal de gestión de llamadas
 * ============================================================================
 *
 * PROPÓSITO:
 * Servicio InCallService que intercepta todas las llamadas entrantes y salientes.
 * Implementa el Modo Secretaria para analizar y filtrar llamadas de spam.
 *
 * ACTUALIZADO: Enero 2026 - Tono personalizado y guardado en BD
 * ============================================================================
 */

@AndroidEntryPoint
class SpamInCallService : InCallService() {

    @Inject lateinit var secretaryModeManager: SecretaryModeManager
    @Inject lateinit var contactsRepository: ContactsRepository
    @Inject lateinit var blockedCallDao: BlockedCallDao

    companion object {
        private const val TAG = "SpamInCallService"

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
                Call.STATE_DIALING, Call.STATE_CONNECTING -> stopRinging()
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

        if (!isIncoming) {
            launchInCallUI(phoneNumber, false, null)
            return
        }

        serviceScope.launch { handleIncomingCall(call, phoneNumber) }
    }

    private suspend fun handleIncomingCall(call: Call, phoneNumber: String) {
        // 1. VERIFICAR SI ES CONTACTO GUARDADO
        val contactName = withContext(Dispatchers.IO) {
            contactsRepository.getContactNameByNumber(phoneNumber)
        }
        val isContact = contactName != null

        if (isContact && isAllowContactsEnabled()) {
            android.util.Log.d(TAG, "✅ CONTACTO GUARDADO: $contactName")
            startRinging()
            launchInCallUI(phoneNumber, true, null)
            return
        }

        // 2. VERIFICAR SI SECRETARY MODE ESTÁ ACTIVADO
        val secretaryModeEnabled = isSecretaryModeEnabled()

        android.util.Log.d(TAG, "═══════════════════════════════════════")
        android.util.Log.d(TAG, "⚙️ Secretary Mode: ${if (secretaryModeEnabled) "ACTIVADO ✅" else "DESACTIVADO ❌"}")
        android.util.Log.d(TAG, "═══════════════════════════════════════")

        if (!secretaryModeEnabled) {
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

        handler.postDelayed({
            answerCallSilently(call)
        }, 300L)

        secretaryModeManager.startAnalysis(phoneNumber) { result ->
            handleAnalysisResult(call, phoneNumber, contactName, result)
        }
    }

    private fun answerCallSilently(call: Call) {
        try {
            android.util.Log.d(TAG, "🔇 CONTESTANDO EN SILENCIO")

            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = false
            audioManager?.isMicrophoneMute = false
            audioManager?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0)

            call.answer(VideoProfile.STATE_AUDIO_ONLY)

            android.util.Log.d(TAG, "✅ Llamada contestada en modo silencioso")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error contestando: ${e.message}")
        }
    }

    private fun handleAnalysisResult(
        call: Call,
        phoneNumber: String,
        contactName: String?,
        result: SecretaryModeManager.AnalysisResult
    ) {
        lastAnalysisResult = result
        isInSecretaryMode = false

        android.util.Log.d(TAG, "═══════════════════════════════════════")
        android.util.Log.d(TAG, "📊 RESULTADO DEL ANÁLISIS")
        android.util.Log.d(TAG, "   Decisión: ${result.decision}")
        android.util.Log.d(TAG, "   Categoría: ${result.category?.displayName ?: result.legitimacyReason?.displayName}")
        android.util.Log.d(TAG, "   Confianza: ${(result.confidence * 100).toInt()}%")
        android.util.Log.d(TAG, "═══════════════════════════════════════")

        // Guardar en base de datos
        serviceScope.launch(Dispatchers.IO) {
            saveCallToDatabase(phoneNumber, contactName, result)
        }

        if (result.shouldHangUp()) {
            // SPAM/ROBOT DETECTADO
            android.util.Log.d(TAG, "🚫 SPAM/ROBOT - Colgando")
            hangUp()
            showBlockedCallNotification(phoneNumber, result)
        } else {
            // LLAMADA LEGÍTIMA
            android.util.Log.d(TAG, "✅ LLAMADA LEGÍTIMA - Alertando")

            // Restaurar volumen
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) ?: 7
            audioManager?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)

            // Sonar con tono personalizado
            startRinging()

            launchInCallUI(phoneNumber, true, result)
            showLegitimateCallNotification(phoneNumber, result)
        }
    }

    /**
     * Guarda la llamada en la base de datos
     */
    private suspend fun saveCallToDatabase(
        phoneNumber: String,
        contactName: String?,
        result: SecretaryModeManager.AnalysisResult
    ) {
        try {
            val category = when {
                result.decision == SecretaryModeManager.CallClassification.ROBOT ->
                    CallCategory.SPAM_ROBOT
                result.decision == SecretaryModeManager.CallClassification.SPAM ->
                    result.category?.let { mapSpamCategory(it) } ?: CallCategory.SPAM_GENERIC
                result.decision == SecretaryModeManager.CallClassification.EMERGENCY ->
                    CallCategory.LEGITIMATE_EMERGENCY
                result.decision == SecretaryModeManager.CallClassification.LEGITIMATE ->
                    result.legitimacyReason?.let { mapLegitimacyReason(it) } ?: CallCategory.LEGITIMATE_HUMAN
                else -> CallCategory.UNCERTAIN
            }

            val blockedCall = BlockedCall(
                phoneNumber = phoneNumber,
                contactName = contactName,
                category = category,
                timestamp = System.currentTimeMillis(),
                analysisSeconds = (result.analysisTimeMs / 1000).toInt(),
                confidence = result.confidence,
                wasBlocked = result.shouldHangUp(),
                wasAlerted = result.shouldAlertUser(),
                detectedKeywords = result.detectedKeywords.take(10).joinToString(","),
                partialTranscript = result.transcript.take(200)
            )

            blockedCallDao.insert(blockedCall)

            android.util.Log.d(TAG, "💾 Llamada guardada en BD:")
            android.util.Log.d(TAG, "   Categoría: ${category.displayName}")
            android.util.Log.d(TAG, "   Bloqueada: ${blockedCall.wasBlocked}")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error guardando en BD: ${e.message}")
        }
    }

    private fun mapSpamCategory(category: SecretaryModeManager.SpamCategory): CallCategory {
        return when (category) {
            SecretaryModeManager.SpamCategory.ROBOT -> CallCategory.SPAM_ROBOT
            SecretaryModeManager.SpamCategory.TELEMARKETING -> CallCategory.SPAM_TELEMARKETING
            SecretaryModeManager.SpamCategory.SURVEYS -> CallCategory.SPAM_SURVEYS
            SecretaryModeManager.SpamCategory.SCAM -> CallCategory.SPAM_SCAM
            SecretaryModeManager.SpamCategory.RELIGIOUS -> CallCategory.SPAM_RELIGIOUS
            SecretaryModeManager.SpamCategory.POLITICAL -> CallCategory.SPAM_POLITICAL
            SecretaryModeManager.SpamCategory.FINANCIAL -> CallCategory.SPAM_FINANCIAL
            SecretaryModeManager.SpamCategory.INSURANCE -> CallCategory.SPAM_INSURANCE
            SecretaryModeManager.SpamCategory.ENERGY -> CallCategory.SPAM_ENERGY
            SecretaryModeManager.SpamCategory.TELECOM -> CallCategory.SPAM_TELECOM
            SecretaryModeManager.SpamCategory.UNKNOWN_SPAM -> CallCategory.SPAM_GENERIC
        }
    }

    private fun mapLegitimacyReason(reason: SecretaryModeManager.LegitimacyReason): CallCategory {
        return when (reason) {
            SecretaryModeManager.LegitimacyReason.SAID_USER_NAME -> CallCategory.LEGITIMATE_MENTIONS_USER
            SecretaryModeManager.LegitimacyReason.SAID_FAMILY_NAME -> CallCategory.LEGITIMATE_FAMILY
            SecretaryModeManager.LegitimacyReason.WORK_RELATED -> CallCategory.LEGITIMATE_WORK
            SecretaryModeManager.LegitimacyReason.EMERGENCY_KEYWORDS -> CallCategory.LEGITIMATE_EMERGENCY
            SecretaryModeManager.LegitimacyReason.OFFICIAL_ENTITY -> CallCategory.LEGITIMATE_OFFICIAL
            SecretaryModeManager.LegitimacyReason.MEDICAL -> CallCategory.LEGITIMATE_MEDICAL
            SecretaryModeManager.LegitimacyReason.SCHOOL -> CallCategory.LEGITIMATE_SCHOOL
            SecretaryModeManager.LegitimacyReason.DELIVERY -> CallCategory.LEGITIMATE_DELIVERY
            SecretaryModeManager.LegitimacyReason.HUMAN_CONVERSATION -> CallCategory.LEGITIMATE_HUMAN
        }
    }

    /**
     * Inicia el tono de llamada con tono personalizado
     */
    private fun startRinging() {
        if (isRinging) return
        isRinging = true

        try {
            val customRingtoneUri = getCustomRingtoneUri()
            val ringtoneUri = customRingtoneUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            android.util.Log.d(TAG, "🔔 Usando tono: $ringtoneUri")

            ringtone = RingtoneManager.getRingtone(this, ringtoneUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error con tono, usando default: ${e.message}")
            try {
                ringtone = RingtoneManager.getRingtone(
                    this,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                )
                ringtone?.play()
            } catch (e2: Exception) {
                android.util.Log.e(TAG, "Error iniciando ringtone: ${e2.message}")
            }
        }

        // Vibración
        try {
            if (isVibrationEnabled()) {
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
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error vibración: ${e.message}")
        }
    }

    private fun getCustomRingtoneUri(): Uri? {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString("notification_ringtone_uri", null)
        return uriString?.let {
            try { Uri.parse(it) } catch (e: Exception) { null }
        }
    }

    private fun stopRinging() {
        if (!isRinging) return
        isRinging = false

        try { ringtone?.stop(); ringtone = null } catch (e: Exception) {}
        try { vibrator?.cancel() } catch (e: Exception) {}
    }

    private fun onCallActive() {
        android.util.Log.d(TAG, "📞 Llamada ACTIVA")

        if (!isInSecretaryMode) {
            stopRinging()
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) ?: 7
            audioManager?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
        }
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
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error lanzando UI: ${e.message}")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            nm?.createNotificationChannel(
                NotificationChannel(
                    "incoming_call_channel",
                    "Llamadas entrantes",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { setSound(null, null) }
            )

            nm?.createNotificationChannel(
                NotificationChannel(
                    "blocked_call_channel",
                    "Llamadas bloqueadas",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private fun showBlockedCallNotification(
        phoneNumber: String,
        result: SecretaryModeManager.AnalysisResult
    ) {
        val category = result.category
        val emoji = category?.emoji ?: "🚫"
        val categoryName = category?.displayName ?: "Spam"

        val notification = NotificationCompat.Builder(this, "blocked_call_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🛡️ Llamada bloqueada")
            .setContentText("$phoneNumber")
            .setSubText("$emoji $categoryName")
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
        val reason = result.legitimacyReason
        val emoji = reason?.emoji ?: "✅"
        val reasonName = reason?.displayName ?: "Verificada"

        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(InCallActivity.EXTRA_PHONE_NUMBER, phoneNumber)
        }

        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "incoming_call_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📞 Llamada verificada")
            .setContentText("$phoneNumber - $emoji $reasonName")
            .setFullScreenIntent(pi, true)
            .setOngoing(true)
            .build()

        getSystemService(NotificationManager::class.java)?.notify(2001, notification)
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java)?.cancel(2001)
    }

    private fun isSecretaryModeEnabled(): Boolean {
        return getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(Constants.PREF_AUTO_ANSWER_ENABLED, false)
    }

    private fun isAllowContactsEnabled(): Boolean {
        return getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(Constants.PREF_ALLOW_CONTACTS, true)
    }

    private fun isVibrationEnabled(): Boolean {
        return getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("vibration_enabled", true)
    }

    private fun restoreAudio() {
        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager?.isSpeakerphoneOn = false
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) ?: 7
        audioManager?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
    }

    // Funciones públicas para InCallActivity
    fun answerCall() {
        currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        stopRinging()
    }

    fun rejectCall() {
        currentCall?.reject(false, null)
        stopRinging()
    }

    fun hangUp() {
        currentCall?.disconnect()
        stopRinging()
    }

    fun setSpeakerOn(on: Boolean) {
        audioManager?.isSpeakerphoneOn = on
    }

    fun setMuteOn(on: Boolean) {
        audioManager?.isMicrophoneMute = on
    }

    /**
     * Llamado cuando el usuario contesta una llamada verificada por Secretary Mode.
     * Para el tono y restaura el audio normal.
     */
    fun userAnsweredVerifiedCall() {
        android.util.Log.d(TAG, "✅ Usuario contestó llamada verificada - parando tono")
        
        // Parar el tono
        stopRinging()
        
        // Restaurar audio normal
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) ?: 7
        audioManager?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
        audioManager?.isSpeakerphoneOn = false
        
        // Cancelar notificación
        cancelNotification()
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
