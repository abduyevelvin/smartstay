package com.smartstay.ai.event;

import java.util.List;

// Internal model — Spring AI deserializes the LLM JSON response into this
public record ReviewAnalysis(Sentiment sentiment, List<String> tags       // e.g. ["clean", "friendly", "good location"]
) {

}
