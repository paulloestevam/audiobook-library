package com.paulloestevam.audiobooklibrary.service;

import com.paulloestevam.audiobooklibrary.model.User;
import com.paulloestevam.audiobooklibrary.repository.BookRepository;
import com.paulloestevam.audiobooklibrary.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
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

    public List<String> toggleFavorite(String email, String bookId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + email));

        List<String> favorites = user.getFavoriteBookIds();

        if (favorites.contains(bookId)) {
            log.info("Removendo livro {} dos favoritos de {}", bookId, email);
            favorites.remove(bookId);
        } else {
            log.info("Adicionando livro {} aos favoritos de {}", bookId, email);
            favorites.add(bookId);
        }

        userRepository.save(user);
        return favorites;
    }

    public List<String> getFavorites(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + email));

        return user.getFavoriteBookIds();
    }

}