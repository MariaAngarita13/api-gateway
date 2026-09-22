package co.edu.uptc.apigateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentDetailCompositionTest {

    static MockWebServer students = new MockWebServer();
    static MockWebServer subjects = new MockWebServer();
    static MockWebServer enrollments = new MockWebServer();

    @Autowired
    WebTestClient webTestClient;

    @BeforeAll
    static void start() throws IOException {
        students.start();
        subjects.start();
        enrollments.start();
    }

    @AfterAll
    static void stop() throws IOException {
        students.shutdown();
        subjects.shutdown();
        enrollments.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("STUDENTS_SERVICE_URL", () -> "http://localhost:" + students.getPort());
        registry.add("SUBJECTS_SERVICE_URL", () -> "http://localhost:" + subjects.getPort());
        registry.add("ENROLLMENTS_SERVICE_URL", () -> "http://localhost:" + enrollments.getPort());
    }

    @Test
    void composesStudentEnrollmentsCoursesAndTeachers() throws Exception {
        students.enqueue(json(200, "{\"id\":12,\"firstName\":\"Laura\",\"lastName\":\"Gomez\"}"));

        enrollments.enqueue(json(200,
                "{\"content\":[{\"id\":40,\"studentId\":12,\"courseId\":7,\"grade\":4.5}]}"));

        subjects.enqueue(json(200, "{\"id\":7,\"subjectId\":4,\"teacherId\":3}"));
        subjects.enqueue(json(200, "{\"id\":3,\"firstName\":\"Ana\",\"lastName\":\"Ruiz\"}"));

        webTestClient.get()
                .uri("/api/students/12/detail")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.student.id").isEqualTo(12)
                .jsonPath("$.enrollments[0].course.id").isEqualTo(7)
                .jsonPath("$.enrollments[0].teacher.id").isEqualTo(3)
                .jsonPath("$.enrollments[0].grade").isEqualTo(4.5);

        assertThat(students.takeRequest(2, TimeUnit.SECONDS).getPath())
                .isEqualTo("/api/students/12");

        assertThat(enrollments.takeRequest(2, TimeUnit.SECONDS).getPath())
                .isEqualTo("/api/enrollments?studentId=12");

        RecordedRequest course = subjects.takeRequest(2, TimeUnit.SECONDS);
        RecordedRequest teacher = subjects.takeRequest(2, TimeUnit.SECONDS);

        assertThat(course.getPath()).isEqualTo("/api/courses/7");
        assertThat(teacher.getPath()).isEqualTo("/api/teachers/3");
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
