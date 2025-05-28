package com.example.chat.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.example.chat.service.DocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    // Spring AI 자동 설정을 통해 주입되는 RedisVectorStore 사용
    private final RedisVectorStore redisVectorStore;

    @Value("${spring.ai.document.path}")
    private String documentPath;

    @Override
    public int loadDocuments() {

        // step 1. 문서 로드
        List<Document> documents = loadMarkdownDocuments();
        int processedCount = 0;

        log.info("총 {}개의 문서를 처리합니다.", documents.size());

        // step 2. 문서 임베딩 및 벡터 저장소에 추가
        for (Document doc : documents) {
            try {
                redisVectorStore.add(List.of(doc));
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

                    // 고정 ID 생성 - 파일명을 기반으로 하여 동일 파일은 항상 동일 ID 가짐
                    // 이를 통해 애플리케이션 재시작 시 동일 파일이 다시 추가되어도 덮어쓰기가 됨

                    // 한글을 보존하면서 유효한 ID 생성
                    // 파일명에서 Redis 키로 사용할 수 없는 특수 문자만 제거
                    String docId = "doc-" + filename
                            .replaceAll("[\\/:*?\"<>|]", "") // Redis 키로 사용할 수 없는 특수 문자 제거
                            .replaceAll("\\s+", "-"); // 공백을 하이픈으로 변경

                    Document doc = new Document(docId, content, metadata);
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
