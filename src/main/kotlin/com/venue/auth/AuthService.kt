package com.venue.auth

import com.venue.common.UnauthorizedException
import com.venue.common.validate
import com.venue.users.UserProfile
import com.venue.users.UserService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

const val MIN_PASSWORD_LENGTH = 8

// A cap keeps one request from making the server hash megabytes of input.
const val MAX_PASSWORD_LENGTH = 128
private val EMAIL_PATTERN = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

@Service
class AuthService(
	private val users: UserService,
	private val passwordEncoder: PasswordEncoder,
	private val tokens: TokenService,
	private val throttle: LoginThrottle,
) {
	// Compared against when an email doesn't exist, so a wrong email takes
	// as long as a wrong password and doesn't reveal which accounts exist.
	private val dummyHash = passwordEncoder.encode("not-a-real-password")

	fun signup(name: String?, email: String?, password: String?): AuthResponse {
		val cleanName = name?.trim().orEmpty()
		val cleanEmail = normalizeEmail(email)
		val cleanPassword = password.orEmpty()
		validate(cleanName.isNotEmpty() && cleanEmail.isNotEmpty() && cleanPassword.isNotEmpty()) {
			"Name, email and password are required."
		}
		validate(cleanName.length <= 255) { "Name must be 255 characters or fewer." }
		validate(cleanEmail.length <= 255 && EMAIL_PATTERN.matches(cleanEmail)) { "Enter a valid email address." }
		validate(cleanPassword.length >= MIN_PASSWORD_LENGTH) {
			"Password must be at least $MIN_PASSWORD_LENGTH characters."
		}
		validate(cleanPassword.length <= MAX_PASSWORD_LENGTH) {
			"Password must be $MAX_PASSWORD_LENGTH characters or fewer."
		}

		val user = users.create(cleanName, cleanEmail, passwordEncoder.encode(cleanPassword)!!)
		return session(user)
	}

	fun login(email: String?, password: String?): AuthResponse {
		val cleanEmail = normalizeEmail(email)
		val cleanPassword = password.orEmpty()
		validate(cleanEmail.isNotEmpty() && cleanPassword.isNotEmpty()) { "Email and password are required." }
		throttle.checkAllowed(cleanEmail)

		val credentials = users.findCredentials(cleanEmail)
		val matches = passwordEncoder.matches(cleanPassword, credentials?.passwordHash ?: dummyHash)
		if (credentials == null || !matches) {
			throttle.recordFailure(cleanEmail)
			throw UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect.")
		}
		throttle.recordSuccess(cleanEmail)
		return session(users.getProfile(credentials.userId))
	}

	private fun session(user: UserProfile) = AuthResponse(
		token = tokens.issue(user.id),
		user = AuthUser(user.id, user.name, user.email, user.hasListings),
	)

	private fun normalizeEmail(email: String?) = email?.trim()?.lowercase().orEmpty()
}

/** The agreed response for both signup and login. */
data class AuthResponse(val token: String, val user: AuthUser)

data class AuthUser(val id: Long, val name: String, val email: String, val hasListings: Boolean)
