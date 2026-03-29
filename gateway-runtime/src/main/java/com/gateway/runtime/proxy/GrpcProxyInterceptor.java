package com.gateway.runtime.proxy;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Server interceptor applied to all gRPC calls on the proxy server.
 * Provides logging, authorization extraction, and rate limiting hooks.
 */
@Component
@ConditionalOnProperty(name = "gateway.runtime.protocols.grpc-enabled", havingValue = "true")
public class GrpcProxyInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcProxyInterceptor.class);

    /** Standard gRPC metadata key for Authorization */
    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    /** Custom metadata key for API key */
    private static final Metadata.Key<String> API_KEY_HEADER =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        String serviceName = extractServiceName(fullMethodName);
        String methodName = extractMethodName(fullMethodName);
        long startTime = System.nanoTime();

        // Extract authorization from metadata
        String authHeader = headers.get(AUTHORIZATION_KEY);
        String apiKey = headers.get(API_KEY_HEADER);

        log.info("gRPC call: service={}, method={}, auth={}, apiKey={}",
                serviceName, methodName,
                authHeader != null ? "present" : "absent",
                apiKey != null ? "present" : "absent");

        // Wrap the listener to capture completion for latency logging
        ServerCall.Listener<ReqT> listener = next.startCall(
                new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
                        log.info("gRPC completed: service={}, method={}, status={}, latency={}ms",
                                serviceName, methodName, status.getCode(), latencyMs);
                        super.close(status, trailers);
                    }
                },
                headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onCancel() {
                long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
                log.info("gRPC cancelled: service={}, method={}, latency={}ms",
                        serviceName, methodName, latencyMs);
                super.onCancel();
            }
        };
    }

    private String extractServiceName(String fullMethodName) {
        int slashIndex = fullMethodName.lastIndexOf('/');
        return slashIndex >= 0 ? fullMethodName.substring(0, slashIndex) : fullMethodName;
    }

    private String extractMethodName(String fullMethodName) {
        int slashIndex = fullMethodName.lastIndexOf('/');
        return slashIndex >= 0 ? fullMethodName.substring(slashIndex + 1) : fullMethodName;
    }
}
