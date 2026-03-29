package com.gateway.identity.service;

import com.gateway.common.events.BaseEvent;
import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RabbitMQExchanges;
import com.gateway.identity.dto.*;
import com.gateway.identity.entity.*;
import com.gateway.identity.entity.enums.OrgStatus;
import com.gateway.identity.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for organization lifecycle, membership, and invitation management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");

    private final OrganizationRepository organizationRepository;
    private final OrgMemberRepository orgMemberRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RoleRepository roleRepository;
    private final EventPublisher eventPublisher;

    // ── Organization CRUD ────────────────────────────────────────────────

    @Transactional
    public OrgResponse createOrg(UUID userId, CreateOrgRequest request) {
        UserEntity owner = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        String slug = generateSlug(request.getName());

        OrganizationEntity org = OrganizationEntity.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .domain(request.getDomain())
                .status(OrgStatus.ACTIVE)
                .build();

        org = organizationRepository.save(org);

        // Add the creator as OWNER member
        OrgMemberEntity ownerMember = OrgMemberEntity.builder()
                .user(owner)
                .organization(org)
                .orgRole("OWNER")
                .joinedAt(Instant.now())
                .build();
        orgMemberRepository.save(ownerMember);

        log.info("Organization created: id={}, name={}, owner={}", org.getId(), org.getName(), userId);
        return toResponse(org, 1);
    }

    @Transactional(readOnly = true)
    public List<OrgResponse> listOrgs() {
        return organizationRepository.findAll().stream()
                .map(org -> {
                    long memberCount = orgMemberRepository.findByOrganizationId(org.getId()).size();
                    return toResponse(org, memberCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public OrgResponse getOrg(UUID orgId) {
        OrganizationEntity org = findOrgOrThrow(orgId);
        long memberCount = orgMemberRepository.findByOrganizationId(orgId).size();
        return toResponse(org, memberCount);
    }

    @Transactional
    public OrgResponse updateOrg(UUID orgId, UpdateOrgRequest request) {
        OrganizationEntity org = findOrgOrThrow(orgId);

        if (request.getName() != null) {
            org.setName(request.getName());
        }
        if (request.getDescription() != null) {
            org.setDescription(request.getDescription());
        }
        if (request.getDomain() != null) {
            org.setDomain(request.getDomain());
        }
        if (request.getLogoUrl() != null) {
            org.setLogoUrl(request.getLogoUrl());
        }

        org = organizationRepository.save(org);
        long memberCount = orgMemberRepository.findByOrganizationId(orgId).size();

        log.info("Organization updated: id={}", orgId);
        return toResponse(org, memberCount);
    }

    @Transactional
    public void deleteOrg(UUID orgId) {
        OrganizationEntity org = findOrgOrThrow(orgId);
        org.setStatus(OrgStatus.DEACTIVATED);
        organizationRepository.save(org);
        log.info("Organization soft-deleted: id={}", orgId);
    }

    // ── Member management ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OrgMemberResponse> listMembers(UUID orgId) {
        findOrgOrThrow(orgId);
        return orgMemberRepository.findByOrganizationId(orgId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional
    public InvitationResponse inviteMember(UUID orgId, InviteMemberRequest request) {
        OrganizationEntity org = findOrgOrThrow(orgId);

        RoleEntity role = null;
        if (request.getRoleId() != null) {
            role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new EntityNotFoundException("Role not found: " + request.getRoleId()));
        }

        String token = UUID.randomUUID().toString();

        InvitationEntity invitation = InvitationEntity.builder()
                .email(request.getEmail())
                .organization(org)
                .role(role)
                .token(token)
                .status("PENDING")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        invitation = invitationRepository.save(invitation);

        // Publish notification event
        publishNotificationEvent(request.getEmail(), org.getName(), token);

        log.info("Invitation created: orgId={}, email={}", orgId, request.getEmail());
        return toInvitationResponse(invitation);
    }

    @Transactional
    public void acceptInvitation(String token) {
        InvitationEntity invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found for token"));

        if (!"PENDING".equals(invitation.getStatus())) {
            throw new IllegalStateException("Invitation is no longer pending; current status: " + invitation.getStatus());
        }

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus("EXPIRED");
            invitationRepository.save(invitation);
            throw new IllegalStateException("Invitation has expired");
        }

        // Find or validate user by email
        UserEntity user = userRepository.findByEmail(invitation.getEmail())
                .orElseThrow(() -> new EntityNotFoundException(
                        "No user account found for email: " + invitation.getEmail()));

        // Prevent duplicate membership
        if (orgMemberRepository.existsByUserIdAndOrganizationId(user.getId(), invitation.getOrganization().getId())) {
            invitation.setStatus("ACCEPTED");
            invitationRepository.save(invitation);
            log.info("User {} is already a member of org {}", user.getId(), invitation.getOrganization().getId());
            return;
        }

        String orgRole = invitation.getRole() != null ? invitation.getRole().getName() : "MEMBER";

        OrgMemberEntity member = OrgMemberEntity.builder()
                .user(user)
                .organization(invitation.getOrganization())
                .orgRole(orgRole)
                .joinedAt(Instant.now())
                .build();
        orgMemberRepository.save(member);

        invitation.setStatus("ACCEPTED");
        invitationRepository.save(invitation);

        log.info("Invitation accepted: userId={}, orgId={}", user.getId(), invitation.getOrganization().getId());
    }

    @Transactional
    public void removeMember(UUID orgId, UUID userId) {
        OrgMemberEntity member = orgMemberRepository.findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Member not found: userId=" + userId + ", orgId=" + orgId));
        orgMemberRepository.delete(member);
        log.info("Member removed: userId={}, orgId={}", userId, orgId);
    }

    @Transactional
    public void transferOwnership(UUID orgId, UUID newOwnerId) {
        findOrgOrThrow(orgId);

        // Demote current owner(s) to ADMIN
        List<OrgMemberEntity> currentOwners = orgMemberRepository.findByOrganizationId(orgId).stream()
                .filter(m -> "OWNER".equals(m.getOrgRole()))
                .toList();
        for (OrgMemberEntity owner : currentOwners) {
            owner.setOrgRole("ADMIN");
            orgMemberRepository.save(owner);
        }

        // Promote new owner
        OrgMemberEntity newOwner = orgMemberRepository.findByUserIdAndOrganizationId(newOwnerId, orgId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "New owner is not a member of the organization: " + newOwnerId));
        newOwner.setOrgRole("OWNER");
        orgMemberRepository.save(newOwner);

        log.info("Ownership transferred: orgId={}, newOwnerId={}", orgId, newOwnerId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private OrganizationEntity findOrgOrThrow(UUID orgId) {
        return organizationRepository.findById(orgId)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found: " + orgId));
    }

    private String generateSlug(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
        String slug = WHITESPACE.matcher(normalized).replaceAll("-");
        slug = NON_LATIN.matcher(slug).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH).replaceAll("-{2,}", "-").replaceAll("^-|-$", "");

        // Ensure uniqueness
        String baseSlug = slug;
        int counter = 1;
        while (organizationRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private void publishNotificationEvent(String email, String orgName, String token) {
        try {
            eventPublisher.publish(
                    RabbitMQExchanges.NOTIFICATIONS,
                    "notification.email",
                    new InvitationEmailEvent(email, orgName, token));
        } catch (Exception ex) {
            log.warn("Failed to publish invitation email event for {}: {}", email, ex.getMessage());
        }
    }

    private OrgResponse toResponse(OrganizationEntity org, long memberCount) {
        return OrgResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .slug(org.getSlug())
                .description(org.getDescription())
                .domain(org.getDomain())
                .logoUrl(org.getLogoUrl())
                .status(org.getStatus())
                .memberCount(memberCount)
                .createdAt(org.getCreatedAt())
                .build();
    }

    private OrgMemberResponse toMemberResponse(OrgMemberEntity member) {
        UserEntity user = member.getUser();
        String displayName = userProfileRepository.findByUserId(user.getId())
                .map(UserProfileEntity::getDisplayName)
                .orElse(null);

        return OrgMemberResponse.builder()
                .id(member.getId())
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(displayName)
                .orgRole(member.getOrgRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private InvitationResponse toInvitationResponse(InvitationEntity invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .email(invitation.getEmail())
                .orgId(invitation.getOrganization().getId())
                .status(invitation.getStatus())
                .expiresAt(invitation.getExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }

    // ── Inner event class ────────────────────────────────────────────────

    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    @lombok.experimental.SuperBuilder
    @lombok.NoArgsConstructor
    private static class InvitationEmailEvent extends BaseEvent {
        private String recipientEmail;
        private String orgName;
        private String invitationToken;

        InvitationEmailEvent(String recipientEmail, String orgName, String invitationToken) {
            super("notification.email", "system", null);
            this.recipientEmail = recipientEmail;
            this.orgName = orgName;
            this.invitationToken = invitationToken;
        }
    }
}
