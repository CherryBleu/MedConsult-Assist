package com.medconsult.ai.client;

import com.medconsult.ai.config.AiProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class MedicalImageFetcher {
    private final HttpClient httpClient;
    private final AiProperties properties;

    public MedicalImageFetcher(AiProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<MedicalImagePayload> fetch(List<String> imageUrls) {
        List<MedicalImagePayload> payloads = new ArrayList<>();
        for (String imageUrl : imageUrls == null ? List.<String>of() : imageUrls) {
            if (!StringUtils.hasText(imageUrl)) {
                continue;
            }
            payloads.add(fetchOne(imageUrl));
        }
        return payloads;
    }

    private MedicalImagePayload fetchOne(String imageUrl) {
        URI uri = URI.create(imageUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        return switch (scheme) {
            case "http", "https" -> fetchHttp(uri);
            default -> throw new IllegalArgumentException("image url must be OSS/MinIO http(s) url: " + imageUrl);
        };
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
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(timeoutSeconds()))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("image fetch failed: HTTP " + response.statusCode());
            }
            byte[] bytes = limit(response.body());
            String contentType = response.headers().firstValue("content-type").orElse(guessContentType(uri.getPath()));
            return new MedicalImagePayload(uri.toString(), contentType, bytes.length, dataUrl(contentType, bytes));
        } catch (IOException ex) {
            throw new IllegalStateException("image fetch failed: " + uri, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("image fetch interrupted: " + uri, ex);
        }
    }

    private byte[] limit(byte[] bytes) {
        if (bytes.length > maxBytesPerImage()) {
            throw new IllegalStateException("image exceeds max bytes: " + bytes.length);
        }
        return bytes;
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

    public record MedicalImagePayload(String sourceUrl, String contentType, int byteSize, String dataUrl) {
    }
}
