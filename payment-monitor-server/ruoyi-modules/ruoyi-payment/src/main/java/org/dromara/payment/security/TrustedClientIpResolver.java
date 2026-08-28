package org.dromara.payment.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Resolves the client address only when the direct peer is a trusted local
 * reverse proxy. Public peers cannot override their address with forwarding
 * headers.
 */
@Component
public class TrustedClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalize(request.getRemoteAddr());
        if (!isTrustedAddress(remoteAddress)) {
            return remoteAddress;
        }
        String realIp = normalize(request.getHeader("X-Real-IP"));
        return isIpLiteral(realIp) ? realIp : remoteAddress;
    }

    public boolean isTrustedProxy(HttpServletRequest request) {
        return request != null && isTrustedAddress(normalize(request.getRemoteAddr()));
    }

    private boolean isTrustedAddress(String value) {
        try {
            InetAddress address = InetAddress.getByName(value);
            return address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isIpLiteral(String value) {
        if (value == null || value.isBlank() || value.contains(",")) {
            return false;
        }
        if (!value.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        if (result.startsWith("[") && result.endsWith("]")) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }
}
