package com.paulloestevam.audiobooklibrary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulloestevam.audiobooklibrary.model.Book;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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
        List<Book> amazonData = loadAmazonData();

        File folder = new File(rootPath);
        File[] directories = folder.listFiles(f -> f.isDirectory() && !f.getName().equals("00 biblioteca"));
    }

    private void createDirectories() throws IOException {
        Files.createDirectories(Paths.get(imagesPath));
        Files.createDirectories(Paths.get(libraryPath, "downloads"));
    }

    private List<Book> loadAmazonData() throws IOException {
        Path jsonPath = Paths.get(rootPath, "amazon_books_scan.json");
        if (Files.exists(jsonPath)) {
            return mapper.readValue(Files.readAllBytes(jsonPath), new TypeReference<>() {
            });
        }
        return List.of();
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


}
