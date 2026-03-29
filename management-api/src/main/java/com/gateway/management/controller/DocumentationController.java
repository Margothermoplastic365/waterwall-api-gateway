package com.gateway.management.controller;

import com.gateway.management.dto.CreateDocPageRequest;
import com.gateway.management.dto.DocPageResponse;
import com.gateway.management.service.DocumentationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/docs")
@RequiredArgsConstructor
public class DocumentationController {

    private final DocumentationService documentationService;

    @PostMapping("/{apiId}/pages")
    public ResponseEntity<DocPageResponse> createPage(@PathVariable UUID apiId,
                                                       @Valid @RequestBody CreateDocPageRequest request) {
        DocPageResponse response = documentationService.createPage(apiId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{apiId}/pages")
    public ResponseEntity<List<DocPageResponse>> listPages(@PathVariable UUID apiId,
                                                            @RequestParam(required = false) String version) {
        return ResponseEntity.ok(documentationService.listPages(apiId, version));
    }

    @GetMapping("/pages/{id}")
    public ResponseEntity<DocPageResponse> getPage(@PathVariable UUID id) {
        return ResponseEntity.ok(documentationService.getPage(id));
    }

    @PutMapping("/pages/{id}")
    public ResponseEntity<DocPageResponse> updatePage(@PathVariable UUID id,
                                                       @Valid @RequestBody CreateDocPageRequest request) {
        return ResponseEntity.ok(documentationService.updatePage(id, request));
    }

    @DeleteMapping("/pages/{id}")
    public ResponseEntity<Void> deletePage(@PathVariable UUID id) {
        documentationService.deletePage(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pages/{id}/feedback")
    public ResponseEntity<Void> feedback(@PathVariable UUID id,
                                          @RequestParam String vote) {
        documentationService.feedback(id, vote);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<DocPageResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(documentationService.search(q));
    }
}
