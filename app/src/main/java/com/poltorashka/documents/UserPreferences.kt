package com.poltorashka.documents

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferences private constructor(context: Context) {
    companion object {
        @Volatile private var instance: UserPreferences? = null

        fun getInstance(context: Context): UserPreferences = instance ?: synchronized(this) {
            instance ?: UserPreferences(context.applicationContext).also { instance = it }
        }
    }

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    // Ordinary UI settings do not need to open Android Keystore or the PIN keyset.
    private val pinPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_pin_prefs",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val userNameState = MutableStateFlow(prefs.getString("USER_NAME", "") ?: "")
    val userNameFlow = userNameState.asStateFlow()
    private val animationsEnabledState = MutableStateFlow(prefs.getBoolean("animations_enabled", true))
    val animationsEnabledFlow = animationsEnabledState.asStateFlow()
    private val themeModeState = MutableStateFlow(prefs.getInt("theme_mode", 0))
    val themeModeFlow = themeModeState.asStateFlow()
    private val dynamicColorState = MutableStateFlow(prefs.getBoolean("use_dynamic_color", true))
    val dynamicColorFlow = dynamicColorState.asStateFlow()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "USER_NAME") userNameState.value = userName
        if (key == null || key == "animations_enabled") animationsEnabledState.value = animationsEnabled
        if (key == null || key == "theme_mode") themeModeState.value = themeMode
        if (key == null || key == "use_dynamic_color") dynamicColorState.value = useDynamicColor
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        // Move the PIN from older app versions out of ordinary SharedPreferences.
        prefs.getString("APP_PIN", null)?.let { legacyPin ->
            if (legacyPin.length == 4 && legacyPin.all { it in '0'..'9' }) {
                setPin(legacyPin)
            } else {
                prefs.edit().putBoolean("PIN_ENABLED", false).putBoolean("BIOMETRIC_ENABLED", false).apply()
            }
            prefs.edit().remove("APP_PIN").commit()
        }
    }

    var userName: String
        get() = prefs.getString("USER_NAME", "") ?: ""
        set(value) = prefs.edit().putString("USER_NAME", value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("ONBOARDING_COMPLETED", false)
        set(value) = prefs.edit().putBoolean("ONBOARDING_COMPLETED", value).apply()

    var isPinEnabled: Boolean
        get() = prefs.getBoolean("PIN_ENABLED", false) && pinPrefs.contains("PIN_HASH")
        set(value) = prefs.edit().putBoolean("PIN_ENABLED", value).apply()

    var isBiometricEnabled: Boolean
        get() = isPinEnabled && prefs.getBoolean("BIOMETRIC_ENABLED", false)
        set(value) = prefs.edit().putBoolean("BIOMETRIC_ENABLED", value).apply()

    fun setPin(pin: String) {
        require(pin.length == 4 && pin.all(Char::isDigit))
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derivePin(pin, salt)
        check(pinPrefs.edit()
            .putString("PIN_SALT", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("PIN_HASH", Base64.encodeToString(hash, Base64.NO_WRAP))
            .commit())
        prefs.edit().remove("APP_PIN").apply()
    }

    fun clearPin() {
        check(pinPrefs.edit().remove("PIN_SALT").remove("PIN_HASH").commit())
        prefs.edit().remove("APP_PIN").apply()
    }

    fun verifyPin(candidate: String): Boolean {
        if (candidate.length != 4) return false
        val salt = pinPrefs.getString("PIN_SALT", null) ?: return false
        val expected = pinPrefs.getString("PIN_HASH", null) ?: return false
        return try {
            MessageDigest.isEqual(
                Base64.decode(expected, Base64.DEFAULT),
                derivePin(candidate, Base64.decode(salt, Base64.DEFAULT))
            )
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derivePin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // 0 = System, 1 = Light, 2 = Dark
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(value) = prefs.edit().putInt("theme_mode", value).apply()

    var useDynamicColor: Boolean
        get() = prefs.getBoolean("use_dynamic_color", true)
        set(value) = prefs.edit().putBoolean("use_dynamic_color", value).apply()

    var backgroundTimestamp: Long
        get() = prefs.getLong("background_timestamp", 0L)
        set(value) = prefs.edit().putLong("background_timestamp", value).apply()

    var animationsEnabled: Boolean
        get() = prefs.getBoolean("animations_enabled", true)
        set(value) = prefs.edit().putBoolean("animations_enabled", value).apply()
}
