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
 * PermissionsHelper - Gestión centralizada de permisos
 */
object PermissionsHelper {

    /**
     * Permisos requeridos para funcionamiento completo
     */
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )

    /**
     * Permisos críticos para Modo Secretaria
     */
    val SECRETARY_PERMISSIONS = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.RECORD_AUDIO
    )

    /**
     * Verifica si todos los permisos están concedidos
     */
    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Verifica permisos para Modo Secretaria
     */
    fun hasSecretaryPermissions(context: Context): Boolean {
        val hasPermissions = SECRETARY_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }

        val isDefaultDialer = isDefaultDialer(context)

        return hasPermissions && isDefaultDialer
    }

    /**
     * Verifica si la app es el marcador por defecto
     */
    fun isDefaultDialer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            return telecomManager.defaultDialerPackage == context.packageName
        }
        return true
    }

    /**
     * Solicita rol de marcador por defecto
     */
    fun requestDefaultDialer(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            intent.putExtra(
                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                context.packageName
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Obtiene permisos faltantes
     */
    fun getMissingPermissions(context: Context): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Obtiene permisos faltantes para Modo Secretaria
     */
    fun getMissingSecretaryPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()

        SECRETARY_PERMISSIONS.forEach { permission ->
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission)
            }
        }

        if (!isDefaultDialer(context)) {
            missing.add("DEFAULT_DIALER")
        }

        return missing
    }

    /**
     * Obtiene nombres legibles de permisos
     */
    fun getPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_PHONE_STATE -> "Estado del teléfono"
            Manifest.permission.CALL_PHONE -> "Realizar llamadas"
            Manifest.permission.READ_CALL_LOG -> "Leer historial"
            Manifest.permission.WRITE_CALL_LOG -> "Escribir historial"
            Manifest.permission.READ_CONTACTS -> "Leer contactos"
            Manifest.permission.ANSWER_PHONE_CALLS -> "Contestar llamadas"
            Manifest.permission.RECORD_AUDIO -> "Grabar audio"
            Manifest.permission.MODIFY_AUDIO_SETTINGS -> "Modificar audio"
            "DEFAULT_DIALER" -> "Marcador por defecto"
            else -> permission
        }
    }
}