package co.edu.uptc.apigateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the composed student detail response.
 *
 * Flow:
 * 1. GET /api/students/{id}
 * 2. GET /api/enrollments?studentId={id}
 * 3. For every enrollment, GET /api/courses/{courseId}
 * 4. For every course, GET /api/teachers/{teacherId}
 * 5. Return one JSON document.
 */
@Service
public class StudentDetailService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String studentsUrl;
    private final String subjectsUrl;
    private final String enrollmentsUrl;

    public StudentDetailService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${STUDENTS_SERVICE_URL:http://localhost:8081}") String studentsUrl,
            @Value("${SUBJECTS_SERVICE_URL:http://localhost:8082}") String subjectsUrl,
            @Value("${ENROLLMENTS_SERVICE_URL:http://localhost:8083}") String enrollmentsUrl) {

        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.studentsUrl = removeTrailingSlash(studentsUrl);
        this.subjectsUrl = removeTrailingSlash(subjectsUrl);
        this.enrollmentsUrl = removeTrailingSlash(enrollmentsUrl);
    }

    public Mono<JsonNode> getStudentDetail(Long studentId) {
        return getJson(studentsUrl + "/api/students/" + studentId)
                .flatMap(student ->
                        getJson(enrollmentsUrl + "/api/enrollments?studentId=" + studentId)
                                .flatMap(enrollmentResponse ->
                                        enrichEnrollments(enrollmentResponse)
                                                .collectList()
                                                .map(enrollments -> buildResponse(student, enrollments))
                                )
                );
    }

    private Flux<JsonNode> enrichEnrollments(JsonNode enrollmentResponse) {
        JsonNode enrollments = extractEnrollments(enrollmentResponse);

        if (!enrollments.isArray()) {
            return Flux.error(new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Invalid enrollments response from enrollments service"));
        }

        return Flux.fromIterable(enrollments)
                .flatMap(this::enrichEnrollment);
    }

    private Mono<JsonNode> enrichEnrollment(JsonNode enrollment) {
        Long courseId = readLong(enrollment, "courseId", "courseID", "cursoId");

        if (courseId == null) {
            return Mono.error(new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "An enrollment does not contain a courseId"));
        }

        return getJson(subjectsUrl + "/api/courses/" + courseId)
                .flatMap(course -> {
                    Long teacherId = readLong(course, "teacherId", "teacherID", "docenteId");

                    if (teacherId == null) {
                        return Mono.error(new ResponseStatusException(
                                org.springframework.http.HttpStatus.BAD_GATEWAY,
                                "A course does not contain a teacherId"));
                    }

                    return getJson(subjectsUrl + "/api/teachers/" + teacherId)
                            .map(teacher -> {
                                ObjectNode result = enrollment.deepCopy();
                                result.set("course", course);
                                result.set("teacher", teacher);
                                return result;
                            });
                });
    }

    private Mono<JsonNode> getJson(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new ResponseStatusException(
                                        response.statusCode(),
                                        "Downstream service returned HTTP "
                                                + response.statusCode().value()
                                                + (body.isBlank() ? "" : ": " + body)))))
                .bodyToMono(JsonNode.class);
    }

    private JsonNode extractEnrollments(JsonNode response) {
        if (response.isArray()) {
            return response;
        }

        // Supports the common paginated response used by the enrollments service:
        // { "content": [ ... ], "pageNumber": 0, ... }
        if (response.has("content") && response.get("content").isArray()) {
            return response.get("content");
        }

        // Also supports a wrapper such as { "enrollments": [ ... ] }.
        if (response.has("enrollments") && response.get("enrollments").isArray()) {
            return response.get("enrollments");
        }

        return objectMapper.createArrayNode();
    }

    private ObjectNode buildResponse(JsonNode student, List<JsonNode> enrollments) {
        ObjectNode response = objectMapper.createObjectNode();
        response.set("student", student);

        ArrayNode enrollmentArray = response.putArray("enrollments");
        enrollments.forEach(enrollmentArray::add);

        return response;
    }

    private Long readLong(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isNumber()) {
                return value.longValue();
            }
            if (value != null && value.isTextual()) {
                try {
                    return Long.parseLong(value.asText());
                } catch (NumberFormatException ignored) {
                    // Try the next accepted field name.
                }
            }
        }
        return null;
    }

    private String removeTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
