package com.tazzzo.app.data.catalog

import com.tazzzo.app.data.local.PersistentStore
import kotlin.random.Random

/**
 * A random, non-secret id for this installation, sent as
 * `X-Tazzzo-Installation-Id` so the backend can rate-limit per install.
 *
 * Generated once and persisted. It is NOT derived from the phone number, an
 * account id, a hardware id or an advertising id, and it is never logged
 * ([toString] is redacted).
 */
class InstallationId private constructor(val value: String) {
    override fun toString(): String = "InstallationId(***)"

    companion object {
        /** Backend charset/length: `[A-Za-z0-9-_.:]`, max 128. */
        private val FORMAT = Regex("^[A-Za-z0-9\\-_.:]{1,128}$")
        const val HEADER = "X-Tazzzo-Installation-Id"

        fun isValid(raw: String): Boolean = FORMAT.matches(raw)

        fun generate(random: Random = Random.Default): String =
            "tzi-" + random.nextBytes(16).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

        /** The persisted id, creating and saving one on first use (or if the stored value is malformed). */
        fun getOrCreate(store: PersistentStore, random: Random = Random.Default): InstallationId {
            val existing = store.installationId?.takeIf { isValid(it) }
            if (existing != null) return InstallationId(existing)
            val fresh = generate(random)
            store.installationId = fresh
            return InstallationId(fresh)
        }
    }
}
