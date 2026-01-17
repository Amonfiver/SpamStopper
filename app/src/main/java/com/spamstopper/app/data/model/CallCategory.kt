package com.spamstopper.app.data.model

/**
 * Categorías de llamadas detectadas por SpamStopper
 *
 * Define todos los tipos posibles de llamadas y su clasificación.
 */
enum class CallCategory(val displayName: String, val description: String) {

    // ✅ LLAMADAS LEGÍTIMAS
    LEGITIMATE_MENTIONS_USER(
        "Legítima - Menciona tu nombre",
        "El caller menciona específicamente tu nombre"
    ),
    LEGITIMATE_EMERGENCY(
        "Legítima - Emergencia",
        "Palabras de emergencia detectadas: urgente, accidente, hospital"
    ),
    LEGITIMATE_FAMILY(
        "Legítima - Familia",
        "Menciona nombres de familia configurados"
    ),
    LEGITIMATE_KEYWORD(
        "Legítima - Palabra clave",
        "Menciona palabra clave personalizada"
    ),
    LEGITIMATE_PERSONAL(
        "Legítima - Personal",
        "Intención personal detectada: 'soy', 'hablar contigo'"
    ),

    // ❌ SPAM - Categorías específicas
    SPAM_TELEMARKETING(
        "Spam - Ventas",
        "Telemarketing detectado: ofertas, promociones, ventas"
    ),
    SPAM_INSURANCE(
        "Spam - Seguros",
        "Llamada de seguros o pólizas"
    ),
    SPAM_LOTTERY(
        "Spam - Sorteos",
        "Sorteos, premios, lotería"
    ),
    SPAM_UTILITIES(
        "Spam - Suministros",
        "Compañías eléctricas, gas, telecomunicaciones"
    ),
    SPAM_REAL_ESTATE(
        "Spam - Inmobiliaria",
        "Ofertas inmobiliarias, viviendas, inversiones"
    ),
    SPAM_SURVEYS(
        "Spam - Encuestas",
        "Encuestas de opinión o satisfacción"
    ),
    SPAM_RELIGIOUS(
        "Spam - Religioso",
        "Llamadas religiosas o evangelización"
    ),
    SPAM_ROBOT_SILENCE(
        "Spam - Robot",
        "Robot automático: silencio o tonos detectados"
    ),
    SPAM_GENERIC(
        "Spam - Genérico",
        "Spam no clasificado en otras categorías"
    ),

    // 🤔 CASOS ESPECIALES
    SUSPICIOUS_UNKNOWN(
        "Sospechoso",
        "No se pudo determinar si es spam o legítimo"
    ),
    ERROR_NO_AUDIO(
        "Error - Sin audio",
        "No se detectó audio para analizar"
    ),
    ERROR_TRANSCRIPTION_FAILED(
        "Error - Transcripción fallida",
        "El STT no pudo transcribir el audio"
    );

    /**
     * Determina si esta categoría es legítima
     */
    fun isLegitimate(): Boolean = name.startsWith("LEGITIMATE_")

    /**
     * Determina si esta categoría es spam
     */
    fun isSpam(): Boolean = name.startsWith("SPAM_")

    /**
     * Determina si esta categoría es un error
     */
    fun isError(): Boolean = name.startsWith("ERROR_")

    /**
     * Obtiene el color asociado a esta categoría (para UI)
     */
    fun getColorHex(): String = when {
        isLegitimate() -> "#10B981" // Verde
        name == "SPAM_TELEMARKETING" -> "#EF4444" // Rojo
        name == "SPAM_INSURANCE" -> "#F59E0B" // Naranja
        name == "SPAM_LOTTERY" -> "#FBBF24" // Amarillo
        name == "SPAM_UTILITIES" -> "#3B82F6" // Azul
        isSpam() -> "#EF4444" // Rojo por defecto
        else -> "#6B7280" // Gris
    }

    companion object {
        /**
         * Obtiene todas las categorías de spam
         */
        fun getSpamCategories(): List<CallCategory> =
            entries.filter { it.isSpam() }

        /**
         * Obtiene todas las categorías legítimas
         */
        fun getLegitimateCategories(): List<CallCategory> =
            entries.filter { it.isLegitimate() }
    }
}