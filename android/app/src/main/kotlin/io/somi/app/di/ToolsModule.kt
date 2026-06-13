package io.somi.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.somi.common.embed.TextEmbedder
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

    @Binds
    @Singleton
    abstract fun bindTextEmbedder(impl: TextEmbedderAdapter): TextEmbedder

    @Binds
    @Singleton
    abstract fun bindMemorySearch(impl: MemorySearchAdapter): MemorySearchPort

    @Binds
    @IntoSet
    abstract fun bindWeatherTool(impl: WeatherTool): ToolExecutor

    @Binds
    @IntoSet
    abstract fun bindWebSearchTool(impl: WebSearchTool): ToolExecutor

    @Binds
    @IntoSet
    abstract fun bindSearchMemoryTool(impl: SearchMemoryTool): ToolExecutor

    companion object {

        @Provides
        @IntoSet
        fun provideWeatherDef(): ToolDefinition = BuiltInToolDefinitions.getWeather

        @Provides
        @IntoSet
        fun provideWebSearchDef(): ToolDefinition = BuiltInToolDefinitions.searchWeb

        @Provides
        @IntoSet
        fun provideSearchMemoryDef(): ToolDefinition = BuiltInToolDefinitions.searchMemory

        @Provides
        @Singleton
        @LlmPlanPassQualifier
        fun provideLlmPlanPass(llama: LlamaContext): @JvmSuppressWildcards suspend (String) -> String =
            { prompt ->
                val sb = StringBuilder()
                llama.generate(prompt, maxTokens = 512).fold(sb) { acc, chunk ->
                    acc.also { it.append(chunk) }
                }
                sb.toString()
            }
    }
}
