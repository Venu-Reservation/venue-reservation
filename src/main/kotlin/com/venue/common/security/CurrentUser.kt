package com.venue.common.security

import org.springframework.security.oauth2.jwt.Jwt

/** The calling user's id, taken from the token's subject. Never from a request body or query. */
fun Jwt.userId(): Long = requireNotNull(subject) { "Token has no subject" }.toLong()
