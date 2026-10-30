package com.venue.reservation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class User(
	@Column(nullable = false)
	var name: String,

	@Column(nullable = false, unique = true)
	var email: String,

	@Column(length = 50)
	var phone: String? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	val createdAt: Instant = Instant.now(),

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null,
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is User) return false
		return id != null && id == other.id
	}

	override fun hashCode(): Int = User::class.hashCode()

	override fun toString(): String = "User(id=$id, name=$name, email=$email)"
}
