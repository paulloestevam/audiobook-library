package com.paulloestevam.audiobooklibrary.controller;

import com.paulloestevam.audiobooklibrary.model.User;
import com.paulloestevam.audiobooklibrary.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PatchMapping("/{id}/toggle-admin")
    public ResponseEntity<User> toggleAdmin(@PathVariable String id) {
        log.info("Request: toggleAdmin para o usuário ID {}", id);
        return ResponseEntity.ok(userService.toggleAdmin(id));
    }

    @PatchMapping("/{id}/toggle-restricted")
    public ResponseEntity<User> toggleRestrictedContent(@PathVariable String id) {
        log.info("Request: toggleRestrictedContent para o usuário ID {}", id);
        return ResponseEntity.ok(userService.toggleRestrictedContent(id));
    }
}
