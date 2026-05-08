package com.paulloestevam.audiobooklibrary.controller;

import com.paulloestevam.audiobooklibrary.model.Book;
import com.paulloestevam.audiobooklibrary.service.BookService;
import com.paulloestevam.audiobooklibrary.service.UploadZipsService;
import com.paulloestevam.audiobooklibrary.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
@RequestMapping("/")
@RequiredArgsConstructor
public class AudiobookController {

    private final BookService bookService;
    private final UploadZipsService uploadZipsService;
    private final UserService userService;

    @GetMapping("/books-all")
    public List<Book> getAllBooks() {
        log.info("Request: Listar todos os livros getAllBooks");
        return bookService.findAll();
    }

    @GetMapping("/books")
    public List<Book> getPublicBooks() {
        log.info("Request: Listar todos os livros públicos");
        return bookService.findBooksByRestriction(false);
    }

    @GetMapping("/books-restricted")
    public List<Book> getRestrictedBooks() {
        log.info("Request: Listar todos os livros restritos");
        return bookService.findBooksByRestriction(true);
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
        log.info("Request: Salvar novo livro createBook {}", book.getTitle());
        return bookService.save(book);
    }

    @PutMapping("/books/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable String id, @RequestBody Book bookDetails) {
        log.info("Request: Editar livro updateBook ID {}", id);
        Book updatedBook = bookService.update(id, bookDetails);
        return ResponseEntity.ok(updatedBook);
    }

    @DeleteMapping("/books/{id}")
    public void deleteBook(@PathVariable String id) {
        log.info("Request: Deletar livro deleteBook {}", id);
        bookService.delete(id);
    }

    @PatchMapping("/books/{id}/toggle-restriction")
    public Book toggleRestriction(@PathVariable String id) {
        log.info("Request: Alternar restrição do livro toggleRestriction {}", id);
        return bookService.toggleRestriction(id);
    }

    @PatchMapping("/books/{id}/genre")
    public Book updateGenre(@PathVariable String id, @RequestBody String genre) {
        log.info("Request: Atualizar gênero do livro updateGenre {}", id);
        return bookService.updateGenre(id, genre);
    }

    @PostMapping("/books/upload-zips")
    public ResponseEntity<String> uploadZips(@RequestParam("files") MultipartFile[] files) {
        log.info("Request: Upload de {} arquivos uploadZips", files.length);
        uploadZipsService.uploadZipFiles(files);
        return ResponseEntity.ok("Upload concluído com sucesso!");
    }

    @GetMapping("/books/subgenres")
    public List<String> getSubGenres() {
        log.info("Request: Listar todos os subgêneros distintos getSubGenres");
        return bookService.findAllSubGenres();
    }

    @GetMapping("/users/favorites")
    public ResponseEntity<List<String>> getFavorites() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        log.info("Request: Buscando favoritos do usuário {} getFavorites", email);
        List<String> favorites = userService.getFavorites(email);

        return ResponseEntity.ok(favorites);
    }

    @PatchMapping("/users/favorites/{bookId}")
    public ResponseEntity<List<String>> toggleFavorite(@PathVariable String bookId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        log.info("Request: Toggle favorite para o livro {} do usuário {} toggleFavorite", bookId, email);
        List<String> updatedFavorites = userService.toggleFavorite(email, bookId);

        return ResponseEntity.ok(updatedFavorites);
    }

    @PostMapping("/scan-download-folder")
    public ResponseEntity<String> scanDownloadFolder() {
        log.info("Request: Scan download folder scanDownloadFolder");
        uploadZipsService.scanDownloadFolder();
        return ResponseEntity.ok("Upload concluído com sucesso!");
    }

    @PostMapping("/seed")
    public String seedData() {
        log.info("Request: Executar Seed seedData");
        bookService.seedDatabase();
        return "Livros de teste criados com sucesso!";
    }
}