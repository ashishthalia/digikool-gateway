package co.digikool.gateway.config;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class GatewayConfig {

    private static final Logger logger = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean
    @Order(-100)
    public GlobalFilter customGlobalFilter() {
        return (exchange, chain) -> {
            logger.debug("Custom Global Filter executed for path: {}", exchange.getRequest().getPath());
            return chain.filter(exchange);
        };
    }

}
