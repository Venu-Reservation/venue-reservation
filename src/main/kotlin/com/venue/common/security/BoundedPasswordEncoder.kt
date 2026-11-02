package com.venue.common.security

import org.springframework.security.crypto.password.PasswordEncoder
import java.util.concurrent.Semaphore

/**
 * Runs at most [permits] password hashes at once; the rest wait their turn.
 * Each Argon2id hash briefly uses about 19 MiB, so this caps memory when many
 * logins arrive together. Hashing is CPU-bound, so running more at once than
 * there are cores wouldn't finish any sooner.
 */
class BoundedPasswordEncoder(private val delegate: PasswordEncoder, permits: Int) : PasswordEncoder {
	private val slots = Semaphore(permits, true)

	override fun encode(rawPassword: CharSequence?): String? = bounded { delegate.encode(rawPassword) }

	override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean =
		bounded { delegate.matches(rawPassword, encodedPassword) }

	override fun upgradeEncoding(encodedPassword: String?): Boolean = delegate.upgradeEncoding(encodedPassword)

	private fun <T> bounded(hash: () -> T): T {
		slots.acquire()
		try {
			return hash()
		} finally {
			slots.release()
		}
	}
}
