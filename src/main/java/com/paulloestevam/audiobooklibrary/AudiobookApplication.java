package com.paulloestevam.audiobooklibrary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class AudiobookApplication {

    public static void main(String[] args) {
        SpringApplication.run(AudiobookApplication.class, args);
    }

}
