package com.venue.auth

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val auth: AuthService) {

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	fun signup(@RequestBody request: SignupRequest): AuthResponse =
		auth.signup(request.name, request.email, request.password)

	@PostMapping("/login")
	fun login(@RequestBody request: LoginRequest): AuthResponse = auth.login(request.email, request.password)
}

// Fields are nullable so a missing one becomes a clear validation message
// instead of a generic "invalid JSON" error.
data class SignupRequest(val name: String?, val email: String?, val password: String?)

data class LoginRequest(val email: String?, val password: String?)
