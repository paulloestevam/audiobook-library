package com.paulloestevam.audiobooklibrary.controller;

import com.paulloestevam.audiobooklibrary.service.AudiobookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/audiobook-library")
@RequiredArgsConstructor
public class AudiobookController {

    private final AudiobookService service;

    @GetMapping("/scan-amazon")
    public String scanAmazon(@RequestParam(required = false, defaultValue = "false") boolean noAmazon) {
        try {
            log.info("Starting scan...");
            service.scanAndSave(noAmazon);
            return "Scan completed successfully.";
        } catch (Exception e) {
            return "Error during scan: " + e.getMessage();
        }
    }
}