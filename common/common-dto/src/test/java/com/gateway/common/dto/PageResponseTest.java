package com.gateway.common.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void shouldCreateFromSpringPage() {
        List<String> items = List.of("alpha", "beta", "gamma");
        Page<String> springPage = new PageImpl<>(items, PageRequest.of(2, 10), 53);

        PageResponse<String> response = PageResponse.from(springPage);

        assertThat(response.getContent()).containsExactly("alpha", "beta", "gamma");
        assertThat(response.getTotalElements()).isEqualTo(53);
        assertThat(response.getTotalPages()).isEqualTo(6); // ceil(53/10)
        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(10);
    }

    @Test
    void shouldCreateFromEmptyPage() {
        Page<String> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);

        PageResponse<String> response = PageResponse.from(emptyPage);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
    }

    @Test
    void shouldBuildUsingBuilder() {
        List<Integer> content = List.of(1, 2, 3);

        PageResponse<Integer> response = PageResponse.<Integer>builder()
                .content(content)
                .totalElements(100)
                .totalPages(10)
                .page(3)
                .size(10)
                .build();

        assertThat(response.getContent()).containsExactly(1, 2, 3);
        assertThat(response.getTotalElements()).isEqualTo(100);
        assertThat(response.getTotalPages()).isEqualTo(10);
        assertThat(response.getPage()).isEqualTo(3);
        assertThat(response.getSize()).isEqualTo(10);
    }

    @Test
    void shouldHaveCorrectToString() {
        PageResponse<String> response = PageResponse.<String>builder()
                .content(List.of("x"))
                .totalElements(1)
                .totalPages(1)
                .page(0)
                .size(10)
                .build();

        String toString = response.toString();

        assertThat(toString).contains("PageResponse");
        assertThat(toString).contains("totalElements=1");
        assertThat(toString).contains("page=0");
        assertThat(toString).contains("size=10");
    }
}
