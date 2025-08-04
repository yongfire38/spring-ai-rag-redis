package com.example.chat.service;

import org.springframework.ai.chat.model.ChatResponse;

import reactor.core.publisher.Flux;

/**
 * 채팅 서비스 인터페이스
 * RAG 기반 응답과 일반 응답을 생성하는 기능 제공
 * 스트리밍 응답을 위한 메서드 추가
 */
public interface ChatService {
    /**
     * RAG 기반 스트리밍 응답 생성
     * 벡터 저장소에서 관련 문서를 검색하여 LLM에 전달하고 스트리밍 응답 생성
     * 
     * @param query 사용자 질의
     * @return 스트리밍 응답 Flux
     */
    Flux<ChatResponse> streamRagResponse(String query);
    
    /**
     * 일반 스트리밍 응답 생성
     * 벡터 저장소 검색 없이 LLM에 직접 질의하여 스트리밍 응답 생성
     * 
     * @param query 사용자 질의
     * @return 스트리밍 응답 Flux
     */
    Flux<ChatResponse> streamSimpleResponse(String query);

    /**
     * JSON 구조화된 출력 - 기술 정보
     * 
     * @param query 사용자 질의
     * @return JSON 구조화된 기술 정보 응답
     */
    TechnologyResponse getTechnologyInfoAsJson(String query);

    /**
     * JSON 구조화된 출력 - 쿼리 분석
     * 
     * @param query 사용자 질의
     * @return JSON 구조화된 쿼리 분석 응답
     */
    QueryAnalysisResponse analyzeQueryAsJson(String query);

    /**
     * JSON 구조화된 출력 - 문서 요약
     * 
     * @param query 사용자 질의
     * @return JSON 구조화된 문서 요약 응답
     */
    DocumentSummaryResponse getDocumentSummaryAsJson(String query);
}