package com.example.pulsetransit.data

import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults

/**
 * The App Group the app shares with its widget extension (both targets' entitlements name it), so
 * the home-screen widgets read the same settings and offline cache as the board.
 */
const val APP_GROUP_ID = "group.com.homieapps.pulsetransit"

/** Set in the group's defaults once the app's own settings were copied there. */
internal const val MIGRATED_TO_APP_GROUP = "migrated_to_app_group"

/**
 * The group's container directory, or null when the build has no App Group entitlement (then the
 * app keeps its own, unshared storage, and widgets show nothing).
 */
internal fun appGroupContainerPath(): String? = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID)?.path

/** Whether this process is an app extension (the widgets) rather than the app. */
internal fun isAppExtension(): Boolean = NSBundle.mainBundle.bundlePath.endsWith(".appex")

/**
 * The defaults the app's settings live in: the App Group's, shared with the widgets, when the build
 * has the group; else the app's own. Settings written by builds from before widgets are copied into
 * the group once, by the app (an extension cannot read the app's own defaults).
 */
internal fun appSettingsDefaults(): NSUserDefaults {
    if (appGroupContainerPath() == null) return NSUserDefaults.standardUserDefaults
    val group = NSUserDefaults(suiteName = APP_GROUP_ID)
    if (!isAppExtension()) {
        val bundleId = NSBundle.mainBundle.bundleIdentifier
        if (bundleId != null) migrateToAppGroup(NSUserDefaults.standardUserDefaults.persistentDomainForName(bundleId), group)
    }
    return group
}

/**
 * Copies [own] (the app's own defaults, as a dictionary) into [group] once. A key the group already
 * has is kept, so nothing written there since is overwritten.
 */
internal fun migrateToAppGroup(
    own: Map<Any?, *>?,
    group: NSUserDefaults,
) {
    if (group.boolForKey(MIGRATED_TO_APP_GROUP)) return
    own?.forEach { (key, value) ->
        val name = key as? String ?: return@forEach
        if (group.objectForKey(name) == null) group.setObject(value, forKey = name)
    }
    group.setBool(true, forKey = MIGRATED_TO_APP_GROUP)
}
