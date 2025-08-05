package com.example.chat.config.etl;

import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.chat.config.etl.readers.MarkdownDocumentReader;
import com.example.chat.config.etl.readers.PdfDocumentReader;
import com.example.chat.config.etl.transformers.EnhancedDocumentTransformer;
import com.example.chat.config.etl.transformers.ContentFormatTransformer;
import com.example.chat.config.etl.writers.VectorStoreWriter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ETLPipelineConfig {

    @Bean
    public DocumentReader markdownReader() {
        log.info("MarkdownDocumentReader 빈 생성");
        return new MarkdownDocumentReader();
    }

    @Bean
    public DocumentReader pdfReader() {
        log.info("PdfDocumentReader 빈 생성");
        return new PdfDocumentReader();
    }

    @Bean
    public DocumentTransformer ContentFormatTransformer() {
        log.info("ContentFormatTransformer 빈 생성");
        return new ContentFormatTransformer();
    }

    @Bean
    public DocumentTransformer enhancedDocumentTransformer(OllamaChatModel ollamaChatModel) {
        log.info("EnhancedDocumentTransformer 빈 생성");
        return new EnhancedDocumentTransformer(ollamaChatModel);
    }

    @Bean
    public DocumentWriter vectorStoreWriter(RedisVectorStore redisVectorStore) {
        log.info("VectorStore DocumentWriter 빈 생성");
        return new VectorStoreWriter(redisVectorStore);
    }
} 