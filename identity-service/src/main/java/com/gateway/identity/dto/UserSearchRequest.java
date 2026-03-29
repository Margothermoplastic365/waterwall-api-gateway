package com.gateway.identity.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UserSearchRequest {

    /** Search by email or display name (partial match). */
    private String search;

    /** Filter by user status (e.g., ACTIVE, SUSPENDED). */
    private String status;

    /** Filter by organization ID. */
    private UUID orgId;

    /** Page number (zero-based). */
    private int page = 0;

    /** Page size. */
    private int size = 20;

    /** Sort field. */
    private String sortBy = "createdAt";

    /** Sort direction: "asc" or "desc". */
    private String sortDir = "desc";
}
