package org.dromara.common.encrypt.v2.config;

import org.dromara.common.encrypt.annotation.ApiCryptoV2;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Fails application startup when an api-crypto-v2 annotation declares an
 * unsupported request/response combination.
 */
public class ApiCryptoV2AnnotationValidator implements SmartInitializingSingleton {

    private final ApiCryptoV2Properties properties;
    private final RequestMappingHandlerMapping handlerMapping;

    public ApiCryptoV2AnnotationValidator(
        ApiCryptoV2Properties properties,
        RequestMappingHandlerMapping handlerMapping
    ) {
        this.properties = properties;
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.isFailOnPlaintext()) {
            throw new IllegalStateException(
                "api-crypto-v2 requires fail-on-plaintext=true when enabled");
        }
        handlerMapping.getHandlerMethods().values().forEach(this::validate);
    }

    private void validate(HandlerMethod handlerMethod) {
        ApiCryptoV2 annotation = handlerMethod.getMethodAnnotation(ApiCryptoV2.class);
        if (annotation == null || annotation.request() || !annotation.response()) {
            return;
        }
        throw new IllegalStateException(
            "@ApiCryptoV2 response encryption requires request encryption: "
                + handlerMethod.getBeanType().getName()
                + "#"
                + handlerMethod.getMethod().getName());
    }
}
