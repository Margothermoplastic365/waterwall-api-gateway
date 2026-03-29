package com.gateway.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic offset-based paginated response.
 * <p>
 * Matches the format specified in Section 10.3 of the API Gateway Requirements:
 * <pre>
 * {
 *   "content": [...],
 *   "totalElements": 142,
 *   "totalPages": 8,
 *   "page": 0,
 *   "size": 20
 * }
 * </pre>
 *
 * @param <T> the type of elements in the page
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageResponse<T> {

    private List<T> content;

    private long totalElements;

    private int totalPages;

    private int page;

    private int size;

    /**
     * Creates a {@link PageResponse} from a Spring Data {@link Page}.
     *
     * @param springPage the Spring Data page
     * @param <T>        the element type
     * @return a {@link PageResponse} populated from the given page
     */
    public static <T> PageResponse<T> from(Page<T> springPage) {
        return PageResponse.<T>builder()
                .content(springPage.getContent())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .build();
    }
}
