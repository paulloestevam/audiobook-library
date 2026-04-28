package com.paulloestevam.audiobooklibrary.service;


import com.paulloestevam.audiobooklibrary.model.Book;
import com.paulloestevam.audiobooklibrary.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class HandleFileService {

    private final BookRepository bookRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public void handleZipfile(MultipartFile[] files) {
        log.info("Iniciando processamento de {} arquivo(s).", files.length);
        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
                log.warn("Arquivo ignorado (não é ZIP): {}", filename);
                continue;
            }

            try {
                log.info("Processando arquivo: {}", filename);
                Path root = Paths.get(uploadDir);
                if (!Files.exists(root)) Files.createDirectories(root);

                Path targetZipPath = root.resolve(filename);
                log.info("Salvando ZIP original em: {}", targetZipPath);
                Files.copy(file.getInputStream(), targetZipPath, StandardCopyOption.REPLACE_EXISTING);

                Path tempDir = root.resolve("temp").resolve(filename.replace(".zip", ""));
                log.info("Criando pasta temporária para extração: {}", tempDir);
                Files.createDirectories(tempDir);

                log.info("Iniciando descompactação (FileSystem API)...");
                unzip(targetZipPath, tempDir);
                log.info("Descompactação concluída.");

                log.info("Iniciando extração de metadados e durações via FFmpeg...");
                Book book = extractMetadataWithFFmpeg(tempDir, filename);

                log.info("Salvando livro '{}' no MongoDB...", book.getTitle());
                bookRepository.save(book);
                log.info("Livro '{}' salvo com sucesso.", book.getTitle());

                log.info("Limpando pasta temporária...");
                deleteDirectory(tempDir);
                log.info("Processamento de '{}' finalizado com sucesso.", filename);

            } catch (Exception e) {
                log.error("ERRO CRÍTICO ao processar arquivo {}: {}", filename, e.getMessage(), e);
            }
        }
    }

    private Book extractMetadataWithFFmpeg(Path tempDir, String zipFilename) throws IOException {
        List<Path> audioFiles = Files.walk(tempDir)
                .filter(p -> p.toString().toLowerCase().matches(".*\\.(mp3|m4a|m4b)$"))
                .sorted()
                .collect(Collectors.toList());

        if (audioFiles.isEmpty()) {
            log.error("Nenhum áudio encontrado em {}", tempDir);
            throw new RuntimeException("Nenhum arquivo de áudio encontrado no ZIP.");
        }

        log.info("Total de arquivos de áudio encontrados: {}", audioFiles.size());

        Book book = new Book();
        book.setZipFilename(zipFilename);
        book.setImageFilename(zipFilename.replace(".zip", ".jpg"));
        book.setRestricted(false);

        log.info("Extraindo metadados do primeiro arquivo: {}", audioFiles.get(0).getFileName());
        processMetadata(audioFiles.get(0), book);

        long totalSeconds = 0;
        int count = 0;
        for (Path audio : audioFiles) {
            count++;
            if (count % 5 == 0) log.info("Calculando duração... progresso: {}/{}", count, audioFiles.size());
            totalSeconds += getDurationInSeconds(audio);
        }

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        book.setDuration(String.format("%02dh%02dm", hours, minutes));
        log.info("Duração total calculada: {} ({} segundos)", book.getDuration(), totalSeconds);

        return book;
    }

    private void processMetadata(Path audioFile, Book book) {
        log.info(">>>> Extraindo metadados do arquivo: {}", audioFile.getFileName());

        try {
            Process process = new ProcessBuilder("ffmpeg", "-i", audioFile.toString(), "-f", "ffmetadata", "-")
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[FFmpeg Raw Output] {}", line);

                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        String key = parts[0].toLowerCase().trim();
                        String value = parts[1].trim();

                        // --- Lógica Existente ---
                        if (key.equals("album")) book.setTitle(value);
                        else if (key.equals("artist") || key.equals("album_artist")) book.setAuthor(value);
                        else if (key.equals("genre")) book.setSubGenre(value);

                        // --- Nova Lógica de Descrição com Prioridade ---
                        // LONG_COMMENT | comment | description | synopsis
                        processDescription(key, value, book);
                    }
                }
            }

            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                log.warn("TIMEOUT: FFmpeg não respondeu em 10s para o arquivo {}", audioFile.getFileName());
                process.destroyForcibly();
            }

            // Fallbacks
            if (book.getTitle() == null || book.getTitle().isBlank()) {
                book.setTitle(audioFile.getFileName().toString());
            }
            if (book.getAuthor() == null || book.getAuthor().isBlank()) {
                book.setAuthor("Desconhecido");
            }

            log.info("<<<< Fim da extração de metadados para: {}", audioFile.getFileName());

        } catch (Exception e) {
            log.error("ERRO ao processar metadados de {}: {}", audioFile.getFileName(), e.getMessage());
        }
    }

    /**
     * Gerencia a prioridade da descrição.
     * Regra: LONG_COMMENT > comment > description > synopsis
     */
    private void processDescription(String key, String value, Book book) {
        if (value.isBlank()) return;

        // Se for a maior prioridade, seta sempre
        if (key.equals("long_comment")) {
            book.setDescription(value);
        }
        // Para as demais, só seta se o campo ainda estiver vazio
        // ou se o que estiver lá for de uma prioridade menor.
        else if (key.equals("comment")) {
            // Se já tiver um LONG_COMMENT, não sobrescreve.
            // Mas como não sabemos a ordem que as linhas chegam,
            // uma abordagem simples é usar um Map temporário ou checar se é nulo.
            if (book.getDescription() == null) book.setDescription(value);
        }
        else if (key.equals("description")) {
            if (book.getDescription() == null) book.setDescription(value);
        }
        else if (key.equals("synopsis")) {
            if (book.getDescription() == null) book.setDescription(value);
        }
    }

    private long getDurationInSeconds(Path audioFile) {
        try {
            // FFmpeg envia informações de duração para o stderr por padrão
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
            log.error("Erro ao obter duração de {}: {}", audioFile.getFileName(), e.getMessage());
        }
        return 0;
    }

    private void unzip(Path zipFile, Path destDir) throws IOException {
        Map<String, Object> env = Map.of("encoding", "IBM850");
        try (FileSystem zipFs = FileSystems.newFileSystem(zipFile, env)) {
            Path root = zipFs.getPath("/");
            Files.walk(root).forEach(sourcePath -> {
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
                    log.error("Erro ao extrair item {} do ZIP", sourcePath);
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }


}

