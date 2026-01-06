package com.enhanced.burpgpt.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

import java.util.concurrent.ConcurrentLinkedQueue;

import burp.api.montoya.logging.Logging;

public class OpenAIProvider {
    private final String model;
    private final String apiKey;
    private final String apiUrl;
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Logging logging;
    private final Consumer<String> statusLogger;
    private Call currentCall;
    private final ConcurrentLinkedQueue<String> injectionQueue = new ConcurrentLinkedQueue<>();

    public OpenAIProvider(String apiKey, String apiUrl, String model, Logging logging) {
        this(apiKey, apiUrl, model, logging, null);
    }

    public OpenAIProvider(String apiKey, String apiUrl, String model, Logging logging, Consumer<String> statusLogger) {
        this.apiKey = apiKey;
        this.model = model;
        this.logging = logging;
        this.statusLogger = statusLogger;
        
        // Ensure apiUrl ends with slash if not empty, otherwise use default
        String baseUrl = (apiUrl != null && !apiUrl.isEmpty()) ? apiUrl : "https://api.openai.com/";
        if (!baseUrl.startsWith("http")) {
            baseUrl = "https://" + baseUrl;
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        this.apiUrl = baseUrl;

        this.client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .build();
    }

    public void cancel() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
            if (logging != null) logging.logToOutput("Request cancelled by user.");
        }
    }

    public void injectUserMessage(String message) {
        injectionQueue.add(message);
        if (logging != null) logging.logToOutput("[DEBUG] Queued user injection: " + message);
    }

    public String sendRequest(String prompt, int maxTokens) {
        return sendRequest(null, prompt, maxTokens);
    }

    public String sendRequest(String systemPrompt, String userPrompt, int maxTokens) {
        // Use List<Map> to manage messages to support new "tool" role and structure
        List<Map<String, Object>> messages = new ArrayList<>();
        
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, Object> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);
        }
        
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        return sendRequestRecursive(messages, maxTokens, 0);
    }

    private String sendRequestRecursive(List<Map<String, Object>> messages, int maxTokens, int depth) {
        
        // Check for injected messages
        while (!injectionQueue.isEmpty()) {
            String msg = injectionQueue.poll();
            Map<String, Object> injectedMsg = new HashMap<>();
            injectedMsg.put("role", "user");
            injectedMsg.put("content", "[User Intervention]: " + msg);
            messages.add(injectedMsg);
            
            logStyle("Intervention", msg, "👤");
        }

        try {
            // Build Request Body
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", this.model);
            requestBody.put("max_tokens", maxTokens);
            
            ArrayNode messagesArray = mapper.valueToTree(messages);
            requestBody.set("messages", messagesArray);

            // Attach Tools
            List<Map<String, Object>> toolDefinitions = ToolExecutor.getAvailableTools();
            if (!toolDefinitions.isEmpty()) {
                if (logging != null) logging.logToOutput("[DEBUG] Loaded " + toolDefinitions.size() + " tools.");
                ArrayNode toolsArray = mapper.valueToTree(toolDefinitions);
                requestBody.set("tools", toolsArray);
                requestBody.put("tool_choice", "auto");
            }

            // Create Request
            String jsonBody = mapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            
            Request request = new Request.Builder()
                .url(this.apiUrl + "chat/completions") // Assuming standard OpenAI path structure
                .post(body)
                .header("Authorization", "Bearer " + this.apiKey)
                .header("Content-Type", "application/json")
                .build();

            // Execute
            currentCall = client.newCall(request);
            try (Response response = currentCall.execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No body";
                    if (logging != null) logging.logToError("API Error: " + response.code() + " - " + errorBody);
                    return "Error: API returned " + response.code();
                }

                String responseString = response.body().string();
                JsonNode responseNode = mapper.readTree(responseString);
                
                JsonNode choices = responseNode.get("choices");
                if (choices != null && choices.size() > 0) {
                    JsonNode messageNode = choices.get(0).get("message");
                    
                    // Check for reasoning_content (DeepSeek R1 / others)
                    if (messageNode.has("reasoning_content") && !messageNode.get("reasoning_content").isNull()) {
                        String reasoning = messageNode.get("reasoning_content").asText();
                        if (!reasoning.isEmpty()) {
                            logStyle("Thinking", reasoning, "🧠");
                        }
                    }

                    // Check for tool_calls (New API)
                    if (messageNode.has("tool_calls")) {
                        // Log regular content if present (Thought before tool call)
                        if (messageNode.has("content") && !messageNode.get("content").isNull()) {
                            String content = messageNode.get("content").asText();
                            if (!content.isEmpty()) {
                                logStyle("Thought", content, "💬");
                            }
                        }

                        // Add assistant message to history
                        messages.add(mapper.convertValue(messageNode, Map.class));
                        
                        JsonNode toolCalls = messageNode.get("tool_calls");
                        if (logging != null) logging.logToOutput("[DEBUG] Processing " + toolCalls.size() + " tool calls.");

                        for (JsonNode toolCall : toolCalls) {
                            String id = toolCall.get("id").asText();
                            JsonNode function = toolCall.get("function");
                            String name = function.get("name").asText();
                            String arguments = function.get("arguments").asText();
                            
                            if (logging != null) logging.logToOutput("[DEBUG] Tool Call: " + name + " args: " + arguments);
                            logStyle("Executing: " + name, "Arguments: " + arguments, "🛠️");
                            
                            String output = ToolExecutor.executeTool(name, arguments);
                            
                            if (logging != null) logging.logToOutput("[DEBUG] Tool Output: " + output);
                            
                            String displayOutput = output.length() > 500 ? output.substring(0, 500) + "... (truncated)" : output;
                            logStyle("Output", displayOutput, "📤");

                            // Append Tool Message
                            Map<String, Object> toolMsg = new HashMap<>();
                            toolMsg.put("role", "tool");
                            toolMsg.put("tool_call_id", id);
                            toolMsg.put("content", output);
                            messages.add(toolMsg);
                        }
                        
                        // Recurse
                        return sendRequestRecursive(messages, maxTokens, depth + 1);
                    }
                    
                    // Fallback for Manual JSON in Content (Legacy/DeepSeek behavior)
                    String content = messageNode.has("content") && !messageNode.get("content").isNull() 
                        ? messageNode.get("content").asText() : null;
                        
                    if (content != null) {
                         try {
                             // Extract JSON block
                             Pattern pattern = Pattern.compile("\\{[\\s\\S]*?\"command\"[\\s\\S]*?\\}");
                             Matcher matcher = pattern.matcher(content);
                             if (matcher.find()) {
                                 String json = matcher.group();
                                 JsonNode node = mapper.readTree(json);
                                 if (node.has("command")) {
                                    if (logging != null) logging.logToOutput("[DEBUG] Detected manual tool call in content: run_cmd");
                                    
                                    // Add assistant message
                                    Map<String, Object> assistantMsg = new HashMap<>();
                                    assistantMsg.put("role", "assistant");
                                    assistantMsg.put("content", content);
                                    messages.add(assistantMsg);

                                    logStyle("Executing: run_cmd", "Arguments: " + json, "🛠️");

                                    // Execute
                                    String output = ToolExecutor.executeTool("run_cmd", json);
                                    if (logging != null) logging.logToOutput("[DEBUG] Tool Output: " + output);
                                    
                                    String displayOutput = output.length() > 500 ? output.substring(0, 500) + "... (truncated)" : output;
                                    logStyle("Output", displayOutput, "📤");
                                    
                                    // Append Tool Message (Simulate)
                                     Map<String, Object> toolMsg = new HashMap<>();
                                     toolMsg.put("role", "user"); // Fallback: send result as user message if strict tool role isn't supported in manual mode
                                     toolMsg.put("content", "Tool Output: " + output);
                                     messages.add(toolMsg);
                                     
                                     return sendRequestRecursive(messages, maxTokens, depth + 1);
                                 }
                             }
                         } catch (Exception e) { /* Ignore */ }
                    }

                    return content;
                }
            }

        } catch (Exception e) {
            if (currentCall != null && currentCall.isCanceled()) {
                return "Analysis cancelled.";
            }
            if (logging != null) logging.logToError("Exception: " + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }

        return "Error: No response.";
    }

    private void logStyle(String title, String content, String icon) {
        if (statusLogger == null) return;
        
        String color = "#333";
        String bgColor = "#fff";
        String borderColor = "#ccc";
        
        if (title.contains("Thinking")) {
            color = "#00529B"; // Deep Blue
            bgColor = "#BDE5F8";
            borderColor = "#00529B";
        } else if (title.contains("Thought")) {
             color = "#4F8A10"; // Green
             bgColor = "#DFF2BF";
             borderColor = "#4F8A10";
        } else if (title.contains("Executing")) {
            color = "#9F6000"; // Orange
            bgColor = "#FEEFB3";
            borderColor = "#9F6000";
        } else if (title.contains("Intervention")) {
             color = "#D8000C"; // Red
             bgColor = "#FFD2D2";
             borderColor = "#D8000C";
        } else if (title.contains("Output")) {
             color = "#333"; 
             bgColor = "#f9f9f9";
             borderColor = "#ccc";
        }

        // Escape HTML content to prevent rendering issues
        String safeContent = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        
        // Manually break long words since Swing HTML engine doesn't support break-all/break-word well
        safeContent = breakLongWords(safeContent);
        
        // Replace newlines
        safeContent = safeContent.replace("\n", "<br>");

        String html = String.format(
            "<div style='border: 1px solid %s; background-color: %s; padding: 8px; margin: 10px 0; border-radius: 5px; font-family: monospace; width: 95%%;'>" +
            "<div style='font-weight: bold; color: %s; border-bottom: 1px solid %s; padding-bottom: 4px; margin-bottom: 4px;'>%s %s</div>" +
            "<div style='color: #333;'>%s</div>" +
            "</div>",
            borderColor, bgColor, color, borderColor, icon, title, safeContent
        );
        
        statusLogger.accept(html);
    }

    private String breakLongWords(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        int consecutive = 0;
        for (char c : input.toCharArray()) {
            sb.append(c);
            if (Character.isWhitespace(c)) {
                consecutive = 0;
            } else {
                consecutive++;
                if (consecutive > 60) { // Force break every 60 chars
                    sb.append(" "); // Insert space to allow break
                    consecutive = 0;
                }
            }
        }
        return sb.toString();
    }
}
