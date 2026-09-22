package co.edu.uptc.apigateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

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

        return getJson(
                studentsUrl + "/api/students/" + studentId
        ).flatMap(student -> {

            Mono<JsonNode> enrollmentsResult =
                    getJson(
                            enrollmentsUrl
                                    + "/api/enrollments?studentId="
                                    + studentId
                    )
                    .flatMapMany(this::extractEnrollmentsAsFlux)
                    .flatMap(this::enrichEnrollment)
                    .collectList()
                    .map(enrollments ->
                            (JsonNode) buildResponse(student, enrollments)
                    );

            return enrollmentsResult.onErrorResume(error ->
                    Mono.just(
                            (JsonNode) buildResponse(
                                    student,
                                    List.of()
                            )
                    )
            );
        });
    }

    private Flux<JsonNode> extractEnrollmentsAsFlux(
            JsonNode enrollmentResponse) {

        JsonNode enrollments =
                extractEnrollments(enrollmentResponse);

        if (!enrollments.isArray()) {
            return Flux.empty();
        }

        return Flux.fromIterable(enrollments);
    }

    private Mono<JsonNode> enrichEnrollment(
            JsonNode enrollment) {

        Long courseId = readLong(
                enrollment,
                "courseId",
                "courseID",
                "cursoId"
        );

        ObjectNode result =
                enrollment.deepCopy();

        if (courseId == null) {

            result.putNull("course");
            result.putNull("teacher");

            return Mono.just(
                    (JsonNode) result
            );
        }

        return getJson(
                subjectsUrl
                        + "/api/courses/"
                        + courseId
        ).flatMap(course -> {

            result.set(
                    "course",
                    course
            );

            Long teacherId = readLong(
                    course,
                    "teacherId",
                    "teacherID",
                    "docenteId"
            );

            if (teacherId == null) {

                result.putNull("teacher");

                return Mono.just(
                        (JsonNode) result
                );
            }

            return getJson(
                    subjectsUrl
                            + "/api/teachers/"
                            + teacherId
            ).map(teacher -> {

                result.set(
                        "teacher",
                        teacher
                );

                return (JsonNode) result;
            }).onErrorResume(error -> {

                result.putNull("teacher");

                return Mono.just(
                        (JsonNode) result
                );
            });

        }).onErrorResume(error -> {

            result.putNull("course");
            result.putNull("teacher");

            return Mono.just(
                    (JsonNode) result
            );
        });
    }

    private Mono<JsonNode> getJson(String url) {

        return webClient
                .get()
                .uri(url)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        response ->
                                response
                                        .bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .flatMap(body ->
                                                Mono.error(
                                                        new RuntimeException(
                                                                "Downstream service returned HTTP "
                                                                        + response
                                                                                .statusCode()
                                                                                .value()
                                                                        + (
                                                                        body.isBlank()
                                                                                ? ""
                                                                                : ": " + body
                                                                )
                                                        )
                                                )
                                        )
                )
                .bodyToMono(JsonNode.class);
    }

    private JsonNode extractEnrollments(
            JsonNode response) {

        if (response.isArray()) {
            return response;
        }

        if (response.has("content")
                && response.get("content").isArray()) {

            return response.get("content");
        }

        if (response.has("enrollments")
                && response.get("enrollments").isArray()) {

            return response.get("enrollments");
        }

        return objectMapper.createArrayNode();
    }

    private ObjectNode buildResponse(
            JsonNode student,
            List<JsonNode> enrollments) {

        ObjectNode response =
                objectMapper.createObjectNode();

        response.set(
                "student",
                student
        );

        ArrayNode enrollmentArray =
                response.putArray("enrollments");

        enrollments.forEach(
                enrollmentArray::add
        );

        return response;
    }

    private Long readLong(
            JsonNode node,
            String... names) {

        for (String name : names) {

            JsonNode value =
                    node.get(name);

            if (value != null
                    && value.isNumber()) {

                return value.longValue();
            }

            if (value != null
                    && value.isTextual()) {

                try {

                    return Long.parseLong(
                            value.asText()
                    );

                } catch (NumberFormatException ignored) {
                    // Intentar con el siguiente nombre.
                }
            }
        }

        return null;
    }

    private String removeTrailingSlash(
            String url) {

        return url.endsWith("/")
                ? url.substring(
                        0,
                        url.length() - 1
                )
                : url;
    }
}

