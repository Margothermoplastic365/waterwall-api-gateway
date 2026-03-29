package com.gateway.management.controller;

import com.gateway.management.dto.ConfigDiffResponse;
import com.gateway.management.service.ConfigExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/config")
@RequiredArgsConstructor
public class ConfigAsCodeController {

    private final ConfigExportService configExportService;

    @GetMapping(value = "/export/{apiId}", produces = {MediaType.APPLICATION_JSON_VALUE, "application/x-yaml"})
    public ResponseEntity<String> exportApi(@PathVariable UUID apiId,
                                             @RequestParam(defaultValue = "yaml") String format) {
        String result = configExportService.exportConfig(apiId, format);
        String contentType = "json".equalsIgnoreCase(format) ? MediaType.APPLICATION_JSON_VALUE : "application/x-yaml";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(result);
    }

    @GetMapping(value = "/export/all", produces = {MediaType.APPLICATION_JSON_VALUE, "application/x-yaml"})
    public ResponseEntity<String> exportAll(@RequestParam(defaultValue = "yaml") String format) {
        String result = configExportService.exportAll(format);
        String contentType = "json".equalsIgnoreCase(format) ? MediaType.APPLICATION_JSON_VALUE : "application/x-yaml";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(result);
    }

    @PostMapping(value = "/import", consumes = {MediaType.TEXT_PLAIN_VALUE, "application/x-yaml", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Map<String, Object>> importConfig(@RequestBody String yaml) {
        Map<String, Object> result = configExportService.importConfig(yaml);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/diff", consumes = {MediaType.TEXT_PLAIN_VALUE, "application/x-yaml", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ConfigDiffResponse> diff(@RequestBody String yaml) {
        return ResponseEntity.ok(configExportService.diff(yaml));
    }
}
