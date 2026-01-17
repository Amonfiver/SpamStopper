package com.spamstopper.app.data.database

import androidx.room.*
import com.spamstopper.app.data.database.entities.BlockedCall
import com.spamstopper.app.data.model.CallCategory
import kotlinx.coroutines.flow.Flow

/**
 * DAO para acceso a llamadas bloqueadas
 *
 * Proporciona todas las operaciones de base de datos
 * necesarias para gestionar el historial de llamadas.
 */
@Dao
interface BlockedCallDao {

    /**
     * Inserta una nueva llamada bloqueada
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: BlockedCall): Long

    /**
     * Inserta múltiples llamadas
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(calls: List<BlockedCall>)

    /**
     * Actualiza una llamada existente
     */
    @Update
    suspend fun update(call: BlockedCall)

    /**
     * Elimina una llamada
     */
    @Delete
    suspend fun delete(call: BlockedCall)

    /**
     * Elimina todas las llamadas
     */
    @Query("DELETE FROM blocked_calls")
    suspend fun deleteAll()

    /**
     * Obtiene todas las llamadas ordenadas por timestamp descendente
     * (Flow para actualizaciones en tiempo real)
     */
    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<BlockedCall>>

    /**
     * Obtiene todas las llamadas (suspendido, para operaciones únicas)
     */
    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    suspend fun getAll(): List<BlockedCall>

    /**
     * Obtiene llamadas filtradas por categoría
     */
    @Query("SELECT * FROM blocked_calls WHERE category = :category ORDER BY timestamp DESC")
    fun getByCategory(category: CallCategory): Flow<List<BlockedCall>>

    /**
     * Obtiene llamadas de un número específico
     */
    @Query("SELECT * FROM blocked_calls WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC")
    fun getByPhoneNumber(phoneNumber: String): Flow<List<BlockedCall>>

    /**
     * Obtiene llamadas en un rango de fechas
     */
    @Query("""
        SELECT * FROM blocked_calls 
        WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp 
        ORDER BY timestamp DESC
    """)
    fun getByDateRange(startTimestamp: Long, endTimestamp: Long): Flow<List<BlockedCall>>

    /**
     * Cuenta total de llamadas bloqueadas
     */
    @Query("SELECT COUNT(*) FROM blocked_calls")
    fun getTotalCount(): Flow<Int>

    /**
     * Cuenta llamadas por categoría
     */
    @Query("SELECT COUNT(*) FROM blocked_calls WHERE category = :category")
    fun getCountByCategory(category: CallCategory): Flow<Int>

    /**
     * Obtiene llamadas legítimas (para estadísticas)
     */
    @Query("""
        SELECT * FROM blocked_calls 
        WHERE category IN ('LEGITIMATE_MENTIONS_USER', 'LEGITIMATE_EMERGENCY', 
                          'LEGITIMATE_FAMILY', 'LEGITIMATE_KEYWORD', 'LEGITIMATE_PERSONAL')
        ORDER BY timestamp DESC
    """)
    fun getLegitimateCallsFlow(): Flow<List<BlockedCall>>

    /**
     * Obtiene llamadas spam (para estadísticas)
     */
    @Query("""
        SELECT * FROM blocked_calls 
        WHERE category LIKE 'SPAM_%'
        ORDER BY timestamp DESC
    """)
    fun getSpamCallsFlow(): Flow<List<BlockedCall>>

    /**
     * Busca llamadas por número (con LIKE para búsquedas parciales)
     */
    @Query("SELECT * FROM blocked_calls WHERE phoneNumber LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<BlockedCall>>

    /**
     * Elimina llamadas antiguas (para limpieza periódica)
     */
    @Query("DELETE FROM blocked_calls WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    /**
     * Obtiene estadísticas agregadas
     */
    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    suspend fun getAllBlockedCallsList(): List<BlockedCall>
    @Query("""
        SELECT 
            category,
            COUNT(*) as count,
            AVG(analysisSeconds) as avgAnalysisTime
        FROM blocked_calls 
        GROUP BY category
    """)
    suspend fun getStatistics(): List<CategoryStats>
}

/**
 * Data class para estadísticas agregadas
 */
data class CategoryStats(
    val category: CallCategory,
    val count: Int,
    val avgAnalysisTime: Double
)