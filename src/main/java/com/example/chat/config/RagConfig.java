package com.example.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.chat.config.rag.transformers.ChatMemoryCompressionQueryTransformer;
import com.example.chat.util.RagPromptTemplates;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RagConfig {

    @Value("${rag.prompt.pattern}")
    private String promptPattern;

    @Bean
    public ChatClient chatClient(OllamaChatModel chatModel, MessageChatMemoryAdvisor messageChatMemoryAdvisor) {
        log.info("ChatClient 구성: Chat Memory 어드바이저 추가해서 생성");
        
        return ChatClient.builder(chatModel)
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build();
    }

    @Bean
    public Advisor retrievalAugmentationAdvisor(RedisVectorStore redisVectorStore, ChatClient chatClient, ChatMemory chatMemory) {
        log.info("RAG 프롬프트 패턴 설정: {}", promptPattern);
        
        PromptTemplate selectedPromptTemplate = getPromptTemplateByPattern(promptPattern);
        
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(
                    // Chat Memory를 활용하는 커스텀 transformer
                    // 대화의 맥락을 유지하면서 불완전한 질문을 완전한 질문으로 변환
                    new ChatMemoryCompressionQueryTransformer(chatMemory, chatClient),
                    // 검색 엔진이나 벡터 스토어에 최적화된 형태로 쿼리 재작성
                    RewriteQueryTransformer.builder()
                        .chatClientBuilder(chatClient.mutate())
                        .build())
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(0.30)
                        .vectorStore(redisVectorStore)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .promptTemplate(selectedPromptTemplate)
                        .build())
                .build();
    }

    /**
     * 패턴에 따라 적절한 프롬프트 템플릿을 반환
     */
    private PromptTemplate getPromptTemplateByPattern(String pattern) {
        return switch (pattern.toLowerCase()) {
            case "basic" -> RagPromptTemplates.createBasicRagPrompt();
            case "zero-shot" -> RagPromptTemplates.createZeroShotRagPrompt();
            case "few-shot" -> RagPromptTemplates.createFewShotRagPrompt();
            case "chain-of-thought" -> RagPromptTemplates.createChainOfThoughtRagPrompt();
            case "structured" -> RagPromptTemplates.createStructuredRagPrompt();
            case "expert" -> RagPromptTemplates.createExpertRagPrompt();
            case "concise" -> RagPromptTemplates.createConciseRagPrompt();
            case "educational" -> RagPromptTemplates.createEducationalRagPrompt();
            case "role-based" -> RagPromptTemplates.createRoleBasedRagPrompt();
            case "step-by-step" -> RagPromptTemplates.createStepByStepRagPrompt();
            default -> {
                log.warn("알 수 없는 프롬프트 패턴 '{}'입니다. 기본값(few-shot)을 사용합니다.", pattern);
                yield RagPromptTemplates.createFewShotRagPrompt();
            }
        };
    }
}