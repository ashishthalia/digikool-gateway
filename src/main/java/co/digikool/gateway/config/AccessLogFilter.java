package co.digikool.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AccessLogFilter implements GlobalFilter {
    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var req = exchange.getRequest();
        long start = System.currentTimeMillis();
        return chain.filter(exchange).doFinally(sig -> {
            var res = exchange.getResponse();
            long took = System.currentTimeMillis() - start;
            var cid = res.getHeaders().getFirst("X-Correlation-Id");
            log.info("cid={} {} {} -> {} {}ms",
                    cid, req.getMethod(), req.getURI().getPath(), res.getStatusCode(), took);
        });
    }
}
