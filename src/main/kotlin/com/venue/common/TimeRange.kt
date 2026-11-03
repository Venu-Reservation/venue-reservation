package com.venue.common

import java.time.Instant

/**
 * A half-open UTC time range [start, end): it includes start but not end, so
 * back-to-back ranges (18:00-19:00 and 19:00-20:00) don't overlap. The same
 * rule as Postgres's tstzrange '[)' and its && operator.
 */
data class TimeRange(val start: Instant, val end: Instant) {
	fun overlaps(other: TimeRange) = start < other.end && other.start < end
}
