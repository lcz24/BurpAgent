package com.enhanced.burpgpt.ui;

import com.enhanced.burpgpt.Config;
import com.enhanced.burpgpt.api.ToolManager;
import com.enhanced.burpgpt.mcp.MCPManager;
import com.enhanced.burpgpt.skills.Skill;
import com.enhanced.burpgpt.skills.SkillManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SettingsPanel extends JPanel {
    private JTabbedPane tabbedPane;

    public SettingsPanel() {
        setLayout(new BorderLayout());
        tabbedPane = new JTabbedPane();
        
        tabbedPane.addTab("General Settings", createGeneralSettingsPanel());
        tabbedPane.addTab("Agent Skills", createSkillsPanel());
        tabbedPane.addTab("Custom Tools", createToolsPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createGeneralSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // API Key
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.1;
        panel.add(new JLabel("API Key:"), gbc);
        
        JTextField apiKeyField = new JTextField(Config.apiKey);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        panel.add(apiKeyField, gbc);
        
        // API URL
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.1;
        panel.add(new JLabel("API URL:"), gbc);
        
        JTextField apiUrlField = new JTextField(Config.apiUrl);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        panel.add(apiUrlField, gbc);
        
        // Model
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.1;
        panel.add(new JLabel("Model:"), gbc);
        
        JTextField modelField = new JTextField(Config.model);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0;
        panel.add(modelField, gbc);

        // Max Chunk Size
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.1;
        panel.add(new JLabel("Max Chunk Size (chars):"), gbc);
        
        JTextField chunkField = new JTextField(String.valueOf(Config.maxChunkSize));
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0;
        panel.add(chunkField, gbc);

        // Tools Path
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.1;
        panel.add(new JLabel("Tools Directory:"), gbc);
        
        JPanel filePanel = new JPanel(new BorderLayout());
        JTextField toolsPathField = new JTextField(Config.toolsPath);
        JButton browseButton = new JButton("...");
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                toolsPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        
        filePanel.add(toolsPathField, BorderLayout.CENTER);
        filePanel.add(browseButton, BorderLayout.EAST);
        
        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 1.0;
        panel.add(filePanel, gbc);

        // Command Blacklist
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0.1;
        panel.add(new JLabel("Command Blacklist (comma separated):"), gbc);
        
        JTextField blacklistField = new JTextField(Config.commandBlacklist);
        gbc.gridx = 1; gbc.gridy = 5; gbc.weightx = 1.0;
        panel.add(blacklistField, gbc);
        
        // MCP Enabled
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0.1; gbc.gridwidth = 2;
        JCheckBox mcpEnabledBox = new JCheckBox("Enable MCP Integration");
        mcpEnabledBox.setSelected(Config.mcpEnabled);
        panel.add(mcpEnabledBox, gbc);

        // MCP Servers
        gbc.gridx = 0; gbc.gridy = 7; gbc.weightx = 1.0; gbc.gridwidth = 2;
        panel.add(new JLabel("MCP Servers (Name|Command OR JSON Config):"), gbc);

        StringBuilder mcpSb = new StringBuilder();
        if (Config.mcpServers != null) {
            for (Config.MCPServerConfig server : Config.mcpServers) {
                mcpSb.append(server.name).append("|").append(server.command).append("\n");
            }
        }
        JTextArea mcpArea = new JTextArea(mcpSb.toString());
        mcpArea.setRows(6);
        JScrollPane mcpScroll = new JScrollPane(mcpArea);
        gbc.gridx = 0; gbc.gridy = 8; gbc.weightx = 1.0; gbc.weighty = 0.2; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(mcpScroll, gbc);
        
        // MCP Status Panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createTitledBorder("MCP Status"));
        gbc.gridx = 0; gbc.gridy = 9; gbc.weightx = 1.0; gbc.weighty = 0; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(statusPanel, gbc);
        
        // Update Status Function
        Runnable updateStatus = () -> {
            statusPanel.removeAll();
            if (!Config.mcpEnabled) {
                statusPanel.add(new JLabel("MCP Disabled"));
            } else {
                Map<String, String> statuses = MCPManager.getServerStatus();
                if (statuses.isEmpty() && (Config.mcpServers == null || Config.mcpServers.isEmpty())) {
                    statusPanel.add(new JLabel("No Servers Configured"));
                } else {
                    for (Map.Entry<String, String> entry : statuses.entrySet()) {
                        String name = entry.getKey();
                        String status = entry.getValue();
                        
                        Color color;
                        if (status.equals("CONNECTED")) {
                            color = new Color(0, 180, 0); // Green
                        } else if (status.equals("CONNECTING")) {
                            color = new Color(255, 180, 0); // Orange/Yellow
                        } else {
                            color = new Color(220, 0, 0); // Red
                        }

                        JLabel label = new JLabel(name + ": " + status);
                        label.setIcon(new StatusIcon(color));
                        label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 10));
                        statusPanel.add(label);
                    }
                }
            }
            statusPanel.revalidate();
            statusPanel.repaint();
        };
        
        MCPManager.addStatusListener(v -> SwingUtilities.invokeLater(updateStatus));
        updateStatus.run(); // Initial state

        // Prompt Label
        gbc.gridx = 0; gbc.gridy = 10; gbc.weightx = 1.0; gbc.gridwidth = 2;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("Prompt Template (Use {REQUEST} and {RESPONSE} as placeholders):"), gbc);
        
        // Prompt Area
        JTextArea promptArea = new JTextArea(Config.prompt);
        promptArea.setRows(10);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(promptArea);
        
        gbc.gridx = 0; gbc.gridy = 11; gbc.weightx = 1.0; gbc.weighty = 0.8; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane, gbc);
        
        // Button Panel
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        gbc.gridx = 0; gbc.gridy = 12; gbc.weightx = 1.0; gbc.weighty = 0; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        JButton saveBtn = new JButton("Save Settings");
        JButton reloadMcpBtn = new JButton("Restart MCP Servers");
        
        saveBtn.addActionListener(e -> {
            Config.apiKey = apiKeyField.getText();
            Config.apiUrl = apiUrlField.getText();
            Config.model = modelField.getText();
            try {
                Config.maxChunkSize = Integer.parseInt(chunkField.getText());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid Max Chunk Size, using default 10000");
                Config.maxChunkSize = 10000;
                chunkField.setText("10000");
            }
            Config.toolsPath = toolsPathField.getText();
            Config.commandBlacklist = blacklistField.getText();
            Config.prompt = promptArea.getText();
            Config.mcpEnabled = mcpEnabledBox.isSelected();
            
            // Save MCP Servers
            Config.mcpServers = new ArrayList<>();
            String mcpText = mcpArea.getText().trim();
            
            if (mcpText.startsWith("{")) {
                // Try parsing as JSON (Claude/VSCode style)
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(mcpText);
                    JsonNode servers = root.get("mcpServers");
                    if (servers != null && servers.isObject()) {
                        Iterator<Map.Entry<String, JsonNode>> fields = servers.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            String name = entry.getKey();
                            JsonNode config = entry.getValue();
                            
                            String command = "";
                            String transport = "stdio";

                            if (config.has("url")) {
                                command = config.get("url").asText();
                                transport = "sse";
                            } else if (config.has("command")) {
                                command = config.get("command").asText();
                                StringBuilder fullCmd = new StringBuilder(command);
                                if (config.has("args") && config.get("args").isArray()) {
                                    for (JsonNode arg : config.get("args")) {
                                        fullCmd.append(" ").append(arg.asText());
                                    }
                                }
                                command = fullCmd.toString();
                            }
                            
                            if (config.has("transport")) {
                                transport = config.get("transport").asText();
                            }
                            
                            if (!command.isEmpty()) {
                                Config.mcpServers.add(new Config.MCPServerConfig(name, command, transport));
                            }
                        }
                    } else {
                         JOptionPane.showMessageDialog(this, "Invalid JSON format: missing 'mcpServers' object.");
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error parsing MCP JSON: " + ex.getMessage());
                }
            } else {
                // Parse as Line-based (Legacy)
                String[] lines = mcpText.split("\n");
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split("\\|", 2);
                    if (parts.length == 2) {
                        Config.mcpServers.add(new Config.MCPServerConfig(parts[0].trim(), parts[1].trim()));
                    }
                }
            }
            
            Config.save();
            JOptionPane.showMessageDialog(this, "Settings Saved! (MCP requires manual restart if changed)");
        });
        
        reloadMcpBtn.addActionListener(e -> {
            Config.mcpEnabled = mcpEnabledBox.isSelected();
            reloadMcpBtn.setEnabled(false);
            MCPManager.reloadAsync(() -> SwingUtilities.invokeLater(() -> {
                reloadMcpBtn.setEnabled(true);
                JOptionPane.showMessageDialog(this, "MCP Servers Reloaded!");
            }));
        });
        
        btnPanel.add(reloadMcpBtn);
        btnPanel.add(saveBtn);
        panel.add(btnPanel, gbc);
        
        return panel;
    }

    private static class StatusIcon implements Icon {
        private final Color color;
        private static final int SIZE = 12;

        public StatusIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            g2d.fillOval(x, y + 2, SIZE, SIZE);
            g2d.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE + 4;
        }

        @Override
        public int getIconHeight() {
            return SIZE + 4;
        }
    }

    private JPanel createSkillsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Model for the table
        String[] columnNames = {"ID", "Name", "Description"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        JTable table = new JTable(model);
        refreshSkillsTable(model);
        
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addBtn = new JButton("Add Skill");
        JButton editBtn = new JButton("Edit Skill");
        JButton deleteBtn = new JButton("Delete Skill");
        
        addBtn.addActionListener(e -> showSkillDialog(null, model));
        
        editBtn.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                String id = (String) model.getValueAt(selectedRow, 0);
                Skill skill = findSkillById(id);
                if (skill != null) {
                    showSkillDialog(skill, model);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a skill to edit.");
            }
        });
        
        deleteBtn.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                String id = (String) model.getValueAt(selectedRow, 0);
                
                boolean isCustom = Config.customSkills.stream().anyMatch(s -> s.getId().equals(id));
                if (!isCustom) {
                    JOptionPane.showMessageDialog(this, "Cannot delete default system skills.");
                    return;
                }
                
                int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this skill?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    Config.customSkills.removeIf(s -> s.getId().equals(id));
                    Config.save();
                    SkillManager.reload();
                    refreshSkillsTable(model);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a skill to delete.");
            }
        });
        
        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createToolsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Model for the table
        String[] columnNames = {"Name", "Type", "Description"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        JTable table = new JTable(model);
        refreshToolsTable(model);
        
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addBtn = new JButton("Add Tool");
        JButton editBtn = new JButton("Edit Tool");
        JButton deleteBtn = new JButton("Delete Tool");
        JButton refreshBtn = new JButton("Refresh");
        
        addBtn.addActionListener(e -> showToolDialog(null, model));
        
        editBtn.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                String name = (String) model.getValueAt(selectedRow, 0);
                ToolManager.ToolDefinition tool = findToolByName(name);
                if (tool != null) {
                    showToolDialog(tool, model);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a tool to edit.");
            }
        });
        
        deleteBtn.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                String name = (String) model.getValueAt(selectedRow, 0);
                int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete tool: " + name + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    try {
                        ToolManager.deleteTool(name);
                        refreshToolsTable(model);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "Error deleting tool: " + ex.getMessage());
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a tool to delete.");
            }
        });
        
        refreshBtn.addActionListener(e -> refreshToolsTable(model));
        
        buttonPanel.add(refreshBtn);
        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }

    private void refreshToolsTable(DefaultTableModel model) {
        model.setRowCount(0);
        List<ToolManager.ToolDefinition> tools = ToolManager.loadTools();
        for (ToolManager.ToolDefinition tool : tools) {
            model.addRow(new Object[]{tool.name, tool.scriptType, tool.description});
        }
    }
    
    private ToolManager.ToolDefinition findToolByName(String name) {
        List<ToolManager.ToolDefinition> tools = ToolManager.loadTools();
        return tools.stream().filter(t -> t.name.equals(name)).findFirst().orElse(null);
    }
    
    private void showToolDialog(ToolManager.ToolDefinition existingTool, DefaultTableModel model) {
        if (Config.toolsPath == null || Config.toolsPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please configure Tools Directory in General Settings first.");
            return;
        }

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), existingTool == null ? "Add Tool" : "Edit Tool", true);
        dialog.setLayout(new BorderLayout());
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Fields
        JTextField nameField = new JTextField(existingTool == null ? "" : existingTool.name);
        nameField.setEditable(existingTool == null);
        
        JTextField descField = new JTextField(existingTool == null ? "" : existingTool.description);
        
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Python", "Bash", "Batch"});
        if (existingTool != null) typeCombo.setSelectedItem(existingTool.scriptType);
        
        JTextArea paramsArea = new JTextArea(existingTool == null ? "{\"type\": \"object\", \"properties\": {\"arg1\": {\"type\": \"string\"}}, \"required\": [\"arg1\"]}" : existingTool.parameters);
        paramsArea.setRows(5);
        paramsArea.setLineWrap(true);
        
        JTextArea scriptArea = new JTextArea(existingTool == null ? "# Write your script here..." : existingTool.scriptContent);
        scriptArea.setRows(15);
        scriptArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        gbc.gridx = 0; gbc.gridy = 0; addLabel(formPanel, "Name (e.g. my_tool):", gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; formPanel.add(nameField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; addLabel(formPanel, "Description:", gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0; formPanel.add(descField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0; addLabel(formPanel, "Script Type:", gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0; formPanel.add(typeCombo, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0; gbc.gridwidth = 2; addLabel(formPanel, "Parameters (JSON Schema):", gbc);
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 1.0; gbc.weighty = 0.3; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(new JScrollPane(paramsArea), gbc);
        
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0; gbc.weighty = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        addLabel(formPanel, "Script Content:", gbc);
        
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 1.0; gbc.weighty = 0.7; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(new JScrollPane(scriptArea), gbc);
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save");
        JButton cancelBtn = new JButton("Cancel");
        
        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Tool Name is required.");
                return;
            }
            
            ToolManager.ToolDefinition tool = new ToolManager.ToolDefinition();
            tool.name = name;
            tool.description = descField.getText().trim();
            tool.scriptType = (String) typeCombo.getSelectedItem();
            tool.parameters = paramsArea.getText().trim();
            tool.scriptContent = scriptArea.getText();
            
            try {
                ToolManager.saveTool(tool);
                refreshToolsTable(model);
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error saving tool: " + ex.getMessage());
            }
        });
        
        cancelBtn.addActionListener(e -> dialog.dispose());
        
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setSize(800, 700);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void refreshSkillsTable(DefaultTableModel model) {
        model.setRowCount(0);
        for (Skill skill : SkillManager.getSkills()) {
            model.addRow(new Object[]{skill.getId(), skill.getName(), skill.getDescription()});
        }
    }
    
    private Skill findSkillById(String id) {
        return SkillManager.getSkills().stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
    }
    
    private void showSkillDialog(Skill existingSkill, DefaultTableModel model) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), existingSkill == null ? "Add Skill" : "Edit Skill", true);
        dialog.setLayout(new BorderLayout());
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Fields
        JTextField idField = new JTextField(existingSkill == null ? UUID.randomUUID().toString().substring(0, 8) : existingSkill.getId());
        idField.setEditable(existingSkill == null);
        
        JTextField nameField = new JTextField(existingSkill == null ? "" : existingSkill.getName());
        JTextField descField = new JTextField(existingSkill == null ? "" : existingSkill.getDescription());
        JTextArea promptArea = new JTextArea(existingSkill == null ? "" : existingSkill.getSystemPrompt());
        promptArea.setRows(10);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        
        gbc.gridx = 0; gbc.gridy = 0; addLabel(formPanel, "ID:", gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; formPanel.add(idField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; addLabel(formPanel, "Name:", gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0; formPanel.add(nameField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0; addLabel(formPanel, "Description:", gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0; formPanel.add(descField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0; gbc.gridwidth = 2; addLabel(formPanel, "System Prompt:", gbc);
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 1.0; gbc.weighty = 1.0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(new JScrollPane(promptArea), gbc);
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save");
        JButton cancelBtn = new JButton("Cancel");
        
        saveBtn.addActionListener(e -> {
            String id = idField.getText().trim();
            String name = nameField.getText().trim();
            String desc = descField.getText().trim();
            String prompt = promptArea.getText().trim();
            
            if (id.isEmpty() || name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "ID and Name are required.");
                return;
            }
            
            Skill newSkill = new Skill(id, name, desc, prompt);
            
            if (existingSkill != null) {
                // Editing
                 boolean isCustom = Config.customSkills.stream().anyMatch(s -> s.getId().equals(existingSkill.getId()));
                 if (!isCustom) {
                     JOptionPane.showMessageDialog(dialog, "Cannot edit default system skills. Please create a new skill.");
                     return;
                 } else {
                     for (int i = 0; i < Config.customSkills.size(); i++) {
                         if (Config.customSkills.get(i).getId().equals(existingSkill.getId())) {
                             Config.customSkills.set(i, newSkill);
                             break;
                         }
                     }
                 }
            } else {
                Config.customSkills.add(newSkill);
            }
            
            Config.save();
            SkillManager.reload();
            refreshSkillsTable(model);
            dialog.dispose();
        });
        
        cancelBtn.addActionListener(e -> dialog.dispose());
        
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    
    private void addLabel(JPanel p, String text, GridBagConstraints gbc) {
        p.add(new JLabel(text), gbc);
    }
}
