package com.spamstopper.app.services.ai

import android.content.Context
import android.util.Log
import com.spamstopper.app.data.preferences.UserPreferences
import com.spamstopper.app.data.repository.ContactsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SecretaryModeManager - Orquestador del Modo Secretaria
 *
 * Gestiona el análisis silencioso de llamadas entrantes:
 * 1. Contesta automáticamente en silencio
 * 2. Captura y analiza audio en tiempo real
 * 3. Detecta spam/robots vs llamadas legítimas
 * 4. Decide si alertar al usuario o colgar silenciosamente
 */
@Singleton
class SecretaryModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioCaptureManager: AudioCaptureManager,
    private val voskSTTEngine: VoskSTTEngine,
    private val robotDetector: RobotCallDetector,
    private val spamClassifier: SpamClassifier,
    private val emergencyDetector: EmergencyKeywordDetector,
    private val legitimacyDetector: LegitimacyDetector,
    private val contactsRepository: ContactsRepository,
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val TAG = "SecretaryMode"
        private const val DEFAULT_ANALYSIS_DURATION_MS = 12000L // 12 segundos máx
        private const val CHUNK_INTERVAL_MS = 2000L // Analizar cada 2 segundos
        private const val BEEP_CHECK_INTERVAL_MS = 500L // Verificar pitidos cada 500ms
    }

    private var analysisJob: Job? = null
    private var isAnalyzing = false

    // Callback para notificar resultados
    var onAnalysisComplete: ((AnalysisResult) -> Unit)? = null

    /**
     * Resultado del análisis de una llamada
     */
    data class AnalysisResult(
        val decision: CallClassification,
        val category: SpamCategory?,
        val legitimacyReason: LegitimacyReason?,
        val confidence: Float,
        val transcript: String,
        val detectedKeywords: List<String>,
        val analysisTimeMs: Long
    ) {
        fun shouldAlertUser(): Boolean = decision == CallClassification.LEGITIMATE ||
                decision == CallClassification.EMERGENCY ||
                decision == CallClassification.UNCERTAIN

        fun shouldHangUp(): Boolean = decision == CallClassification.SPAM ||
                decision == CallClassification.ROBOT
    }

    /**
     * Clasificación de la llamada
     */
    enum class CallClassification {
        LEGITIMATE,  // Llamada legítima - alertar usuario
        EMERGENCY,   // Emergencia detectada - alertar URGENTE
        SPAM,        // Spam detectado - colgar
        ROBOT,       // Robot/IVR detectado - colgar
        UNCERTAIN    // No se pudo determinar - alertar por seguridad
    }

    /**
     * Categorías de spam
     */
    enum class SpamCategory(val displayName: String, val emoji: String) {
        ROBOT("Robot/Marcador automático", "🤖"),
        TELEMARKETING("Telemarketing/Ventas", "📞"),
        SURVEYS("Encuestas", "📋"),
        SCAM("Estafa/Premio falso", "⚠️"),
        RELIGIOUS("Propaganda religiosa", "⛪"),
        POLITICAL("Propaganda política", "🗳️"),
        FINANCIAL("Servicios financieros", "💰"),
        INSURANCE("Seguros", "🛡️"),
        ENERGY("Compañías energéticas", "⚡"),
        TELECOM("Operadoras telefónicas", "📱"),
        UNKNOWN_SPAM("Spam no identificado", "🚫")
    }

    /**
     * Razones por las que una llamada se considera legítima
     */
    enum class LegitimacyReason(val displayName: String, val emoji: String) {
        SAID_USER_NAME("Mencionó tu nombre", "👤"),
        SAID_FAMILY_NAME("Mencionó familiar", "👨‍👩‍👧"),
        WORK_RELATED("Relacionado con trabajo", "💼"),
        EMERGENCY_KEYWORDS("Palabras de emergencia", "🚨"),
        OFFICIAL_ENTITY("Entidad oficial", "🏛️"),
        MEDICAL("Tema médico/hospital", "🏥"),
        SCHOOL("Colegio/Escuela", "🏫"),
        DELIVERY("Entrega/Paquete", "📦"),
        HUMAN_CONVERSATION("Conversación humana normal", "💬")
    }

    /**
     * Inicia el análisis silencioso de una llamada
     *
     * @param phoneNumber Número de teléfono
     * @param onResult Callback con el resultado
     */
    fun startAnalysis(phoneNumber: String, onResult: (AnalysisResult) -> Unit) {
        if (isAnalyzing) {
            Log.w(TAG, "⚠️ Ya hay un análisis en curso")
            return
        }

        onAnalysisComplete = onResult
        isAnalyzing = true

        analysisJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "🎧 INICIANDO MODO SECRETARIA")
            Log.d(TAG, "   Número: $phoneNumber")
            Log.d(TAG, "═══════════════════════════════════════")

            val startTime = System.currentTimeMillis()
            val transcriptBuilder = StringBuilder()
            var detectedKeywords = mutableListOf<String>()
            var currentDecision: CallClassification = CallClassification.UNCERTAIN
            var spamCategory: SpamCategory? = null
            var legitimacyReason: LegitimacyReason? = null
            var confidence = 0f

            try {
                // Cargar configuración del usuario
                val userName = userPreferences.getUserName().first()
                val familyNames = userPreferences.getFamilyNames().first()
                val customKeywords = userPreferences.getEmergencyKeywords().first()
                val analysisDuration = userPreferences.getAnalysisDuration().first() * 1000L

                Log.d(TAG, "📋 Config: userName=$userName, familyNames=$familyNames")
                Log.d(TAG, "⏱️ Duración análisis: ${analysisDuration/1000}s")

                // Inicializar captura de audio
                if (!audioCaptureManager.initialize()) {
                    Log.e(TAG, "❌ Error inicializando captura de audio")
                    emitResult(AnalysisResult(
                        decision = CallClassification.UNCERTAIN,
                        category = null,
                        legitimacyReason = null,
                        confidence = 0f,
                        transcript = "",
                        detectedKeywords = emptyList(),
                        analysisTimeMs = System.currentTimeMillis() - startTime
                    ))
                    return@launch
                }

                // Inicializar Vosk
                voskSTTEngine.initialize()

                // Bucle de análisis
                var elapsedTime = 0L
                var chunkCount = 0

                while (isAnalyzing && elapsedTime < analysisDuration) {
                    // Capturar chunk de audio (2 segundos)
                    val audioChunk = audioCaptureManager.captureAudio(2)

                    if (audioChunk != null && audioChunk.isNotEmpty()) {
                        chunkCount++
                        Log.d(TAG, "📦 Chunk #$chunkCount capturado (${audioChunk.size} bytes)")

                        // 1. DETECTAR PITIDO DE ROBOT (prioridad máxima)
                        if (robotDetector.detectBeepInAudio(audioChunk)) {
                            Log.d(TAG, "🤖 ¡PITIDO DE ROBOT DETECTADO!")
                            currentDecision = CallClassification.ROBOT
                            spamCategory = SpamCategory.ROBOT
                            confidence = 0.95f
                            detectedKeywords.add("pitido_robot")
                            break // Terminar análisis inmediatamente
                        }

                        // 2. TRANSCRIBIR AUDIO
                        val partialTranscript = voskSTTEngine.transcribe(audioChunk)
                        if (!partialTranscript.isNullOrBlank()) {
                            transcriptBuilder.append(partialTranscript).append(" ")
                            val fullTranscript = transcriptBuilder.toString()

                            Log.d(TAG, "📝 Transcripción parcial: $partialTranscript")

                            // 3. VERIFICAR ROBOT POR TEXTO
                            if (robotDetector.isRobotCall(fullTranscript)) {
                                Log.d(TAG, "🤖 Robot detectado por texto")
                                currentDecision = CallClassification.ROBOT
                                spamCategory = SpamCategory.ROBOT
                                confidence = robotDetector.getRobotConfidence(fullTranscript)
                                detectedKeywords.addAll(robotDetector.getDetectedPatterns(fullTranscript))
                                break
                            }

                            // 4. VERIFICAR EMERGENCIA/LEGITIMIDAD
                            val legitimacyCheck = checkLegitimacy(
                                fullTranscript, userName, familyNames, customKeywords
                            )
                            if (legitimacyCheck != null) {
                                Log.d(TAG, "✅ Llamada legítima: ${legitimacyCheck.first}")
                                currentDecision = if (legitimacyCheck.first == LegitimacyReason.EMERGENCY_KEYWORDS) {
                                    CallClassification.EMERGENCY
                                } else {
                                    CallClassification.LEGITIMATE
                                }
                                legitimacyReason = legitimacyCheck.first
                                confidence = legitimacyCheck.second
                                detectedKeywords.addAll(legitimacyCheck.third)
                                break // Alertar al usuario inmediatamente
                            }

                            // 5. VERIFICAR SPAM
                            val spamCheck = spamClassifier.classify(fullTranscript)
                            if (spamCheck.isSpam && spamCheck.confidence >= 0.75f) {
                                Log.d(TAG, "🚫 Spam detectado: ${spamCheck.category}")
                                currentDecision = CallClassification.SPAM
                                spamCategory = spamCheck.category
                                confidence = spamCheck.confidence
                                detectedKeywords.addAll(spamCheck.detectedKeywords)
                                // No break - seguir analizando por si detectamos emergencia
                            }
                        }
                    }

                    elapsedTime = System.currentTimeMillis() - startTime
                    delay(100) // Pequeña pausa entre análisis
                }

                // Si no se tomó decisión definitiva
                if (currentDecision == CallClassification.UNCERTAIN) {
                    // Analizar transcripción completa una última vez
                    val fullTranscript = transcriptBuilder.toString()

                    if (fullTranscript.isNotBlank()) {
                        val finalSpamCheck = spamClassifier.classify(fullTranscript)
                        if (finalSpamCheck.isSpam && finalSpamCheck.confidence >= 0.6f) {
                            currentDecision = CallClassification.SPAM
                            spamCategory = finalSpamCheck.category
                            confidence = finalSpamCheck.confidence
                        }
                    }
                }

                val analysisTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "📊 ANÁLISIS COMPLETADO")
                Log.d(TAG, "   Decisión: $currentDecision")
                Log.d(TAG, "   Categoría: ${spamCategory?.displayName ?: legitimacyReason?.displayName}")
                Log.d(TAG, "   Confianza: ${(confidence * 100).toInt()}%")
                Log.d(TAG, "   Tiempo: ${analysisTime}ms")
                Log.d(TAG, "   Transcripción: ${transcriptBuilder.toString().take(100)}...")
                Log.d(TAG, "═══════════════════════════════════════")

                emitResult(AnalysisResult(
                    decision = currentDecision,
                    category = spamCategory,
                    legitimacyReason = legitimacyReason,
                    confidence = confidence,
                    transcript = transcriptBuilder.toString(),
                    detectedKeywords = detectedKeywords,
                    analysisTimeMs = analysisTime
                ))

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en análisis", e)
                emitResult(AnalysisResult(
                    decision = CallClassification.UNCERTAIN,
                    category = null,
                    legitimacyReason = null,
                    confidence = 0f,
                    transcript = transcriptBuilder.toString(),
                    detectedKeywords = detectedKeywords,
                    analysisTimeMs = System.currentTimeMillis() - startTime
                ))
            } finally {
                cleanup()
            }
        }
    }

    /**
     * Verifica si la llamada es legítima basándose en palabras clave
     */
    private fun checkLegitimacy(
        transcript: String,
        userName: String,
        familyNames: Set<String>,
        customKeywords: Set<String>
    ): Triple<LegitimacyReason, Float, List<String>>? {
        val lower = transcript.lowercase()
        val detectedKeywords = mutableListOf<String>()

        // 1. Verificar nombre del usuario
        if (userName.isNotBlank() && lower.contains(userName.lowercase())) {
            detectedKeywords.add(userName)
            return Triple(LegitimacyReason.SAID_USER_NAME, 0.9f, detectedKeywords)
        }

        // 2. Verificar nombres de familia
        for (familyName in familyNames) {
            if (lower.contains(familyName.lowercase())) {
                detectedKeywords.add(familyName)
                return Triple(LegitimacyReason.SAID_FAMILY_NAME, 0.85f, detectedKeywords)
            }
        }

        // 3. Verificar palabras de emergencia personalizadas
        for (keyword in customKeywords) {
            if (lower.contains(keyword.lowercase())) {
                detectedKeywords.add(keyword)
                return Triple(LegitimacyReason.EMERGENCY_KEYWORDS, 0.9f, detectedKeywords)
            }
        }

        // 4. Verificar con detector de emergencias
        if (emergencyDetector.hasEmergencyKeywords(transcript)) {
            detectedKeywords.addAll(emergencyDetector.getDetectedKeywords(transcript))
            val type = emergencyDetector.getEmergencyType(transcript)
            val reason = when (type) {
                EmergencyType.MEDICAL -> LegitimacyReason.MEDICAL
                EmergencyType.DANGER -> LegitimacyReason.EMERGENCY_KEYWORDS
                EmergencyType.FAMILY -> LegitimacyReason.SAID_FAMILY_NAME
                EmergencyType.WORK -> LegitimacyReason.WORK_RELATED
                else -> LegitimacyReason.EMERGENCY_KEYWORDS
            }
            return Triple(reason, emergencyDetector.getUrgencyLevel(transcript), detectedKeywords)
        }

        // 5. Verificar palabras de trabajo
        val workKeywords = listOf("trabajo", "oficina", "jefe", "reunión", "empresa", "cliente", "proyecto")
        for (keyword in workKeywords) {
            if (lower.contains(keyword)) {
                detectedKeywords.add(keyword)
                return Triple(LegitimacyReason.WORK_RELATED, 0.7f, detectedKeywords)
            }
        }

        // 6. Verificar entrega/paquete
        val deliveryKeywords = listOf("paquete", "entrega", "envío", "correos", "mensajero", "reparto")
        for (keyword in deliveryKeywords) {
            if (lower.contains(keyword)) {
                detectedKeywords.add(keyword)
                return Triple(LegitimacyReason.DELIVERY, 0.6f, detectedKeywords)
            }
        }

        return null
    }

    /**
     * Emite el resultado del análisis
     */
    private fun emitResult(result: AnalysisResult) {
        CoroutineScope(Dispatchers.Main).launch {
            onAnalysisComplete?.invoke(result)
        }
    }

    /**
     * Detiene el análisis en curso
     */
    fun stopAnalysis() {
        Log.d(TAG, "🛑 Deteniendo análisis...")
        isAnalyzing = false
        analysisJob?.cancel()
        cleanup()
    }

    /**
     * Limpia recursos
     */
    private fun cleanup() {
        isAnalyzing = false
        audioCaptureManager.stopCapture()
        audioCaptureManager.release()
        voskSTTEngine.reset()
    }

    /**
     * Verifica si el modo secretaria está activo
     */
    suspend fun isSecretaryModeEnabled(): Boolean {
        val prefs = context.getSharedPreferences("spamstopper_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("auto_answer_enabled", false)
    }
}