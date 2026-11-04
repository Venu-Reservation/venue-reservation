package com.venue.users

import com.venue.common.security.userId
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The signed-in user's own profile. There's no id in the path: the token says who it is. */
@RestController
@RequestMapping("/api/v1/users")
class UsersController(private val users: UserService) {

	@GetMapping
	fun get(@AuthenticationPrincipal jwt: Jwt): UserProfile = users.getProfile(jwt.userId())

	@PutMapping
	fun update(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: UpdateProfileRequest): UserProfile =
		users.updateProfile(jwt.userId(), request.name, request.phone)
}

data class UpdateProfileRequest(val name: String?, val phone: String?)
