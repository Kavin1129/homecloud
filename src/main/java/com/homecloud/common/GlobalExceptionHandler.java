package com.homecloud.common;

import com.homecloud.auth.InvalidCredentialsException;
import com.homecloud.auth.TooManyLoginAttemptsException;
import com.homecloud.user.UsernameAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleUsernameAlreadyExists(
            UsernameAlreadyExistsException exception) {

        return new ApiError(HttpStatus.CONFLICT.value(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidationError(
            MethodArgumentNotValidException exception) {

        return new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid request"
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleInvalidCredentials(
            InvalidCredentialsException exception) {

        return new ApiError(
                HttpStatus.UNAUTHORIZED.value(),
                exception.getMessage()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        return ResponseEntity
                .badRequest()
                .body(new ApiError(
                        400,
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex) {

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError(
                        413,
                        "File size exceeds the maximum allowed limit"
                ));
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyLoginAttempts(
            TooManyLoginAttemptsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiError(429, exception.getMessage()));
    }
}