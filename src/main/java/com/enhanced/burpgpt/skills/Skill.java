package com.enhanced.burpgpt.skills;

public class Skill {
    private String id;
    private String name;
    private String description;
    private String systemPrompt;

    public Skill() {}

    public Skill(String id, String name, String description, String systemPrompt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSystemPrompt() { return systemPrompt; }
    
    @Override
    public String toString() {
        return name; // For JComboBox display
    }
}
