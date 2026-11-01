package com.venue.auth

import com.venue.common.AppProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant

/** Issues the bearer tokens clients send back. The subject is the user id. */
@Service
class TokenService(
	private val encoder: JwtEncoder,
	private val properties: AppProperties,
) {
	fun issue(userId: Long): String {
		val now = Instant.now()
		val claims = JwtClaimsSet.builder()
			.subject(userId.toString())
			.issuedAt(now)
			.expiresAt(now.plus(properties.jwt.ttl))
			.build()
		val header = JwsHeader.with(MacAlgorithm.HS256).build()
		return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
	}
}
