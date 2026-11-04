package com.venue.common

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType

/**
 * Writes a { code, message } error from a servlet filter. Filters run before
 * controllers, so ApiExceptionHandler never sees their errors. Only for fixed
 * messages without quotes or backslashes, so no JSON escaping is needed.
 */
fun HttpServletResponse.writeError(status: Int, code: String, message: String) {
	this.status = status
	contentType = MediaType.APPLICATION_JSON_VALUE
	characterEncoding = "UTF-8"
	writer.write("""{"code":"$code","message":"$message"}""")
}
