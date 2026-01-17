package com.spamstopper.app.presentation.dialer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spamstopper.app.domain.CallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DialerViewModel @Inject constructor(
    private val callManager: CallManager
) : ViewModel() {

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _isCallInProgress = MutableStateFlow(false)
    val isCallInProgress: StateFlow<Boolean> = _isCallInProgress.asStateFlow()

    /**
     * Añade un dígito al número
     */
    fun addDigit(digit: String) {
        if (_phoneNumber.value.length < 15) {
            _phoneNumber.value += digit
            android.util.Log.d("DialerViewModel", "Número actual: ${_phoneNumber.value}")
        }
    }

    /**
     * Borra el último dígito
     */
    fun deleteLastDigit() {
        if (_phoneNumber.value.isNotEmpty()) {
            _phoneNumber.value = _phoneNumber.value.dropLast(1)
            android.util.Log.d("DialerViewModel", "Número actual: ${_phoneNumber.value}")
        }
    }

    /**
     * Limpia el número
     */
    fun clearNumber() {
        _phoneNumber.value = ""
    }

    /**
     * Inicia una llamada
     */
    fun makeCall() {
        val number = _phoneNumber.value

        if (number.isEmpty()) {
            android.util.Log.w("DialerViewModel", "Número vacío")
            return
        }

        viewModelScope.launch {
            val success = callManager.makeCall(number)

            if (success) {
                _isCallInProgress.value = true
                android.util.Log.d("DialerViewModel", "Llamada iniciada: $number")

                // Limpiar número después de llamar
                clearNumber()
            } else {
                android.util.Log.e("DialerViewModel", "Error al iniciar llamada")
            }
        }
    }

    /**
     * Cuelga la llamada actual
     */
    fun hangUp() {
        viewModelScope.launch {
            val success = callManager.endCall()

            if (success) {
                _isCallInProgress.value = false
                android.util.Log.d("DialerViewModel", "Llamada colgada")
            }
        }
    }

    /**
     * Verifica el estado de la llamada
     */
    fun checkCallStatus() {
        viewModelScope.launch {
            _isCallInProgress.value = callManager.checkCallStatus()
        }
    }
}