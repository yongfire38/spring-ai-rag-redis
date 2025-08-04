package com.example.chat.service;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 쿼리 분석 JSON 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryAnalysisResponse {
    private String originalQuery;
    private String intent;
    private List<String> keyTopics;
    private String confidence;
    private String suggestedResponse;
} 