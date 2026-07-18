package com.medconsult.ai.client;

import com.medconsult.ai.config.AiProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Component
public class MedicalImageFetcher {
    private static final int MAX_IMAGES = 8;
    private final HttpClient httpClient;
    private final AiProperties properties;
    private final List<Origin> allowedOrigins;
    private final MinioClient minioClient;

    @Autowired
    public MedicalImageFetcher(AiProperties properties) {
        this(properties, configuredMinioClient(properties));
    }

    public MedicalImageFetcher(AiProperties properties, MinioClient minioClient) {
        this.properties = properties;
        this.allowedOrigins = configuredStorageOrigins(properties == null ? null : properties.fileStorage());
        this.minioClient = minioClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public List<MedicalImagePayload> fetch(List<String> imageUrls) {
        validateImageCount(imageUrls);
        List<MedicalImagePayload> payloads = new ArrayList<>();
        for (String imageUrl : imageUrls == null ? List.<String>of() : imageUrls) {
            if (!StringUtils.hasText(imageUrl)) {
                continue;
            }
            payloads.add(fetchOne(imageUrl));
        }
        return payloads;
    }

    public void validateSources(List<String> imageLocators) {
        validateImageCount(imageLocators);
        if (imageLocators == null || imageLocators.isEmpty()) {
            throw new IllegalArgumentException("image sources are required");
        }
        for (String locator : imageLocators) {
            if (!StringUtils.hasText(locator)) {
                throw new IllegalArgumentException("image source must not be blank");
            }
            URI uri = parseUri(locator);
            if (!"minio".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("image source must be a canonical minio locator: " + locator);
            }
            parseMinioLocation(uri);
        }
    }

    public void validateLegacyHttpSources(List<String> imageUrls) {
        validateImageCount(imageUrls);
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new IllegalArgumentException("image sources are required");
        }
        for (String imageUrl : imageUrls) {
            if (!StringUtils.hasText(imageUrl)) {
                throw new IllegalArgumentException("image source must not be blank");
            }
            URI uri = parseUri(imageUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException("legacy image source must be an http(s) url: " + imageUrl);
            }
            guardSsrf(uri);
            guardConfiguredStorageOrigin(uri);
        }
    }

    private MedicalImagePayload fetchOne(String imageUrl) {
        URI uri = parseUri(imageUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        return switch (scheme) {
            case "minio" -> fetchMinio(uri);
            case "http", "https" -> fetchHttp(uri);
            default -> throw new IllegalArgumentException("image url must be OSS/MinIO http(s) url: " + imageUrl);
        };
    }

    private MedicalImagePayload fetchMinio(URI uri) {
        MinioLocation location = parseMinioLocation(uri);
        if (minioClient == null) {
            throw new IllegalStateException("MINIO client is not configured");
        }
        try (GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(location.bucket())
                .object(location.objectKey())
                .build())) {
            byte[] bytes = readBounded(response);
            String contentType = guessContentType(location.objectKey());
            return new MedicalImagePayload(uri.toString(), contentType, bytes.length, dataUrl(contentType, bytes));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("image fetch failed: " + uri, ex);
        }
    }

    /**
     * SSRF 防护：拒绝云元数据 / 链路本地地址，防止服务端伪造请求（SSRF）打内网元数据服务。
     * 注：合法的 MinIO 可能就在 127.0.0.1 / localhost，故不拦截回环，只拦链路本地段（169.254.x.x）。
     */
    private static void guardSsrf(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return;
        }
        String lower = host.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("169.254.169.254") || lower.equals("metadata.google.internal")
                || lower.startsWith("169.254.") || lower.endsWith(".metadata")) {
            throw new IllegalArgumentException("blocked SSRF target host: " + host);
        }
    }

    private MedicalImagePayload fetchHttp(URI uri) {
        guardSsrf(uri);
        guardConfiguredStorageOrigin(uri);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(timeoutSeconds()))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("image fetch failed: HTTP " + response.statusCode());
                }
                byte[] bytes = readBounded(body);
                String contentType = response.headers().firstValue("content-type").orElse(guessContentType(uri.getPath()));
                return new MedicalImagePayload(uri.toString(), contentType, bytes.length, dataUrl(contentType, bytes));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("image fetch failed: " + redactedSource(uri), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("image fetch interrupted: " + redactedSource(uri), ex);
        }
    }

    private void guardConfiguredStorageOrigin(URI uri) {
        Origin requestedOrigin = Origin.from(uri);
        if (requestedOrigin == null || !allowedOrigins.contains(requestedOrigin)) {
            throw new IllegalArgumentException("image url must match a configured file storage origin: " + uri);
        }
    }

    private static List<Origin> configuredStorageOrigins(AiProperties.FileStorageProperties storage) {
        if (storage == null) {
            return List.of();
        }
        List<Origin> origins = new ArrayList<>(2);
        addConfiguredOrigin(origins, storage.endpoint());
        addConfiguredOrigin(origins, storage.publicEndpoint());
        return List.copyOf(origins);
    }

    private static void addConfiguredOrigin(List<Origin> origins, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        Origin origin = Origin.from(URI.create(value.trim()));
        if (origin != null && !origins.contains(origin)) {
            origins.add(origin);
        }
    }

    private byte[] readBounded(InputStream input) throws IOException {
        long maxBytes = maxBytesPerImage();
        if (maxBytes >= Integer.MAX_VALUE) {
            throw new IllegalStateException("image max bytes must be less than " + Integer.MAX_VALUE);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        long total = 0;
        while (total <= maxBytes) {
            int remaining = (int) Math.min(buffer.length, maxBytes + 1 - total);
            int read = input.read(buffer, 0, remaining);
            if (read < 0) {
                return output.toByteArray();
            }
            output.write(buffer, 0, read);
            total += read;
            if (total > maxBytes) {
                throw new IllegalStateException("image exceeds max bytes: " + total);
            }
        }
        throw new IllegalStateException("image exceeds max bytes: " + total);
    }

    private static String dataUrl(String contentType, byte[] bytes) {
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private long maxBytesPerImage() {
        AiProperties.VisionProperties vision = properties.vision();
        return vision == null || vision.maxBytesPerImage() <= 0 ? 15L * 1024 * 1024 : vision.maxBytesPerImage();
    }

    private int timeoutSeconds() {
        AiProperties.VisionProperties vision = properties.vision();
        return vision == null || vision.timeoutSeconds() <= 0 ? 30 : vision.timeoutSeconds();
    }

    private static String guessContentType(String value) {
        String lower = value == null ? "" : value.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".dcm") || lower.endsWith(".dicom")) {
            return "application/dicom";
        }
        return "image/jpeg";
    }

    private MinioLocation parseMinioLocation(URI uri) {
        AiProperties.FileStorageProperties storage = properties == null ? null : properties.fileStorage();
        String configuredBucket = storage == null ? null : storage.bucket();
        String rawAuthority = uri.getRawAuthority();
        String rawPath = uri.getRawPath();
        boolean trusted = StringUtils.hasText(configuredBucket)
                && configuredBucket.equals(rawAuthority)
                && uri.getUserInfo() == null
                && uri.getPort() < 0
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null
                && StringUtils.hasText(rawPath)
                && rawPath.startsWith("/")
                && rawPath.length() > 1;
        if (!trusted) {
            throw new IllegalArgumentException("untrusted minio locator: " + uri);
        }
        String objectKey = rawPath.substring(1);
        if (objectKey.startsWith("/") || objectKey.contains("\\") || containsParentSegment(objectKey)) {
            throw new IllegalArgumentException("untrusted minio locator: " + uri);
        }
        return new MinioLocation(configuredBucket, objectKey);
    }

    private static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid image source: " + value, ex);
        }
    }

    private static String redactedSource(URI uri) {
        if (uri == null || !StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            return "[redacted-image-source]";
        }
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    uri.getPath(), null, null).toString();
        } catch (URISyntaxException ex) {
            return "[redacted-image-source]";
        }
    }

    private static void validateImageCount(List<String> sources) {
        if (sources != null && sources.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("at most 8 images are allowed");
        }
    }

    private static boolean containsParentSegment(String objectKey) {
        for (String segment : objectKey.split("/", -1)) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static MinioClient configuredMinioClient(AiProperties properties) {
        AiProperties.FileStorageProperties storage = properties == null ? null : properties.fileStorage();
        if (storage == null || !StringUtils.hasText(storage.endpoint())
                || !StringUtils.hasText(storage.accessKey()) || !StringUtils.hasText(storage.secretKey())) {
            return null;
        }
        String region = StringUtils.hasText(storage.region()) ? storage.region().trim() : "us-east-1";
        return MinioClient.builder()
                .endpoint(storage.endpoint().trim())
                .region(region)
                .credentials(storage.accessKey(), storage.secretKey())
                .build();
    }

    private record Origin(String scheme, String host, int port) {
        private static Origin from(URI uri) {
            if (uri == null || !StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            return new Origin(scheme, uri.getHost().toLowerCase(Locale.ROOT), port);
        }
    }

    private record MinioLocation(String bucket, String objectKey) {
    }

    public record MedicalImagePayload(String sourceUrl, String contentType, int byteSize, String dataUrl) {
    }
}
