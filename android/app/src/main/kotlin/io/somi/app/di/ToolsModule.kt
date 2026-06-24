package io.somi.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.somi.common.embed.TextEmbedder
import io.somi.common.llm.LlmCaller
import io.somi.common.memory.MemorySearchPort
import io.somi.llm.LlamaContext
import io.somi.rag.embed.TextEmbedderAdapter
import io.somi.rag.memory.MemorySearchAdapter
import io.somi.tools.executor.ToolExecutor
import io.somi.tools.memory.SearchMemoryTool
import io.somi.tools.model.ToolDefinition
import io.somi.tools.registry.BuiltInToolDefinitions
import io.somi.tools.weather.WeatherTool
import io.somi.tools.web.WebSearchTool
import kotlinx.coroutines.flow.fold
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsModule {

    @Binds @Singleton
    abstract fun bindTextEmbedder(impl: TextEmbedderAdapter): TextEmbedder

    @Binds @Singleton
    abstract fun bindMemorySearch(impl: MemorySearchAdapter): MemorySearchPort

    @Binds @Singleton
    abstract fun bindLlmCaller(impl: LlamaContextLlmCaller): LlmCaller

    @Binds @IntoSet
    abstract fun bindWeatherTool(impl: WeatherTool): ToolExecutor

    @Binds @IntoSet
    abstract fun bindWebSearchTool(impl: WebSearchTool): ToolExecutor

    @Binds @IntoSet
    abstract fun bindSearchMemoryTool(impl: SearchMemoryTool): ToolExecutor

    @Binds @IntoSet abstract fun bindReminderTool(impl: io.somi.tools.reminder.ReminderTool): ToolExecutor
    @Binds @IntoSet abstract fun bindExchangeRateTool(impl: io.somi.tools.exchange.ExchangeRateTool): ToolExecutor
    @Binds @IntoSet abstract fun bindNewsBriefingTool(impl: io.somi.tools.news.NewsBriefingTool): ToolExecutor
    @Binds @IntoSet abstract fun bindCalendarReadTool(impl: io.somi.tools.calendar.CalendarReadTool): ToolExecutor
    @Binds @IntoSet abstract fun bindCalendarCreateTool(impl: io.somi.tools.calendar.CalendarCreateTool): ToolExecutor

    companion object {
        @Provides @IntoSet
        fun provideWeatherDef(): ToolDefinition = BuiltInToolDefinitions.getWeather

        @Provides @IntoSet
        fun provideWebSearchDef(): ToolDefinition = BuiltInToolDefinitions.searchWeb

        @Provides @IntoSet
        fun provideSearchMemoryDef(): ToolDefinition = BuiltInToolDefinitions.searchMemory

        @Provides @IntoSet fun provideReminderDef(): ToolDefinition = BuiltInToolDefinitions.setAlarm
        @Provides @IntoSet fun provideExchangeRateDef(): ToolDefinition = BuiltInToolDefinitions.getExchangeRate
        @Provides @IntoSet fun provideNewsBriefingDef(): ToolDefinition = BuiltInToolDefinitions.newsBriefing
        @Provides @IntoSet fun provideCalendarReadDef(): ToolDefinition = BuiltInToolDefinitions.readCalendar
        @Provides @IntoSet fun provideCalendarCreateDef(): ToolDefinition = BuiltInToolDefinitions.createEvent
    }
}
