package com.pokernight.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SpaServingIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void servesEntryHtmlForEverySupportedReactRouteWithoutCachingIt() throws Exception {
        for (String route : new String[]{"/", "/room/ABC123", "/room/ABC123/play", "/room/ABC123/tv"}) {
            mvc.perform(get(route).accept("text/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("id=\"root\"")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        }
    }

    @Test
    void cachesHashedAssetPathsButDoesNotTurnUnknownApiRoutesIntoHtml() throws Exception {
        mvc.perform(get("/assets/app-test.js"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("max-age=31536000")));
        mvc.perform(get("/api/v1/not-a-real-endpoint"))
            .andExpect(status().isNotFound())
            .andExpect(content().string(not(containsString("id=\"root\""))));
    }

    @Test
    void exposesHealthMetricsAndCorrelationIds() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-ID", "test-request-123"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-ID", "test-request-123"))
            .andExpect(content().string(containsString("\"status\":\"UP\"")));
        mvc.perform(get("/actuator/metrics/pokernight.rooms.active"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("pokernight.rooms.active")));
    }

    @Test
    void exposesPhoneReachableNetworkInformation() throws Exception {
        mvc.perform(get("/api/v1/network-info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.origin").isString())
            .andExpect(jsonPath("$.address").isString())
            .andExpect(jsonPath("$.lanAvailable").isBoolean());
    }

    @Test
    void rejectsOversizedApiRequestsBeforeControllerParsing() throws Exception {
        byte[] oversized = new byte[65 * 1024];
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/rooms").contentType("application/json").content(oversized))
            .andExpect(status().isPayloadTooLarge());
    }
}
