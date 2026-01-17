package com.spamstopper.app.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spamstopper.app.data.model.CallCategory

/**
 * Entity Room para llamadas bloqueadas
 *
 * Almacena SOLO información mínima:
 * - Número de teléfono
 * - Categoría genérica
 * - Timestamp
 * - Duración del análisis
 *
 * ❌ NO almacena:
 * - Audio
 * - Transcripción
 * - Datos personales del caller
 */
@Entity(tableName = "blocked_calls")
data class BlockedCall(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Número de teléfono del caller
     * Nota: Se mostrará ofuscado en UI (últimos 4 dígitos)
     */
    val phoneNumber: String,

    /**
     * Categoría de la llamada
     */
    val category: CallCategory,

    /**
     * Timestamp Unix (milisegundos)
     */
    val timestamp: Long,

    /**
     * Duración del análisis en segundos
     */
    val analysisSeconds: Int,

    /**
     * Si el usuario marcó esta llamada manualmente (override)
     */
    val markedByUser: Boolean = false,

    /**
     * Notas opcionales del usuario
     */
    val userNotes: String? = null
) {
    /**
     * Obtiene el número ofuscado para mostrar en UI
     * Ejemplo: +34 900 123 456 → +34 900 *** 456
     */
    fun getObfuscatedNumber(): String {
        return if (phoneNumber.length > 7) {
            val visible = phoneNumber.takeLast(4)
            val prefix = phoneNumber.take(phoneNumber.length - 7)
            "$prefix *** $visible"
        } else {
            "*** ${phoneNumber.takeLast(3)}"
        }
    }

    /**
     * Formatea el timestamp a fecha legible
     */
    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    /**
     * Obtiene tiempo relativo (ej: "Hace 2 horas")
     */
    fun getRelativeTime(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Hace menos de 1 minuto"
            diff < 3600_000 -> "Hace ${diff / 60_000} minutos"
            diff < 86400_000 -> "Hace ${diff / 3600_000} horas"
            diff < 604800_000 -> "Hace ${diff / 86400_000} días"
            else -> getFormattedDate()
        }
    }
}