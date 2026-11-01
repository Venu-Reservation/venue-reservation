package com.venue.payments

import com.venue.common.ForbiddenException
import com.venue.common.security.userId
import com.venue.reservations.ReservationResponse
import com.venue.reservations.ReservationService
import org.springframework.context.annotation.Profile
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Development only: pays for your own reservation as if the provider had sent
 * a successful payment event, so clients can finish a booking without a real
 * payment provider. Goes through the same path as the webhook, so an expired
 * hold still isn't confirmed.
 */
@Profile("dev")
@RestController
class DevPaymentController(
	private val payments: PaymentService,
	private val reservations: ReservationService,
) {
	@PostMapping("/api/v1/reservations/{id}/pay")
	fun pay(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long): ReservationResponse {
		val details = reservations.getPaymentDetails(id)
		if (details.renterId != jwt.userId()) throw ForbiddenException("Only the guest who booked can pay.")
		payments.handle(PaymentEvent("dev-${UUID.randomUUID()}", id, details.totalPrice, PaymentEventStatus.SUCCEEDED))
		return reservations.get(jwt.userId(), id)
	}
}
