package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.ChunkUploadResponse;
import com.medconsult.ai.dto.AiModels.FileUploadResponse;
import com.medconsult.ai.persistence.entity.AiFileUploadEntity;
import com.medconsult.ai.persistence.mapper.AiFileUploadMapper;
import com.medconsult.ai.util.BusinessIds;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FileUploadService {
    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);
    private static final long UNKNOWN_PART_SIZE = -1L;
    private static final int MAX_PRESIGNED_URL_EXPIRY_SECONDS = 7 * 24 * 60 * 60;
    private static final Pattern UPLOAD_ID_PATTERN =
            Pattern.compile("UP-[0-9a-f]{24}-[0-9a-f]{32}");

    private final AiProperties properties;
    private final AiFileUploadMapper fileUploadMapper;
    private final MinioClient minioClient;
    private final MinioClient presignClient;
    private final int presignedUrlExpirySeconds;

    public FileUploadService(AiProperties properties, AiFileUploadMapper fileUploadMapper) {
        this.properties = properties;
        this.fileUploadMapper = fileUploadMapper;
        AiProperties.FileStorageProperties storage = storage();
        String endpoint = required(storage.endpoint(), "MINIO endpoint is not configured");
        String accessKey = required(storage.accessKey(), "MINIO access key is not configured");
        String secretKey = required(storage.secretKey(), "MINIO secret key is not configured");
        String region = StringUtils.hasText(storage.region()) ? storage.region().trim() : "us-east-1";
        this.presignedUrlExpirySeconds = validPresignedUrlExpiry(storage.presignedUrlExpirySeconds());
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .region(region)
                .credentials(accessKey, secretKey)
                .build();
        String publicEndpoint = StringUtils.hasText(storage.publicEndpoint())
                ? storage.publicEndpoint().trim()
                : endpoint;
        this.presignClient = publicEndpoint.equals(endpoint)
                ? minioClient
                : MinioClient.builder().endpoint(publicEndpoint).region(region)
                        .credentials(accessKey, secretKey).build();
    }

    public FileUploadResponse upload(MultipartFile file, String patientId, String recordId) {
        validateFile(file);
        UploadOwner owner = resolveUploadOwner(patientId);
        ensureBucket();
        String objectKey = objectKey(file.getOriginalFilename());
        putObject(objectKey, file);
        AiFileUploadEntity entity = saveCompleted(file.getOriginalFilename(), file.getContentType(), file.getSize(),
                owner, recordId, objectKey, "SINGLE", null, null);
        return toResponse(entity);
    }

    public ChunkUploadResponse uploadChunk(MultipartFile file, String uploadId, Integer chunkIndex, Integer totalChunks,
                                           String filename, String patientId, String recordId) {
        validateFile(file);
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "filename is required");
        }
        if (chunkIndex == null || totalChunks == null || chunkIndex < 0 || totalChunks <= 0 || chunkIndex >= totalChunks) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "invalid chunk index or total chunks");
        }
        UploadOwner owner = resolveUploadOwner(patientId);
        String normalizedUploadId = normalizeUploadId(owner, uploadId, chunkIndex);
        ensureBucket();
        String chunkObjectKey = chunkObjectKey(normalizedUploadId, chunkIndex);
        putObject(chunkObjectKey, file);

        boolean completed = allChunksUploaded(normalizedUploadId, totalChunks);
        FileUploadResponse response = null;
        if (completed) {
            String objectKey = objectKey(filename);
            composeChunks(normalizedUploadId, totalChunks, objectKey);
            AiFileUploadEntity entity = saveCompleted(filename, file.getContentType(), objectSize(objectKey),
                    owner, recordId, objectKey, "CHUNK", normalizedUploadId, totalChunks);
            removeSourceChunks(normalizedUploadId, totalChunks);
            response = toResponse(entity);
        }
        return new ChunkUploadResponse(normalizedUploadId, chunkIndex, totalChunks, completed, response);
    }

    public FileUploadResponse getFile(String fileId) {
        JwtPayload payload = requirePublicFileUser();
        AiFileUploadEntity entity = fileUploadMapper.selectOne(new LambdaQueryWrapper<AiFileUploadEntity>()
                .eq(AiFileUploadEntity::getFileNo, fileId)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "file not found");
        }
        enforceReadOwnership(payload, entity);
        return toResponse(entity);
    }

    public List<String> resolveDetectionFileLocators(List<String> fileIds) {
        return resolveDetectionFiles(fileIds, null, null).locators();
    }

    public DetectionFileResolution resolveDetectionFiles(List<String> fileIds,
                                                          Long requestedPatientId,
                                                          Long requestedRecordId) {
        List<String> normalizedIds = normalizeDetectionFileIds(fileIds);
        JwtPayload payload = SecurityContext.getPayload();
        if (payload == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "authentication is required");
        }
        List<String> locators = new ArrayList<>(normalizedIds.size());
        Long resolvedPatientId = null;
        Long resolvedRecordId = null;
        boolean firstFile = true;
        for (String fileId : normalizedIds) {
            AiFileUploadEntity entity = fileUploadMapper.selectOne(new LambdaQueryWrapper<AiFileUploadEntity>()
                    .eq(AiFileUploadEntity::getFileNo, fileId)
                    .last("limit 1"));
            if (entity == null || !"COMPLETED".equals(entity.getStatus())
                    || !"MINIO".equals(entity.getStorageType())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "file is unavailable");
            }
            enforceDetectionOwnership(payload, entity);
            if (!bucket().equals(entity.getBucket())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "file bucket is not trusted");
            }
            String objectKey = entity.getObjectKey();
            if (!StringUtils.hasText(objectKey)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "file object is unavailable");
            }
            if (objectKey.startsWith("/") || objectKey.contains("?") || objectKey.contains("#")
                    || objectKey.contains("\\") || containsParentSegment(objectKey)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "file object key is not trusted");
            }
            locators.add(fileLocator(entity.getBucket(), objectKey));
            if (firstFile) {
                resolvedPatientId = entity.getPatientId();
                resolvedRecordId = entity.getRecordId();
                firstFile = false;
            } else {
                requireConsistentFileContext("patientId", resolvedPatientId, entity.getPatientId());
                requireConsistentFileContext("recordId", resolvedRecordId, entity.getRecordId());
            }
        }
        requireRequestedContextMatch("patientId", requestedPatientId, resolvedPatientId);
        requireRequestedContextMatch("recordId", requestedRecordId, resolvedRecordId);
        return new DetectionFileResolution(
                List.copyOf(locators),
                requestedPatientId == null ? resolvedPatientId : requestedPatientId,
                requestedRecordId == null ? resolvedRecordId : requestedRecordId);
    }

    private void ensureBucket() {
        AiProperties.FileStorageProperties storage = storage();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket()).build());
            if (!exists && storage.autoCreateBucket()) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket()).build());
            } else if (!exists) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MINIO bucket does not exist: " + bucket());
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MINIO bucket check failed: " + ex.getMessage());
        }
    }

    private void putObject(String objectKey, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .contentType(contentType(file.getContentType(), file.getOriginalFilename()))
                    .stream(in, file.getSize(), UNKNOWN_PART_SIZE)
                    .build());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MINIO upload failed: " + ex.getMessage());
        }
    }

    private boolean allChunksUploaded(String uploadId, int totalChunks) {
        try {
            for (int i = 0; i < totalChunks; i++) {
                minioClient.statObject(StatObjectArgs.builder()
                        .bucket(bucket())
                        .object(chunkObjectKey(uploadId, i))
                        .build());
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void composeChunks(String uploadId, int totalChunks, String objectKey) {
        try {
            List<ComposeSource> sources = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                sources.add(ComposeSource.builder()
                        .bucket(bucket())
                        .object(chunkObjectKey(uploadId, i))
                        .build());
            }
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .sources(sources)
                    .build());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MINIO chunk compose failed: " + ex.getMessage());
        }
    }

    private long objectSize(String objectKey) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build()).size();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MINIO object stat failed: " + ex.getMessage());
        }
    }

    private AiFileUploadEntity saveCompleted(String filename, String contentType, long fileSize,
                                             UploadOwner owner, String recordId, String objectKey,
                                             String uploadMode, String uploadId, Integer totalChunks) {
        LocalDateTime now = LocalDateTime.now();
        AiFileUploadEntity entity = new AiFileUploadEntity();
        entity.setFileNo(BusinessIds.next("FILE"));
        entity.setPatientId(owner.patientId());
        entity.setUploadedByUserId(owner.userId());
        entity.setUploadedByServiceCode(owner.serviceCode());
        entity.setRecordId(BusinessIds.numericId(recordId));
        entity.setOriginalFilename(safeFilename(filename));
        entity.setFileType(contentType(contentType, filename));
        entity.setFileSize(fileSize);
        entity.setStorageType("MINIO");
        entity.setBucket(bucket());
        entity.setObjectKey(objectKey);
        entity.setFileUrl(fileLocator(bucket(), objectKey));
        entity.setUploadMode(uploadMode);
        entity.setChunkUploadId(uploadId);
        entity.setTotalChunks(totalChunks);
        entity.setUploadedChunks(totalChunks);
        entity.setStatus("COMPLETED");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            if (fileUploadMapper.insert(entity) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "file metadata persistence failed");
            }
        } catch (BusinessException ex) {
            removeUploadedObject(objectKey);
            throw ex;
        } catch (RuntimeException ex) {
            removeUploadedObject(objectKey);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "file metadata persistence failed: " + ex.getMessage());
        }
        return entity;
    }

    private FileUploadResponse toResponse(AiFileUploadEntity entity) {
        return new FileUploadResponse(
                entity.getFileNo(),
                presignedGetUrl(entity.getBucket(), entity.getObjectKey()),
                entity.getFileSize(),
                entity.getFileType(),
                entity.getStorageType(),
                entity.getBucket(),
                entity.getObjectKey(),
                entity.getOriginalFilename()
        );
    }

    private String objectKey(String filename) {
        String date = LocalDate.now().toString().replace("-", "/");
        return prefix(storage().objectPrefix(), "imaging")
                + "/" + date
                + "/" + UUID.randomUUID().toString().replace("-", "")
                + "-" + safeFilename(filename);
    }

    private String chunkObjectKey(String uploadId, int chunkIndex) {
        return prefix(storage().chunkPrefix(), "chunks")
                + "/" + uploadId
                + "/" + String.format("%06d.part", chunkIndex);
    }

    private String presignedGetUrl(String objectBucket, String objectKey) {
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(required(objectBucket, "file bucket is missing"))
                    .object(required(objectKey, "file object key is missing"))
                    .expiry(presignedUrlExpirySeconds)
                    .build());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "MINIO presigned URL generation failed: " + ex.getMessage());
        }
    }

    private void removeUploadedObject(String objectKey) {
        removeObjectBestEffort(objectKey, "orphan upload");
    }

    private void removeSourceChunks(String uploadId, int totalChunks) {
        for (int i = 0; i < totalChunks; i++) {
            removeObjectBestEffort(chunkObjectKey(uploadId, i), "composed source chunk");
        }
    }

    private void removeObjectBestEffort(String objectKey, String reason) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception cleanupError) {
            log.warn("failed to remove MINIO object reason={} bucket={} object={}: {}",
                    reason, bucket(), objectKey, cleanupError.getMessage());
        }
    }

    private AiProperties.FileStorageProperties storage() {
        return properties.fileStorage();
    }

    private String bucket() {
        return required(storage().bucket(), "MINIO bucket is not configured");
    }

    /** 允许的医学影像/文档 MIME 白名单（拒绝可执行/脚本等任意类型上传） */
    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp",
            "application/dicom",
            "application/pdf",
            "text/plain"
    );

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file is required");
        }
        // MIME 白名单校验：拒绝非影像/文档类型（防止上传可执行/脚本文件到对象存储）
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(java.util.Locale.ROOT))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "unsupported file type: " + contentType);
        }
    }

    private static String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
        }
        return value;
    }

    private static int validPresignedUrlExpiry(int value) {
        if (value <= 0 || value > MAX_PRESIGNED_URL_EXPIRY_SECONDS) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "MINIO presigned URL expiry must be between 1 and 604800 seconds");
        }
        return value;
    }

    private static String prefix(String value, String fallback) {
        String raw = StringUtils.hasText(value) ? value : fallback;
        return raw.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private static String safeFilename(String filename) {
        String value = StringUtils.hasText(filename) ? filename : "image.bin";
        return value.replace("\\", "/").substring(value.replace("\\", "/").lastIndexOf('/') + 1)
                .replaceAll("[\\r\\n]", "")
                .replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }

    private static String contentType(String contentType, String filename) {
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".dcm") || lower.endsWith(".dicom")) {
            return "application/dicom";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private UploadOwner resolveUploadOwner(String requestedPatientId) {
        JwtPayload payload = SecurityContext.getPayload();
        if (payload == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "authentication is required");
        }
        Long requestedOwner = optionalNumericId(requestedPatientId, "patientId");
        if (payload.isService()) {
            if (!StringUtils.hasText(payload.serviceCode())) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "service identity is incomplete");
            }
            return new UploadOwner(requestedOwner, null, payload.serviceCode());
        }
        if (!payload.isUser() || payload.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "user identity is incomplete");
        }
        if (isActiveRole(payload, "PATIENT")) {
            Long selfPatientId = payload.patientId();
            if (selfPatientId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "current account is not linked to a patient profile");
            }
            if (requestedOwner != null && !selfPatientId.equals(requestedOwner)) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "patientId must match the current patient");
            }
            return new UploadOwner(selfPatientId, payload.userId(), null);
        }
        if (isActiveRole(payload, "DOCTOR") || isActiveRole(payload, "HOSPITAL_ADMIN")) {
            return new UploadOwner(requestedOwner, payload.userId(), null);
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "current role cannot upload medical images");
    }

    private static JwtPayload requirePublicFileUser() {
        JwtPayload payload = SecurityContext.getPayload();
        if (payload == null || !payload.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "user login is required");
        }
        if (!isActiveRole(payload, "PATIENT") && !isActiveRole(payload, "DOCTOR")
                && !isActiveRole(payload, "HOSPITAL_ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "current role cannot access medical images");
        }
        return payload;
    }

    private static void enforceReadOwnership(JwtPayload payload, AiFileUploadEntity entity) {
        boolean hasBusinessOwner = entity.getPatientId() != null;
        boolean hasUserUploader = entity.getUploadedByUserId() != null;
        boolean hasServiceUploader = StringUtils.hasText(entity.getUploadedByServiceCode());
        if (!hasBusinessOwner && !hasUserUploader && !hasServiceUploader) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "file ownership is missing");
        }
        if (isActiveRole(payload, "PATIENT")) {
            if (payload.patientId() != null && payload.patientId().equals(entity.getPatientId())) {
                return;
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, "no permission to access this file");
        }
        if (payload.userId() != null && payload.userId().equals(entity.getUploadedByUserId())) {
            return;
        }
        if (payload.patientId() != null && payload.patientId().equals(entity.getPatientId())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "no permission to access this file");
    }

    private static void enforceDetectionOwnership(JwtPayload payload, AiFileUploadEntity entity) {
        if (payload.isService()) {
            if (StringUtils.hasText(payload.serviceCode())
                    && payload.serviceCode().equals(entity.getUploadedByServiceCode())) {
                return;
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, "no permission to use this file");
        }
        if (!payload.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "authentication is required");
        }
        if (!isActiveRole(payload, "PATIENT") && !isActiveRole(payload, "DOCTOR")
                && !isActiveRole(payload, "HOSPITAL_ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "current role cannot use medical images");
        }
        enforceReadOwnership(payload, entity);
    }

    private static List<String> normalizeDetectionFileIds(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "fileIds are required");
        }
        if (fileIds.size() > 8) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "at most 8 images are allowed");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String fileId : fileIds) {
            if (!StringUtils.hasText(fileId)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "fileId must not be blank");
            }
            if (!normalized.add(fileId.trim())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "duplicate fileId is not allowed");
            }
        }
        return List.copyOf(normalized);
    }

    private static boolean containsParentSegment(String objectKey) {
        for (String segment : objectKey.split("/", -1)) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static void requireConsistentFileContext(String fieldName, Long expected, Long actual) {
        if (!Objects.equals(expected, actual)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "selected files have inconsistent " + fieldName);
        }
    }

    private static void requireRequestedContextMatch(String fieldName, Long requested, Long resolved) {
        if (requested != null && !requested.equals(resolved)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "file " + fieldName + " does not match requested task " + fieldName);
        }
    }

    private static boolean isActiveRole(JwtPayload payload, String role) {
        if (StringUtils.hasText(payload.primaryRole())) {
            return role.equals(payload.primaryRole());
        }
        return payload.hasRole(role);
    }

    private static Long optionalNumericId(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Long numericId = BusinessIds.numericId(value);
        if (numericId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "invalid " + fieldName);
        }
        return numericId;
    }

    private static String fileLocator(String objectBucket, String objectKey) {
        return "minio://" + objectBucket + "/" + objectKey;
    }

    private static String normalizeUploadId(UploadOwner owner, String uploadId, int chunkIndex) {
        if (!StringUtils.hasText(uploadId)) {
            if (chunkIndex != 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "uploadId is required after the first chunk");
            }
            return "UP-" + ownerNamespace(owner) + "-"
                    + UUID.randomUUID().toString().replace("-", "");
        }
        if (!UPLOAD_ID_PATTERN.matcher(uploadId).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "invalid uploadId");
        }
        String expectedPrefix = "UP-" + ownerNamespace(owner) + "-";
        if (!uploadId.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "uploadId does not belong to current uploader");
        }
        return uploadId;
    }

    private static String ownerNamespace(UploadOwner owner) {
        String actor = owner.userId() != null
                ? "USER:" + owner.userId()
                : "SERVICE:" + owner.serviceCode();
        actor += ":PATIENT:" + (owner.patientId() == null ? "NONE" : owner.patientId());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(actor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record UploadOwner(Long patientId, Long userId, String serviceCode) {
    }

    public record DetectionFileResolution(List<String> locators, Long patientId, Long recordId) {
    }
}
