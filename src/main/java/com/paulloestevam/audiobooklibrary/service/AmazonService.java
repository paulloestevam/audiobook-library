package com.paulloestevam.audiobooklibrary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulloestevam.audiobooklibrary.model.Book;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AmazonService {

    private final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private final ObjectMapper mapper = new ObjectMapper();
    private final Random random = new Random();
    private final FFprobe ffprobe;

    @Value("${file.downloads-dir}")
    private String downloadsDir;

    public AmazonService() throws IOException {
        this.ffprobe = new FFprobe("ffprobe");
    }

    public void scanAmazon() throws Exception {
        File folder = new File(downloadsDir);
        File[] directories = folder.listFiles(File::isDirectory);
        List<Book> bookList = new ArrayList<>();

        if (directories != null) {
            for (File dir : directories) {
                String title = dir.getName();
                File[] audioFiles = dir.listFiles((d, name) -> name.matches(".*\\.(mp3|m4a|m4b)$"));

                if (audioFiles != null && audioFiles.length > 0) {
                    title = extractAlbumMetadata(audioFiles[0].getAbsolutePath(), title);
                    log.info("ID3 Title: {}", title);
                }

                String rating = "";
                int reviewCount = 0;
                String url = "";

                Long delayAmazon = random.nextLong(5000, 16001);
                log.info("Waiting {} for scan amazon...", delayAmazon);
                Thread.sleep(delayAmazon);

                try {
                    String searchQuery = title.replaceAll("\\(.*?\\)|\\[.*?\\]|\\.mp3|(?i)\\(?Unabridged\\)?", "").trim();
                    String searchUrl = "https://www.amazon.com.br/s?k=" + URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);

                    Document doc = Jsoup.connect(searchUrl).userAgent(userAgent).get();
                    String html = doc.html();

                    url = extractUrl(html);
                    rating = extractRating(html);
                    reviewCount = extractReviewCount(html);
                    log.info("rating: {}, reviewCount: {}, url: {}", rating, reviewCount, url);


                } catch (Exception e) {
                    e.printStackTrace();
                }
                bookList.add(new Book(title, reviewCount, rating, url));
            }

        } else {
            log.info("Folder is empty!");
        }

        log.info("Writing file amazon_books_scan.json");
        Files.writeString(Paths.get(downloadsDir, "amazon_books_scan.json"), mapper.writeValueAsString(bookList), StandardCharsets.UTF_8);

        log.info("Scan completed successfully");
    }

    private String extractAlbumMetadata(String filePath, String defaultTitle) {
        try {
            FFmpegProbeResult probeResult = ffprobe.probe(filePath);
            FFmpegFormat format = probeResult.getFormat();

            if (format.tags != null && format.tags.containsKey("album")) {
                return format.tags.get("album");
            }
        } catch (Exception e) {
            log.error("Could not extract metadata for file: {}", filePath, e);
            return defaultTitle;
        }
        return defaultTitle;
    }

    private String extractUrl(String html) {
        Pattern pattern = Pattern.compile("href=\"(/[^\" ]+/dp/[A-Z0-9]{10}[^\"]*)\"");
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? "https://www.amazon.com.br" + matcher.group(1) : "";
    }

    private String extractRating(String html) {
        Pattern pattern = Pattern.compile("(\\d+,\\d) de 5 estrelas");
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private int extractReviewCount(String html) {
        Pattern pattern = Pattern.compile("aria-label=\"([\\d,.]+(?:\\s+mil)?)\\s+(classificações|avaliações)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String temp = matcher.group(1).toLowerCase().trim();
            if (temp.contains("mil")) {
                temp = temp.replace("mil", "").replace(" ", "").replace(",", ".");
                return (int) (Double.parseDouble(temp) * 1000);
            }
            return Integer.parseInt(temp.replace(".", "").replace(",", ""));
        }
        return 0;
    }
}