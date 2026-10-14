package com.cartmate.cartmate.global.exception;

import com.cartmate.cartmate.global.rsData.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.stream.Collectors;

public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<RsData<Void>> handle(ServiceException e) {
        RsData<Void> rsData = e.getRsData();
        return ResponseEntity.status(rsData.getStatusCode()).body(rsData);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RsData<Void>> handle(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " : " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining(" / "));

        return ResponseEntity.status(400).body(new RsData<>("400-COMMON_INVALID_INPUT", msg));
    }
}
