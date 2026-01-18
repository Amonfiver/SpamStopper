package com.spamstopper.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.spamstopper.app.data.database.entities.BlockedCall
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Base de datos Room cifrada con SQLCipher
 *
 * Almacena historial de llamadas bloqueadas de forma segura.
 * Los datos están cifrados en reposo para protección adicional.
 */
@Database(
    entities = [BlockedCall::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SpamStopperDatabase : RoomDatabase() {

    abstract fun blockedCallDao(): BlockedCallDao

    companion object {
        @Volatile
        private var INSTANCE: SpamStopperDatabase? = null

        private const val DATABASE_NAME = "spamstopper.db"

        /**
         * Obtiene instancia singleton de la base de datos
         *
         * @param context Contexto de la aplicación
         * @return Instancia de la base de datos cifrada
         */
        fun getInstance(context: Context): SpamStopperDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context)
                INSTANCE = instance
                instance
            }
        }

        /**
         * Construye la base de datos con cifrado
         */
        private fun buildDatabase(context: Context): SpamStopperDatabase {
            // Generar clave de cifrado basada en el dispositivo
            val passphrase = SQLiteDatabase.getBytes(
                generateDatabaseKey(context).toCharArray()
            )
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                SpamStopperDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration() // Solo para desarrollo
                .build()
        }

        /**
         * Genera clave de cifrado única por dispositivo
         *
         * En producción, usar Android Keystore para mayor seguridad
         */
        private fun generateDatabaseKey(context: Context): String {
            val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

            return prefs.getString("db_key", null) ?: run {
                // Generar nueva clave aleatoria
                val key = java.util.UUID.randomUUID().toString()
                prefs.edit().putString("db_key", key).apply()
                key
            }
        }

        /**
         * Limpia la instancia (solo para testing)
         */
        fun closeInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}