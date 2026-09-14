package com.ClinicaDeYmid.clinical_history_service.infrastructure.attachment;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.Attachment;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.AttachmentVault;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.EncryptedField;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.Purpose;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.EncryptedContentUnreadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.LifecycleExpiration;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectLockEnabled;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.ObjectLockRetentionMode;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class S3AttachmentVault implements AttachmentVault {

    static final String DATA_KEY_METADATA = "data-key-id";
    private static final Logger log = LoggerFactory.getLogger(S3AttachmentVault.class);

    private final S3Client s3;
    private final AttachmentStorageProperties properties;
    private final ContentEncryption encryption;
    private final AtomicBoolean archiveVerified = new AtomicBoolean();

    S3AttachmentVault(S3Client s3, AttachmentStorageProperties properties, ContentEncryption encryption) {
        this.s3 = s3;
        this.properties = properties;
        this.encryption = encryption;
    }

    @Override
    public void stage(UUID patientUuid, Attachment attachment, byte[] content) {
        EncryptedField encrypted = encryption.encrypt(patientUuid, Purpose.ATTACHMENT_CONTENT, attachment.id(), content);
        storage(() -> s3.putObject(request -> request.bucket(properties.stagingBucket()).key(key(attachment.id()))
                        .contentType("application/octet-stream")
                        .metadata(Map.of(DATA_KEY_METADATA, encrypted.dataKeyId().toString())),
                RequestBody.fromBytes(encrypted.ciphertext())));
    }

    @Override
    public void archive(Attachment attachment, Instant retainUntil) {
        requireWormArchive();
        storage(() -> s3.copyObject(request -> request
                .sourceBucket(properties.stagingBucket()).sourceKey(key(attachment.id()))
                .destinationBucket(properties.archiveBucket()).destinationKey(key(attachment.id()))
                .metadataDirective(MetadataDirective.COPY)
                .objectLockMode(ObjectLockMode.COMPLIANCE)
                .objectLockRetainUntilDate(retainUntil)));
        log.info("Attachment {} archived under compliance retention until {}", attachment.id(), retainUntil);
    }

    @Override
    public void discardStaged(UUID attachmentId) {
        storage(() -> s3.deleteObject(request -> request.bucket(properties.stagingBucket()).key(key(attachmentId))));
    }

    @Override
    public byte[] openArchived(Attachment attachment) {
        ResponseBytes<GetObjectResponse> object = storage(() -> {
            try {
                return s3.getObjectAsBytes(request -> request.bucket(properties.archiveBucket()).key(key(attachment.id())));
            } catch (NoSuchKeyException missing) {
                throw new EncryptedContentUnreadableException("Archived attachment " + attachment.id() + " is missing from storage");
            }
        });
        String dataKeyId = object.response().metadata().get(DATA_KEY_METADATA);
        if (dataKeyId == null) {
            throw new EncryptedContentUnreadableException("Archived attachment " + attachment.id() + " has no data key reference");
        }
        byte[] content = encryption.decrypt(new EncryptedField(UUID.fromString(dataKeyId), object.asByteArray()), Purpose.ATTACHMENT_CONTENT,
                attachment.id());
        if (!attachment.matches(content)) {
            throw new EncryptedContentUnreadableException("Archived attachment " + attachment.id() + " does not match its signed SHA-256");
        }
        return content;
    }

    @Override
    public void extendRetention(UUID attachmentId, Instant retainUntil) {
        HeadObjectResponse head = storage(() -> s3.headObject(request -> request.bucket(properties.archiveBucket()).key(key(attachmentId))));
        if (head.objectLockRetainUntilDate() != null && !head.objectLockRetainUntilDate().isBefore(retainUntil)) {
            return;
        }
        storage(() -> s3.putObjectRetention(request -> request.bucket(properties.archiveBucket()).key(key(attachmentId))
                .retention(retention -> retention.mode(ObjectLockRetentionMode.COMPLIANCE).retainUntilDate(retainUntil))));
    }

    Instant retainedUntil(UUID attachmentId) {
        return storage(() -> s3.headObject(request -> request.bucket(properties.archiveBucket()).key(key(attachmentId))))
                .objectLockRetainUntilDate();
    }

    List<UUID> stagedOlderThan(Instant threshold) {
        return storage(() -> s3.listObjectsV2Paginator(request -> request.bucket(properties.stagingBucket()).prefix("attachments/"))
                .contents().stream()
                .filter(object -> object.lastModified().isBefore(threshold))
                .map(S3Object::key)
                .map(key -> key.substring("attachments/".length()))
                .filter(id -> id.matches("^[0-9a-f-]{36}$"))
                .map(UUID::fromString)
                .toList());
    }

    void createBucketsIfMissing() {
        storage(() -> {
            if (!exists(properties.stagingBucket())) {
                s3.createBucket(request -> request.bucket(properties.stagingBucket()));
                s3.putBucketLifecycleConfiguration(request -> request.bucket(properties.stagingBucket())
                        .lifecycleConfiguration(BucketLifecycleConfiguration.builder().rules(LifecycleRule.builder()
                                .id("expire-unconfirmed-uploads").status(ExpirationStatus.ENABLED)
                                .filter(LifecycleRuleFilter.builder().prefix("attachments/").build())
                                .expiration(LifecycleExpiration.builder().days(7).build()).build()).build()));
            }
            if (!exists(properties.archiveBucket())) {
                s3.createBucket(request -> request.bucket(properties.archiveBucket()).objectLockEnabledForBucket(true));
            }
            return null;
        });
    }

    private boolean exists(String bucket) {
        try {
            s3.headBucket(request -> request.bucket(bucket));
            return true;
        } catch (NoSuchBucketException missing) {
            return false;
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) {
                return false;
            }
            throw failure;
        }
    }

    private void requireWormArchive() {
        if (archiveVerified.get()) {
            return;
        }
        ObjectLockEnabled enabled = storage(() -> {
            try {
                return s3.getObjectLockConfiguration(request -> request.bucket(properties.archiveBucket()))
                        .objectLockConfiguration().objectLockEnabled();
            } catch (S3Exception missingConfiguration) {
                if ("ObjectLockConfigurationNotFoundError".equals(missingConfiguration.awsErrorDetails().errorCode())) {
                    return null;
                }
                throw missingConfiguration;
            }
        });
        if (enabled != ObjectLockEnabled.ENABLED) {
            throw new IllegalStateException("Bucket " + properties.archiveBucket() + " must have Object Lock enabled to keep clinical attachments");
        }
        archiveVerified.set(true);
    }

    private static String key(UUID attachmentId) {
        return "attachments/" + attachmentId;
    }

    private static <T> T storage(StorageCall<T> call) {
        try {
            return call.run();
        } catch (EncryptedContentUnreadableException | IllegalStateException | ClinicalException expected) {
            throw expected;
        } catch (SdkException unavailable) {
            log.warn("Attachment storage call failed: {}", unavailable.getMessage());
            throw new ClinicalException.AttachmentStorageUnavailable();
        }
    }

    @FunctionalInterface
    private interface StorageCall<T> {
        T run();
    }
}
