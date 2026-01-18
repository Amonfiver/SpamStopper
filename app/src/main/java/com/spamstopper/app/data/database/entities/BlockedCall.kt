package com.spamstopper.app.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spamstopper.app.data.model.CallCategory

/**
 * ============================================================================
 * BlockedCall.kt - Entity para historial de llamadas analizadas
 * ============================================================================
 *
 * PROPÓSITO:
 * Almacena información de cada llamada procesada por SpamStopper.
 *
 * ACTUALIZADO: Enero 2026 - Añadidos campos para etiquetado detallado
 * ============================================================================
 */
@Entity(tableName = "blocked_calls")
data class BlockedCall(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Número de teléfono del llamante */
    val phoneNumber: String,

    /** Nombre del contacto si existe */
    val contactName: String? = null,

    /** Categoría de la llamada */
    val category: CallCategory,

    /** Timestamp Unix en milisegundos */
    val timestamp: Long,

    /** Duración del análisis en segundos */
    val analysisSeconds: Int,

    /** Confianza del análisis (0.0 - 1.0) */
    val confidence: Float = 0f,

    /** Si la llamada fue bloqueada automáticamente */
    val wasBlocked: Boolean = false,

    /** Si el usuario fue alertado */
    val wasAlerted: Boolean = false,

    /** Palabras clave detectadas (separadas por coma) */
    val detectedKeywords: String = "",

    /** Transcripción parcial (máx 200 caracteres) */
    val partialTranscript: String = "",

    /** Si el usuario marcó/corrigió esta clasificación */
    val markedByUser: Boolean = false,

    /** Categoría corregida por el usuario */
    val userCorrectedCategory: CallCategory? = null,

    /** Notas del usuario */
    val userNotes: String? = null
) {
    // ═══════════════════════════════════════════════════════════════════════
    // MÉTODOS DE FORMATEO PARA UI
    // ═══════════════════════════════════════════════════════════════════════

    fun getObfuscatedNumber(): String {
        return if (phoneNumber.length > 7) {
            val prefix = phoneNumber.take(phoneNumber.length - 6)
            val suffix = phoneNumber.takeLast(3)
            "$prefix***$suffix"
        } else {
            "***${phoneNumber.takeLast(3)}"
        }
    }

    fun getDisplayName(): String = contactName ?: phoneNumber

    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun getRelativeTime(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Hace un momento"
            diff < 3600_000 -> {
                val mins = diff / 60_000
                "Hace $mins ${if (mins == 1L) "minuto" else "minutos"}"
            }
            diff < 86400_000 -> {
                val hours = diff / 3600_000
                "Hace $hours ${if (hours == 1L) "hora" else "horas"}"
            }
            diff < 604800_000 -> {
                val days = diff / 86400_000
                "Hace $days ${if (days == 1L) "día" else "días"}"
            }
            else -> getFormattedDate()
        }
    }

    fun getKeywordsList(): List<String> {
        return if (detectedKeywords.isBlank()) emptyList()
        else detectedKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun getConfidencePercent(): String = "${(confidence * 100).toInt()}%"

    fun getCategoryEmoji(): String = (userCorrectedCategory ?: category).emoji

    fun getCategoryDisplayName(): String = (userCorrectedCategory ?: category).displayName

    fun getCategoryShortDescription(): String = (userCorrectedCategory ?: category).shortDescription

    fun getDetailedExplanation(): String = (userCorrectedCategory ?: category).detailedExplanation

    fun getActionTakenText(): String = when {
        wasBlocked -> "🛡️ Bloqueada automáticamente"
        wasAlerted -> "🔔 Te alertamos de esta llamada"
        else -> "ℹ️ Registrada"
    }

    fun getCategoryColor(): String = (userCorrectedCategory ?: category).getColorHex()

    fun isEffectivelySpam(): Boolean = (userCorrectedCategory ?: category).isSpam()

    fun isEffectivelyLegitimate(): Boolean = (userCorrectedCategory ?: category).isLegitimate()

    fun wasUserCorrected(): Boolean = markedByUser && userCorrectedCategory != null

    companion object {
        fun fromAnalysisResult(
            phoneNumber: String,
            contactName: String?,
            category: CallCategory,
            confidence: Float,
            wasBlocked: Boolean,
            keywords: List<String>,
            transcript: String,
            analysisTimeMs: Long
        ): BlockedCall {
            return BlockedCall(
                phoneNumber = phoneNumber,
                contactName = contactName,
                category = category,
                timestamp = System.currentTimeMillis(),
                analysisSeconds = (analysisTimeMs / 1000).toInt(),
                confidence = confidence,
                wasBlocked = wasBlocked,
                wasAlerted = !wasBlocked,
                detectedKeywords = keywords.take(10).joinToString(","),
                partialTranscript = transcript.take(200)
            )
        }
    }
}
