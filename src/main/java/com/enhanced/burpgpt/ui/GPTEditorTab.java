package com.enhanced.burpgpt.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import com.enhanced.burpgpt.Config;
import com.enhanced.burpgpt.api.OpenAIProvider;
import com.enhanced.burpgpt.skills.Skill;
import com.enhanced.burpgpt.skills.SkillManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.BadLocationException;
import java.io.IOException;

public class GPTEditorTab implements ExtensionProvidedHttpRequestEditor, ExtensionProvidedHttpResponseEditor {
    private final MontoyaApi api;
    private final JPanel panel;
    private final JTextPane outputArea;
    private HttpRequestResponse currentRequestResponse;
    private volatile boolean isRunning = false;
    private OpenAIProvider currentProvider;
    private final JButton analyzeButton;
    private final JButton stopButton;
    private final JButton skillsButton;
    private final ConcurrentLinkedQueue<String> userInterventions = new ConcurrentLinkedQueue<>();

    public GPTEditorTab(MontoyaApi api, boolean isEditable) {
        this.api = api;
        this.panel = new JPanel(new BorderLayout());
        
        // Output Area
        this.outputArea = new JTextPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true; // Force line wrapping
            }
        };
        this.outputArea.setEditable(false);
        this.outputArea.setContentType("text/html");
        
        // Use Burp's current font settings but increase size slightly for readability
        Font burpFont = api.userInterface().currentDisplayFont();
        int fontSize = burpFont.getSize() + 2; // Increase font size
        String fontStyle = "font-family: " + burpFont.getFamily() + "; font-size: " + fontSize + "pt;";
        ((HTMLDocument)this.outputArea.getDocument()).getStyleSheet().addRule("body { " + fontStyle + " color: #333333; width: 100%; overflow-wrap: break-word; word-wrap: break-word; }");
        
        this.outputArea.setMargin(new Insets(15, 15, 15, 15)); // Add more padding inside the text area
        
        // Apply Burp theme to component
        api.userInterface().applyThemeToComponent(this.outputArea);

        // Top Panel with Buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Skills Button (Multi-select)
        skillsButton = new JButton("Skills: " + getSkillsButtonText());
        skillsButton.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        skillsButton.setMargin(new Insets(10, 20, 10, 20));
        skillsButton.setFocusPainted(false);
        skillsButton.addActionListener(e -> showSkillsPopup());

        // Register listener for skill updates
        SkillManager.addListener(skills -> {
            SwingUtilities.invokeLater(() -> {
                skillsButton.setText("Skills: " + getSkillsButtonText());
            });
        });
        
        analyzeButton = new JButton("分析请求 (Analyze)");
        analyzeButton.setFocusPainted(false);
        analyzeButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        analyzeButton.setMargin(new Insets(10, 20, 10, 20));
        analyzeButton.setBackground(new Color(41, 128, 185));
        analyzeButton.setForeground(Color.WHITE);
        analyzeButton.addActionListener(this::analyze);
        
        stopButton = new JButton("停止 (Stop)");
        stopButton.setFocusPainted(false);
        stopButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        stopButton.setMargin(new Insets(10, 20, 10, 20));
        stopButton.setBackground(new Color(192, 57, 43));
        stopButton.setForeground(Color.WHITE);
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopAnalysis());

        JButton clearButton = new JButton("清空 (Clear)");
        clearButton.setFocusPainted(false);
        clearButton.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        clearButton.setMargin(new Insets(10, 20, 10, 20));
        clearButton.addActionListener(e -> outputArea.setText(""));
        
        topPanel.add(skillsButton);
        topPanel.add(analyzeButton);
        topPanel.add(stopButton);
        topPanel.add(clearButton);
        
        // Chat Input Area (Bottom)
        JPanel inputPanel = new JPanel(new BorderLayout(15, 0));
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        
        JTextField chatField = new JTextField();
        chatField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        chatField.setPreferredSize(new Dimension(0, 50));
        
        JButton sendButton = new JButton("发送 (Send)");
        sendButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        sendButton.setMargin(new Insets(10, 25, 10, 25));
        sendButton.setFocusPainted(false);
        sendButton.setBackground(new Color(39, 174, 96));
        sendButton.setForeground(Color.WHITE);
        
        ActionListener sendAction = e -> {
            String question = chatField.getText().trim();
            if (!question.isEmpty()) {
                askGPT(question);
                chatField.setText("");
            }
        };
        
        sendButton.addActionListener(sendAction);
        chatField.addActionListener(sendAction); // Handle Enter key
        
        inputPanel.add(new JLabel("Follow-up Question: "), BorderLayout.WEST);
        inputPanel.add(chatField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        this.panel.add(topPanel, BorderLayout.NORTH);
        this.panel.add(new JScrollPane(this.outputArea), BorderLayout.CENTER);
        this.panel.add(inputPanel, BorderLayout.SOUTH);
    }

    private void appendHtml(String html) {
        SwingUtilities.invokeLater(() -> {
            try {
                HTMLDocument doc = (HTMLDocument) outputArea.getDocument();
                HTMLEditorKit kit = (HTMLEditorKit) outputArea.getEditorKit();
                kit.insertHTML(doc, doc.getLength(), html, 0, 0, null);
                outputArea.setCaretPosition(doc.getLength());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private String getPlainText() {
        try {
            return outputArea.getDocument().getText(0, outputArea.getDocument().getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }

    private String renderMarkdown(String markdown) {
        if (markdown == null) return "";
        
        // Escape HTML special characters
        String html = markdown.replace("&", "&amp;")
                              .replace("<", "&lt;")
                              .replace(">", "&gt;");

        // Headers (### -> h3, ## -> h2, # -> h1)
        html = html.replaceAll("(?m)^### (.*)$", "<h3>$1</h3>");
        html = html.replaceAll("(?m)^## (.*)$", "<h2>$1</h2>");
        html = html.replaceAll("(?m)^# (.*)$", "<h1>$1</h1>");

        // Bold (**text**)
        html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
        
        // Italic (*text*)
        html = html.replaceAll("\\*(.*?)\\*", "<i>$1</i>");

        // Inline Code (`code`)
        html = html.replaceAll("`([^`]+)`", "<code style='background-color: #f0f0f0; padding: 2px; border-radius: 3px;'>$1</code>");

        // Code Blocks (```code```) - Simplified
        // Note: Real markdown parsing is complex, this is a basic approximation for common outputs
        html = html.replaceAll("```([\\s\\S]*?)```", "<pre style='background-color: #f5f5f5; padding: 10px; border-radius: 5px; overflow-x: auto;'>$1</pre>");

        // Lists (- item)
        html = html.replaceAll("(?m)^- (.*)$", "<li>$1</li>");
        // Wrap adjacent li elements in ul (Basic heuristic)
        // For simplicity in JTextPane, we just leave them as bullets or wrap simply if needed.
        // Actually JTextPane HTML support for lists is okay.
        
        // Newlines to <br>, but try to avoid breaking HTML tags we just added
        // A simple strategy: replace newlines that are NOT inside tags. 
        // But since we just added tags, let's just use a simple replacement for remaining newlines.
        // However, we must be careful not to break the headers which might have newlines after them.
        
        // Let's replace newlines with <br> generally, but headers typically block-level.
        // JTextPane's HTML engine treats headers as blocks.
        
        html = html.replace("\n", "<br>");
        
        // Cleanup extra breaks around headers/pre
        html = html.replace("</h1><br>", "</h1>");
        html = html.replace("</h2><br>", "</h2>");
        html = html.replace("</h3><br>", "</h3>");
        html = html.replace("</pre><br>", "</pre>");
        
        return html;
    }

    private void askGPT(String question) {
        if (isRunning) {
            // Intervention mode
            userInterventions.add(question);
            if (currentProvider != null) {
                currentProvider.injectUserMessage(question);
            }
            // Display intervention in UI
            appendHtml("<div style='margin-top: 10px; margin-bottom: 5px;'><b style='color: #D8000C; font-size: 1.1em;'>👤 You (Intervention):</b> " + question + "</div>");
            return;
        }
        setRunningState(true);

        String currentContent = getPlainText();
        
        appendHtml("<div style='margin-top: 10px; margin-bottom: 5px;'><b style='color: #2980B9; font-size: 1.1em;'>👤 You:</b> " + question + "</div>");
        appendHtml("<div style='margin-bottom: 5px;'><b style='color: #8E44AD; font-size: 1.1em;'>🤖 GPT:</b> <span style='color: #7F8C8D;'>Thinking...</span></div>");
        
        new Thread(() -> {
            try {
                // Construct conversation context from previous output
                String context = currentContent.length() > 5000 ? currentContent.substring(currentContent.length() - 5000) : currentContent;
                
                // Get current request/response details
                String reqInfo = "None";
                String respInfo = "None";
                if (currentRequestResponse != null) {
                    reqInfo = currentRequestResponse.request().toString();
                    if (currentRequestResponse.response() != null) {
                        respInfo = currentRequestResponse.response().toString();
                    }
                }

                // Pass system prompt from current active skills
                String systemPrompt = SkillManager.getCombinedSystemPrompt();

                String userPrompt = "The user is asking a follow-up question.\n\n" +
                                "=== Current HTTP Request ===\n" + reqInfo + "\n" +
                                "=== Current HTTP Response ===\n" + respInfo + "\n" +
                                "============================\n\n" +
                                "=== Previous Conversation Context ===\n" + context + "\n" +
                                "=====================================\n\n" +
                                "User Question: " + question;
                
                currentProvider = new OpenAIProvider(Config.apiKey, Config.apiUrl, Config.model, api.logging(), logMsg -> {
                    appendHtml(logMsg);
                });
                String result = currentProvider.sendRequest(systemPrompt, userPrompt, 2000);
                
                if (isRunning) {
                    SwingUtilities.invokeLater(() -> {
                        // Remove "Thinking..." and append result
                        String rendered = renderMarkdown(result);
                        String html = String.format(
                            "<div style='border: 1px solid #ccc; background-color: #f9f9f9; padding: 10px; margin: 10px 0; border-radius: 5px; font-family: sans-serif;'>" +
                            "<div style='font-weight: bold; color: #333; border-bottom: 1px solid #ddd; padding-bottom: 5px; margin-bottom: 5px;'>📝 Analysis Report</div>" +
                            "<div style='color: #333;'>%s</div>" +
                            "</div>",
                            rendered
                        );
                        appendHtml(html);
                    });
                }
            } catch (Exception ex) {
                appendHtml("<div style='color: red;'>Error: " + ex.getMessage() + "</div>");
            } finally {
                setRunningState(false);
                currentProvider = null;
            }
        }).start();
    }

    private void analyze(ActionEvent e) {
        if (currentRequestResponse == null || isRunning) return;
        setRunningState(true);

        outputArea.setText(""); // Clear previous analysis
        appendHtml("<b>Analyzing...</b>");
        
        new Thread(() -> {
            try {
                String req = currentRequestResponse.request().toString();
                String resp = currentRequestResponse.response() != null ? currentRequestResponse.response().toString() : "";
                
                if (resp.length() > Config.maxChunkSize) {
                    analyzeInChunks(req, resp);
                } else {
                    analyzeSingle(req, resp);
                }
            } catch (Exception ex) {
                if (isRunning) {
                    appendHtml("<div style='color: red;'>Error: " + ex.getMessage() + "</div>");
                }
            } finally {
                setRunningState(false);
                currentProvider = null;
            }
        }).start();
    }

    private void analyzeSingle(String req, String resp) throws Exception {
        String prompt = Config.prompt
            .replace("{REQUEST}", req)
            .replace("{RESPONSE}", resp);
        
        currentProvider = new OpenAIProvider(Config.apiKey, Config.apiUrl, Config.model, api.logging(), logMsg -> {
            appendHtml(logMsg);
        });
        
        // Pass system prompt from current active skills
        String systemPrompt = SkillManager.getCombinedSystemPrompt();
        String result = currentProvider.sendRequest(systemPrompt, prompt, 2000);
        
        if (isRunning) {
            String rendered = renderMarkdown(result);
            String html = String.format(
                "<div style='border: 1px solid #ccc; background-color: #f9f9f9; padding: 10px; margin: 10px 0; border-radius: 5px; font-family: sans-serif;'>" +
                "<div style='font-weight: bold; color: #333; border-bottom: 1px solid #ddd; padding-bottom: 5px; margin-bottom: 5px;'>📝 Analysis Report</div>" +
                "<div style='color: #333;'>%s</div>" +
                "</div>",
                rendered
            );
            appendHtml(html);
        }
    }

    private void analyzeInChunks(String req, String resp) {
        List<String> chunks = splitIntoChunks(resp, Config.maxChunkSize);
        int totalChunks = chunks.size();
        
        appendHtml("<div>Response is too large (" + resp.length() + " chars). Splitting into " + totalChunks + " chunks...</div><br>");
        
        currentProvider = new OpenAIProvider(Config.apiKey, Config.apiUrl, Config.model, api.logging(), logMsg -> {
            appendHtml(logMsg);
        });
        
        String previousContext = "None (Start of analysis)";
        
        for (int i = 0; i < totalChunks; i++) {
            if (!isRunning) break;

            String chunk = chunks.get(i);
            String partInfo = "Part " + (i + 1) + "/" + totalChunks;
            
            // Construct context-aware prompt
            String contextPrompt = "You are analyzing a large file in chunks. This is " + partInfo + ".\n" +
                                   "=== Context from Previous Analysis (Summary) ===\n" + previousContext + "\n" +
                                   "==============================================\n\n";
                                   
            // Check for interventions in chunk loop
            while (!userInterventions.isEmpty()) {
                String intervention = userInterventions.poll();
                contextPrompt += "[User Intervention]: " + intervention + "\n\n";
                appendHtml("<div style='color: #D8000C;'>[Intervention applied to next chunk]</div>");
            }

            contextPrompt += "Analyze the following chunk. If you find new vulnerabilities, list them. " +
                                   "Also, please briefly summarize the key findings so far to be passed to the next chunk analysis.\n\n";

            String prompt = contextPrompt + Config.prompt
                .replace("{REQUEST}", req)
                .replace("{RESPONSE}", "--- " + partInfo + " ---\n" + chunk + "\n--- End of " + partInfo + " ---");
            
            try {
                // Pass system prompt from current active skills
                String systemPrompt = SkillManager.getCombinedSystemPrompt();
                String result = currentProvider.sendRequest(systemPrompt, prompt, 2000);
                
                if (!isRunning) break;

                // Update previous context for next iteration (Take last 1500 chars to avoid token limit issues)
                if (result.length() > 1500) {
                    previousContext = "..." + result.substring(result.length() - 1500);
                } else {
                    previousContext = result;
                }

                String rendered = renderMarkdown(result);
                String html = String.format(
                    "<div style='border: 1px solid #ccc; background-color: #f9f9f9; padding: 10px; margin: 10px 0; border-radius: 5px; font-family: sans-serif;'>" +
                    "<div style='font-weight: bold; color: #333; border-bottom: 1px solid #ddd; padding-bottom: 5px; margin-bottom: 5px;'>📝 Analysis for %s</div>" +
                    "<div style='color: #333;'>%s</div>" +
                    "</div>",
                    partInfo, rendered
                );
                appendHtml(html);
            } catch (Exception ex) {
                if (isRunning) {
                    appendHtml("<div style='color: red;'>Error analyzing " + partInfo + ": " + ex.getMessage() + "</div>");
                }
            }
        }
        
        if (isRunning) {
            appendHtml("<br><b>=== Analysis Complete ===</b>");
        }
    }

    private void stopAnalysis() {
        if (isRunning) {
            isRunning = false;
            if (currentProvider != null) {
                currentProvider.cancel();
            }
            appendHtml("<br><b style='color: red;'>[Analysis Stopped by User]</b>");
            setRunningState(false);
        }
    }

    private void setRunningState(boolean running) {
        isRunning = running;
        SwingUtilities.invokeLater(() -> {
            if (analyzeButton != null) analyzeButton.setEnabled(!running);
            if (stopButton != null) stopButton.setEnabled(running);
            // Send button remains enabled to allow interventions
        });
    }

    private List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int overlap = 500; // Overlap to prevent cutting context
        
        for (int i = 0; i < length; i += (chunkSize - overlap)) {
            int end = Math.min(length, i + chunkSize);
            chunks.add(text.substring(i, end));
            if (end == length) break;
        }
        return chunks;
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        this.currentRequestResponse = requestResponse;
    }

    @Override
    public HttpRequest getRequest() {
        return currentRequestResponse.request();
    }

    @Override
    public HttpResponse getResponse() {
        return currentRequestResponse.response();
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        return true;
    }

    @Override
    public String caption() {
        return "BurpAgent";
    }

    @Override
    public Component uiComponent() {
        return panel;
    }

    private String getSkillsButtonText() {
        List<Skill> active = SkillManager.getActiveSkills();
        if (active.isEmpty()) return "None";
        if (active.size() == 1) return active.get(0).getName();
        return active.size() + " Selected";
    }

    private void showSkillsPopup() {
        JPopupMenu popup = new JPopupMenu();
        List<Skill> allSkills = SkillManager.getSkills();
        List<Skill> activeSkills = SkillManager.getActiveSkills();

        // Add a label hint
        JMenuItem hintItem = new JMenuItem("<html><i>Order is based on selection sequence</i></html>");
        hintItem.setEnabled(false);
        popup.add(hintItem);
        popup.addSeparator();

        for (Skill skill : allSkills) {
            String label = skill.getName();
            int index = activeSkills.indexOf(skill);
            if (index >= 0) {
                label = "[" + (index + 1) + "] " + label;
            }
            
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(label);
            item.setSelected(index >= 0);
            item.addActionListener(e -> {
                List<Skill> newActive = new ArrayList<>(SkillManager.getActiveSkills());
                if (item.isSelected()) {
                    if (!newActive.contains(skill)) {
                        newActive.add(skill);
                    }
                } else {
                    newActive.remove(skill);
                }
                SkillManager.setActiveSkills(newActive);
                skillsButton.setText("Skills: " + getSkillsButtonText());
            });
            popup.add(item);
        }
        
        popup.show(skillsButton, 0, skillsButton.getHeight());
    }

    @Override
    public Selection selectedData() {
        return null;
    }

    @Override
    public boolean isModified() {
        return false;
    }
}
