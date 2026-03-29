package com.gateway.runtime.proxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.model.MatchedRoute;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SSE (Server-Sent Events) protocol proxy handler. Opens a streaming connection
 * to the upstream SSE endpoint and relays events to the client via {@link SseEmitter}.
 *
 * <p>Since SseEmitter returns its own response, this handler sets the emitter as a
 * request attribute and returns a sentinel response that ProxyController can detect.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseProxyHandler implements ProtocolProxyHandler {

    /** Request attribute key used to pass the SseEmitter back to ProxyController. */
    public static final String SSE_EMITTER_ATTR = "gateway.sseEmitter";

    private final RestClient restClient;

    @Override
    public String getProtocolType() {
        return "SSE";
    }

    @Override
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, MatchedRoute matchedRoute) {
        GatewayRoute route = matchedRoute.getRoute();
        String upstreamUrl = route.getUpstreamUrl();

        // Append any remaining path and query string
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            upstreamUrl = upstreamUrl + "?" + queryString;
        }

        log.info("SSE proxy {} -> {}", request.getRequestURI(), upstreamUrl);

        // No timeout (0) means the emitter stays open indefinitely
        SseEmitter emitter = new SseEmitter(0L);

        String finalUpstreamUrl = upstreamUrl;

        // Relay events from upstream in a virtual thread
        Thread.startVirtualThread(() -> relayEvents(emitter, finalUpstreamUrl, request));

        // Store the emitter so ProxyController can return it
        request.setAttribute(SSE_EMITTER_ATTR, emitter);

        // Return null body — the controller detects the SSE_EMITTER_ATTR and returns it directly
        return null;
    }

    /**
     * Opens a streaming GET to the upstream SSE endpoint and relays events to the client emitter.
     */
    private void relayEvents(SseEmitter emitter, String upstreamUrl, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        long eventCount = 0;

        try {
            // Use RestClient to get the upstream response as a stream
            ResponseEntity<InputStream> upstreamResponse = restClient
                    .method(HttpMethod.GET)
                    .uri(URI.create(upstreamUrl))
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        // Allow errors to propagate for handling below
                    })
                    .toEntity(InputStream.class);

            if (upstreamResponse.getBody() == null) {
                emitter.completeWithError(new IOException("Upstream returned empty body"));
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(upstreamResponse.getBody(), StandardCharsets.UTF_8))) {

                String currentEvent = null;
                StringBuilder currentData = new StringBuilder();
                String currentId = null;

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        // Empty line = end of event, dispatch it
                        if (currentData.length() > 0) {
                            SseEmitter.SseEventBuilder event = SseEmitter.event()
                                    .data(currentData.toString());
                            if (currentEvent != null) {
                                event.name(currentEvent);
                            }
                            if (currentId != null) {
                                event.id(currentId);
                            }
                            emitter.send(event);
                            eventCount++;
                        }
                        currentEvent = null;
                        currentData.setLength(0);
                        currentId = null;
                        continue;
                    }

                    if (line.startsWith("event:")) {
                        currentEvent = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (currentData.length() > 0) {
                            currentData.append('\n');
                        }
                        currentData.append(line.substring(5).trim());
                    } else if (line.startsWith("id:")) {
                        currentId = line.substring(3).trim();
                    } else if (line.startsWith("retry:")) {
                        String retryStr = line.substring(6).trim();
                        try {
                            long retryMs = Long.parseLong(retryStr);
                            emitter.send(SseEmitter.event()
                                    .reconnectTime(retryMs)
                                    .data(""));
                        } catch (NumberFormatException e) {
                            log.debug("Ignoring invalid retry value: {}", retryStr);
                        }
                    }
                    // Lines starting with ':' are comments, ignored per SSE spec
                }

                // Upstream closed — complete the emitter
                emitter.complete();
            }

        } catch (Exception ex) {
            log.warn("SSE relay error for {}: {}", upstreamUrl, ex.getMessage());
            try {
                emitter.completeWithError(ex);
            } catch (Exception ignored) {
                // Emitter already completed/timed out
            }
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("SSE connection closed for {} — duration={}ms events={}", upstreamUrl, duration, eventCount);
        }
    }
}
