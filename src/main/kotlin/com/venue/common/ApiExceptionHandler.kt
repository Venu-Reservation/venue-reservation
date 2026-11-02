package com.venue.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/** Turns every error into JSON { code, message } with the right status. */
@RestControllerAdvice
class ApiExceptionHandler {
	private val log = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(ApiException::class)
	fun handleApi(e: ApiException) = error(e.status, e.code, e.message ?: "")

	@ExceptionHandler(TooManyRequestsException::class)
	fun handleTooManyRequests(e: TooManyRequestsException): ResponseEntity<ErrorResponse> =
		ResponseEntity.status(e.status)
			.header(HttpHeaders.RETRY_AFTER, e.retryAfterSeconds.toString())
			.body(ErrorResponse(e.code, e.message ?: ""))

	@ExceptionHandler(HttpMessageNotReadableException::class)
	fun handleUnreadable(e: HttpMessageNotReadableException) =
		error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request body is missing or isn't valid JSON.")

	@ExceptionHandler(MissingServletRequestParameterException::class)
	fun handleMissingParameter(e: MissingServletRequestParameterException) =
		error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Missing required parameter '${e.parameterName}'.")

	@ExceptionHandler(MethodArgumentTypeMismatchException::class)
	fun handleTypeMismatch(e: MethodArgumentTypeMismatchException) =
		error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Parameter '${e.name}' has an invalid value.")

	@ExceptionHandler(NoResourceFoundException::class)
	fun handleNoResource(e: NoResourceFoundException) = error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found.")

	@ExceptionHandler(HttpRequestMethodNotSupportedException::class)
	fun handleMethod(e: HttpRequestMethodNotSupportedException) =
		error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This endpoint doesn't support ${e.method}.")

	@ExceptionHandler(Exception::class)
	fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
		log.error("Unhandled error", e)
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong. Please try again.")
	}

	private fun error(status: HttpStatus, code: String, message: String) =
		ResponseEntity.status(status).body(ErrorResponse(code, message))
}
