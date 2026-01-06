package com.enhanced.burpgpt.api;

import com.theokanning.openai.completion.chat.ChatFunction;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class DynamicChatFunction extends ChatFunction {
    @JsonProperty("parameters")
    private Map<String, Object> parameters;

    public DynamicChatFunction(String name, String description, Map<String, Object> parameters) {
        super();
        setName(name);
        setDescription(description);
        this.parameters = parameters;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
}
