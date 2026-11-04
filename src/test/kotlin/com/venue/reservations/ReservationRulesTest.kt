package com.venue.reservations

import com.venue.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals

/** The booking rules the spec requires tests for (docs/project.md, Backend rules). */
class ReservationRulesTest : IntegrationTest() {

	@Test
	fun `concurrent overlapping bookings give exactly one success and one 409`() {
		val host = signUp("Host")
		val guests = listOf(signUp("Guest One"), signUp("Guest Two"))
		val venueId = createVenue(host)
		val slot = slot(daysAhead = 3, hour = 18)

		// Both requests wait at the gate, then hit the database together.
		val gate = CountDownLatch(1)
		val pool = Executors.newFixedThreadPool(2)
		val results = guests.map { guest ->
			pool.submit(
				Callable {
					gate.await()
					reserve(guest, venueId, slot)
				},
			)
		}
		gate.countDown()
		val responses = results.map { it.get() }
		pool.shutdown()

		assertEquals(listOf(201, 409), responses.map { it.status }.sorted())
		assertEquals("RESERVATION_CONFLICT", responses.single { it.status == 409 }.field("code").asString())
	}

	@Test
	fun `back-to-back ranges both succeed`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host)

		val first = reserve(guest, venueId, slot(daysAhead = 3, hour = 14, hours = 2)) // 14:00-16:00
		val second = reserve(guest, venueId, slot(daysAhead = 3, hour = 16, hours = 2)) // 16:00-18:00

		assertEquals(201, first.status)
		assertEquals(201, second.status)
	}

	@Test
	fun `expired holds do not block search or booking`() {
		val host = signUp("Host")
		val firstGuest = signUp("First Guest")
		val secondGuest = signUp("Second Guest")
		val venueId = createVenue(host)
		val slot = slot(daysAhead = 3, hour = 18)

		val hold = reserve(firstGuest, venueId, slot)
		assertEquals(201, hold.status)
		assertEquals(0, searchCount(slot), "an active hold blocks the venue")

		clock.advance(Duration.ofMinutes(9))
		assertEquals(0, searchCount(slot), "the hold still blocks after 9 minutes")

		clock.advance(Duration.ofMinutes(2))
		assertEquals(1, searchCount(slot), "an expired hold doesn't block search")
		assertEquals(201, reserve(secondGuest, venueId, slot).status, "an expired hold doesn't block booking")
	}

	@Test
	fun `non-owners cannot edit, delete or cancel`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val stranger = signUp("Stranger")
		val venueId = createVenue(host)
		val reservationId = reserve(guest, venueId, slot(daysAhead = 3, hour = 18)).field("id").asLong()
		val venueBody = venueJson(name = "Taken Over")

		assertEquals(403, put("/api/v1/venues/$venueId", venueBody, stranger).status)
		assertEquals(403, delete("/api/v1/venues/$venueId", stranger).status)
		assertEquals(403, delete("/api/v1/reservations/$reservationId", stranger).status)
		assertEquals(403, delete("/api/v1/reservations/$reservationId", host).status, "the host can't cancel a guest's booking")

		assertEquals("Test Hall", get("/api/v1/venues/$venueId").field("name").asString())
		assertEquals("PENDING", get("/api/v1/reservations/$reservationId", guest).field("status").asString())
	}

	@Test
	fun `a booking needs a venue and a forward time range`() {
		val guest = signUp("Guest")
		val missing = post("/api/v1/reservations", """{"startTime":"2030-01-01T18:00:00Z"}""", guest)
		assertEquals(400, missing.status)
		assertEquals("venueId, startTime and endTime are required.", missing.field("message").asString())

		val backwards = post(
			"/api/v1/reservations",
			"""{"venueId":1,"startTime":"2030-01-01T20:00:00Z","endTime":"2030-01-01T18:00:00Z"}""",
			guest,
		)
		assertEquals("startTime must be before endTime.", backwards.field("message").asString())
	}

	@Test
	fun `booking rules are enforced in the venue's time zone`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host)

		fun messageFor(slot: Slot) = reserve(guest, venueId, slot).field("message").asString()

		assertEquals("Bookings start between 6:00 AM and 11:00 PM, venue time.", messageFor(slot(3, hour = 5)))
		assertEquals("Bookings last between 1 and 12 hours.", messageFor(slot(3, hour = 8, hours = 13)))
		val offTheHour = slot(3, hour = 18).let { Slot(it.start.plusSeconds(1800), it.end.plusSeconds(1800)) }
		assertEquals("Bookings start on the hour.", messageFor(offTheHour))
		assertEquals("Pick a time in the future.", messageFor(slot(daysAhead = -1, hour = 18)))
	}

	private fun searchCount(slot: Slot): Int =
		get("/api/v1/venues/search?city=New York&startTime=${slot.start}&endTime=${slot.end}")
			.field("totalItems").asInt()

	@Test
	fun `the guest can cancel, twice is fine, but not an expired or started booking`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host)
		val booking = reserve(guest, venueId, slot(daysAhead = 3, hour = 18)).field("id").asLong()
		val hold = reserve(guest, venueId, slot(daysAhead = 4, hour = 18)).field("id").asLong()
		val soon = reserve(guest, venueId, slot(daysAhead = 1, hour = 10))
		pay(soon)

		val cancelled = delete("/api/v1/reservations/$booking", guest)
		assertEquals(200, cancelled.status)
		assertEquals("CANCELLED", cancelled.field("status").asString())
		assertEquals("CANCELLED", delete("/api/v1/reservations/$booking", guest).field("status").asString(), "twice is fine")

		clock.advance(Duration.ofMinutes(11))
		val expired = delete("/api/v1/reservations/$hold", guest)
		assertEquals(409, expired.status)
		assertEquals("RESERVATION_NOT_CANCELLABLE", expired.field("code").asString())

		clock.advance(Duration.ofDays(2))
		val started = delete("/api/v1/reservations/${soon.field("id").asLong()}", guest)
		assertEquals(409, started.status)
		assertEquals("This booking has already started.", started.field("message").asString())
	}

	@Test
	fun `upcoming and past lists, and the host's view`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val other = signUp("Other")
		val venueId = createVenue(host)
		val later = reserve(guest, venueId, slot(daysAhead = 5, hour = 18))
		pay(later)
		val sooner = reserve(guest, venueId, slot(daysAhead = 3, hour = 18)) // still a pending hold
		val cancelled = reserve(guest, venueId, slot(daysAhead = 4, hour = 18))
		delete("/api/v1/reservations/${cancelled.field("id").asLong()}", guest)
		reserve(other, venueId, slot(daysAhead = 6, hour = 18))

		assertEquals(ids(sooner, later), listIds("/api/v1/reservations?when=upcoming", guest), "soonest first")
		assertEquals(ids(cancelled), listIds("/api/v1/reservations?when=past", guest))
		assertEquals(ids(later, cancelled, sooner), listIds("/api/v1/reservations", guest), "all, latest start first")
		assertEquals(4, listIds("/api/v1/reservations?as=host", host).size, "the host sees every guest's bookings")
		assertEquals(emptyList(), listIds("/api/v1/reservations?as=host", guest), "the guest hosts nothing")

		val bad = get("/api/v1/reservations?when=soon", guest)
		assertEquals(400, bad.status)
		assertEquals("when must be upcoming or past.", bad.field("message").asString())
	}

	@Test
	fun `a reservation is visible to its guest and the venue's host only`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val stranger = signUp("Stranger")
		val id = reserve(guest, createVenue(host), slot(daysAhead = 3, hour = 18)).field("id").asLong()

		assertEquals(200, get("/api/v1/reservations/$id", guest).status)
		assertEquals(200, get("/api/v1/reservations/$id", host).status)
		assertEquals(403, get("/api/v1/reservations/$id", stranger).status)
	}

	@Test
	fun `the expiry job marks lapsed holds EXPIRED in the database`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host)
		val lapsed = reserve(guest, venueId, slot(daysAhead = 3, hour = 18)).field("id").asLong()
		clock.advance(Duration.ofMinutes(11))
		val fresh = reserve(guest, venueId, slot(daysAhead = 4, hour = 18)).field("id").asLong()

		holdExpiryJob.expireStaleHolds()

		assertEquals("EXPIRED", storedStatus(lapsed))
		assertEquals("PENDING", storedStatus(fresh))
	}

	@Autowired
	lateinit var holdExpiryJob: HoldExpiryJob

	private fun ids(vararg reservations: Response) = reservations.map { it.field("id").asLong() }

	private fun listIds(path: String, user: TestUser): List<Long> {
		val items = get(path, user).body!!
		return (0 until items.size()).map { items.get(it).get("id").asLong() }
	}

	private fun storedStatus(id: Long) =
		jdbc.queryForObject("select status from reservations where id = ?", String::class.java, id)
}
