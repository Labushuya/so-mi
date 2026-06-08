package io.somi.rag

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/**
 * v0.14.0 M1 — placeholder entity required so the io.objectbox
 * Gradle plugin generates `MyObjectBox` (the schema builder
 * needed by RagModule). Without at least one `@Entity`, the
 * plugin emits no schema and the BoxStore.builder() call won't
 * resolve.
 *
 * Lives in `io.somi.rag` (not a subpackage) so the generated
 * `MyObjectBox` lands in the same package and `RagModule`'s
 * import resolves cleanly. The `internal` modifier still hides
 * the entity from consumers of `:core-rag`.
 *
 * Never written in production. Existence proves codegen ran
 * end-to-end. M3 introduces the real `MemoryFact` entity; this
 * placeholder can stay (cheap) or be deleted then.
 */
@Entity
internal data class _Bootstrap(
    @Id var id: Long = 0,
    /** Will never be written. */
    var sentinel: String = "",
)
