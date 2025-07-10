package com.example.chat.service.impl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatResponse;

import org.springframework.stereotype.Service;

import com.example.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final Advisor retrievalAugmentationAdvisor;
    private final ChatClient ollamaChatClient;

    /**
     * RAG 기반 스트리밍 응답 생성
     * 
     * @param query 사용자 질의
     * @return 스트리밍 응답 Flux
     */
    @Override
    public Flux<ChatResponse> streamRagResponse(String query) {
        log.info("RAG 기반 스트리밍 질의 수신: {}", query);

        try {
            return ollamaChatClient.prompt()
                    .advisors(retrievalAugmentationAdvisor)
                    .user(query).stream().chatResponse();

        } catch (Exception e) {
            log.error("AI 스트리밍 응답 생성 중 오류 발생", e);
            return Flux.error(e);
        }
    }

    /**
     * 일반 스트리밍 응답 생성
     * 
     * @param query 사용자 질의
     * @return 스트리밍 응답 Flux
     */
    @Override
    public Flux<ChatResponse> streamSimpleResponse(String query) {
        log.info("일반 스트리밍 질의 수신: {}", query);

        try {
            // 일반 스트리밍 응답 생성 (RAG 없이)
            return ollamaChatClient.prompt()
                    .user(query).stream().chatResponse();
        } catch (Exception e) {
            log.error("AI 스트리밍 응답 생성 중 오류 발생", e);
            return Flux.error(e);
        }
    }
}
