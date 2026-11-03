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

	@Test
	fun `a paused venue leaves search and takes no bookings until resumed`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host)
		val booking = slot(daysAhead = 3, hour = 18)

		assertEquals(403, post("/api/v1/venues/$venueId/pause", "", guest).status)
		val paused = post("/api/v1/venues/$venueId/pause", "", host)
		assertTrue(paused.field("paused").asBoolean())

		assertEquals(0, get("/api/v1/venues/search?city=New York").field("totalItems").asInt())
		assertTrue(get("/api/v1/venues/$venueId").field("paused").asBoolean(), "still visible by id")
		val date = booking.start.atZone(java.time.ZoneId.of(NEW_YORK)).toLocalDate()
		val slots = get("/api/v1/venues/$venueId/availability?date=$date").field("slots")
		assertTrue(slots.none { it.get("available").asBoolean() })

		val refused = reserve(guest, venueId, booking)
		assertEquals(409, refused.status)
		assertEquals("VENUE_NOT_ACCEPTING_BOOKINGS", refused.field("code").asString())

		put("/api/v1/venues/$venueId", venueJson(name = "Renamed"), host)
		assertTrue(get("/api/v1/venues/$venueId").field("paused").asBoolean(), "PUT doesn't change paused")

		post("/api/v1/venues/$venueId/resume", "", host)
		assertEquals(201, reserve(guest, venueId, booking).status)
	}

	@Test
	fun `a venue with upcoming bookings can't be deleted, one with only past bookings can`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host)
		reserve(guest, venueId, slot(daysAhead = 3, hour = 18, hours = 2))

		val blocked = delete("/api/v1/venues/$venueId", host)
		assertEquals(409, blocked.status)
		assertEquals("VENUE_HAS_BOOKINGS", blocked.field("code").asString())

		// Once the booking has ended, only history is left.
		clock.advance(java.time.Duration.ofDays(4))
		assertEquals(204, delete("/api/v1/venues/$venueId", host).status)
	}

	@Test
	fun `an expired hold doesn't block deleting`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host)
		reserve(guest, venueId, slot(daysAhead = 3, hour = 18))

		clock.advance(java.time.Duration.ofMinutes(11))
		assertEquals(204, delete("/api/v1/venues/$venueId", host).status)
	}

	@Test
	fun `availability follows daylight saving in the venue's zone`() {
		val venueId = createVenue(signUp("Host"))
		fun slots(date: String) = get("/api/v1/venues/$venueId/availability?date=$date").field("slots")

		// New York moves its clocks forward on March 14, 2027: 06:00 is 11:00 UTC the
		// day before (EST, UTC-5) and 10:00 UTC that day (EDT, UTC-4).
		val before = slots("2027-03-13")
		val after = slots("2027-03-14")
		assertEquals("2027-03-13T11:00:00Z", before.get(0).get("startTime").asString())
		assertEquals("2027-03-14T10:00:00Z", after.get(0).get("startTime").asString())
		assertEquals("06:00", after.get(0).get("localTime").asString())
		assertEquals(18, after.size(), "still one slot per hour from 06:00 to 23:00")

		// And back on November 7, 2027.
		assertEquals("2027-11-07T11:00:00Z", slots("2027-11-07").get(0).get("startTime").asString())
	}
}
