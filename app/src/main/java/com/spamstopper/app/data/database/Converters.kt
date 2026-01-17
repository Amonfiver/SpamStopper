package com.spamstopper.app.data.database

import androidx.room.TypeConverter
import com.spamstopper.app.data.model.CallCategory

/**
 * Converters para Room Database
 *
 * Convierte tipos personalizados a tipos que Room puede almacenar.
 */
class Converters {

    /**
     * Convierte CallCategory a String para almacenamiento
     */
    @TypeConverter
    fun fromCallCategory(category: CallCategory): String {
        return category.name
    }

    /**
     * Convierte String a CallCategory al leer de DB
     */
    @TypeConverter
    fun toCallCategory(value: String): CallCategory {
        return try {
            CallCategory.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // Si la categoría no existe (por actualización), usar genérica
            CallCategory.SPAM_GENERIC
        }
    }
}