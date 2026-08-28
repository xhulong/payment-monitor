package org.dromara.payment.config;

import jakarta.servlet.DispatcherType;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContextInterceptor;
import org.dromara.payment.security.DeviceAuthFilter;
import org.dromara.payment.security.MerchantApiAuthFilter;
import org.dromara.payment.security.MerchantApiRequestGuard;
import org.dromara.payment.security.TrustedClientIpResolver;
import org.dromara.payment.service.MerchantApiKeyService;
import org.dromara.payment.service.MerchantApiAuditService;
import org.dromara.payment.service.PaymentDeviceService;
import org.dromara.payment.service.MerchantLifecycleService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumSet;

/**
 * 支付设备 Web 鉴权配置。
 */
@Configuration
public class PaymentWebConfiguration implements WebMvcConfigurer {

    private final MerchantAccessService merchantAccessService;

    public PaymentWebConfiguration(MerchantAccessService merchantAccessService) {
        this.merchantAccessService = merchantAccessService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MerchantContextInterceptor(merchantAccessService))
            .addPathPatterns("/payment/**")
            .excludePathPatterns("/payment/platform/**", "/payment/app-releases/**");
    }

    @Bean
    public DeviceAuthFilter deviceAuthFilter(PaymentDeviceService deviceService,
                                             PaymentProperties properties,
                                             ObjectMapper objectMapper) {
        return new DeviceAuthFilter(deviceService, properties, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<DeviceAuthFilter> deviceAuthFilterRegistration(DeviceAuthFilter filter) {
        FilterRegistrationBean<DeviceAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setName("paymentDeviceAuthFilter");
        registration.addUrlPatterns("/api/v1/device/*", "/api/v1/payment-events/*");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Bean
    public MerchantApiAuthFilter merchantApiAuthFilter(
        MerchantApiKeyService apiKeyService,
        MerchantApiRequestGuard requestGuard,
        PaymentProperties properties,
        ObjectMapper objectMapper,
        MerchantApiAuditService auditService,
        MerchantLifecycleService lifecycleService,
        TrustedClientIpResolver clientIpResolver
    ) {
        return new MerchantApiAuthFilter(
            apiKeyService,
            requestGuard,
            properties,
            objectMapper,
            auditService,
            lifecycleService,
            clientIpResolver);
    }

    @Bean
    public FilterRegistrationBean<MerchantApiAuthFilter> merchantApiAuthFilterRegistration(
        MerchantApiAuthFilter filter
    ) {
        FilterRegistrationBean<MerchantApiAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setName("paymentMerchantApiAuthFilter");
        registration.addUrlPatterns("/api/v1/merchant/*");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 21);
        return registration;
    }
}
