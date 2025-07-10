package com.example.chat.service;

/**
 * 문서 처리 상태를 나타내는 응답 객체
 */
public record DocumentStatusResponse(
    boolean processing,     // 현재 처리 중인지 여부
    int processedCount,    // 처리된 청크 수
    int totalCount,        // 총 문서 수
    int changedCount       // 변경된 문서 수
) {
    // 기존 생성자에 대한 호환성 유지
    public DocumentStatusResponse(boolean processing, int processedCount, int totalCount) {
        this(processing, processedCount, totalCount, 0);
    }
}