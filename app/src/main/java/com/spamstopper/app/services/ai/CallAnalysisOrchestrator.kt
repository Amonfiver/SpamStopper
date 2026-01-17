package com.spamstopper.app.services.ai

import android.content.Context
import com.spamstopper.app.data.repository.ContactsRepository
import com.spamstopper.app.domain.model.CallDecision
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestador de análisis de llamadas
 *
 * Coordina todos los motores de IA para tomar decisiones
 * inteligentes sobre llamadas entrantes
 */
@Singleton
class CallAnalysisOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voskSTTEngine: VoskSTTEngine,
    private val emergencyDetector: EmergencyKeywordDetector,
    private val robotDetector: RobotCallDetector,
    private val contactsRepository: ContactsRepository
) {

    private var isInitialized = false

    /**
     * Inicializa todos los componentes
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        try {
            android.util.Log.d("CallOrchestrator", "🚀 Inicializando orquestador...")

            // Inicializar Vosk
            voskSTTEngine.initialize()

            isInitialized = true
            android.util.Log.d("CallOrchestrator", "✅ Orquestador inicializado")
        } catch (e: Exception) {
            android.util.Log.e("CallOrchestrator", "❌ Error en inicialización: ${e.message}")
            throw e
        }
    }

    /**
     * Analiza una llamada y decide qué hacer
     *
     * Flujo de decisión:
     * 1. ¿Es contacto guardado? → ALLOW_DIRECT
     * 2. Transcribir audio
     * 3. ¿Es robot/IVR? → BLOCK_ROBOT
     * 4. ¿Tiene palabras de emergencia? → ALERT_EMERGENCY
     * 5. ¿Es spam? → BLOCK_SPAM / ALLOW
     */
    suspend fun analyzeCall(
        phoneNumber: String,
        audioData: ByteArray? = null
    ): CallDecision = withContext(Dispatchers.IO) {

        android.util.Log.d("CallOrchestrator", "═══════════════════════════════════════")
        android.util.Log.d("CallOrchestrator", "📞 ANALIZANDO LLAMADA")
        android.util.Log.d("CallOrchestrator", "Número: $phoneNumber")
        android.util.Log.d("CallOrchestrator", "═══════════════════════════════════════")

        try {
            // PASO 1: Verificar si es contacto guardado
            val isContact = contactsRepository.isContact(phoneNumber)
            if (isContact) {
                android.util.Log.d("CallOrchestrator", "✅ Contacto guardado detectado")
                android.util.Log.d("CallOrchestrator", "Decisión: ALLOW_DIRECT")
                return@withContext CallDecision.ALLOW_DIRECT
            }

            android.util.Log.d("CallOrchestrator", "⚠️ Número desconocido - Iniciando análisis...")

            // Si no hay audio, permitir por seguridad (no podemos analizar)
            if (audioData == null || audioData.isEmpty()) {
                android.util.Log.d("CallOrchestrator", "⚠️ Sin audio para analizar")
                android.util.Log.d("CallOrchestrator", "Decisión: ALLOW (por seguridad)")
                return@withContext CallDecision.ALLOW
            }

            // PASO 2: Transcribir audio
            android.util.Log.d("CallOrchestrator", "🎤 Transcribiendo audio...")
            val transcript = transcribeAudio(audioData)

            if (transcript.isEmpty()) {
                android.util.Log.d("CallOrchestrator", "⚠️ Transcripción vacía")
                android.util.Log.d("CallOrchestrator", "Decisión: ALLOW (sin datos)")
                return@withContext CallDecision.ALLOW
            }

            android.util.Log.d("CallOrchestrator", "📝 Transcripción: '$transcript'")

            // PASO 3: Detectar robot/IVR (PRIORIDAD ALTA)
            if (robotDetector.isRobotCall(transcript)) {
                val confidence = robotDetector.getRobotConfidence(transcript)
                val patterns = robotDetector.getDetectedPatterns(transcript)

                android.util.Log.d("CallOrchestrator", "🤖 ROBOT DETECTADO")
                android.util.Log.d("CallOrchestrator", "Confianza: ${(confidence * 100).toInt()}%")
                android.util.Log.d("CallOrchestrator", "Patrones: ${patterns.joinToString()}")
                android.util.Log.d("CallOrchestrator", "Decisión: BLOCK_ROBOT")

                return@withContext CallDecision.BLOCK_ROBOT
            }

            // PASO 4: Detectar emergencia (MÁXIMA PRIORIDAD)
            if (emergencyDetector.hasEmergencyKeywords(transcript)) {
                val urgencyLevel = emergencyDetector.getUrgencyLevel(transcript)
                val emergencyType = emergencyDetector.getEmergencyType(transcript)
                val keywords = emergencyDetector.getDetectedKeywords(transcript)

                android.util.Log.d("CallOrchestrator", "🚨 EMERGENCIA DETECTADA")
                android.util.Log.d("CallOrchestrator", "Tipo: ${emergencyType?.getDescription()}")
                android.util.Log.d("CallOrchestrator", "Urgencia: ${(urgencyLevel * 100).toInt()}%")
                android.util.Log.d("CallOrchestrator", "Keywords: ${keywords.joinToString()}")
                android.util.Log.d("CallOrchestrator", "Decisión: ALERT_EMERGENCY")

                return@withContext CallDecision.ALERT_EMERGENCY
            }

            // PASO 5: Análisis de spam
            android.util.Log.d("CallOrchestrator", "🔍 Analizando spam...")
            val spamScore = analyzeSpam(transcript)

            android.util.Log.d("CallOrchestrator", "📊 Score de spam: ${(spamScore * 100).toInt()}%")

            val decision = if (spamScore >= 0.7f) {
                android.util.Log.d("CallOrchestrator", "🚫 Clasificado como SPAM")
                CallDecision.BLOCK_SPAM
            } else {
                android.util.Log.d("CallOrchestrator", "✅ Clasificado como legítimo")
                CallDecision.ALLOW
            }

            android.util.Log.d("CallOrchestrator", "Decisión final: $decision")
            android.util.Log.d("CallOrchestrator", "═══════════════════════════════════════")

            return@withContext decision

        } catch (e: Exception) {
            android.util.Log.e("CallOrchestrator", "❌ Error en análisis: ${e.message}")
            android.util.Log.e("CallOrchestrator", "Decisión: ALLOW (por seguridad)")

            // En caso de error, permitir por seguridad
            return@withContext CallDecision.ALLOW
        }
    }

    /**
     * Transcribe audio usando Vosk STT
     */
    private suspend fun transcribeAudio(audioData: ByteArray): String = withContext(Dispatchers.IO) {
        try {
            // TODO: Implementar transcripción real con Vosk
            // Por ahora, retornar vacío para que funcione sin modelo
            android.util.Log.d("CallOrchestrator", "⚠️ Transcripción no implementada aún")
            return@withContext ""
        } catch (e: Exception) {
            android.util.Log.e("CallOrchestrator", "Error en transcripción: ${e.message}")
            return@withContext ""
        }
    }

    /**
     * Analiza si el contenido es spam
     */
    private fun analyzeSpam(transcript: String): Float {
        if (transcript.isEmpty()) return 0f

        val lowerTranscript = transcript.lowercase()
        var spamScore = 0f

        // Palabras clave de spam
        val spamKeywords = setOf(
            "oferta", "promoción", "gratis", "descuento", "premio",
            "ganador", "sorteo", "regalo", "limitada", "aprovecha",
            "oportunidad", "llamada comercial", "inversión", "préstamo",
            "deuda", "banco", "tarjeta", "crédito", "financiación",
            "contrato", "renovar", "abonar", "pagar ahora"
        )

        val spamCount = spamKeywords.count { lowerTranscript.contains(it) }

        // Score basado en cantidad de palabras spam
        spamScore = when {
            spamCount >= 4 -> 0.95f  // 4+ palabras = muy probable spam
            spamCount >= 3 -> 0.85f  // 3 palabras = probable spam
            spamCount >= 2 -> 0.70f  // 2 palabras = posible spam
            spamCount == 1 -> 0.40f  // 1 palabra = sospechoso
            else -> 0.15f            // 0 palabras = probablemente legítimo
        }

        android.util.Log.d("CallOrchestrator", "Palabras spam detectadas: $spamCount")

        return spamScore
    }

    /**
     * Libera recursos
     */
    fun release() {
        voskSTTEngine.release()
        isInitialized = false
        android.util.Log.d("CallOrchestrator", "🛑 Orquestador liberado")
    }
}