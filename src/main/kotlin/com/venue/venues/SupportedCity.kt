package com.venue.venues

/**
 * The cities venues can be listed in (docs/project.md, Shared data). A venue's
 * state and time zone come from here, so clients can't send a wrong zone.
 * GET /cities will replace this list later.
 */
enum class SupportedCity(val displayName: String, val state: String, val timezone: String) {
	NEW_YORK("New York", "NY", "America/New_York"),
	CHICAGO("Chicago", "IL", "America/Chicago"),
	;

	companion object {
		/** The city whose name matches [name], ignoring case and surrounding spaces. */
		fun find(name: String): SupportedCity? = entries.find { it.displayName.equals(name.trim(), ignoreCase = true) }
	}
}
