package co.digikool.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/__fallback")
public class FallbackController {

    /**
     * Generic fallback for any service.
     */
    @GetMapping(value = "/{service}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> genericFallback(@PathVariable String service) {
        return Map.of(
                "service", service,
                "status", "degraded",
                "message", String.format("%s is temporarily unavailable. Please try again later.", service),
                "timestamp", Instant.now(),
                "code", HttpStatus.SERVICE_UNAVAILABLE.value()
        );
    }

    /**
     * Specific fallback endpoints for each service
     */
    @GetMapping(value = "/schools", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> schoolFallback() {
        return Map.of(
                "service", "school-service",
                "status", "degraded",
                "message", "School service is temporarily unavailable. Please try again later.",
                "timestamp", Instant.now(),
                "code", HttpStatus.SERVICE_UNAVAILABLE.value()
        );
    }

    @GetMapping(value = "/students", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> studentFallback() {
        return Map.of(
                "service", "student-service",
                "status", "degraded",
                "message", "Student service is temporarily unavailable. Please try again later.",
                "timestamp", Instant.now(),
                "code", HttpStatus.SERVICE_UNAVAILABLE.value()
        );
    }

    @GetMapping(value = "/teachers", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> teacherFallback() {
        return Map.of(
                "service", "teacher-service",
                "status", "degraded",
                "message", "Teacher service is temporarily unavailable. Please try again later.",
                "timestamp", Instant.now(),
                "code", HttpStatus.SERVICE_UNAVAILABLE.value()
        );
    }

    @GetMapping(value = "/courses", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> courseFallback() {
        return Map.of(
                "service", "course-service",
                "status", "degraded",
                "message", "Course service is temporarily unavailable. Please try again later.",
                "timestamp", Instant.now(),
                "code", HttpStatus.SERVICE_UNAVAILABLE.value()
        );
    }

    @GetMapping(value = "/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> notificationFallback() {
        return Map.of(
                "service", "notification-service",
                "status", "degraded",
                "message", "Notification service is temporarily unavailable. Please try again later.",
                "timestamp", Instant.now(),
                "code", HttpStatus.SERVICE_UNAVAILABLE.value()
        );
    }
}