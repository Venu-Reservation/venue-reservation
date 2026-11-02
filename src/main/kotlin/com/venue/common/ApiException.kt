package com.venue.common

import org.springframework.http.HttpStatus
import java.time.Duration

/**
 * An error the API returns as JSON { code, message }. The message is shown to
 * users as is, so keep it plain and specific.
 */
open class ApiException(val status: HttpStatus, val code: String, message: String) : RuntimeException(message)

class ValidationException(message: String) : ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)

class UnauthorizedException(code: String, message: String) : ApiException(HttpStatus.UNAUTHORIZED, code, message)

class ForbiddenException(message: String) : ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message)

class NotFoundException(message: String) : ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message)

class ConflictException(code: String, message: String) : ApiException(HttpStatus.CONFLICT, code, message)

/** 429: the caller must wait [retryAfter] before trying again; sent as the Retry-After header. */
class TooManyRequestsException(retryAfter: Duration) : ApiException(
	HttpStatus.TOO_MANY_REQUESTS,
	"TOO_MANY_REQUESTS",
	"Too many attempts. Wait a moment and try again.",
) {
	/** Whole seconds, rounded up, and at least 1. */
	val retryAfterSeconds: Long = maxOf(1, (retryAfter.toMillis() + 999) / 1000)
}

data class ErrorResponse(val code: String, val message: String)

/** Throws a VALIDATION_ERROR with [message] unless [condition] holds. */
fun validate(condition: Boolean, message: () -> String) {
	if (!condition) throw ValidationException(message())
}
