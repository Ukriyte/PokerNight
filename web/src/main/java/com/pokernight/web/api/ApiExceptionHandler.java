package com.pokernight.web.api;

import com.pokernight.application.room.RoomFailure;
import com.pokernight.application.room.RoomOperationException;
import com.pokernight.web.session.ApiAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(RoomOperationException.class)
    ResponseEntity<ApiProblem> roomFailure(RoomOperationException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.failure()) {
            case ROOM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_HOST -> HttpStatus.FORBIDDEN;
            case PLAYER_ALREADY_REGISTERED, PLAYER_NOT_PENDING, PLAYER_NOT_APPROVED,
                 ROOM_FULL, SEAT_UNAVAILABLE, GAME_IN_PROGRESS -> HttpStatus.CONFLICT;
            case CODE_GENERATION_EXHAUSTED -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return problem(status, exception.failure().name(), exception.getMessage(), request);
    }

    @ExceptionHandler(ApiAuthenticationException.class)
    ResponseEntity<ApiProblem> authentication(ApiAuthenticationException exception, HttpServletRequest request) {
        return problem(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("request validation failed");
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiProblem> invalidArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request);
    }

    private ResponseEntity<ApiProblem> problem(
        HttpStatus status,
        String code,
        String detail,
        HttpServletRequest request
    ) {
        String slug = code.toLowerCase().replace('_', '-');
        ApiProblem problem = new ApiProblem(
            URI.create("https://pokernight.dev/problems/" + slug),
            status.getReasonPhrase(), status.value(), detail, code, URI.create(request.getRequestURI())
        );
        return ResponseEntity.status(status).body(problem);
    }
}
