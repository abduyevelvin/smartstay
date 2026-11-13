package com.smartstay.ai.service;

/**
 * Manages the multi-turn AI-guided hotel creation conversation for hotel managers.
 * <p>
 * State is scoped to a <em>safe session ID</em> ({@code userId:sessionId}),
 * the same namespace used by {@link ChatAssistantService}.
 */
public interface HotelCreationService {

    /**
     * Returns {@code true} when there is an active hotel-creation conversation
     * in progress for the given session.
     */
    boolean hasActiveSession(String safeSessionId);

    /**
     * Starts a new hotel-creation conversation.
     *
     * @param safeSessionId namespaced session key ({@code userId:sessionId})
     * @return the first message to display to the manager
     */
    String start(String safeSessionId);

    /**
     * Handles the next manager message within an active hotel-creation session.
     *
     * @param managerId     the authenticated user's ID (forwarded to hotel-service)
     * @param safeSessionId namespaced session key ({@code userId:sessionId})
     * @param userMessage   the raw text typed by the manager
     * @return the AI assistant's response
     */
    String handleMessage(String managerId,
                         String safeSessionId,
                         String userMessage);

    /**
     * Forcibly removes any active session for this key (used on logout / timeout).
     */
    void cancelSession(String safeSessionId);
}
