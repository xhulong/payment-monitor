package org.dromara.payment.integration.epay.security;

import lombok.RequiredArgsConstructor;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.integration.epay.protocol.EpayException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EpayUrlValidator {
    private final PaymentProperties properties;

    public URI validate(String value, String allowedHosts) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme)
                && !("http".equals(scheme) && properties.getEasyPay().isAllowHttp())) {
                throw new EpayException("回调地址必须使用 HTTPS");
            }
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new EpayException("回调地址格式不合法");
            }
            if (uri.getPort() == 0 || uri.getPort() < -1 || uri.getPort() > 65535) {
                throw new EpayException("回调地址端口不合法");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!parseHosts(allowedHosts).contains(host)) {
                throw new EpayException("回调地址不在接入应用域名白名单中");
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new EpayException("回调主机无法解析");
            }
            for (InetAddress address : addresses) {
                validateResolvedAddress(address);
            }
            return uri;
        } catch (EpayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EpayException("回调地址无法解析");
        }
    }

    public String normalizeHosts(Iterable<String> hosts) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (hosts != null) {
            for (String value : hosts) {
                if (value == null) continue;
                for (String candidate : value.split("[,\\s]+")) {
                    String host = candidate.trim().toLowerCase(Locale.ROOT);
                    if (host.isEmpty()) continue;
                    if (!host.matches("(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
                        throw new EpayException("回调域名白名单格式不合法");
                    }
                    normalized.add(host);
                }
            }
        }
        if (normalized.isEmpty()) {
            throw new EpayException("至少配置一个回调域名");
        }
        return String.join(",", normalized);
    }

    public Set<String> parseHosts(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(","))
            .map(item -> item.trim().toLowerCase(Locale.ROOT))
            .filter(item -> !item.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void validateResolvedAddress(InetAddress address) {
        if (properties.getEasyPay().isAllowPrivateNetwork()) return;
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress() || isIpv6UniqueLocal(address)
            || isIpv4Reserved(address)) {
            throw new EpayException("回调地址指向受限网络");
        }
    }

    private boolean isIpv4Reserved(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) return false;
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 0 || first == 10 || first == 127 || first >= 224
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
