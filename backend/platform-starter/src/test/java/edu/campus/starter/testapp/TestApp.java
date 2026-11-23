package edu.campus.starter.testapp;

import edu.campus.kernel.error.CampusProblem;
import edu.campus.kernel.error.ProblemCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A tiny app used only by the starter's tests. It has the starter on its classpath, like a real
 * app, plus endpoints that fail in each of the ways the problem handler must cover.
 */
@SpringBootApplication
public class TestApp {

    record NewCourse(@NotBlank String name, @Max(30) int credits) {}

    @RestController
    static class FailingController {

        @GetMapping("/test/ok")
        Map<String, String> ok() {
            return Map.of("status", "fine");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new CampusProblem(ProblemCode.CONFLICT, "Section S1 has no seats left");
        }

        @PostMapping("/test/courses")
        void create(@Valid @RequestBody NewCourse course) {}

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("db password is hunter2");
        }
    }
}
