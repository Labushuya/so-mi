package io.somi.llm.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.somi.llm.SoulPromptLoader
import io.somi.llm.StubSoulPromptLoader
import javax.inject.Singleton

/**
 * Hilt module exposing the soul.md loader.
 *
 * Phase 2.3: the `LlamaContext` binding moved out of this module — it's
 * now provided by `:core-llm-llama:LlamaCppModule` (real native impl).
 * The `NoOpLlamaContext` class stays in this module so unit tests can
 * substitute it via `@TestInstallIn`.
 *
 * Hilt fails the build on duplicate bindings; if you ever revert the
 * Phase-2.3 swap, re-add the NoOp `@Binds` line below AND drop the
 * `:core-llm-llama` dependency from `:app`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LlamaModule {

    @Binds
    @Singleton
    abstract fun bindSoulPromptLoader(impl: StubSoulPromptLoader): SoulPromptLoader
}
