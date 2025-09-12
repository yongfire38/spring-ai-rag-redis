package com.example.chat.service.impl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.stereotype.Service;

import com.example.chat.context.SessionContext;
import com.example.chat.config.ThinkTagAwareOutputConverter;
import com.example.chat.response.TechnologyResponse;
import com.example.chat.service.SessionAwareChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionAwareChatServiceImpl implements SessionAwareChatService {

    private final Advisor retrievalAugmentationAdvisor;
    private final ChatClient ollamaChatClient;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    
    // StructuredOutputConverter 인스턴스들 (<think> 태그 처리)
    private final StructuredOutputConverter<TechnologyResponse> technologyOutputConverter = 
        ThinkTagAwareOutputConverter.of(TechnologyResponse.class);

    /**
     * 세션별 RAG 기반 스트리밍 응답 생성
     */
    @Override
    public Flux<ChatResponse> streamRagResponse(String query, String model) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("세션별 RAG 기반 스트리밍 질의 수신: {}, 모델: {}, 세션: {}", query, model, sessionId);

        try {
            // Spring AI 표준 방식: ChatClient가 자동으로 메시지 히스토리 관리
            log.debug("세션 {} RAG 응답 생성 시작", sessionId);
            
            // ChatClient 빌더 생성 (모델 옵션 포함)
            ChatClient.ChatClientRequestSpec requestSpec = ollamaChatClient.prompt()
                    .user(query);
            
            // 모델 지정이 있는 경우 옵션 추가
            if (model != null && !model.trim().isEmpty()) {
                requestSpec = requestSpec.options(ChatOptions.builder().model(model).temperature(0.3).build());
                log.debug("지정된 모델로 응답 생성: {}", model);
            } else {
                log.debug("기본 모델로 응답 생성");
            }
            
            // ChatMemory 어드바이저와 RAG 어드바이저 적용하여 스트리밍 응답 생성 (문맥 유지)
            return requestSpec
                    .advisors(messageChatMemoryAdvisor, retrievalAugmentationAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .stream()
                    .chatResponse();

        } catch (Exception e) {
            log.error("세션별 RAG 스트리밍 응답 생성 중 오류 발생 - 세션: {}", sessionId, e);
            return Flux.error(e);
        }
    }

    /**
     * 세션별 일반 스트리밍 응답 생성
     */
    @Override
    public Flux<ChatResponse> streamSimpleResponse(String query, String model) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("세션별 일반 스트리밍 질의 수신: {}, 모델: {}, 세션: {}", query, model, sessionId);

        try {
            // Spring AI 표준 방식: ChatClient가 자동으로 메시지 히스토리 관리
            log.debug("세션 {} 일반 응답 생성 시작", sessionId);
            
            // ChatClient 빌더 생성 (모델 옵션 포함)
            ChatClient.ChatClientRequestSpec requestSpec = ollamaChatClient.prompt()
                    .user(query);
            
            // 모델 지정이 있는 경우 옵션 추가
            if (model != null && !model.trim().isEmpty()) {
                requestSpec = requestSpec.options(ChatOptions.builder().model(model).temperature(0.3).build());
                log.debug("지정된 모델로 응답 생성: {}", model);
            } else {
                log.debug("기본 모델로 응답 생성");
            }
            
            // ChatMemory 어드바이저 적용하여 일반 스트리밍 응답 생성 (문맥 유지)
            return requestSpec
                    .advisors(messageChatMemoryAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .stream()
                    .chatResponse();
            
        } catch (Exception e) {
            log.error("세션별 일반 스트리밍 응답 생성 중 오류 발생 - 세션: {}", sessionId, e);
            return Flux.error(e);
        }
    }
    
    /**
     * JSON 구조화된 출력 - 기술 정보
     * 
     * @param query 사용자 질의
     * @return JSON 구조화된 기술 정보 응답
     */
    @Override
    public TechnologyResponse getTechnologyInfoAsJson(String query) {
        log.info("기술 정보 JSON 응답 생성: {}", query);
        
        try {
            // 커스텀 StructuredOutputConverter 사용하여 <think> 태그 처리
            return ollamaChatClient.prompt()
                    .user(u -> u.text("다음 질문에 대해 기술 정보를 제공해주세요: {query}")
                               .param("query", query))
                    .call()
                    .entity(technologyOutputConverter);
                    
        } catch (Exception e) {
            log.error("기술 정보 JSON 응답 생성 중 오류 발생", e);
            return new TechnologyResponse("알 수 없음", "알 수 없음", "오류가 발생했습니다", null, null);
        }
    }
}
