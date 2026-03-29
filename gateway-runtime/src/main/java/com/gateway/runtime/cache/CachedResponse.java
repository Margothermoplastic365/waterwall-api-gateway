package com.gateway.runtime.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * POJO representing a cached upstream response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedResponse implements Serializable {

    private int statusCode;
    private Map<String, String> headers;
    private byte[] body;
    private Instant cachedAt;
    private String etag;
}
