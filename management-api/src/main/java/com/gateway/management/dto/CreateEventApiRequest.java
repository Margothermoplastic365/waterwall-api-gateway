package com.gateway.management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventApiRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Protocol is required (RABBITMQ, KAFKA, MQTT)")
    private String protocol;

    /** JSON object with broker connection details. */
    private String connectionConfig;

    /** JSON array/object describing available topics. */
    private String topics;

    /** JSON object with JSON-Schema definitions for message validation. */
    private String schemaConfig;
}
