package com.spamstopper.app.services.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * LegitimacyDetector - Detector de llamadas legítimas
 *
 * Identifica patrones que indican que una llamada es genuina:
 * - Menciones de nombres personales
 * - Contexto de trabajo
 * - Entidades oficiales reales
 * - Entregas/Servicios
 */
@Singleton
class LegitimacyDetector @Inject constructor() {

    companion object {
        private const val TAG = "LegitimacyDetector"
    }

    // Palabras que indican conversación humana normal
    private val humanConversationIndicators = listOf(
        "hola", "buenos días", "buenas tardes", "buenas noches",
        "¿cómo estás", "¿está", "¿se encuentra", "¿puedo hablar con",
        "soy", "me llamo", "te llamo porque", "quería decirte",
        "necesito hablar contigo", "es importante", "cuando puedas"
    )

    // Palabras de trabajo/profesional
    private val workIndicators = listOf(
        "trabajo", "oficina", "reunión", "proyecto", "cliente",
        "jefe", "compañero", "empresa", "departamento", "informe",
        "presentación", "deadline", "entrega", "urgente trabajo"
    )

    // Entidades oficiales (pueden ser legítimas)
    private val officialEntities = listOf(
        "hacienda", "agencia tributaria", "seguridad social",
        "ayuntamiento", "juzgado", "policía", "guardia civil",
        "hospital", "ambulatorio", "centro de salud", "médico",
        "colegio", "instituto", "universidad", "secretaría"
    )

    // Servicios de entrega
    private val deliveryIndicators = listOf(
        "paquete", "entrega", "envío", "pedido", "reparto",
        "correos", "seur", "mrw", "dhl", "ups", "fedex",
        "amazon", "glovo", "just eat", "deliveroo"
    )

    // Indicadores de banco REAL (no telemarketing)
    private val realBankIndicators = listOf(
        "movimiento sospechoso", "actividad inusual", "bloqueo de tarjeta",
        "verificar identidad", "cajero automático", "sucursal"
    )

    /**
     * Analiza si la transcripción indica una llamada legítima
     */
    fun analyze(transcript: String): LegitimacyAnalysis {
        if (transcript.isBlank()) {
            return LegitimacyAnalysis(false, null, 0f, emptyList())
        }

        val lower = transcript.lowercase()
        val indicators = mutableListOf<String>()
        var score = 0f
        var reason: SecretaryModeManager.LegitimacyReason? = null

        // 1. Conversación humana normal
        val humanCount = humanConversationIndicators.count { lower.contains(it) }
        if (humanCount >= 2) {
            score += 0.3f
            reason = SecretaryModeManager.LegitimacyReason.HUMAN_CONVERSATION
            indicators.addAll(humanConversationIndicators.filter { lower.contains(it) })
        }

        // 2. Contexto de trabajo
        val workCount = workIndicators.count { lower.contains(it) }
        if (workCount >= 1) {
            score += 0.25f * workCount.coerceAtMost(3)
            if (reason == null || workCount >= 2) {
                reason = SecretaryModeManager.LegitimacyReason.WORK_RELATED
            }
            indicators.addAll(workIndicators.filter { lower.contains(it) })
        }

        // 3. Entidades oficiales
        val officialCount = officialEntities.count { lower.contains(it) }
        if (officialCount >= 1) {
            score += 0.2f * officialCount.coerceAtMost(2)
            if (reason == null) {
                reason = SecretaryModeManager.LegitimacyReason.OFFICIAL_ENTITY
            }
            indicators.addAll(officialEntities.filter { lower.contains(it) })
        }

        // 4. Servicios de entrega
        val deliveryCount = deliveryIndicators.count { lower.contains(it) }
        if (deliveryCount >= 1) {
            score += 0.25f * deliveryCount.coerceAtMost(2)
            if (reason == null) {
                reason = SecretaryModeManager.LegitimacyReason.DELIVERY
            }
            indicators.addAll(deliveryIndicators.filter { lower.contains(it) })
        }

        // 5. Banco real (no telemarketing)
        val realBankCount = realBankIndicators.count { lower.contains(it) }
        if (realBankCount >= 1) {
            score += 0.3f
            if (reason == null) {
                reason = SecretaryModeManager.LegitimacyReason.OFFICIAL_ENTITY
            }
            indicators.addAll(realBankIndicators.filter { lower.contains(it) })
        }

        // Verificar si parece médico/hospital
        if (lower.contains("hospital") || lower.contains("médico") ||
            lower.contains("cita") || lower.contains("consulta")) {
            score += 0.3f
            reason = SecretaryModeManager.LegitimacyReason.MEDICAL
            indicators.add("contexto_médico")
        }

        // Verificar colegio
        if (lower.contains("colegio") || lower.contains("profesor") ||
            lower.contains("niño") || lower.contains("clase")) {
            score += 0.25f
            reason = SecretaryModeManager.LegitimacyReason.SCHOOL
            indicators.add("contexto_escolar")
        }

        val isLegitimate = score >= 0.4f

        android.util.Log.d(TAG, "✅ Análisis legitimidad: isLegit=$isLegitimate, reason=$reason, score=$score")

        return LegitimacyAnalysis(
            isLegitimate = isLegitimate,
            reason = if (isLegitimate) reason else null,
            confidence = score.coerceAtMost(1f),
            indicators = indicators
        )
    }

    /**
     * Resultado del análisis de legitimidad
     */
    data class LegitimacyAnalysis(
        val isLegitimate: Boolean,
        val reason: SecretaryModeManager.LegitimacyReason?,
        val confidence: Float,
        val indicators: List<String>
    )

    /**
     * Verifica si un nombre específico está en la transcripción
     */
    fun containsName(transcript: String, name: String): Boolean {
        if (name.isBlank()) return false
        return transcript.lowercase().contains(name.lowercase())
    }

    /**
     * Verifica si alguno de los nombres de una lista está en la transcripción
     */
    fun containsAnyName(transcript: String, names: Set<String>): String? {
        val lower = transcript.lowercase()
        return names.firstOrNull { lower.contains(it.lowercase()) }
    }
}