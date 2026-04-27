package com.paulloestevam.audiobooklibrary.controller;

import com.paulloestevam.audiobooklibrary.model.Book;
import com.paulloestevam.audiobooklibrary.repository.BookRepository;
import com.paulloestevam.audiobooklibrary.service.AudiobookService;
import com.paulloestevam.audiobooklibrary.service.GeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Random;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
@RequestMapping("/audiobook-library")
@RequiredArgsConstructor
public class AudiobookController {

    private final AudiobookService service;
    private final GeneratorService generatorService;
    private final BookRepository bookRepository;

    @GetMapping("/books")
    public List<Book> getAllBooks() {
        log.info("Listando todos os livros");
        return bookRepository.findAll();
    }

    @PostMapping("/books")
    public Book createBook(@RequestBody Book book) {
        log.info("Salvando novo livro: {}", book.getTitle());
        return bookRepository.save(book);
    }

    @PostMapping("/seed")
    public String seedData() {
        bookRepository.deleteAll();

        for (int i = 1; i < 20; i++) {
            Book book = new Book();
            book.setTitle("Livro de Teste " + i);
            book.setAuthor("Autor Exemplo " + i);
            book.setGenre(List.of("Ficção", "Não-ficção", "Literatura").get(new Random().nextInt(3)));
            book.setSubGenre(List.of("Auto-Ajuda", "Romance", "Poesia", "Drama", "Policial").get(new Random().nextInt(5)));
            book.setDuration("0" + new Random().nextInt(1, 9) + ":"
                    + new Random().nextInt(10, 59) + ":00");
            book.setRating("4." + i);
            book.setReviewsCount(i * new Random().nextInt(2, 126));
            book.setDescription(new Random().ints(new Random().nextInt(20, 201)
                            , 0, 15)
                    .mapToObj(idx -> new String[]{"lorem", "ipsum", "dolor"
                            , "sit", "amet", "consectetur", "adipiscing", "elit", "sed", "do", "eiusmod"
                            , "tempor", "incididunt", "ut", "labore", "et"}
                            [idx]).collect(java.util.stream.Collectors
                            .joining(" ")));
            book.setUrlAmazon("https://amazon.com.br/book" + i);
            book.setImageFilename("audiobook_" + i + ".jpg");
            book.setZipFilename("audiobook_" + i + ".zip");
            book.setRestricted(i > 12 && i % 2 == 0);
            bookRepository.save(book);
        }

        return "10 livros de teste criados com sucesso!";
    }


    @GetMapping("/scan-amazon")
    public String scanAmazon() {
        try {
            log.info("Calling endpoint /scan-amazon");
            service.scanAmazon();
            return "Finished endpoint /scan-amazon";
        } catch (Exception e) {
            return "Error during scan: " + e.getMessage();
        }
    }

    @GetMapping("/generate-library")
    public String generateLibrary() {
        try {
            log.info("Calling endpoint /generate-library");
            generatorService.generate();
            return "Library generated successfully!";
        } catch (Exception e) {
            log.error("Error generating library", e);
            return "Error: " + e.getMessage();
        }
    }
}