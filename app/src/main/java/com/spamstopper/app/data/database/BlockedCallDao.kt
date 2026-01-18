package com.spamstopper.app.data.database

import androidx.room.*
import com.spamstopper.app.data.database.entities.BlockedCall
import com.spamstopper.app.data.model.CallCategory
import kotlinx.coroutines.flow.Flow

/**
 * ============================================================================
 * BlockedCallDao.kt - DAO para acceso a historial de llamadas
 * ============================================================================
 *
 * PROPÓSITO:
 * Proporciona todas las operaciones de base de datos para gestionar
 * el historial de llamadas analizadas por SpamStopper.
 *
 * ACTUALIZADO: Enero 2026 - Compatible con CallHistoryRepository existente
 * ============================================================================
 */
@Dao
interface BlockedCallDao {

    // ═══════════════════════════════════════════════════════════════════════
    // OPERACIONES BÁSICAS
    // ═══════════════════════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: BlockedCall): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(calls: List<BlockedCall>)

    @Update
    suspend fun update(call: BlockedCall)

    @Delete
    suspend fun delete(call: BlockedCall)

    @Query("DELETE FROM blocked_calls")
    suspend fun deleteAll()

    @Query("DELETE FROM blocked_calls WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ═══════════════════════════════════════════════════════════════════════
    // CONSULTAS CON FLOW (REACTIVAS)
    // ═══════════════════════════════════════════════════════════════════════

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<BlockedCall>>

    @Query("SELECT * FROM blocked_calls WHERE wasBlocked = 1 ORDER BY timestamp DESC")
    fun getBlockedCallsFlow(): Flow<List<BlockedCall>>

    @Query("SELECT * FROM blocked_calls WHERE wasBlocked = 0 ORDER BY timestamp DESC")
    fun getAllowedCallsFlow(): Flow<List<BlockedCall>>

    @Query("SELECT * FROM blocked_calls WHERE category = :category ORDER BY timestamp DESC")
    fun getByCategoryFlow(category: CallCategory): Flow<List<BlockedCall>>

    @Query("SELECT * FROM blocked_calls WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC")
    fun getByPhoneNumberFlow(phoneNumber: String): Flow<List<BlockedCall>>

    @Query("""
        SELECT * FROM blocked_calls 
        WHERE timestamp BETWEEN :startTime AND :endTime 
        ORDER BY timestamp DESC
    """)
    fun getByDateRangeFlow(startTime: Long, endTime: Long): Flow<List<BlockedCall>>

    @Query("""
        SELECT * FROM blocked_calls 
        WHERE phoneNumber LIKE '%' || :query || '%' 
           OR contactName LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchFlow(query: String): Flow<List<BlockedCall>>

    // ═══════════════════════════════════════════════════════════════════════
    // CONSULTAS SUSPENDIDAS (COMPATIBILIDAD CON CallHistoryRepository)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * IMPORTANTE: Este método es usado por CallHistoryRepository.getCombinedHistory()
     */
    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    suspend fun getAllBlockedCallsList(): List<BlockedCall>

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    suspend fun getAll(): List<BlockedCall>

    @Query("SELECT * FROM blocked_calls WHERE id = :id")
    suspend fun getById(id: Long): BlockedCall?

    @Query("SELECT * FROM blocked_calls WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastByPhoneNumber(phoneNumber: String): BlockedCall?

    @Query("SELECT COUNT(*) FROM blocked_calls")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM blocked_calls WHERE wasBlocked = 1")
    suspend fun getBlockedCount(): Int

    @Query("SELECT COUNT(*) FROM blocked_calls WHERE wasBlocked = 0")
    suspend fun getAllowedCount(): Int

    // ═══════════════════════════════════════════════════════════════════════
    // CONTADORES CON FLOW
    // ═══════════════════════════════════════════════════════════════════════

    @Query("SELECT COUNT(*) FROM blocked_calls")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_calls WHERE wasBlocked = 1")
    fun getBlockedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_calls WHERE wasBlocked = 0")
    fun getAllowedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_calls WHERE category = :category")
    fun getCountByCategoryFlow(category: CallCategory): Flow<Int>

    // ═══════════════════════════════════════════════════════════════════════
    // FILTROS POR TIPO DE CATEGORÍA
    // ═══════════════════════════════════════════════════════════════════════

    @Query("""
        SELECT * FROM blocked_calls 
        WHERE category IN (
            'SPAM_ROBOT', 'SPAM_TELEMARKETING', 'SPAM_INSURANCE', 
            'SPAM_ENERGY', 'SPAM_TELECOM', 'SPAM_FINANCIAL',
            'SPAM_SCAM', 'SPAM_SURVEYS', 'SPAM_POLITICAL',
            'SPAM_RELIGIOUS', 'SPAM_GENERIC'
        )
        ORDER BY timestamp DESC
    """)
    fun getSpamCallsFlow(): Flow<List<BlockedCall>>

    @Query("""
        SELECT * FROM blocked_calls 
        WHERE category IN (
            'LEGITIMATE_CONTACT', 'LEGITIMATE_MENTIONS_USER', 
            'LEGITIMATE_FAMILY', 'LEGITIMATE_EMERGENCY',
            'LEGITIMATE_WORK', 'LEGITIMATE_DELIVERY',
            'LEGITIMATE_MEDICAL', 'LEGITIMATE_OFFICIAL',
            'LEGITIMATE_SCHOOL', 'LEGITIMATE_HUMAN'
        )
        ORDER BY timestamp DESC
    """)
    fun getLegitimateCallsFlow(): Flow<List<BlockedCall>>

    @Query("SELECT * FROM blocked_calls WHERE markedByUser = 1 ORDER BY timestamp DESC")
    fun getUserCorrectedCallsFlow(): Flow<List<BlockedCall>>

    // ═══════════════════════════════════════════════════════════════════════
    // LIMPIEZA Y MANTENIMIENTO
    // ═══════════════════════════════════════════════════════════════════════

    @Query("DELETE FROM blocked_calls WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM blocked_calls WHERE phoneNumber = :phoneNumber")
    suspend fun deleteByPhoneNumber(phoneNumber: String): Int

    // ═══════════════════════════════════════════════════════════════════════
    // ESTADÍSTICAS
    // ═══════════════════════════════════════════════════════════════════════

    @Query("""
        SELECT * FROM blocked_calls 
        WHERE timestamp >= :todayStart
        ORDER BY timestamp DESC
    """)
    fun getTodayCallsFlow(todayStart: Long): Flow<List<BlockedCall>>

    @Query("""
        SELECT COUNT(*) FROM blocked_calls 
        WHERE timestamp >= :weekStart AND wasBlocked = 1
    """)
    suspend fun getBlockedLastWeek(weekStart: Long): Int
}
