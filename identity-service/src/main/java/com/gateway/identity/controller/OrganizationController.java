package com.gateway.identity.controller;

import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.common.auth.RequiresPermission;
import com.gateway.identity.dto.*;
import com.gateway.identity.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/orgs")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<OrgResponse> createOrg(
            GatewayAuthentication authentication,
            @Valid @RequestBody CreateOrgRequest request) {
        UUID userId = UUID.fromString(authentication.getUserId());
        OrgResponse response = organizationService.createOrg(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrgResponse>> listOrgs() {
        return ResponseEntity.ok(organizationService.listOrgs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrgResponse> getOrg(@PathVariable UUID id) {
        return ResponseEntity.ok(organizationService.getOrg(id));
    }

    @PutMapping("/{id}")
    @RequiresPermission("org:update")
    public ResponseEntity<OrgResponse> updateOrg(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrgRequest request) {
        return ResponseEntity.ok(organizationService.updateOrg(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("org:delete")
    public ResponseEntity<Void> deleteOrg(@PathVariable UUID id) {
        organizationService.deleteOrg(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<OrgMemberResponse>> listMembers(@PathVariable UUID id) {
        return ResponseEntity.ok(organizationService.listMembers(id));
    }

    @PostMapping("/{id}/invitations")
    @RequiresPermission("org:invite")
    public ResponseEntity<InvitationResponse> inviteMember(
            @PathVariable UUID id,
            @Valid @RequestBody InviteMemberRequest request) {
        InvitationResponse response = organizationService.inviteMember(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<Void> acceptInvitation(@RequestParam String token) {
        organizationService.acceptInvitation(token);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    @RequiresPermission("org:update")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        organizationService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/transfer-ownership")
    @RequiresPermission("org:update")
    public ResponseEntity<Void> transferOwnership(
            @PathVariable UUID id,
            @RequestParam UUID newOwnerId) {
        organizationService.transferOwnership(id, newOwnerId);
        return ResponseEntity.ok().build();
    }
}
