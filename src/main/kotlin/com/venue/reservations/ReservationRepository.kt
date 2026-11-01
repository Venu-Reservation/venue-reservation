package com.venue.reservations

import com.venue.common.TimeRange
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Reservations are read and written with SQL rather than JPA, because the
 * period is a Postgres tstzrange. Ranges are always half-open, '[)'.
 */
@Repository
class ReservationRepository(private val jdbc: JdbcTemplate) {

	fun venuesBookedDuring(range: TimeRange): Set<Long> = jdbc.query(
		"""
		select distinct venue_id from reservations
		where period && tstzrange(?, ?, '[)') and $BLOCKING
		""",
		{ rs, _ -> rs.getLong("venue_id") },
		range.start.toDb(),
		range.end.toDb(),
	).toSet()

	fun bookedPeriods(venueId: Long, range: TimeRange): List<TimeRange> = jdbc.query(
		"""
		select lower(period) as start_time, upper(period) as end_time from reservations
		where venue_id = ? and period && tstzrange(?, ?, '[)') and $BLOCKING
		order by lower(period)
		""",
		{ rs, _ -> TimeRange(rs.instant("start_time"), rs.instant("end_time")) },
		venueId,
		range.start.toDb(),
		range.end.toDb(),
	)

	companion object {
		/** SQL condition for reservations that make a venue unavailable. */
		const val BLOCKING = "(status = 'CONFIRMED' or (status = 'PENDING' and expires_at > now()))"
	}
}

internal fun Instant.toDb(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

internal fun ResultSet.instant(column: String): Instant = getObject(column, OffsetDateTime::class.java).toInstant()

internal fun ResultSet.instantOrNull(column: String): Instant? =
	getObject(column, OffsetDateTime::class.java)?.toInstant()
