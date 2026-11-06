package com.victory.apartment.controller;

import com.victory.apartment.service.PriceRuleException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PriceRuleExceptionHandler {

    @ExceptionHandler(PriceRuleException.class)
    public ResponseEntity<Map<String, String>> handle(PriceRuleException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("code", exception.getCode(), "message", exception.getMessage()));
    }
}
