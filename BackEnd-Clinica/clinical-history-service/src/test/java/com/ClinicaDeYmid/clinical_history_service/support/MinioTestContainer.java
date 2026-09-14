package com.ClinicaDeYmid.clinical_history_service.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

public final class MinioTestContainer {

    public static final DockerImageName IMAGE = DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z")
            .asCompatibleSubstituteFor("minio/minio");
    public static final String ACCESS_KEY = "clinical-test";
    public static final String SECRET_KEY = "clinical-test-secret";

    private static final MinIOContainer MINIO = new MinIOContainer(IMAGE).withUserName(ACCESS_KEY).withPassword(SECRET_KEY);

    private MinioTestContainer() {
    }

    public static String endpoint() {
        if (!MINIO.isRunning()) {
            MINIO.start();
        }
        return MINIO.getS3URL();
    }

    public static void register(DynamicPropertyRegistry registry, String stagingBucket, String archiveBucket) {
        registry.add("clinica.clinical.attachments.endpoint", MinioTestContainer::endpoint);
        registry.add("clinica.clinical.attachments.access-key", () -> ACCESS_KEY);
        registry.add("clinica.clinical.attachments.secret-key", () -> SECRET_KEY);
        registry.add("clinica.clinical.attachments.staging-bucket", () -> stagingBucket);
        registry.add("clinica.clinical.attachments.archive-bucket", () -> archiveBucket);
        registry.add("clinica.clinical.attachments.create-buckets", () -> true);
    }
}
