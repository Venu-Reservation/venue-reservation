package com.venue.common.security

import com.venue.common.AppProperties
import com.venue.common.writeError
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import java.time.Clock
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Stateless JWT auth. Clients send `Authorization: Bearer <token>`; the token's
 * subject is the user id, which is the only way the API learns who is calling.
 */
@Configuration
class SecurityConfig(properties: AppProperties, private val clock: Clock) {
	private val signingKey: SecretKey = run {
		val secret = properties.jwt.secret
		require(secret.length >= 32) { "app.jwt.secret must be at least 32 characters" }
		SecretKeySpec(secret.toByteArray(), "HmacSHA256")
	}

	/** Endpoints anyone can call, signed in or not. */
	private val publicEndpoints: RequestMatcher = PathPatternRequestMatcher.withDefaults().let { paths ->
		OrRequestMatcher(
			paths.matcher(HttpMethod.POST, "/api/v1/auth/**"),
			// Public browsing: search, venue details and availability.
			// GET /api/v1/venues (your own listings) still needs a token.
			paths.matcher(HttpMethod.GET, "/api/v1/venues/*"),
			paths.matcher(HttpMethod.GET, "/api/v1/venues/*/availability"),
			// The provider authenticates with a body signature, not a token.
			paths.matcher(HttpMethod.POST, "/api/v1/payments/webhook"),
			paths.matcher("/actuator/health"),
			paths.matcher("/error"),
		)
	}

	/**
	 * Reads the Bearer token, except on public endpoints, where it's ignored.
	 * Otherwise an expired token would turn browsing or signing in into a 401
	 * until the client noticed and dropped it.
	 */
	private val bearerTokens: BearerTokenResolver = DefaultBearerTokenResolver().let { default ->
		BearerTokenResolver { request -> if (publicEndpoints.matches(request)) null else default.resolve(request) }
	}

	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http {
			csrf { disable() }
			sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
			authorizeHttpRequests {
				authorize(publicEndpoints, permitAll)
				authorize(anyRequest, authenticated)
			}
			oauth2ResourceServer {
				bearerTokenResolver = bearerTokens
				jwt { }
				authenticationEntryPoint = unauthorized
			}
			exceptionHandling {
				authenticationEntryPoint = unauthorized
				accessDeniedHandler = forbidden
			}
		}
		return http.build()
	}

	@Bean
	fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder.withSecretKey(signingKey).algorithm(MacAlgorithm.HS256).build()

	/**
	 * Checks the signature, then `exp` and `nbf` against the same [Clock] that
	 * TokenService issues with (Spring's default validator reads the system time).
	 */
	@Bean
	fun jwtDecoder(): JwtDecoder =
		NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build().apply {
			setJwtValidator(JwtTimestampValidator().apply { setClock(clock) })
		}

	/**
	 * Argon2id with OWASP's recommended settings: 19 MiB of memory, 2 iterations,
	 * 1 thread. Hashes are stored with an `{argon2}` prefix, so a future algorithm
	 * can be added next to it and old hashes still verify.
	 */
	@Bean
	fun passwordEncoder(): PasswordEncoder = BoundedPasswordEncoder(
		DelegatingPasswordEncoder(
			"argon2",
			mapOf("argon2" to Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2)),
		),
		permits = Runtime.getRuntime().availableProcessors(),
	)

	// Security errors happen before controllers run, so they're written here
	// in the same { code, message } shape as ApiExceptionHandler.
	private val unauthorized = AuthenticationEntryPoint { _, response, _ ->
		response.writeError(HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Sign in to continue.")
	}

	private val forbidden = AccessDeniedHandler { _, response, _ ->
		response.writeError(HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "You don't have access to this.")
	}
}
