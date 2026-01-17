package com.spamstopper.app.presentation.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callManager: CallManager
) : ViewModel() {

    private val _favorites = MutableStateFlow<List<Contact>>(emptyList())
    val favorites: StateFlow<List<Contact>> = _favorites.asStateFlow()

    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
    val allContacts: StateFlow<List<Contact>> = _allContacts.asStateFlow()

    private val prefs = context.getSharedPreferences("SpamStopperPrefs", Context.MODE_PRIVATE)

    init {
        loadFavorites()
        loadAllContacts()
    }

    /**
     * Carga todos los contactos del sistema
     */
    private fun loadAllContacts() {
        viewModelScope.launch {
            if (!hasContactsPermission()) {
                android.util.Log.w("ContactsViewModel", "Sin permiso READ_CONTACTS")
                return@launch
            }

            val contacts = withContext(Dispatchers.IO) {
                readContactsFromSystem()
            }

            _allContacts.value = contacts
            android.util.Log.d("ContactsViewModel", "Contactos cargados: ${contacts.size}")
        }
    }

    /**
     * Lee contactos del sistema
     */
    private fun readContactsFromSystem(): List<Contact> {
        val contactsList = mutableListOf<Contact>()

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val name = it.getString(nameIndex)
                    val number = it.getString(numberIndex)

                    if (id != null && name != null && number != null) {
                        contactsList.add(
                            Contact(
                                id = id,
                                name = name,
                                phoneNumber = number.replace(Regex("[^0-9+]"), "")
                            )
                        )
                    }
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("ContactsViewModel", "Error leyendo contactos: ${e.message}", e)
        }

        return contactsList
    }

    /**
     * Carga favoritos desde SharedPreferences
     */
    private fun loadFavorites() {
        val favoritesJson = prefs.getString("favorites", "[]") ?: "[]"

        // Parse simple: "id|name|phone;id|name|phone"
        val favoritesList = if (favoritesJson != "[]") {
            favoritesJson
                .removeSurrounding("[", "]")
                .split(";")
                .filter { it.isNotEmpty() }
                .mapNotNull { entry ->
                    val parts = entry.split("|")
                    if (parts.size == 3) {
                        Contact(
                            id = parts[0],
                            name = parts[1],
                            phoneNumber = parts[2]
                        )
                    } else null
                }
        } else {
            emptyList()
        }

        _favorites.value = favoritesList
        android.util.Log.d("ContactsViewModel", "Favoritos cargados: ${favoritesList.size}")
    }

    /**
     * Guarda favoritos en SharedPreferences
     */
    private fun saveFavorites() {
        val favoritesString = _favorites.value.joinToString(";") { contact ->
            "${contact.id}|${contact.name}|${contact.phoneNumber}"
        }

        prefs.edit()
            .putString("favorites", "[$favoritesString]")
            .apply()

        android.util.Log.d("ContactsViewModel", "Favoritos guardados: ${_favorites.value.size}")
    }

    /**
     * Añade un contacto a favoritos
     */
    fun addToFavorites(contact: Contact) {
        if (_favorites.value.none { it.phoneNumber == contact.phoneNumber }) {
            _favorites.value = _favorites.value + contact
            saveFavorites()
            android.util.Log.d("ContactsViewModel", "Favorito añadido: ${contact.name}")
        }
    }

    /**
     * Elimina un contacto de favoritos
     */
    fun removeFromFavorites(contact: Contact) {
        _favorites.value = _favorites.value.filterNot { it.phoneNumber == contact.phoneNumber }
        saveFavorites()
        android.util.Log.d("ContactsViewModel", "Favorito eliminado: ${contact.name}")
    }

    /**
     * Llama a un contacto
     */
    fun callContact(contact: Contact) {
        viewModelScope.launch {
            callManager.makeCall(contact.phoneNumber)
        }
    }

    /**
     * Verifica permiso de contactos
     */
    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
}