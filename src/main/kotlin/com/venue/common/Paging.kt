package com.venue.common

import java.time.Instant

/** A page of results. [page] is zero-based. */
data class PageResponse<T>(
	val items: List<T>,
	val page: Int,
	val size: Int,
	val totalItems: Long,
	val totalPages: Int,
)

/** A half-open UTC time range [start, end). */
data class TimeRange(val start: Instant, val end: Instant) {
	fun overlaps(other: TimeRange) = start < other.end && other.start < end
}
