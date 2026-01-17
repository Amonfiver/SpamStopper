package com.spamstopper.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * DataStore para preferencias del usuario
 *
 * Almacena configuración personalizada:
 * - Nombre del usuario
 * - Nombres de familia
 * - Palabras clave de emergencia
 * - Configuraciones de la app
 */
class UserPreferences(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("user_preferences")

        // Keys existentes
        private val USER_NAME = stringPreferencesKey("user_name")
        private val FAMILY_NAMES = stringSetPreferencesKey("family_names")
        private val EMERGENCY_KEYWORDS = stringSetPreferencesKey("emergency_keywords")
        private val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        private val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        private val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        private val ANALYSIS_DURATION = intPreferencesKey("analysis_duration")
        private val DETECTION_MODE = stringPreferencesKey("detection_mode")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        // Keys NUEVAS para Task 8.2
        private val AUTO_BLOCK_ROBOTS = booleanPreferencesKey("auto_block_robots")
        private val AUTO_BLOCK_SPAM = booleanPreferencesKey("auto_block_spam")
        private val EMERGENCY_ALERTS = booleanPreferencesKey("emergency_alerts")
        private val SHOW_ANALYSIS_NOTIFICATIONS = booleanPreferencesKey("show_analysis_notifications")
        private val MIC_SENSITIVITY = floatPreferencesKey("mic_sensitivity")
        private val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
    }

    // ========== USER INFO ==========

    suspend fun setUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME] = name
        }
    }

    fun getUserName(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USER_NAME] ?: ""
    }

    suspend fun setFamilyNames(names: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[FAMILY_NAMES] = names
        }
    }

    fun getFamilyNames(): Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[FAMILY_NAMES] ?: emptySet()
    }

    suspend fun addFamilyName(name: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAMILY_NAMES] ?: emptySet()
            prefs[FAMILY_NAMES] = current + name
        }
    }

    suspend fun removeFamilyName(name: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAMILY_NAMES] ?: emptySet()
            prefs[FAMILY_NAMES] = current - name
        }
    }

    // ========== EMERGENCY KEYWORDS ==========

    suspend fun setEmergencyKeywords(keywords: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[EMERGENCY_KEYWORDS] = keywords
        }
    }

    fun getEmergencyKeywords(): Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[EMERGENCY_KEYWORDS] ?: setOf("urgente", "emergencia", "accidente", "hospital")
    }

    suspend fun addEmergencyKeyword(keyword: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[EMERGENCY_KEYWORDS] ?: emptySet()
            prefs[EMERGENCY_KEYWORDS] = current + keyword.lowercase()
        }
    }

    suspend fun removeEmergencyKeyword(keyword: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[EMERGENCY_KEYWORDS] ?: emptySet()
            prefs[EMERGENCY_KEYWORDS] = current - keyword.lowercase()
        }
    }

    // ========== APP SETTINGS (ORIGINALES) ==========

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PROTECTION_ENABLED] = enabled
        }
    }

    fun isProtectionEnabled(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PROTECTION_ENABLED] ?: true
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NOTIFICATION_ENABLED] = enabled
        }
    }

    fun isNotificationEnabled(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NOTIFICATION_ENABLED] ?: true
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[VIBRATION_ENABLED] = enabled
        }
    }

    fun isVibrationEnabled(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[VIBRATION_ENABLED] ?: false
    }

    suspend fun setAnalysisDuration(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[ANALYSIS_DURATION] = seconds.coerceIn(5, 15)
        }
    }

    fun getAnalysisDuration(): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ANALYSIS_DURATION] ?: 10
    }

    suspend fun setDetectionMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[DETECTION_MODE] = mode
        }
    }

    fun getDetectionMode(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DETECTION_MODE] ?: "balanced"
    }

    // ========== ONBOARDING ==========

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED] = completed
        }
    }

    fun isOnboardingCompleted(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }

    // ========== NUEVAS PREFERENCIAS (TASK 8.2) ==========

    // Bloqueo automático de robots
    var autoBlockRobots: Boolean
        get() = runBlocking {
            context.dataStore.data.map { it[AUTO_BLOCK_ROBOTS] ?: true }.first()
        }
        set(value) = runBlocking {
            context.dataStore.edit { it[AUTO_BLOCK_ROBOTS] = value }
        }

    // Bloqueo automático de spam
    var autoBlockSpam: Boolean
        get() = runBlocking {
            context.dataStore.data.map { it[AUTO_BLOCK_SPAM] ?: true }.first()
        }
        set(value) = runBlocking {
            context.dataStore.edit { it[AUTO_BLOCK_SPAM] = value }
        }

    // Alertas de emergencia
    var emergencyAlertsEnabled: Boolean
        get() = runBlocking {
            context.dataStore.data.map { it[EMERGENCY_ALERTS] ?: true }.first()
        }
        set(value) = runBlocking {
            context.dataStore.edit { it[EMERGENCY_ALERTS] = value }
        }

    // Palabras clave personalizadas (delegado a getEmergencyKeywords)
    var emergencyKeywordsCustom: Set<String>
        get() = runBlocking { getEmergencyKeywords().first() }
        set(value) = runBlocking { setEmergencyKeywords(value) }

    // Notificaciones habilitadas
    var notificationsEnabled: Boolean
        get() = runBlocking { isNotificationEnabled().first() }
        set(value) = runBlocking { setNotificationEnabled(value) }

    // Mostrar notificaciones de análisis
    var showAnalysisNotifications: Boolean
        get() = runBlocking {
            context.dataStore.data.map { it[SHOW_ANALYSIS_NOTIFICATIONS] ?: false }.first()
        }
        set(value) = runBlocking {
            context.dataStore.edit { it[SHOW_ANALYSIS_NOTIFICATIONS] = value }
        }

    // Sensibilidad del micrófono
    var micSensitivity: Float
        get() = runBlocking {
            context.dataStore.data.map { it[MIC_SENSITIVITY] ?: 0.5f }.first()
        }
        set(value) = runBlocking {
            context.dataStore.edit { it[MIC_SENSITIVITY] = value }
        }

    // Setup completado
    var setupCompleted: Boolean
        get() = runBlocking {
            context.dataStore.data.map { it[SETUP_COMPLETED] ?: false }.first()
        }
        set(value) = runBlocking {
            context.dataStore.edit { it[SETUP_COMPLETED] = value }
        }

    // ========== MÉTODOS HELPER ==========

    /**
     * Reinicia todas las preferencias a valores por defecto
     */
    fun resetToDefaults() = runBlocking {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    /**
     * Exporta la configuración actual
     */
    fun exportSettings(): Map<String, Any> {
        return mapOf(
            "auto_block_robots" to autoBlockRobots,
            "auto_block_spam" to autoBlockSpam,
            "emergency_alerts" to emergencyAlertsEnabled,
            "notifications_enabled" to notificationsEnabled,
            "show_analysis_notifications" to showAnalysisNotifications,
            "mic_sensitivity" to micSensitivity
        )
    }

    /**
     * Limpia todas las preferencias
     */
    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}