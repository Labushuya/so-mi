package io.somi.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Network-stack singletons.
 *
 * Phase 2.4: only OkHttpClient. Tuned for streaming downloads —
 * callTimeout=0 means no overall deadline (a 4 GiB download on a
 * slow Wi-Fi can legitimately take an hour), but per-request read /
 * connect timeouts are short enough to recover from a hung CDN.
 *
 * Phase 3+ may need a separate "online-boost" client with different
 * tuning for the Claude API; introduce that under a @Qualifier when
 * the time comes.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.MILLISECONDS)        // streaming download
        .readTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
