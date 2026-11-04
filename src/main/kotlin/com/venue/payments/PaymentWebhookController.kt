package com.venue.payments

import com.venue.common.AppProperties
import com.venue.common.UnauthorizedException
import com.venue.common.ValidationException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Receives events from the payment provider. There's no user token: the
 * provider signs the raw body with the shared webhook secret and sends
 * `X-Signature: <hex HMAC-SHA256>`.
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentWebhookController(
	private val payments: PaymentService,
	private val jsonMapper: JsonMapper,
	properties: AppProperties,
) {
	private val key = SecretKeySpec(properties.payments.webhookSecret.toByteArray(), "HmacSHA256")

	@PostMapping("/webhook")
	fun receive(
		@RequestHeader("X-Signature", required = false) signature: String?,
		// The raw bytes, because the signature covers exactly what was sent.
		@RequestBody body: ByteArray,
	): PaymentReceipt {
		if (signature == null || !signatureMatches(body, signature)) {
			throw UnauthorizedException("INVALID_SIGNATURE", "The webhook signature is missing or wrong.")
		}
		// Parsing JSON into an object is HTTP work; the rules are in PaymentService.
		val event = try {
			jsonMapper.readValue(body, PaymentEvent::class.java)
		} catch (e: JacksonException) {
			throw ValidationException("The event must have eventId, reservationId, amount and status.")
		}
		return payments.handle(event)
	}

	private fun signatureMatches(body: ByteArray, signature: String): Boolean {
		val expected = sign(body)
		// Constant-time comparison, so response timing doesn't leak the signature.
		return MessageDigest.isEqual(expected.toByteArray(), signature.trim().lowercase().toByteArray())
	}

	/** Hex HMAC-SHA256 of [body]. */
	private fun sign(body: ByteArray): String {
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(key)
		return mac.doFinal(body).joinToString("") { "%02x".format(it) }
	}
}
