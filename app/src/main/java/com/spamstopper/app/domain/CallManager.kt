package com.spamstopper.app.domain

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.spamstopper.app.presentation.incall.InCallActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CallManager - Gestor centralizado de llamadas
 *
 * Funciones:
 * - Iniciar llamadas usando TelecomCallManager
 * - Abrir InCallActivity
 * - Colgar llamadas activas
 * - Verificar permisos
 */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telecomCallManager: TelecomCallManager
) {

    /**
     * Estado actual de la llamada
     */
    private var _isCallActive = false
    val isCallActive: Boolean get() = _isCallActive

    /**
     * Realiza una llamada saliente
     */
    fun makeCall(phoneNumber: String, contactName: String? = null): Boolean {
        if (!hasCallPermission()) {
            android.util.Log.e("CallManager", "❌ Sin permiso CALL_PHONE")
            return false
        }

        if (!isValidPhoneNumber(phoneNumber)) {
            android.util.Log.e("CallManager", "❌ Número inválido: $phoneNumber")
            return false
        }

        return try {
            // 1. Iniciar llamada con TelecomManager
            val callStarted = telecomCallManager.placeCall(phoneNumber)

            if (!callStarted) {
                android.util.Log.e("CallManager", "❌ Error iniciando llamada con TelecomManager")
                return false
            }

            // 2. Abrir InCallActivity
            val intent = Intent(context, InCallActivity::class.java).apply {
                putExtra(InCallActivity.EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(InCallActivity.EXTRA_CONTACT_NAME, contactName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)

            _isCallActive = true

            android.util.Log.d("CallManager", "✅ Llamada iniciada: $phoneNumber")
            true

        } catch (e: Exception) {
            android.util.Log.e("CallManager", "❌ Error al llamar: ${e.message}", e)
            false
        }
    }

    /**
     * Cuelga la llamada actual
     */
    fun endCall(): Boolean {
        return try {
            val success = telecomCallManager.endCall()

            if (success) {
                _isCallActive = false
                android.util.Log.d("CallManager", "✅ Llamada colgada")
            } else {
                android.util.Log.w("CallManager", "⚠️ No hay llamada activa")
            }

            success

        } catch (e: Exception) {
            android.util.Log.e("CallManager", "❌ Error al colgar: ${e.message}", e)
            false
        }
    }

    /**
     * Verifica si hay permiso de llamadas
     */
    private fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Valida formato de número telefónico
     */
    private fun isValidPhoneNumber(number: String): Boolean {
        if (number.isEmpty() || number.length < 3) return false
        val pattern = Regex("^[+]?[0-9]{3,15}$")
        return pattern.matches(number.replace(Regex("[\\s-]"), ""))
    }

    /**
     * Verifica si hay una llamada en curso
     */
    fun checkCallStatus(): Boolean {
        _isCallActive = telecomCallManager.isInCall()
        return _isCallActive
    }

    /**
     * Obtiene el nombre del contacto desde el número
     * (para usar al llamar desde historial/favoritos)
     */
    fun getContactName(phoneNumber: String): String? {
        // TODO: Implementar búsqueda en ContactsProvider
        return null
    }
}