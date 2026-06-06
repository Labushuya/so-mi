package io.somi.llm.llama.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.somi.llm.LlamaContext
import io.somi.llm.llama.LlamaCppContext
import javax.inject.Singleton

/**
 * Phase-2.3 Hilt swap: replaces the NoOpLlamaContext binding from
 * `:core-llm:LlamaModule` with the real native-backed LlamaCppContext.
 *
 * Coexistence rule: the NoOp binding in `:core-llm:LlamaModule` MUST be
 * removed before this module compiles in. Hilt fails the build on
 * duplicate bindings to the same interface; that's the load-bearing
 * constraint that makes the swap atomic and reversible — drop the
 * `:core-llm-llama` dependency from `:app` and re-add the NoOp `@Binds`
 * line in `:core-llm` to revert.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LlamaCppModule {

    @Binds
    @Singleton
    abstract fun bindLlamaContext(impl: LlamaCppContext): LlamaContext
}
