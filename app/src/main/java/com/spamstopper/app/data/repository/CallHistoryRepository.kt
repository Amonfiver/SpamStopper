package com.spamstopper.app.data.repository

import android.content.Context
import android.provider.CallLog
import com.spamstopper.app.data.database.BlockedCallDao
import com.spamstopper.app.data.database.entities.BlockedCall
import com.spamstopper.app.domain.model.CallHistoryItem
import com.spamstopper.app.domain.model.CallType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedCallDao: BlockedCallDao,
    private val contactsRepository: ContactsRepository
) {

    /**
     * Obtiene el historial de llamadas del sistema
     */
    suspend fun getCallHistory(limit: Int = 100): List<CallHistoryItem> = withContext(Dispatchers.IO) {
        val historyItems = mutableListOf<CallHistoryItem>()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)

                while (cursor.moveToNext()) {
                    val phoneNumber = cursor.getString(numberIndex) ?: "Desconocido"
                    val contactName = contactsRepository.getContactNameByNumber(phoneNumber)

                    historyItems.add(
                        CallHistoryItem(
                            id = cursor.getString(idIndex),
                            phoneNumber = phoneNumber,
                            contactName = contactName,
                            date = Date(cursor.getLong(dateIndex)),
                            duration = cursor.getInt(durationIndex),
                            callType = when (cursor.getInt(typeIndex)) {
                                CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                                CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                                CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                                CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                                else -> CallType.MISSED
                            },
                            isBlocked = false,
                            transcript = null,
                            spamScore = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CallHistoryRepo", "Error reading call log: ${e.message}")
        }

        historyItems
    }

    /**
     * Obtiene llamadas bloqueadas (desde Room)
     */
    fun getBlockedCalls(): Flow<List<CallHistoryItem>> {
        return blockedCallDao.getAllFlow().map { entities ->
            entities.map { entity ->
                CallHistoryItem(
                    id = entity.id.toString(),
                    phoneNumber = entity.phoneNumber,
                    contactName = null,
                    date = Date(entity.timestamp),
                    duration = 0,
                    callType = CallType.BLOCKED,
                    isBlocked = true,
                    transcript = null, // BlockedCall no tiene transcript
                    spamScore = null   // BlockedCall no tiene confidence
                )
            }
        }
    }

    /**
     * Obtiene historial combinado (sistema + bloqueadas)
     */
    suspend fun getCombinedHistory(limit: Int = 100): List<CallHistoryItem> = withContext(Dispatchers.IO) {
        val systemCalls = getCallHistory(limit)
        val blockedCalls = blockedCallDao.getAllBlockedCallsList()
            .map { entity ->
                CallHistoryItem(
                    id = entity.id.toString(),
                    phoneNumber = entity.phoneNumber,
                    contactName = null,
                    date = Date(entity.timestamp),
                    duration = 0,
                    callType = CallType.BLOCKED,
                    isBlocked = true,
                    transcript = null, // BlockedCall no tiene transcript
                    spamScore = null   // BlockedCall no tiene confidence
                )
            }

        (systemCalls + blockedCalls)
            .sortedByDescending { it.date }
            .take(limit)
    }

    /**
     * Filtra historial por tipo
     */
    suspend fun getHistoryByType(callType: CallType, limit: Int = 100): List<CallHistoryItem> {
        return getCombinedHistory(limit).filter { it.callType == callType }
    }

    /**
     * Obtiene llamadas perdidas
     */
    suspend fun getMissedCalls(limit: Int = 50): List<CallHistoryItem> {
        return getHistoryByType(CallType.MISSED, limit)
    }

    /**
     * Obtiene llamadas recientes (últimas N)
     */
    suspend fun getRecentCalls(limit: Int = 10): List<CallHistoryItem> {
        return getCombinedHistory(limit)
    }

    /**
     * Busca en el historial
     */
    suspend fun searchHistory(query: String): List<CallHistoryItem> = withContext(Dispatchers.IO) {
        getCombinedHistory().filter {
            it.phoneNumber.contains(query) ||
                    it.contactName?.contains(query, ignoreCase = true) == true
        }
    }

    /**
     * Obtiene estadísticas del historial
     */
    suspend fun getStatistics(): CallHistoryStatistics = withContext(Dispatchers.IO) {
        val history = getCombinedHistory()

        CallHistoryStatistics(
            totalCalls = history.size,
            incomingCalls = history.count { it.callType == CallType.INCOMING },
            outgoingCalls = history.count { it.callType == CallType.OUTGOING },
            missedCalls = history.count { it.callType == CallType.MISSED },
            blockedCalls = history.count { it.callType == CallType.BLOCKED }
        )
    }
}

/**
 * Estadísticas del historial
 */
data class CallHistoryStatistics(
    val totalCalls: Int,
    val incomingCalls: Int,
    val outgoingCalls: Int,
    val missedCalls: Int,
    val blockedCalls: Int
)