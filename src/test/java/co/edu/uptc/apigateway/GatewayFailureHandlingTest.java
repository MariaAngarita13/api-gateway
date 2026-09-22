package co.edu.uptc.apigateway;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

/**
 * Verifies "Gateway y manejo de fallos": when a downstream service does not
 * respond, the Gateway itself must stay up and answer with 502 Bad Gateway
 * using the team's common error contract — never a raw 500 or a hang.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayFailureHandlingTest {

    static int unreachablePort;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void overrideServiceUrls(DynamicPropertyRegistry registry) throws IOException {
        // Start a server, note its port, then close it immediately so the
        // port is free but refuses connections — simulating a downed service.
        MockWebServer temporaryServer = new MockWebServer();
        temporaryServer.start();
        unreachablePort = temporaryServer.getPort();
        temporaryServer.shutdown();

        registry.add("STUDENTS_SERVICE_URL", () -> "http://localhost:" + unreachablePort);
        registry.add("SUBJECTS_SERVICE_URL", () -> "http://localhost:" + unreachablePort);
        registry.add("ENROLLMENTS_SERVICE_URL", () -> "http://localhost:" + unreachablePort);
    }

    @Test
    void returnsBadGatewayWhenDownstreamServiceIsUnreachable() {
        webTestClient.get().uri("/api/students/12")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.status").isEqualTo(502)
                .jsonPath("$.error").isEqualTo("Bad Gateway")
                .jsonPath("$.message").isEqualTo("The requested service is currently unavailable")
                .jsonPath("$.path").isEqualTo("/api/students/12")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void returnsStructuredErrorWhenNoRouteMatches() {
        webTestClient.get().uri("/api/this-resource-does-not-exist")
                .exchange()
                .expectStatus().is4xxClientError()
                .expectBody()
                .jsonPath("$.status").exists()
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.path").isEqualTo("/api/this-resource-does-not-exist");
    }
}
