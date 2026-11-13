package com.smartstay.ai.service;

import java.util.List;

import com.smartstay.ai.dto.MessageDto;

public interface ChatAssistantService {

    /**
     * @param userId    the authenticated user's ID (from X-User-Id header)
     * @param sessionId the client-supplied session UUID (from sessionStorage)
     */
    String chat(String userId,
                String sessionId,
                String userMessage);

    /**
     * @param userId    the authenticated user's ID (from X-User-Id header)
     * @param sessionId the client-supplied session UUID
     */
    List<MessageDto> getHistory(String userId,
                                String sessionId);
}
