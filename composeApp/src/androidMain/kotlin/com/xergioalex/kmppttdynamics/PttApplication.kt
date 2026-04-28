package com.xergioalex.kmppttdynamics

import android.app.Application
import com.russhwolf.settings.SharedPreferencesSettings
import com.xergioalex.kmppttdynamics.settings.AppSettings

/**
 * Process-scoped holder for [AppContainer].
 *
 * Hoisting the container into [Application] means every `MainActivity`
 * recreation (rotation, theme change, Compose hot reload, language
 * switch, …) re-uses the same container — and therefore the same
 * Supabase websocket and the same presence tracker. Without this, every
 * activity recreation spawned a brand-new `GlobalPresenceTracker` that
 * tracked itself as a new presence on the lobby channel, which is what
 * pushed the "X online in the app" badge from the real value (e.g. 2)
 * up to 3 every time the device hot-reloaded.
 */
class PttApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val prefs = applicationContext.getSharedPreferences(
            "kmppttdynamics.settings",
            MODE_PRIVATE,
        )
        container = AppContainer(
            settings = AppSettings(SharedPreferencesSettings(prefs)),
        )
        container.startGlobalPresence()
    }
}
