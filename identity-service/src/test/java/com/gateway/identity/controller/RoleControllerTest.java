package com.gateway.identity.controller;

import com.gateway.identity.dto.*;
import com.gateway.identity.entity.enums.ScopeType;
import com.gateway.identity.service.RbacService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private RbacService rbacService;

    @InjectMocks
    private RoleController roleController;

    private static final UUID ROLE_ID = UUID.randomUUID();

    private RoleResponse sampleRole() {
        return RoleResponse.builder()
                .id(ROLE_ID)
                .name("EDITOR")
                .description("Can edit resources")
                .scopeType(ScopeType.GLOBAL)
                .isSystem(false)
                .createdAt(Instant.now())
                .permissions(List.of())
                .build();
    }

    // ── createRole ──────────────────────────────────────────────────────

    @Test
    void createRole_returnsCreatedWithRoleResponse() {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("EDITOR")
                .description("Can edit resources")
                .scopeType(ScopeType.GLOBAL)
                .permissionIds(List.of())
                .build();

        RoleResponse expected = sampleRole();
        when(rbacService.createRole(request)).thenReturn(expected);

        ResponseEntity<RoleResponse> response = roleController.createRole(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
        verify(rbacService).createRole(request);
    }

    // ── listRoles ───────────────────────────────────────────────────────

    @Test
    void listRoles_returnsOkWithList() {
        List<RoleResponse> expected = List.of(sampleRole());
        when(rbacService.listRoles()).thenReturn(expected);

        ResponseEntity<List<RoleResponse>> response = roleController.listRoles();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(rbacService).listRoles();
    }

    // ── getRole ─────────────────────────────────────────────────────────

    @Test
    void getRole_returnsOkWithRoleResponse() {
        RoleResponse expected = sampleRole();
        when(rbacService.getRole(ROLE_ID)).thenReturn(expected);

        ResponseEntity<RoleResponse> response = roleController.getRole(ROLE_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(rbacService).getRole(ROLE_ID);
    }

    // ── updateRole ──────────────────────────────────────────────────────

    @Test
    void updateRole_returnsOkWithUpdatedRole() {
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setDescription("Updated description");
        request.setPermissionIds(List.of(UUID.randomUUID()));

        RoleResponse expected = sampleRole();
        when(rbacService.updateRole(ROLE_ID, request)).thenReturn(expected);

        ResponseEntity<RoleResponse> response = roleController.updateRole(ROLE_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(rbacService).updateRole(ROLE_ID, request);
    }

    // ── deleteRole ──────────────────────────────────────────────────────

    @Test
    void deleteRole_returnsNoContent() {
        ResponseEntity<Void> response = roleController.deleteRole(ROLE_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(rbacService).deleteRole(ROLE_ID);
    }

    // ── listPermissions ─────────────────────────────────────────────────

    @Test
    void listPermissions_returnsOkWithList() {
        PermissionResponse perm = PermissionResponse.builder()
                .id(UUID.randomUUID())
                .resource("api")
                .action("read")
                .description("Read API resources")
                .build();

        when(rbacService.listPermissions()).thenReturn(List.of(perm));

        ResponseEntity<List<PermissionResponse>> response = roleController.listPermissions();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getResource()).isEqualTo("api");
        verify(rbacService).listPermissions();
    }
}
