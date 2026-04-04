package com.gateway.identity.service;

import com.gateway.identity.dto.*;
import com.gateway.identity.entity.PermissionEntity;
import com.gateway.identity.entity.RoleAssignmentEntity;
import com.gateway.identity.entity.RoleEntity;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.enums.ScopeType;
import com.gateway.identity.repository.OrganizationRepository;
import com.gateway.identity.repository.PermissionRepository;
import com.gateway.identity.repository.RoleAssignmentRepository;
import com.gateway.identity.repository.RoleRepository;
import com.gateway.identity.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private RoleAssignmentRepository roleAssignmentRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private PermissionEvaluator permissionEvaluator;
    @Mock private AuditService auditService;

    @InjectMocks
    private RbacService rbacService;

    private UUID roleId;
    private UUID userId;
    private RoleEntity role;
    private UserEntity user;
    private PermissionEntity permission1;
    private PermissionEntity permission2;

    @BeforeEach
    void setUp() {
        roleId = UUID.randomUUID();
        userId = UUID.randomUUID();

        permission1 = PermissionEntity.builder()
                .id(UUID.randomUUID())
                .resource("api")
                .action("read")
                .description("Read APIs")
                .build();

        permission2 = PermissionEntity.builder()
                .id(UUID.randomUUID())
                .resource("api")
                .action("write")
                .description("Write APIs")
                .build();

        role = RoleEntity.builder()
                .id(roleId)
                .name("API_VIEWER")
                .description("Can view APIs")
                .scopeType(ScopeType.GLOBAL)
                .isSystem(false)
                .permissions(new HashSet<>(Set.of(permission1)))
                .createdAt(Instant.now())
                .build();

        user = UserEntity.builder()
                .id(userId)
                .email("user@example.com")
                .build();
    }

    // ── createRole tests ────────────────────────────────────────────────

    @Test
    void createRole_success_savesRoleWithPermissions() {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("NEW_ROLE")
                .description("A new role")
                .scopeType(ScopeType.GLOBAL)
                .permissionIds(List.of(permission1.getId(), permission2.getId()))
                .build();

        when(roleRepository.findByName("NEW_ROLE")).thenReturn(Optional.empty());
        when(permissionRepository.findAllById(request.getPermissionIds()))
                .thenReturn(List.of(permission1, permission2));
        when(roleRepository.save(any(RoleEntity.class))).thenAnswer(inv -> {
            RoleEntity r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(Instant.now());
            return r;
        });

        RoleResponse result = rbacService.createRole(request);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("NEW_ROLE");
        assertThat(result.getDescription()).isEqualTo("A new role");
        assertThat(result.getPermissions()).hasSize(2);

        ArgumentCaptor<RoleEntity> captor = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getIsSystem()).isFalse();
        assertThat(captor.getValue().getScopeType()).isEqualTo(ScopeType.GLOBAL);
    }

    @Test
    void createRole_duplicateName_throwsConflict() {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("API_VIEWER")
                .scopeType(ScopeType.GLOBAL)
                .build();

        when(roleRepository.findByName("API_VIEWER")).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> rbacService.createRole(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createRole_missingPermissions_throwsEntityNotFound() {
        UUID missingPermId = UUID.randomUUID();
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("ROLE_X")
                .scopeType(ScopeType.GLOBAL)
                .permissionIds(List.of(permission1.getId(), missingPermId))
                .build();

        when(roleRepository.findByName("ROLE_X")).thenReturn(Optional.empty());
        when(permissionRepository.findAllById(request.getPermissionIds()))
                .thenReturn(List.of(permission1)); // only 1 found out of 2

        assertThatThrownBy(() -> rbacService.createRole(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Permissions not found");
    }

    // ── listRoles tests ─────────────────────────────────────────────────

    @Test
    void listRoles_returnsAllRoles() {
        RoleEntity role2 = RoleEntity.builder()
                .id(UUID.randomUUID())
                .name("ADMIN")
                .scopeType(ScopeType.GLOBAL)
                .isSystem(true)
                .permissions(new HashSet<>())
                .createdAt(Instant.now())
                .build();

        when(roleRepository.findAll()).thenReturn(List.of(role, role2));

        List<RoleResponse> result = rbacService.listRoles();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RoleResponse::getName)
                .containsExactlyInAnyOrder("API_VIEWER", "ADMIN");
    }

    // ── getRole tests ───────────────────────────────────────────────────

    @Test
    void getRole_existingRole_returnsResponse() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        RoleResponse result = rbacService.getRole(roleId);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("API_VIEWER");
        assertThat(result.getPermissions()).hasSize(1);
    }

    @Test
    void getRole_notFound_throwsEntityNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(roleRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rbacService.getRole(unknownId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Role not found");
    }

    // ── deleteRole tests ────────────────────────────────────────────────

    @Test
    void deleteRole_noAssignments_deletesRole() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleAssignmentRepository.findAll()).thenReturn(List.of());

        rbacService.deleteRole(roleId);

        verify(roleRepository).delete(role);
        verify(auditService).logEvent(eq("role.deleted"), eq("ROLE"), eq(roleId.toString()), eq("SUCCESS"));
    }

    @Test
    void deleteRole_systemRole_throwsBadRequest() {
        role.setIsSystem(true);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> rbacService.deleteRole(roleId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("system role");
    }

    @Test
    void deleteRole_withActiveAssignments_throwsConflict() {
        RoleAssignmentEntity assignment = RoleAssignmentEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .role(role)
                .scopeType(ScopeType.GLOBAL)
                .build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleAssignmentRepository.findAll()).thenReturn(List.of(assignment));

        assertThatThrownBy(() -> rbacService.deleteRole(roleId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active assignment");
    }

    // ── assignRole tests ────────────────────────────────────────────────

    @Test
    void assignRole_globalScope_createsAssignment() {
        AssignRoleRequest request = new AssignRoleRequest();
        request.setRoleId(roleId);
        request.setScopeType(ScopeType.GLOBAL);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleAssignmentRepository.save(any(RoleAssignmentEntity.class))).thenAnswer(inv -> {
            RoleAssignmentEntity a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        RoleAssignmentResponse result = rbacService.assignRole(userId, request);

        assertThat(result).isNotNull();
        assertThat(result.getRoleId()).isEqualTo(roleId);
        assertThat(result.getRoleName()).isEqualTo("API_VIEWER");
        assertThat(result.getScopeType()).isEqualTo(ScopeType.GLOBAL);

        verify(permissionEvaluator).invalidateCache(userId);
    }

    @Test
    void assignRole_userNotFound_throwsEntityNotFound() {
        UUID unknownUserId = UUID.randomUUID();
        when(userRepository.findById(unknownUserId)).thenReturn(Optional.empty());

        AssignRoleRequest request = new AssignRoleRequest();
        request.setRoleId(roleId);
        request.setScopeType(ScopeType.GLOBAL);

        assertThatThrownBy(() -> rbacService.assignRole(unknownUserId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ── revokeRole tests ────────────────────────────────────────────────

    @Test
    void revokeRole_success_deletesAssignment() {
        UUID assignmentId = UUID.randomUUID();
        RoleAssignmentEntity assignment = RoleAssignmentEntity.builder()
                .id(assignmentId)
                .user(user)
                .role(role)
                .scopeType(ScopeType.GLOBAL)
                .build();

        when(roleAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        rbacService.revokeRole(userId, assignmentId);

        verify(roleAssignmentRepository).delete(assignment);
        verify(permissionEvaluator).invalidateCache(userId);
        verify(auditService).logEvent(eq("role.revoked"), eq("ROLE_ASSIGNMENT"),
                eq(assignmentId.toString()), eq("SUCCESS"));
    }

    @Test
    void revokeRole_assignmentNotFound_throwsEntityNotFound() {
        UUID assignmentId = UUID.randomUUID();
        when(roleAssignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rbacService.revokeRole(userId, assignmentId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Role assignment not found");
    }

    @Test
    void revokeRole_assignmentBelongsToDifferentUser_throwsBadRequest() {
        UUID assignmentId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserEntity otherUser = UserEntity.builder().id(otherUserId).build();

        RoleAssignmentEntity assignment = RoleAssignmentEntity.builder()
                .id(assignmentId)
                .user(otherUser)
                .role(role)
                .scopeType(ScopeType.GLOBAL)
                .build();

        when(roleAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> rbacService.revokeRole(userId, assignmentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not belong to user");
    }

    // ── getEffectivePermissions tests ───────────────────────────────────

    @Test
    void getEffectivePermissions_returnsPermissionsAndAssignments() {
        Set<String> permissions = Set.of("api:read", "api:write");
        when(permissionEvaluator.getEffectivePermissions(userId)).thenReturn(permissions);
        when(roleAssignmentRepository.findActiveByUserId(userId)).thenReturn(List.of());

        EffectivePermissionsResponse result = rbacService.getEffectivePermissions(userId);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getPermissions()).containsExactlyInAnyOrder("api:read", "api:write");
        assertThat(result.getRoleAssignments()).isEmpty();
    }
}
