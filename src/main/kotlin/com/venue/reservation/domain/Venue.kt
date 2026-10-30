package com.venue.reservation.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "venues")
class Venue(
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "host_id", nullable = false)
	var host: User,

	@Column(nullable = false)
	var name: String,

	@Column(nullable = false)
	var capacity: Int,

	@Column(name = "size_sqft")
	var sizeSqft: Int? = null,

	@Column(nullable = false, length = 100)
	var city: String,

	@Column(nullable = false, length = 50)
	var state: String,

	// IANA zone ID, e.g. America/New_York
	@Column(nullable = false, length = 64)
	var timezone: String,

	@Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
	var hourlyRate: BigDecimal,

	@Column(name = "thumbnail_url", length = 500)
	var thumbnailUrl: String? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	val createdAt: Instant = Instant.now(),

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null,
) {
	@OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
	val images: MutableList<VenueImage> = mutableListOf()

	fun addImage(image: VenueImage) {
		images.add(image)
		image.venue = this
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is Venue) return false
		return id != null && id == other.id
	}

	override fun hashCode(): Int = Venue::class.hashCode()

	override fun toString(): String = "Venue(id=$id, name=$name, city=$city, state=$state)"
}
