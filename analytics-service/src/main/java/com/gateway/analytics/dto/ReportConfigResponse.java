package com.gateway.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportConfigResponse {

    private ReportTypeConfig daily;
    private ReportTypeConfig weekly;
    private ReportTypeConfig monthly;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportTypeConfig {
        private boolean enabled;
        private List<String> recipients;
    }
}
