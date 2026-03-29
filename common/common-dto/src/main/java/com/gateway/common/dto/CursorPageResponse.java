package com.gateway.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Generic cursor-based paginated response.
 * <p>
 * Matches the format specified in Section 10.3 of the API Gateway Requirements:
 * <pre>
 * {
 *   "content": [...],
 *   "next_cursor": "eyJpZCI6MTIzfQ",
 *   "has_next": true,
 *   "has_previous": false
 * }
 * </pre>
 *
 * @param <T> the type of elements in the page
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CursorPageResponse<T> {

    private List<T> content;

    @JsonProperty("next_cursor")
    private String nextCursor;

    @JsonProperty("has_next")
    private boolean hasNext;

    @JsonProperty("has_previous")
    private boolean hasPrevious;
}
