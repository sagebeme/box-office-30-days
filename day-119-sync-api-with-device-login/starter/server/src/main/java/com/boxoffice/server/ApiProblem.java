package com.boxoffice.server;

import org.springframework.http.HttpStatus;

/** Something wrong with a request: answered with this status and {"error": message}. */
class ApiProblem extends RuntimeException {

    private final HttpStatus status;

    ApiProblem(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    HttpStatus status() {
        return status;
    }
}
