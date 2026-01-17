package com.spamstopper.app.presentation.history

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spamstopper.app.domain.CallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Tipos de llamadas
 */
enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    BLOCKED
}

/**
 * Filtros para historial
 */
enum class CallTypeFilter {
    ALL,
    INCOMING,
    OUTGOING,
    MISSED
}

/**
 * Item del historial de llamadas
 */
data class CallHistoryItem(
    val id: String,
    val phoneNumber: String,
    val contactName: String?,
    val type: CallType,
    val timestamp: Long,
    val duration: Long,
    val isBlocked: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callManager: CallManager
) : ViewModel() {

    private val _historyItems = MutableStateFlow<List<CallHistoryItem>>(emptyList())
    val historyItems: StateFlow<List<CallHistoryItem>> = _historyItems.asStateFlow()

    private var allItems: List<CallHistoryItem> = emptyList()
    private var currentFilter: CallTypeFilter = CallTypeFilter.ALL

    // Cache de contactos para búsqueda rápida
    private val contactsCache = mutableMapOf<String, String>()

    init {
        loadCallHistory()
        loadContactsCache()
    }

    /**
     * Carga caché de contactos
     */
    private fun loadContactsCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val cursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        ),
                        null,
                        null,
                        null
                    )

                    cursor?.use {
                        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                        while (it.moveToNext()) {
                            val number = it.getString(numberIndex)?.replace(Regex("[^0-9+]"), "")
                            val name = it.getString(nameIndex)

                            if (number != null && name != null) {
                                contactsCache[number] = name
                            }
                        }
                    }

                    android.util.Log.d("HistoryViewModel", "📇 Contactos cargados: ${contactsCache.size}")

                } catch (e: Exception) {
                    android.util.Log.e("HistoryViewModel", "Error cargando contactos: ${e.message}")
                }
            }
        }
    }

    /**
     * Busca nombre de contacto por número
     */
    private fun getContactName(phoneNumber: String): String? {
        val normalized = phoneNumber.replace(Regex("[^0-9+]"), "")

        // Búsqueda exacta
        contactsCache[normalized]?.let { return it }

        // Búsqueda flexible (últimos 9 dígitos)
        if (normalized.length >= 9) {
            val suffix = normalized.takeLast(9)
            contactsCache.forEach { (cachedNumber, name) ->
                if (cachedNumber.endsWith(suffix)) {
                    return name
                }
            }
        }

        return null
    }

    /**
     * Carga el historial de llamadas del sistema
     */
    private fun loadCallHistory() {
        viewModelScope.launch {
            if (!hasCallLogPermission()) {
                android.util.Log.w("HistoryViewModel", "⚠️ Sin permiso READ_CALL_LOG")
                return@launch
            }

            val history = withContext(Dispatchers.IO) {
                readCallLogFromSystem()
            }

            allItems = history
            applyFilter(currentFilter)

            android.util.Log.d("HistoryViewModel", "📋 Historial cargado: ${history.size} llamadas")
        }
    }

    /**
     * Lee el call log del sistema
     */
    private fun readCallLogFromSystem(): List<CallHistoryItem> {
        val historyList = mutableListOf<CallHistoryItem>()

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 100"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    val id = it.getString(idIndex) ?: continue
                    val number = it.getString(numberIndex) ?: "Desconocido"
                    val cachedName = if (nameIndex != -1) it.getString(nameIndex) else null
                    val typeInt = it.getInt(typeIndex)
                    val date = it.getLong(dateIndex)
                    val duration = it.getLong(durationIndex)

                    // Buscar nombre de contacto
                    val contactName = cachedName ?: getContactName(number)

                    val type = when (typeInt) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                        else -> CallType.MISSED
                    }

                    historyList.add(
                        CallHistoryItem(
                            id = id,
                            phoneNumber = number,
                            contactName = contactName,
                            type = type,
                            timestamp = date,
                            duration = duration,
                            isBlocked = typeInt == CallLog.Calls.BLOCKED_TYPE
                        )
                    )
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("HistoryViewModel", "❌ Error leyendo call log: ${e.message}", e)
        }

        return historyList
    }

    /**
     * Filtra por tipo de llamada
     */
    fun filterByType(filter: CallTypeFilter) {
        currentFilter = filter
        applyFilter(filter)
    }

    /**
     * Aplica el filtro actual
     */
    private fun applyFilter(filter: CallTypeFilter) {
        _historyItems.value = when (filter) {
            CallTypeFilter.ALL -> allItems
            CallTypeFilter.INCOMING -> allItems.filter { it.type == CallType.INCOMING }
            CallTypeFilter.OUTGOING -> allItems.filter { it.type == CallType.OUTGOING }
            CallTypeFilter.MISSED -> allItems.filter { it.type == CallType.MISSED }
        }

        android.util.Log.d("HistoryViewModel", "🔍 Filtro: $filter - Items: ${_historyItems.value.size}")
    }

    /**
     * Devuelve la llamada a un contacto
     */
    fun callBack(item: CallHistoryItem) {
        viewModelScope.launch {
            callManager.makeCall(item.phoneNumber, item.contactName)
        }
    }

    /**
     * Recarga el historial
     */
    fun refresh() {
        loadContactsCache()
        loadCallHistory()
    }

    /**
     * Verifica permiso de call log
     */
    private fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }
}