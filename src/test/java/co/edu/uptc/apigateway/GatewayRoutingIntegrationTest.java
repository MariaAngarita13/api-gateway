package co.edu.uptc.apigateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Gateway's routing table against fake students/subjects/
 * enrollments services (MockWebServer instances), and checks that HTTP
 * method, query parameters and request body all reach the downstream
 * service unchanged — the "forwarding" items of the Gateway Definition of
 * Done.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

    static MockWebServer studentsService = new MockWebServer();
    static MockWebServer subjectsService = new MockWebServer();
    static MockWebServer enrollmentsService = new MockWebServer();

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startServers() throws IOException {
        studentsService.start();
        subjectsService.start();
        enrollmentsService.start();
    }

    @AfterAll
    static void stopServers() throws IOException {
        studentsService.shutdown();
        subjectsService.shutdown();
        enrollmentsService.shutdown();
    }

    @DynamicPropertySource
    static void overrideServiceUrls(DynamicPropertyRegistry registry) {
        registry.add("STUDENTS_SERVICE_URL", () -> "http://localhost:" + studentsService.getPort());
        registry.add("SUBJECTS_SERVICE_URL", () -> "http://localhost:" + subjectsService.getPort());
        registry.add("ENROLLMENTS_SERVICE_URL", () -> "http://localhost:" + enrollmentsService.getPort());
    }

    @Test
    void routesGetRequestToStudentsService() throws InterruptedException {
        studentsService.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"id\":12,\"firstName\":\"Laura\",\"lastName\":\"Gomez\"}"));

        webTestClient.get().uri("/api/students/12")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.firstName").isEqualTo("Laura");

        RecordedRequest recorded = studentsService.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/api/students/12");
    }

    @Test
    void routesGetRequestToSubjectsServiceForCourses() throws InterruptedException {
        subjectsService.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"id\":7,\"subjectId\":4,\"teacherId\":3}"));

        webTestClient.get().uri("/api/courses/7")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest recorded = subjectsService.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getPath()).isEqualTo("/api/courses/7");
    }

    @Test
    void routesGetRequestToEnrollmentsService() throws InterruptedException {
        enrollmentsService.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"content\":[],\"pageNumber\":0,\"pageSize\":10,\"totalElements\":0,\"totalPages\":0}"));

        webTestClient.get().uri("/api/enrollments?studentId=12")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest recorded = enrollmentsService.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getPath()).isEqualTo("/api/enrollments?studentId=12");
    }

    @Test
    void forwardsQueryParametersUnchanged() throws InterruptedException {
        studentsService.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"content\":[]}"));

        String query = "pageNumber=0&pageSize=10&sortBy=lastName&sortDirection=asc&firstName=Laura&programId=2";

        webTestClient.get().uri("/api/students?" + query)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest recorded = studentsService.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getPath()).isEqualTo("/api/students?" + query);
    }

    @Test
    void forwardsHttpMethodAndRequestBodyOnPost() throws InterruptedException {
        studentsService.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"id\":99}"));

        String requestBody = "{\"firstName\":\"Ana\",\"lastName\":\"Ruiz\",\"email\":\"ana@example.com\",\"programId\":2}";

        webTestClient.post().uri("/api/students")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated();

        RecordedRequest recorded = studentsService.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getBody().readUtf8()).isEqualTo(requestBody);
    }
}
