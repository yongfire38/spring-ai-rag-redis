package com.example.chat.config.etl.transformers;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 문서 내용 형식을 정규화하는 Transformer
 * 다양한 소스의 문서를 일관된 형식으로 변환
 */
@Slf4j
@Component
public class ContentFormatTransformer implements DocumentTransformer {

    // HTML 태그 제거 패턴
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    
    // 연속된 공백 패턴
    private static final Pattern MULTIPLE_SPACES_PATTERN = Pattern.compile("\\s+");
    
    // 연속된 줄바꿈 패턴
    private static final Pattern MULTIPLE_NEWLINES_PATTERN = Pattern.compile("\\n\\s*\\n\\s*\\n+");
    
    // 정규화 설정
    @Value("${spring.ai.document.normalization.enabled}")
    private boolean normalizationEnabled;
    
    @Value("${spring.ai.document.normalization.remove-html-tags}")
    private boolean removeHtmlTags;
    
    @Value("${spring.ai.document.normalization.remove-code-blocks}")
    private boolean removeCodeBlocks;
    
    @Value("${spring.ai.document.normalization.normalize-whitespace}")
    private boolean normalizeWhitespace;
    
    @Value("${spring.ai.document.normalization.normalize-newlines}")
    private boolean normalizeNewlines;
    
    @Value("${spring.ai.document.normalization.clean-special-chars}")
    private boolean cleanSpecialChars;

    @Override
    public List<Document> apply(List<Document> documents) {
        if (!normalizationEnabled) {
            log.info("문서 정규화가 비활성화되어 있습니다. 원본 문서를 그대로 반환합니다.");
            return documents;
        }
        
        log.info("문서 형식 정규화 시작: {}개 문서", documents.size());
        log.info("정규화 설정 - HTML 태그 제거: {}, 코드 블록 제거: {}, 공백 정규화: {}, 줄바꿈 정규화: {}, 특수문자 정리: {}", 
            removeHtmlTags, removeCodeBlocks, normalizeWhitespace, normalizeNewlines, cleanSpecialChars);
        
        List<Document> normalizedDocuments = documents.stream()
                .map(this::normalizeDocument)
                .toList();
        
        log.info("문서 형식 정규화 완료: {}개 문서", normalizedDocuments.size());
        
        return normalizedDocuments;
    }
    
    /**
     * 개별 문서 정규화
     */
    private Document normalizeDocument(Document document) {
        String originalContent = document.getText();
        String normalizedContent = normalizeContent(originalContent);
        
        // 메타데이터에 정규화 정보 추가
        document.getMetadata().put("original_length", originalContent.length());
        document.getMetadata().put("normalized_length", normalizedContent.length());
        document.getMetadata().put("normalization_applied", true);
        document.getMetadata().put("normalization_settings", createNormalizationSettings());
        
        // 정규화된 내용으로 Document 업데이트
        return new Document(document.getId(), normalizedContent, document.getMetadata());
    }
    
    /**
     * 텍스트 내용 정규화
     */
    private String normalizeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        
        String normalized = content;
        
        // 1. HTML 태그 제거 (설정에 따라)
        if (removeHtmlTags) {
            normalized = removeHtmlTags(normalized);
        }
        
        // 2. 마크다운 코드 블록 제거 (설정에 따라)
        if (removeCodeBlocks) {
            normalized = removeCodeBlocks(normalized);
        }
        
        // 3. 공백 정규화 (설정에 따라)
        if (normalizeWhitespace) {
            normalized = normalizeWhitespace(normalized);
        }
        
        // 4. 줄바꿈 정규화 (설정에 따라)
        if (normalizeNewlines) {
            normalized = normalizeNewlines(normalized);
        }
        
        // 5. 특수문자 정리 (설정에 따라)
        if (cleanSpecialChars) {
            normalized = cleanSpecialCharacters(normalized);
        }
        
        // 6. 앞뒤 공백 제거 (항상 수행)
        normalized = normalized.trim();
        
        return normalized;
    }
    
    /**
     * HTML 태그 제거
     */
    private String removeHtmlTags(String content) {
        return HTML_TAG_PATTERN.matcher(content).replaceAll("");
    }
    
    /**
     * 마크다운 코드 블록 제거 (선택적)
     */
    private String removeCodeBlocks(String content) {
        // ```로 둘러싸인 코드 블록 제거
        return content.replaceAll("```[\\s\\S]*?```", "");
    }
    
    /**
     * 공백 정규화
     */
    private String normalizeWhitespace(String content) {
        // 탭을 공백으로 변환
        content = content.replaceAll("\\t", " ");
        
        // 연속된 공백을 단일 공백으로 변환
        content = MULTIPLE_SPACES_PATTERN.matcher(content).replaceAll(" ");
        
        return content;
    }
    
    /**
     * 줄바꿈 정규화
     */
    private String normalizeNewlines(String content) {
        // 연속된 줄바꿈을 최대 2개로 제한
        content = MULTIPLE_NEWLINES_PATTERN.matcher(content).replaceAll("\n\n");
        
        return content;
    }
    
    /**
     * 특수문자 정리
     */
    private String cleanSpecialCharacters(String content) {
        // 한글은 전혀 건드리지 않고, 불필요한 특수문자만 제거
        // 한글 범위: \uAC00-\uD7AF (완성형 한글)
        // 한글 자모 범위: \u1100-\u11FF (한글 자모)
        // 한글 호환 자모 범위: \u3130-\u318F (한글 호환 자모)
        // 한글 확장 A 범위: \uA960-\uA97F (한글 확장 A)
        // 한글 확장 B 범위: \uD7B0-\uD7FF (한글 확장 B)
        
        // 한글과 기본 문자만 유지하는 패턴 (한글은 그대로 유지)
        String allowedChars = "\\uAC00-\\uD7AF\\u1100-\\u11FF\\u3130-\\u318F\\uA960-\\uA97F\\uD7B0-\\uD7FFa-zA-Z0-9\\s\\n\\t\\-\\_\\.\\,\\(\\)\\[\\]\\{\\}\"\\'\\:\\;\\!\\?\\@\\#\\$\\%\\&\\*\\+\\=\\|\\\\\\/\\<\\>";
        
        // 불필요한 특수문자 제거 (한글, 영문, 숫자, 기본 문장부호는 유지)
        String cleaned = content.replaceAll("[^" + allowedChars + "]", "");
        
        // 인코딩 디버깅을 위한 로그
        if (!content.equals(cleaned)) {
            log.debug("특수문자 정리: {} -> {} (길이: {} -> {})", 
                content.substring(0, Math.min(50, content.length())), 
                cleaned.substring(0, Math.min(50, cleaned.length())),
                content.length(), cleaned.length());
        }
        
        return cleaned;
    }
    
    /**
     * 정규화 설정 정보 생성
     */
    private String createNormalizationSettings() {
        return String.format("enabled=%s,html=%s,code=%s,whitespace=%s,newlines=%s,special=%s",
            normalizationEnabled, removeHtmlTags, removeCodeBlocks, 
            normalizeWhitespace, normalizeNewlines, cleanSpecialChars);
    }
} 