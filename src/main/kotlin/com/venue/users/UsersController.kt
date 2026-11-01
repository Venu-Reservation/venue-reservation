package com.venue.users

import com.venue.common.security.userId
import com.venue.common.validate
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
	fun update(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: UpdateProfileRequest): UserProfile {
		val name = request.name?.trim().orEmpty()
		val phone = request.phone?.trim()?.ifEmpty { null }
		validate(name.isNotEmpty()) { "Name is required." }
		validate(name.length <= 255) { "Name must be 255 characters or fewer." }
		validate(phone == null || phone.length <= 50) { "Phone must be 50 characters or fewer." }
		return users.updateProfile(jwt.userId(), name, phone)
	}
}

data class UpdateProfileRequest(val name: String?, val phone: String?)
