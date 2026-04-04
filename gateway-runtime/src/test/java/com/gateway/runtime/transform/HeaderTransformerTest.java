package com.gateway.runtime.transform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeaderTransformerTest {

    @InjectMocks
    private HeaderTransformer headerTransformer;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    void shouldAddHeaderToResponse() {
        headerTransformer.addHeader(response, "X-Custom", "value1");

        verify(response).addHeader("X-Custom", "value1");
    }

    @Test
    void shouldRemoveHeaderFromResponse() {
        headerTransformer.removeHeader(response, "X-Remove-Me");

        verify(response).setHeader("X-Remove-Me", null);
    }

    @Test
    void shouldRenameHeaderOnResponse() {
        when(response.getHeader("X-Old-Name")).thenReturn("someValue");

        headerTransformer.renameHeader(response, "X-Old-Name", "X-New-Name");

        verify(response).setHeader("X-New-Name", "someValue");
        verify(response).setHeader("X-Old-Name", null);
    }

    @Test
    void shouldApplyRequestHeaderRules() {
        // add rule
        TransformationConfig.HeaderRule addRule = new TransformationConfig.HeaderRule("add", "X-Added", "addedValue", null);
        // remove rule
        TransformationConfig.HeaderRule removeRule = new TransformationConfig.HeaderRule("remove", "X-Remove", null, null);
        // rename rule
        TransformationConfig.HeaderRule renameRule = new TransformationConfig.HeaderRule("rename", "X-Old", null, "X-New");

        // Stub request for add rule - getHeaders returns existing values
        when(request.getHeaders("X-Added")).thenReturn(Collections.emptyEnumeration());
        // Stub request for rename rule
        when(request.getHeader("X-Old")).thenReturn("renamedValue");

        List<TransformationConfig.HeaderRule> rules = List.of(addRule, removeRule, renameRule);

        Map<String, List<String>> result = headerTransformer.applyRequestRules(request, rules);

        assertThat(result).isNotNull();
        // add: header present with value
        assertThat(result.get("x-added")).contains("addedValue");
        // remove: header mapped to empty list
        assertThat(result.get("x-remove")).isEmpty();
        // rename: old name mapped to empty, new name has value
        assertThat(result.get("x-old")).isEmpty();
        assertThat(result.get("x-new")).containsExactly("renamedValue");
    }

    @Test
    void shouldOverrideExistingHeader() {
        TransformationConfig.HeaderRule overrideRule = new TransformationConfig.HeaderRule("override", "X-Override", "newVal", null);

        Map<String, List<String>> result = headerTransformer.applyRequestRules(request, List.of(overrideRule));

        assertThat(result).isNotNull();
        assertThat(result.get("x-override")).containsExactly("newVal");
    }

    @Test
    void shouldApplyResponseRulesWithHeaderRules() {
        TransformationConfig.HeaderRule addRule = new TransformationConfig.HeaderRule("add", "X-Resp", "respVal", null);
        TransformationConfig.HeaderRule overrideRule = new TransformationConfig.HeaderRule("override", "Content-Type", "application/xml", null);

        headerTransformer.applyResponseRules(response, List.of(addRule, overrideRule));

        verify(response).addHeader("X-Resp", "respVal");
        verify(response).setHeader("Content-Type", "application/xml");
    }

    @Test
    void shouldReturnNullWhenNoRequestRules() {
        Map<String, List<String>> result = headerTransformer.applyRequestRules(request, null);
        assertThat(result).isNull();

        result = headerTransformer.applyRequestRules(request, List.of());
        assertThat(result).isNull();
    }

    @Test
    void shouldDoNothingWhenResponseRulesNull() {
        headerTransformer.applyResponseRules(response, null);
        verifyNoInteractions(response);
    }
}
