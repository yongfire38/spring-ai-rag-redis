package com.example.chat.service.impl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;

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
    private final Advisor retrievalAugmentationAdvisor;
    private final ChatClient ollamaChatClient;

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
            // 응답 요청
            String response = ollamaChatClient.prompt()
                    .advisors(retrievalAugmentationAdvisor)
                    .user(query)
                    .call()
                    .content();

            // 응답을 HTML로 변환
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
            return ollamaChatClient.prompt()
                    .advisors(retrievalAugmentationAdvisor)
                    .user(query).stream().chatResponse();

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
            return ollamaChatClient.prompt()
                    .user(query).stream().chatResponse();
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
