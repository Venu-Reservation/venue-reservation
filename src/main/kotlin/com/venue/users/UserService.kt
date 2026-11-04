package com.venue.users

import java.time.Instant

/** The users module's API for the rest of the app. */
interface UserService {
	/** Creates a user. Throws a 409 EMAIL_ALREADY_REGISTERED if the email is taken. */
	fun create(name: String, email: String, passwordHash: String): UserProfile

	/** The stored id and password hash for an email, for signing in. */
	fun findCredentials(email: String): Credentials?

	/** Throws a 404 if there's no such user. */
	fun getProfile(userId: Long): UserProfile

	/** Validates and saves the name (required) and phone (optional; blank clears it). */
	fun updateProfile(userId: Long, name: String?, phone: String?): UserProfile

	/** Display names for the given ids; missing ids are left out. */
	fun findNames(userIds: Collection<Long>): Map<Long, String>
}

data class Credentials(val userId: Long, val passwordHash: String)

/** What GET /users returns. hasListings is true when the user hosts any active venue. */
data class UserProfile(
	val id: Long,
	val name: String,
	val email: String,
	val phone: String?,
	val hasListings: Boolean,
	val createdAt: Instant,
)
