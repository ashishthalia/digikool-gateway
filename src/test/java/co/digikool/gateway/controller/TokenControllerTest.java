package co.digikool.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(TokenController.class)
public class TokenControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    public void testGenerateToken() {
        String tokenRequest = """
            {
                "username": "test@example.com",
                "password": "password123",
                "userId": "user123",
                "schoolId": "school456",
                "permissions": ["read", "write"]
            }
            """;

        webTestClient.post()
                .uri("/api/token/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(tokenRequest)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.access_token").exists()
                .jsonPath("$.token_type").isEqualTo("Bearer")
                .jsonPath("$.expires_in").isEqualTo(3600)
                .jsonPath("$.user_id").isEqualTo("user123")
                .jsonPath("$.school_id").isEqualTo("school456");
    }

    @Test
    public void testValidateToken() {
        webTestClient.get()
                .uri("/api/token/validate")
                .header("Authorization", "Bearer test_token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.valid").isEqualTo(true)
                .jsonPath("$.active").isEqualTo(true)
                .jsonPath("$.token_type").isEqualTo("Bearer");
    }

    @Test
    public void testValidateTokenWithoutHeader() {
        webTestClient.get()
                .uri("/api/token/validate")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.valid").isEqualTo(false)
                .jsonPath("$.error").isEqualTo("invalid_token");
    }

    @Test
    public void testRefreshToken() {
        String refreshRequest = """
            {
                "refresh_token": "test_refresh_token"
            }
            """;

        webTestClient.post()
                .uri("/api/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(refreshRequest)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.access_token").exists()
                .jsonPath("$.token_type").isEqualTo("Bearer")
                .jsonPath("$.expires_in").isEqualTo(3600);
    }

    @Test
    public void testRevokeToken() {
        String revokeRequest = """
            {
                "token": "test_access_token"
            }
            """;

        webTestClient.post()
                .uri("/api/token/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(revokeRequest)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.revoked").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Token successfully revoked");
    }

    @Test
    public void testGetTokenInfo() {
        webTestClient.get()
                .uri("/api/token/info")
                .header("Authorization", "Bearer test_token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.token_type").isEqualTo("Bearer")
                .jsonPath("$.expires_in").isEqualTo(3600)
                .jsonPath("$.scope").isEqualTo("read write");
    }
}

