package com.example.pulsetransit.data

import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The one-time copy of the app's own settings into the App Group the widgets read. */
class AppGroupMigrationTest {
    private val suite = "pulsetransit.test.appgroup"
    private val group = NSUserDefaults(suiteName = suite)

    @AfterTest
    fun clear() {
        NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suite)
    }

    @Test
    fun `settings from before widgets are copied into the group once`() {
        migrateToAppGroup(mapOf("app_mode" to "BOARD", "refresh_interval" to 60), group)

        assertEquals("BOARD", group.stringForKey("app_mode"))
        assertEquals(60L, group.integerForKey("refresh_interval"))
        assertTrue(group.boolForKey(MIGRATED_TO_APP_GROUP))
    }

    @Test
    fun `a value already in the group is kept`() {
        group.setObject("ON_SPOT", forKey = "app_mode")

        migrateToAppGroup(mapOf("app_mode" to "BOARD"), group)

        assertEquals("ON_SPOT", group.stringForKey("app_mode"))
    }

    @Test
    fun `a second run copies nothing`() {
        migrateToAppGroup(mapOf("app_mode" to "BOARD"), group)
        group.setObject("ON_SPOT", forKey = "app_mode")
        group.removeObjectForKey("theme")

        migrateToAppGroup(mapOf("app_mode" to "BOARD", "theme" to "DARK"), group)

        assertEquals("ON_SPOT", group.stringForKey("app_mode"))
        assertEquals(null, group.stringForKey("theme"))
    }

    @Test
    fun `nothing to copy still marks the group done`() {
        migrateToAppGroup(null, group)

        assertTrue(group.boolForKey(MIGRATED_TO_APP_GROUP))
    }
}
