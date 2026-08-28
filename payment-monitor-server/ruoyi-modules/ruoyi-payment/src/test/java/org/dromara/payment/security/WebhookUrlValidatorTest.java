package org.dromara.payment.security;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class WebhookUrlValidatorTest {

    @Test
    void rejectsHttpAndPrivateTargetsByDefault() {
        WebhookUrlValidator validator = new WebhookUrlValidator(new PaymentProperties());

        assertThrows(ServiceException.class,
            () -> validator.validate("http://example.com/webhook"));
        assertThrows(ServiceException.class,
            () -> validator.validate("https://127.0.0.1/webhook"));
        assertThrows(ServiceException.class,
            () -> validator.validate("https://10.1.2.3/webhook"));
    }

    @Test
    void rejectsCredentialsFragmentsAndPortZero() {
        PaymentProperties properties = localProperties();
        WebhookUrlValidator validator = new WebhookUrlValidator(properties);

        assertThrows(ServiceException.class,
            () -> validator.validate("http://user:password@127.0.0.1/webhook"));
        assertThrows(ServiceException.class,
            () -> validator.validate("http://127.0.0.1/webhook#fragment"));
        assertThrows(ServiceException.class,
            () -> validator.validate("http://127.0.0.1:0/webhook"));
    }

    @Test
    void localDevelopmentMayExplicitlyAllowHttpPrivateTarget() {
        WebhookUrlValidator validator = new WebhookUrlValidator(localProperties());

        assertEquals(
            "http://127.0.0.1:19090/webhook?source=payment",
            validator.validate(" http://127.0.0.1:19090/webhook?source=payment ").toString()
        );
    }

    private PaymentProperties localProperties() {
        PaymentProperties properties = new PaymentProperties();
        properties.getWebhook().setAllowHttp(true);
        properties.getWebhook().setAllowPrivateNetwork(true);
        return properties;
    }
}
