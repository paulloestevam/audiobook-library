package com.paulloestevam.audiobooklibrary.service;

import com.paulloestevam.audiobooklibrary.model.Book;
import com.paulloestevam.audiobooklibrary.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class UploadZipsService {

    private final BookRepository bookRepository;
    private final AmazonService amazonService;
    private final GenreCategorizerService genreCategorizerService;

    @Value("${app.downloads-dir}")
    private String downloadsDir;

    @Value("${app.images-dir}")
    private String imagesDir;

    public void scanDownloadFolder() {
        log.info("Iniciando scan da pasta {}", downloadsDir);
        Path root = Paths.get(downloadsDir);

        if (!Files.exists(root)) {
            log.warn("Pasta de downloads não encontrada: {}", downloadsDir);
            return;
        }

        try (Stream<Path> stream = Files.list(root)) {
            List<Path> zipFiles = stream
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> path.toString().toLowerCase().endsWith(".zip"))
                    .collect(Collectors.toList());

            log.info("Encontrados {} arquivos ZIP para processar.", zipFiles.size());

            for (Path zipPath : zipFiles) {
                processSingleZipFile(zipPath);
            }
        } catch (IOException e) {
            log.error("Erro ao listar arquivos da pasta de downloads: {}", e.getMessage());
        }
    }

    public void uploadZipFiles(MultipartFile[] files) {
        log.info("Iniciando processamento de {} arquivo(s) via upload.", files.length);

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
                log.warn("Arquivo ignorado (não é ZIP): {}", filename);
                continue;
            }

            try {
                Path root = Paths.get(downloadsDir);
                if (!Files.exists(root)) Files.createDirectories(root);
                Path targetZipPath = root.resolve(filename);
                Files.copy(file.getInputStream(), targetZipPath, StandardCopyOption.REPLACE_EXISTING);
                processSingleZipFile(targetZipPath);
            } catch (Exception e) {
                log.error("ERRO ao fazer upload do arquivo {}: {}", filename, e.getMessage());
            }
        }
    }

    private void processSingleZipFile(Path targetZipPath) {
        String filename = targetZipPath.getFileName().toString();
        log.info("Processando ZIP: {}", filename);

        Path tempDir = targetZipPath.getParent().resolve("temp").resolve(filename.replace(".zip", ""));

        try {
            if (!Files.exists(tempDir)) Files.createDirectories(tempDir);
            unzip(targetZipPath, tempDir);
            Book book = processBookInformation(tempDir, filename);

            Optional<Book> existingBook = bookRepository.findByTitleAndAuthorAndDuration(
                    book.getTitle(), book.getAuthor(), book.getDuration());

            if (existingBook.isPresent()) {
                log.info("Livro '{}' já existe no banco de dados. Pulando.", book.getTitle());
            } else {
                copyCoverImage(tempDir, book);
                extractAmazonInformation(book);
                bookRepository.save(book);
                log.info("Livro '{}' processado e salvo com sucesso.", book.getTitle());
            }

        } catch (Exception e) {
            log.error("Erro ao processar o arquivo {}: {}", filename, e.getMessage());
        } finally {
            try {
                deleteDirectory(tempDir);
            } catch (IOException e) {
                log.error("Erro ao limpar pasta temporária {}: {}", tempDir, e.getMessage());
            }
        }
    }

    private void copyCoverImage(Path tempDir, Book book) throws IOException {
        Path imagesPath = Paths.get(imagesDir);

        if (!Files.exists(imagesPath)) Files.createDirectories(imagesPath);

        try (Stream<Path> stream = Files.walk(tempDir)) {
            Optional<Path> imageFile = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".jpg") || p.toString().toLowerCase().endsWith(".jpeg"))
                    .findFirst();

            if (imageFile.isPresent()) {
                Path source = imageFile.get();
                String originalName = source.getFileName().toString();
                String cleanedName = originalName.replaceAll("\\s*\\[.*?\\]", "").trim();

                Path target = imagesPath.resolve(cleanedName);

                // Redimensionamento com Thumbnailator
                Thumbnails.of(source.toFile())
                        .size(620, 620)
                        .outputFormat("jpg")
                        .outputQuality(0.90) // 90% de qualidade para um bom equilíbrio de tamanho
                        .toFile(target.toFile());

                book.setImageFilename(cleanedName);
                log.info("Imagem redimensionada e salva: {} -> {} (620x620)", originalName, cleanedName);
            } else {
                log.warn("Nenhuma imagem JPG encontrada dentro do ZIP.");
            }
        }
    }

    private Book processBookInformation(Path tempDir, String zipFilename) throws Exception {
        List<Path> audioFiles;

        try (Stream<Path> stream = Files.walk(tempDir)) {
            audioFiles = stream
                    .filter(p -> p.toString().toLowerCase().matches(".*\\.(mp3|m4a|m4b)$"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (audioFiles.isEmpty()) {
            throw new RuntimeException("Nenhum arquivo de áudio encontrado no ZIP.");
        }

        Book book = new Book();
        book.setZipFilename(zipFilename);
        book.setRestricted(false);

        extractMetadataWithFFmpeg(audioFiles.get(0), book);
        extractGenreFromSubGenre(book);

        long totalSeconds = 0;
        for (Path audio : audioFiles) {
            totalSeconds += getDurationInSeconds(audio);
        }

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        book.setDuration(String.format("%02dh%02dm", hours, minutes));

        return book;
    }

    private void extractGenreFromSubGenre(Book book) {
        String definedGenre = genreCategorizerService.defineGenre(book.getSubGenre());
        book.setGenre(definedGenre);
    }

    private void extractAmazonInformation(Book book) throws Exception {
        amazonService.scanAmazonByFile(book);
    }

    private void extractMetadataWithFFmpeg(Path audioFile, Book book) {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-i", audioFile.toString(), "-f", "ffmetadata", "-")
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        String key = parts[0].toLowerCase().trim();
                        String value = parts[1].trim();

                        if (key.equals("album")) {
                            book.setTitle(cleanTitle(value));
                        } else if (key.equals("artist") || key.equals("album_artist")) {
                            book.setAuthor(cleanAuthor(value));
                        } else if (key.equals("genre")) {
                            book.setSubGenre(value);
                        }

                        extractDescription(key, value, book);
                    }
                }
            }

            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (book.getTitle() == null || book.getTitle().isBlank()) {
                book.setTitle(cleanTitle(audioFile.getFileName().toString().replaceFirst("[.][^.]+$", "")));
            }

            if (book.getAuthor() == null || book.getAuthor().isBlank()) {
                book.setAuthor("Desconhecido");
            }

        } catch (Exception e) {
            log.error("ERRO ao processar metadados: {}", e.getMessage());
        }
    }

    private void extractDescription(String key, String value, Book book) {
        if (value.isBlank()) return;

        if (key.equals("long_comment")) {
            book.setDescription(value);
        } else if (List.of("comment", "description", "synopsis").contains(key)) {
            if (book.getDescription() == null) book.setDescription(value);
        }

        book.setDescription(cleanDescription(book.getDescription()));
    }

    private String cleanDescription(String text) {
        if (text == null) return null;

        return text.replace("\\n", " ")
                .replace("\\;", ";")
                .replace("\\\"", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private long getDurationInSeconds(Path audioFile) {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-i", audioFile.toString())
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                Pattern pattern = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+)");
                while ((line = reader.readLine()) != null) {
                    Matcher m = pattern.matcher(line);
                    if (m.find()) {
                        long h = Long.parseLong(m.group(1));
                        long m1 = Long.parseLong(m.group(2));
                        long s = Long.parseLong(m.group(3));
                        return (h * 3600) + (m1 * 60) + s;
                    }
                }
            }

            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Erro ao obter duração: {}", e.getMessage());
        }

        return 0;
    }

    private void unzip(Path zipFile, Path destDir) throws IOException {
        Map<String, Object> env = Map.of("encoding", "IBM850");

        try (FileSystem zipFs = FileSystems.newFileSystem(zipFile, env)) {
            Path root = zipFs.getPath("/");

            try (Stream<Path> stream = Files.walk(root)) {
                stream.forEach(sourcePath -> {
                    try {
                        String relativePath = sourcePath.toString();
                        Path targetPath = destDir.resolve(relativePath.startsWith("/") ? relativePath.substring(1) : relativePath);

                        if (Files.isDirectory(sourcePath)) {
                            Files.createDirectories(targetPath);
                        } else {
                            if (targetPath.getParent() != null) Files.createDirectories(targetPath.getParent());
                            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    private String cleanAuthor(String author) {
        if (author == null || author.isBlank()) return author;

        if (author.toLowerCase().contains("tradutor") || author.toLowerCase().contains("translator")) {
            String[] parts = author.split(",");

            StringBuilder authorsOnly = new StringBuilder();

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (!part.toLowerCase().contains("tradutor") && !part.toLowerCase().contains("translator")) {
                    if (authorsOnly.length() > 0) authorsOnly.append(", ");
                    authorsOnly.append(part);
                }
            }

            String result = authorsOnly.toString();

            if (result.contains(" -")) {
                result = result.split(" -")[0].trim();
            }

            return result.isBlank() ? author : result;
        }

        return author;
    }

    private String cleanTitle(String title) {
        if (title == null) return null;
        return title.replaceAll("(?i)\\s*\\(unabridged\\)\\s*", " ").trim();
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }
}