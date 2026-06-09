package com.onlineshopping.orchestrator.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackageClasses = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            detail.setTitle("Unauthorized");
        } else if (ex.getStatusCode() == HttpStatus.CONFLICT) {
            detail.setTitle("Conflict");
        }
        return detail;
    }
}
