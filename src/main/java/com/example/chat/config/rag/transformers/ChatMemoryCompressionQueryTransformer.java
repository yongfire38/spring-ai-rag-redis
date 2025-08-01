package com.example.chat.config.rag.transformers;

import java.util.List;

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
        // 기본 대화 ID 사용
        List<Message> conversationHistory = chatMemory.get(ChatMemory.DEFAULT_CONVERSATION_ID);
        
        if (conversationHistory.isEmpty()) {
            return query;
        }
        
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
