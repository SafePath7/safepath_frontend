package com.example.safepath_test1.session

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/** Account-scoped device preferences; these switches do not claim server-side AI activation. */
class DetectionSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("safe_path_detection", Context.MODE_PRIVATE)
    private val prefix = FirebaseAuth.getInstance().currentUser?.uid ?: "local"

    fun read() = DetectionSettings(
        voice = preferences.getBoolean("$prefix.voice", false),
        sound = preferences.getBoolean("$prefix.sound", false),
        motion = preferences.getBoolean("$prefix.motion", false),
        routeDeviation = preferences.getBoolean("$prefix.route", false),
        // TBD-6 has no agreed default/range. Do not invent a production timeout.
        responseTimeoutSeconds = null,
    )

    fun save(value: DetectionSettings) {
        preferences.edit()
            .putBoolean("$prefix.voice", value.voice)
            .putBoolean("$prefix.sound", value.sound)
            .putBoolean("$prefix.motion", value.motion)
            .putBoolean("$prefix.route", value.routeDeviation)
            .apply()
    }
}
