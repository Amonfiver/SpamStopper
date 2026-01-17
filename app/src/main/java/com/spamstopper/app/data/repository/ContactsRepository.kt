package com.spamstopper.app.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.spamstopper.app.data.database.SpamStopperDatabase
import com.spamstopper.app.domain.model.Contact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SpamStopperDatabase
) {

    /**
     * Verifica si un número es un contacto guardado
     */
    suspend fun isContact(phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedNumber = normalizePhoneNumber(phoneNumber)

        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
        val selectionArgs = arrayOf(normalizedNumber)

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            return@withContext cursor.count > 0
        } ?: false
    }

    /**
     * Obtiene todos los contactos del sistema
     */
    suspend fun getAllContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (cursor.moveToNext()) {
                contacts.add(
                    Contact(
                        id = cursor.getString(idIndex),
                        name = cursor.getString(nameIndex) ?: "Sin nombre",
                        phoneNumber = cursor.getString(numberIndex) ?: "",
                        photoUri = cursor.getString(photoIndex)
                    )
                )
            }
        }

        contacts
    }

    /**
     * Busca contactos por nombre o número
     */
    suspend fun searchContacts(query: String): List<Contact> = withContext(Dispatchers.IO) {
        val allContacts = getAllContacts()
        allContacts.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.phoneNumber.contains(query)
        }
    }

    /**
     * Obtiene contactos favoritos (guardados en Room)
     */
    suspend fun getFavorites(): List<Contact> = withContext(Dispatchers.IO) {
        // TODO: Implementar tabla de favoritos en Room
        emptyList()
    }

    /**
     * Añade un contacto a favoritos
     */
    suspend fun addToFavorites(contact: Contact) = withContext(Dispatchers.IO) {
        // TODO: Implementar en Room
    }

    /**
     * Elimina un contacto de favoritos
     */
    suspend fun removeFromFavorites(contactId: String) = withContext(Dispatchers.IO) {
        // TODO: Implementar en Room
    }

    /**
     * Normaliza un número de teléfono para comparación
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.filter { it.isDigit() }
    }

    /**
     * Obtiene el nombre de un contacto por número
     */
    suspend fun getContactNameByNumber(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        val normalizedNumber = normalizePhoneNumber(phoneNumber)

        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
        val selectionArgs = arrayOf(normalizedNumber)

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                return@withContext cursor.getString(nameIndex)
            }
        }

        null
    }
}