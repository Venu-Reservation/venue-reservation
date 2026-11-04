package com.venue.common

/** A page of results. [page] is zero-based. */
data class PageResponse<T>(
	val items: List<T>,
	val page: Int,
	val size: Int,
	val totalItems: Long,
	val totalPages: Int,
)
