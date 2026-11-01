package com.venue.common

import org.springframework.http.HttpStatus

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

data class ErrorResponse(val code: String, val message: String)

/** Throws a VALIDATION_ERROR with [message] unless [condition] holds. */
fun validate(condition: Boolean, message: () -> String) {
	if (!condition) throw ValidationException(message())
}
