package com.example.chat.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 문서 요약 JSON 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSummaryResponse {
    private String title;
    private String summary;
    private List<String> keyPoints;
    private String source;
    private String relevance;
} 