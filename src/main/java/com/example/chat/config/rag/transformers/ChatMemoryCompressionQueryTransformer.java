package com.example.chat.config.rag.transformers;

import java.util.List;

import com.example.chat.context.SessionContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ChatMemoryCompressionQueryTransformer implements QueryTransformer {

    private final ChatMemory chatMemory;
    private final ChatClient chatClient;
    
    public ChatMemoryCompressionQueryTransformer(ChatMemory chatMemory, ChatClient chatClient) {
        this.chatMemory = chatMemory;
        this.chatClient = chatClient;
    }

    @SuppressWarnings("null")
    @Override
    public Query transform(@NonNull Query query) {
        // 현재 세션 ID를 가져와서 사용
        String sessionId = SessionContext.getCurrentSessionId();
        log.debug("ChatMemory 조회 - 세션 ID: {}", sessionId);
        
        List<Message> conversationHistory = chatMemory.get(sessionId);
        
        if (conversationHistory.isEmpty()) {
            log.debug("대화 히스토리가 없음: {}", sessionId);
            return query;
        }
        
        log.debug("대화 히스토리 발견: {} - {} 개 메시지", sessionId, conversationHistory.size());
        
        // 대화 히스토리를 Query 객체로 변환
        Query queryWithHistory = Query.builder()
            .text(query.text())
            .history(conversationHistory)
            .build();
        
        // CompressionQueryTransformer로 압축
        CompressionQueryTransformer compressionTransformer = CompressionQueryTransformer.builder()
            .chatClientBuilder(chatClient.mutate())
            .build();
        
        return compressionTransformer.transform(queryWithHistory);
    }
}
