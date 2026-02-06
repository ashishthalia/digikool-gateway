package co.digikool.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements GlobalFilter {

    private static final String CORR = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var req = exchange.getRequest();
        var headers = req.getHeaders();
        String id = headers.getFirst(CORR);
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();

        String finalId = id;
        var mutated = exchange.mutate()
                .request(builder -> builder.headers(h -> h.set(CORR, finalId)))
                .response(exchange.getResponse()).build();

        // Return header to clients as well
        mutated.getResponse().getHeaders().set(CORR, id);

        return chain.filter(mutated);
    }
}