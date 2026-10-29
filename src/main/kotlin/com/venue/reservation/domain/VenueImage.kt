package com.venue.reservation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "venue_images")
class VenueImage(
	@Column(name = "image_url", nullable = false, length = 500)
	var imageUrl: String,

	@Column(name = "display_order", nullable = false)
	var displayOrder: Int = 0,

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null,
) {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "venue_id", nullable = false)
	lateinit var venue: Venue

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is VenueImage) return false
		return id != null && id == other.id
	}

	override fun hashCode(): Int = VenueImage::class.hashCode()

	override fun toString(): String =
		"VenueImage(id=$id, imageUrl=$imageUrl, displayOrder=$displayOrder)"
}
