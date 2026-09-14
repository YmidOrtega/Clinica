package com.ClinicaDeYmid.clinical_history_service.infrastructure.attachment;

import com.ClinicaDeYmid.clinical_history_service.application.attachment.AttachmentRetention;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AttachmentStorageProperties.class)
public class AttachmentStorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client attachmentsS3Client(AttachmentStorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .httpClient(UrlConnectionHttpClient.create())
                .overrideConfiguration(override -> override.apiCallTimeout(properties.callTimeout()))
                .forcePathStyle(true);
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        if (properties.accessKey() != null && !properties.accessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        return builder.build();
    }

    @Bean
    AttachmentRetention attachmentRetention(AttachmentStorageProperties properties) {
        return new AttachmentRetention(properties.retentionAfterLastCare());
    }
}
