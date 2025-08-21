package com.example.chat.config.etl;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.chat.config.etl.readers.EgovMarkdownReader;
import com.example.chat.config.etl.readers.PdfDocumentReader;
import com.example.chat.config.etl.transformers.EnhancedDocumentTransformer;
import com.example.chat.config.etl.transformers.EgovContentFormatTransformer;
import com.example.chat.config.etl.writers.VectorStoreWriter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ETLPipelineConfig {

    @Bean
    public EgovMarkdownReader markdownReader() {
        log.info("EgovMarkdownReader 빈 생성");
        return new EgovMarkdownReader();
    }

    @Bean
    public PdfDocumentReader pdfReader() {
        log.info("PdfDocumentReader 빈 생성");
        return new PdfDocumentReader();
    }

    @Bean
    public EgovContentFormatTransformer egovContentFormatTransformer() {
        log.info("EgovContentFormatTransformer 빈 생성");
        return new EgovContentFormatTransformer();
    }

    @Bean
    public EnhancedDocumentTransformer enhancedDocumentTransformer(OllamaChatModel ollamaChatModel) {
        log.info("EnhancedDocumentTransformer 빈 생성");
        return new EnhancedDocumentTransformer(ollamaChatModel);
    }

    @Bean
    public VectorStoreWriter vectorStoreWriter(RedisVectorStore redisVectorStore) {
        log.info("VectorStore DocumentWriter 빈 생성");
        return new VectorStoreWriter(redisVectorStore);
    }
} 