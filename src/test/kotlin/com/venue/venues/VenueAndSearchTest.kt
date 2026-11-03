package com.venue.venues

import com.venue.IntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VenueAndSearchTest : IntegrationTest() {

	@Test
	fun `listing a venue needs a token, a supported city and an address`() {
		val host = signUp("Host")
		assertEquals(401, post("/api/v1/venues", venueJson()).status)

		val boston = post("/api/v1/venues", venueJson(city = "Boston"), host)
		assertEquals(400, boston.status)
		assertEquals("City must be one of: New York, Chicago.", boston.field("message").asString())

		val noAddress = post("/api/v1/venues", venueJson(addressLine1 = " "), host)
		assertEquals(400, noAddress.status)
		assertEquals("Street address and postal code are required.", noAddress.field("message").asString())
	}

	@Test
	fun `state and time zone come from the city, and coordinates start empty`() {
		val host = signUp("Host")
		val venue = post("/api/v1/venues", venueJson(city = " chicago ", addressLine1 = "1550 N Milwaukee Ave"), host)

		assertEquals(201, venue.status)
		assertEquals("Chicago", venue.field("city").asString())
		assertEquals("IL", venue.field("state").asString())
		assertEquals("America/Chicago", venue.field("timezone").asString())
		assertEquals("1550 N Milwaukee Ave", venue.field("addressLine1").asString())
		assertTrue(venue.field("addressLine2").isNull)
		assertTrue(venue.field("latitude").isNull)
	}

	@Test
	fun `changing the address clears coordinates, other edits keep them`() {
		val host = signUp("Host")
		val venueId = createVenue(host)
		jdbc.update("update venues set latitude = 40.7, longitude = -74.0 where id = ?", venueId)

		val renamed = put("/api/v1/venues/$venueId", venueJson(name = "Renamed"), host)
		assertEquals(40.7, renamed.field("latitude").asDouble())

		val moved = put("/api/v1/venues/$venueId", venueJson(addressLine1 = "2 Other St"), host)
		assertTrue(moved.field("latitude").isNull)
		assertTrue(moved.field("longitude").isNull)
	}

	@Test
	fun `the first photo becomes the thumbnail and GET venues lists only your own`() {
		val host = signUp("Host")
		val other = signUp("Other")
		val photos = """["https://example.com/a.jpg","https://example.com/b.jpg"]"""
		val venue = post("/api/v1/venues", venueJson(imageUrls = photos), host)
		assertEquals("https://example.com/a.jpg", venue.field("thumbnailUrl").asString())
		assertEquals(2, venue.field("images").size())
		createVenue(other)

		assertEquals(1, get("/api/v1/venues", host).body!!.size())
	}

	@Test
	fun `deleted venues disappear from reads and search`() {
		val host = signUp("Host")
		val venueId = createVenue(host)
		assertEquals(1, get("/api/v1/venues/search?city=new york").field("totalItems").asInt())
		assertEquals(204, delete("/api/v1/venues/$venueId", host).status)

		assertEquals(404, get("/api/v1/venues/$venueId").status)
		assertEquals(0, get("/api/v1/venues/search?city=new york").field("totalItems").asInt())
		assertEquals(0, get("/api/v1/venues", host).body!!.size())
	}

	@Test
	fun `search rejects half a time range and bad paging`() {
		val halfRange = get("/api/v1/venues/search?city=New York&startTime=2030-01-01T18:00:00Z")
		assertEquals(400, halfRange.status)
		assertEquals("Send both startTime and endTime, or neither.", halfRange.field("message").asString())

		val tooBig = get("/api/v1/venues/search?city=New York&size=101")
		assertEquals("size must be between 1 and 100.", tooBig.field("message").asString())

		assertEquals(0, get("/api/v1/venues/search?city=Boston").field("totalItems").asInt(), "unknown cities are just empty")
	}

	@Test
	fun `search pages results and availability marks booked hours`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueIds = (1..3).map { createVenue(host, name = "Hall $it") }

		val page = get("/api/v1/venues/search?city=New York&page=1&size=2")
		assertEquals(3, page.field("totalItems").asInt())
		assertEquals(2, page.field("totalPages").asInt())
		assertEquals(1, page.field("items").size())

		val booking = slot(daysAhead = 3, hour = 18, hours = 2)
		reserve(guest, venueIds[0], booking)
		val date = booking.start.atZone(java.time.ZoneId.of(NEW_YORK)).toLocalDate()
		val slots = get("/api/v1/venues/${venueIds[0]}/availability?date=$date").field("slots")
		val unavailable = slots.filter { !it.get("available").asBoolean() }.map { it.get("localTime").asString() }
		assertEquals(listOf("18:00", "19:00"), unavailable)
	}
}
