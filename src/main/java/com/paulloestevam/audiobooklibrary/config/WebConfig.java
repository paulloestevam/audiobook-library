package com.paulloestevam.audiobooklibrary.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.images-dir}")
    private String imagesDir;

    @Value("${file.downloads-dir}")
    private String downloadsDir;

    // Configuração de Recursos Estáticos (Imagens)
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String imgLocation = formatPath(imagesDir);
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + imgLocation);

        String downloadLocation = formatPath(downloadsDir);
        registry.addResourceHandler("/downloads/**")
                .addResourceLocations("file:" + downloadLocation);
    }

    private String formatPath(String path) {
        return path.endsWith("/") || path.endsWith("\\") ? path : path + "/";
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        // Permite localhost e qualquer IP na sub-rede 192.168.1.x
                        .allowedOriginPatterns(
                                "http://localhost:5173",
                                "http://192.168.1.*:5173"
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}