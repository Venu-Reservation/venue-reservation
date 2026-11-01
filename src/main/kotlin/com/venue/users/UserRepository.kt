package com.venue.users

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
	/** [email] must already be lowercased; that's how emails are stored. */
	fun findByEmail(email: String): User?
}
