package com.jdhelper.app.di

import android.content.Context
import com.jdhelper.app.service.DefaultTimeService
import com.jdhelper.app.service.JdTimeService
import com.jdhelper.app.service.LogConsole
import com.jdhelper.app.service.TimeService
import com.jdhelper.app.domain.repository.ClickSettingsRepository
import com.jdhelper.app.domain.repository.LogRepository
import com.jdhelper.app.service.NtpTimeService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideNtpTimeService(@ApplicationContext context: Context): NtpTimeService {
        return NtpTimeService(context)
    }

    @Provides
    @Singleton
    fun provideTimeService(
        ntpTimeService: NtpTimeService,
        jdTimeService: com.jdhelper.app.service.JdTimeService,
        clickSettingsRepository: ClickSettingsRepository
    ): TimeService {
        return DefaultTimeService(ntpTimeService, jdTimeService, clickSettingsRepository)
    }

    @Provides
    @Singleton
    fun provideLogConsoleInitializer(logRepository: LogRepository): LogConsoleInitializer {
        LogConsole.setRepository(logRepository)
        return LogConsoleInitializer()
    }
}

class LogConsoleInitializer