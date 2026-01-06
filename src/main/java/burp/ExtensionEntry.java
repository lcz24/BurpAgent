package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.enhanced.burpgpt.Config;
import com.enhanced.burpgpt.ui.GPTContextMenuProvider;
import com.enhanced.burpgpt.ui.GPTEditorTabProvider;
import com.enhanced.burpgpt.ui.SettingsPanel;
import com.enhanced.burpgpt.mcp.MCPManager;

import com.enhanced.burpgpt.api.ToolExecutor;

public class ExtensionEntry implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("BurpAgent");
        
        // Initialize ToolExecutor
        ToolExecutor.initialize(api);
        
        // Load Configuration
        Config.load();
        
        // Initialize MCP Manager
        if (Config.mcpEnabled) {
            MCPManager.reloadAsync(null);
        }
        
        // Register Editor Tab (The "Custom actions" equivalent requested)
        GPTEditorTabProvider provider = new GPTEditorTabProvider(api);
        api.userInterface().registerHttpRequestEditorProvider(provider);
        api.userInterface().registerHttpResponseEditorProvider(provider);
        
        // Register Context Menu
        api.userInterface().registerContextMenuItemsProvider(new GPTContextMenuProvider(api));
        
        // Register Settings Tab
        api.userInterface().registerSuiteTab("BurpAgent Settings", new SettingsPanel());
        
        api.logging().logToOutput("BurpAgent loaded successfully.");
    }
}
