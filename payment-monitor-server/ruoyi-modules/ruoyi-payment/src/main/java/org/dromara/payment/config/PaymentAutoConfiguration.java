package org.dromara.payment.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * 支付事件中心自动配置。
 */
@AutoConfiguration
@EnableConfigurationProperties(PaymentProperties.class)
@EnableScheduling
public class PaymentAutoConfiguration {

    @Bean
    public S3Client paymentS3Client(PaymentProperties properties) {
        PaymentProperties.AppRelease release = properties.getAppRelease();
        var builder = S3Client.builder()
            .region(Region.of(release.getRegion()));
        if (release.getEndpoint() != null && !release.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(release.getEndpoint()))
                .forcePathStyle(true);
        }
        if (release.getAccessKey() != null && !release.getAccessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(release.getAccessKey(), release.getSecretKey())));
        }
        return builder.build();
    }

}
