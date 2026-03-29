package com.gateway.management.dto;

import com.gateway.management.entity.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateApiRequest {

    private String name;

    private String version;

    private String description;

    private List<String> tags;

    private String category;

    private Visibility visibility;

    private String backendBaseUrl;
}
