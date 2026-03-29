package com.gateway.runtime.config;

import com.gateway.runtime.proxy.GrpcProxyInterceptor;
import com.gateway.runtime.proxy.GrpcProxyService;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Starts a Netty-based gRPC server on a separate port (default 9090).
 * The server acts as a generic gRPC proxy that forwards all incoming
 * calls to the appropriate upstream gRPC service based on route configuration.
 *
 * <p>Uses a fallback {@link io.grpc.HandlerRegistry} from {@link GrpcProxyService}
 * to intercept all gRPC method calls, regardless of service or method name.</p>
 */
@Configuration
@ConditionalOnProperty(name = "gateway.runtime.protocols.grpc-enabled", havingValue = "true")
public class GrpcServerConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);

    private final GrpcProxyService grpcProxyService;
    private final GrpcProxyInterceptor grpcProxyInterceptor;

    @Value("${gateway.runtime.grpc-port:9090}")
    private int grpcPort;

    private Server server;

    public GrpcServerConfig(GrpcProxyService grpcProxyService,
                            GrpcProxyInterceptor grpcProxyInterceptor) {
        this.grpcProxyService = grpcProxyService;
        this.grpcProxyInterceptor = grpcProxyInterceptor;
    }

    @PostConstruct
    public void start() throws IOException {
        server = NettyServerBuilder.forPort(grpcPort)
                .fallbackHandlerRegistry(grpcProxyService.createFallbackRegistry())
                .intercept(grpcProxyInterceptor)
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();
        log.info("gRPC proxy server started on port {}", grpcPort);
    }

    @PreDestroy
    public void shutdown() {
        if (server != null) {
            log.info("Shutting down gRPC proxy server...");
            server.shutdown();
            try {
                if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("gRPC server did not terminate gracefully, forcing shutdown");
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
            log.info("gRPC proxy server stopped");
        }
    }
}
