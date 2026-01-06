package com.enhanced.burpgpt;

import com.enhanced.burpgpt.skills.Skill;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Config {
    public static String apiKey = "";
    public static String apiUrl = "https://api.openai.com/v1/chat/completions";
    public static String model = "gpt-4o";
    public static int maxChunkSize = 10000; // Default 10k chars per chunk
    public static String toolsPath = ""; // Path to tools directory
    public static String commandBlacklist = "del,rm,shutdown,restart,format"; // Comma separated commands
    public static String prompt = "Analyze this request and response for security vulnerabilities:\n\nRequest:\n{REQUEST}\n\nResponse:\n{RESPONSE}";
    public static boolean mcpEnabled = true;
    public static java.util.List<MCPServerConfig> mcpServers = new java.util.ArrayList<>();
    public static java.util.List<Skill> customSkills = new java.util.ArrayList<>();

    private static final String CONFIG_FILE = System.getProperty("user.home") + File.separator + ".burpgpt_config.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        try {
            if (Files.exists(Paths.get(CONFIG_FILE))) {
                String json = new String(Files.readAllBytes(Paths.get(CONFIG_FILE)));
                ConfigData data = gson.fromJson(json, ConfigData.class);
                if (data != null) {
                    apiKey = data.apiKey != null ? data.apiKey : "";
                    apiUrl = data.apiUrl != null ? data.apiUrl : "https://api.openai.com/v1/chat/completions";
                    model = data.model != null ? data.model : "gpt-4o";
                    maxChunkSize = data.maxChunkSize > 0 ? data.maxChunkSize : 10000;
                    toolsPath = data.toolsPath != null ? data.toolsPath : "";
                    commandBlacklist = data.commandBlacklist != null ? data.commandBlacklist : "del,rm,shutdown,restart,format";
                    prompt = data.prompt != null ? data.prompt : "Analyze this request and response for security vulnerabilities:\n\nRequest:\n{REQUEST}\n\nResponse:\n{RESPONSE}";
                    mcpEnabled = data.mcpEnabled;
                    mcpServers = data.mcpServers != null ? data.mcpServers : new java.util.ArrayList<>();
                    customSkills = data.customSkills != null ? data.customSkills : new java.util.ArrayList<>();
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading config: " + e.getMessage());
        }
    }

    public static void save() {
        try {
            ConfigData data = new ConfigData();
            data.apiKey = apiKey;
            data.apiUrl = apiUrl;
            data.model = model;
            data.maxChunkSize = maxChunkSize;
            data.toolsPath = toolsPath;
            data.commandBlacklist = commandBlacklist;
            data.prompt = prompt;
            data.mcpEnabled = mcpEnabled;
            data.mcpServers = mcpServers;
            data.customSkills = customSkills;
            
            String json = gson.toJson(data);
            Files.write(Paths.get(CONFIG_FILE), json.getBytes());
        } catch (Exception e) {
            System.err.println("Error saving config: " + e.getMessage());
        }
    }

    public static class MCPServerConfig {
        public String name;
        public String command;
        public String transport; // "stdio" or "sse"

        public MCPServerConfig() {}
        public MCPServerConfig(String name, String command) {
            this(name, command, "stdio");
        }
        public MCPServerConfig(String name, String command, String transport) {
            this.name = name;
            this.command = command;
            this.transport = transport != null ? transport : "stdio";
        }
        
        @Override
        public String toString() {
            return name + " (" + transport + ": " + command + ")";
        }
    }

    private static class ConfigData {
        String apiKey;
        String apiUrl;
        String model;
        int maxChunkSize;
        String toolsPath;
        String commandBlacklist;
        String prompt;
        boolean mcpEnabled = true;
        java.util.List<MCPServerConfig> mcpServers;
        java.util.List<Skill> customSkills;
    }
}
