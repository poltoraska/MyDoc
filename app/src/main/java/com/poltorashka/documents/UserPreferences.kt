package com.poltorashka.documents

import android.content.Context

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = prefs.getString("USER_NAME", "") ?: ""
        set(value) = prefs.edit().putString("USER_NAME", value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("ONBOARDING_COMPLETED", false)
        set(value) = prefs.edit().putBoolean("ONBOARDING_COMPLETED", value).apply()

    var isPinEnabled: Boolean
        get() = prefs.getBoolean("PIN_ENABLED", false)
        set(value) = prefs.edit().putBoolean("PIN_ENABLED", value).apply()

    var appPin: String
        get() = prefs.getString("APP_PIN", "") ?: ""
        set(value) = prefs.edit().putString("APP_PIN", value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean("BIOMETRIC_ENABLED", false)
        set(value) = prefs.edit().putBoolean("BIOMETRIC_ENABLED", value).apply()

    // 0 = Авто (Системная), 1 = Светлая, 2 = Тёмная
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(value) = prefs.edit().putInt("theme_mode", value).apply()

    // Использовать ли цвета от обоев (Material You)
    var useDynamicColor: Boolean
        get() = prefs.getBoolean("use_dynamic_color", true)
        set(value) = prefs.edit().putBoolean("use_dynamic_color", value).apply()
}