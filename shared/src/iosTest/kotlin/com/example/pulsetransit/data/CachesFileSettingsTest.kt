package com.example.pulsetransit.data

import platform.Foundation.NSTemporaryDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The file-backed store for where the device was, kept out of backups. */
class CachesFileSettingsTest {
    private val path = NSTemporaryDirectory() + "pulsetransit-device-state-test.json"
    private val settings = CachesFileSettings(path)

    @AfterTest
    fun clear() = settings.clear()

    @Test
    fun `values written are read back - also by a second instance`() {
        settings.putString("device_lat", "59.9111")
        settings.putDouble("lon", 10.7528)

        val again = CachesFileSettings(path)
        assertEquals("59.9111", again.getStringOrNull("device_lat"))
        assertEquals(10.7528, again.getDoubleOrNull("lon"))
        assertEquals(setOf("device_lat", "lon"), again.keys)
    }

    @Test
    fun `remove drops one key and clear drops them all`() {
        settings.putString("a", "1")
        settings.putBoolean("b", true)

        settings.remove("a")
        assertFalse(settings.hasKey("a"))
        assertTrue(settings.getBoolean("b", false))

        settings.clear()
        assertEquals(0, settings.size)
    }

    @Test
    fun `a missing file reads as empty`() {
        assertNull(CachesFileSettings(path + ".none").getStringOrNull("device_lat"))
    }
}
