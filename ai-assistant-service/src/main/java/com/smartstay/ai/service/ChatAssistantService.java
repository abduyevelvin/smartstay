package com.smartstay.ai.service;

import java.util.List;

import com.smartstay.ai.dto.MessageDto;

public interface ChatAssistantService {

    /**
     * @param userId      the authenticated user's ID (from X-User-Id header)
     * @param sessionId   the client-supplied session UUID (from sessionStorage)
     * @param userRole    the authenticated user's role (from X-User-Role header),
     *                    e.g. {@code "GUEST"}, {@code "HOTEL_MANAGER"}, {@code "ADMIN"}
     * @param userMessage the raw text sent by the user
     */
    String chat(String userId,
                String sessionId,
                String userRole,
                String userMessage);

    /**
     * @param userId    the authenticated user's ID (from X-User-Id header)
     * @param sessionId the client-supplied session UUID
     */
    List<MessageDto> getHistory(String userId,
                                String sessionId);
}
