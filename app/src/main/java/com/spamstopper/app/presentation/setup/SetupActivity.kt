package com.spamstopper.app.presentation.setup

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.spamstopper.app.presentation.MainActivity
import com.spamstopper.app.ui.theme.SpamStopperTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * SetupActivity - Configuración inicial de permisos
 *
 * Maneja la solicitud de todos los permisos necesarios
 * y configura SpamStopper como app de teléfono predeterminada
 */
@AndroidEntryPoint
class SetupActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SetupActivity"

        /**
         * Todos los permisos requeridos por la app
         */
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        ).apply {
            // POST_NOTIFICATIONS solo en Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Estado reactivo para forzar recomposición
    private var permissionsState = mutableStateOf(false)
    private var defaultDialerState = mutableStateOf(false)

    /**
     * Launcher para solicitar permisos múltiples
     */
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "📋 Resultado de permisos: $permissions")

        val granted = permissions.filterValues { it }.keys
        val denied = permissions.filterValues { !it }.keys

        Log.d(TAG, "✅ Concedidos: $granted")
        Log.d(TAG, "❌ Denegados: $denied")

        // Actualizar estado
        permissionsState.value = hasAllPermissions()

        if (denied.isNotEmpty()) {
            Toast.makeText(
                this,
                "⚠️ Algunos permisos fueron denegados. La app puede no funcionar correctamente.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Launcher para solicitar rol de marcador (Android 10+)
     */
    private val roleDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "📞 Resultado de rol marcador: ${result.resultCode}")
        defaultDialerState.value = isDefaultDialer()
    }

    /**
     * Launcher para solicitar marcador por defecto (Android 6-9)
     */
    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "📞 Resultado de marcador por defecto: ${result.resultCode}")
        defaultDialerState.value = isDefaultDialer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 SetupActivity iniciada")

        // Verificar estado inicial
        permissionsState.value = hasAllPermissions()
        defaultDialerState.value = isDefaultDialer()

        // Si ya está todo configurado, ir directo a MainActivity
        if (isSetupComplete()) {
            Log.d(TAG, "✅ Setup ya completo, saltando a MainActivity")
            goToMainActivity()
            return
        }

        setContent {
            SpamStopperTheme {
                SetupScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🔄 onResume - Actualizando estados")

        // Actualizar estados al volver
        permissionsState.value = hasAllPermissions()
        defaultDialerState.value = isDefaultDialer()
    }

    /**
     * Verifica si todos los permisos están concedidos
     */
    private fun hasAllPermissions(): Boolean {
        val result = REQUIRED_PERMISSIONS.all { permission ->
            val granted = ContextCompat.checkSelfPermission(this, permission) ==
                    PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "  $permission: ${if (granted) "✅" else "❌"}")
            granted
        }
        Log.d(TAG, "📋 Todos los permisos: ${if (result) "✅" else "❌"}")
        return result
    }

    /**
     * Obtiene lista de permisos faltantes
     */
    private fun getMissingPermissions(): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Verifica si la app es el marcador por defecto
     */
    private fun isDefaultDialer(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val isDefault = telecomManager.defaultDialerPackage == packageName
            Log.d(TAG, "📞 Es marcador por defecto: ${if (isDefault) "✅" else "❌"}")
            isDefault
        } else {
            true
        }
    }

    /**
     * Verifica si el setup está completo
     */
    private fun isSetupComplete(): Boolean {
        val prefs = getSharedPreferences("spamstopper_prefs", MODE_PRIVATE)
        val savedComplete = prefs.getBoolean("setup_completed", false)
        val actuallyComplete = hasAllPermissions() && isDefaultDialer()

        return savedComplete && actuallyComplete
    }

    /**
     * Solicita todos los permisos necesarios
     */
    private fun requestPermissions() {
        val missing = getMissingPermissions()

        if (missing.isEmpty()) {
            Log.d(TAG, "✅ Todos los permisos ya están concedidos")
            permissionsState.value = true
            return
        }

        Log.d(TAG, "🔐 Solicitando permisos: $missing")

        try {
            permissionsLauncher.launch(missing.toTypedArray())
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error solicitando permisos", e)
            Toast.makeText(this, "Error al solicitar permisos", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Solicita ser el marcador por defecto
     */
    private fun requestDefaultDialer() {
        Log.d(TAG, "📞 Solicitando rol de marcador por defecto...")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ usa RoleManager
                val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager

                if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                        roleDialerLauncher.launch(intent)
                    } else {
                        Log.d(TAG, "✅ Ya somos el marcador por defecto")
                        defaultDialerState.value = true
                    }
                } else {
                    Log.w(TAG, "⚠️ Rol DIALER no disponible")
                    Toast.makeText(this, "El rol de marcador no está disponible", Toast.LENGTH_SHORT).show()
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6-9 usa TelecomManager
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                }
                defaultDialerLauncher.launch(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error solicitando marcador por defecto", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Finaliza el setup y va a MainActivity
     */
    private fun finishSetup() {
        Log.d(TAG, "🎉 Finalizando setup...")

        // Guardar que el setup está completo
        getSharedPreferences("spamstopper_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("setup_completed", true)
            .apply()

        goToMainActivity()
    }

    /**
     * Navega a MainActivity
     */
    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    @Composable
    private fun SetupScreen() {
        val hasPermissions by permissionsState
        val isDialer by defaultDialerState
        val setupComplete = hasPermissions && isDialer

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Header
                Text(
                    text = "🛡️",
                    style = MaterialTheme.typography.displayLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SpamStopper",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Configuración Inicial",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Paso 1: Permisos
                PermissionCard(
                    stepNumber = 1,
                    title = "Permisos del Sistema",
                    description = "Necesitamos acceso a llamadas, contactos, historial y micrófono para protegerte del spam.",
                    isCompleted = hasPermissions,
                    buttonText = if (hasPermissions) "Permisos Concedidos" else "Conceder Permisos",
                    onButtonClick = { requestPermissions() },
                    enabled = !hasPermissions
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Paso 2: Marcador por defecto
                PermissionCard(
                    stepNumber = 2,
                    title = "App de Teléfono",
                    description = "Establece SpamStopper como tu app de teléfono para gestionar todas las llamadas.",
                    isCompleted = isDialer,
                    buttonText = if (isDialer) "Configurado" else "Establecer como Predeterminado",
                    onButtonClick = { requestDefaultDialer() },
                    enabled = hasPermissions && !isDialer
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Botón finalizar
                Button(
                    onClick = { finishSetup() },
                    enabled = setupComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (setupComplete)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (setupComplete) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (setupComplete) "¡Comenzar!" else "Completa los pasos anteriores",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Info adicional
                if (!setupComplete) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Todos los permisos son necesarios para que SpamStopper funcione correctamente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    @Composable
    private fun PermissionCard(
        stepNumber: Int,
        title: String,
        description: String,
        isCompleted: Boolean,
        buttonText: String,
        onButtonClick: () -> Unit,
        enabled: Boolean
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isCompleted)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Número de paso
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isCompleted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    ) {
                        Text(
                            text = "$stepNumber",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (isCompleted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Completado",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onButtonClick,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (isCompleted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(buttonText)
                }
            }
        }
    }
}