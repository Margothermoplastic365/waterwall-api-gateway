package com.gateway.management.controller;

import com.gateway.common.dto.PageResponse;
import com.gateway.management.dto.ApiGatewayConfigRequest;
import com.gateway.management.dto.ApiResponse;
import com.gateway.management.dto.AuthPolicyRequest;
import com.gateway.management.dto.CreateApiRequest;
import com.gateway.management.dto.UpdateApiRequest;
import com.gateway.management.service.ApiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiControllerTest {

    @Mock
    private ApiService apiService;

    @InjectMocks
    private ApiController apiController;

    @Test
    void createApi_returnsCreated() {
        CreateApiRequest request = CreateApiRequest.builder().name("Test API").build();
        ApiResponse expected = ApiResponse.builder().id(UUID.randomUUID()).name("Test API").build();
        when(apiService.createApi(request)).thenReturn(expected);

        ResponseEntity<ApiResponse> response = apiController.createApi(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(apiService).createApi(request);
    }

    @Test
    void listApis_returnsOk() {
        ApiResponse api = ApiResponse.builder().id(UUID.randomUUID()).name("API 1").build();
        Page<ApiResponse> page = new PageImpl<>(List.of(api));
        when(apiService.listApis(eq("search"), eq("PUBLISHED"), eq("finance"), any(Pageable.class)))
                .thenReturn(page);

        ResponseEntity<PageResponse<ApiResponse>> response =
                apiController.listApis("search", "PUBLISHED", "finance", 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        verify(apiService).listApis(eq("search"), eq("PUBLISHED"), eq("finance"), any(Pageable.class));
    }

    @Test
    void getApi_returnsOk() {
        UUID id = UUID.randomUUID();
        ApiResponse expected = ApiResponse.builder().id(id).name("My API").build();
        when(apiService.getApi(id)).thenReturn(expected);

        ResponseEntity<ApiResponse> response = apiController.getApi(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(apiService).getApi(id);
    }

    @Test
    void updateApi_returnsOk() {
        UUID id = UUID.randomUUID();
        UpdateApiRequest request = new UpdateApiRequest();
        request.setName("Updated API");
        ApiResponse expected = ApiResponse.builder().id(id).name("Updated API").build();
        when(apiService.updateApi(id, request)).thenReturn(expected);

        ResponseEntity<ApiResponse> response = apiController.updateApi(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(apiService).updateApi(id, request);
    }

    @Test
    void deleteApi_returnsNoContent() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = apiController.deleteApi(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(apiService).deleteApi(id);
    }

    @Test
    void publishApi_returnsOk() {
        UUID id = UUID.randomUUID();
        ApiResponse expected = ApiResponse.builder().id(id).name("Published API").build();
        when(apiService.publishApi(id)).thenReturn(expected);

        ResponseEntity<ApiResponse> response = apiController.publishApi(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(apiService).publishApi(id);
    }

    @Test
    void deprecateApi_returnsOk() {
        UUID id = UUID.randomUUID();
        ApiResponse expected = ApiResponse.builder().id(id).name("Deprecated API").build();
        when(apiService.deprecateApi(id)).thenReturn(expected);

        ResponseEntity<ApiResponse> response = apiController.deprecateApi(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(apiService).deprecateApi(id);
    }

    @Test
    void retireApi_returnsOk() {
        UUID id = UUID.randomUUID();
        ApiResponse expected = ApiResponse.builder().id(id).name("Retired API").build();
        when(apiService.retireApi(id)).thenReturn(expected);

        ResponseEntity<ApiResponse> response = apiController.retireApi(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(apiService).retireApi(id);
    }

    @Test
    void getAuthPolicy_returnsOk() {
        UUID id = UUID.randomUUID();
        AuthPolicyRequest expected = new AuthPolicyRequest();
        when(apiService.getAuthPolicy(id)).thenReturn(expected);

        ResponseEntity<AuthPolicyRequest> response = apiController.getAuthPolicy(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(apiService).getAuthPolicy(id);
    }

    @Test
    void updateAuthPolicy_returnsOk() {
        UUID id = UUID.randomUUID();
        AuthPolicyRequest request = new AuthPolicyRequest();
        ApiResponse expected = ApiResponse.builder().id(id).build();
        when(apiService.updateAuthPolicy(id, request)).thenReturn(expected);

        ResponseEntity<ApiResponse> response = apiController.updateAuthPolicy(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(apiService).updateAuthPolicy(id, request);
    }

    @Test
    void updateGatewayConfig_returnsOk() {
        UUID id = UUID.randomUUID();
        ApiGatewayConfigRequest request = new ApiGatewayConfigRequest();
        ApiResponse expected = ApiResponse.builder().id(id).build();
        when(apiService.updateGatewayConfig(id, request)).thenReturn(expected);

        ResponseEntity<ApiResponse> response = apiController.updateGatewayConfig(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(apiService).updateGatewayConfig(id, request);
    }

    @Test
    void getGatewayConfig_returnsOk() {
        UUID id = UUID.randomUUID();
        ApiGatewayConfigRequest expected = new ApiGatewayConfigRequest();
        when(apiService.getGatewayConfig(id)).thenReturn(expected);

        ResponseEntity<ApiGatewayConfigRequest> response = apiController.getGatewayConfig(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(apiService).getGatewayConfig(id);
    }
}
