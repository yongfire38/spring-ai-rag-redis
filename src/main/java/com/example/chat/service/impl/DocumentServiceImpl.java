package com.example.chat.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.io.File;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import com.example.chat.service.DocumentService;
import com.example.chat.service.DocumentStatusResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    // Spring AI 자동 설정을 통해 주입되는 RedisVectorStore 사용
    private final RedisVectorStore redisVectorStore;

    private static final int BATCH_SIZE = 100;

    private final TextSplitter textSplitter;

    // 비동기 처리를 위한 Executor 주입
    private final Executor executor;

    @Value("${spring.ai.document.path}")
    private String documentPath;

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);

    @Override
    public boolean isProcessing() {
        return isProcessing.get();
    }

    @Override
    public int getProcessedCount() {
        return processedCount.get();
    }

    @Override
    public int getTotalCount() {
        return totalCount.get();
    }

    @Override
    public CompletableFuture<Integer> loadDocumentsAsync() {
        if (isProcessing.get()) {
            log.warn("이미 문서 처리가 진행 중입니다.");
            return CompletableFuture.completedFuture(0);
        }

        log.info("비동기 문서 로딩 시작");
        isProcessing.set(true);
        processedCount.set(0);
        totalCount.set(0);

        return CompletableFuture.supplyAsync(() -> processDocuments(), executor)
            .handle((result, ex) -> {
                isProcessing.set(false);
                if (ex != null) {
                    log.error("비동기 문서 처리 중 오류 발생", ex);
                    throw new RuntimeException("문서 처리 중 오류 발생", ex);
                }
                log.info("비동기 문서 로딩 완료: {}개 청크 처리됨", result);
                return result;
            });
    }

    private int processDocuments() {
        // step 0. 기존 벡터 데이터는 덮어쓰기로 처리.
        log.info("기존 벡터 데이터는 덮어쓰기로 처리합니다.");
    
        // step 1. 문서 로드
        List<Document> documents = loadMarkdownDocuments();
        totalCount.set(documents.size());
        log.info("총 {}개의 문서를 처리합니다.", documents.size());
    
        // step 2. 문서 분할
        List<Document> splitDocuments = textSplitter.split(documents);
        log.info("{}개의 원본 문서를 {}개의 청크로 분할했습니다.", documents.size(), splitDocuments.size());
    
        List<Document> processedDocuments = new ArrayList<>();
        
        // step 3. 문서 임베딩 및 벡터 저장소에 추가 (청크 인덱스 기반)
        Map<String, Integer> chunkCountByDocument = new HashMap<>();
        
        for (Document chunk : splitDocuments) {
            try {
                Optional<Document> processedChunk = processChunkWithIndex(chunk, chunkCountByDocument);
                if (processedChunk.isPresent()) {
                    processedDocuments.add(processedChunk.get());
                    processedCount.incrementAndGet();
                }
            } catch (Exception e) {
                log.error("청크 처리 중 오류 발생: {}", chunk.getId(), e);
            }
        }
    
        try {
            List<Document> batch = new ArrayList<>(BATCH_SIZE);
    
            for (Document doc : processedDocuments) {
                batch.add(doc);
                if (batch.size() >= BATCH_SIZE) {
                    redisVectorStore.add(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redisVectorStore.add(batch);
            }
            log.info("총 {}개 청크 처리 완료", processedDocuments.size());
            return processedDocuments.size();
        } catch (Exception e) {
            log.error("문서 처리 중 오류 발생", e);
            return 0;
        }
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

                    String filename = resource.getFilename();

                    if (filename == null) {
                        log.warn("파일명이 null입니다: {}", resource.getDescription());
                        continue;
                    }

                    log.info("파일 처리 시작: {}", filename);

                    String content = readResourceContent(resource);

                    if (content == null || content.trim().isEmpty()) {
                        log.warn("빈 파일 건너뜀: {}", filename);
                        continue;
                    }

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

    /**
 * Document를 처리 가능한 청크로 변환하는 메서드 (청크 인덱스 기반)
 */
private Optional<Document> processChunkWithIndex(Document chunk, Map<String, Integer> chunkCountByDocument) {
    return getSafeText(chunk).map(chunkText -> {
        // 파일명 기반으로 안정적인 ID 생성
        String source = (String) chunk.getMetadata().get("source");
        String stableId = "";

        if (source != null && !source.isEmpty()) {
            // 파일명에서 확장자를 제거하고 특수문자 대체
            stableId = source.replaceAll("\\.md$", "").replaceAll("[^a-zA-Z0-9가-힣]", "_");
        } else {
            // source가 없는 경우 임의의 고정 ID 사용
            stableId = "unknown_document";
        }

        // 해당 문서의 청크 카운트 증가
        int chunkIndex = chunkCountByDocument.getOrDefault(stableId, 0) + 1;
        chunkCountByDocument.put(stableId, chunkIndex);

        // 안정적인 ID 생성 (문서ID_청크번호)
        String stableChunkId = stableId + "_chunk_" + chunkIndex;

        // 메타데이터 복사
        Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
        metadata.put("original_document_id", stableId);
        metadata.put("chunk_index", chunkIndex);

        // 안정적인 ID로 새 문서 생성
        return new Document(stableChunkId, chunkText, metadata);
    });
}

    /**
     * 안전하게 Document의 텍스트를 추출하는 유틸리티 메서드
     */
    private Optional<String> getSafeText(Document document) {
        return Optional.ofNullable(document.getText())
                .filter(text -> !text.trim().isEmpty());
    }

    @Override
    public Map<String, Object> uploadMarkdownFiles(MultipartFile[] files) {
        Map<String, Object> result = new HashMap<>();
        if (files == null || files.length == 0) {
            result.put("success", false);
            result.put("message", "업로드할 파일이 없습니다.");
            return result;
        }
        if (files.length > 5) {
            result.put("success", false);
            result.put("message", "최대 5개 파일만 업로드할 수 있습니다.");
            return result;
        }
        long totalSize = 0;
        int uploaded = 0;
        for (MultipartFile file : files) {
            String filename = StringUtils.cleanPath(file.getOriginalFilename());
            if (!filename.endsWith(".md")) {
                result.put("success", false);
                result.put("message", "마크다운(.md) 파일만 업로드 가능합니다.");
                return result;
            }
            if (file.getSize() > 5 * 1024 * 1024) {
                result.put("success", false);
                result.put("message", "파일당 최대 5MB까지만 업로드할 수 있습니다.");
                return result;
            }
            totalSize += file.getSize();
        }
        if (totalSize > 20 * 1024 * 1024) {
            result.put("success", false);
            result.put("message", "총 20MB를 초과할 수 없습니다.");
            return result;
        }
        // 저장 경로
        String saveDir = "C:/workspace-test/upload/data";
        File dir = new File(saveDir);
        if (!dir.exists()) dir.mkdirs();
        for (MultipartFile file : files) {
            String filename = StringUtils.cleanPath(file.getOriginalFilename());
            File dest = new File(dir, filename);
            try {
                file.transferTo(dest);
                uploaded++;
            } catch (IOException e) {
                result.put("success", false);
                result.put("message", filename + " 저장 실패: " + e.getMessage());
                return result;
            }
        }
        result.put("success", true);
        result.put("uploaded", uploaded);
        return result;
    }

    @Override
    public String reindexDocuments() {
        log.info("문서 재인덱싱 요청 수신");
        try {
            this.loadDocumentsAsync()
                .thenAccept(count -> log.info("재인덱싱 완료: {}개 청크 처리됨", count))
                .exceptionally(throwable -> {
                    log.error("재인덱싱 중 오류 발생", throwable);
                    return null;
                });
            log.info("비동기 재인덱싱 요청 성공");
            return "문서 재인덱싱이 시작되었습니다.";
        } catch (Exception e) {
            log.error("재인덱싱 요청 처리 중 오류", e);
            return "재인덱싱 요청 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    @Override
    public DocumentStatusResponse getStatusResponse() {
        return new DocumentStatusResponse(
            this.isProcessing(),
            this.getProcessedCount(),
            this.getTotalCount()
        );
    }
}
