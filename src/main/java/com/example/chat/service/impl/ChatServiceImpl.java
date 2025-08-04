package com.example.chat.service.impl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatResponse;

import org.springframework.stereotype.Service;

import com.example.chat.response.DocumentSummaryResponse;
import com.example.chat.response.QueryAnalysisResponse;
import com.example.chat.response.TechnologyResponse;
import com.example.chat.service.ChatService;
import com.example.chat.util.JsonPromptTemplates;

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
            String jsonPrompt = JsonPromptTemplates.createTechnologyInfoPrompt(query);
            return ollamaChatClient.prompt()
                    .user(jsonPrompt)
                    .call()
                    .entity(TechnologyResponse.class);
        } catch (Exception e) {
            log.error("기술 정보 JSON 응답 생성 중 오류 발생", e);
            return new TechnologyResponse("알 수 없음", "알 수 없음", "오류가 발생했습니다", null, null, "알 수 없음");
        }
    }

    /**
     * JSON 구조화된 출력 - 쿼리 분석
     * 
     * @param query 사용자 질의
     * @return JSON 구조화된 쿼리 분석 응답
     */
    @Override
    public QueryAnalysisResponse analyzeQueryAsJson(String query) {
        log.info("쿼리 분석 JSON 응답 생성: {}", query);
        
        try {
            String jsonPrompt = JsonPromptTemplates.createQueryAnalysisPrompt(query);
            return ollamaChatClient.prompt()
                    .user(jsonPrompt)
                    .call()
                    .entity(QueryAnalysisResponse.class);
        } catch (Exception e) {
            log.error("쿼리 분석 JSON 응답 생성 중 오류 발생", e);
            return new QueryAnalysisResponse(query, "알 수 없음", null, "낮음", "분석할 수 없습니다");
        }
    }

    /**
     * JSON 구조화된 출력 - 문서 요약
     * 
     * @param query 사용자 질의
     * @return JSON 구조화된 문서 요약 응답
     */
    @Override
    public DocumentSummaryResponse getDocumentSummaryAsJson(String query) {
        log.info("문서 요약 JSON 응답 생성: {}", query);
        
        try {
            String jsonPrompt = JsonPromptTemplates.createDocumentSummaryPrompt(query);
            return ollamaChatClient.prompt()
                    .advisors(retrievalAugmentationAdvisor)
                    .user(jsonPrompt)
                    .call()
                    .entity(DocumentSummaryResponse.class);
        } catch (Exception e) {
            log.error("문서 요약 JSON 응답 생성 중 오류 발생", e);
            return new DocumentSummaryResponse("알 수 없음", "요약할 수 없습니다", null, "알 수 없음", "낮음");
        }
    }
}
