package com.gateway.runtime.transform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class QueryParamTransformerTest {

    @InjectMocks
    private QueryParamTransformer queryParamTransformer;

    @Test
    void shouldAddQueryParam() {
        Map<String, String[]> original = new LinkedHashMap<>();
        original.put("existing", new String[]{"val"});

        TransformationConfig.QueryParamRule addRule = new TransformationConfig.QueryParamRule("add", "newParam", "newValue", null);

        Map<String, String[]> result = queryParamTransformer.applyRules(original, List.of(addRule));

        assertThat(result).containsKey("newParam");
        assertThat(result.get("newParam")).containsExactly("newValue");
        assertThat(result).containsKey("existing");
    }

    @Test
    void shouldRemoveQueryParam() {
        Map<String, String[]> original = new LinkedHashMap<>();
        original.put("removeMe", new String[]{"val"});
        original.put("keepMe", new String[]{"val2"});

        TransformationConfig.QueryParamRule removeRule = new TransformationConfig.QueryParamRule("remove", "removeMe", null, null);

        Map<String, String[]> result = queryParamTransformer.applyRules(original, List.of(removeRule));

        assertThat(result).doesNotContainKey("removeMe");
        assertThat(result).containsKey("keepMe");
    }

    @Test
    void shouldRenameQueryParam() {
        Map<String, String[]> original = new LinkedHashMap<>();
        original.put("oldName", new String[]{"theValue"});

        TransformationConfig.QueryParamRule renameRule = new TransformationConfig.QueryParamRule("rename", "oldName", null, "newName");

        Map<String, String[]> result = queryParamTransformer.applyRules(original, List.of(renameRule));

        assertThat(result).doesNotContainKey("oldName");
        assertThat(result).containsKey("newName");
        assertThat(result.get("newName")).containsExactly("theValue");
    }

    @Test
    void shouldPreserveUnmodifiedParams() {
        Map<String, String[]> original = new LinkedHashMap<>();
        original.put("keep1", new String[]{"a"});
        original.put("keep2", new String[]{"b"});
        original.put("modify", new String[]{"c"});

        TransformationConfig.QueryParamRule removeRule = new TransformationConfig.QueryParamRule("remove", "modify", null, null);

        Map<String, String[]> result = queryParamTransformer.applyRules(original, List.of(removeRule));

        assertThat(result).containsKey("keep1");
        assertThat(result).containsKey("keep2");
        assertThat(result.get("keep1")).containsExactly("a");
        assertThat(result.get("keep2")).containsExactly("b");
    }

    @Test
    void shouldReturnOriginalWhenRulesNull() {
        Map<String, String[]> original = new LinkedHashMap<>();
        original.put("key", new String[]{"val"});

        Map<String, String[]> result = queryParamTransformer.applyRules(original, null);
        assertThat(result).isSameAs(original);

        result = queryParamTransformer.applyRules(original, List.of());
        assertThat(result).isSameAs(original);
    }
}
