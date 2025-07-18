package com.example.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RagConfig {

    @Bean
    public ChatClient chatClient(OllamaChatModel chatModel, MessageChatMemoryAdvisor messageChatMemoryAdvisor) {
        log.info("ChatClient 구성: Chat Memory 어드바이저 추가해서 생성");
        return ChatClient.builder(chatModel)
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build();
    }

    @Bean
    public Advisor retrievalAugmentationAdvisor(RedisVectorStore redisVectorStore, ChatClient chatClient) {
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(RewriteQueryTransformer.builder()
                        .chatClientBuilder(chatClient.mutate())
                        .build())
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(0.60)
                        .vectorStore(redisVectorStore)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();
    }
}