package com.gateway.management.controller;

import com.gateway.management.dto.CreateEventApiRequest;
import com.gateway.management.dto.EventApiResponse;
import com.gateway.management.service.EventApiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/event-apis")
@RequiredArgsConstructor
public class EventApiController {

    private final EventApiService eventApiService;

    @PostMapping
    public ResponseEntity<EventApiResponse> createEventApi(@Valid @RequestBody CreateEventApiRequest request) {
        EventApiResponse response = eventApiService.createEventApi(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<EventApiResponse>> listEventApis() {
        return ResponseEntity.ok(eventApiService.listEventApis());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventApiResponse> getEventApi(@PathVariable UUID id) {
        return ResponseEntity.ok(eventApiService.getEventApi(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventApiResponse> updateEventApi(@PathVariable UUID id,
                                                            @Valid @RequestBody CreateEventApiRequest request) {
        return ResponseEntity.ok(eventApiService.updateEventApi(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEventApi(@PathVariable UUID id) {
        eventApiService.deleteEventApi(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/subscribe")
    public ResponseEntity<?> subscribeConsumer(@PathVariable UUID id,
                                               @RequestBody Map<String, String> request) {
        UUID consumerId = UUID.fromString(request.getOrDefault("consumerId", UUID.randomUUID().toString()));
        String topic = request.getOrDefault("topic", "");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventApiService.subscribeConsumer(id, consumerId, topic));
    }

    @GetMapping("/{id}/subscriptions")
    public ResponseEntity<?> listSubscriptions(@PathVariable UUID id) {
        return ResponseEntity.ok(eventApiService.listSubscriptions(id));
    }
}
