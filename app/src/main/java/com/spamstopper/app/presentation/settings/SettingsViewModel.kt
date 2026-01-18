package com.spamstopper.app.presentation.settings

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.CallLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spamstopper.app.data.database.BlockedCallDao
import com.spamstopper.app.data.preferences.UserPreferences
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
 * ============================================================================
 * SettingsViewModel.kt - ViewModel para configuración de SpamStopper
 * ============================================================================
 *
 * PROPÓSITO:
 * Gestiona el estado y la lógica de negocio para la pantalla de configuración.
 *
 * ACTUALIZADO: Enero 2026 - Añadido soporte para tono de notificación,
 * nombres de familia y nombre de usuario
 * ============================================================================
 */

/**
 * Estado de la configuración
 */
data class SettingsState(
    val autoAnswerEnabled: Boolean = false,
    val allowContactsEnabled: Boolean = true,
    val customKeywords: String = "",
    val familyNames: String = "",
    val userName: String = "",
    val notificationRingtoneUri: Uri? = null,
    val notificationRingtoneName: String = "Tono predeterminado",
    val snackbarMessage: String? = null,
    val hasAllPermissions: Boolean = false,
    val hasSecretaryPermissions: Boolean = false,
    val missingPermissions: List<String> = emptyList(),
    val needsPermissionRequest: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emergencyDetector: EmergencyKeywordDetector,
    private val userPreferences: UserPreferences,
    private val blockedCallDao: BlockedCallDao
) : ViewModel() {

    companion object {
        private const val PREF_NOTIFICATION_RINGTONE = "notification_ringtone_uri"
        private const val PREF_FAMILY_NAMES = "family_names"
        private const val PREF_USER_NAME = "user_name"
    }

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
        val familyNames = prefs.getString(PREF_FAMILY_NAMES, "") ?: ""
        val userName = prefs.getString(PREF_USER_NAME, "") ?: ""

        // Cargar tono de notificación
        val ringtoneUriString = prefs.getString(PREF_NOTIFICATION_RINGTONE, null)
        val ringtoneUri = ringtoneUriString?.let { Uri.parse(it) }
        val ringtoneName = getRingtoneName(ringtoneUri)

        _state.value = _state.value.copy(
            autoAnswerEnabled = autoAnswer,
            allowContactsEnabled = allowContacts,
            customKeywords = keywords,
            familyNames = familyNames,
            userName = userName,
            notificationRingtoneUri = ringtoneUri,
            notificationRingtoneName = ringtoneName
        )

        android.util.Log.d("SettingsViewModel", "═══════════════════════════════════════")
        android.util.Log.d("SettingsViewModel", "⚙️ CONFIGURACIÓN CARGADA")
        android.util.Log.d("SettingsViewModel", "   Auto-answer: $autoAnswer")
        android.util.Log.d("SettingsViewModel", "   Allow contacts: $allowContacts")
        android.util.Log.d("SettingsViewModel", "   Ringtone: $ringtoneName")
        android.util.Log.d("SettingsViewModel", "   User name: $userName")
        android.util.Log.d("SettingsViewModel", "═══════════════════════════════════════")
    }

    private fun getRingtoneName(uri: Uri?): String {
        if (uri == null) return "Tono predeterminado"
        return try {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context) ?: "Tono personalizado"
        } catch (e: Exception) {
            "Tono personalizado"
        }
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
    }

    /**
     * Activa/desactiva el modo secretaria
     */
    fun setAutoAnswer(enabled: Boolean) {
        if (enabled && !_state.value.hasSecretaryPermissions) {
            _state.value = _state.value.copy(needsPermissionRequest = true)
            showMessage("⚠️ Se requieren permisos adicionales")
            return
        }

        prefs.edit()
            .putBoolean(Constants.PREF_AUTO_ANSWER_ENABLED, enabled)
            .apply()

        _state.value = _state.value.copy(autoAnswerEnabled = enabled)

        android.util.Log.d("SettingsViewModel", "🎙️ MODO SECRETARIA: ${if (enabled) "ACTIVADO ✅" else "DESACTIVADO ❌"}")

        showMessage(
            if (enabled) "✅ Modo Secretaria activado"
            else "⚠️ Modo Secretaria desactivado"
        )
    }

    /**
     * Configura el tono de notificación
     */
    fun setNotificationRingtone(uri: Uri?) {
        val uriString = uri?.toString()
        prefs.edit().putString(PREF_NOTIFICATION_RINGTONE, uriString).apply()

        val name = getRingtoneName(uri)
        _state.value = _state.value.copy(
            notificationRingtoneUri = uri,
            notificationRingtoneName = name
        )

        android.util.Log.d("SettingsViewModel", "🔔 Tono cambiado: $name")
        showMessage("✅ Tono de notificación actualizado")
    }

    /**
     * Prueba el tono de notificación
     */
    fun testNotificationRingtone() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = _state.value.notificationRingtoneUri
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                val ringtone = RingtoneManager.getRingtone(context, uri)
                ringtone?.play()

                kotlinx.coroutines.delay(3000)
                ringtone?.stop()

                android.util.Log.d("SettingsViewModel", "🔔 Tono probado")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "❌ Error reproduciendo tono: ${e.message}")
                withContext(Dispatchers.Main) {
                    showMessage("❌ Error al reproducir el tono")
                }
            }
        }
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
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        emergencyDetector.saveUserKeywords(keywordsList)
        _state.value = _state.value.copy(customKeywords = keywords)

        android.util.Log.d("SettingsViewModel", "🔑 Keywords guardadas: ${keywordsList.size}")
        showMessage("✅ Palabras clave guardadas")
    }

    /**
     * Guarda nombres de familia
     */
    fun setFamilyNames(names: String) {
        prefs.edit().putString(PREF_FAMILY_NAMES, names).apply()
        _state.value = _state.value.copy(familyNames = names)

        // También guardar en UserPreferences para SecretaryModeManager
        viewModelScope.launch {
            val namesSet = names
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            userPreferences.setFamilyNames(namesSet)
        }

        android.util.Log.d("SettingsViewModel", "👨‍👩‍👧 Nombres de familia guardados")
        showMessage("✅ Nombres de familia guardados")
    }

    /**
     * Guarda nombre del usuario
     */
    fun setUserName(name: String) {
        prefs.edit().putString(PREF_USER_NAME, name).apply()
        _state.value = _state.value.copy(userName = name)

        viewModelScope.launch {
            userPreferences.setUserName(name)
        }

        android.util.Log.d("SettingsViewModel", "👤 Nombre de usuario: $name")
        showMessage("✅ Nombre guardado")
    }

    /**
     * Limpia el historial de llamadas
     */
    fun clearCallHistory() {
        viewModelScope.launch {
            try {
                // Limpiar historial de SpamStopper
                withContext(Dispatchers.IO) {
                    blockedCallDao.deleteAll()
                }

                // Intentar limpiar call log del sistema
                val deleted = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.delete(
                            CallLog.Calls.CONTENT_URI,
                            null,
                            null
                        )
                    } catch (e: SecurityException) {
                        android.util.Log.w("SettingsViewModel", "Sin permiso WRITE_CALL_LOG")
                        0
                    }
                }

                android.util.Log.d("SettingsViewModel", "🗑️ Historial eliminado")
                showMessage("✅ Historial eliminado")

            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "❌ Error eliminando historial", e)
                showMessage("❌ Error al eliminar")
            }
        }
    }

    fun requestPermissions() {
        _state.value = _state.value.copy(needsPermissionRequest = true)
    }

    fun clearPermissionRequest() {
        _state.value = _state.value.copy(needsPermissionRequest = false)
    }

    private fun showMessage(message: String) {
        _state.value = _state.value.copy(snackbarMessage = message)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }
}
