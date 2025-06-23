package com.example.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RagConfig {

    private final RedisVectorStore redisVectorStore;

    /**
     * RAG Advisor 설정
     */
    @Bean
    public Advisor retrievalAugmentationAdvisor() {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold((double) 0.60f)
                        .vectorStore(redisVectorStore)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true) // 빈 컨텍스트 허용
                        .build())
                .build();
    }

    /**
     * ChatClient Bean 설정
     */
    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}