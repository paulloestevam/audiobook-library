package com.paulloestevam.audiobooklibrary.service;

import com.paulloestevam.audiobooklibrary.model.User;
import com.paulloestevam.audiobooklibrary.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public void processUserLogin(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        User user = userRepository.findByEmail(email)
                .orElse(User.builder()
                        .email(email)
                        .favoriteBookIds(new java.util.ArrayList<>())
                        .build());

        user.setName(name);
        user.setPicture(picture);
        user.setLastLogin(LocalDateTime.now());

        userRepository.save(user);
    }
}