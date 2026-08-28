package org.dromara.payment.security;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class WebhookUrlValidator {

    private final PaymentProperties properties;

    public URI validate(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null
                ? ""
                : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme)
                && !("http".equals(scheme) && properties.getWebhook().isAllowHttp())) {
                throw new ServiceException("Webhook 地址必须使用 HTTPS");
            }
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new ServiceException("Webhook 地址格式不合法");
            }
            if (uri.getPort() == 0 || uri.getPort() < -1 || uri.getPort() > 65535) {
                throw new ServiceException("Webhook 端口不合法");
            }
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0) {
                throw new ServiceException("Webhook 主机无法解析");
            }
            for (InetAddress address : addresses) {
                validateResolvedAddress(address);
            }
            return uri;
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("Webhook 地址无法解析");
        }
    }

    public void validateResolvedAddress(InetAddress address) {
        if (properties.getWebhook().isAllowPrivateNetwork()) {
            return;
        }
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()
            || isIpv6UniqueLocal(address)
            || isIpv4Reserved(address)) {
            throw new ServiceException("Webhook 地址指向受限网络");
        }
    }

    private boolean isIpv4Reserved(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 0
            || first == 10
            || first == 127
            || first >= 224
            || (first == 100 && second >= 64 && second <= 127)
            || (first == 169 && second == 254)
            || (first == 172 && second >= 16 && second <= 31)
            || (first == 192 && second == 0)
            || (first == 192 && second == 168)
            || (first == 198 && (second == 18 || second == 19));
    }

    private boolean isIpv6UniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
