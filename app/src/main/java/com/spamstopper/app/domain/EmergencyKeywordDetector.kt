package com.spamstopper.app.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EmergencyKeywordDetector - Detecta palabras clave de emergencia
 *
 * Analiza transcripciones en tiempo real para identificar:
 * - Emergencias (fuego, accidente, ambulancia)
 * - Familia (mamá, papá, hijo)
 * - Propiedad (coche, casa, móvil)
 * - Keywords personalizadas del usuario
 */
@Singleton
class EmergencyKeywordDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /**
         * Keywords predefinidas de emergencia
         */
        private val DEFAULT_KEYWORDS = listOf(
            // Emergencias críticas
            "emergencia", "urgente", "urgencia", "ayuda",
            "hospital", "ambulancia", "médico", "doctor",
            "accidente", "herido", "sangre",
            "fuego", "incendio", "humo",
            "policía", "robo", "ladrón",

            // Familia
            "mamá", "mama", "madre",
            "papá", "papa", "padre",
            "hijo", "hija", "niño", "niña",
            "hermano", "hermana",
            "abuelo", "abuela",
            "esposo", "esposa", "marido", "mujer",

            // Propiedad personal
            "coche", "auto", "automóvil", "vehículo",
            "casa", "piso", "vivienda", "hogar",
            "móvil", "teléfono", "celular",
            "cartera", "billetera", "dinero",
            "llaves", "documentos",

            // Trabajo crítico
            "jefe", "trabajo urgente", "oficina urgente",
            "reunión importante", "cliente importante",

            // Salud
            "dolor", "enfermo", "caída", "desmayo",
            "corazón", "respirar"
        )
    }

    /**
     * Detecta si el texto contiene keywords de emergencia
     */
    fun detect(transcription: String): Boolean {
        if (transcription.isBlank()) return false

        val normalized = transcription.lowercase().trim()

        // Verificar keywords predefinidas
        val hasDefaultKeyword = DEFAULT_KEYWORDS.any { keyword ->
            normalized.contains(keyword)
        }

        if (hasDefaultKeyword) {
            val matchedKeyword = DEFAULT_KEYWORDS.first { normalized.contains(it) }
            android.util.Log.d(
                "EmergencyKeywordDetector",
                "🚨 KEYWORD DETECTADA: '$matchedKeyword' en: '$transcription'"
            )
            return true
        }

        // Verificar keywords personalizadas del usuario
        val customKeywords = getUserKeywords()
        val hasCustomKeyword = customKeywords.any { keyword ->
            normalized.contains(keyword.lowercase())
        }

        if (hasCustomKeyword) {
            val matchedKeyword = customKeywords.first { normalized.contains(it.lowercase()) }
            android.util.Log.d(
                "EmergencyKeywordDetector",
                "🚨 KEYWORD PERSONALIZADA: '$matchedKeyword' en: '$transcription'"
            )
            return true
        }

        return false
    }

    /**
     * Detecta keywords en tiempo real (análisis parcial)
     * Para uso durante captura de audio
     */
    fun detectRealTime(partialTranscription: String): Boolean {
        // Más permisivo para detectar temprano
        return detect(partialTranscription)
    }

    /**
     * Obtiene las keywords personalizadas del usuario
     */
    fun getUserKeywords(): List<String> {
        val prefs = context.getSharedPreferences("SpamStopperPrefs", Context.MODE_PRIVATE)
        val customString = prefs.getString("custom_keywords", "") ?: ""

        return customString
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Guarda keywords personalizadas
     */
    fun saveUserKeywords(keywords: List<String>) {
        val prefs = context.getSharedPreferences("SpamStopperPrefs", Context.MODE_PRIVATE)
        val joined = keywords.joinToString(",")

        prefs.edit()
            .putString("custom_keywords", joined)
            .apply()

        android.util.Log.d("EmergencyKeywordDetector", "💾 Keywords guardadas: $joined")
    }

    /**
     * Obtiene todas las keywords (predefinidas + personalizadas)
     */
    fun getAllKeywords(): List<String> {
        return DEFAULT_KEYWORDS + getUserKeywords()
    }

    /**
     * Verifica si un contacto está guardado (para bypass)
     */
    fun isContact(phoneNumber: String): Boolean {
        // TODO: Implementar verificación de contactos
        // Por ahora retorna false
        return false
    }
}