package com.venue.auth

import com.venue.IntegrationTest
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthAndProfileTest : IntegrationTest() {

	@Test
	fun `signup signs the user in and login works with any email casing`() {
		val signup = post("/api/v1/auth/signup", """{"name":"Alex","email":"Alex@Example.com","password":"longenough1"}""")
		assertEquals(201, signup.status)
		assertEquals("alex@example.com", signup.field("user").get("email").asString())
		assertFalse(signup.field("user").get("hasListings").asBoolean())

		val login = post("/api/v1/auth/login", """{"email":"ALEX@example.com","password":"longenough1"}""")
		assertEquals(200, login.status)
		assertTrue(login.field("token").asString().isNotBlank())
	}

	@Test
	fun `signup rejects a taken email and a short password`() {
		signUp("Alex", "alex@example.com")

		val taken = post("/api/v1/auth/signup", """{"name":"Other","email":"alex@example.com","password":"longenough1"}""")
		assertEquals(409, taken.status)
		assertEquals("EMAIL_ALREADY_REGISTERED", taken.field("code").asString())

		val short = post("/api/v1/auth/signup", """{"name":"Sam","email":"sam@example.com","password":"short"}""")
		assertEquals(400, short.status)
		assertEquals("Password must be at least 8 characters.", short.field("message").asString())
	}

	@Test
	fun `wrong password is 401 INVALID_CREDENTIALS`() {
		signUp("Alex", "alex@example.com")
		val response = post("/api/v1/auth/login", """{"email":"alex@example.com","password":"wrong-password"}""")
		assertEquals(401, response.status)
		assertEquals("INVALID_CREDENTIALS", response.field("code").asString())
	}

	@Test
	fun `profile comes from the token and hasListings follows the user's venues`() {
		val user = signUp("Alex")
		assertEquals(401, get("/api/v1/users").status)
		assertFalse(get("/api/v1/users", user).field("hasListings").asBoolean())

		createVenue(user)
		assertTrue(get("/api/v1/users", user).field("hasListings").asBoolean())

		val updated = put("/api/v1/users", """{"name":"Alex Morgan","phone":"555-0100"}""", user)
		assertEquals("Alex Morgan", updated.field("name").asString())
		assertEquals("555-0100", updated.field("phone").asString())
	}

	@Test
	fun `a token works for 7 days, then protected endpoints answer 401`() {
		val user = signUp("Alex")

		clock.advance(Duration.ofDays(6))
		assertEquals(200, get("/api/v1/users", user).status)

		clock.advance(Duration.ofDays(2))
		val expired = get("/api/v1/users", user)
		assertEquals(401, expired.status)
		assertEquals("UNAUTHORIZED", expired.field("code").asString())
	}

	@Test
	fun `a bad token is ignored on public endpoints but rejected elsewhere`() {
		val stale = TestUser(id = 0, token = "not-a-valid-token")
		signUp("Alex", "alex@example.com")

		assertEquals(200, get("/api/v1/venues/search?city=New York", stale).status)
		assertEquals(200, post("/api/v1/auth/login", """{"email":"alex@example.com","password":"longenough1"}""", stale).status)

		val profile = get("/api/v1/users", stale)
		assertEquals(401, profile.status)
		assertEquals("UNAUTHORIZED", profile.field("code").asString())
	}

	@Test
	fun `passwords can be long but not over 128 characters`() {
		val long = post("/api/v1/auth/signup", """{"name":"Alex","email":"alex@example.com","password":"${"a".repeat(128)}"}""")
		assertEquals(201, long.status)
		val login = post("/api/v1/auth/login", """{"email":"alex@example.com","password":"${"a".repeat(128)}"}""")
		assertEquals(200, login.status)

		val tooLong = post("/api/v1/auth/signup", """{"name":"Sam","email":"sam@example.com","password":"${"a".repeat(129)}"}""")
		assertEquals(400, tooLong.status)
		assertEquals("Password must be 128 characters or fewer.", tooLong.field("message").asString())
	}

	@Test
	fun `one IP gets 10 auth requests a minute, then 429 with Retry-After`() {
		repeat(10) { i ->
			val attempt = post("/api/v1/auth/login", """{"email":"guess$i@example.com","password":"wrong-password"}""", remoteAddr = "10.0.0.7")
			assertEquals(401, attempt.status)
		}

		val blocked = post("/api/v1/auth/login", """{"email":"guess@example.com","password":"wrong-password"}""", remoteAddr = "10.0.0.7")
		assertEquals(429, blocked.status)
		assertEquals("TOO_MANY_REQUESTS", blocked.field("code").asString())
		assertTrue(blocked.headers.getValue("Retry-After").toLong() in 1..60)

		// Another address has its own count.
		val other = post("/api/v1/auth/login", """{"email":"guess@example.com","password":"wrong-password"}""", remoteAddr = "10.0.0.8")
		assertEquals(401, other.status)
	}

	@Test
	fun `five wrong passwords make the next login for that email wait, even with the right password`() {
		signUp("Alex", "alex@example.com")
		repeat(5) {
			assertEquals(401, post("/api/v1/auth/login", """{"email":"alex@example.com","password":"wrong-password"}""").status)
		}

		val blocked = post("/api/v1/auth/login", """{"email":"alex@example.com","password":"longenough1"}""")
		assertEquals(429, blocked.status)
		assertEquals("1", blocked.headers["Retry-After"])

		// Other accounts aren't affected.
		signUp("Sam", "sam@example.com")
		assertEquals(200, post("/api/v1/auth/login", """{"email":"sam@example.com","password":"longenough1"}""").status)
	}
}
