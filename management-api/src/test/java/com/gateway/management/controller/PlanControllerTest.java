package com.gateway.management.controller;

import com.gateway.management.dto.CreatePlanRequest;
import com.gateway.management.dto.PlanResponse;
import com.gateway.management.service.PlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanControllerTest {

    @Mock
    private PlanService planService;

    @InjectMocks
    private PlanController planController;

    @Test
    void createPlan_returnsCreated() {
        CreatePlanRequest request = CreatePlanRequest.builder()
                .name("Basic Plan")
                .description("A basic plan")
                .priceAmount(BigDecimal.TEN)
                .currency("USD")
                .build();
        PlanResponse expected = PlanResponse.builder()
                .id(UUID.randomUUID())
                .name("Basic Plan")
                .description("A basic plan")
                .build();
        when(planService.createPlan(request)).thenReturn(expected);

        ResponseEntity<PlanResponse> response = planController.createPlan(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(planService).createPlan(request);
    }

    @Test
    void listPlans_returnsOk() {
        List<PlanResponse> plans = List.of(
                PlanResponse.builder().id(UUID.randomUUID()).name("Free").build(),
                PlanResponse.builder().id(UUID.randomUUID()).name("Pro").build()
        );
        when(planService.listPlans()).thenReturn(plans);

        ResponseEntity<List<PlanResponse>> response = planController.listPlans();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        verify(planService).listPlans();
    }

    @Test
    void getPlan_returnsOk() {
        UUID id = UUID.randomUUID();
        PlanResponse expected = PlanResponse.builder().id(id).name("Enterprise").build();
        when(planService.getPlan(id)).thenReturn(expected);

        ResponseEntity<PlanResponse> response = planController.getPlan(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(planService).getPlan(id);
    }

    @Test
    void updatePlan_returnsOk() {
        UUID id = UUID.randomUUID();
        CreatePlanRequest request = CreatePlanRequest.builder()
                .name("Updated Plan")
                .build();
        PlanResponse expected = PlanResponse.builder().id(id).name("Updated Plan").build();
        when(planService.updatePlan(id, request)).thenReturn(expected);

        ResponseEntity<PlanResponse> response = planController.updatePlan(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(planService).updatePlan(id, request);
    }

    @Test
    void deletePlan_returnsNoContent() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = planController.deletePlan(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(planService).deletePlan(id);
    }
}
