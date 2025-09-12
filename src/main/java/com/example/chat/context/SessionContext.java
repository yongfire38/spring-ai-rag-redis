package com.example.chat.context;

import org.springframework.ai.chat.memory.ChatMemory;

public class SessionContext {
    
    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();
    
    public static void setCurrentSessionId(String sessionId) {
        CURRENT_SESSION_ID.set(sessionId);
    }
    
    public static String getCurrentSessionId() {
        String sessionId = CURRENT_SESSION_ID.get();
        return sessionId != null ? sessionId : ChatMemory.DEFAULT_CONVERSATION_ID;
    }
    
    public static void clear() {
        CURRENT_SESSION_ID.remove();
    }
}