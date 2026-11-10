package com.smartstay.ai.service;

import java.util.List;

import com.smartstay.ai.dto.MessageDto;

public interface ChatAssistantService {

    String chat(String sessionId,
                String userMessage);

    List<MessageDto> getHistory(String sessionId);
}
