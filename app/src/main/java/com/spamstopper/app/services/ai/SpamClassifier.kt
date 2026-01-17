package com.spamstopper.app.services.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * SpamClassifier - Clasificador de spam por categorías
 *
 * Analiza transcripciones y clasifica el tipo de spam:
 * - Telemarketing/Ventas
 * - Encuestas
 * - Estafas
 * - Religioso
 * - Político
 * - Financiero
 * - etc.
 */
@Singleton
class SpamClassifier @Inject constructor() {

    companion object {
        private const val TAG = "SpamClassifier"
    }

    /**
     * Resultado de la clasificación
     */
    data class ClassificationResult(
        val isSpam: Boolean,
        val category: SecretaryModeManager.SpamCategory?,
        val confidence: Float,
        val detectedKeywords: List<String>
    )

    // Palabras clave por categoría
    private val categoryKeywords = mapOf(
        SecretaryModeManager.SpamCategory.TELEMARKETING to listOf(
            "oferta", "promoción", "descuento", "gratis", "oportunidad",
            "exclusivo", "limitado", "ahorro", "mejor precio", "no te pierdas",
            "aprovecha", "solo hoy", "última oportunidad", "venta", "compra"
        ),
        SecretaryModeManager.SpamCategory.SURVEYS to listOf(
            "encuesta", "opinión", "valoración", "satisfacción", "calificar",
            "experiencia", "feedback", "puntuación", "preguntas", "responder",
            "minutos de su tiempo", "breve encuesta"
        ),
        SecretaryModeManager.SpamCategory.SCAM to listOf(
            "premio", "ganador", "sorteo", "lotería", "herencia",
            "millones", "transferencia", "urgente responder", "has sido seleccionado",
            "felicidades has ganado", "reclamar premio"
        ),
        SecretaryModeManager.SpamCategory.RELIGIOUS to listOf(
            "testigos", "jehová", "iglesia", "dios", "salvación",
            "biblia", "evangelio", "cristo", "fe", "oración",
            "congregación", "ministro", "predicar"
        ),
        SecretaryModeManager.SpamCategory.POLITICAL to listOf(
            "partido", "votar", "elecciones", "candidato", "campaña",
            "político", "gobierno", "votación", "apoyo", "afiliación"
        ),
        SecretaryModeManager.SpamCategory.FINANCIAL to listOf(
            "préstamo", "crédito", "deuda", "refinanciar", "hipoteca",
            "inversión", "rentabilidad", "interés", "financiación",
            "tarjeta de crédito", "aval", "cuotas"
        ),
        SecretaryModeManager.SpamCategory.INSURANCE to listOf(
            "seguro", "póliza", "cobertura", "asegurar", "prima",
            "siniestro", "indemnización", "seguro de vida", "seguro médico",
            "accidente", "protección"
        ),
        SecretaryModeManager.SpamCategory.ENERGY to listOf(
            "luz", "electricidad", "gas", "factura energética", "ahorro energético",
            "tarifa", "compañía eléctrica", "endesa", "iberdrola", "naturgy",
            "consumo", "potencia contratada"
        ),
        SecretaryModeManager.SpamCategory.TELECOM to listOf(
            "fibra", "internet", "móvil", "línea", "datos",
            "megas", "gigas", "tarifa plana", "portabilidad", "permanencia",
            "movistar", "vodafone", "orange", "yoigo", "masmovil"
        )
    )

    // Frases genéricas de spam
    private val genericSpamPhrases = listOf(
        "le llamamos de",
        "el motivo de mi llamada",
        "no le voy a quitar mucho tiempo",
        "solo serán unos minutos",
        "le interesaría",
        "tengo una oferta",
        "hemos seleccionado",
        "como cliente preferente",
        "departamento comercial",
        "departamento de ventas",
        "información sin compromiso",
        "totalmente gratis",
        "sin ningún coste",
        "sin compromiso alguno",
        "llamada comercial",
        "fines comerciales",
        "mejorar su",
        "actualizar su",
        "revisar su contrato"
    )

    /**
     * Clasifica una transcripción
     */
    fun classify(transcript: String): ClassificationResult {
        if (transcript.isBlank()) {
            return ClassificationResult(false, null, 0f, emptyList())
        }

        val lower = transcript.lowercase()
        val detectedKeywords = mutableListOf<String>()
        var bestCategory: SecretaryModeManager.SpamCategory? = null
        var bestScore = 0f

        // Analizar cada categoría
        for ((category, keywords) in categoryKeywords) {
            var categoryScore = 0f
            val categoryMatches = mutableListOf<String>()

            for (keyword in keywords) {
                if (lower.contains(keyword)) {
                    categoryScore += 0.2f
                    categoryMatches.add(keyword)
                }
            }

            // Si encontramos matches en esta categoría
            if (categoryScore > bestScore) {
                bestScore = categoryScore
                bestCategory = category
                detectedKeywords.clear()
                detectedKeywords.addAll(categoryMatches)
            }
        }

        // Verificar frases genéricas de spam
        var genericSpamScore = 0f
        for (phrase in genericSpamPhrases) {
            if (lower.contains(phrase)) {
                genericSpamScore += 0.15f
                detectedKeywords.add(phrase)
            }
        }

        // Combinar scores
        val totalScore = (bestScore + genericSpamScore).coerceAtMost(1f)

        // Determinar si es spam
        val isSpam = totalScore >= 0.4f

        // Si es spam genérico sin categoría específica
        if (isSpam && bestCategory == null) {
            bestCategory = SecretaryModeManager.SpamCategory.UNKNOWN_SPAM
        }

        android.util.Log.d(TAG, "📊 Clasificación: isSpam=$isSpam, category=$bestCategory, score=$totalScore")
        android.util.Log.d(TAG, "   Keywords: ${detectedKeywords.take(5)}")

        return ClassificationResult(
            isSpam = isSpam,
            category = if (isSpam) bestCategory else null,
            confidence = totalScore,
            detectedKeywords = detectedKeywords
        )
    }

    /**
     * Obtiene descripción de la categoría para mostrar al usuario
     */
    fun getCategoryDescription(category: SecretaryModeManager.SpamCategory): String {
        return when (category) {
            SecretaryModeManager.SpamCategory.ROBOT ->
                "Sistema automatizado de marcación"
            SecretaryModeManager.SpamCategory.TELEMARKETING ->
                "Llamada comercial intentando venderte algo"
            SecretaryModeManager.SpamCategory.SURVEYS ->
                "Encuesta telefónica"
            SecretaryModeManager.SpamCategory.SCAM ->
                "Posible intento de estafa"
            SecretaryModeManager.SpamCategory.RELIGIOUS ->
                "Propaganda religiosa"
            SecretaryModeManager.SpamCategory.POLITICAL ->
                "Propaganda política"
            SecretaryModeManager.SpamCategory.FINANCIAL ->
                "Oferta de productos financieros"
            SecretaryModeManager.SpamCategory.INSURANCE ->
                "Oferta de seguros"
            SecretaryModeManager.SpamCategory.ENERGY ->
                "Compañía energética"
            SecretaryModeManager.SpamCategory.TELECOM ->
                "Operadora de telefonía"
            SecretaryModeManager.SpamCategory.UNKNOWN_SPAM ->
                "Llamada no deseada"
        }
    }
}