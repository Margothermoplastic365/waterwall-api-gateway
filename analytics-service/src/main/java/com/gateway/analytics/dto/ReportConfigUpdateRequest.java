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
public class ReportConfigUpdateRequest {

    private List<String> dailyRecipients;
    private List<String> weeklyRecipients;
    private List<String> monthlyRecipients;
}
