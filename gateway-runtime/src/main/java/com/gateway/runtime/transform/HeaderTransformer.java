package com.gateway.runtime.transform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Applies header-level transformations (add, remove, rename) to requests and responses.
 */
@Slf4j
@Component
public class HeaderTransformer {

    public void addHeader(HttpServletResponse response, String name, String value) {
        response.addHeader(name, value);
        log.debug("Added response header: {}={}", name, value);
    }

    public void removeHeader(HttpServletResponse response, String name) {
        // HttpServletResponse doesn't support direct header removal,
        // so this is a best-effort approach for response headers
        response.setHeader(name, null);
        log.debug("Removed response header: {}", name);
    }

    public void renameHeader(HttpServletResponse response, String oldName, String newName) {
        String value = response.getHeader(oldName);
        if (value != null) {
            response.setHeader(newName, value);
            response.setHeader(oldName, null);
            log.debug("Renamed response header: {} -> {}", oldName, newName);
        }
    }

    /**
     * Apply a list of header transformation rules.
     * Each rule is a map with keys: action (add/remove/rename), name, value, newName.
     */
    @SuppressWarnings("unchecked")
    public void applyRules(HttpServletResponse response, List<Map<String, String>> rules) {
        if (rules == null) return;
        for (Map<String, String> rule : rules) {
            String action = rule.getOrDefault("action", "");
            switch (action) {
                case "add" -> addHeader(response, rule.get("name"), rule.get("value"));
                case "remove" -> removeHeader(response, rule.get("name"));
                case "rename" -> renameHeader(response, rule.get("name"), rule.get("newName"));
                default -> log.warn("Unknown header transformation action: {}", action);
            }
        }
    }
}
