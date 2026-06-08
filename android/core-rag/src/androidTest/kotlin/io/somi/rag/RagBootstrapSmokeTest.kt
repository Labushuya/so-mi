package io.somi.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v0.14.0 M1 smoke test. Proves the io.objectbox plugin actually
 * generated MyObjectBox and that the BoxStore opens, accepts a
 * round-trip, and closes cleanly. < 2s, no LLM, no network.
 */
@RunWith(AndroidJUnit4::class)
class RagBootstrapSmokeTest {

    private lateinit var boxStore: BoxStore

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Test uses a separate DB name so it can't clobber the
        // production "so-mi-rag" DB on a developer device.
        BoxStore.deleteAllFiles(context, TEST_DB_NAME)
        boxStore = MyObjectBox.builder()
            .androidContext(context)
            .name(TEST_DB_NAME)
            .build()
    }

    @After
    fun teardown() {
        boxStore.close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        BoxStore.deleteAllFiles(context, TEST_DB_NAME)
    }

    @Test
    fun open_write_read_close() {
        val box = boxStore.boxFor(_Bootstrap::class.java)
        val id = box.put(_Bootstrap(sentinel = "hello"))
        val readBack = box.get(id)
        assertEquals("hello", readBack.sentinel)
    }

    private companion object {
        const val TEST_DB_NAME = "so-mi-rag-smoke-test"
    }
}
