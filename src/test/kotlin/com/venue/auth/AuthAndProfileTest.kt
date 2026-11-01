package com.venue.auth

import com.venue.IntegrationTest
import org.junit.jupiter.api.Test
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
}
