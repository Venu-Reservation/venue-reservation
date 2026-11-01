package com.venue.venues

import com.venue.IntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VenueAndSearchTest : IntegrationTest() {

	@Test
	fun `listing a venue needs a token and validates the time zone`() {
		val host = signUp("Host")
		val body = """
			{"name":"Hall","capacity":10,"city":"New York","state":"NY",
			"timezone":"+05:00","hourlyRate":50,"imageUrls":[]}
		"""
		assertEquals(401, post("/api/v1/venues", body).status)
		val response = post("/api/v1/venues", body, host)
		assertEquals(400, response.status)
		assertEquals("Time zone must be an IANA name like America/New_York.", response.field("message").asString())
	}

	@Test
	fun `the first photo becomes the thumbnail and GET venues lists only your own`() {
		val host = signUp("Host")
		val other = signUp("Other")
		val body = """
			{"name":"Hall","capacity":10,"city":"New York","state":"NY","timezone":"America/New_York",
			"hourlyRate":50.5,"imageUrls":["https://example.com/a.jpg","https://example.com/b.jpg"]}
		"""
		val venue = post("/api/v1/venues", body, host)
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
