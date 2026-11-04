package com.venue.common

import java.sql.SQLException

private const val EXCLUSION_VIOLATION = "23P01"
private const val UNIQUE_VIOLATION = "23505"
private const val DEADLOCK_DETECTED = "40P01"

/** True if [e] was caused by a Postgres exclusion constraint violation (SQLState 23P01). */
fun isExclusionViolation(e: Throwable): Boolean = sqlState(e) == EXCLUSION_VIOLATION

/** True if [e] was caused by a unique constraint or index violation (SQLState 23505). */
fun isUniqueViolation(e: Throwable): Boolean = sqlState(e) == UNIQUE_VIOLATION

/** True if Postgres aborted the transaction to break a deadlock (SQLState 40P01). Safe to retry. */
fun isDeadlock(e: Throwable): Boolean = sqlState(e) == DEADLOCK_DETECTED

private fun sqlState(e: Throwable): String? =
	generateSequence(e) { it.cause }.filterIsInstance<SQLException>().firstNotNullOfOrNull { it.sqlState }
