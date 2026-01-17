package com.spamstopper.app.services

import android.telecom.Call
import android.telecom.CallScreeningService
import com.spamstopper.app.data.repository.ContactsRepository
import com.spamstopper.app.domain.model.CallDecision
import com.spamstopper.app.services.ai.CallAnalysisOrchestrator
import com.spamstopper.app.services.ai.EmergencyKeywordDetector
import com.spamstopper.app.services.ai.RobotCallDetector
import com.spamstopper.app.utils.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Servicio de screening de llamadas COMPLETO
 *
 * Intercepta llamadas entrantes y decide automáticamente:
 * - Contactos → Permitir directo
 * - Robots → Bloquear automático
 * - Emergencias → Alertar con notificación urgente
 * - Spam → Bloquear
 * - Desconocidos legítimos → Permitir
 */
@AndroidEntryPoint
class SpamCallScreeningService : CallScreeningService() {

    @Inject
    lateinit var callAnalysisOrchestrator: CallAnalysisOrchestrator

    @Inject
    lateinit var contactsRepository: ContactsRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var emergencyDetector: EmergencyKeywordDetector

    @Inject
    lateinit var robotDetector: RobotCallDetector

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("SpamStopper", "═══════════════════════════════════════")
        android.util.Log.d("SpamStopper", "🚀 CallScreeningService COMPLETO iniciado")
        android.util.Log.d("SpamStopper", "═══════════════════════════════════════")

        // Inicializar orquestador en background
        serviceScope.launch {
            try {
                callAnalysisOrchestrator.initialize()
            } catch (e: Exception) {
                android.util.Log.e("SpamStopper", "Error inicializando orquestador: ${e.message}")
            }
        }
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "Desconocido"

        android.util.Log.d("SpamStopper", "═══════════════════════════════════════")
        android.util.Log.d("SpamStopper", "📞 LLAMADA ENTRANTE INTERCEPTADA")
        android.util.Log.d("SpamStopper", "Número: $phoneNumber")
        android.util.Log.d("SpamStopper", "Hora: ${java.util.Date()}")
        android.util.Log.d("SpamStopper", "═══════════════════════════════════════")

        serviceScope.launch {
            try {
                // Analizar llamada con el orquestador
                val decision = callAnalysisOrchestrator.analyzeCall(phoneNumber)

                android.util.Log.d("SpamStopper", "🎯 Decisión tomada: $decision")
                android.util.Log.d("SpamStopper", "${decision.getEmoji()} ${decision.getDescription()}")

                // Actuar según la decisión
                when (decision) {
                    CallDecision.ALLOW_DIRECT -> {
                        // Contacto guardado - Pasar directo sin notificación
                        allowCall(callDetails)
                    }

                    CallDecision.ALLOW -> {
                        // Número desconocido pero legítimo - Permitir
                        allowCall(callDetails)
                        notificationHelper.notifyAnalysisComplete(
                            phoneNumber = phoneNumber,
                            decision = decision,
                            details = "Llamada analizada - No se detectó spam"
                        )
                    }

                    CallDecision.BLOCK_ROBOT -> {
                        // Robot detectado - Bloquear y notificar
                        blockCall(callDetails, "Robot/IVR detectado")

                        // Obtener detalles del robot
                        val confidence = 0.9f // TODO: Obtener del análisis real
                        val patterns = listOf("Patrón IVR detectado")

                        notificationHelper.notifyRobotBlocked(
                            phoneNumber = phoneNumber,
                            confidence = confidence,
                            detectedPatterns = patterns
                        )
                    }

                    CallDecision.BLOCK_SPAM -> {
                        // Spam detectado - Bloquear y notificar
                        blockCall(callDetails, "Spam comercial detectado")

                        notificationHelper.notifySpamBlocked(
                            phoneNumber = phoneNumber,
                            spamScore = 0.85f, // TODO: Obtener del análisis real
                            reason = "Palabras clave comerciales detectadas"
                        )
                    }

                    CallDecision.ALERT_EMERGENCY -> {
                        // Emergencia - Permitir y notificar con MÁXIMA PRIORIDAD
                        allowCall(callDetails)

                        // Obtener detalles de la emergencia
                        // TODO: Pasar transcript real cuando esté disponible
                        val emergencyType = null // emergencyDetector.getEmergencyType(transcript)
                        val keywords = listOf("emergencia") // emergencyDetector.getDetectedKeywords(transcript)

                        notificationHelper.notifyEmergency(
                            phoneNumber = phoneNumber,
                            emergencyType = emergencyType,
                            detectedKeywords = keywords
                        )
                    }
                }

                android.util.Log.d("SpamStopper", "═══════════════════════════════════════")

            } catch (e: Exception) {
                android.util.Log.e("SpamStopper", "❌ Error en screening: ${e.message}")
                android.util.Log.e("SpamStopper", "Stack trace:", e)

                // En caso de error, permitir la llamada por seguridad
                allowCall(callDetails)
            }
        }
    }

    /**
     * Permite que la llamada pase
     */
    private fun allowCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)
        android.util.Log.d("SpamStopper", "✅ Llamada permitida")
    }

    /**
     * Bloquea la llamada
     */
    private fun blockCall(callDetails: Call.Details, reason: String) {
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false) // Guardar en log para historial
            .setSkipNotification(false) // Mostrar notificación de bloqueo
            .build()

        respondToCall(callDetails, response)
        android.util.Log.d("SpamStopper", "🚫 Llamada bloqueada: $reason")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        callAnalysisOrchestrator.release()
        android.util.Log.d("SpamStopper", "═══════════════════════════════════════")
        android.util.Log.d("SpamStopper", "🛑 CallScreeningService destruido")
        android.util.Log.d("SpamStopper", "═══════════════════════════════════════")
        super.onDestroy()
    }
}