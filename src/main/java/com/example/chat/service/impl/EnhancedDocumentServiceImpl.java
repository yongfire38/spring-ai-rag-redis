package com.example.chat.service.impl;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.chat.service.DocumentService;
import com.example.chat.service.DocumentStatusResponse;
import com.example.chat.util.DocumentHashUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedDocumentServiceImpl implements DocumentService {

    // ETL 파이프라인 컴포넌트들
    private final DocumentReader markdownReader;
    private final DocumentTransformer enhancedDocumentTransformer;
    private final DocumentWriter vectorStoreWriter;
    
    // 직접 Redis 저장을 위한 컴포넌트
    private final RedisVectorStore redisVectorStore;
    
    // 기존 의존성들
    private final StringRedisTemplate stringRedisTemplate;
    private final Executor executor;

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger changedCount = new AtomicInteger(0);

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

        log.info("Spring AI ETL 파이프라인으로 문서 처리 시작");
        isProcessing.set(true);
        processedCount.set(0);
        totalCount.set(0);
        changedCount.set(0);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1단계: 문서 읽기
                List<Document> documents = markdownReader.get();
                totalCount.set(documents.size());
                log.info("총 {}개의 문서를 로드했습니다.", documents.size());

                // 2단계: 변경된 문서 필터링
                List<Document> changedDocuments = filterChangedDocuments(documents);
                changedCount.set(changedDocuments.size());
                log.info("총 {}개의 문서 중 {}개의 변경된 문서를 처리합니다.",
                        documents.size(), changedDocuments.size());

                if (changedDocuments.isEmpty()) {
                    log.info("변경된 문서가 없습니다. 인덱싱 작업을 건너뜁니다.");
                    return 0;
                }

                // 3단계: 문서 변환 (분할 + 요약 생성)
                List<Document> processedDocuments = enhancedDocumentTransformer.apply(changedDocuments);
                log.info("문서 변환 완료: {}개 청크 생성", processedDocuments.size());

                // 4단계: 벡터 저장소에 저장 (ETL 파이프라인 사용)
                try {
                    vectorStoreWriter.accept(processedDocuments);
                    log.info("ETL 파이프라인을 통한 벡터 저장 완료");
                } catch (Exception e) {
                    log.warn("ETL 파이프라인 저장 실패, 직접 저장 시도: {}", e.getMessage());
                    // ETL 파이프라인 실패 시 직접 저장
                    redisVectorStore.add(processedDocuments);
                    log.info("직접 벡터 저장 완료");
                }

                // 5단계: 청크 생성 완료 후 해시값 저장 (원자적 처리)
                // 청크가 실제로 저장되었는지 확인 후 해시값 저장
                if (processedDocuments.size() > 0) {
                    for (Document originalDoc : changedDocuments) {
                        saveDocumentHash(originalDoc);
                    }
                    log.info("문서 해시값 저장 완료: {}개 문서", changedDocuments.size());
                } else {
                    log.warn("청크 생성이 실패했으므로 해시값 저장을 건너뜁니다.");
                }
                
                processedCount.set(processedDocuments.size());
                log.info("ETL 파이프라인 완료: {}개 청크 처리됨", processedDocuments.size());
                return processedDocuments.size();

            } catch (Exception e) {
                log.error("ETL 파이프라인 실행 중 오류 발생", e);
                throw new RuntimeException("문서 처리 중 오류 발생", e);
            } finally {
                isProcessing.set(false);
            }
        }, executor);
    }

    @Override
    public Map<String, Object> uploadMarkdownFiles(MultipartFile[] files) {
        // 결과 맵 초기화
        Map<String, Object> result = new HashMap<>();
        if (files == null || files.length == 0) {
            result.put("success", false);
            result.put("message", "업로드할 파일이 없습니다.");
            result.putIfAbsent("files", Collections.emptyList());
            return result;
        }
        if (files.length > 5) {
            result.put("success", false);
            result.put("message", "최대 5개 파일만 업로드할 수 있습니다.");
            result.putIfAbsent("files", Collections.emptyList());
            return result;
        }
        long totalSize = 0;
        int uploaded = 0;
        for (MultipartFile file : files) {
            String filename = StringUtils.cleanPath(file.getOriginalFilename());
            if (!filename.endsWith(".md")) {
                result.put("success", false);
                result.put("message", "마크다운(.md) 파일만 업로드 가능합니다.");
                result.putIfAbsent("files", Collections.emptyList());
                return result;
            }
            if (file.getSize() > 5 * 1024 * 1024) {
                result.put("success", false);
                result.put("message", "파일당 최대 5MB까지만 업로드할 수 있습니다.");
                result.putIfAbsent("files", Collections.emptyList());
                return result;
            }
            totalSize += file.getSize();
        }
        if (totalSize > 20 * 1024 * 1024) {
            result.put("success", false);
            result.put("message", "총 20MB를 초과할 수 없습니다.");
            result.putIfAbsent("files", Collections.emptyList());
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
            } catch (Exception e) {
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

    /**
     * 변경된 문서만 필터링하는 메서드
     */
    private List<Document> filterChangedDocuments(List<Document> documents) {
        return documents.stream()
                .filter(this::isDocumentChanged)
                .toList();
    }

    /**
     * 문서가 변경되었는지 확인하는 메서드 (해시 저장 없이)
     */
    private boolean isDocumentChanged(Document document) {
        String docId = document.getId();
        String content = document.getText();

        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        // 문서 내용의 해시 계산
        String newHash = DocumentHashUtil.calculateHash(content);

        // Redis에서 기존 해시 조회
        String redisKey = "docmeta:" + docId;
        String oldHash = stringRedisTemplate.opsForValue().get(redisKey);

        if (oldHash != null && oldHash.equals(newHash)) {
            log.debug("문서 '{}' 변경 없음 (해시: {})", docId, newHash);
            return false;
        }

        // 해시가 다르거나 없으면 변경됨으로 판단 (해시 저장은 나중에)
        log.debug("문서 '{}' 변경 감지 (이전 해시: {}, 새 해시: {})", docId, oldHash, newHash);
        return true;
    }

    /**
     * 문서 처리 완료 후 해시값을 저장하는 메서드
     */
    private void saveDocumentHash(Document document) {
        String docId = document.getId();
        String content = document.getText();

        if (content != null && !content.trim().isEmpty()) {
            String newHash = DocumentHashUtil.calculateHash(content);
            String redisKey = "docmeta:" + docId;
            stringRedisTemplate.opsForValue().set(redisKey, newHash);
            log.debug("문서 '{}' 해시 저장 완료: {}", docId, newHash);
        }
    }
} 