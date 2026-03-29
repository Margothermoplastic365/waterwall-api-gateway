package com.gateway.management.controller;

import com.gateway.management.dto.SdkResponse;
import com.gateway.management.service.SdkGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sdks")
@RequiredArgsConstructor
public class SdkController {

    private final SdkGenerationService sdkGenerationService;

    @GetMapping("/languages")
    public ResponseEntity<List<String>> supportedLanguages() {
        return ResponseEntity.ok(sdkGenerationService.supportedLanguages());
    }

    @PostMapping("/generate/{apiId}")
    public ResponseEntity<SdkResponse> generateSdk(@PathVariable UUID apiId,
                                                     @RequestParam(defaultValue = "curl") String language) {
        SdkResponse response = sdkGenerationService.generateSdk(apiId, language);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{apiId}")
    public ResponseEntity<byte[]> downloadSdk(@PathVariable UUID apiId,
                                               @RequestParam(defaultValue = "curl") String language) {
        byte[] data = sdkGenerationService.downloadSdk(apiId, language);
        String filename = "sdk-" + language + "-" + apiId + ".zip";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(data);
    }
}
