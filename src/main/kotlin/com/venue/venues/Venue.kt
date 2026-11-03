package com.venue.venues

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "venues")
class Venue(
	// The user who listed the venue. A plain id rather than a User relation,
	// so the venues module doesn't depend on the users module's entity.
	@Column(name = "host_id", nullable = false, updatable = false)
	val hostId: Long,

	@Column(nullable = false)
	var name: String,

	@Column(nullable = false)
	var capacity: Int,

	@Column(name = "size_sqft")
	var sizeSqft: Int? = null,

	@Column(name = "address_line1", nullable = false)
	var addressLine1: String,

	@Column(name = "address_line2")
	var addressLine2: String? = null,

	// City, state and timezone always come from SupportedCity together.
	@Column(nullable = false, length = 100)
	var city: String,

	@Column(nullable = false, length = 50)
	var state: String,

	@Column(name = "postal_code", nullable = false, length = 20)
	var postalCode: String,

	// IANA zone ID, e.g. America/New_York
	@Column(nullable = false, length = 64)
	var timezone: String,

	// Both null until the address is geocoded (not built yet), or both set.
	@Column(precision = 9, scale = 6)
	var latitude: BigDecimal? = null,

	@Column(precision = 9, scale = 6)
	var longitude: BigDecimal? = null,

	@Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
	var hourlyRate: BigDecimal,

	@Column(name = "thumbnail_url", length = 500)
	var thumbnailUrl: String? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	val createdAt: Instant = Instant.now(),

	// Set when the venue is removed. Deleted venues are excluded from every read.
	@Column(name = "deleted_at")
	var deletedAt: Instant? = null,

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null,
) {
	@OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
	@OrderBy("displayOrder")
	val images: MutableList<VenueImage> = mutableListOf()

	fun addImage(image: VenueImage) {
		images.add(image)
		image.venue = this
	}

	/** Replaces all photos; the first URL becomes the thumbnail. */
	fun replaceImages(imageUrls: List<String>) {
		images.clear()
		imageUrls.forEachIndexed { order, url -> addImage(VenueImage(imageUrl = url, displayOrder = order)) }
		thumbnailUrl = imageUrls.firstOrNull()
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is Venue) return false
		return id != null && id == other.id
	}

	override fun hashCode(): Int = Venue::class.hashCode()

	override fun toString(): String = "Venue(id=$id, name=$name, city=$city, state=$state)"
}
