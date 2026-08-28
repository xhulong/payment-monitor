package org.dromara.payment.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class TrustedClientIpResolverTest {

    private final TrustedClientIpResolver resolver = new TrustedClientIpResolver();

    @Test
    void acceptsRealIpOnlyFromLocalProxy() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("172.18.0.4");
        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.15");

        assertEquals("203.0.113.15", resolver.resolve(request));
    }

    @Test
    void ignoresForwardingHeadersFromPublicPeer() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("198.51.100.10");
        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.15");

        assertEquals("198.51.100.10", resolver.resolve(request));
    }

    @Test
    void rejectsCommaSeparatedSpoofedAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.2, 10.0.0.1");

        assertEquals("127.0.0.1", resolver.resolve(request));
    }
}
