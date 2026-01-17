package com.spamstopper.app.di

import android.content.Context
import com.spamstopper.app.data.database.BlockedCallDao
import com.spamstopper.app.data.database.SpamStopperDatabase
import com.spamstopper.app.data.preferences.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para inyección de dependencias
 *
 * Proporciona instancias singleton de:
 * - Database
 * - DAOs
 * - UserPreferences
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Proporciona instancia de la base de datos
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): SpamStopperDatabase {
        return SpamStopperDatabase.getInstance(context)
    }

    /**
     * Proporciona BlockedCallDao
     */
    @Provides
    @Singleton
    fun provideBlockedCallDao(
        database: SpamStopperDatabase
    ): BlockedCallDao {
        return database.blockedCallDao()
    }

    /**
     * Proporciona UserPreferences
     */
    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context
    ): UserPreferences {
        return UserPreferences(context)
    }
}