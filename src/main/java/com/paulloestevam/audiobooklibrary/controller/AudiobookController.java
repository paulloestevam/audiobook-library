package com.paulloestevam.audiobooklibrary.controller;

import com.paulloestevam.audiobooklibrary.service.AudiobookService;
import com.paulloestevam.audiobooklibrary.service.GeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/audiobook-library")
@RequiredArgsConstructor
public class AudiobookController {

    private final AudiobookService service;
    private final GeneratorService generatorService;

    @GetMapping("/scan-amazon")
    public String scanAmazon() {
        try {
            log.info("Calling endpoint /scan-amazon");
            service.scanAmazon();
            return "Finished endpoint /scan-amazon";
        } catch (Exception e) {
            return "Error during scan: " + e.getMessage();
        }
    }

    @GetMapping("/generate-library")
    public String generateLibrary() {
        try {
            log.info("Calling endpoint /generate-library");
            generatorService.generate();
            return "Library generated successfully!";
        } catch (Exception e) {
            log.error("Error generating library", e);
            return "Error: " + e.getMessage();
        }
    }
}