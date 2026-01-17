package com.spamstopper.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spamstopper.app.presentation.contacts.Contact
import com.spamstopper.app.presentation.contacts.ContactsViewModel
import com.spamstopper.app.presentation.contacts.FavoritesScreen
import com.spamstopper.app.presentation.dialer.DialerScreen
import com.spamstopper.app.presentation.dialer.DialerViewModel
import com.spamstopper.app.presentation.history.CallHistoryItem
import com.spamstopper.app.presentation.history.CallTypeFilter
import com.spamstopper.app.presentation.history.HistoryScreen
import com.spamstopper.app.presentation.history.HistoryViewModel
import com.spamstopper.app.presentation.settings.SettingsScreen
import com.spamstopper.app.ui.theme.SpamStopperTheme
import com.spamstopper.app.utils.PermissionsHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        android.util.Log.d("MainActivity", "📋 Permisos recibidos: $permissions")

        val allGranted = permissions.values.all { it }

        if (allGranted) {
            android.util.Log.d("MainActivity", "✅ Todos los permisos concedidos")

            // Solicitar marcador por defecto si es necesario
            if (!PermissionsHelper.isDefaultDialer(this)) {
                android.util.Log.d("MainActivity", "📞 Solicitando marcador por defecto...")
                PermissionsHelper.requestDefaultDialer(this)
            }
        } else {
            android.util.Log.w("MainActivity", "⚠️ Algunos permisos fueron denegados")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            android.util.Log.d("MainActivity", "🚀 MainActivity iniciada")

            setContent {
                SpamStopperTheme {
                    MainScreen()
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ ERROR: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("MainActivity", "🔄 MainActivity: onResume")

        // Verificar permisos al volver
        if (!PermissionsHelper.hasAllPermissions(this)) {
            android.util.Log.w("MainActivity", "⚠️ Faltan permisos")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentRoute) {
                            "dialer" -> "📞 SpamStopper"
                            "favorites" -> "⭐ Favoritos"
                            "history" -> "📋 Historial"
                            "settings" -> "⚙️ Configuración"
                            else -> "SpamStopper"
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    label = { Text("Dialer") },
                    selected = currentRoute == "dialer",
                    onClick = {
                        navController.navigate("dialer") {
                            popUpTo("dialer") { inclusive = true }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text("Favoritos") },
                    selected = currentRoute == "favorites",
                    onClick = {
                        navController.navigate("favorites") {
                            popUpTo("dialer")
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Historial") },
                    selected = currentRoute == "history",
                    onClick = {
                        navController.navigate("history") {
                            popUpTo("dialer")
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Ajustes") },
                    selected = currentRoute == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo("dialer")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "dialer",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("dialer") {
                val viewModel: DialerViewModel = hiltViewModel()
                val phoneNumber by viewModel.phoneNumber.collectAsState()
                val isCallInProgress by viewModel.isCallInProgress.collectAsState()

                DialerScreen(
                    phoneNumber = phoneNumber,
                    onNumberChange = { digit: String ->
                        viewModel.addDigit(digit)
                    },
                    onCallClick = {
                        viewModel.makeCall()
                    },
                    onHangUpClick = {
                        viewModel.hangUp()
                    },
                    onDeleteClick = {
                        viewModel.deleteLastDigit()
                    },
                    onContactsClick = {
                        navController.navigate("favorites")
                    },
                    isCallInProgress = isCallInProgress
                )
            }

            composable("favorites") {
                val viewModel: ContactsViewModel = hiltViewModel()
                val favorites by viewModel.favorites.collectAsState()
                val allContacts by viewModel.allContacts.collectAsState()

                FavoritesScreen(
                    favorites = favorites,
                    allContacts = allContacts,
                    onContactClick = { contact: Contact ->
                        viewModel.callContact(contact)
                    },
                    onAddFavorite = { contact: Contact ->
                        viewModel.addToFavorites(contact)
                    },
                    onRemoveFavorite = { contact: Contact ->
                        viewModel.removeFromFavorites(contact)
                    }
                )
            }

            composable("history") {
                val viewModel: HistoryViewModel = hiltViewModel()
                val historyItems by viewModel.historyItems.collectAsState()

                HistoryScreen(
                    historyItems = historyItems,
                    onCallClick = { item: CallHistoryItem ->
                        viewModel.callBack(item)
                    },
                    onFilterChange = { filter: CallTypeFilter ->
                        viewModel.filterByType(filter)
                    }
                )
            }

            composable("settings") {
                SettingsScreen()
            }
        }
    }
}