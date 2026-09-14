package com.ClinicaDeYmid.clinical_history_service.infrastructure.attachment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.Period;

@ConfigurationProperties("clinica.clinical.attachments")
public record AttachmentStorageProperties(
        String endpoint,
        @DefaultValue("us-east-1") String region,
        String accessKey,
        String secretKey,
        @DefaultValue("clinical-attachments-staging") String stagingBucket,
        @DefaultValue("clinical-attachments") String archiveBucket,
        @DefaultValue("P15Y") Period retentionAfterLastCare,
        @DefaultValue("false") boolean createBuckets,
        @DefaultValue("1h") Duration stagingOrphanAfter,
        @DefaultValue("30s") Duration callTimeout) {
}
