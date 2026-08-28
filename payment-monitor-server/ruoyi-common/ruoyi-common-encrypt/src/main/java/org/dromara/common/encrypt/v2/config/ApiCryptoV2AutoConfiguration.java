package org.dromara.common.encrypt.v2.config;

import jakarta.servlet.DispatcherType;
import org.dromara.common.encrypt.v2.crypto.ApiCryptoV2KeyStore;
import org.dromara.common.encrypt.v2.crypto.ApiCryptoV2ReplayGuard;
import org.dromara.common.encrypt.v2.crypto.ApiCryptoV2Service;
import org.dromara.common.encrypt.v2.filter.ApiCryptoV2Filter;
import org.redisson.api.RedissonClient;
import org.dromara.common.encrypt.v2.web.ApiCryptoV2JwksController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * Auto-configuration for api-crypto-v2.
 */
@AutoConfiguration
@ConditionalOnProperty(value = "api-crypto-v2.enabled", havingValue = "true")
@EnableConfigurationProperties(ApiCryptoV2Properties.class)
@Import(ApiCryptoV2JwksController.class)
public class ApiCryptoV2AutoConfiguration {

    @Bean
    public ApiCryptoV2KeyStore apiCryptoV2KeyStore(
        ApiCryptoV2Properties properties,
        Environment environment
    ) {
        return new ApiCryptoV2KeyStore(properties, environment);
    }

    @Bean
    public ApiCryptoV2Service apiCryptoV2Service(
        ApiCryptoV2Properties properties,
        ApiCryptoV2KeyStore keyStore,
        JsonMapper jsonMapper
    ) {
        return new ApiCryptoV2Service(properties, keyStore, jsonMapper);
    }

    @Bean
    public ApiCryptoV2ReplayGuard apiCryptoV2ReplayGuard(RedissonClient redissonClient) {
        return new ApiCryptoV2ReplayGuard(redissonClient);
    }

    @Bean
    public ApiCryptoV2AnnotationValidator apiCryptoV2AnnotationValidator(
        ApiCryptoV2Properties properties,
        @Qualifier("requestMappingHandlerMapping")
        RequestMappingHandlerMapping handlerMapping
    ) {
        return new ApiCryptoV2AnnotationValidator(properties, handlerMapping);
    }

    @Bean
    @FilterRegistration(
        name = "apiCryptoV2Filter",
        urlPatterns = "/*",
        order = FilterRegistrationBean.HIGHEST_PRECEDENCE + 1,
        dispatcherTypes = DispatcherType.REQUEST
    )
    public ApiCryptoV2Filter apiCryptoV2Filter(
        ApiCryptoV2Properties properties,
        ApiCryptoV2Service service,
        ApiCryptoV2ReplayGuard replayGuard,
        @Qualifier("requestMappingHandlerMapping")
        RequestMappingHandlerMapping handlerMapping
    ) {
        return new ApiCryptoV2Filter(properties, service, replayGuard, handlerMapping);
    }
}
