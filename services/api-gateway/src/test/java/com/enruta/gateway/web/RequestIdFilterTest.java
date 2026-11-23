package com.enruta.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesRequestIdWhenMissing() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/x"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain =
                ex -> {
                    forwarded.set(ex);
                    return Mono.empty();
                };

        filter.filter(exchange, chain).block();

        String header = forwarded.get().getRequest().getHeaders().getFirst(RequestIdFilter.HEADER);
        assertThat(header).isNotBlank().hasSize(36);
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIdFilter.HEADER))
                .isEqualTo(header);
    }

    @Test
    void keepsExistingRequestId() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/x").header(RequestIdFilter.HEADER, "abc-123"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain =
                ex -> {
                    forwarded.set(ex);
                    return Mono.empty();
                };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(RequestIdFilter.HEADER))
                .isEqualTo("abc-123");
    }
}
