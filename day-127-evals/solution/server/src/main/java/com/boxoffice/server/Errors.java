package com.boxoffice.server;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns problems into short JSON answers a gate can show: {"error":"..."}. */
@RestControllerAdvice
class Errors {

    @ExceptionHandler(ApiProblem.class)
    ResponseEntity<Map<String, String>> problem(ApiProblem problem) {
        return ResponseEntity.status(problem.status()).body(Map.of("error", problem.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "That isn't a sale"));
    }
}
