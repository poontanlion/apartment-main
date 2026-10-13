package com.victory.apartment.controller;

import com.victory.apartment.model.Booking;
import com.victory.apartment.service.BookingLeaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingLeaseController {
    private final BookingLeaseService service;

    public BookingLeaseController(BookingLeaseService service) {
        this.service = service;
    }

    @PostMapping("/{id}/lease")
    public Booking create(@PathVariable String id,
                          @RequestBody BookingLeaseService.LeaseApplication application,
                          HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String ownerEmail = session == null ? null : (String) session.getAttribute("userEmail");
        return service.createLease(id, ownerEmail, application);
    }

    @ExceptionHandler(BookingLeaseService.LeaseSubmissionException.class)
    public ResponseEntity<Map<String, Object>> handleError(BookingLeaseService.LeaseSubmissionException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("code", exception.getCode(),
                        "message", exception.getReason() == null ? "Unable to create lease agreement." : exception.getReason(),
                        "fieldErrors", exception.getFieldErrors()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST", "message", "The submitted data is invalid."));
    }
}
