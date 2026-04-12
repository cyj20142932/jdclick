package com.jdhelper.di

import android.content.Context
import androidx.room.Room
import com.jdhelper.data.local.AutoClickerDatabase
import com.jdhelper.data.local.ClickSettingsDao
import com.jdhelper.data.local.GiftClickHistoryDao
import com.jdhelper.data.repository.ClickSettingsRepositoryImpl
import com.jdhelper.domain.repository.ClickSettingsRepository
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
    fun provideClickSettingsRepository(clickSettingsDao: ClickSettingsDao, @ApplicationContext context: Context): ClickSettingsRepository {
        return ClickSettingsRepositoryImpl(clickSettingsDao, context)
    }
}