package com.example.chat.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.stereotype.Service;

import com.example.chat.context.SessionContext;
import com.example.chat.config.RedisChatMemoryRepository;
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
    private final RedisChatMemoryRepository redisChatMemoryRepository;
    
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
            // 세션별 대화 히스토리 가져오기 (RedisChatMemoryRepository에서 직접 조회)
            List<Message> conversationHistory = redisChatMemoryRepository.findByConversationId(sessionId);
            log.debug("세션 {} 대화 히스토리: {} 개 메시지", sessionId, conversationHistory.size());
            
            // 새로운 가변 리스트 생성 후 히스토리 복사
            List<Message> messages = new ArrayList<>(conversationHistory);
            
            // 현재 사용자 메시지 추가
            UserMessage currentUserMessage = new UserMessage(query);
            messages.add(currentUserMessage);
            
            // Prompt 생성
            Prompt prompt;
            if (model != null && !model.trim().isEmpty()) {
                prompt = new Prompt(messages, ChatOptions.builder().model(model).temperature(0.3).build());
                log.debug("지정된 모델로 응답 생성: {}", model);
            } else {
                prompt = new Prompt(messages);
                log.debug("기본 모델로 응답 생성");
            }
            
            // 응답 내용을 수집하기 위한 AtomicReference
            AtomicReference<StringBuilder> responseBuilder = new AtomicReference<>(new StringBuilder());
            
            // RAG 어드바이저 적용하여 스트리밍 응답 생성
            return ollamaChatClient.prompt(prompt)
                    .advisors(retrievalAugmentationAdvisor)
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> {
                        // 각 청크를 누적
                        if (response.getResult() != null && response.getResult().getOutput() != null) {
                            String chunk = response.getResult().getOutput().getText();
                            if (chunk != null && !chunk.trim().isEmpty()) {
                                responseBuilder.get().append(chunk);
                            }
                        }
                    })
                    .doOnComplete(() -> {
                        // 응답 완료 후 RedisChatMemoryRepository에 직접 저장
                        try {
                            String fullResponse = responseBuilder.get().toString();
                            if (!fullResponse.isEmpty()) {
                                // 현재 메시지 목록 가져오기
                                List<Message> currentMessages = new ArrayList<>(redisChatMemoryRepository.findByConversationId(sessionId));
                                
                                // 사용자 메시지와 AI 응답 추가
                                currentMessages.add(new UserMessage(query));
                                currentMessages.add(new AssistantMessage(fullResponse));
                                
                                // Redis에 저장
                                redisChatMemoryRepository.saveAll(sessionId, currentMessages);
                                log.debug("RedisChatMemoryRepository 업데이트 완료 - 세션: {}, 응답 길이: {}", sessionId, fullResponse.length());
                            }
                        } catch (Exception e) {
                            log.error("RedisChatMemoryRepository 업데이트 실패 - 세션: {}", sessionId, e);
                        }
                    });

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
            // 세션별 대화 히스토리 가져오기 (RedisChatMemoryRepository에서 직접 조회)
            List<Message> conversationHistory = redisChatMemoryRepository.findByConversationId(sessionId);
            log.debug("세션 {} 대화 히스토리: {} 개 메시지", sessionId, conversationHistory.size());
            
            // 새로운 가변 리스트 생성 후 히스토리 복사
            List<Message> messages = new ArrayList<>(conversationHistory);
            
            // 현재 사용자 메시지 추가
            UserMessage currentUserMessage = new UserMessage(query);
            messages.add(currentUserMessage);
            
            // Prompt 생성
            Prompt prompt;
            if (model != null && !model.trim().isEmpty()) {
                prompt = new Prompt(messages, ChatOptions.builder().model(model).temperature(0.3).build());
                log.debug("지정된 모델로 응답 생성: {}", model);
            } else {
                prompt = new Prompt(messages);
                log.debug("기본 모델로 응답 생성");
            }
            
            // 응답 내용을 수집하기 위한 AtomicReference
            AtomicReference<StringBuilder> responseBuilder = new AtomicReference<>(new StringBuilder());
            
            // 일반 스트리밍 응답 생성 (RAG 어드바이저 없이)
            return ollamaChatClient.prompt(prompt)
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> {
                        // 각 청크를 누적
                        if (response.getResult() != null && response.getResult().getOutput() != null) {
                            String chunk = response.getResult().getOutput().getText();
                            if (chunk != null && !chunk.trim().isEmpty()) {
                                responseBuilder.get().append(chunk);
                            }
                        }
                    })
                    .doOnComplete(() -> {
                        // 응답 완료 후 RedisChatMemoryRepository에 직접 저장
                        try {
                            String fullResponse = responseBuilder.get().toString();
                            if (!fullResponse.isEmpty()) {
                                // 현재 메시지 목록 가져오기
                                List<Message> currentMessages = new ArrayList<>(redisChatMemoryRepository.findByConversationId(sessionId));
                                
                                // 사용자 메시지와 AI 응답 추가
                                currentMessages.add(new UserMessage(query));
                                currentMessages.add(new AssistantMessage(fullResponse));
                                
                                // Redis에 저장
                                redisChatMemoryRepository.saveAll(sessionId, currentMessages);
                                log.debug("RedisChatMemoryRepository 업데이트 완료 - 세션: {}, 응답 길이: {}", sessionId, fullResponse.length());
                            }
                        } catch (Exception e) {
                            log.error("RedisChatMemoryRepository 업데이트 실패 - 세션: {}", sessionId, e);
                        }
                    });
            
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
