package com.venue.reservations

import com.venue.common.TimeRange
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Reservations are read and written with SQL rather than JPA, because the
 * period is a Postgres tstzrange. Ranges are always half-open, '[)'.
 *
 * Queries never call Postgres's now(). The service passes [now] from the app's
 * Clock, so Java and SQL agree on the time and tests can move it.
 */
@Repository
class ReservationRepository(private val jdbc: JdbcTemplate) {

	fun venuesBookedDuring(range: TimeRange, now: Instant): Set<Long> = jdbc.query(
		"""
		select distinct venue_id from reservations
		where period && tstzrange(?, ?, '[)') and $BLOCKING
		""",
		{ rs, _ -> rs.getLong("venue_id") },
		range.start.toDb(),
		range.end.toDb(),
		now.toDb(),
	).toSet()

	fun bookedPeriods(venueId: Long, range: TimeRange, now: Instant): List<TimeRange> = jdbc.query(
		"""
		select lower(period) as start_time, upper(period) as end_time from reservations
		where venue_id = ? and period && tstzrange(?, ?, '[)') and $BLOCKING
		order by lower(period)
		""",
		{ rs, _ -> TimeRange(rs.instant("start_time"), rs.instant("end_time")) },
		venueId,
		range.start.toDb(),
		range.end.toDb(),
		now.toDb(),
	)

	fun hasBlockingEndingAfter(venueId: Long, now: Instant): Boolean = jdbc.queryForObject(
		"select exists (select 1 from reservations where venue_id = ? and upper(period) > ? and $BLOCKING)",
		Boolean::class.java,
		venueId,
		now.toDb(),
		now.toDb(),
	)!!

	/**
	 * Inserts a PENDING hold. Throws if it overlaps another active reservation
	 * (SQLState 23P01 from the exclusion constraint).
	 */
	fun insertHold(venueId: Long, renterId: Long, range: TimeRange, totalPrice: BigDecimal, expiresAt: Instant): Long =
		jdbc.queryForObject(
			"""
			insert into reservations (venue_id, renter_id, period, total_price, status, expires_at)
			values (?, ?, tstzrange(?, ?, '[)'), ?, 'PENDING', ?)
			returning id
			""",
			Long::class.java,
			venueId,
			renterId,
			range.start.toDb(),
			range.end.toDb(),
			totalPrice,
			expiresAt.toDb(),
		)!!

	fun find(id: Long): ReservationRow? = jdbc.query("$SELECT where id = ?", ROW, id).firstOrNull()

	/** Like [find], but locks the row until the transaction ends. */
	fun findForUpdate(id: Long): ReservationRow? = jdbc.query("$SELECT where id = ? for update", ROW, id).firstOrNull()

	fun findByRenter(renterId: Long, period: ListPeriod?, now: Instant): List<ReservationRow> {
		val filter = filter(period, now)
		return jdbc.query("$SELECT where renter_id = ? ${filter.sql}", ROW, renterId, *filter.args)
	}

	fun findByVenues(venueIds: Collection<Long>, period: ListPeriod?, now: Instant): List<ReservationRow> {
		if (venueIds.isEmpty()) return emptyList()
		val placeholders = venueIds.joinToString(",") { "?" }
		val filter = filter(period, now)
		return jdbc.query("$SELECT where venue_id in ($placeholders) ${filter.sql}", ROW, *venueIds.toTypedArray(), *filter.args)
	}

	/** Moves a reservation out of PENDING; only PENDING ones keep an expiry time. */
	fun updateStatus(id: Long, status: ReservationStatus) {
		require(status != ReservationStatus.PENDING) { "Reservations never go back to PENDING" }
		jdbc.update("update reservations set status = ?, expires_at = null where id = ?", status.name, id)
	}

	/** Marks holds that lapsed by [now] EXPIRED, for one venue or (with null) all of them. */
	fun expireStaleHolds(venueId: Long?, now: Instant): Int =
		if (venueId == null) {
			jdbc.update("update reservations set status = 'EXPIRED' where status = 'PENDING' and expires_at <= ?", now.toDb())
		} else {
			jdbc.update(
				"update reservations set status = 'EXPIRED' where venue_id = ? and status = 'PENDING' and expires_at <= ?",
				venueId,
				now.toDb(),
			)
		}

	/** The `when=` filter and ordering, with the values for its placeholders. */
	private fun filter(period: ListPeriod?, now: Instant): Filter = when (period) {
		ListPeriod.UPCOMING -> Filter("and upper(period) > ? and $BLOCKING order by lower(period)", now.toDb(), now.toDb())
		ListPeriod.PAST -> Filter("and not (upper(period) > ? and $BLOCKING) order by lower(period) desc", now.toDb(), now.toDb())
		null -> Filter("order by lower(period) desc")
	}

	private class Filter(val sql: String, vararg val args: Any)

	companion object {
		/** SQL condition for reservations that make a venue unavailable. Its one placeholder is now. */
		const val BLOCKING = "(status = 'CONFIRMED' or (status = 'PENDING' and expires_at > ?))"

		private const val SELECT = """
			select id, venue_id, renter_id, lower(period) as start_time, upper(period) as end_time,
				total_price, status, expires_at, created_at
			from reservations
		"""

		private val ROW = RowMapper { rs, _ ->
			ReservationRow(
				id = rs.getLong("id"),
				venueId = rs.getLong("venue_id"),
				renterId = rs.getLong("renter_id"),
				period = TimeRange(rs.instant("start_time"), rs.instant("end_time")),
				totalPrice = rs.getBigDecimal("total_price"),
				status = ReservationStatus.valueOf(rs.getString("status")),
				expiresAt = rs.instantOrNull("expires_at"),
				createdAt = rs.instant("created_at"),
			)
		}
	}
}

data class ReservationRow(
	val id: Long,
	val venueId: Long,
	val renterId: Long,
	val period: TimeRange,
	val totalPrice: BigDecimal,
	val status: ReservationStatus,
	val expiresAt: Instant?,
	val createdAt: Instant,
) {
	/** PENDING past its expiry counts as EXPIRED even before the job has updated the row. */
	fun effectiveStatus(now: Instant): ReservationStatus =
		if (status == ReservationStatus.PENDING && expiresAt != null && expiresAt <= now) ReservationStatus.EXPIRED else status
}

internal fun Instant.toDb(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

internal fun ResultSet.instant(column: String): Instant = getObject(column, OffsetDateTime::class.java).toInstant()

internal fun ResultSet.instantOrNull(column: String): Instant? =
	getObject(column, OffsetDateTime::class.java)?.toInstant()
