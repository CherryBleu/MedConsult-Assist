package com.medconsult.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.dto.AiModels.ChunkUploadResponse;
import com.medconsult.ai.dto.AiModels.FileUploadResponse;
import com.medconsult.ai.persistence.entity.AiFileUploadEntity;
import com.medconsult.ai.persistence.mapper.AiFileUploadMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileUploadServiceTest {

    private AiFileUploadMapper mapper;
    private MinioClient minioClient;
    private MinioClient presignClient;
    private FileUploadService service;

    @BeforeAll
    static void initializeMybatisLambdaMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "file-upload-test"),
                AiFileUploadEntity.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(AiFileUploadMapper.class);
        minioClient = mock(MinioClient.class);
        presignClient = mock(MinioClient.class);
        service = service(storage("http://minio:9000", "https://files.example.test/", "/medical\\imaging/",
                "/temporary\\chunks/", true));
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(presignClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenAnswer(invocation -> {
                    GetPresignedObjectUrlArgs args = invocation.getArgument(0);
                    return "https://files.example.test/" + args.bucket() + "/" + args.object()
                            + "?X-Amz-Expires=" + args.expiry();
                });
        bind(user(100L, "HOSPITAL_ADMIN", null));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void uploadShouldStoreSanitizedFileAndPersistCompletedMetadata() throws Exception {
        MockMultipartFile file = file("folder/scan report\r\n.png", "image/png", "image-bytes");
        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        ArgumentCaptor<AiFileUploadEntity> entityCaptor = ArgumentCaptor.forClass(AiFileUploadEntity.class);
        ArgumentCaptor<GetPresignedObjectUrlArgs> presignCaptor =
                ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        when(mapper.insert(entityCaptor.capture())).thenReturn(1);

        FileUploadResponse response = service.upload(file, "PAT42", "MR77");

        verify(minioClient).putObject(putCaptor.capture());
        PutObjectArgs put = putCaptor.getValue();
        assertEquals("medical/imaging", put.object().substring(0, "medical/imaging".length()));
        assertTrue(put.object().endsWith("-scan_report.png"));
        assertEquals("image/png", put.contentType());

        AiFileUploadEntity entity = entityCaptor.getValue();
        assertEquals(42L, entity.getPatientId());
        assertEquals(77L, entity.getRecordId());
        assertEquals("scan_report.png", entity.getOriginalFilename());
        assertEquals("SINGLE", entity.getUploadMode());
        assertEquals("COMPLETED", entity.getStatus());
        assertEquals("medical-images", entity.getBucket());
        assertEquals(100L, entity.getUploadedByUserId());
        assertNull(entity.getUploadedByServiceCode());
        assertEquals("minio://medical-images/" + entity.getObjectKey(), entity.getFileUrl());
        assertNull(entity.getChunkUploadId());
        assertNotNull(entity.getCreatedAt());
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());

        verify(presignClient).getPresignedObjectUrl(presignCaptor.capture());
        assertEquals(Method.GET, presignCaptor.getValue().method());
        assertEquals(300, presignCaptor.getValue().expiry());
        assertEquals("medical-images", presignCaptor.getValue().bucket());
        assertEquals(entity.getObjectKey(), presignCaptor.getValue().object());
        assertEquals(entity.getFileNo(), response.fileId());
        assertEquals(entity.getObjectKey(), response.objectKey());
        assertEquals("https://files.example.test/medical-images/" + entity.getObjectKey()
                + "?X-Amz-Expires=300", response.fileUrl());
    }

    @Test
    void uploadShouldCreateMissingBucketAndUseFallbackFilename() throws Exception {
        bind(user(101L, "PATIENT", 51L));
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile("file", null, "IMAGE/JPEG", "jpeg".getBytes());
        ArgumentCaptor<AiFileUploadEntity> captor = ArgumentCaptor.forClass(AiFileUploadEntity.class);
        when(mapper.insert(captor.capture())).thenReturn(1);

        FileUploadResponse response = service.upload(file, null, null);

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        assertEquals("image.bin", captor.getValue().getOriginalFilename());
        assertTrue(response.objectKey().endsWith("-image.bin"));
        assertEquals(51L, captor.getValue().getPatientId());
        assertEquals(101L, captor.getValue().getUploadedByUserId());
        assertNull(captor.getValue().getRecordId());
    }

    @Test
    void uploadShouldRejectMissingBucketWhenAutoCreationIsDisabled() throws Exception {
        service = service(storage("http://minio:9000", "", "imaging", "chunks", false));
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("scan.png", "image/png", "data"), "1", "2"));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertEquals("MINIO bucket does not exist: medical-images", error.getMessage());
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadShouldWrapBucketCheckFailure() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new IOException("offline"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("scan.png", "image/png", "data"), "1", "2"));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertEquals("MINIO bucket check failed: offline", error.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    void uploadShouldWrapObjectWriteFailureAndAvoidPersistence() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new IOException("write failed"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("scan.png", "image/png", "data"), "1", "2"));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertEquals("MINIO upload failed: write failed", error.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    void uploadChunkShouldReturnIncompleteWhenAnyPartIsMissing() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(new IOException("not found"));
        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);

        ChunkUploadResponse response = service.uploadChunk(
                file("part.bin", "application/dicom", "part-0"), null, 0, 2,
                "study.dcm", "PAT12", "MR34");

        verify(minioClient).putObject(putCaptor.capture());
        assertTrue(response.uploadId().matches("UP-[0-9a-f]{24}-[0-9a-f]{32}"));
        assertTrue(putCaptor.getValue().object().matches(
                "temporary/chunks/UP-[0-9a-f]{24}-[0-9a-f]{32}/000000\\.part"));
        assertFalse(response.completed());
        assertNull(response.file());
        verify(minioClient, never()).composeObject(any(ComposeObjectArgs.class));
        verifyNoInteractions(mapper);
    }

    @Test
    void uploadChunkShouldComposeAllPartsAndPersistMergedObject() throws Exception {
        service = service(storage("http://minio:9000", "", "", "chunks", true));
        String uploadId = uploadIdForUser(100L, 12L, 'a');
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(8192L);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        ArgumentCaptor<ComposeObjectArgs> composeCaptor = ArgumentCaptor.forClass(ComposeObjectArgs.class);
        ArgumentCaptor<AiFileUploadEntity> entityCaptor = ArgumentCaptor.forClass(AiFileUploadEntity.class);
        ArgumentCaptor<RemoveObjectArgs> removeCaptor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        when(mapper.insert(entityCaptor.capture())).thenReturn(1);

        ChunkUploadResponse response = service.uploadChunk(
                file("000001.part", "application/dicom", "part-1"), uploadId, 1, 2,
                "folder/study 1.dcm", "PAT12", "MR34");

        verify(minioClient).composeObject(composeCaptor.capture());
        ComposeObjectArgs compose = composeCaptor.getValue();
        assertEquals(List.of("chunks/" + uploadId + "/000000.part", "chunks/" + uploadId + "/000001.part"),
                compose.sources().stream().map(source -> source.object()).toList());
        assertTrue(compose.object().endsWith("-study_1.dcm"));
        verify(minioClient, times(3)).statObject(any(StatObjectArgs.class));

        AiFileUploadEntity entity = entityCaptor.getValue();
        assertEquals(8192L, entity.getFileSize());
        assertEquals("CHUNK", entity.getUploadMode());
        assertEquals(uploadId, entity.getChunkUploadId());
        assertEquals(2, entity.getTotalChunks());
        assertEquals(2, entity.getUploadedChunks());
        assertEquals("minio://medical-images/" + entity.getObjectKey(), entity.getFileUrl());
        verify(minioClient, times(2)).removeObject(removeCaptor.capture());
        assertEquals(List.of("chunks/" + uploadId + "/000000.part", "chunks/" + uploadId + "/000001.part"),
                removeCaptor.getAllValues().stream().map(RemoveObjectArgs::object).toList());
        assertTrue(response.completed());
        assertEquals(entity.getFileNo(), response.file().fileId());
    }

    @Test
    void uploadChunkShouldRemainSuccessfulWhenSourceCleanupFails() throws Exception {
        String uploadId = uploadIdForUser(100L, 42L, 'b');
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(1024L);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        when(mapper.insert(any(AiFileUploadEntity.class))).thenReturn(1);
        doThrow(new IOException("cleanup unavailable"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        ChunkUploadResponse response = service.uploadChunk(
                file("000000.part", "application/dicom", "part"), uploadId, 0, 1,
                "study.dcm", "42", null);

        assertTrue(response.completed());
        assertNotNull(response.file());
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void patientChunkUploadShouldRejectForgedOwnerBeforeWritingPart() {
        bind(user(501L, "PATIENT", 42L));

        BusinessException error = assertThrows(BusinessException.class, () -> service.uploadChunk(
                file("part.bin", "application/dicom", "part"), "UP-1", 0, 2,
                "study.dcm", "99", null));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("patientId must match the current patient", error.getMessage());
        verifyNoInteractions(minioClient, mapper, presignClient);
    }

    @Test
    void uploadChunkShouldRejectUploadIdOwnedByAnotherUserBeforeWritingPart() {
        bind(user(602L, "DOCTOR", null));
        String anotherUsersUploadId = uploadIdForUser(601L, null, 'e');

        BusinessException error = assertThrows(BusinessException.class, () -> service.uploadChunk(
                file("part.bin", "application/dicom", "part"), anotherUsersUploadId, 0, 2,
                "study.dcm", null, null));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("uploadId does not belong to current uploader", error.getMessage());
        verifyNoInteractions(minioClient, mapper, presignClient);
    }

    @Test
    void uploadChunkShouldRejectPatientRebindingBySameDoctorBeforeWritingPart() {
        bind(user(601L, "DOCTOR", null));
        String patient42UploadId = uploadIdForUser(601L, 42L, '1');

        BusinessException error = assertThrows(BusinessException.class, () -> service.uploadChunk(
                file("part.bin", "application/dicom", "part"), patient42UploadId, 1, 2,
                "study.dcm", "43", null));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("uploadId does not belong to current uploader", error.getMessage());
        verifyNoInteractions(minioClient, mapper, presignClient);
    }

    @Test
    void uploadChunkShouldRejectPatientRebindingBySameServiceBeforeWritingPart() {
        bind(serviceIdentity("medical-record-service"));
        String patient42UploadId = uploadIdForService("medical-record-service", 42L, '2');

        BusinessException error = assertThrows(BusinessException.class, () -> service.uploadChunk(
                file("part.bin", "application/dicom", "part"), patient42UploadId, 1, 2,
                "study.dcm", "43", null));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("uploadId does not belong to current uploader", error.getMessage());
        verifyNoInteractions(minioClient, mapper, presignClient);
    }

    @Test
    void uploadChunkShouldRejectPathCharactersInUploadIdBeforeWritingPart() {
        String unsafeUploadId = uploadIdForUser(100L, 42L, 'f') + "/../../other";

        BusinessException error = assertThrows(BusinessException.class, () -> service.uploadChunk(
                file("part.bin", "application/dicom", "part"), unsafeUploadId, 0, 2,
                "study.dcm", "42", null));

        assertEquals(ErrorCode.PARAM_ERROR, error.getErrorCode());
        assertEquals("invalid uploadId", error.getMessage());
        verifyNoInteractions(minioClient, mapper, presignClient);
    }

    @Test
    void uploadChunkShouldRequireServerUploadIdAfterFirstPart() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.uploadChunk(
                file("part.bin", "application/dicom", "part"), null, 1, 2,
                "study.dcm", "42", null));

        assertEquals(ErrorCode.PARAM_ERROR, error.getErrorCode());
        assertEquals("uploadId is required after the first chunk", error.getMessage());
        verifyNoInteractions(minioClient, mapper, presignClient);
    }

    @Test
    void uploadChunkShouldWrapComposeFailure() throws Exception {
        String uploadId = uploadIdForUser(100L, 1L, 'c');
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        when(minioClient.composeObject(any(ComposeObjectArgs.class))).thenThrow(new IOException("compose failed"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.uploadChunk(
                file("part.bin", "application/dicom", "part"), uploadId, 0, 1,
                "study.dcm", "1", "2"));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertEquals("MINIO chunk compose failed: compose failed", error.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    void uploadChunkShouldWrapMergedObjectStatFailure() throws Exception {
        String uploadId = uploadIdForUser(100L, 1L, 'd');
        StatObjectResponse part = mock(StatObjectResponse.class);
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenReturn(part, part)
                .thenThrow(new IOException("stat failed"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.uploadChunk(
                file("part.bin", "application/dicom", "part"), uploadId, 1, 2,
                "study.dcm", "1", "2"));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertEquals("MINIO object stat failed: stat failed", error.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    void uploadChunkShouldRequireFilenameBeforeCallingMinio() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.uploadChunk(
                file("part.bin", "application/dicom", "part"), "UP-1", 0, 1,
                "  ", "1", "2"));

        assertEquals(ErrorCode.PARAM_ERROR, error.getErrorCode());
        assertEquals("filename is required", error.getMessage());
        verifyNoInteractions(minioClient, mapper);
    }

    @ParameterizedTest(name = "chunkIndex={0}, totalChunks={1}")
    @MethodSource("invalidChunkCoordinates")
    void uploadChunkShouldRejectInvalidCoordinates(Integer chunkIndex, Integer totalChunks) {
        BusinessException error = assertThrows(BusinessException.class, () -> service.uploadChunk(
                file("part.bin", "application/dicom", "part"), "UP-1", chunkIndex, totalChunks,
                "study.dcm", "1", "2"));

        assertEquals(ErrorCode.PARAM_ERROR, error.getErrorCode());
        assertEquals("invalid chunk index or total chunks", error.getMessage());
        verifyNoInteractions(minioClient, mapper);
    }

    @Test
    void uploadShouldRejectNullFile() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.upload(null, "1", "2"));

        assertEquals(ErrorCode.PARAM_ERROR, error.getErrorCode());
        assertEquals("file is required", error.getMessage());
        verifyNoInteractions(minioClient, mapper);
    }

    @Test
    void uploadShouldRejectEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "scan.png", "image/png", new byte[0]);

        BusinessException error = assertThrows(BusinessException.class, () -> service.upload(empty, "1", "2"));

        assertEquals(ErrorCode.PARAM_ERROR, error.getErrorCode());
        assertEquals("file is required", error.getMessage());
        verifyNoInteractions(minioClient, mapper);
    }

    @Test
    void uploadShouldRejectMissingContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "scan.png", null, "data".getBytes());

        BusinessException error = assertThrows(BusinessException.class, () -> service.upload(file, "1", "2"));

        assertEquals(ErrorCode.PARAM_ERROR, error.getErrorCode());
        assertEquals("unsupported file type: null", error.getMessage());
        verifyNoInteractions(minioClient, mapper);
    }

    @Test
    void uploadShouldRejectExecutableContentType() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("payload.js", "application/javascript", "alert(1)"), "1", "2"));

        assertEquals(ErrorCode.PARAM_ERROR, error.getErrorCode());
        assertEquals("unsupported file type: application/javascript", error.getMessage());
        verifyNoInteractions(minioClient, mapper);
    }

    @Test
    void patientUploadShouldBindOwnerFromJwtWhenRequestOwnerIsMissing() throws Exception {
        bind(user(501L, "PATIENT", 42L));
        ArgumentCaptor<AiFileUploadEntity> captor = ArgumentCaptor.forClass(AiFileUploadEntity.class);
        when(mapper.insert(captor.capture())).thenReturn(1);

        service.upload(file("scan.png", "image/png", "data"), null, null);

        assertEquals(42L, captor.getValue().getPatientId());
        assertEquals(501L, captor.getValue().getUploadedByUserId());
        assertNull(captor.getValue().getUploadedByServiceCode());
    }

    @Test
    void patientUploadShouldRejectForgedOwnerBeforeWritingObject() {
        bind(user(501L, "PATIENT", 42L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("scan.png", "image/png", "data"), "PAT99", null));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("patientId must match the current patient", error.getMessage());
        verifyNoInteractions(minioClient, mapper, presignClient);
    }

    @Test
    void patientUploadShouldRejectAccountWithoutPatientProfile() {
        bind(user(501L, "PATIENT", null));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("scan.png", "image/png", "data"), null, null));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("current account is not linked to a patient profile", error.getMessage());
        verifyNoInteractions(minioClient, mapper, presignClient);
    }

    @Test
    void doctorUploadWithoutPatientShouldPersistJwtUploader() throws Exception {
        bind(user(601L, "DOCTOR", null));
        ArgumentCaptor<AiFileUploadEntity> captor = ArgumentCaptor.forClass(AiFileUploadEntity.class);
        when(mapper.insert(captor.capture())).thenReturn(1);

        service.upload(file("scan.png", "image/png", "data"), null, null);

        assertNull(captor.getValue().getPatientId());
        assertEquals(601L, captor.getValue().getUploadedByUserId());
        assertNull(captor.getValue().getUploadedByServiceCode());
    }

    @Test
    void multiRoleDoctorShouldUploadWithoutPatientAndReadOwnFile() throws Exception {
        bind(user(602L, List.of("PATIENT", "DOCTOR"), "DOCTOR", 42L));
        ArgumentCaptor<AiFileUploadEntity> captor = ArgumentCaptor.forClass(AiFileUploadEntity.class);
        when(mapper.insert(captor.capture())).thenReturn(1);

        service.upload(file("scan.png", "image/png", "data"), null, null);

        AiFileUploadEntity entity = captor.getValue();
        assertNull(entity.getPatientId());
        assertEquals(602L, entity.getUploadedByUserId());
        when(mapper.selectOne(any())).thenReturn(entity);
        assertEquals(entity.getFileNo(), service.getFile(entity.getFileNo()).fileId());
    }

    @Test
    void multiRolePatientShouldStillRejectForgedPatientOwner() {
        bind(user(603L, List.of("PATIENT", "DOCTOR"), "PATIENT", 42L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("scan.png", "image/png", "data"), "99", null));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("patientId must match the current patient", error.getMessage());
        verifyNoInteractions(minioClient, mapper, presignClient);
    }

    @Test
    void missingPrimaryRoleShouldFallBackToPatientRoleAndSelfScope() {
        bind(user(604L, List.of("PATIENT", "DOCTOR"), null, 42L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("scan.png", "image/png", "data"), "99", null));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verifyNoInteractions(minioClient, mapper, presignClient);
    }

    @Test
    void serviceUploadWithoutPatientShouldPersistServiceOwner() throws Exception {
        bind(serviceIdentity("medical-record-service"));
        ArgumentCaptor<AiFileUploadEntity> captor = ArgumentCaptor.forClass(AiFileUploadEntity.class);
        when(mapper.insert(captor.capture())).thenReturn(1);

        service.upload(file("scan.png", "image/png", "data"), null, null);

        assertNull(captor.getValue().getPatientId());
        assertNull(captor.getValue().getUploadedByUserId());
        assertEquals("medical-record-service", captor.getValue().getUploadedByServiceCode());
    }

    @Test
    void publicUploadShouldRejectUnsupportedUserRole() {
        bind(user(701L, "PHARMACY_ADMIN", null));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("scan.png", "image/png", "data"), null, null));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("current role cannot upload medical images", error.getMessage());
        verifyNoInteractions(minioClient, mapper, presignClient);
    }

    @Test
    void uploadShouldRemoveObjectWhenMetadataInsertReturnsZero() throws Exception {
        when(mapper.insert(any(AiFileUploadEntity.class))).thenReturn(0);
        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        ArgumentCaptor<RemoveObjectArgs> removeCaptor = ArgumentCaptor.forClass(RemoveObjectArgs.class);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("scan.png", "image/png", "data"), "42", null));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertEquals("file metadata persistence failed", error.getMessage());
        verify(minioClient).putObject(putCaptor.capture());
        verify(minioClient).removeObject(removeCaptor.capture());
        assertEquals(putCaptor.getValue().object(), removeCaptor.getValue().object());
        verifyNoInteractions(presignClient);
    }

    @Test
    void uploadShouldRemoveObjectAndWrapMetadataInsertException() throws Exception {
        when(mapper.insert(any(AiFileUploadEntity.class))).thenThrow(new IllegalStateException("db offline"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("scan.png", "image/png", "data"), "42", null));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertEquals("file metadata persistence failed: db offline", error.getMessage());
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verifyNoInteractions(presignClient);
    }

    @Test
    void getFileShouldReturnPersistedMetadata() {
        AiFileUploadEntity entity = completedEntity();
        when(mapper.selectOne(any())).thenReturn(entity);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<AiFileUploadEntity>> queryCaptor =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);

        FileUploadResponse response = service.getFile("FILE-1");

        verify(mapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("file_no"));
        assertTrue(queryCaptor.getValue().getParamNameValuePairs().containsValue("FILE-1"));
        assertEquals("FILE-1", response.fileId());
        assertEquals("https://files.example.test/medical-images/imaging/scan.png?X-Amz-Expires=300",
                response.fileUrl());
        assertEquals(123L, response.fileSize());
        assertEquals("scan.png", response.originalFilename());
    }

    @Test
    void getFileShouldAllowPatientOwnerEvenWhenUploadedByAnotherUser() {
        bind(user(502L, "PATIENT", 42L));
        AiFileUploadEntity entity = completedEntity();
        entity.setUploadedByUserId(601L);
        when(mapper.selectOne(any())).thenReturn(entity);

        FileUploadResponse response = service.getFile("FILE-1");

        assertEquals("FILE-1", response.fileId());
    }

    @Test
    void getFileShouldAllowOriginalUploaderWithoutPatientOwner() {
        bind(user(601L, "DOCTOR", null));
        AiFileUploadEntity entity = completedEntity();
        entity.setPatientId(null);
        entity.setUploadedByUserId(601L);
        when(mapper.selectOne(any())).thenReturn(entity);

        FileUploadResponse response = service.getFile("FILE-1");

        assertEquals("FILE-1", response.fileId());
    }

    @Test
    void getFileShouldRejectCrossOwnerAccess() {
        bind(user(503L, "PATIENT", 43L));
        when(mapper.selectOne(any())).thenReturn(completedEntity());

        BusinessException error = assertThrows(BusinessException.class, () -> service.getFile("FILE-1"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("no permission to access this file", error.getMessage());
        verifyNoInteractions(presignClient);
    }

    @Test
    void getFileShouldRejectRecordWithoutBusinessOrUploaderOwner() {
        AiFileUploadEntity entity = completedEntity();
        entity.setPatientId(null);
        entity.setUploadedByUserId(null);
        entity.setUploadedByServiceCode(null);
        when(mapper.selectOne(any())).thenReturn(entity);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getFile("FILE-1"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("file ownership is missing", error.getMessage());
        verifyNoInteractions(presignClient);
    }

    @Test
    void getFileShouldRejectDoctorWhoIsNeitherPatientNorUploader() {
        bind(user(602L, "DOCTOR", null));
        AiFileUploadEntity entity = completedEntity();
        entity.setUploadedByUserId(601L);
        when(mapper.selectOne(any())).thenReturn(entity);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getFile("FILE-1"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verifyNoInteractions(presignClient);
    }

    @Test
    void getFileShouldAllowHospitalAdminWhoUploadedTheFile() {
        bind(user(801L, "HOSPITAL_ADMIN", null));
        AiFileUploadEntity entity = completedEntity();
        entity.setUploadedByUserId(801L);
        when(mapper.selectOne(any())).thenReturn(entity);

        FileUploadResponse response = service.getFile("FILE-1");

        assertEquals("FILE-1", response.fileId());
    }

    @Test
    void getFileShouldRejectHospitalAdminWhoIsNotTheUploaderOrPatient() {
        bind(user(801L, "HOSPITAL_ADMIN", null));
        AiFileUploadEntity entity = completedEntity();
        entity.setUploadedByUserId(601L);
        when(mapper.selectOne(any())).thenReturn(entity);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getFile("FILE-1"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("no permission to access this file", error.getMessage());
        verifyNoInteractions(presignClient);
    }

    @Test
    void publicGetFileShouldRejectServiceIdentity() {
        bind(serviceIdentity("medical-record-service"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.getFile("FILE-1"));

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertEquals("user login is required", error.getMessage());
        verifyNoInteractions(mapper, presignClient);
    }

    @Test
    void getFileShouldReportMissingRecord() {
        when(mapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getFile("missing"));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
        assertEquals("file not found", error.getMessage());
    }

    @Test
    void constructorShouldRejectMissingMinioEndpoint() {
        AiProperties properties = properties(storage(" ", "", "imaging", "chunks", true));

        BusinessException error = assertThrows(BusinessException.class,
                () -> new FileUploadService(properties, mapper));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertEquals("MINIO endpoint is not configured", error.getMessage());
    }

    @Test
    void uploadShouldSignCanonicalRequestForConfiguredPublicEndpoint() throws Exception {
        FileUploadService realSignerService = new FileUploadService(properties(storage(
                "http://minio.internal:9000", "https://files.example.test/",
                "imaging", "chunks", true)), mapper);
        ReflectionTestUtils.setField(realSignerService, "minioClient", minioClient);
        when(mapper.insert(any(AiFileUploadEntity.class))).thenReturn(1);

        FileUploadResponse response = realSignerService.upload(
                file("scan.png", "image/png", "data"), "42", null);

        URI signed = URI.create(response.fileUrl());
        assertEquals("https", signed.getScheme());
        assertEquals("files.example.test", signed.getHost());
        assertTrue(signed.getRawQuery().contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));
        assertTrue(signed.getRawQuery().contains("X-Amz-Expires=300"));
        assertTrue(signed.getRawQuery().contains("X-Amz-Signature="));
    }

    @Test
    void constructorShouldRejectInvalidPresignedUrlExpiry() {
        AiProperties.FileStorageProperties storage = new AiProperties.FileStorageProperties(
                "http://minio:9000", "https://files.example.test", "access-key", "secret-key", "us-east-1",
                "medical-images", "imaging", "chunks", true, 0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> new FileUploadService(properties(storage), mapper));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertEquals("MINIO presigned URL expiry must be between 1 and 604800 seconds", error.getMessage());
    }

    @Test
    void uploadShouldRejectMissingBucketConfiguration() {
        service = service(new AiProperties.FileStorageProperties(
                "http://minio:9000", "", "access-key", "secret-key", "us-east-1",
                " ", "imaging", "chunks", true, 300));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file("scan.png", "image/png", "data"), "1", "2"));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertEquals("MINIO bucket is not configured", error.getMessage());
    }

    private FileUploadService service(AiProperties.FileStorageProperties storage) {
        FileUploadService result = new FileUploadService(properties(storage), mapper);
        ReflectionTestUtils.setField(result, "minioClient", minioClient);
        ReflectionTestUtils.setField(result, "presignClient", presignClient);
        return result;
    }

    private static void bind(JwtPayload payload) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContext.setPayload(payload);
    }

    private static JwtPayload user(Long userId, String role, Long patientId) {
        return user(userId, List.of(role), role, patientId);
    }

    private static JwtPayload user(Long userId, List<String> roles, String primaryRole, Long patientId) {
        return new JwtPayload(JwtPayload.SubjectType.USER, userId, null, primaryRole,
                roles, primaryRole, patientId, null, null, "U" + userId,
                List.of("*"), "jti-" + userId, Long.MAX_VALUE);
    }

    private static JwtPayload serviceIdentity(String serviceCode) {
        return new JwtPayload(JwtPayload.SubjectType.SERVICE, null, serviceCode, serviceCode,
                List.of(), null, null, null, null, null,
                List.of("*"), "jti-" + serviceCode, Long.MAX_VALUE);
    }

    private static AiProperties properties(AiProperties.FileStorageProperties storage) {
        return new AiProperties(null, null, null, null, null, null, null, storage, null, null, null);
    }

    private static AiProperties.FileStorageProperties storage(String endpoint, String publicEndpoint,
                                                               String objectPrefix, String chunkPrefix,
                                                               boolean autoCreateBucket) {
        return new AiProperties.FileStorageProperties(
                endpoint, publicEndpoint, "access-key", "secret-key", "us-east-1", "medical-images",
                objectPrefix, chunkPrefix, autoCreateBucket, 300);
    }

    private static MockMultipartFile file(String originalFilename, String contentType, String content) {
        return new MockMultipartFile("file", originalFilename, contentType, content.getBytes());
    }

    private static Stream<Arguments> invalidChunkCoordinates() {
        return Stream.of(
                Arguments.of(null, 1),
                Arguments.of(0, null),
                Arguments.of(-1, 1),
                Arguments.of(0, 0),
                Arguments.of(2, 2)
        );
    }

    private static String uploadIdForUser(long userId, Long patientId, char randomHex) {
        return "UP-" + ownerNamespace("USER:" + userId + ":PATIENT:" + ownerValue(patientId))
                + "-" + String.valueOf(randomHex).repeat(32);
    }

    private static String uploadIdForService(String serviceCode, Long patientId, char randomHex) {
        return "UP-" + ownerNamespace("SERVICE:" + serviceCode + ":PATIENT:" + ownerValue(patientId))
                + "-" + String.valueOf(randomHex).repeat(32);
    }

    private static String ownerValue(Long patientId) {
        return patientId == null ? "NONE" : patientId.toString();
    }

    private static String ownerNamespace(String actor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(actor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }

    private static AiFileUploadEntity completedEntity() {
        AiFileUploadEntity entity = new AiFileUploadEntity();
        entity.setFileNo("FILE-1");
        entity.setPatientId(42L);
        entity.setUploadedByUserId(100L);
        entity.setFileUrl("minio://medical-images/imaging/scan.png");
        entity.setFileSize(123L);
        entity.setFileType("image/png");
        entity.setStorageType("MINIO");
        entity.setBucket("medical-images");
        entity.setObjectKey("imaging/scan.png");
        entity.setOriginalFilename("scan.png");
        return entity;
    }
}
