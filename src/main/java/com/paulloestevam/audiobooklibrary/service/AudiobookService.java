package com.paulloestevam.audiobooklibrary.service;

import com.paulloestevam.audiobooklibrary.model.Book;
import com.paulloestevam.audiobooklibrary.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AudiobookService {

    private final BookRepository bookRepository;
    private final HandleFileService handleFileService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public List<Book> findAll() {
        return bookRepository.findAll();
    }

    public Book save(Book book) {
        return bookRepository.save(book);
    }

    public void delete(String id) {
        if (!bookRepository.existsById(id)) {
            throw new RuntimeException("Livro não encontrado com o ID: " + id);
        }
        bookRepository.deleteById(id);
        log.info("Livro com ID {} foi removido com sucesso.", id);
    }

    public void seedDatabase() {
        bookRepository.deleteAll();
        Random random = new Random();

        int contBooks = 20;
        for (int i = 1; i < contBooks; i++) {
            Book book = new Book();
            book.setTitle("Livro de Teste " + i);
            book.setAuthor("Autor Exemplo " + i);
            book.setGenre(List.of("Ficção", "Não-ficção", "Literatura").get(random.nextInt(3)));
            book.setSubGenre(List.of("Auto-Ajuda", "Romance", "Poesia", "Drama", "Policial").get(random.nextInt(5)));
            book.setDuration("0" + random.nextInt(1, 9) + ":" + random.nextInt(10, 59) + ":00");
            book.setRating("4." + i);
            book.setReviewsCount(i * random.nextInt(2, 126));

            // Lógica do Lorem Ipsum mantida aqui
            String description = random.ints(random.nextInt(20, 201), 0, 15)
                    .mapToObj(idx -> new String[]{"lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit", "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore", "et"}[idx])
                    .collect(Collectors.joining(" "));

            book.setDescription(description);
            book.setUrlAmazon("https://amazon.com.br/book" + i);
            book.setImageFilename("audiobook_" + i + ".jpg");
            book.setZipFilename("audiobook_" + i + ".zip");
            book.setRestricted(i > 12 && i % 2 == 0);

            bookRepository.save(book);
        }
        log.info("Seed finalizado com {} livros.", contBooks);
    }

    public Book toggleRestriction(String id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Livro não encontrado"));
        book.setRestricted(!book.isRestricted());
        return bookRepository.save(book);
    }

    public Book updateGenre(String id, String newGenre) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Livro não encontrado"));
        book.setGenre(newGenre);
        return bookRepository.save(book);
    }

    public void uploadZipFiles(MultipartFile[] files) {
        handleFileService.handleZipfile(files);
//        for (MultipartFile file : files) {
//            String filename = file.getOriginalFilename();
//            if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
//                throw new RuntimeException("Apenas arquivos .zip são permitidos: " + filename);
//            }
//
//            try {
//                Path root = Paths.get(uploadDir);
//                if (!Files.exists(root)) {
//                    Files.createDirectories(root);
//                }
//
//                Path targetPath = root.resolve(filename);
//                Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
//
//                log.info("Arquivo salvo com sucesso: {}", targetPath.toAbsolutePath());
//            } catch (IOException e) {
//                throw new RuntimeException("Erro ao salvar o arquivo: " + filename, e);
//            }
    }
}
