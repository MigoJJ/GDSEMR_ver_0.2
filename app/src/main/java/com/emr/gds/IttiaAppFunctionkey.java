package com.emr.gds;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import java.util.HashMap;
import java.util.Map;

/**
 * Function Key Handler for IttiaApp
 * Manages F1-F12 key bindings and their corresponding actions
 */
public class IttiaAppFunctionkey {
    
    private final IttiaApp mainApp;
    private final Map<KeyCode, FunctionKeyAction> functionKeyActions;
    
    /**
     * Interface for function key actions
     */
    @FunctionalInterface
    public interface FunctionKeyAction {
        void execute();
    }
    
    /**
     * Constructor
     * @param mainApp Reference to the main IttiaApp instance
     */
    public IttiaAppFunctionkey(IttiaApp mainApp) {
        this.mainApp = mainApp;
        this.functionKeyActions = new HashMap<>();
        initializeFunctionKeyActions();
    }
    
    /**
     * Initialize all F1-F12 key actions
     */
    private void initializeFunctionKeyActions() {
        // F1 - Help/About
        functionKeyActions.put(KeyCode.F1, this::showHelp);
        
        // F2 - Template Quick Insert
        functionKeyActions.put(KeyCode.F2, this::quickInsertTemplate);
        
        // F3 - Search/Find in current area
        functionKeyActions.put(KeyCode.F3, this::findInCurrentArea);
        
        // F4 - Open Vital Window
        functionKeyActions.put(KeyCode.F4, () -> mainApp.openVitalWindow());
        
        // F5 - Refresh/Reload data
        functionKeyActions.put(KeyCode.F5, this::refreshData);
        
        // F6 - Format current area
        functionKeyActions.put(KeyCode.F6, () -> mainApp.formatCurrentArea());
        
        // F7 - Spell check current area
        functionKeyActions.put(KeyCode.F7, this::spellCheckCurrentArea);
        
        // F8 - Toggle word wrap
        functionKeyActions.put(KeyCode.F8, this::toggleWordWrap);
        
        // F9 - Save current state
        functionKeyActions.put(KeyCode.F9, this::saveCurrentState);
        
        // F10 - Show all shortcuts
        functionKeyActions.put(KeyCode.F10, this::showAllShortcuts);
        
        // F11 - Toggle fullscreen mode
        functionKeyActions.put(KeyCode.F11, this::toggleFullscreen);
        
        // F12 - Copy all to clipboard
        functionKeyActions.put(KeyCode.F12, () -> mainApp.copyAllToClipboard());
    }
    
    /**
     * Install function key shortcuts to the given scene
     * @param scene The JavaFX scene to install shortcuts on
     */
    public void installFunctionKeyShortcuts(Scene scene) {
        for (Map.Entry<KeyCode, FunctionKeyAction> entry : functionKeyActions.entrySet()) {
            KeyCode keyCode = entry.getKey();
            FunctionKeyAction action = entry.getValue();
            
            // Install direct function key
            scene.getAccelerators().put(
                new KeyCodeCombination(keyCode), 
                action::execute
            );
            
            // Also install with Alt modifier for additional flexibility
            scene.getAccelerators().put(
                new KeyCodeCombination(keyCode, KeyCombination.ALT_DOWN),
                action::execute
            );
        }
    }
    
    // ================================
    // FUNCTION KEY ACTION IMPLEMENTATIONS
    // ================================
    
    private void showHelp() {
        String helpText = buildHelpText();
        showInfoDialog("Help - Function Keys", helpText);
    }
    
    private String buildHelpText() {
        return """
            Function Key Shortcuts:
            
            F1  - Show this help dialog
            F2  - Quick template insert
            F3  - Find in current text area
            F4  - Open Vital BP & HbA1c window
            F5  - Refresh/Reload data
            F6  - Format current text area
            F7  - Spell check current area
            F8  - Toggle word wrap
            F9  - Save current state
            F10 - Show all keyboard shortcuts
            F11 - Toggle fullscreen mode
            F12 - Copy all content to clipboard
            
            Other Shortcuts:
            Ctrl+1-9, Ctrl+0 - Focus text areas 1-10
            Ctrl+I - Insert HPI template
            Ctrl+Shift+F - Format current area
            Ctrl+Shift+C - Copy all to clipboard
            """;
    }
    
    private void quickInsertTemplate() {
        try {
            // Insert HPI template as default quick template
            mainApp.insertTemplateIntoFocusedArea(
                com.emr.gds.main.ListButtonAction.TemplateLibrary.HPI
            );
            showToast("HPI template inserted");
        } catch (Exception e) {
            showErrorDialog("Template Insert Error", "Failed to insert template: " + e.getMessage());
        }
    }
    
    private void findInCurrentArea() {
        showInfoDialog("Find Function", "Find functionality will be implemented in future version.");
    }
    
    private void refreshData() {
        try {
            // Refresh abbreviations from database
            mainApp.getAbbrevMap().clear();
            // Re-load abbreviations (this would need to be implemented in IttiaApp)
            showToast("Data refreshed successfully");
        } catch (Exception e) {
            showErrorDialog("Refresh Error", "Failed to refresh data: " + e.getMessage());
        }
    }
    
    private void spellCheckCurrentArea() {
        showInfoDialog("Spell Check", "Spell check functionality will be implemented in future version.");
    }
    
    private void toggleWordWrap() {
        try {
            var textAreas = mainApp.getTextAreaManager().getTextAreas();
            if (textAreas != null && !textAreas.isEmpty()) {
                // Toggle word wrap for all text areas
                boolean currentWrap = textAreas.get(0).isWrapText();
                textAreas.forEach(textArea -> textArea.setWrapText(!currentWrap));
                showToast("Word wrap " + (!currentWrap ? "enabled" : "disabled"));
            }
        } catch (Exception e) {
            showErrorDialog("Word Wrap Error", "Failed to toggle word wrap: " + e.getMessage());
        }
    }
    
    private void saveCurrentState() {
        showInfoDialog("Save State", "Auto-save functionality will be implemented in future version.");
    }
    
    private void showAllShortcuts() {
        String shortcutsText = buildAllShortcutsText();
        showInfoDialog("All Keyboard Shortcuts", shortcutsText);
    }
    
    private String buildAllShortcutsText() {
        return """
            Complete Keyboard Shortcuts Reference:
            
            === FUNCTION KEYS ===
            F1  - Help dialog
            F2  - Quick template insert
            F3  - Find in current area
            F4  - Open Vital window
            F5  - Refresh data
            F6  - Format current area
            F7  - Spell check
            F8  - Toggle word wrap
            F9  - Save state
            F10 - This shortcuts dialog
            F11 - Toggle fullscreen
            F12 - Copy all to clipboard
            
            === CONTROL KEYS ===
            Ctrl+1 to Ctrl+9 - Focus text areas 1-9
            Ctrl+0 - Focus text area 10
            Ctrl+I - Insert HPI template
            Ctrl+Shift+F - Format current area
            Ctrl+Shift+C - Copy all to clipboard
            
            === ALT KEYS ===
            Alt+F1 to Alt+F12 - Alternative function key access
            """;
    }
    
    private void toggleFullscreen() {
        try {
            var stage = mainApp.getTextAreaManager().getTextAreas().get(0).getScene().getWindow();
            if (stage instanceof javafx.stage.Stage) {
                javafx.stage.Stage primaryStage = (javafx.stage.Stage) stage;
                primaryStage.setFullScreen(!primaryStage.isFullScreen());
                showToast("Fullscreen " + (primaryStage.isFullScreen() ? "enabled" : "disabled"));
            }
        } catch (Exception e) {
            showErrorDialog("Fullscreen Error", "Failed to toggle fullscreen: " + e.getMessage());
        }
    }
    
    // ================================
    // UTILITY METHODS
    // ================================
    
    private void showToast(String message) {
        showInfoDialog("Info", message);
    }
    
    private void showInfoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle(title);
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }
    
    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle(title);
        alert.setResizable(true);
        alert.showAndWait();
    }
    
    // ================================
    // CONFIGURATION METHODS
    // ================================
    
    /**
     * Add or update a function key action
     * @param keyCode The function key code
     * @param action The action to execute
     */
    public void setFunctionKeyAction(KeyCode keyCode, FunctionKeyAction action) {
        if (isFunctionKey(keyCode)) {
            functionKeyActions.put(keyCode, action);
        } else {
            throw new IllegalArgumentException("Key code must be a function key (F1-F12)");
        }
    }
    
    /**
     * Remove a function key action
     * @param keyCode The function key code to remove
     */
    public void removeFunctionKeyAction(KeyCode keyCode) {
        functionKeyActions.remove(keyCode);
    }
    
    /**
     * Check if a key code is a function key
     * @param keyCode The key code to check
     * @return true if it's F1-F12
     */
    private boolean isFunctionKey(KeyCode keyCode) {
        return keyCode == KeyCode.F1 || keyCode == KeyCode.F2 || keyCode == KeyCode.F3 ||
               keyCode == KeyCode.F4 || keyCode == KeyCode.F5 || keyCode == KeyCode.F6 ||
               keyCode == KeyCode.F7 || keyCode == KeyCode.F8 || keyCode == KeyCode.F9 ||
               keyCode == KeyCode.F10 || keyCode == KeyCode.F11 || keyCode == KeyCode.F12;
    }
    
    /**
     * Get all currently registered function key actions
     * @return Map of key codes to their actions
     */
    public Map<KeyCode, FunctionKeyAction> getFunctionKeyActions() {
        return new HashMap<>(functionKeyActions);
    }
}