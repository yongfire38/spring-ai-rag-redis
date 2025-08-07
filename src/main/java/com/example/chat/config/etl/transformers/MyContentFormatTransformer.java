package com.example.chat.config.etl.transformers;

import java.util.List;
import java.util.ArrayList;

import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.ContentFormatTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI의 ContentFormatTransformer를 사용하여 문서 내용 형식을 정규화하는 Transformer
 * 다양한 소스의 문서를 일관된 형식으로 변환
 */
@Slf4j
@Component
public class MyContentFormatTransformer implements DocumentTransformer {
    
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
        
        // 문서 타입별로 그룹화하여 처리
        List<Document> normalizedDocuments = new ArrayList<>();
        
        for (Document document : documents) {
            // 문서 타입에 따라 다른 ContentFormatter 적용
            ContentFormatter contentFormatter = createContentFormatterForDocument(document);
            ContentFormatTransformer contentFormatTransformer = new ContentFormatTransformer(contentFormatter);
            
            List<Document> singleDocumentList = List.of(document);
            List<Document> normalizedSingleDocument = contentFormatTransformer.apply(singleDocumentList);
            
            // 추가적인 정규화가 필요한 경우 수동 처리
            if (removeCodeBlocks || cleanSpecialChars) {
                normalizedSingleDocument = applyAdditionalNormalization(normalizedSingleDocument);
            }
            
            normalizedDocuments.addAll(normalizedSingleDocument);
        }
        
        log.info("문서 형식 정규화 완료: {}개 문서", normalizedDocuments.size());
        
        return normalizedDocuments;
    }
    
    /**
     * 문서 타입에 따른 ContentFormatter 생성
     */
    private ContentFormatter createContentFormatterForDocument(Document document) {
        DefaultContentFormatter.Builder builder = DefaultContentFormatter.builder();
        
        // 텍스트 템플릿 설정 - 정규화된 텍스트 사용
        String textTemplate = createTextTemplate();
        builder.withTextTemplate(textTemplate);
        
        // 문서 타입에 따른 메타데이터 템플릿 설정
        String metadataTemplate = createMetadataTemplateForDocument(document);
        
        builder.withMetadataTemplate(metadataTemplate);
        builder.withMetadataSeparator("\n");
        
        return builder.build();
    }
    
    /**
     * 문서 타입에 따른 메타데이터 템플릿 생성
     */
    private String createMetadataTemplateForDocument(Document document) {
        // 기본 정규화 설정 정보
        String normalizationSettings = String.format("enabled=%s,html=%s,code=%s,whitespace=%s,newlines=%s,special=%s",
            normalizationEnabled, removeHtmlTags, removeCodeBlocks, 
            normalizeWhitespace, normalizeNewlines, cleanSpecialChars);
        
        // 통합된 메타데이터 템플릿 - 모든 문서 타입에 동일하게 적용
        return """
            source: {source}
            type: {type}
            content_length: {content_length}
            normalization_applied: true
            normalization_settings: %s
            """.formatted(normalizationSettings);
    }
    
    /**
     * 텍스트 템플릿 생성 - 정규화 로직 포함
     */
    private String createTextTemplate() {
        StringBuilder template = new StringBuilder();
        template.append("{text}");
        
        // HTML 태그 제거
        if (removeHtmlTags) {
            template.append(" | html_clean");
        }
        
        // 공백 정규화
        if (normalizeWhitespace) {
            template.append(" | whitespace_normalize");
        }
        
        // 줄바꿈 정규화
        if (normalizeNewlines) {
            template.append(" | newlines_normalize");
        }
        
        return template.toString();
    }
    
    /**
     * 추가적인 정규화 적용 (ContentFormatter로 처리할 수 없는 부분)
     */
    private List<Document> applyAdditionalNormalization(List<Document> documents) {
        return documents.stream()
                .map(this::applyAdditionalNormalization)
                .toList();
    }
    
    /**
     * 개별 문서에 추가 정규화 적용
     */
    private Document applyAdditionalNormalization(Document document) {
        String originalContent = document.getText();
        String normalizedContent = originalContent;
        
        // 코드 블록 제거
        if (removeCodeBlocks) {
            normalizedContent = removeCodeBlocks(normalizedContent);
        }
        
        // 특수문자 정리
        if (cleanSpecialChars) {
            normalizedContent = cleanSpecialCharacters(normalizedContent);
        }
        
        // 내용이 변경된 경우에만 새 Document 생성
        if (!originalContent.equals(normalizedContent)) {
            // 메타데이터에 정규화 정보 추가
            document.getMetadata().put("original_length", originalContent.length());
            document.getMetadata().put("normalized_length", normalizedContent.length());
            document.getMetadata().put("additional_normalization_applied", true);
            
            return new Document(document.getId(), normalizedContent, document.getMetadata());
        }
        
        return document;
    }
    
    /**
     * 마크다운 코드 블록 제거
     */
    private String removeCodeBlocks(String content) {
        // ```로 둘러싸인 코드 블록 제거
        return content.replaceAll("```[\\s\\S]*?```", "");
    }
    
    /**
     * 특수문자 정리
     */
    private String cleanSpecialCharacters(String content) {
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
} 