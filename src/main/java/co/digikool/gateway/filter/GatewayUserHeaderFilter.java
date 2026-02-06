package co.digikool.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class GatewayUserHeaderFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayUserHeaderFilter.class);

    // Header names for gateway user information
    public static final String GATEWAY_USER_ID_HEADER = "gateway-id";
    public static final String GATEWAY_SCHOOL_ID_HEADER = "gateway-school-id";
    public static final String GATEWAY_PERMISSION_HEADER = "gateway-permission";
    public static final String GATEWAY_EMAIL_HEADER = "gateway-email";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        log.debug("GatewayUserHeaderFilter processing request: {}", path);
        
        // First, try to get authentication from ReactiveSecurityContextHolder (most reliable)
        return org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                .cast(org.springframework.security.core.context.SecurityContext.class)
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated())
                .flatMap(auth -> {
                    log.debug("Found authentication from ReactiveSecurityContextHolder");
                    return extractAndSetHeaders(exchange, chain, auth);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // If not in SecurityContext, try session
                    log.debug("No authentication in ReactiveSecurityContextHolder, trying session");
                    
                    // Check for JSESSIONID cookie - this is the primary indicator of authentication
                    var jsessionCookie = exchange.getRequest().getCookies().getFirst("JSESSIONID");
                    boolean hasSessionCookie = jsessionCookie != null && jsessionCookie.getValue() != null && !jsessionCookie.getValue().isEmpty();
                    
                    if (!hasSessionCookie) {
                        log.debug("No JSESSIONID cookie found, skipping header setting");
                        return chain.filter(exchange);
                    }
                    
                    log.debug("JSESSIONID cookie found, extracting user info from session");
                    // Get user info from session
                    return exchange.getSession()
                            .flatMap(session -> {
                                log.debug("Session found, extracting authentication. Session ID: {}", session.getId());
                                
                                // Log all session attributes for debugging
                                log.debug("Session attributes: {}", session.getAttributes().keySet());
                                
                                // Try to get authentication from SecurityContext in session
                                Object authObj = session.getAttribute("SPRING_SECURITY_CONTEXT");
                                log.debug("SPRING_SECURITY_CONTEXT attribute type: {}", authObj != null ? authObj.getClass().getName() : "null");
                                if (authObj instanceof SecurityContext) {
                                    SecurityContext secCtx = (SecurityContext) authObj;
                                    Authentication auth = secCtx.getAuthentication();
                                    log.debug("Authentication from SecurityContext: {}, authenticated: {}", 
                                            auth != null ? auth.getClass().getName() : "null",
                                            auth != null ? auth.isAuthenticated() : false);
                                    if (auth != null && auth.isAuthenticated()) {
                                        log.debug("Found authenticated user in session, extracting user info");
                                        return extractAndSetHeaders(exchange, chain, auth);
                                    }
                                }
                                
                                // Try to get OAuth2User directly from session attributes
                                Object oauth2UserObj = session.getAttribute("OAUTH2_USER");
                                log.debug("OAUTH2_USER attribute type: {}", oauth2UserObj != null ? oauth2UserObj.getClass().getName() : "null");
                                if (oauth2UserObj instanceof OAuth2User) {
                                    log.debug("Found OAuth2User in session attributes");
                                    OAuth2User oauth2User = (OAuth2User) oauth2UserObj;
                                    return extractAndSetHeadersFromOAuth2User(exchange, chain, oauth2User);
                                }
                                
                                log.warn("JSESSIONID cookie exists but no authentication found in session");
                                return setMinimalHeaders(exchange, chain);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.warn("JSESSIONID cookie exists but session not available");
                                return setMinimalHeaders(exchange, chain);
                            }));
                }));
    }
    
    private Mono<Void> extractAndSetHeaders(ServerWebExchange exchange, GatewayFilterChain chain, Authentication authentication) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest.Builder requestBuilder = request.mutate();
        
        // Extract user information from authentication
        String userId = null;
        String schoolId = null;
        String email = null;
        String permissions = null;
        
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            OAuth2User oauth2User = oauth2Token.getPrincipal();
            
            // Extract user_id (try user_id first, then sub, then name)
            Object userIdAttr = oauth2User.getAttribute("user_id");
            if (userIdAttr == null) {
                userIdAttr = oauth2User.getAttribute("sub");
            }
            if (userIdAttr == null) {
                userIdAttr = oauth2User.getName();
            }
            userId = userIdAttr != null ? userIdAttr.toString() : null;
            
            // Extract school_id from attributes
            Object schoolIdAttr = oauth2User.getAttribute("school_id");
            if (schoolIdAttr != null) {
                schoolId = schoolIdAttr.toString();
            }
            
            // Extract email from attributes
            Object emailAttr = oauth2User.getAttribute("email");
            if (emailAttr != null) {
                email = emailAttr.toString();
            }
            
            // Extract permissions/roles from attributes
            Object rolesAttr = oauth2User.getAttribute("roles");
            if (rolesAttr == null) {
                rolesAttr = oauth2User.getAttribute("authorities");
            }
            if (rolesAttr != null) {
                if (rolesAttr instanceof List) {
                    permissions = String.join(",", ((List<?>) rolesAttr).stream()
                            .map(obj -> {
                                // Handle GrantedAuthority objects
                                if (obj instanceof org.springframework.security.core.GrantedAuthority) {
                                    return ((org.springframework.security.core.GrantedAuthority) obj).getAuthority();
                                }
                                return obj.toString();
                            })
                            .toList());
                } else if (rolesAttr instanceof String) {
                    permissions = (String) rolesAttr;
                } else {
                    permissions = rolesAttr.toString();
                }
            }
            
            // Also check authorities
            if (permissions == null || permissions.isEmpty()) {
                permissions = authentication.getAuthorities().stream()
                        .map(auth -> auth.getAuthority())
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
            }
        } else {
            // For other authentication types, try to extract from principal
            Object principal = authentication.getPrincipal();
            if (principal instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> principalMap = (Map<String, Object>) principal;
                userId = principalMap.get("user_id") != null ? principalMap.get("user_id").toString() : null;
                schoolId = principalMap.get("school_id") != null ? principalMap.get("school_id").toString() : null;
                email = principalMap.get("email") != null ? principalMap.get("email").toString() : null;
            }
            
            // Extract permissions from authorities
            permissions = authentication.getAuthorities().stream()
                    .map(auth -> auth.getAuthority())
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
        }
        
        // Set headers if values are available
        if (userId != null) {
            requestBuilder.header(GATEWAY_USER_ID_HEADER, userId);
            log.debug("Setting {} header: {}", GATEWAY_USER_ID_HEADER, userId);
        }
        if (schoolId != null) {
            requestBuilder.header(GATEWAY_SCHOOL_ID_HEADER, schoolId);
            log.debug("Setting {} header: {}", GATEWAY_SCHOOL_ID_HEADER, schoolId);
        }
        if (email != null) {
            requestBuilder.header(GATEWAY_EMAIL_HEADER, email);
            log.debug("Setting {} header: {}", GATEWAY_EMAIL_HEADER, email);
        }
        if (permissions != null && !permissions.isEmpty()) {
            requestBuilder.header(GATEWAY_PERMISSION_HEADER, permissions);
            log.debug("Setting {} header: {}", GATEWAY_PERMISSION_HEADER, permissions);
        }
        
        // Don't set headers if we don't have valid user information
        // The attendance service will reject requests without valid user/school IDs
        if (userId == null && schoolId == null && email == null && (permissions == null || permissions.isEmpty())) {
            log.warn("No valid user information found in session, not setting gateway headers");
            // Don't set any headers - let the attendance service handle unauthorized requests
        }
        
        ServerHttpRequest mutatedRequest = requestBuilder.build();
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();
        
        return chain.filter(mutatedExchange);
    }
    
    private Mono<Void> extractAndSetHeadersFromOAuth2User(ServerWebExchange exchange, GatewayFilterChain chain, OAuth2User oauth2User) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest.Builder requestBuilder = request.mutate();
        
        // Extract user_id (try user_id first, then sub, then name)
        Object userIdAttr = oauth2User.getAttribute("user_id");
        if (userIdAttr == null) {
            userIdAttr = oauth2User.getAttribute("sub");
        }
        if (userIdAttr == null) {
            userIdAttr = oauth2User.getName();
        }
        String userId = userIdAttr != null ? userIdAttr.toString() : null;
        
        // Extract school_id from attributes
        Object schoolIdAttr = oauth2User.getAttribute("school_id");
        String schoolId = schoolIdAttr != null ? schoolIdAttr.toString() : null;
        
        // Extract email from attributes
        Object emailAttr = oauth2User.getAttribute("email");
        String email = emailAttr != null ? emailAttr.toString() : null;
        
        // Extract permissions/roles from attributes
        Object rolesAttr = oauth2User.getAttribute("roles");
        if (rolesAttr == null) {
            rolesAttr = oauth2User.getAttribute("authorities");
        }
        String permissions = null;
        if (rolesAttr != null) {
            if (rolesAttr instanceof List) {
                permissions = String.join(",", ((List<?>) rolesAttr).stream()
                        .map(obj -> {
                            if (obj instanceof org.springframework.security.core.GrantedAuthority) {
                                return ((org.springframework.security.core.GrantedAuthority) obj).getAuthority();
                            }
                            return obj.toString();
                        })
                        .toList());
            } else if (rolesAttr instanceof String) {
                permissions = (String) rolesAttr;
            } else {
                permissions = rolesAttr.toString();
            }
        }
        
        // Set headers
        if (userId != null) {
            requestBuilder.header(GATEWAY_USER_ID_HEADER, userId);
            log.debug("Setting {} header: {}", GATEWAY_USER_ID_HEADER, userId);
        }
        if (schoolId != null) {
            requestBuilder.header(GATEWAY_SCHOOL_ID_HEADER, schoolId);
            log.debug("Setting {} header: {}", GATEWAY_SCHOOL_ID_HEADER, schoolId);
        }
        if (email != null) {
            requestBuilder.header(GATEWAY_EMAIL_HEADER, email);
            log.debug("Setting {} header: {}", GATEWAY_EMAIL_HEADER, email);
        }
        if (permissions != null && !permissions.isEmpty()) {
            requestBuilder.header(GATEWAY_PERMISSION_HEADER, permissions);
            log.debug("Setting {} header: {}", GATEWAY_PERMISSION_HEADER, permissions);
        }
        
        // Don't set headers if we don't have valid user information
        // The attendance service will reject requests without valid user/school IDs
        if (userId == null && schoolId == null && email == null && (permissions == null || permissions.isEmpty())) {
            log.warn("No valid user information found in OAuth2User, not setting gateway headers");
            // Don't set any headers - let the attendance service handle unauthorized requests
        }
        
        ServerHttpRequest mutatedRequest = requestBuilder.build();
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();
        
        return chain.filter(mutatedExchange);
    }
    
           private Mono<Void> setMinimalHeaders(ServerWebExchange exchange, GatewayFilterChain chain) {
               // Don't set headers if we don't have valid user information
               // The attendance service will reject requests without valid user/school IDs
               log.warn("JSESSIONID cookie exists but no valid user information found, not setting gateway headers");
               return chain.filter(exchange);
           }

    @Override
    public int getOrder() {
        // Run after security context is set but before routing
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}

