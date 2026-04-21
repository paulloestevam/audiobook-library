package com.paulloestevam.audiobooklibrary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulloestevam.audiobooklibrary.model.BookData;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
@Slf4j
public class GeneratorService {

    private final String rootPath = "C:\\projetos\\audiobook-library\\src\\main\\resources\\ZZ_BOOKS_TEMP";
    private final String libraryPath = rootPath + "\\00 biblioteca";
    private final String imagesPath = libraryPath + "\\images";
    private final ObjectMapper mapper = new ObjectMapper();
    private final FFprobe ffprobe;
    private final FFmpeg ffmpeg;

    public GeneratorService() throws IOException {
        this.ffprobe = new FFprobe("ffprobe");
        this.ffmpeg = new FFmpeg("ffmpeg");
    }

    public void generate() throws Exception {
        createDirectories();
        List<BookData> amazonData = loadAmazonData();

        File folder = new File(rootPath);
        File[] directories = folder.listFiles(f -> f.isDirectory() && !f.getName().equals("00 biblioteca"));

        StringBuilder htmlBody = new StringBuilder();
        int totalBooks = (directories != null) ? directories.length : 0;

        if (directories != null) {
            for (File dir : directories) {
                processBook(dir, amazonData, htmlBody);
            }
        }

        String finalHtml = getHtmlHeader(totalBooks) + htmlBody.toString() + getHtmlFooter();
        Files.writeString(Paths.get(libraryPath, "index.html"), finalHtml, StandardCharsets.UTF_8);

        resizeImages();
    }

    private void createDirectories() throws IOException {
        Files.createDirectories(Paths.get(imagesPath));
        Files.createDirectories(Paths.get(libraryPath, "downloads"));
    }

    private List<BookData> loadAmazonData() throws IOException {
        Path jsonPath = Paths.get(rootPath, "amazon_books_scan.json");
        if (Files.exists(jsonPath)) {
            return mapper.readValue(Files.readAllBytes(jsonPath), new TypeReference<>() {
            });
        }
        return List.of();
    }

    private void processBook(File dir, List<BookData> amazonData, StringBuilder htmlBody) throws IOException {
        File[] audioFiles = dir.listFiles((d, name) -> name.matches(".*\\.(mp3|m4a|m4b)$"));
        File[] jpgFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg"));

        String safeName = dir.getName().replaceAll("[^a-zA-Z0-9\\-\\.]", "_");
        String imgSrc = "https://via.placeholder.com/300x300?text=Sem+Capa";

        if (jpgFiles != null && jpgFiles.length > 0) {
            Path destImg = Paths.get(imagesPath, safeName + ".jpg");
            Files.copy(jpgFiles[0].toPath(), destImg, StandardCopyOption.REPLACE_EXISTING);
            imgSrc = "images/" + safeName + ".jpg";
        }

        String album = dir.getName().replaceAll("(?i)\\(?Unabridged\\)?", "").trim();
        final String searchAlbum = album;
        String artist = "Desconhecido";
        String genre = "-";
        String desc = "Sem descrição.";
        double totalDurationSeconds = 0;

        if (audioFiles != null && audioFiles.length > 0) {
            try {
                FFmpegFormat format = ffprobe.probe(audioFiles[0].getAbsolutePath()).getFormat();
                if (format.tags != null) {
                    artist = format.tags.getOrDefault("artist", artist);
                    album = format.tags.getOrDefault("album", album).replaceAll("(?i)\\(?Unabridged\\)?", "").trim();
                    genre = format.tags.getOrDefault("genre", genre);
                    desc = format.tags.getOrDefault("comment", format.tags.getOrDefault("description", desc));
                }

                for (File file : audioFiles) {
                    totalDurationSeconds += ffprobe.probe(file.getAbsolutePath()).getFormat().duration;
                }
            } catch (Exception e) {
                log.error("Error probing file in {}", dir.getName());
            }
        }

        BookData matchedBook = amazonData.stream()
                .filter(b -> b.getTitle().equalsIgnoreCase(searchAlbum) || dir.getName().contains(b.getTitle()))
                .findFirst()
                .orElse(null);

        htmlBody.append(buildCardHtml(album, artist, genre, desc, totalDurationSeconds, matchedBook, imgSrc, dir.getName()));
    }

    private String buildCardHtml(String title, String artist, String genre, String desc, double duration, BookData amazon, String imgSrc, String folderName) {
        String ratingHtml = "<div class=\"rating-none\">Sem avaliações</div>";
        double ratingValue = 0;
        int reviews = 0;

        if (amazon != null) {
            ratingValue = Double.parseDouble(amazon.getRatingText().replace(",", "."));
            reviews = amazon.getReviewsQuantity();
            String url = amazon.getUrl();
            ratingHtml = (url != null && !url.isEmpty())
                    ? "<a href=\"" + url + "\" target=\"_blank\" class=\"rating-container\" onclick=\"event.stopPropagation()\">"
                    : "<div class=\"rating-container\">";
            ratingHtml += "<span class=\"rating-stars\">&#9733; " + amazon.getRatingText() + "</span>";
            if (reviews > 0) ratingHtml += "<span class=\"rating-count\">(" + reviews + ")</span>";
            ratingHtml += (url != null && !url.isEmpty()) ? "</a>" : "</div>";
        }

        long hours = (long) (duration / 3600);
        long minutes = (long) ((duration % 3600) / 60);
        String formattedTime = hours + "h" + minutes + "m";

        return String.format("""
                <div class="book-card" onclick="toggleCard(this)" data-title="%s" data-author="%s" data-duration="%.0f" data-rating="%.1f" data-review-count="%d">
                    <div class="cover-wrapper">
                        <img src="%s" alt="BG" class="cover-bg-blur" loading="lazy">
                        <img src="%s" alt="Capa" class="cover-img" loading="lazy">
                        <a href="downloads/%s.zip" download class="btn-download" title="Baixar .zip" onclick="event.stopPropagation()">
                            <svg viewBox="0 0 24 24"><path d="M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z"/></svg>
                        </a>
                    </div>
                    <div class="section-title"><div class="title">%s</div></div>
                    <div class="section-meta">
                        %s
                        <div class="author">Autor: %s <span class="grid-time">(%s)</span></div>
                        <div class="list-time">Duração: %s</div>
                    </div>
                    <div class="section-desc">
                        <div class="meta-tag">%s</div>
                        <div class="description">%s</div>
                    </div>
                </div>
                """, title, artist, duration, ratingValue, reviews, imgSrc, imgSrc, folderName, title, ratingHtml, artist, formattedTime, formattedTime, genre, desc);
    }

    private void resizeImages() throws IOException {
        File folder = new File(imagesPath);
        File[] images = folder.listFiles(f -> f.getName().toLowerCase().endsWith(".jpg"));
        if (images == null) return;

        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
        for (File img : images) {
            String tempName = imagesPath + "\\temp_" + img.getName();
            FFmpegBuilder builder = new FFmpegBuilder()
                    .overrideOutputFiles(true)
                    .setInput(img.getAbsolutePath())
                    .done()
                    .addOutput(tempName)
                    .setVideoFilter("scale=416:-1")
                    .setFormat("mjpeg")
                    .done();
            executor.createJob(builder).run();
            Files.move(Paths.get(tempName), img.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }


    private String getHtmlHeader(int total) {

        String styleContent = getCodeFromResource("html_block_header.html");

        return """
                                <!DOCTYPE html>
                                <html lang="pt-BR">
                                <head>
                                    <meta charset="UTF-8">
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                    <title>Biblioteca</title>
                                    %s                    
                                </head>
                                <body>
                                <div class="controls">
                                    <div class="header-top">
                                        <div class="header-title">
                                            <h1>&#128218; Biblioteca</h1>
                                            <span class="book-count">Total de livros: %d</span>
                                        </div>
                                        <a href="$LinkApk" class="apk-link" title="Baixar APK Player">&#128242; Baixar App Player</a>
                
                                    </div>
                                    <div class="header-bottom">
                                        <div class="search-group">
                                            <input type="text" id="search-input" class="search-input" placeholder="Pesquisar..." onkeyup="filterBooks()">
                                            <select id="sort-select" class="sort-select" onchange="sortBooks()">
                                                <option value="title">Nome</option>
                                                <option value="author">Autor</option>
                                                <option value="duration">Duração</option>
                                                <option value="rating">Popularidade</option>
                                            </select>
                                        </div>
                                        <div class="btn-group">
                                            <button onclick="setMode('grid')" id="btn-grid" >Blocos</button>
                                            <button onclick="setMode('compact')" id="btn-compact" class="active">Compacto</button>
                                            <button onclick="setMode('list')" id="btn-list">Lista</button>
                                        </div>
                                    </div>
                                </div>
                                <div id="book-container" class="mode-compact">
                """.formatted(styleContent, total);
    }

    private String getHtmlFooter() {
        return """
                </div>
                <script>
                    /* ... script original omitido para brevidade ... */
                </script>
                </body></html>
                """;
    }

    private String getCodeFromResource(String filename) {
        try {
            Path path = Paths.get("src/main/resources/" + filename);
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Erro ao carregar arquivo de estilo", e);
            return "";
        }
    }
}
