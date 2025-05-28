package com.example.chat.service;

/**
 * 채팅 서비스 인터페이스
 * RAG 기반 응답과 일반 응답을 생성하는 기능 제공
 */
public interface ChatService {

    /**
     * RAG 기반 응답 생성
     * 벡터 저장소에서 관련 문서를 검색하여 LLM에 전달하고 응답 생성
     * 
     * @param query 사용자 질의
     * @return 생성된 응답
     */
    String generateRagResponse(String query);

    /**
     * 일반 응답 생성
     * 벡터 저장소 검색 없이 LLM에 직접 질의하여 응답 생성
     * 
     * @param query 사용자 질의
     * @return 생성된 응답
     */
    String generateSimpleResponse(String query);

}