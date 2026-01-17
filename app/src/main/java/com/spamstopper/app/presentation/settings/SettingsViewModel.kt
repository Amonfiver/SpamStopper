package com.spamstopper.app.presentation.settings

import android.content.Context
import android.provider.CallLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spamstopper.app.domain.EmergencyKeywordDetector
import com.spamstopper.app.utils.Constants
import com.spamstopper.app.utils.PermissionsHelper
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
 * Estado de la configuración
 */
data class SettingsState(
    val autoAnswerEnabled: Boolean = false,
    val allowContactsEnabled: Boolean = true,
    val customKeywords: String = "",
    val snackbarMessage: String? = null,
    val hasAllPermissions: Boolean = false,
    val hasSecretaryPermissions: Boolean = false,
    val missingPermissions: List<String> = emptyList(),
    val needsPermissionRequest: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emergencyDetector: EmergencyKeywordDetector
) : ViewModel() {

    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
        checkPermissions()
    }

    /**
     * Carga la configuración guardada
     */
    private fun loadSettings() {
        val autoAnswer = prefs.getBoolean(Constants.PREF_AUTO_ANSWER_ENABLED, false)
        val allowContacts = prefs.getBoolean(Constants.PREF_ALLOW_CONTACTS, true)
        val keywords = emergencyDetector.getUserKeywords().joinToString(", ")

        _state.value = _state.value.copy(
            autoAnswerEnabled = autoAnswer,
            allowContactsEnabled = allowContacts,
            customKeywords = keywords
        )

        android.util.Log.d("SettingsViewModel", "═══════════════════════════════════════")
        android.util.Log.d("SettingsViewModel", "⚙️ CONFIGURACIÓN CARGADA")
        android.util.Log.d("SettingsViewModel", "   Auto-answer: $autoAnswer")
        android.util.Log.d("SettingsViewModel", "   Allow contacts: $allowContacts")
        android.util.Log.d("SettingsViewModel", "   Keywords: $keywords")
        android.util.Log.d("SettingsViewModel", "═══════════════════════════════════════")
    }

    /**
     * Verifica permisos
     */
    fun checkPermissions() {
        val hasAll = PermissionsHelper.hasAllPermissions(context)
        val hasSecretary = PermissionsHelper.hasSecretaryPermissions(context)
        val missing = PermissionsHelper.getMissingSecretaryPermissions(context)

        _state.value = _state.value.copy(
            hasAllPermissions = hasAll,
            hasSecretaryPermissions = hasSecretary,
            missingPermissions = missing
        )

        android.util.Log.d("SettingsViewModel", "🔐 Permisos: All=$hasAll, Secretary=$hasSecretary")
        if (missing.isNotEmpty()) {
            android.util.Log.d("SettingsViewModel", "   Faltantes: ${missing.joinToString()}")
        }
    }

    /**
     * Activa/desactiva el modo secretaria
     */
    fun setAutoAnswer(enabled: Boolean) {
        if (enabled && !_state.value.hasSecretaryPermissions) {
            // Solicitar permisos
            _state.value = _state.value.copy(needsPermissionRequest = true)
            showMessage("⚠️ Se requieren permisos adicionales")

            android.util.Log.w("SettingsViewModel", "⚠️ Intento activar Secretary Mode sin permisos")
            return
        }

        // Guardar en SharedPreferences
        prefs.edit()
            .putBoolean(Constants.PREF_AUTO_ANSWER_ENABLED, enabled)
            .apply()

        _state.value = _state.value.copy(autoAnswerEnabled = enabled)

        android.util.Log.d("SettingsViewModel", "═══════════════════════════════════════")
        android.util.Log.d("SettingsViewModel", "🎙️ MODO SECRETARIA: ${if (enabled) "ACTIVADO ✅" else "DESACTIVADO ❌"}")
        android.util.Log.d("SettingsViewModel", "   SharedPrefs: ${Constants.PREFS_NAME}")
        android.util.Log.d("SettingsViewModel", "   Key: ${Constants.PREF_AUTO_ANSWER_ENABLED}")
        android.util.Log.d("SettingsViewModel", "   Valor: $enabled")
        android.util.Log.d("SettingsViewModel", "═══════════════════════════════════════")

        showMessage(
            if (enabled) "✅ Modo Secretaria activado"
            else "⚠️ Modo Secretaria desactivado"
        )
    }

    /**
     * Activa/desactiva permitir contactos
     */
    fun setAllowContacts(enabled: Boolean) {
        prefs.edit()
            .putBoolean(Constants.PREF_ALLOW_CONTACTS, enabled)
            .apply()

        _state.value = _state.value.copy(allowContactsEnabled = enabled)

        android.util.Log.d("SettingsViewModel", "📲 Permitir contactos: $enabled")
    }

    /**
     * Guarda keywords personalizadas
     */
    fun setCustomKeywords(keywords: String) {
        val keywordsList = keywords
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        emergencyDetector.saveUserKeywords(keywordsList)

        _state.value = _state.value.copy(customKeywords = keywords)

        android.util.Log.d("SettingsViewModel", "🔑 Keywords guardadas: ${keywordsList.size}")
        android.util.Log.d("SettingsViewModel", "   Lista: ${keywordsList.joinToString()}")

        showMessage("✅ Palabras clave guardadas")
    }

    /**
     * Limpia el historial de llamadas
     */
    fun clearCallHistory() {
        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    context.contentResolver.delete(
                        CallLog.Calls.CONTENT_URI,
                        null,
                        null
                    )
                }

                android.util.Log.d("SettingsViewModel", "🗑️ Historial eliminado: $deleted llamadas")

                showMessage("✅ Historial eliminado ($deleted llamadas)")

            } catch (e: SecurityException) {
                android.util.Log.e("SettingsViewModel", "❌ Sin permiso WRITE_CALL_LOG", e)
                showMessage("❌ Permiso denegado")

            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "❌ Error eliminando historial", e)
                showMessage("❌ Error al eliminar")
            }
        }
    }

    /**
     * Marca que se deben solicitar permisos
     */
    fun requestPermissions() {
        _state.value = _state.value.copy(needsPermissionRequest = true)
        android.util.Log.d("SettingsViewModel", "🔐 Solicitando permisos al usuario...")
    }

    /**
     * Limpia flag de solicitud de permisos
     */
    fun clearPermissionRequest() {
        _state.value = _state.value.copy(needsPermissionRequest = false)
    }

    /**
     * Muestra un mensaje temporal
     */
    private fun showMessage(message: String) {
        _state.value = _state.value.copy(snackbarMessage = message)

        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _state.value = _state.value.copy(snackbarMessage = null)
        }
    }

    /**
     * Limpia el mensaje
     */
    fun clearMessage() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }
}