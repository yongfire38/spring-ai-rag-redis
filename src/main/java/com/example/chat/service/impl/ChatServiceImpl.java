package com.example.chat.service.impl;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.stereotype.Service;

import com.example.chat.service.ChatService;
import com.example.chat.util.MarkdownConverter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final OllamaChatModel ollamaChatModel;
    private final RedisVectorStore redisVectorStore;

    /**
     * RAG 기반 응답 생성
     * 
     * @param query 사용자 질의
     * @return 생성된 응답
     */
    @Override
    public String generateRagResponse(String query) {
        log.info("사용자 질의 수신: {}", query);

        try {

            // step 1. 벡터 스토어에서 관련 문서 검색
            var searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(3)
                    .similarityThreshold(0.6f)
                    .build();

            var searchResults = redisVectorStore.similaritySearch(searchRequest);

            // 검색된 문서 로그 출력 (디버깅용)
            log.info("검색된 문서 개수: {}", searchResults.size());
            int docIndex = 0;
            for (var document : searchResults) {
                log.info("문서 #{}: {}", ++docIndex, document);
                // 유사도 점수가 있다면 출력
                if (document.getMetadata().containsKey("similarity")) {
                    log.info("유사도: {}", document.getMetadata().get("similarity"));
                }
            }

            // step 2. 검색된 문서를 시스템 메시지에 포함
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("다음 정보를 바탕으로 질문에 답변하세요:\n\n");

            for (var document : searchResults) {
                contextBuilder.append("문서:\n");
                contextBuilder.append(document.toString()).append("\n\n");
            }

            // step 3. 프롬프트 생성 및 응답 요청
            Prompt prompt = new Prompt(
                    new SystemMessage(contextBuilder.toString()),
                    new UserMessage(query));

            String response = ollamaChatModel.call(prompt).getResult().getOutput().getText();

            // step 4. 응답을 HTML로 변환
            // 마크다운을 HTML로 변환
            response = MarkdownConverter.convertToHtml(response);

            log.debug("AI 응답 (HTML 변환 후): {}", response);

            return response;

        } catch (Exception e) {
            log.error("AI 응답 생성 중 오류 발생", e);
            return handleException(e);
        }
    }

    /**
     * 일반 응답 생성
     * 벡터 저장소 검색 없이 LLM에 직접 질의하여 응답 생성
     * 
     * @param query 사용자 질의
     * @return 생성된 응답
     */
    @Override
    public String generateSimpleResponse(String query) {
        log.info("일반 채팅 질의 수신: {}", query);

        try {
            // 일반 응답 생성 (RAG 없이)
            Prompt prompt = new Prompt(new UserMessage(query));
            String response = ollamaChatModel.call(prompt).getResult().getOutput().getText();

            // 마크다운을 HTML로 변환
            response = MarkdownConverter.convertToHtml(response);

            log.debug("AI 응답 (HTML 변환 후): {}", response);

            return response;
        } catch (Exception e) {
            log.error("AI 응답 생성 중 오류 발생", e);
            return handleException(e);
        }
    }

    /**
     * RAG 기반 스트리밍 응답 생성
     * 
     * @param query 사용자 질의
     * @return 스트리밍 응답 Flux
     */
    @Override
    public Flux<ChatResponse> streamRagResponse(String query) {
        log.info("RAG 기반 스트리밍 질의 수신: {}", query);

        try {
            // step 1. 벡터 스토어에서 관련 문서 검색
            var searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(3)
                    .similarityThreshold(0.6f)
                    .build();

            var searchResults = redisVectorStore.similaritySearch(searchRequest);

            // 검색된 문서 로그 출력 (디버깅용)
            log.info("검색된 문서 개수: {}", searchResults.size());
            int docIndex = 0;
            for (var document : searchResults) {
                log.info("문서 #{}: {}", ++docIndex, document);
                // 유사도 점수가 있다면 출력
                if (document.getMetadata().containsKey("similarity")) {
                    log.info("유사도: {}", document.getMetadata().get("similarity"));
                }
            }

            // step 2. 검색된 문서를 시스템 메시지에 포함
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("다음 정보를 바탕으로 질문에 답변하세요:\n\n");

            for (var document : searchResults) {
                contextBuilder.append("문서:\n");
                contextBuilder.append(document.toString()).append("\n\n");
            }

            // step 3. 프롬프트 생성 및 스트리밍 응답 요청
            Prompt prompt = new Prompt(
                    new SystemMessage(contextBuilder.toString()),
                    new UserMessage(query));

            return ollamaChatModel.stream(prompt);

        } catch (Exception e) {
            log.error("AI 스트리밍 응답 생성 중 오류 발생", e);
            return Flux.error(e);
        }
    }

    /**
     * 일반 스트리밍 응답 생성
     * 
     * @param query 사용자 질의
     * @return 스트리밍 응답 Flux
     */
    @Override
    public Flux<ChatResponse> streamSimpleResponse(String query) {
        log.info("일반 스트리밍 질의 수신: {}", query);

        try {
            // 일반 스트리밍 응답 생성 (RAG 없이)
            Prompt prompt = new Prompt(new UserMessage(query));
            return ollamaChatModel.stream(prompt);
        } catch (Exception e) {
            log.error("AI 스트리밍 응답 생성 중 오류 발생", e);
            return Flux.error(e);
        }
    }

    /**
     * 예외 처리를 위한 공통 메서드
     * 
     * @param e 발생한 예외
     * @return 사용자에게 표시할 오류 메시지
     */
    private String handleException(Exception e) {
        String errorMessage = e.getMessage();

        // 타임아웃 오류 처리
        if (errorMessage != null && (errorMessage.contains("timeout") || errorMessage.contains("timed out")
                || errorMessage.contains("connection") || e instanceof java.net.SocketTimeoutException
                || e instanceof java.util.concurrent.TimeoutException)) {
            return "죄송합니다. 서버 응답 시간이 초과되었습니다.\n\n" + "지금 서버가 바쁨 상태이거나 질의가 너무 복잡한 것 같습니다.\n" + "잠시 후 다시 시도해주세요.";
        }

        // 기본 오류 메시지
        return "죄송합니다. 응답을 생성하는 중에 오류가 발생했습니다: " + errorMessage;
    }

}
