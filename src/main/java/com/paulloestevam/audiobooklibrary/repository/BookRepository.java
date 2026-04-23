package com.paulloestevam.audiobooklibrary.repository;

import com.paulloestevam.audiobooklibrary.model.Book;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BookRepository extends MongoRepository<Book, String> {
}