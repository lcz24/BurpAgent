package com.enhanced.burpgpt.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MCPClient {
    private final String name;
    private final Transport transport;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

    public MCPClient(String name, String command) {
        this(name, command, "stdio");
    }

    public MCPClient(String name, String command, String transportType) {
        this.name = name;
        if ("sse".equalsIgnoreCase(transportType)) {
            this.transport = new SSETransport(command);
        } else {
            this.transport = new StdioTransport(command);
        }
    }

    public void start() throws IOException {
        transport.setListener(this::onMessage);
        transport.start();
        initialize();
    }

    public void stop() {
        transport.stop();
    }

    private void onMessage(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            if (node.has("id")) {
                int id = node.get("id").asInt();
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                if (future != null) {
                    if (node.has("error")) {
                        future.completeExceptionally(new RuntimeException(node.get("error").toString()));
                    } else {
                        future.complete(node.get("result"));
                    }
                }
            }
            // Handle notifications
        } catch (Exception e) {
            System.err.println("Error parsing MCP message: " + e.getMessage() + " content: " + message);
        }
    }

    private JsonNode sendRequest(String method, Object params) throws Exception {
        int id = idCounter.incrementAndGet();
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.set("params", mapper.valueToTree(params));
        }

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        transport.send(mapper.writeValueAsString(request));

        return future.get(30, TimeUnit.SECONDS);
    }

    private void initialize() {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("protocolVersion", "2024-11-05");
            Map<String, Object> clientInfo = new HashMap<>();
            clientInfo.put("name", "BurpAgent");
            clientInfo.put("version", "1.0");
            params.put("clientInfo", clientInfo);
            
            Map<String, Object> capabilities = new HashMap<>();
            capabilities.put("sampling", new HashMap<>());
            capabilities.put("roots", new HashMap<>());
            params.put("capabilities", capabilities);

            sendRequest("initialize", params);
            sendRequest("notifications/initialized", new HashMap<>());
        } catch (Exception e) {
            System.err.println("Failed to initialize MCP client " + name + ": " + e.getMessage());
        }
    }

    public JsonNode listTools() {
        try {
            JsonNode result = sendRequest("tools/list", null);
            return result.get("tools");
        } catch (Exception e) {
            System.err.println("Failed to list tools for " + name + ": " + e.getMessage());
            return mapper.createArrayNode();
        }
    }

    public String callTool(String toolName, Map<String, Object> args) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", args);

        JsonNode result = sendRequest("tools/call", params);
        
        if (result.has("content")) {
            StringBuilder output = new StringBuilder();
            for (JsonNode item : result.get("content")) {
                if (item.has("text")) {
                    output.append(item.get("text").asText());
                }
            }
            return output.toString();
        }
        
        return result.toString();
    }

    public String getName() {
        return name;
    }
}
