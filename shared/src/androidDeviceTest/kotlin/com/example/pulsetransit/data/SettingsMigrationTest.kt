package com.example.pulsetransit.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.russhwolf.settings.SharedPreferencesSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings written by builds that used `putStringSet` must survive the move to JSON strings. This
 * needs real SharedPreferences, which is why it is an instrumented test.
 */
@RunWith(AndroidJUnit4::class)
class SettingsMigrationTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("settings_migration_test", Context.MODE_PRIVATE)
        prefs.edit(commit = true) { clear() }
    }

    @After
    fun tearDown() {
        prefs.edit(commit = true) { clear() }
    }

    @Test
    fun legacyStringSetsAreReadableAfterMigration() {
        prefs.edit(commit = true) {
            putStringSet("filtered_lines", setOf("L1", "31"))
            putStringSet("quays_NSR:StopPlace:1", setOf("NSR:Quay:7"))
            putString("theme_mode", "DARK")
        }

        migrateLegacyStringSets(prefs)
        val repo = SettingsRepositoryImpl(SharedPreferencesSettings(prefs))

        assertEquals(setOf("L1", "31"), repo.getFilteredLines())
        assertEquals(setOf("NSR:Quay:7"), repo.getSelectedQuays("NSR:StopPlace:1"))
        assertEquals("unrelated settings are left alone", ThemeMode.DARK, repo.getThemeMode())
        assertFalse("the legacy set is removed", prefs.contains("filtered_lines"))
        assertFalse(prefs.contains("quays_NSR:StopPlace:1"))
    }

    @Test
    fun migratingTwiceChangesNothing() {
        prefs.edit(commit = true) { putStringSet("filtered_lines", setOf("L1")) }

        migrateLegacyStringSets(prefs)
        migrateLegacyStringSets(prefs)

        assertEquals(setOf("L1"), SettingsRepositoryImpl(SharedPreferencesSettings(prefs)).getFilteredLines())
    }
}
