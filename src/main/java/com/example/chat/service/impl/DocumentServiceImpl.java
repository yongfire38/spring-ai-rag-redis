package com.example.chat.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.example.chat.service.DocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final VectorStore vectorStore;

    @Value("${spring.ai.document.path}")
    private String documentPath;

    // Redis 키 패턴 (application.properties의 prefix 설정과 동일해야 함)
    private static final String REDIS_KEY_PREFIX = "embedding:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public int loadDocuments() {

        // step 1. 기존 Redis 데이터 삭제
        // 해당 메서드는 애플리케이션 기동 시마다 호출되므로 중복 업로드 방지
        try {
            log.info("Redis 벡터 저장소 데이터 삭제 시도");

            // Redis에서 embedding: 프리픽스를 가진 모든 키 가져오기
            Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");

            if (keys != null && !keys.isEmpty()) {
                log.info("{}개의 기존 데이터 항목 발견", keys.size());
                redisTemplate.delete(keys);
                log.info("기존 데이터 삭제 완료");
            } else {
                log.info("삭제할 기존 데이터 없음");
            }
        } catch (Exception e) {
            log.warn("Redis 데이터 삭제 중 오류: {}", e.getMessage());
            log.debug("오류 상세 정보", e);
        }

        // step 2. 문서 로드
        List<Document> documents = loadMarkdownDocuments();
        int processedCount = 0;

        log.info("총 {}개의 문서를 처리합니다.", documents.size());

        // step 3. 문서 임베딩 및 벡터 저장소에 추가
        for (Document doc : documents) {
            try {
                // 새 문서 추가 (기존 데이터는 이미 삭제한 상태태)
                vectorStore.add(List.of(doc));
                processedCount++;
                String source = (String) doc.getMetadata().get("source");
                log.info("문서 처리 완료: {}", (source != null ? source : "unknown"));
            } catch (Exception e) {
                String errorSource = (String) doc.getMetadata().get("source");
                log.error("문서 처리 중 오류 발생: {}", (errorSource != null ? errorSource : "unknown"), e);
            }
        }

        log.info("총 {}개 문서 중 {}개 처리 완료", documents.size(), processedCount);
        return processedCount;

    }

    /**
     * 리소스 디렉토리에서 모든 마크다운 파일을 로드
     */
    private List<Document> loadMarkdownDocuments() {
        List<Document> documents = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {

            Resource[] resources = resolver.getResources(documentPath);
            log.info("{}개의 마크다운 파일을 찾았습니다.", resources.length);

            for (Resource resource : resources) {
                try {

                    String content = readResourceContent(resource);
                    String filename = resource.getFilename();

                    // 메타데이터 생성
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("source", filename);
                    metadata.put("type", "markdown");

                    Document doc = new Document(content, metadata);
                    documents.add(doc);
                    log.info("문서 로드 완료: {}, 크기: {}바이트", filename, content.length());
                } catch (IOException e) {
                    log.error("파일 읽기 오류: {}", resource.getFilename(), e);
                }
            }

        } catch (IOException e) {
            log.error("리소스 검색 중 오류 발생", e);
        }

        return documents;
    }

    /**
     * 리소스 파일의 내용을 문자열로 읽기
     */
    private String readResourceContent(Resource resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

}
