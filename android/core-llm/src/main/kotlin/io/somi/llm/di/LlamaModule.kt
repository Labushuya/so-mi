package io.somi.llm.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.somi.llm.LlamaContext
import io.somi.llm.NoOpLlamaContext
import io.somi.llm.SoulPromptLoader
import io.somi.llm.StubSoulPromptLoader
import javax.inject.Singleton

/**
 * Hilt module exposing the LLM-side singletons.
 *
 * Scope: SingletonComponent. The native llama.cpp context (Phase 2.3+)
 * is multi-GB-mmap and must live exactly once per process; binding it
 * any narrower is a memory bug. The Phase-2.1 NoOp implementation
 * doesn't need the singleton scope semantically, but matching the real
 * scope prevents a binding-scope churn when 2.3 swaps in the JNI impl.
 *
 * Swap pattern for 2.3: change the `bindLlamaContext` impl arg from
 * `NoOpLlamaContext` to `LlamaCppContext` and move this module to
 * `:core-llm-llama`. No call-site changes.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LlamaModule {

    @Binds
    @Singleton
    abstract fun bindLlamaContext(impl: NoOpLlamaContext): LlamaContext

    @Binds
    @Singleton
    abstract fun bindSoulPromptLoader(impl: StubSoulPromptLoader): SoulPromptLoader
}
