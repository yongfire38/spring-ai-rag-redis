package com.example.chat.controller;

import org.springframework.web.bind.annotation.*;
import com.example.chat.service.DocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/status")
    public DocumentStatusResponse getStatus() {
        log.debug("문서 상태 조회 요청");
        DocumentStatusResponse response = new DocumentStatusResponse(
                documentService.isProcessing(),
                documentService.getProcessedCount(),
                documentService.getTotalCount()
        );
        log.debug("문서 상태 응답: {}", response);
        return response;
    }

    @PostMapping("/reindex")
    public String reindexDocuments() {
        log.info("문서 재인덱싱 요청 수신");
        try {
            documentService.loadDocumentsAsync()
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
}

record DocumentStatusResponse(boolean processing, int processedCount, int totalCount) {}