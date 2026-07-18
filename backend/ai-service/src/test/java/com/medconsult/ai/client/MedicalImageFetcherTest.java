package com.medconsult.ai.client;

import com.medconsult.ai.client.MedicalImageFetcher.MedicalImagePayload;
import com.medconsult.ai.config.AiProperties;
import com.sun.net.httpserver.HttpServer;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MedicalImageFetcherTest {

    @Test
    void fetchShouldReadTrustedMinioLocatorWithConfiguredCredentials() throws Exception {
        byte[] image = {1, 2, 3};
        MinioClient minio = mock(MinioClient.class);
        GetObjectResponse response = streamResponse(image, new AtomicInteger());
        when(minio.getObject(any(GetObjectArgs.class))).thenReturn(response);
        MedicalImageFetcher fetcher = new MedicalImageFetcher(
                properties("http://minio.internal:9000", "", 2, 1024), minio);

        MedicalImagePayload payload = fetcher.fetch(
                List.of("minio://medical/studies/scan.dcm")).getFirst();

        assertEquals("minio://medical/studies/scan.dcm", payload.sourceUrl());
        assertEquals("application/dicom", payload.contentType());
        assertEquals(3, payload.byteSize());
        assertEquals("data:application/dicom;base64,AQID", payload.dataUrl());
        ArgumentCaptor<GetObjectArgs> args = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minio).getObject(args.capture());
        assertEquals("medical", args.getValue().bucket());
        assertEquals("studies/scan.dcm", args.getValue().object());
    }

    @Test
    void fetchShouldRejectUntrustedMinioLocatorFormsBeforeObjectRead() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        MedicalImageFetcher fetcher = new MedicalImageFetcher(
                properties("http://minio.internal:9000", "", 2, 1024), minio);
        List<String> invalid = List.of(
                "minio://other-bucket/study.dcm",
                "minio://user@medical/study.dcm",
                "minio://medical/study.dcm?token=secret",
                "minio://medical/study.dcm#fragment",
                "minio://medical/"
        );

        for (String locator : invalid) {
            assertThrows(IllegalArgumentException.class, () -> fetcher.fetch(List.of(locator)), locator);
        }

        verify(minio, org.mockito.Mockito.never()).getObject(any(GetObjectArgs.class));
    }

    @Test
    void fetchShouldStopReadingMinioAtMaxBytesPlusOne() throws Exception {
        byte[] image = new byte[100];
        MinioClient minio = mock(MinioClient.class);
        AtomicInteger bytesSupplied = new AtomicInteger();
        GetObjectResponse response = streamResponse(image, bytesSupplied);
        when(minio.getObject(any(GetObjectArgs.class))).thenReturn(response);
        MedicalImageFetcher fetcher = new MedicalImageFetcher(
                properties("http://minio.internal:9000", "", 2, 3), minio);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fetcher.fetch(List.of("minio://medical/studies/large.dcm")));

        assertEquals("image exceeds max bytes: 4", error.getMessage());
        assertEquals(4, bytesSupplied.get());
    }

    @Test
    void fetchShouldRejectMoreThanEightImagesBeforeNetworkIo() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        MedicalImageFetcher fetcher = new MedicalImageFetcher(
                properties("http://minio.internal:9000", "", 2, 1024), minio);
        List<String> locators = java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> "minio://medical/studies/" + i + ".dcm")
                .toList();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fetcher.fetch(locators));

        assertEquals("at most 8 images are allowed", error.getMessage());
        verify(minio, org.mockito.Mockito.never()).getObject(any(GetObjectArgs.class));
    }

    @Test
    void fetchShouldDownloadMinioObjectSkipBlankUrlsAndBuildDataUrl() throws Exception {
        byte[] image = "png-bytes".getBytes(StandardCharsets.UTF_8);
        HttpServer server = startServer("/studies/scan.png", 200, null, image);
        try {
            String url = url(server, "/studies/scan.png");
            MedicalImageFetcher fetcher = new MedicalImageFetcher(
                    properties(origin(server), "", 2, 1024));

            List<MedicalImagePayload> payloads = fetcher.fetch(List.of("", "  ", url));

            assertEquals(1, payloads.size());
            MedicalImagePayload payload = payloads.getFirst();
            assertEquals(url, payload.sourceUrl());
            assertEquals("image/png", payload.contentType());
            assertEquals(image.length, payload.byteSize());
            assertEquals("data:image/png;base64,cG5nLWJ5dGVz", payload.dataUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchShouldPreferMinioResponseContentTypeOverFileExtension() throws Exception {
        byte[] image = {1, 2, 3};
        HttpServer server = startServer("/studies/scan.jpg", 200, "application/dicom", image);
        try {
            MedicalImageFetcher fetcher = new MedicalImageFetcher(
                    properties(origin(server), "", 2, 1024));

            MedicalImagePayload payload = fetcher.fetch(List.of(url(server, "/studies/scan.jpg"))).getFirst();

            assertEquals("application/dicom", payload.contentType());
            assertEquals("data:application/dicom;base64,AQID", payload.dataUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchShouldInferWebpDicomAndJpegWhenContentTypeIsMissing() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        addResponse(server, "/one.webp", 200, null, new byte[]{1});
        addResponse(server, "/two.dicom", 200, null, new byte[]{2});
        addResponse(server, "/three.unknown", 200, null, new byte[]{3});
        server.start();
        try {
            MedicalImageFetcher fetcher = new MedicalImageFetcher(
                    properties(origin(server), "", 2, 1024));

            List<MedicalImagePayload> payloads = fetcher.fetch(List.of(
                    url(server, "/one.webp"),
                    url(server, "/two.dicom"),
                    url(server, "/three.unknown")
            ));

            assertEquals(List.of("image/webp", "application/dicom", "image/jpeg"),
                    payloads.stream().map(MedicalImagePayload::contentType).toList());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchShouldRejectObjectLargerThanConfiguredPerImageLimit() throws Exception {
        HttpServer server = startServer("/large.dcm", 200, "application/dicom", new byte[]{1, 2, 3, 4});
        try {
            MedicalImageFetcher fetcher = new MedicalImageFetcher(
                    properties(origin(server), "", 2, 3));

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> fetcher.fetch(List.of(url(server, "/large.dcm"))));

            assertEquals("image exceeds max bytes: 4", error.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchShouldSurfaceMinioHttpFailure() throws Exception {
        HttpServer server = startServer("/missing.jpg", 503, null, new byte[0]);
        try {
            MedicalImageFetcher fetcher = new MedicalImageFetcher(
                    properties(origin(server), "", 2, 1024));

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> fetcher.fetch(List.of(url(server, "/missing.jpg"))));

            assertEquals("image fetch failed: HTTP 503", error.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchShouldWrapConnectionFailureFromMinio() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            unusedPort = socket.getLocalPort();
        }
        MedicalImageFetcher fetcher = new MedicalImageFetcher(properties(
                "http://127.0.0.1:" + unusedPort, "", 1, 1024));
        String url = "http://127.0.0.1:" + unusedPort + "/unavailable.jpg";

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fetcher.fetch(List.of(url)));

        assertEquals("image fetch failed: " + url, error.getMessage());
        assertTrue(error.getCause() instanceof IOException);
    }

    @Test
    void fetchShouldRedactLegacyCredentialsFromConnectionFailureMessage() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            unusedPort = socket.getLocalPort();
        }
        MedicalImageFetcher fetcher = new MedicalImageFetcher(properties(
                "http://127.0.0.1:" + unusedPort, "", 1, 1024));
        String credentialUrl = "http://legacy-user:legacy-password@127.0.0.1:" + unusedPort
                + "/unavailable.jpg?X-Amz-Credential=credential&X-Amz-Signature=secret";

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fetcher.fetch(List.of(credentialUrl)));

        assertEquals("image fetch failed: http://127.0.0.1:" + unusedPort + "/unavailable.jpg",
                error.getMessage());
        assertTrue(error.getCause() instanceof IOException);
        assertTrue(!error.getMessage().contains("legacy-password"));
        assertTrue(!error.getMessage().contains("X-Amz-Signature"));
    }

    @Test
    void fetchShouldRejectUnsupportedStorageSchemeAndMetadataSsrfTargets() {
        MedicalImageFetcher fetcher = new MedicalImageFetcher(properties(
                "http://minio.internal:9000", "", 2, 1024));

        IllegalArgumentException schemeError = assertThrows(IllegalArgumentException.class,
                () -> fetcher.fetch(List.of("s3://medical-bucket/study.dcm")));
        IllegalArgumentException metadataIpError = assertThrows(IllegalArgumentException.class,
                () -> fetcher.fetch(List.of("http://169.254.1.20/latest/meta-data")));
        IllegalArgumentException metadataHostError = assertThrows(IllegalArgumentException.class,
                () -> fetcher.fetch(List.of("https://metadata.google.internal/computeMetadata/v1")));

        assertTrue(schemeError.getMessage().contains("must be OSS/MinIO http(s) url"));
        assertTrue(metadataIpError.getMessage().contains("blocked SSRF target host"));
        assertTrue(metadataHostError.getMessage().contains("blocked SSRF target host"));
    }

    @Test
    void fetchShouldTreatNullInputAsNoImages() {
        MedicalImageFetcher fetcher = new MedicalImageFetcher(propertiesWithNoVisionConfiguration());

        assertTrue(fetcher.fetch(null).isEmpty());
    }

    @Test
    void fetchShouldRejectNonBlankUrlWhenFileStorageOriginsAreNotConfigured() throws Exception {
        HttpServer server = startServer("/study.dcm", 200, "application/dicom", new byte[]{1});
        try {
            MedicalImageFetcher fetcher = new MedicalImageFetcher(propertiesWithNoVisionConfiguration());

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> fetcher.fetch(List.of(url(server, "/study.dcm"))));

            assertTrue(error.getMessage().contains("file storage origin"));
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://10.23.45.67:9000/medical/study.dcm",
            "http://192.168.55.20:9000/medical/study.dcm"
    })
    void fetchShouldRejectPrivateOriginsThatAreNotConfiguredFileStorage(String imageUrl) {
        MedicalImageFetcher fetcher = new MedicalImageFetcher(properties(
                "http://minio.internal:9000", "https://images.example.test", 1, 1024));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fetcher.fetch(List.of(imageUrl)));

        assertTrue(error.getMessage().contains("file storage origin"));
    }

    @Test
    void fetchShouldRejectLocalhostOnPortNotConfiguredForFileStorage() throws Exception {
        HttpServer unconfiguredServer = startServer("/study.dcm", 200, "application/dicom", new byte[]{1});
        try {
            MedicalImageFetcher fetcher = new MedicalImageFetcher(properties(
                    "http://127.0.0.1:9000", "", 2, 1024));

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> fetcher.fetch(List.of("http://localhost:"
                            + unconfiguredServer.getAddress().getPort() + "/study.dcm")));

            assertTrue(error.getMessage().contains("file storage origin"));
        } finally {
            unconfiguredServer.stop(0);
        }
    }

    @Test
    void fetchShouldRejectDifferentPortOnSameConfiguredHost() throws Exception {
        HttpServer unconfiguredServer = startServer("/study.dcm", 200, "application/dicom", new byte[]{1});
        try {
            MedicalImageFetcher fetcher = new MedicalImageFetcher(properties(
                    "http://127.0.0.1:9000", "", 2, 1024));

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> fetcher.fetch(List.of(url(unconfiguredServer, "/study.dcm"))));

            assertTrue(error.getMessage().contains("file storage origin"));
        } finally {
            unconfiguredServer.stop(0);
        }
    }

    @Test
    void fetchShouldRejectDifferentHostNameEvenWhenItResolvesToConfiguredServer() throws Exception {
        HttpServer server = startServer("/study.dcm", 200, "application/dicom", new byte[]{1});
        try {
            MedicalImageFetcher fetcher = new MedicalImageFetcher(properties(
                    origin(server), "", 2, 1024));
            String localhostUrl = "http://localhost:" + server.getAddress().getPort() + "/study.dcm";

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> fetcher.fetch(List.of(localhostUrl)));

            assertTrue(error.getMessage().contains("file storage origin"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchShouldRejectSchemeMismatchEvenWhenHostAndPortMatchConfiguredOrigin() throws Exception {
        HttpServer server = startServer("/study.dcm", 200, "application/dicom", new byte[]{1});
        try {
            MedicalImageFetcher fetcher = new MedicalImageFetcher(properties(
                    origin(server), "", 2, 1024));
            String httpsUrl = "https://127.0.0.1:" + server.getAddress().getPort() + "/study.dcm";

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> fetcher.fetch(List.of(httpsUrl)));

            assertTrue(error.getMessage().contains("file storage origin"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchShouldAllowConfiguredEndpointAndPublicEndpointOrigins() throws Exception {
        HttpServer internal = startServer("/internal.dcm", 200, "application/dicom", new byte[]{1});
        HttpServer publicServer = startServer("/public.dcm", 200, "application/dicom", new byte[]{2});
        try {
            MedicalImageFetcher fetcher = new MedicalImageFetcher(properties(
                    origin(internal), origin(publicServer), 2, 1024));

            List<MedicalImagePayload> payloads = fetcher.fetch(List.of(
                    url(internal, "/internal.dcm"),
                    url(publicServer, "/public.dcm")));

            assertEquals(2, payloads.size());
            assertEquals(List.of("AQ==", "Ag=="), payloads.stream()
                    .map(MedicalImagePayload::dataUrl)
                    .map(value -> value.substring(value.indexOf(',') + 1))
                    .toList());
        } finally {
            internal.stop(0);
            publicServer.stop(0);
        }
    }

    @Test
    void fetchShouldNotFollowRedirectToDifferentOrigin() throws Exception {
        AtomicInteger redirectedTargetHits = new AtomicInteger();
        HttpServer redirectedTarget = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirectedTarget.createContext("/private.dcm", exchange -> {
            redirectedTargetHits.incrementAndGet();
            byte[] body = {9};
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        redirectedTarget.start();
        HttpServer configuredOrigin = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        configuredOrigin.createContext("/redirect.dcm", exchange -> {
            exchange.getResponseHeaders().add("Location", url(redirectedTarget, "/private.dcm"));
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        configuredOrigin.start();
        try {
            MedicalImageFetcher fetcher = new MedicalImageFetcher(properties(
                    origin(configuredOrigin), "", 2, 1024));

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> fetcher.fetch(List.of(url(configuredOrigin, "/redirect.dcm"))));

            assertEquals("image fetch failed: HTTP 302", error.getMessage());
            assertEquals(0, redirectedTargetHits.get());
        } finally {
            configuredOrigin.stop(0);
            redirectedTarget.stop(0);
        }
    }

    private static HttpServer startServer(String path, int status, String contentType, byte[] body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        addResponse(server, path, status, contentType, body);
        server.start();
        return server;
    }

    private static void addResponse(HttpServer server, String path, int status, String contentType, byte[] body) {
        server.createContext(path, exchange -> {
            if (contentType != null) {
                exchange.getResponseHeaders().add("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }

    private static String url(HttpServer server, String path) {
        return origin(server) + path;
    }

    private static String origin(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static AiProperties properties(String endpoint,
                                           String publicEndpoint,
                                           int timeoutSeconds,
                                           long maxBytesPerImage) {
        return new AiProperties(
                null, null, null, null, null, null,
                new AiProperties.VisionProperties("http://vision", "key", "model", timeoutSeconds, maxBytesPerImage),
                new AiProperties.FileStorageProperties(
                        endpoint, publicEndpoint, "access", "secret", "us-east-1",
                        "medical", "imaging", "chunks", false, 300),
                null, null, null
        );
    }

    private static AiProperties propertiesWithNoVisionConfiguration() {
        return new AiProperties(null, null, null, null, null, null, null, null, null, null, null);
    }

    private static GetObjectResponse streamResponse(byte[] bytes, AtomicInteger bytesSupplied) throws Exception {
        GetObjectResponse response = mock(GetObjectResponse.class);
        AtomicInteger offset = new AtomicInteger();
        when(response.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(invocation -> {
            byte[] target = invocation.getArgument(0);
            int targetOffset = invocation.getArgument(1);
            int requested = invocation.getArgument(2);
            int remaining = bytes.length - offset.get();
            if (remaining <= 0) {
                return -1;
            }
            int count = Math.min(requested, remaining);
            System.arraycopy(bytes, offset.get(), target, targetOffset, count);
            offset.addAndGet(count);
            bytesSupplied.addAndGet(count);
            return count;
        });
        return response;
    }
}
