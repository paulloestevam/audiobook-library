package com.paulloestevam.audiobooklibrary.controller;

import com.paulloestevam.audiobooklibrary.model.Book;
import com.paulloestevam.audiobooklibrary.service.AmazonService;
import com.paulloestevam.audiobooklibrary.service.BookService;
import com.paulloestevam.audiobooklibrary.service.UploadZipsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
@RequestMapping("/audiobook-library")
@RequiredArgsConstructor
public class AudiobookController {

    private final BookService bookService;
    private final AmazonService amazonService;
    private final UploadZipsService uploadZipsService;

    @GetMapping("/books")
    public List<Book> getAllBooks() {
        log.info("Request: Listar todos os livros");
        return bookService.findAll();
    }

    @GetMapping("/books/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable String id) {
        log.info("Request: Buscar livro por ID {}", id);
        return bookService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/books")
    public Book createBook(@RequestBody Book book) {
        log.info("Request: Salvar novo livro {}", book.getTitle());
        return bookService.save(book);
    }

    @PutMapping("/books/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable String id, @RequestBody Book bookDetails) {
        log.info("Request: Editar livro ID {}", id);
        Book updatedBook = bookService.update(id, bookDetails);
        return ResponseEntity.ok(updatedBook);
    }

    @DeleteMapping("/books/{id}")
    public void deleteBook(@PathVariable String id) {
        log.info("Request: Deletar livro {}", id);
        bookService.delete(id);
    }

    @PostMapping("/seed")
    public String seedData() {
        log.info("Request: Executar Seed");
        bookService.seedDatabase();
        return "Livros de teste criados com sucesso!";
    }

    @PatchMapping("/books/{id}/toggle-restriction")
    public Book toggleRestriction(@PathVariable String id) {
        log.info("Request: Alternar restrição do livro {}", id);
        return bookService.toggleRestriction(id);
    }

    @PatchMapping("/books/{id}/genre")
    public Book updateGenre(@PathVariable String id, @RequestBody String genre) {
        log.info("Request: Atualizar gênero do livro {}", id);
        return bookService.updateGenre(id, genre);
    }

//    @GetMapping("/scan-amazon")
//    public String scanAmazon() throws Exception {
//        log.info("Request: Scan Amazon");
//        amazonService.scanAmazonByDirectory();
//        return "Finished scan";
//    }

    @PostMapping("/books/upload-zips")
    public ResponseEntity<String> uploadZips(@RequestParam("files") MultipartFile[] files) {
        log.info("Request: Upload de {} arquivos", files.length);
        uploadZipsService.uploadZipFiles(files);
        return ResponseEntity.ok("Upload concluído com sucesso!");
    }

    @PostMapping("/books/scan-download-folder")
    public ResponseEntity<String> scanDownloadFolder() {
        log.info("Request: Scan download folder");
        uploadZipsService.scanDownloadFolder();
        return ResponseEntity.ok("Upload concluído com sucesso!");
    }

    @GetMapping("/books/subgenres")
    public List<String> getSubGenres() {
        log.info("Request: Listar todos os subgêneros distintos");
        return bookService.findAllSubGenres();
    }
}