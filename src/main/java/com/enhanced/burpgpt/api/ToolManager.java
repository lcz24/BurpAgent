package com.enhanced.burpgpt.api;

import com.enhanced.burpgpt.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ToolManager {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<ToolDefinition> loadTools() {
        List<ToolDefinition> tools = new ArrayList<>();
        String toolsPath = Config.toolsPath;
        if (toolsPath == null || toolsPath.isEmpty()) return tools;

        File toolsDir = new File(toolsPath);
        if (!toolsDir.exists()) return tools;

        File jsonFile = new File(toolsDir, "tools.json");
        if (!jsonFile.exists()) return tools;

        try {
            ArrayNode root = (ArrayNode) mapper.readTree(jsonFile);
            for (JsonNode node : root) {
                JsonNode func = node.get("function");
                ToolDefinition tool = new ToolDefinition();
                tool.name = func.get("name").asText();
                tool.description = func.get("description").asText();
                tool.parameters = func.get("parameters").toString();
                
                // Try to find associated script
                tool.scriptType = findScriptType(toolsDir, tool.name);
                tool.scriptContent = loadScriptContent(toolsDir, tool.name, tool.scriptType);
                
                tools.add(tool);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tools;
    }

    public static void saveTool(ToolDefinition tool) throws IOException {
        String toolsPath = Config.toolsPath;
        if (toolsPath == null || toolsPath.isEmpty()) throw new IOException("Tools directory not configured");

        File toolsDir = new File(toolsPath);
        if (!toolsDir.exists()) toolsDir.mkdirs();

        // 1. Update tools.json
        File jsonFile = new File(toolsDir, "tools.json");
        ArrayNode root;
        if (jsonFile.exists()) {
            root = (ArrayNode) mapper.readTree(jsonFile);
        } else {
            root = mapper.createArrayNode();
        }

        // Remove existing entry if update
        Iterator<JsonNode> it = root.iterator();
        while (it.hasNext()) {
            if (it.next().get("function").get("name").asText().equals(tool.name)) {
                it.remove();
            }
        }

        // Add new entry
        ObjectNode entry = mapper.createObjectNode();
        entry.put("type", "function");
        ObjectNode func = entry.putObject("function");
        func.put("name", tool.name);
        func.put("description", tool.description);
        func.set("parameters", mapper.readTree(tool.parameters));
        root.add(entry);

        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, root);

        // 2. Save script file
        String ext = getExtension(tool.scriptType);
        if (ext != null) {
            File scriptFile = new File(toolsDir, tool.name + ext);
            Files.writeString(scriptFile.toPath(), tool.scriptContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }
    
    public static void deleteTool(String toolName) throws IOException {
        String toolsPath = Config.toolsPath;
        if (toolsPath == null || toolsPath.isEmpty()) return;
        
        File toolsDir = new File(toolsPath);
        
        // 1. Update tools.json
        File jsonFile = new File(toolsDir, "tools.json");
        if (jsonFile.exists()) {
            ArrayNode root = (ArrayNode) mapper.readTree(jsonFile);
            Iterator<JsonNode> it = root.iterator();
            while (it.hasNext()) {
                if (it.next().get("function").get("name").asText().equals(toolName)) {
                    it.remove();
                }
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, root);
        }
        
        // 2. Delete script files
        String[] exts = {".py", ".sh", ".bat", ".cmd"};
        for (String ext : exts) {
            File f = new File(toolsDir, toolName + ext);
            if (f.exists()) f.delete();
        }
    }

    private static String findScriptType(File dir, String name) {
        if (new File(dir, name + ".py").exists()) return "Python";
        if (new File(dir, name + ".sh").exists()) return "Bash";
        if (new File(dir, name + ".bat").exists()) return "Batch";
        return "Unknown";
    }
    
    private static String getExtension(String type) {
        if ("Python".equals(type)) return ".py";
        if ("Bash".equals(type)) return ".sh";
        if ("Batch".equals(type)) return ".bat";
        return null;
    }

    private static String loadScriptContent(File dir, String name, String type) {
        String ext = getExtension(type);
        if (ext == null) return "";
        try {
            File f = new File(dir, name + ext);
            if (f.exists()) return Files.readString(f.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static class ToolDefinition {
        public String name;
        public String description;
        public String parameters = "{\"type\": \"object\", \"properties\": {}, \"required\": []}";
        public String scriptType = "Python";
        public String scriptContent = "";
    }
}
