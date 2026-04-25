package com.jdhelper.app.di

import android.content.Context
import androidx.room.Room
import com.jdhelper.app.data.local.AutoClickerDatabase
import com.jdhelper.app.data.local.ClickSettingsDao
import com.jdhelper.app.data.local.ClickTaskDao
import com.jdhelper.app.data.local.GiftClickHistoryDao
import com.jdhelper.app.data.local.LogDao
import com.jdhelper.app.data.repository.ClickSettingsRepositoryImpl
import com.jdhelper.app.data.repository.ClickTaskRepositoryImpl
import com.jdhelper.app.data.repository.LogRepositoryImpl
import com.jdhelper.app.data.repository.ServiceStateRepositoryImpl
import com.jdhelper.app.data.repository.TimeSyncRepositoryImpl
import com.jdhelper.app.domain.repository.ClickSettingsRepository
import com.jdhelper.app.domain.repository.ClickTaskRepository
import com.jdhelper.app.domain.repository.LogRepository
import com.jdhelper.app.domain.repository.ServiceStateRepository
import com.jdhelper.app.domain.repository.TimeSyncRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AutoClickerDatabase {
        return Room.databaseBuilder(
            context,
            AutoClickerDatabase::class.java,
            "jdhelper.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideClickSettingsDao(database: AutoClickerDatabase): ClickSettingsDao {
        return database.clickSettingsDao()
    }

    @Provides
    @Singleton
    fun provideGiftClickHistoryDao(database: AutoClickerDatabase): GiftClickHistoryDao {
        return database.giftClickHistoryDao()
    }

    @Provides
    @Singleton
    fun provideLogDao(database: AutoClickerDatabase): LogDao {
        return database.logDao()
    }

    @Provides
    @Singleton
    fun provideClickTaskDao(database: AutoClickerDatabase): ClickTaskDao {
        return database.clickTaskDao()
    }

    @Provides
    @Singleton
    fun provideClickSettingsRepository(clickSettingsDao: ClickSettingsDao, @ApplicationContext context: Context): ClickSettingsRepository {
        return ClickSettingsRepositoryImpl(clickSettingsDao, context)
    }

    @Provides
    @Singleton
    fun provideLogRepository(logDao: LogDao): LogRepository {
        return LogRepositoryImpl(logDao)
    }

    @Provides
    @Singleton
    fun provideClickTaskRepository(clickTaskDao: ClickTaskDao): ClickTaskRepository {
        return ClickTaskRepositoryImpl(clickTaskDao)
    }

    @Provides
    @Singleton
    fun provideTimeSyncRepository(clickSettingsRepository: ClickSettingsRepository): TimeSyncRepository {
        return TimeSyncRepositoryImpl(clickSettingsRepository)
    }

    @Provides
    @Singleton
    fun provideServiceStateRepository(@ApplicationContext context: Context): ServiceStateRepository {
        return ServiceStateRepositoryImpl(context)
    }
}