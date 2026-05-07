package com.paulloestevam.audiobooklibrary.repository;

import com.paulloestevam.audiobooklibrary.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
}