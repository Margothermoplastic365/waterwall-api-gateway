package com.gateway.runtime.transform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UrlRewriterTest {

    @InjectMocks
    private UrlRewriter urlRewriter;

    @Test
    void shouldRewritePathWithRegex() {
        TransformationConfig.UrlRewriteRule rule = new TransformationConfig.UrlRewriteRule(
                "/api/v1/(.*)", "/api/v2/$1"
        );

        String result = urlRewriter.rewrite("/api/v1/users", rule);

        assertThat(result).isEqualTo("/api/v2/users");
    }

    @Test
    void shouldReturnOriginalWhenNoMatch() {
        TransformationConfig.UrlRewriteRule rule = new TransformationConfig.UrlRewriteRule(
                "/api/v1/(.*)", "/api/v2/$1"
        );

        String result = urlRewriter.rewrite("/other/path", rule);

        assertThat(result).isEqualTo("/other/path");
    }

    @Test
    void shouldHandleNullRule() {
        String result = urlRewriter.rewrite("/api/v1/users", null);

        assertThat(result).isEqualTo("/api/v1/users");
    }

    @Test
    void shouldHandleNullPatternInRule() {
        TransformationConfig.UrlRewriteRule rule = new TransformationConfig.UrlRewriteRule(null, "/api/v2/$1");

        String result = urlRewriter.rewrite("/api/v1/users", rule);

        assertThat(result).isEqualTo("/api/v1/users");
    }

    @Test
    void shouldHandleNullReplacementInRule() {
        TransformationConfig.UrlRewriteRule rule = new TransformationConfig.UrlRewriteRule("/api/v1/(.*)", null);

        String result = urlRewriter.rewrite("/api/v1/users", rule);

        assertThat(result).isEqualTo("/api/v1/users");
    }

    @Test
    void shouldRewriteComplexPathWithMultipleGroups() {
        TransformationConfig.UrlRewriteRule rule = new TransformationConfig.UrlRewriteRule(
                "/services/([^/]+)/api/(.*)", "/v2/$1/$2"
        );

        String result = urlRewriter.rewrite("/services/users/api/list", rule);

        assertThat(result).isEqualTo("/v2/users/list");
    }
}
