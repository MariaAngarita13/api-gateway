package co.edu.uptc.apigateway.controller;

import co.edu.uptc.apigateway.service.StudentDetailService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Gateway-owned Backend-for-Frontend endpoint.
 *
 * Unlike the proxy routes, this endpoint performs orchestration and returns
 * one response assembled from the three domain services.
 */
@RestController
@Tag(
        name = "Student Detail",
        description = "Composed student information across students, enrollments and subjects services"
)
public class DetailController {

    private final StudentDetailService studentDetailService;

    public DetailController(StudentDetailService studentDetailService) {
        this.studentDetailService = studentDetailService;
    }

    @Operation(
            summary = "Get complete student detail",
            description = """
                    Returns one composed JSON document.
                    The Gateway retrieves the student, the student's enrollments,
                    and then the course and teacher associated with each enrollment.
                    """
    )
    @GetMapping("/api/students/{id}/detail")
    public Mono<JsonNode> getStudentDetail(
            @Parameter(description = "Student identifier", example = "12")
            @PathVariable Long id) {

        return studentDetailService.getStudentDetail(id);
    }
}
