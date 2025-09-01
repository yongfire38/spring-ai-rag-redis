package com.example.chat.service.impl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.stereotype.Service;

import com.example.chat.config.ThinkTagAwareOutputConverter;
import com.example.chat.response.TechnologyResponse;
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

    // StructuredOutputConverter 인스턴스들 (<think> 태그 처리)
    private final StructuredOutputConverter<TechnologyResponse> technologyOutputConverter = 
        ThinkTagAwareOutputConverter.of(TechnologyResponse.class);

    /**
     * RAG 기반 스트리밍 응답 생성
     * 
     * @param query 사용자 질의
     * @param model 사용할 모델명 (null이면 기본 모델 사용)
     * @return 스트리밍 응답 Flux
     */
    @Override
    public Flux<ChatResponse> streamRagResponse(String query, String model) {
        log.info("RAG 기반 스트리밍 질의 수신: {}, 모델: {}", query, model);

        try {
            var promptBuilder = ollamaChatClient.prompt()
                    .advisors(retrievalAugmentationAdvisor);
            
            // 모델이 지정된 경우 ChatOptions 설정
            if (model != null && !model.trim().isEmpty()) {
                promptBuilder.options(ChatOptions.builder().model(model).temperature(0.3).build());
                log.debug("지정된 모델로 응답 생성: {}", model);
            } else {
                log.debug("기본 모델로 응답 생성");
            }
            
            return promptBuilder.user(query).stream().chatResponse();

        } catch (Exception e) {
            log.error("AI 스트리밍 응답 생성 중 오류 발생", e);
            return Flux.error(e);
        }
    }

    /**
     * 일반 스트리밍 응답 생성
     * 
     * @param query 사용자 질의
     * @param model 사용할 모델명 (null이면 기본 모델 사용)
     * @return 스트리밍 응답 Flux
     */
    @Override
    public Flux<ChatResponse> streamSimpleResponse(String query, String model) {
        log.info("일반 스트리밍 질의 수신: {}, 모델: {}", query, model);

        try {
            var promptBuilder = ollamaChatClient.prompt();
            
            // 모델이 지정된 경우 ChatOptions 설정
            if (model != null && !model.trim().isEmpty()) {
                promptBuilder.options(ChatOptions.builder().model(model).temperature(0.3).build());
                log.debug("지정된 모델로 응답 생성: {}", model);
            } else {
                log.debug("기본 모델로 응답 생성");
            }
            
            return promptBuilder.user(query).stream().chatResponse();
        } catch (Exception e) {
            log.error("AI 스트리밍 응답 생성 중 오류 발생", e);
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
