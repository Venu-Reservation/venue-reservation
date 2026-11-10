package com.venue.reservation.venues

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(VenueController::class)
class VenueControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var venueService: VenueService

    @Test
    fun `should return search stub json with 200 OK`() {
        val stubResponse = mapOf(
            "endpoint" to "GET /api/v1/venues/search",
            "status" to "STUB"
        )

        given(venueService.getSearchStub()).willReturn(stubResponse)

        mockMvc.perform(get("/api/v1/venues/search"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.endpoint").value("GET /api/v1/venues/search"))
            .andExpect(jsonPath("$.status").value("STUB"))
    }
}
