package com.example.chat.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.chat.service.DocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RagConfig {

    private final DocumentService documentService;

    /**
     * 애플리케이션 시작 시 문서 로드 및 임베딩 저장
     */
    @Bean
    public CommandLineRunner loadDocuments() {
        return args -> {
            log.info("RAG 시스템 초기화 시작");
            int count = documentService.loadDocuments();
            log.info("RAG 시스템 초기화 완료 - {} 문서 처리됨", count);
        };
    }
}