package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigDiffResponse {

    private int addedCount;
    private int modifiedCount;
    private int removedCount;
    private List<String> details;
}
