package io.somi.data

import android.content.Context
import java.io.File

/**
 * v0.15.0 — single source for every on-disk path so-mi uses.
 *
 * **Why this exists:** before v0.15.0 paths were sprinkled across
 * five repositories (ModelStorage, EmbeddingModelStorage,
 * MemoryFileRepository, SoulRepository, DatabaseModule, RagModule,
 * SamplerSettingsRepository, UiSettingsRepository) with no
 * consolidation. The user couldn't see what so-mi had on disk
 * without a file manager and a treasure hunt under
 * `Android/data/io.somi.app/files/`. Per ROADMAP.md user
 * agreement, everything user-visible-relevant moves under
 * `$externalFilesDir/SoMi/` so the in-app Data-Browser (also new
 * in v0.15.0) and external tools both have one place to look.
 *
 * **Layout:**
 *
 *   $externalFilesDir/SoMi/
 *       llm/                  # GGUF model shards, .part sidecars
 *           <modelId>/
 *               *.gguf
 *               *.gguf.part
 *       memory/               # user-readable Markdown mirror
 *           persons.md
 *           preferences.md
 *           ...
 *       soul/                 # editable persona override + backups
 *           soul.md
 *           backups/
 *               <ts>.md
 *       db/                   # Room chat history
 *           somi.db
 *           somi.db-shm
 *           somi.db-wal
 *       settings/             # JSON blobs for UI / sampler / etc
 *           sampler.json
 *           ui.json
 *       .migrated-storage-v1  # sentinel file, marks migration done
 *
 *   $filesDir/                # internal-only, kept under filesDir
 *       models/embeddings/    # ONNX embedder + tokenizer.json
 *       objectbox/so-mi-rag/  # binary vector DB (ROADMAP-locked
 *                               in filesDir; LMDB-on-FUSE risk
 *                               not yet evaluated)
 *
 * **Why some artifacts stay in filesDir:**
 *  - **Embedder ONNX**: binary; user has no useful interaction
 *  - **ObjectBox vector DB**: LMDB file format, file-locking
 *    semantics on FUSE-emulated app-private external are
 *    reportedly fragile; deferred to v0.16.0 after on-device
 *    verification per user-locked ROADMAP decision.
 *  - **EncryptedSharedPreferences**: forced by androidx.security
 *    to live in `/data/data/<pkg>/shared_prefs/`. Cannot be moved.
 *
 * Single object, no Hilt — callers reach it via top-level
 * functions to keep it usable from `StorageMigrator` (which runs
 * BEFORE Hilt's component is constructed).
 */
object StorageRoots {

    /**
     * Top-level SoMi/ root under externalFilesDir. Created on first
     * access; safe across cold starts. If externalFilesDir is null
     * (rare — exotic OEM/work-profile config), falls back to
     * filesDir/SoMi so the app still launches; the user just doesn't
     * get the visibility win.
     */
    fun root(context: Context): File {
        val external = context.getExternalFilesDir(null) ?: context.filesDir
        return File(external, "SoMi").apply { mkdirs() }
    }

    fun llm(context: Context): File = File(root(context), "llm").apply { mkdirs() }

    fun memory(context: Context): File = File(root(context), "memory").apply { mkdirs() }

    fun soul(context: Context): File = File(root(context), "soul").apply { mkdirs() }

    fun soulBackups(context: Context): File = File(soul(context), "backups").apply { mkdirs() }

    fun db(context: Context): File = File(root(context), "db").apply { mkdirs() }

    fun settings(context: Context): File = File(root(context), "settings").apply { mkdirs() }

    /**
     * Sentinel file that marks the v0.15.0 migration as complete.
     * Existence == migration ran successfully. Stable name (NOT
     * version-tied) so future migrations can reuse the framework
     * with their own sentinels (`.migrated-storage-v2`, etc.).
     */
    fun migrationSentinel(context: Context): File = File(root(context), ".migrated-storage-v1")

    /**
     * Internal-only paths (NOT moved to SoMi/). Kept here for
     * symmetry so every read-of-a-path goes through StorageRoots.
     */
    fun embedder(context: Context): File =
        File(File(context.filesDir, "models"), "embeddings").apply { mkdirs() }

    fun objectBoxParent(context: Context): File =
        File(context.filesDir, "objectbox").apply { mkdirs() }

    fun objectBoxStore(context: Context): File =
        File(objectBoxParent(context), "so-mi-rag").apply { mkdirs() }
}
