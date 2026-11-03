package com.venue.users

import com.venue.common.ConflictException
import com.venue.common.NotFoundException
import com.venue.common.isUniqueViolation
import com.venue.common.validate
import com.venue.venues.VenueService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DefaultUserService(
	private val users: UserRepository,
	private val venues: VenueService,
) : UserService {

	@Transactional
	override fun create(name: String, email: String, passwordHash: String): UserProfile {
		if (users.findByEmail(email) != null) throw emailTaken()
		val user = try {
			users.saveAndFlush(User(name = name, email = email, passwordHash = passwordHash))
		} catch (e: DataIntegrityViolationException) {
			// Another signup with the same email won the race.
			if (isUniqueViolation(e)) throw emailTaken() else throw e
		}
		return user.toProfile(hasListings = false)
	}

	@Transactional(readOnly = true)
	override fun findCredentials(email: String): Credentials? =
		users.findByEmail(email)?.let { Credentials(it.id!!, it.passwordHash) }

	@Transactional(readOnly = true)
	override fun getProfile(userId: Long): UserProfile {
		val user = find(userId)
		return user.toProfile(venues.hostsAnyVenue(userId))
	}

	@Transactional
	override fun updateProfile(userId: Long, name: String?, phone: String?): UserProfile {
		val cleanName = name?.trim().orEmpty()
		val cleanPhone = phone?.trim()?.ifEmpty { null }
		validate(cleanName.isNotEmpty()) { "Name is required." }
		validate(cleanName.length <= 255) { "Name must be 255 characters or fewer." }
		validate(cleanPhone == null || cleanPhone.length <= 50) { "Phone must be 50 characters or fewer." }

		val user = find(userId)
		user.name = cleanName
		user.phone = cleanPhone
		return user.toProfile(venues.hostsAnyVenue(userId))
	}

	@Transactional(readOnly = true)
	override fun findNames(userIds: Collection<Long>): Map<Long, String> =
		users.findAllById(userIds).associate { it.id!! to it.name }

	private fun find(userId: Long) = users.findById(userId).orElseThrow { NotFoundException("User not found.") }

	private fun emailTaken() =
		ConflictException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists.")

	private fun User.toProfile(hasListings: Boolean) =
		UserProfile(id!!, name, email, phone, hasListings, createdAt)
}
