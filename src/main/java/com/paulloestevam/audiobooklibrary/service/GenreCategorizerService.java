package com.paulloestevam.audiobooklibrary.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GenreCategorizerService {

    private final Set<String> ficcaoTags = new HashSet<>();
    private final Set<String> naoFiccaoTags = new HashSet<>();
    private final Set<String> literaturaTags = new HashSet<>();

    @PostConstruct
    public void init() {
        ficcaoTags.addAll(loadTags("subgenre_ficcao.txt"));
        naoFiccaoTags.addAll(loadTags("subgenre_nao_ficcao.txt"));
        literaturaTags.addAll(loadTags("subgenre_literatura.txt"));
    }

    private Set<String> loadTags(String filename) {
        try {
            return new BufferedReader(new InputStreamReader(
                    new ClassPathResource(filename).getInputStream()))
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    public String defineGenre(String subGenre) {
        if (subGenre == null) return "Outros";
        String lowerSub = subGenre.toLowerCase();

        if (literaturaTags.stream().anyMatch(lowerSub::contains)) return "Literatura";
        if (naoFiccaoTags.stream().anyMatch(lowerSub::contains)) return "Não-ficção";
        if (ficcaoTags.stream().anyMatch(lowerSub::contains)) return "Ficção";

        return "00 Outros";
    }
}
