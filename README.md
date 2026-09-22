# API Gateway

Single entry point for the Distributed Systems project.

The Gateway exposes port **8080** and provides:

1. Proxy routing to the `students`, `subjects`, and `enrollments` services.
2. Transparent forwarding of HTTP method, query parameters and request body.
3. Controlled `502 Bad Gateway` responses when a downstream service is unavailable.
4. A Backend for Frontend (BFF) endpoint that composes student information from all three services.
5. Swagger documentation for the Gateway-owned composition endpoint.

## Service routes

| Gateway path | Downstream service |
|---|---|
| `/api/students/**` | `STUDENTS_SERVICE_URL` |
| `/api/programs/**` | `STUDENTS_SERVICE_URL` |
| `/api/contacts/**` | `STUDENTS_SERVICE_URL` |
| `/api/documents/**` | `STUDENTS_SERVICE_URL` |
| `/api/subjects/**` | `SUBJECTS_SERVICE_URL` |
| `/api/courses/**` | `SUBJECTS_SERVICE_URL` |
| `/api/teachers/**` | `SUBJECTS_SERVICE_URL` |
| `/api/enrollments/**` | `ENROLLMENTS_SERVICE_URL` |
| `/api/grades/**` | `ENROLLMENTS_SERVICE_URL` |
| `/api/enrollment-status-history/**` | `ENROLLMENTS_SERVICE_URL` |

Spring Cloud Gateway forwards the original HTTP method, query string and request body without custom transformation.

## Student detail composition

The Gateway owns:

```text
GET /api/students/{id}/detail
```

The endpoint performs these calls:

```text
Client
  |
  | GET /api/students/{id}/detail
  v
API Gateway
  |
  +--> GET students-service/api/students/{id}
  |
  +--> GET enrollments-service/api/enrollments?studentId={id}
  |       |
  |       +--> for each enrollment:
  |              GET subjects-service/api/courses/{courseId}
  |                    |
  |                    +--> GET subjects-service/api/teachers/{teacherId}
  |
  v
One combined JSON response
```

The Gateway supports an enrollments response as either a JSON array, a paginated object with
`content`, or an object with an `enrollments` array.

The composed response has this shape:

```json
{
  "student": {
    "id": 12,
    "firstName": "Laura",
    "lastName": "Gomez"
  },
  "enrollments": [
    {
      "id": 40,
      "studentId": 12,
      "courseId": 7,
      "grade": 4.5,
      "course": {
        "id": 7,
        "subjectId": 4,
        "teacherId": 3
      },
      "teacher": {
        "id": 3,
        "firstName": "Ana",
        "lastName": "Ruiz"
      }
    }
  ]
}
```

The original enrollment fields are preserved and `course` and `teacher` are added by the Gateway.

## Error handling

If a downstream service cannot be reached, times out, or closes the connection unexpectedly,
the Gateway remains running and returns:

```json
{
  "timestamp": "2026-09-22T10:30:00",
  "status": 502,
  "error": "Bad Gateway",
  "message": "The requested service is currently unavailable",
  "path": "/api/students/12"
}
```

If a service returns an HTTP error during composition, the Gateway returns a controlled error
instead of crashing.

## Configuration

Local defaults:

```text
STUDENTS_SERVICE_URL=http://localhost:8081
SUBJECTS_SERVICE_URL=http://localhost:8082
ENROLLMENTS_SERVICE_URL=http://localhost:8083
```

Docker example:

```text
STUDENTS_SERVICE_URL=http://students-service:8081
SUBJECTS_SERVICE_URL=http://subjects-service:8082
ENROLLMENTS_SERVICE_URL=http://enrollments-service:8083
```

## Running

```bash
mvn spring-boot:run
```

Gateway:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

Health:

```text
http://localhost:8080/actuator/health
```

## Docker

```bash
docker build -t api-gateway .
docker run -p 8080:8080 \
  -e STUDENTS_SERVICE_URL=http://students-service:8081 \
  -e SUBJECTS_SERVICE_URL=http://subjects-service:8082 \
  -e ENROLLMENTS_SERVICE_URL=http://enrollments-service:8083 \
  api-gateway
```

The Gateway and the three downstream services must be attached to the same Docker network.

## Tests

```bash
mvn test
```

The test suite verifies routing, HTTP method forwarding, query parameters, request bodies,
downstream failure handling, and the student-detail composition flow.
