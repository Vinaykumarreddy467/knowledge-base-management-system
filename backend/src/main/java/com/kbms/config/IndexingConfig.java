package com.kbms.config;

import com.kbms.index.TextChunker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IndexingConfig {

    @Bean
    public TextChunker textChunker(AppProperties properties) {
        return new TextChunker(properties);
    }
}
