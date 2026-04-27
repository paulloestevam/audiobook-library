package com.paulloestevam.audiobooklibrary.controller;

import com.paulloestevam.audiobooklibrary.model.Book;
import com.paulloestevam.audiobooklibrary.service.AmazonService;
import com.paulloestevam.audiobooklibrary.service.AudiobookService;
import com.paulloestevam.audiobooklibrary.service.GeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:5174")
@Slf4j
@RequestMapping("/audiobook-library")
@RequiredArgsConstructor
public class AudiobookController {

    private final AudiobookService audiobookService;
    private final AmazonService amazonService;
    private final GeneratorService generatorService;

    @GetMapping("/books")
    public List<Book> getAllBooks() {
        log.info("Request: Listar todos os livros");
        return audiobookService.findAll();
    }

    @PostMapping("/books")
    public Book createBook(@RequestBody Book book) {
        log.info("Request: Salvar novo livro {}", book.getTitle());
        return audiobookService.save(book);
    }

    @DeleteMapping("/books/{id}")
    public void deleteBook(@PathVariable String id) {
        log.info("Request: Deletar livro {}", id);
        audiobookService.delete(id);
    }

    @PostMapping("/seed")
    public String seedData() {
        log.info("Request: Executar Seed");
        audiobookService.seedDatabase();
        return "Livros de teste criados com sucesso!";
    }

    @PatchMapping("/books/{id}/toggle-restriction")
    public Book toggleRestriction(@PathVariable String id) {
        log.info("Request: Alternar restrição do livro {}", id);
        return audiobookService.toggleRestriction(id);
    }

    @PatchMapping("/books/{id}/genre")
    public Book updateGenre(@PathVariable String id, @RequestBody String genre) {
        log.info("Request: Atualizar gênero do livro {}", id);
        return audiobookService.updateGenre(id, genre);
    }

    @GetMapping("/scan-amazon")
    public String scanAmazon() throws Exception {
        log.info("Request: Scan Amazon");
        amazonService.scanAmazon();
        return "Finished scan";
    }

    @GetMapping("/generate-library")
    public String generateLibrary() throws Exception {
        log.info("Request: Generate Library");
        generatorService.generate();
        return "Library generated successfully!";
    }
}