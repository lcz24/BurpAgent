package com.enhanced.burpgpt.api;

import com.enhanced.burpgpt.Config;
import com.enhanced.burpgpt.mcp.MCPManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.http.HttpService;

public class ToolExecutor {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static MontoyaApi api;

    public static void initialize(MontoyaApi montoyaApi) {
        api = montoyaApi;
    }

    public static List<Map<String, Object>> getAvailableTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // Add internal tool: send_request
        Map<String, Object> sendRequestTool = Map.of(
            "type", "function",
            "function", Map.of(
                "name", "send_request",
                "description", "Send a custom HTTP request using Burp Suite. Use this to test payloads or bypasses. Returns the full response.",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "request", Map.of(
                            "type", "string",
                            "description", "The raw HTTP request to send."
                        ),
                        "host", Map.of(
                            "type", "string",
                            "description", "The target host (e.g. example.com)"
                        ),
                        "port", Map.of(
                            "type", "integer",
                            "description", "The target port (e.g. 443)"
                        ),
                        "use_https", Map.of(
                            "type", "boolean",
                            "description", "True for HTTPS, False for HTTP"
                        )
                    ),
                    "required", List.of("request", "host", "port", "use_https")
                )
            )
        );
        tools.add(sendRequestTool);

        // Add internal tool: read_file
        Map<String, Object> readFileTool = Map.of(
            "type", "function",
            "function", Map.of(
                "name", "read_file",
                "description", "Read the content of a file from the local file system. Use this to analyze source code or config files.",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "path", Map.of(
                            "type", "string",
                            "description", "The absolute path to the file."
                        )
                    ),
                    "required", List.of("path")
                )
            )
        );
        tools.add(readFileTool);

        // Add internal tool: list_files
        Map<String, Object> listFilesTool = Map.of(
            "type", "function",
            "function", Map.of(
                "name", "list_files",
                "description", "List files and directories in a given path.",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "path", Map.of(
                            "type", "string",
                            "description", "The absolute path to the directory."
                        )
                    ),
                    "required", List.of("path")
                )
            )
        );
        tools.add(listFilesTool);

        // Add MCP Tools
        tools.addAll(MCPManager.getAllTools());

        String toolsPath = Config.toolsPath;

        if (toolsPath == null || toolsPath.isEmpty()) {
            return tools;
        }

        File toolsDir = new File(toolsPath);
        if (!toolsDir.exists() || !toolsDir.isDirectory()) {
            return tools;
        }

        // Look for tools.json definition file
        File definitionFile = new File(toolsDir, "tools.json");
        if (definitionFile.exists()) {
            try {
                // Parse tools.json and add to list
                ArrayNode root = (ArrayNode) mapper.readTree(definitionFile);
                if (root != null) {
                    for (int i = 0; i < root.size(); i++) {
                        tools.add(mapper.convertValue(root.get(i), Map.class));
                    }
                }
            } catch (Exception e) {
                System.err.println("Error parsing tools.json: " + e.getMessage());
            }
        }

        return tools;
    }

    public static String executeTool(String toolName, String argumentsJson) {
        // Handle internal tools
        if ("send_request".equals(toolName)) {
            return executeSendRequest(argumentsJson);
        }
        if ("read_file".equals(toolName)) {
            return executeReadFile(argumentsJson);
        }
        if ("list_files".equals(toolName)) {
            return executeListFiles(argumentsJson);
        }

        // Try MCP tools
        String mcpResult = MCPManager.executeTool(toolName, argumentsJson);
        if (mcpResult != null) {
            return mcpResult;
        }

        String toolsPath = Config.toolsPath;
        if (toolsPath == null || toolsPath.isEmpty()) {
            return "Error: Tools directory not configured.";
        }

        // Simple mapping: toolName -> script file
        // e.g. run_sqlmap -> run_sqlmap.py / run_sqlmap.bat
        File scriptFile = findScript(toolsPath, toolName);
        if (scriptFile == null) {
            return "Error: Tool script not found for " + toolName;
        }

        if (isBlacklisted(toolName, argumentsJson)) {
            int choice = JOptionPane.showConfirmDialog(
                    null,
                    "The tool execution contains a blacklisted command.\nTool: " + toolName + "\nArguments: " + argumentsJson + "\n\nDo you want to proceed?",
                    "Restricted Command Warning",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.YES_OPTION) {
                return "Error: Execution cancelled by user due to blacklisted command.";
            }
        }

        try {
            List<String> command = new ArrayList<>();
            
            // Determine executor based on extension
            String fileName = scriptFile.getName().toLowerCase();
            if (fileName.endsWith(".py")) {
                command.add("python");
                command.add(scriptFile.getAbsolutePath());
            } else if (fileName.endsWith(".sh")) {
                command.add("bash");
                command.add(scriptFile.getAbsolutePath());
            } else if (fileName.endsWith(".bat") || fileName.endsWith(".cmd")) {
                command.add("cmd.exe");
                command.add("/c");
                command.add(scriptFile.getAbsolutePath());
            } else {
                // Try executing directly
                command.add(scriptFile.getAbsolutePath());
            }

            // Parse arguments and pass them
            // We parse the JSON arguments and convert them to CLI flags: --key "value"
            try {
                ObjectNode argsNode = (ObjectNode) mapper.readTree(argumentsJson);
                argsNode.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue().asText();
                    command.add("--" + key);
                    command.add(value);
                });
            } catch (Exception e) {
                // If parsing fails, try to construct a simple JSON object if it looks like a raw command
                if (!argumentsJson.trim().startsWith("{")) {
                    command.add("--command");
                    command.add(argumentsJson);
                } else {
                     // If parsing fails, fallback to passing raw JSON
                    System.err.println("Failed to parse arguments JSON, passing raw: " + e.getMessage());
                    command.add(argumentsJson);
                }
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(toolsPath));
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            
            // Detect OS to choose correct charset
            Charset charset = Charset.defaultCharset();
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows often uses GBK or similar for console output in China region
                // Try to detect or fallback to "GBK" if system default is not enough
                try {
                    charset = Charset.forName("GBK");
                } catch (Exception ignored) {
                    // Fallback to default if GBK not supported
                }
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            process.waitFor();
            return output.toString();

        } catch (Exception e) {
            return "Error executing tool: " + e.getMessage();
        }
    }

    private static String executeSendRequest(String argumentsJson) {
        if (api == null) {
            return "Error: Burp API not initialized.";
        }
        
        try {
            ObjectNode argsNode = (ObjectNode) mapper.readTree(argumentsJson);
            String requestStr = argsNode.get("request").asText();
            
            // Normalize line endings to CRLF for raw HTTP
            requestStr = requestStr.replace("\r\n", "\n").replace("\n", "\r\n");
            
            String host = argsNode.get("host").asText();
            int port = argsNode.get("port").asInt();
            boolean useHttps = argsNode.get("use_https").asBoolean();
            
            HttpService service = HttpService.httpService(host, port, useHttps);
            
            // Ensure request string has proper termination for HTTP headers
            if (!requestStr.endsWith("\r\n\r\n")) {
                if (requestStr.endsWith("\r\n")) {
                    requestStr = requestStr + "\r\n";
                } else {
                    requestStr = requestStr + "\r\n\r\n";
                }
            }
            
            // Fix Content-Length if body exists
            if (requestStr.contains("\r\n\r\n")) {
                String[] parts = requestStr.split("\r\n\r\n", 2);
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    String body = parts[1];
                    String headers = parts[0];
                    int contentLength = body.getBytes().length;
                    
                    // Replace existing Content-Length or add it
                    if (headers.toLowerCase().contains("content-length:")) {
                        headers = headers.replaceAll("(?i)Content-Length:\\s*\\d+", "Content-Length: " + contentLength);
                    } else {
                        headers += "\r\nContent-Length: " + contentLength;
                    }
                    requestStr = headers + "\r\n\r\n" + body;
                }
            }
            
            HttpRequest request = HttpRequest.httpRequest(service, requestStr);
            
            HttpResponse response = api.http().sendRequest(request).response();
            
            return response.toString();
        } catch (Exception e) {
            return "Error sending request: " + e.getMessage();
        }
    }

    private static String executeReadFile(String argumentsJson) {
        try {
            ObjectNode argsNode = (ObjectNode) mapper.readTree(argumentsJson);
            String path = argsNode.get("path").asText();
            File file = new File(path);
            
            if (!file.exists()) {
                return "Error: File not found: " + path;
            }
            
            if (file.length() > 50000) {
                return "Error: File too large to read directly (Size: " + file.length() + " bytes). Max 50KB.";
            }
            
            return Files.readString(file.toPath());
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    private static String executeListFiles(String argumentsJson) {
        try {
            ObjectNode argsNode = (ObjectNode) mapper.readTree(argumentsJson);
            String path = argsNode.get("path").asText();
            File dir = new File(path);
            
            if (!dir.exists() || !dir.isDirectory()) {
                return "Error: Directory not found: " + path;
            }
            
            StringBuilder sb = new StringBuilder();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    sb.append(f.isDirectory() ? "[DIR] " : "[FILE] ");
                    sb.append(f.getName()).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error listing files: " + e.getMessage();
        }
    }

    private static File findScript(String dirPath, String baseName) {
        File dir = new File(dirPath);
        String[] extensions = {".py", ".bat", ".cmd", ".sh", ".exe", ""};
        
        for (String ext : extensions) {
            File f = new File(dir, baseName + ext);
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }

    private static boolean isBlacklisted(String toolName, String argumentsJson) {
        if (containsBlacklistedWord(toolName)) return true;
        if (argumentsJson == null) return false;

        try {
            if (argumentsJson.trim().startsWith("{")) {
                ObjectNode argsNode = (ObjectNode) mapper.readTree(argumentsJson);
                // Check all values
                boolean[] found = {false};
                argsNode.fields().forEachRemaining(entry -> {
                    if (containsBlacklistedWord(entry.getValue().asText())) {
                        found[0] = true;
                    }
                });
                return found[0];
            } else {
                return containsBlacklistedWord(argumentsJson);
            }
        } catch (Exception e) {
            // If parse fails, check raw
            return containsBlacklistedWord(argumentsJson);
        }
    }

    private static boolean containsBlacklistedWord(String text) {
        if (Config.commandBlacklist == null || Config.commandBlacklist.isEmpty()) {
            return false;
        }
        if (text == null) return false;
        
        String[] blacklist = Config.commandBlacklist.split(",");
        for (String blockedCmd : blacklist) {
            blockedCmd = blockedCmd.trim();
            if (blockedCmd.isEmpty()) continue;
            // Case insensitive whole word match
            if (Pattern.compile("(?i)\\b" + Pattern.quote(blockedCmd) + "\\b").matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
