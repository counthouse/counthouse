package io.github.counthouse.api.problems;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers the errors Spring MVC does not handle, such as a failure in a servlet filter or a status set with
 * {@code sendError}, as problem details. Replaces Boot's error controller, which answers in its own format.
 */
@RestController
class ProblemErrorController implements ErrorController {

    @RequestMapping("${spring.web.error.path:${error.path:/error}}")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        if (request.getDispatcherType() != DispatcherType.ERROR) {
            // A client asking for the error path itself: there is no error to describe.
            return problem(HttpStatus.NOT_FOUND);
        }
        HttpStatus status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
                ? HttpStatus.resolve(code)
                : null;
        return problem(status != null && status.isError() ? status : HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetail.forStatus(status));
    }
}
