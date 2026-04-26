package com.paulloestevam.audiobooklibrary.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "books")
public class Book {
    @Id
    private String id;
    private String title;
    private String author;
    private String genre;
    private String duration;
    private String rating;
    private Integer reviewsCount;
    private String description;
    private String urlAmazon;
    private String imageFilename;
    private String zipFilename;
    private boolean restricted;
    @CreatedDate
    private LocalDateTime dateAdded;

    public Book(String title, int reviewCount, String rating, String urlAmazon) {
        this.title = title;
        this.rating = rating;
        this.urlAmazon = urlAmazon;
    }
}