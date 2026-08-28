package org.dromara.payment.security;

import lombok.RequiredArgsConstructor;
import okhttp3.Dns;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WebhookDnsGuard implements Dns {

    private final WebhookUrlValidator validator;

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
        if (addresses.isEmpty()) {
            throw new UnknownHostException(hostname);
        }
        addresses.forEach(validator::validateResolvedAddress);
        return addresses;
    }
}
