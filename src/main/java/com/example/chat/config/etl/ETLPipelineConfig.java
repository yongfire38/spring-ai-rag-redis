package com.example.chat.config.etl;

import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.chat.config.etl.readers.MyMarkdownReader;
import com.example.chat.config.etl.readers.PdfDocumentReader;
import com.example.chat.config.etl.transformers.EnhancedDocumentTransformer;
import com.example.chat.config.etl.transformers.MyContentFormatTransformer;
import com.example.chat.config.etl.writers.VectorStoreWriter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ETLPipelineConfig {

    @Bean
    public MyMarkdownReader markdownReader() {
        log.info("MyMarkdownReader 빈 생성");
        return new MyMarkdownReader();
    }

    @Bean
    public PdfDocumentReader pdfReader() {
        log.info("PdfDocumentReader 빈 생성");
        return new PdfDocumentReader();
    }

    @Bean
    public MyContentFormatTransformer myContentFormatTransformer() {
        log.info("MyContentFormatTransformer 빈 생성");
        return new MyContentFormatTransformer();
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