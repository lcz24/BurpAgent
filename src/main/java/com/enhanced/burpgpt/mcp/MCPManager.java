package com.enhanced.burpgpt.mcp;

import com.enhanced.burpgpt.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MCPManager {
    private static final Map<String, MCPClient> clients = new ConcurrentHashMap<>();
    private static final Map<String, MCPClient> toolOwnerMap = new ConcurrentHashMap<>();
    private static final Map<String, String> serverStatus = new ConcurrentHashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final List<Consumer<Void>> statusListeners = new ArrayList<>();

    public static void reload() {
        reloadAsync(null);
    }

    public static void reloadAsync(Runnable callback) {
        executor.submit(() -> {
            try {
                // Stop existing clients
                stopAll();
                clients.clear();
                toolOwnerMap.clear();
                serverStatus.clear();
                notifyStatusChanged();

                if (!Config.mcpEnabled) {
                    System.out.println("MCP is disabled.");
                    if (callback != null) callback.run();
                    return;
                }

                // Load from config
                if (Config.mcpServers != null) {
                    for (Config.MCPServerConfig serverConfig : Config.mcpServers) {
                        serverStatus.put(serverConfig.name, "CONNECTING");
                        notifyStatusChanged();
                        
                        try {
                            String transport = serverConfig.transport != null ? serverConfig.transport : "stdio";
                            MCPClient client = new MCPClient(serverConfig.name, serverConfig.command, transport);
                            client.start();
                            clients.put(serverConfig.name, client);
                            System.out.println("Started MCP Client: " + serverConfig.name);
                            serverStatus.put(serverConfig.name, "CONNECTED");
                            
                            // Pre-fetch tools to populate mapping
                            cacheTools(client);
                        } catch (Exception e) {
                            System.err.println("Failed to start MCP Client " + serverConfig.name + ": " + e.getMessage());
                            serverStatus.put(serverConfig.name, "FAILED: " + e.getMessage());
                        }
                        notifyStatusChanged();
                    }
                }
            } finally {
                if (callback != null) callback.run();
            }
        });
    }
    
    public static Map<String, String> getServerStatus() {
        return new HashMap<>(serverStatus);
    }
    
    public static void addStatusListener(Consumer<Void> listener) {
        statusListeners.add(listener);
    }
    
    private static void notifyStatusChanged() {
        for (Consumer<Void> listener : statusListeners) {
            listener.accept(null);
        }
    }
    
    private static void cacheTools(MCPClient client) {
        JsonNode tools = client.listTools();
        if (tools != null && tools.isArray()) {
            for (JsonNode tool : tools) {
                String toolName = tool.get("name").asText();
                toolOwnerMap.put(toolName, client);
            }
        }
    }

    public static void stopAll() {
        for (MCPClient client : clients.values()) {
            client.stop();
        }
    }

    public static List<Map<String, Object>> getAllTools() {
        List<Map<String, Object>> openAITools = new ArrayList<>();
        toolOwnerMap.clear(); // Refresh mapping

        for (MCPClient client : clients.values()) {
            JsonNode tools = client.listTools();
            if (tools != null && tools.isArray()) {
                for (JsonNode tool : tools) {
                    try {
                        String toolName = tool.get("name").asText();
                        toolOwnerMap.put(toolName, client); // Update mapping
                        
                        Map<String, Object> openAITool = new HashMap<>();
                        openAITool.put("type", "function");
                        
                        Map<String, Object> function = new HashMap<>();
                        function.put("name", toolName);
                        if (tool.has("description")) {
                            function.put("description", tool.get("description").asText());
                        }
                        if (tool.has("inputSchema")) {
                            function.put("parameters", mapper.convertValue(tool.get("inputSchema"), Map.class));
                        } else {
                            function.put("parameters", new HashMap<>());
                        }
                        
                        openAITool.put("function", function);
                        openAITools.add(openAITool);
                    } catch (Exception e) {
                        System.err.println("Error converting MCP tool: " + e.getMessage());
                    }
                }
            }
        }
        return openAITools;
    }

    public static String executeTool(String toolName, String argsJson) {
        MCPClient client = toolOwnerMap.get(toolName);
        if (client == null) {
            // Try to find it again (maybe it's a new tool or mapping is stale)
            // But we can't easily query all clients for one tool without listing all.
            // Let's assume it's not found.
            return null; // Return null to indicate "not an MCP tool"
        }

        try {
            Map<String, Object> args = mapper.readValue(argsJson, Map.class);
            return client.callTool(toolName, args);
        } catch (Exception e) {
            return "Error executing MCP tool " + toolName + ": " + e.getMessage();
        }
    }
}
