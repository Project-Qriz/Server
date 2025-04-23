package com.qriz.sqld.controller;

import com.qriz.sqld.dto.ResponseDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final Logger log = LoggerFactory.getLogger(HealthController.class);

    @GetMapping
    public ResponseEntity<?> healthCheck() {
        try {
            log.info("Health Check");

            return new ResponseEntity<>(
                new ResponseDto<>(1, "Health Check successful",
                    Map.of("status", "UP", "timestamp", System.currentTimeMillis())),
                HttpStatus.OK
            );
        } catch (Exception e) {
            log.error("Health Check failed", e);
            return new ResponseEntity<>(
                new ResponseDto<>(-1, "Health Check failed", null),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
