package com.venue

import com.venue.auth.AuthRateLimitFilter
import com.venue.auth.LoginThrottle
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Base for tests that call the API over HTTP (MockMvc) against a real Postgres
 * in a container. Every test starts with empty tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var jdbc: JdbcTemplate

	@Autowired
	lateinit var jsonMapper: JsonMapper

	@Autowired
	lateinit var rateLimit: AuthRateLimitFilter

	@Autowired
	lateinit var loginThrottle: LoginThrottle

	@BeforeEach
	fun cleanDatabase() {
		jdbc.execute("truncate payments, reservations, venue_images, venues, users restart identity cascade")
		// Every test calls signup and login from the same address, so start each with fresh limits.
		rateLimit.clear()
		loginThrottle.clear()
	}

	data class TestUser(val id: Long, val token: String)

	data class Response(val status: Int, val body: JsonNode?, val headers: Map<String, String> = emptyMap()) {
		fun field(name: String): JsonNode = body!!.get(name)
	}

	fun signUp(name: String, email: String = "${name.lowercase().replace(' ', '.')}@example.com"): TestUser {
		val response = post("/api/v1/auth/signup", """{"name":"$name","email":"$email","password":"longenough1"}""")
		check(response.status == 201) { "Signup failed: $response" }
		return TestUser(response.field("user").get("id").asLong(), response.field("token").asString())
	}

	fun createVenue(host: TestUser, name: String = "Test Hall", timezone: String = NEW_YORK, rate: String = "100"): Long {
		val body = """
			{"name":"$name","capacity":50,"sizeSqft":1000,"city":"New York","state":"NY",
			"timezone":"$timezone","hourlyRate":$rate,"imageUrls":[]}
		"""
		val response = post("/api/v1/venues", body, host)
		check(response.status == 201) { "Create venue failed: $response" }
		return response.field("id").asLong()
	}

	fun reserve(user: TestUser, venueId: Long, slot: Slot): Response = post(
		"/api/v1/reservations",
		"""{"venueId":$venueId,"startTime":"${slot.start}","endTime":"${slot.end}"}""",
		user,
	)

	fun get(path: String, user: TestUser? = null) = perform(MockMvcRequestBuilders.get(path), user)

	fun post(
		path: String,
		body: String,
		user: TestUser? = null,
		headers: Map<String, String> = emptyMap(),
		remoteAddr: String? = null,
	): Response {
		val request = MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(body)
		headers.forEach { (name, value) -> request.header(name, value) }
		remoteAddr?.let { address -> request.with { it.apply { this.remoteAddr = address } } }
		return perform(request, user)
	}

	fun put(path: String, body: String, user: TestUser? = null) =
		perform(MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON).content(body), user)

	fun delete(path: String, user: TestUser? = null) = perform(MockMvcRequestBuilders.delete(path), user)

	private fun perform(request: MockHttpServletRequestBuilder, user: TestUser?): Response {
		user?.let { request.header("Authorization", "Bearer ${it.token}") }
		return toResponse(mockMvc.perform(request).andReturn())
	}

	private fun toResponse(result: MvcResult): Response {
		val content = result.response.contentAsString
		val headers = result.response.headerNames.associateWith { result.response.getHeader(it).orEmpty() }
		return Response(result.response.status, if (content.isBlank()) null else jsonMapper.readTree(content), headers)
	}

	/** Pretends a reservation's 10-minute hold ran out. */
	fun expireHold(reservationId: Long) {
		jdbc.update("update reservations set expires_at = now() - interval '1 second' where id = ?", reservationId)
	}

	/** A booking window [start, end) in UTC, built from a local time at a venue. */
	data class Slot(val start: java.time.Instant, val end: java.time.Instant)

	/** [hours] hours from [hour]:00 venue time, [daysAhead] days from today. */
	fun slot(daysAhead: Long, hour: Int, hours: Long = 2, zone: String = NEW_YORK): Slot {
		val start = ZonedDateTime.of(LocalDate.now(ZoneId.of(zone)).plusDays(daysAhead), LocalTime.of(hour, 0), ZoneId.of(zone))
		return Slot(start.toInstant(), start.plusHours(hours).toInstant())
	}

	companion object {
		const val NEW_YORK = "America/New_York"
	}
}
