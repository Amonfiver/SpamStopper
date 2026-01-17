package com.spamstopper.app

import android.app.Application
import com.spamstopper.app.domain.TelecomCallManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpamStopperApp : Application() {

    @Inject
    lateinit var telecomCallManager: TelecomCallManager

    override fun onCreate() {
        super.onCreate()

        android.util.Log.d("SpamStopperApp", "🚀 App iniciada")

        // Registrar PhoneAccount al iniciar la app
        try {
            telecomCallManager.registerPhoneAccount()
            android.util.Log.d("SpamStopperApp", "✅ PhoneAccount registrado")
        } catch (e: Exception) {
            android.util.Log.e("SpamStopperApp", "❌ Error registrando PhoneAccount", e)
        }
    }
}