package com.gateway.runtime.proxy;

import com.gateway.runtime.model.GatewayRoute;
import com.gateway.runtime.service.RouteConfigService;
import io.grpc.*;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Generic gRPC proxy service that receives ALL incoming gRPC calls
 * and forwards them to the appropriate upstream gRPC server.
 *
 * <p>Phase 1 supports unary calls only. Server-streaming, client-streaming,
 * and bidirectional streaming return UNIMPLEMENTED.</p>
 *
 * <p>This component provides a {@link HandlerRegistry} that acts as a fallback
 * for all methods not explicitly registered on the gRPC server, enabling
 * transparent proxying of any gRPC service/method combination.</p>
 */
@Component
@ConditionalOnProperty(name = "gateway.runtime.protocols.grpc-enabled", havingValue = "true")
public class GrpcProxyService {

    private static final Logger log = LoggerFactory.getLogger(GrpcProxyService.class);

    private final RouteConfigService routeConfigService;

    /** Channel pool: keyed by "host:port" */
    private final ConcurrentHashMap<String, ManagedChannel> channelPool = new ConcurrentHashMap<>();

    /**
     * Byte-array marshaller that passes through raw bytes without protobuf deserialization.
     */
    private static final MethodDescriptor.Marshaller<byte[]> BYTE_MARSHALLER =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(byte[] value) {
                    return new ByteArrayInputStream(value);
                }

                @Override
                public byte[] parse(InputStream stream) {
                    try {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = stream.read(buf)) != -1) {
                            bos.write(buf, 0, n);
                        }
                        return bos.toByteArray();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read gRPC message bytes", e);
                    }
                }
            };

    public GrpcProxyService(RouteConfigService routeConfigService) {
        this.routeConfigService = routeConfigService;
    }

    /**
     * Creates a {@link HandlerRegistry} that catches all gRPC calls and proxies them.
     * This is used as the fallback handler registry on the gRPC server.
     */
    public HandlerRegistry createFallbackRegistry() {
        return new HandlerRegistry() {
            @Override
            public ServerMethodDefinition<?, ?> lookupMethod(String methodName, String authority) {
                // Build a method descriptor for the requested method (assume unary)
                MethodDescriptor<byte[], byte[]> methodDescriptor = MethodDescriptor.<byte[], byte[]>newBuilder()
                        .setType(MethodType.UNARY)
                        .setFullMethodName(methodName)
                        .setRequestMarshaller(BYTE_MARSHALLER)
                        .setResponseMarshaller(BYTE_MARSHALLER)
                        .build();

                ServerCallHandler<byte[], byte[]> handler = createProxyHandler(methodName);

                return ServerMethodDefinition.create(methodDescriptor, handler);
            }
        };
    }

    private ServerCallHandler<byte[], byte[]> createProxyHandler(String fullMethodName) {
        String serviceName = extractServiceName(fullMethodName);
        String methodName = extractMethodName(fullMethodName);

        return (call, headers) -> {
            log.debug("gRPC proxy received call: service={}, method={}", serviceName, methodName);

            // Look up route for the gRPC service
            Optional<GatewayRoute> routeOpt = findGrpcRoute(serviceName);
            if (routeOpt.isEmpty()) {
                log.warn("No gRPC route found for service: {}", serviceName);
                call.close(Status.NOT_FOUND.withDescription(
                        "No route configured for gRPC service: " + serviceName), new Metadata());
                return new ServerCall.Listener<>() {};
            }

            GatewayRoute route = routeOpt.get();
            ManagedChannel channel = getOrCreateChannel(route.getUpstreamUrl());

            // Build the method descriptor for the upstream call
            MethodDescriptor<byte[], byte[]> upstreamMethod = MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodType.UNARY)
                    .setFullMethodName(fullMethodName)
                    .setRequestMarshaller(BYTE_MARSHALLER)
                    .setResponseMarshaller(BYTE_MARSHALLER)
                    .build();

            return new ServerCall.Listener<>() {
                private byte[] requestPayload;

                @Override
                public void onMessage(byte[] message) {
                    requestPayload = message;
                }

                @Override
                public void onHalfClose() {
                    if (requestPayload == null) {
                        call.close(Status.INTERNAL.withDescription("No request payload received"),
                                new Metadata());
                        return;
                    }

                    try {
                        CallOptions callOptions = CallOptions.DEFAULT;
                        ClientCall<byte[], byte[]> clientCall = channel.newCall(upstreamMethod, callOptions);

                        long startTime = System.nanoTime();

                        ClientCalls.asyncUnaryCall(clientCall, requestPayload,
                                new StreamObserver<>() {
                                    @Override
                                    public void onNext(byte[] response) {
                                        call.sendHeaders(new Metadata());
                                        call.sendMessage(response);
                                    }

                                    @Override
                                    public void onError(Throwable t) {
                                        long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
                                        Status status = Status.fromThrowable(t);
                                        log.error("gRPC upstream error: service={}, method={}, status={}, latency={}ms",
                                                serviceName, methodName, status.getCode(), latencyMs);
                                        call.close(status, new Metadata());
                                    }

                                    @Override
                                    public void onCompleted() {
                                        long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
                                        log.debug("gRPC proxy completed: service={}, method={}, latency={}ms",
                                                serviceName, methodName, latencyMs);
                                        call.close(Status.OK, new Metadata());
                                    }
                                });

                    } catch (Exception e) {
                        log.error("gRPC proxy error forwarding call: service={}, method={}",
                                serviceName, methodName, e);
                        call.close(Status.INTERNAL.withDescription("Proxy error: " + e.getMessage()),
                                new Metadata());
                    }
                }

                @Override
                public void onCancel() {
                    log.debug("gRPC call cancelled: service={}, method={}", serviceName, methodName);
                }

                @Override
                public void onReady() {
                    call.request(1);
                }
            };
        };
    }

    // ── Route lookup ────────────────────────────────────────────────────

    private Optional<GatewayRoute> findGrpcRoute(String serviceName) {
        return routeConfigService.getAllRoutes().stream()
                .filter(r -> "GRPC".equalsIgnoreCase(r.getProtocolType()))
                .filter(r -> {
                    String path = r.getPath();
                    if (path == null) return false;
                    // Strip leading slash and "grpc/" prefix if present
                    String normalized = path.replaceFirst("^/+", "").replaceFirst("^grpc/", "");
                    return normalized.equals(serviceName) || path.equals("/" + serviceName);
                })
                .findFirst();
    }

    // ── Channel management ──────────────────────────────────────────────

    private ManagedChannel getOrCreateChannel(String upstreamUrl) {
        try {
            URI uri = URI.create(upstreamUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 9090;
            String key = host + ":" + port;

            return channelPool.computeIfAbsent(key, k -> {
                log.info("Creating gRPC channel to upstream: {}", k);
                return NettyChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .build();
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to create gRPC channel for: " + upstreamUrl, e);
        }
    }

    @PreDestroy
    public void shutdownChannels() {
        log.info("Shutting down {} gRPC upstream channels", channelPool.size());
        channelPool.values().forEach(channel -> {
            try {
                channel.shutdown();
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        });
        channelPool.clear();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String extractServiceName(String fullMethodName) {
        int slashIndex = fullMethodName.lastIndexOf('/');
        return slashIndex >= 0 ? fullMethodName.substring(0, slashIndex) : fullMethodName;
    }

    private String extractMethodName(String fullMethodName) {
        int slashIndex = fullMethodName.lastIndexOf('/');
        return slashIndex >= 0 ? fullMethodName.substring(slashIndex + 1) : fullMethodName;
    }
}
