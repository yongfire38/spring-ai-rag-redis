package com.example.chat.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.chat.service.DocumentService;
import com.example.chat.service.DocumentStatusResponse;
import com.example.chat.util.DocumentHashUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    // Spring AI 자동 설정을 통해 주입되는 RedisVectorStore 사용
    private final RedisVectorStore redisVectorStore;

    // 작은 배치 크기로 처리하여 시스템 부하 분산
    private static final int SMALL_BATCH_SIZE = 20;

    private final TextSplitter textSplitter;

    // 비동기 처리를 위한 Executor 주입
    private final Executor executor;

    @Value("${spring.ai.document.path}")
    private String documentPath;

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger changedCount = new AtomicInteger(0);

    private final StringRedisTemplate stringRedisTemplate;

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
    public int getChangedCount() {
        return changedCount.get();
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
        changedCount.set(0);

        // 전체 작업을 여러 단계로 분리하여 각 단계를 비동기로 처리
        return CompletableFuture.supplyAsync(() -> {
            // 1단계: 문서 로드
            List<Document> documents = loadMarkdownDocuments();
            totalCount.set(documents.size());
            log.info("총 {}개의 문서를 로드했습니다.", documents.size());
            return documents;
        }, executor)
        .exceptionally(ex -> {
            log.error("문서 로딩 중 오류 발생", ex);
            throw new RuntimeException("문서 로딩 중 오류 발생", ex);
        })
        .thenApplyAsync(documents -> {
            // 2단계: 변경된 문서만 필터링
            List<Document> changedDocuments = filterChangedDocuments(documents);
            changedCount.set(changedDocuments.size());
            log.info("총 {}개의 문서 중 {}개의 변경된 문서를 처리합니다.",
                    documents.size(), changedDocuments.size());
            return changedDocuments;
        }, executor)
        .exceptionally(ex -> {
            log.error("문서 필터링 중 오류 발생", ex);
            throw new RuntimeException("문서 필터링 중 오류 발생", ex);
        })
        .thenApplyAsync(filteredDocuments -> {
            // 3단계: 문서 분할
            if (filteredDocuments.isEmpty()) {
                log.info("변경된 문서가 없습니다. 인덱싱 작업을 건너뜁니다.");
                return new ArrayList<Document>();
            }
            List<Document> splitDocuments = textSplitter.split(filteredDocuments);
            log.info("{}개의 문서를 {}개의 청크로 분할했습니다.",
                    filteredDocuments.size(), splitDocuments.size());
            return splitDocuments;
        }, executor)
        .exceptionally(ex -> {
            log.error("문서 분할 중 오류 발생", ex);
            throw new RuntimeException("문서 분할 중 오류 발생", ex);
        })
        .thenApplyAsync(this::processChunksInSmallBatches, executor)
        .handle((result, ex) -> {
            isProcessing.set(false);
            if (ex != null) {
                log.error("비동기 문서 처리 중 오류 발생", ex);
                throw new RuntimeException("문서 처리 중 오류 발생", ex);
            }
            log.info("비동기 문서 로딩 완료: 총 {}개 문서 중 {}개 변경, {}개 청크 처리됨",
                    totalCount.get(), changedCount.get(), result);
            return result;
        });
    }

    /**
     * 변경된 문서만 필터링하는 메서드
     * 
     * @param documents 모든 문서 목록
     * @return 변경된 문서만 포함된 목록
     */
    private List<Document> filterChangedDocuments(List<Document> documents) {
        return documents.stream()
                .filter(this::isDocumentChanged)
                .collect(Collectors.toList());
    }

    /**
     * 문서가 변경되었는지 확인하고 Redis에 해시를 저장/비교하는 메서드
     * @param document 확인할 문서
     * @return 문서가 변경되었으면 true, 아니면 false
     */
    private boolean isDocumentChanged(Document document) {
        String docId = document.getId();
        String content = document.getText();

        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        // 문서 내용의 해시 계산
        String newHash = DocumentHashUtil.calculateHash(content);

        // Redis에서 기존 해시 조회 (기본 DB index 사용)
        String redisKey = "docmeta:" + docId;
        String oldHash = stringRedisTemplate.opsForValue().get(redisKey);

        if (oldHash != null && oldHash.equals(newHash)) {
            log.debug("문서 '{}' 변경 없음 (해시: {})", docId, newHash);
            return false;
        }

        // 해시가 다르거나 없으면 Redis에 해시 저장
        stringRedisTemplate.opsForValue().set(redisKey, newHash);
        log.debug("문서 '{}' 변경 감지 (이전 해시: {}, 새 해시: {})", docId, oldHash, newHash);
        return true;
    }

    /**
     * 청크를 작은 배치 단위로 처리하여 시스템 부하 분산
     * 
     * @param splitDocuments 처리할 청크 목록
     * @return 처리된 총 청크 수
     */
    private int processChunksInSmallBatches(List<Document> splitDocuments) {
        if (splitDocuments.isEmpty()) {
            return 0;
        }

        int totalProcessed = 0;
        Map<String, Integer> chunkCountByDocument = new ConcurrentHashMap<>();

        // 작은 배치 단위로 처리
        for (int i = 0; i < splitDocuments.size(); i += SMALL_BATCH_SIZE) {
            int end = Math.min(i + SMALL_BATCH_SIZE, splitDocuments.size());
            List<Document> batch = splitDocuments.subList(i, end);

            List<Document> processedBatch = batch.stream()
                    .map(chunk -> {
                        try {
                            Optional<Document> processed = processChunkWithIndex(chunk, chunkCountByDocument);
                            if (processed.isPresent()) {
                                processedCount.incrementAndGet();
                                return processed.get();
                            }
                            return null;
                        } catch (Exception e) {
                            log.error("청크 처리 중 오류 발생: {}", chunk.getId(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (!processedBatch.isEmpty()) {
                try {
                    // 배치 단위로 저장
                    redisVectorStore.add(processedBatch);
                    totalProcessed += processedBatch.size();
                    log.debug("배치 처리 완료: {}개의 청크 저장 (총 {}/{})",
                            processedBatch.size(), totalProcessed, splitDocuments.size());

                    // 잠시 대기하여 시스템 부하 분산
                    Thread.sleep(50);
                } catch (Exception e) {
                    log.error("벡터 저장 중 오류 발생", e);
                }
            }
        }

        return totalProcessed;
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
            // 파일명 기반으로 안정적인 ID 생성 (null 안전하게 처리)
            String source = chunk.getMetadata().containsKey("source")
                    ? String.valueOf(chunk.getMetadata().get("source"))
                    : "";
            String stableId;

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
        // 결과 맵 초기화 (null 값이 들어가지 않도록 함)
        Map<String, Object> result = new HashMap<>();
        if (files == null || files.length == 0) {
            result.put("success", false);
            result.put("message", "업로드할 파일이 없습니다.");
            result.putIfAbsent("files", Collections.emptyList()); // null이 아닌 빈 목록 추가
            return result;
        }
        if (files.length > 5) {
            result.put("success", false);
            result.put("message", "최대 5개 파일만 업로드할 수 있습니다.");
            result.putIfAbsent("files", Collections.emptyList()); // null이 아닌 빈 목록 추가
            return result;
        }
        long totalSize = 0;
        int uploaded = 0;
        for (MultipartFile file : files) {
            String filename = StringUtils.cleanPath(file.getOriginalFilename());
            if (!filename.endsWith(".md")) {
                result.put("success", false);
                result.put("message", "마크다운(.md) 파일만 업로드 가능합니다.");
                result.putIfAbsent("files", Collections.emptyList()); // null이 아닌 빈 목록 추가
                return result;
            }
            if (file.getSize() > 5 * 1024 * 1024) {
                result.put("success", false);
                result.put("message", "파일당 최대 5MB까지만 업로드할 수 있습니다.");
                result.putIfAbsent("files", Collections.emptyList()); // null이 아닌 빈 목록 추가
                return result;
            }
            totalSize += file.getSize();
        }
        if (totalSize > 20 * 1024 * 1024) {
            result.put("success", false);
            result.put("message", "총 20MB를 초과할 수 없습니다.");
            result.putIfAbsent("files", Collections.emptyList()); // null이 아닌 빈 목록 추가
            return result;
        }
        // 저장 경로
        String saveDir = "C:/workspace-test/upload/data";
        File dir = new File(saveDir);
        if (!dir.exists())
            dir.mkdirs();
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
        CompletableFuture<Integer> future = this.loadDocumentsAsync();

        // 이미 인덱싱 중이면 0을 반환하도록 구현되어 있으므로, 바로 메시지 반환
        if (future.isDone()) {
            try {
                if (future.get() == 0) {
                    return "이미 문서 인덱싱이 진행 중입니다.";
                }
            } catch (Exception e) {
                log.error("상태 확인 중 오류", e);
                return "상태 확인 중 오류가 발생했습니다: " + e.getMessage();
            }
        }

        // 비동기 완료 후 로그 처리
        future.thenAccept(count -> log.info("재인덱싱 완료: {}개 청크 처리됨", count))
              .exceptionally(throwable -> {
                  log.error("재인덱싱 중 오류 발생", throwable);
                  return null;
              });

        log.info("비동기 재인덱싱 요청 성공");
        return "문서 재인덱싱이 처리되었습니다.";
    }

    @Override
    public DocumentStatusResponse getStatusResponse() {
        return new DocumentStatusResponse(
                this.isProcessing(),
                this.getProcessedCount(),
                this.getTotalCount(),
                this.getChangedCount());
    }
}
