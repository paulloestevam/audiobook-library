package com.paulloestevam.audiobooklibrary.repository;

import com.paulloestevam.audiobooklibrary.model.Book;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends MongoRepository<Book, String> {

    @Query(value = "{}", fields = "{ 'subGenre' : 1 }")
    List<String> findDistinctSubGenre();

    Optional<Book> findByTitleAndAuthorAndDuration(String title, String author, String duration);

}