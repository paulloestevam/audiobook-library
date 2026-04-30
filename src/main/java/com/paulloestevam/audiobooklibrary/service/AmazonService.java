package com.paulloestevam.audiobooklibrary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulloestevam.audiobooklibrary.model.Book;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
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

    @Value("${file.downloads-dir}")
    private String downloadsDir;

    public void scanAmazon() throws Exception {
        File folder = new File(downloadsDir);

        // Alterado para filtrar apenas arquivos .zip
        File[] zipFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
        List<Book> bookList = new ArrayList<>();

        if (zipFiles != null && zipFiles.length > 0) {
            log.info("Encontrados {} arquivos zip para escanear.", zipFiles.length);

            for (File zip : zipFiles) {
                // Remove a extensão .zip para usar como título de busca
                String fileName = zip.getName();
                String titleForSearch = fileName.substring(0, fileName.lastIndexOf('.'));

                log.info("Processando busca para: {}", titleForSearch);

                String rating = "";
                int reviewCount = 0;
                String url = "";

                // Delay para evitar block da Amazon
                long delayAmazon = random.nextLong(5000, 10001);
                log.info("Waiting {}ms for Amazon rate limit...", delayAmazon);
                Thread.sleep(delayAmazon);

                try {
                    // Limpa o nome do arquivo para a busca (remove caracteres especiais de regex que você já usava)
                    String searchQuery = titleForSearch.replaceAll("\\(.*?\\)|\\[.*?\\]|(?i)\\(?Unabridged\\)?", "").trim();

                    log.info("searchQuery:{}", searchQuery);
                    String searchUrl = "https://www.amazon.com.br/s?k=" + URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
                    log.info("searchUrl:{}", searchUrl);

                    Document doc = Jsoup.connect(searchUrl).userAgent(userAgent).get();
                    String html = doc.html();

                    url = extractUrl(html);
                    rating = extractRating(html);
                    reviewCount = extractReviewCount(html);

                    log.info("Resultado -> Rating: {}, Reviews: {}, URL: {}", rating, reviewCount, url);

                } catch (Exception e) {
                    e.printStackTrace();
                    log.error("Erro ao buscar dados na Amazon para {}: {}", titleForSearch, e.getMessage());
                }

                // Adiciona à lista usando o construtor resumido que você possui no Model
                bookList.add(new Book(titleForSearch, reviewCount, rating, url));
            }
        } else {
            log.warn("Nenhum arquivo .zip encontrado em: {}", downloadsDir);
        }

        log.info("Gerando arquivo amazon_books_scan.json...");
        Files.writeString(Paths.get(downloadsDir, "amazon_books_scan.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(bookList),
                StandardCharsets.UTF_8);

        log.info("Scan concluído com sucesso.");
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