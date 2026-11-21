package flasher

import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Checks against the private YBC soundpack; skipped when private/ is absent. */
class PackFileTest {
    private val ybc = File("../private/YBC-Flippin-Grooves-Circuit/Soundpack/Big City Flippn'Grooves For Mono Station/Big City Flippn'Grooves Pack For Mono Station.monostationpack")

    @Test
    fun ybcPack() = runTest {
        if (!ybc.exists()) return@runTest
        val files = readZip(ybc.readBytes())
        assertEquals(setOf("patches.syx", "sessions.syx"), files.keys)
        val sessions = files["sessions.syx"]!!
        val (start, stream) = Storage.decodeTransfer(sessions)
        assertEquals(Storage.SESSIONS_START, start)
        assertEquals(Storage.SESSIONS * Storage.SESSION_BYTES, stream.size)
        assertContentEquals(sessions, Storage.encodeTransfer(stream, start))

        // A backup built from these bytes: patches.syx matches the pack's (slot index in byte 7).
        val patchesSyx = files["patches.syx"]!!
        val dump = ByteArray(Storage.PATCHES_LENGTH)
        for (slot in 0 until 64) patchesSyx.copyInto(dump, slot * 512, slot * 136 + 9, slot * 136 + 135)
        assertContentEquals(patchesSyx, Storage.patchesSyx(dump))

        // Our ZIP opens with java.util.zip.
        val zip = Backup(dump, stream).pack(ZipDate(2026, 11, 20))
        val seen = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) { val e = z.nextEntry ?: break; seen[e.name] = z.readBytes() }
        }
        assertContentEquals(patchesSyx, seen["patches.syx"])
        assertContentEquals(sessions, seen["sessions.syx"])
        println("backup pack: ${zip.size} bytes")
    }
}
