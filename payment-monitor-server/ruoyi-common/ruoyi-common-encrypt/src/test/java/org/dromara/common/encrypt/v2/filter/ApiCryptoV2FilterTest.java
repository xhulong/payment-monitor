package org.dromara.common.encrypt.v2.filter;

import org.dromara.common.encrypt.annotation.ApiCryptoV2;
import org.dromara.common.encrypt.v2.config.ApiCryptoV2Properties;
import org.dromara.common.encrypt.v2.crypto.ApiCryptoV2ReplayGuard;
import org.dromara.common.encrypt.v2.crypto.ApiCryptoV2Service;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class ApiCryptoV2FilterTest {

    @Test
    void rejectsAnnotatedEndpointWithoutBothProtocolHeaders() throws Exception {
        Fixture fixture = fixture("encrypted");
        fixture.request().addHeader(ApiCryptoV2Service.VERSION_HEADER, "2");
        fixture.request().setContentType("application/json");

        fixture.filter().doFilter(
            fixture.request(),
            fixture.response(),
            fixture.chain());

        assertEquals(400, fixture.response().getStatus());
        assertTrue(fixture.response().getContentAsString().contains("API_CRYPTO_INVALID"));
        assertNull(fixture.chain().getRequest());
    }

    @Test
    void rejectsProtocolMarkerOnUnannotatedEndpoint() throws Exception {
        Fixture fixture = fixture("plain");
        fixture.request().addHeader(ApiCryptoV2Service.VERSION_HEADER, "2");
        fixture.request().setContentType(ApiCryptoV2Service.CONTENT_TYPE);

        fixture.filter().doFilter(
            fixture.request(),
            fixture.response(),
            fixture.chain());

        assertEquals(400, fixture.response().getStatus());
        assertNull(fixture.chain().getRequest());
    }

    @Test
    void rejectsLookalikeCryptoMediaType() throws Exception {
        Fixture fixture = fixture("encrypted");
        fixture.request().addHeader(ApiCryptoV2Service.VERSION_HEADER, "2");
        fixture.request().setContentType(ApiCryptoV2Service.CONTENT_TYPE + "-invalid");

        fixture.filter().doFilter(
            fixture.request(),
            fixture.response(),
            fixture.chain());

        assertEquals(400, fixture.response().getStatus());
        assertNull(fixture.chain().getRequest());
    }

    private Fixture fixture(String methodName) throws Exception {
        ApiCryptoV2Properties properties = new ApiCryptoV2Properties();
        ApiCryptoV2Service service = mock(ApiCryptoV2Service.class);
        ApiCryptoV2ReplayGuard replayGuard = mock(ApiCryptoV2ReplayGuard.class);
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        TestController controller = new TestController();
        Method method = TestController.class.getDeclaredMethod(methodName);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setContent("{}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(handlerMapping.getHandler(request)).thenReturn(
            new HandlerExecutionChain(new HandlerMethod(controller, method)));
        return new Fixture(
            new ApiCryptoV2Filter(properties, service, replayGuard, handlerMapping),
            request,
            response,
            chain);
    }

    private record Fixture(
        ApiCryptoV2Filter filter,
        MockHttpServletRequest request,
        MockHttpServletResponse response,
        MockFilterChain chain
    ) {
    }

    private static class TestController {

        @ApiCryptoV2(request = true, response = true)
        public void encrypted() {
        }

        public void plain() {
        }
    }
}
