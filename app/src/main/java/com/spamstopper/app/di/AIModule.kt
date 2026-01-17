package com.spamstopper.app.di

import android.content.Context
import com.spamstopper.app.data.preferences.UserPreferences
import com.spamstopper.app.data.repository.ContactsRepository
import com.spamstopper.app.services.ai.AudioCaptureManager
import com.spamstopper.app.services.ai.CallAnalysisOrchestrator
import com.spamstopper.app.services.ai.EmergencyKeywordDetector
import com.spamstopper.app.services.ai.LegitimacyDetector
import com.spamstopper.app.services.ai.RobotCallDetector
import com.spamstopper.app.services.ai.SecretaryModeManager
import com.spamstopper.app.services.ai.SpamClassifier
import com.spamstopper.app.services.ai.VoskSTTEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    @Provides
    @Singleton
    fun provideVoskSTTEngine(
        @ApplicationContext context: Context
    ): VoskSTTEngine {
        return VoskSTTEngine(context)
    }

    @Provides
    @Singleton
    fun provideAudioCaptureManager(): AudioCaptureManager {
        return AudioCaptureManager()
    }

    @Provides
    @Singleton
    fun provideEmergencyKeywordDetector(): EmergencyKeywordDetector {
        return EmergencyKeywordDetector()
    }

    @Provides
    @Singleton
    fun provideRobotCallDetector(): RobotCallDetector {
        return RobotCallDetector()
    }

    @Provides
    @Singleton
    fun provideSpamClassifier(): SpamClassifier {
        return SpamClassifier()
    }

    @Provides
    @Singleton
    fun provideLegitimacyDetector(): LegitimacyDetector {
        return LegitimacyDetector()
    }

    // REMOVIDO: provideUserPreferences (ya está en DatabaseModule)

    @Provides
    @Singleton
    fun provideSecretaryModeManager(
        @ApplicationContext context: Context,
        audioCaptureManager: AudioCaptureManager,
        voskSTTEngine: VoskSTTEngine,
        robotDetector: RobotCallDetector,
        spamClassifier: SpamClassifier,
        emergencyDetector: EmergencyKeywordDetector,
        legitimacyDetector: LegitimacyDetector,
        contactsRepository: ContactsRepository,
        userPreferences: UserPreferences
    ): SecretaryModeManager {
        return SecretaryModeManager(
            context,
            audioCaptureManager,
            voskSTTEngine,
            robotDetector,
            spamClassifier,
            emergencyDetector,
            legitimacyDetector,
            contactsRepository,
            userPreferences
        )
    }

    @Provides
    @Singleton
    fun provideCallAnalysisOrchestrator(
        @ApplicationContext context: Context,
        voskSTTEngine: VoskSTTEngine,
        emergencyDetector: EmergencyKeywordDetector,
        robotDetector: RobotCallDetector,
        contactsRepository: ContactsRepository
    ): CallAnalysisOrchestrator {
        return CallAnalysisOrchestrator(
            context,
            voskSTTEngine,
            emergencyDetector,
            robotDetector,
            contactsRepository
        )
    }
}