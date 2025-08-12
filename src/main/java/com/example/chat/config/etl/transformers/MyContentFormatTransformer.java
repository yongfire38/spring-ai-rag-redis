package com.example.chat.config.etl.transformers;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.ContentFormatTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI의 ContentFormatTransformer를 활용한 문서 정규화 Transformer
 */
@Slf4j
@Component
public class MyContentFormatTransformer implements DocumentTransformer {
    
    private final ContentFormatTransformer contentFormatTransformer;
    
    // 정규화 설정
    @Value("${spring.ai.document.normalization.enabled}")
    private boolean normalizationEnabled;
    
    @Value("${spring.ai.document.normalization.remove-html-tags}")
    private boolean removeHtmlTags;
    
    @Value("${spring.ai.document.normalization.normalize-whitespace}")
    private boolean normalizeWhitespace;
    
    @Value("${spring.ai.document.normalization.normalize-newlines}")
    private boolean normalizeNewlines;
    
    @Value("${spring.ai.document.normalization.remove-code-blocks}")
    private boolean removeCodeBlocks;
    
    @Value("${spring.ai.document.normalization.clean-special-chars}")
    private boolean cleanSpecialChars;
    
    // 정규식 패턴들
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NEWLINES_PATTERN = Pattern.compile("\\n\\s*\\n");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile(
        "[^\\uAC00-\\uD7AF\\u1100-\\u11FF\\u3130-\\u318F\\uA960-\\uA97F\\uD7B0-\\uD7FF" +
        "a-zA-Z0-9\\s\\n\\t\\-_.,()\\[\\]{}\"':;!?@#$%&*+=|\\\\/<>]");
    
    public MyContentFormatTransformer() {
        // Spring AI의 DefaultContentFormatter 사용
        ContentFormatter contentFormatter = DefaultContentFormatter.builder()
                .withTextTemplate("{text}") // 기본 텍스트 템플릿
                .withMetadataTemplate("source: {source}\\ntype: {type}\\nnormalized: true")
                .withMetadataSeparator("\\n")
                .build();
                
        this.contentFormatTransformer = new ContentFormatTransformer(contentFormatter);
    }

    @Override
    public List<Document> apply(List<Document> documents) { 
        if (!normalizationEnabled) {
            log.info("문서 정규화가 비활성화되어 있습니다. 원본 문서를 그대로 반환합니다.");
            return documents;
        }
        
        log.info("문서 형식 정규화 시작: {}개 문서 (HTML: {}, 공백: {}, 줄바꿈: {}, 코드블록: {}, 특수문자: {})", 
                 documents.size(), removeHtmlTags, normalizeWhitespace, normalizeNewlines, removeCodeBlocks, cleanSpecialChars);
        
        // 1. Spring AI ContentFormatTransformer로 기본 정규화 수행
        List<Document> normalizedDocuments = contentFormatTransformer.apply(documents);
        
        // 2. 추가적인 정규화 수행 (Spring AI가 지원하지 않는 기능들)
        normalizedDocuments = applyAdditionalNormalization(normalizedDocuments);
        
        log.info("문서 형식 정규화 완료: {}개 문서", normalizedDocuments.size());
        return normalizedDocuments;
    }
    
    /**
     * Spring AI에서 지원하지 않는 추가적인 정규화 적용
     */
    private List<Document> applyAdditionalNormalization(List<Document> documents) {
        return documents.stream()
                .map(this::applyAdditionalNormalizationToDocument)
                .toList();
    }
    
    /**
     * 개별 문서에 추가 정규화 적용 (Spring AI가 자동으로 처리하지 않는 기능들)
     */
    private Document applyAdditionalNormalizationToDocument(Document document) {
        String originalContent = document.getText();
        String normalizedContent = originalContent;
        
        // HTML 태그 제거
        if (removeHtmlTags) {
            normalizedContent = HTML_TAG_PATTERN.matcher(normalizedContent).replaceAll("");
        }
        
        // 공백 정규화 (연속된 공백을 하나로)
        if (normalizeWhitespace) {
            normalizedContent = WHITESPACE_PATTERN.matcher(normalizedContent).replaceAll(" ");
        }
        
        // 줄바꿈 정규화 (연속된 줄바꿈을 하나로)
        if (normalizeNewlines) {
            normalizedContent = NEWLINES_PATTERN.matcher(normalizedContent).replaceAll("\n");
        }
        
        // 코드 블록 제거
        if (removeCodeBlocks) {
            normalizedContent = CODE_BLOCK_PATTERN.matcher(normalizedContent).replaceAll("");
        }
        
        // 특수문자 정리
        if (cleanSpecialChars) {
            normalizedContent = SPECIAL_CHARS_PATTERN.matcher(normalizedContent).replaceAll("");
        }
        
        // 앞뒤 공백 제거
        normalizedContent = normalizedContent.trim();
        
        // 내용이 변경된 경우에만 새 Document 생성
        if (!originalContent.equals(normalizedContent)) {
            // 메타데이터에 정규화 정보 추가
            document.getMetadata().put("original_length", originalContent.length());
            document.getMetadata().put("normalized_length", normalizedContent.length());
            document.getMetadata().put("normalization_applied", true);
            document.getMetadata().put("html_tags_removed", removeHtmlTags);
            document.getMetadata().put("whitespace_normalized", normalizeWhitespace);
            document.getMetadata().put("newlines_normalized", normalizeNewlines);
            document.getMetadata().put("code_blocks_removed", removeCodeBlocks);
            document.getMetadata().put("special_chars_cleaned", cleanSpecialChars);
            
            if (log.isDebugEnabled()) {
                log.debug("정규화 적용: {} -> {} (길이: {} -> {})", 
                    originalContent.substring(0, Math.min(50, originalContent.length())), 
                    normalizedContent.substring(0, Math.min(50, normalizedContent.length())),
                    originalContent.length(), normalizedContent.length());
            }
            
            return new Document(document.getId(), normalizedContent, document.getMetadata());
        }
        
        return document;
    }
}