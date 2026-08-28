package org.dromara.common.web.config;

import org.dromara.common.web.config.properties.CorsProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class ApiCryptoV2CorsTest {

    private static final String ORIGIN = "https://admin.payment-monitor.test";
    private static final String VERSION_HEADER = "X-Api-Crypto-Version";

    @Test
    void acceptsApiCryptoV2PreflightHeaders() throws Exception {
        CorsFilter filter = new ResourcesConfig().corsFilter(new CorsProperties());
        MockHttpServletRequest request = new MockHttpServletRequest(
            "OPTIONS",
            "/auth/login");
        request.addHeader("Origin", ORIGIN);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader(
            "Access-Control-Request-Headers",
            "Content-Type, " + VERSION_HEADER);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals(ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getHeader("Access-Control-Allow-Headers")
            .toLowerCase()
            .contains(VERSION_HEADER.toLowerCase()));
    }

    @Test
    void exposesApiCryptoV2ResponseHeaderToBrowser() throws Exception {
        CorsFilter filter = new ResourcesConfig().corsFilter(new CorsProperties());
        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST",
            "/auth/login");
        request.addHeader("Origin", ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getHeader("Access-Control-Expose-Headers")
            .contains(VERSION_HEADER));
    }
}
