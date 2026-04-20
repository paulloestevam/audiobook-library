package com.paulloestevam.audiobooklibrary.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookData {
    private String title;
    private int reviewsQuantity;
    private String ratingText;
    private String url;
}