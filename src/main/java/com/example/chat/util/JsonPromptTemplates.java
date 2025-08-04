package com.example.chat.util;

/**
 * JSON 구조화된 출력을 위한 프롬프트 템플릿 클래스
 */
public class JsonPromptTemplates {

    /**
     * 쿼리 분석을 위한 JSON 프롬프트
     * 
     * @param query 사용자 질의
     * @return JSON 형식으로 응답하도록 하는 프롬프트
     */
    public static String createQueryAnalysisPrompt(String query) {
        return """
                다음 질문을 분석하여 JSON 형식으로 응답해주세요.

                질문: %s

                다음 JSON 형식으로 정확히 응답해주세요:
                {
                  "originalQuery": "원본 질문",
                  "intent": "질문의 의도 (정보 요청, 비교, 방법 문의 등)",
                  "keyTopics": ["주요 키워드1", "주요 키워드2", "주요 키워드3"],
                  "confidence": "신뢰도 (높음/중간/낮음)",
                  "suggestedResponse": "제안하는 답변"
                }

                규칙:
                1. 반드시 위 JSON 형식으로만 응답하세요
                2. 다른 텍스트나 설명을 추가하지 마세요
                3. JSON 형식이 정확해야 합니다
                4. 모든 필드를 채워주세요
                """.formatted(query);
    }

    /**
     * 기술 정보를 위한 JSON 프롬프트
     * 
     * @param query 사용자 질의
     * @return JSON 형식으로 응답하도록 하는 프롬프트
     */
    public static String createTechnologyInfoPrompt(String query) {
        return """
                다음 질문에 대해 기술 정보를 JSON 형식으로 응답해주세요.

                질문: %s

                다음 JSON 형식으로 정확히 응답해주세요:
                {
                  "name": "기술명",
                  "category": "카테고리 (프레임워크, 언어, 도구 등)",
                  "description": "기술에 대한 설명",
                  "features": ["특징1", "특징2", "특징3"],
                  "useCases": ["사용 사례1", "사용 사례2", "사용 사례3"],
                  "complexity": "복잡도 (쉬움/보통/어려움)"
                }

                규칙:
                1. 반드시 위 JSON 형식으로만 응답하세요
                2. 다른 텍스트나 설명을 추가하지 마세요
                3. JSON 형식이 정확해야 합니다
                4. 모든 필드를 채워주세요
                """.formatted(query);
    }

    /**
     * 문서 요약을 위한 JSON 프롬프트
     * 
     * @param query 사용자 질의
     * @return JSON 형식으로 응답하도록 하는 프롬프트
     */
    public static String createDocumentSummaryPrompt(String query) {
        return """
                다음 질문에 대해 문서를 검색하여 JSON 형식으로 요약해주세요.

                질문: %s

                다음 JSON 형식으로 정확히 응답해주세요:
                {
                  "title": "문서 제목 또는 주제",
                  "summary": "문서 내용 요약",
                  "keyPoints": ["핵심 포인트1", "핵심 포인트2", "핵심 포인트3"],
                  "source": "정보 출처",
                  "relevance": "관련성 (높음/중간/낮음)"
                }

                규칙:
                1. 반드시 위 JSON 형식으로만 응답하세요
                2. 다른 텍스트나 설명을 추가하지 마세요
                3. JSON 형식이 정확해야 합니다
                4. 모든 필드를 채워주세요
                5. 제공된 문서의 정보만을 사용하세요
                """.formatted(query);
    }
} 