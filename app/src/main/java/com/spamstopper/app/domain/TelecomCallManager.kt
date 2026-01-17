package com.spamstopper.app.domain

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TelecomCallManager - Gestión de llamadas
 *
 * NOTA: Como somos el marcador por defecto (default dialer),
 * NO necesitamos registrar un PhoneAccount propio.
 * El sistema usa el PhoneAccount de la SIM automáticamente.
 *
 * Este manager solo se encarga de iniciar y finalizar llamadas.
 */
@Singleton
class TelecomCallManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TelecomCallManager"
    }

    private val telecomManager: TelecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    /**
     * Registra el PhoneAccount - DESHABILITADO
     *
     * Como somos el default dialer, no necesitamos PhoneAccount propio.
     * El sistema enruta las llamadas a través de nuestro InCallService automáticamente.
     */
    fun registerPhoneAccount() {
        // NO registrar PhoneAccount - causa conflictos con SELF_MANAGED
        android.util.Log.d(TAG, "ℹ️ PhoneAccount no necesario (somos default dialer)")
    }

    /**
     * Inicia una llamada saliente
     *
     * Usa el sistema de telecom estándar, que enrutará la llamada
     * a través de nuestro SpamInCallService porque somos el default dialer.
     */
    fun placeCall(phoneNumber: String): Boolean {
        return try {
            // Limpiar número
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

            if (cleanNumber.isEmpty()) {
                android.util.Log.w(TAG, "⚠️ Número vacío")
                return false
            }

            val uri = Uri.fromParts("tel", cleanNumber, null)

            val extras = Bundle().apply {
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_AUDIO_ONLY)
            }

            telecomManager.placeCall(uri, extras)

            android.util.Log.d(TAG, "✅ Llamada iniciada: $cleanNumber")
            true

        } catch (e: SecurityException) {
            android.util.Log.e(TAG, "❌ Sin permiso CALL_PHONE", e)
            false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error iniciando llamada", e)
            false
        }
    }

    /**
     * Finaliza la llamada actual
     */
    fun endCall(): Boolean {
        return try {
            if (telecomManager.isInCall) {
                @Suppress("DEPRECATION")
                telecomManager.endCall()
                android.util.Log.d(TAG, "✅ Llamada finalizada")
                true
            } else {
                android.util.Log.w(TAG, "⚠️ No hay llamada activa")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error finalizando llamada", e)
            false
        }
    }

    /**
     * Verifica si hay una llamada en curso
     */
    fun isInCall(): Boolean {
        return try {
            telecomManager.isInCall
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verifica si somos el marcador por defecto
     */
    fun isDefaultDialer(): Boolean {
        return try {
            telecomManager.defaultDialerPackage == context.packageName
        } catch (e: Exception) {
            false
        }
    }
}