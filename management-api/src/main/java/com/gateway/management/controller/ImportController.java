package com.gateway.management.controller;

import com.gateway.common.auth.RequiresPermission;
import com.gateway.management.dto.ApiResponse;
import com.gateway.management.dto.ImportApiRequest;
import com.gateway.management.dto.ImportApiResult;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.service.ApiService;
import com.gateway.management.service.ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final ApiService apiService;

    /**
     * Preview an import without creating anything.
     * Accepts JSON body with content or URL.
     */
    @PostMapping("/preview")
    @RequiresPermission("api:create")
    public ResponseEntity<ImportApiResult> preview(@RequestBody ImportApiRequest request) {
        String content = resolveContent(request);
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(
                    ImportApiResult.builder().build()
            );
        }

        String format = request.getFormat();
        if (format == null || "AUTO".equalsIgnoreCase(format)) {
            format = importService.detectFormat(content, null);
        }

        ImportApiResult result = importService.parse(content, format);
        return ResponseEntity.ok(result);
    }

    /**
     * Preview from file upload.
     */
    @PostMapping(value = "/preview/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("api:create")
    public ResponseEntity<ImportApiResult> previewFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "AUTO") String format) throws IOException {

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String filename = file.getOriginalFilename();

        if ("AUTO".equalsIgnoreCase(format)) {
            format = importService.detectFormat(content, filename);
        }

        ImportApiResult result = importService.parse(content, format);
        return ResponseEntity.ok(result);
    }

    /**
     * Import and create the API (from JSON body).
     */
    @PostMapping
    @RequiresPermission("api:create")
    public ResponseEntity<ApiResponse> importApi(@RequestBody ImportApiRequest request) {
        String content = resolveContent(request);
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String format = request.getFormat();
        if (format == null || "AUTO".equalsIgnoreCase(format)) {
            format = importService.detectFormat(content, null);
        }

        ImportApiResult result = importService.parse(content, format);
        ApiEntity api = importService.importApi(result, request.getSensitivity(), request.getCategory());

        ApiResponse response = apiService.getApi(api.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Import from file upload.
     */
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("api:create")
    public ResponseEntity<ApiResponse> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "AUTO") String format,
            @RequestParam(value = "sensitivity", defaultValue = "LOW") String sensitivity,
            @RequestParam(value = "category", required = false) String category) throws IOException {

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String filename = file.getOriginalFilename();

        if ("AUTO".equalsIgnoreCase(format)) {
            format = importService.detectFormat(content, filename);
        }

        ImportApiResult result = importService.parse(content, format);
        ApiEntity api = importService.importApi(result, sensitivity, category);

        ApiResponse response = apiService.getApi(api.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Detect format of content.
     */
    @PostMapping("/detect")
    public ResponseEntity<Map<String, String>> detectFormat(@RequestBody Map<String, String> body) {
        String content = body.getOrDefault("content", "");
        String filename = body.getOrDefault("filename", null);
        String format = importService.detectFormat(content, filename);
        return ResponseEntity.ok(Map.of("format", format));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private String resolveContent(ImportApiRequest request) {
        // Content provided directly
        if (request.getContent() != null && !request.getContent().isBlank()) {
            return request.getContent();
        }

        // Fetch from URL
        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(request.getUrl()))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json, application/yaml, text/plain, */*")
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                log.warn("Failed to fetch URL {}: status={}", request.getUrl(), response.statusCode());
            } catch (Exception e) {
                log.warn("Failed to fetch URL {}: {}", request.getUrl(), e.getMessage());
            }
        }

        return null;
    }
}
