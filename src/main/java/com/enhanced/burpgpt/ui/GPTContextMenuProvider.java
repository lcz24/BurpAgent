package com.enhanced.burpgpt.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.enhanced.burpgpt.Config;
import com.enhanced.burpgpt.api.OpenAIProvider;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GPTContextMenuProvider implements ContextMenuItemsProvider {
    private final MontoyaApi api;

    public GPTContextMenuProvider(MontoyaApi api) {
        this.api = api;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<Component> menuItems = new ArrayList<>();
        
        JMenuItem analyzeItem = new JMenuItem("Send to BurpAgent");
        analyzeItem.addActionListener(e -> {
            List<HttpRequestResponse> selectedItems = event.selectedRequestResponses();
            if (!selectedItems.isEmpty()) {
                analyze(selectedItems.get(0));
            }
        });
        
        menuItems.add(analyzeItem);
        return menuItems;
    }

    private void analyze(HttpRequestResponse requestResponse) {
        // Show a dialog immediately
        JDialog dialog = new JDialog();
        dialog.setTitle("BurpAgent Analysis");
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(null); // Center
        
        JTextArea outputArea = new JTextArea("Analyzing...");
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        
        dialog.add(new JScrollPane(outputArea));
        dialog.setVisible(true);

        new Thread(() -> {
            try {
                String req = requestResponse.request().toString();
                String resp = requestResponse.response() != null ? requestResponse.response().toString() : "";
                
                String prompt = Config.prompt
                    .replace("{REQUEST}", req)
                    .replace("{RESPONSE}", resp);
                
                OpenAIProvider provider = new OpenAIProvider(Config.apiKey, Config.apiUrl, Config.model, api.logging());
                String result = provider.sendRequest(prompt, 2000);
                
                SwingUtilities.invokeLater(() -> outputArea.setText(result));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> outputArea.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }
}
