package com.xnvalabs.smarteyex.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import java.security.MessageDigest

/**
 * Feature #26 (User Authentication) — optional PIN lock. Explicitly
 * opt-in (this feature is marked non-MVP/"Tidak" in the feature map):
 * nothing is locked until the user sets a PIN from Privacy Control.
 * Stores only a SHA-256 hash of the PIN, never the PIN itself.
 *
 * [isUnlocked] is the actual gate MainActivity checks: true whenever no
 * PIN is set (nothing to unlock), or once [verify] succeeds. It resets
 * to false on every fresh process start — this is a "kalau kacamata
 * hilang, orang lain gak langsung dapet akses" gate, not a per-screen
 * timeout or lock-on-background.
 *
 * No "forgot PIN" recovery flow yet — losing the PIN currently means
 * reinstalling. Adding recovery is a deliberate later decision.
 */
object AuthRepository {
    private const val PREFS_NAME = "smarteyex_auth"
    private const val KEY_PIN_HASH = "pin_hash"

    private lateinit var prefs: SharedPreferences
    private var initialized = false

    var isUnlocked = mutableStateOf(false)
        private set

    /** Call once, from MainActivity.onCreate, before checking [isUnlocked]. */
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isUnlocked.value = !isPinSet()
        initialized = true
    }

    fun isPinSet(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, hash(pin)).apply()
        isUnlocked.value = true
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).apply()
        isUnlocked.value = true
    }

    fun verify(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return true
        val ok = stored == hash(pin)
        if (ok) isUnlocked.value = true
        return ok
    }

    private fun hash(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
