package com.spamstopper.app.services.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detector de palabras clave de emergencia
 *
 * Identifica llamadas urgentes que deben pasar inmediatamente
 * aunque sean de números desconocidos
 */
@Singleton
class EmergencyKeywordDetector @Inject constructor() {

    // Palabras clave de familia
    private val familyKeywords = setOf(
        "mamá", "mama", "madre", "mami",
        "papá", "papa", "padre", "papi",
        "hijo", "hija", "niño", "niña",
        "hermano", "hermana",
        "abuelo", "abuela",
        "esposo", "esposa", "marido", "mujer",
        "pareja", "familia"
    )

    // Palabras clave de emergencia médica
    private val medicalEmergencyKeywords = setOf(
        "emergencia", "urgente", "urgencia",
        "ayuda", "socorro", "auxilio",
        "accidente", "accidentado",
        "hospital", "clínica", "ambulancia",
        "herido", "herida", "lesionado",
        "enfermo", "enferma", "grave",
        "operación", "cirugía",
        "infarto", "derrame",
        "caída", "golpe fuerte"
    )

    // Palabras clave de peligro inmediato
    private val dangerKeywords = setOf(
        "fuego", "incendio", "humo",
        "robo", "ladrón", "ladrones",
        "policía", "bomberos",
        "atrapado", "atrapada",
        "secuestro", "secuestrado",
        "amenaza", "peligro"
    )

    // Palabras clave de trabajo urgente
    private val workUrgentKeywords = setOf(
        "trabajo urgente", "reunión urgente",
        "jefe llamando", "despido", "despedir",
        "cliente importante", "emergencia laboral",
        "oficina urgente", "problema grave trabajo"
    )

    // Todas las palabras clave juntas
    private val allEmergencyKeywords =
        familyKeywords + medicalEmergencyKeywords + dangerKeywords + workUrgentKeywords

    /**
     * Detecta si hay palabras clave de emergencia
     *
     * @param transcript Transcripción del audio
     * @return true si se detectan palabras de emergencia
     */
    fun hasEmergencyKeywords(transcript: String): Boolean {
        if (transcript.isEmpty() || transcript.length < 5) {
            return false
        }

        val lowerTranscript = transcript.lowercase()

        // Buscar coincidencias
        val matches = allEmergencyKeywords.filter { keyword ->
            lowerTranscript.contains(keyword)
        }

        if (matches.isNotEmpty()) {
            android.util.Log.d("EmergencyDetector", "🚨 Emergencia detectada: ${matches.joinToString()}")
            return true
        }

        return false
    }

    /**
     * Calcula el nivel de urgencia (0.0 - 1.0)
     */
    fun getUrgencyLevel(transcript: String): Float {
        if (transcript.isEmpty()) return 0f

        val lowerTranscript = transcript.lowercase()
        var urgencyScore = 0f

        // Emergencias médicas = máxima prioridad
        val medicalMatches = medicalEmergencyKeywords.count {
            lowerTranscript.contains(it)
        }
        urgencyScore += (medicalMatches * 0.4f).coerceAtMost(1f)

        // Peligro inmediato = alta prioridad
        val dangerMatches = dangerKeywords.count {
            lowerTranscript.contains(it)
        }
        urgencyScore += (dangerMatches * 0.35f).coerceAtMost(0.9f)

        // Familia = prioridad media-alta
        val familyMatches = familyKeywords.count {
            lowerTranscript.contains(it)
        }
        urgencyScore += (familyMatches * 0.25f).coerceAtMost(0.7f)

        // Trabajo urgente = prioridad media
        val workMatches = workUrgentKeywords.count { keyword ->
            lowerTranscript.contains(keyword)
        }
        urgencyScore += (workMatches * 0.2f).coerceAtMost(0.6f)

        return urgencyScore.coerceIn(0f, 1f)
    }

    /**
     * Obtiene el tipo de emergencia detectada
     */
    fun getEmergencyType(transcript: String): EmergencyType? {
        if (transcript.isEmpty()) return null

        val lowerTranscript = transcript.lowercase()

        // Priorizar por gravedad
        if (medicalEmergencyKeywords.any { lowerTranscript.contains(it) }) {
            return EmergencyType.MEDICAL
        }

        if (dangerKeywords.any { lowerTranscript.contains(it) }) {
            return EmergencyType.DANGER
        }

        if (familyKeywords.any { lowerTranscript.contains(it) }) {
            return EmergencyType.FAMILY
        }

        if (workUrgentKeywords.any { lowerTranscript.contains(it) }) {
            return EmergencyType.WORK
        }

        return null
    }

    /**
     * Obtiene todas las palabras clave detectadas
     */
    fun getDetectedKeywords(transcript: String): List<String> {
        val lowerTranscript = transcript.lowercase()
        return allEmergencyKeywords.filter {
            lowerTranscript.contains(it)
        }
    }

    /**
     * Genera un mensaje de alerta personalizado
     */
    fun getAlertMessage(transcript: String): String {
        val emergencyType = getEmergencyType(transcript)
        val urgencyLevel = getUrgencyLevel(transcript)
        val keywords = getDetectedKeywords(transcript)

        return when {
            urgencyLevel >= 0.8f -> "🚨 EMERGENCIA CRÍTICA: ${emergencyType?.getDescription()}"
            urgencyLevel >= 0.5f -> "⚠️ LLAMADA URGENTE: ${emergencyType?.getDescription()}"
            else -> "ℹ️ Posible llamada importante: ${keywords.joinToString()}"
        }
    }
}

/**
 * Tipos de emergencia
 */
enum class EmergencyType {
    MEDICAL,    // Emergencia médica
    DANGER,     // Peligro inmediato
    FAMILY,     // Llamada de familia
    WORK;       // Trabajo urgente

    fun getDescription(): String = when (this) {
        MEDICAL -> "Emergencia médica detectada"
        DANGER -> "Situación de peligro"
        FAMILY -> "Llamada de familiar"
        WORK -> "Asunto laboral urgente"
    }

    fun getEmoji(): String = when (this) {
        MEDICAL -> "🏥"
        DANGER -> "⚠️"
        FAMILY -> "👨‍👩‍👧‍👦"
        WORK -> "💼"
    }

    fun getPriority(): Int = when (this) {
        MEDICAL -> 1  // Máxima prioridad
        DANGER -> 2
        FAMILY -> 3
        WORK -> 4     // Mínima prioridad (de las emergencias)
    }
}