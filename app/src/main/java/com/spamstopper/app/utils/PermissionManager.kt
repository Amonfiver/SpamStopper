package com.spamstopper.app.utils

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * Gestor de permisos para SpamStopper
 *
 * Maneja todos los permisos necesarios:
 * - Permisos de llamadas
 * - Permisos de contactos
 * - Permisos de audio
 * - Role de Default Dialer
 */
object PermissionManager {

    /**
     * Permisos runtime necesarios
     */
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.RECORD_AUDIO
    )

    /**
     * Permiso de notificaciones (Android 13+)
     */
    val NOTIFICATION_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    /**
     * Verifica si TODOS los permisos están concedidos
     */
    fun hasAllPermissions(context: Context): Boolean {
        val runtimePermissions = REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return runtimePermissions && notificationPermission && isDefaultDialer(context)
    }

    /**
     * Verifica permisos runtime específicos
     */
    fun hasRuntimePermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Verifica si la app es Default Dialer
     */
    fun isDefaultDialer(context: Context): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return telecomManager?.defaultDialerPackage == context.packageName
    }

    /**
     * Obtiene Intent para solicitar role de Default Dialer
     */
    fun getDefaultDialerIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Usar RoleManager
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else {
                null
            }
        } else {
            // Android 9 y anteriores: Usar TelecomManager
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        }
    }

    /**
     * Obtiene lista de permisos faltantes
     */
    fun getMissingPermissions(context: Context): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Obtiene nombre legible de un permiso
     */
    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_PHONE_STATE -> "Estado del teléfono"
            Manifest.permission.READ_CALL_LOG -> "Registro de llamadas"
            Manifest.permission.ANSWER_PHONE_CALLS -> "Contestar llamadas"
            Manifest.permission.READ_CONTACTS -> "Contactos"
            Manifest.permission.CALL_PHONE -> "Realizar llamadas"
            Manifest.permission.RECORD_AUDIO -> "Grabar audio"
            Manifest.permission.POST_NOTIFICATIONS -> "Notificaciones"
            else -> permission
        }
    }

    /**
     * Obtiene descripción de para qué se usa cada permiso
     */
    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_PHONE_STATE ->
                "Para detectar llamadas entrantes"
            Manifest.permission.READ_CALL_LOG ->
                "Para verificar historial de llamadas"
            Manifest.permission.ANSWER_PHONE_CALLS ->
                "Para contestar automáticamente y analizar"
            Manifest.permission.READ_CONTACTS ->
                "Para identificar contactos conocidos"
            Manifest.permission.CALL_PHONE ->
                "Para funciones de marcador"
            Manifest.permission.RECORD_AUDIO ->
                "Para analizar lo que dice el caller"
            Manifest.permission.POST_NOTIFICATIONS ->
                "Para notificar spam bloqueado"
            else -> ""
        }
    }
}