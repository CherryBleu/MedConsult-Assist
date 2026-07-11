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
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileUploadService {
    private static final long UNKNOWN_PART_SIZE = -1L;

    private final AiProperties properties;
    private final AiFileUploadMapper fileUploadMapper;
    private final MinioClient minioClient;

    public FileUploadService(AiProperties properties, AiFileUploadMapper fileUploadMapper) {
        this.properties = properties;
        this.fileUploadMapper = fileUploadMapper;
        AiProperties.FileStorageProperties storage = storage();
        this.minioClient = MinioClient.builder()
                .endpoint(required(storage.endpoint(), "MINIO endpoint is not configured"))
                .credentials(required(storage.accessKey(), "MINIO access key is not configured"),
                        required(storage.secretKey(), "MINIO secret key is not configured"))
                .build();
    }

    public FileUploadResponse upload(MultipartFile file, String patientId, String recordId) {
        validateFile(file);
        ensureBucket();
        String objectKey = objectKey(file.getOriginalFilename());
        putObject(objectKey, file);
        AiFileUploadEntity entity = saveCompleted(file.getOriginalFilename(), file.getContentType(), file.getSize(),
                patientId, recordId, objectKey, "SINGLE", null, null);
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
        ensureBucket();
        String normalizedUploadId = StringUtils.hasText(uploadId) ? uploadId : "UP" + UUID.randomUUID().toString().replace("-", "");
        String chunkObjectKey = chunkObjectKey(normalizedUploadId, chunkIndex);
        putObject(chunkObjectKey, file);

        boolean completed = allChunksUploaded(normalizedUploadId, totalChunks);
        FileUploadResponse response = null;
        if (completed) {
            String objectKey = objectKey(filename);
            composeChunks(normalizedUploadId, totalChunks, objectKey);
            AiFileUploadEntity entity = saveCompleted(filename, file.getContentType(), objectSize(objectKey),
                    patientId, recordId, objectKey, "CHUNK", normalizedUploadId, totalChunks);
            response = toResponse(entity);
        }
        return new ChunkUploadResponse(normalizedUploadId, chunkIndex, totalChunks, completed, response);
    }

    public FileUploadResponse getFile(String fileId) {
        AiFileUploadEntity entity = fileUploadMapper.selectOne(new LambdaQueryWrapper<AiFileUploadEntity>()
                .eq(AiFileUploadEntity::getFileNo, fileId)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "file not found");
        }
        return toResponse(entity);
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
                                             String patientId, String recordId, String objectKey,
                                             String uploadMode, String uploadId, Integer totalChunks) {
        LocalDateTime now = LocalDateTime.now();
        AiFileUploadEntity entity = new AiFileUploadEntity();
        entity.setFileNo(BusinessIds.next("FILE"));
        entity.setPatientId(BusinessIds.numericId(patientId));
        entity.setRecordId(BusinessIds.numericId(recordId));
        entity.setOriginalFilename(safeFilename(filename));
        entity.setFileType(contentType(contentType, filename));
        entity.setFileSize(fileSize);
        entity.setStorageType("MINIO");
        entity.setBucket(bucket());
        entity.setObjectKey(objectKey);
        entity.setFileUrl(fileUrl(objectKey));
        entity.setUploadMode(uploadMode);
        entity.setChunkUploadId(uploadId);
        entity.setTotalChunks(totalChunks);
        entity.setUploadedChunks(totalChunks);
        entity.setStatus("COMPLETED");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        fileUploadMapper.insert(entity);
        return entity;
    }

    private FileUploadResponse toResponse(AiFileUploadEntity entity) {
        return new FileUploadResponse(
                entity.getFileNo(),
                entity.getFileUrl(),
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

    private String fileUrl(String objectKey) {
        String endpoint = StringUtils.hasText(storage().publicEndpoint()) ? storage().publicEndpoint() : storage().endpoint();
        String cleanEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return cleanEndpoint + "/" + bucket() + "/" + encodePath(objectKey);
    }

    private AiProperties.FileStorageProperties storage() {
        return properties.fileStorage();
    }

    private String bucket() {
        return required(storage().bucket(), "MINIO bucket is not configured");
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file is required");
        }
    }

    private static String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
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

    private static String encodePath(String objectKey) {
        String[] parts = objectKey.split("/");
        List<String> encoded = new ArrayList<>();
        for (String part : parts) {
            encoded.add(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return String.join("/", encoded);
    }
}
