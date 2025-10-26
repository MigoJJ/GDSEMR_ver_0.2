package com.emr.gds.main;

import com.emr.gds.IttiaApp;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages F1-F12 function key bindings and their corresponding actions for the main application.
 * This class centralizes all function key handling logic.
 */
public class IAMFunctionkey {

    private final IttiaApp mainApp;
    private final Map<KeyCode, FunctionKeyAction> functionKeyActions = new HashMap<>();

    /**
     * A functional interface for defining an action to be executed by a function key.
     */
    @FunctionalInterface
    public interface FunctionKeyAction {
        void execute();
    }

    /**
     * Constructor for the function key handler.
     * @param mainApp A reference to the main IttiaApp instance.
     */
    public IAMFunctionkey(IttiaApp mainApp) {
        this.mainApp = mainApp;
        initializeFunctionKeyActions();
    }

    /**
     * Initializes the default actions for all F1-F12 keys.
     */
    private void initializeFunctionKeyActions() {
        functionKeyActions.put(KeyCode.F1, this::showHelp);
        functionKeyActions.put(KeyCode.F2, this::quickInsertTemplate);
        functionKeyActions.put(KeyCode.F3, this::findInCurrentArea);
        functionKeyActions.put(KeyCode.F4, mainApp::openVitalWindow);
        functionKeyActions.put(KeyCode.F5, this::refreshData);
        functionKeyActions.put(KeyCode.F6, mainApp::formatCurrentArea);
        functionKeyActions.put(KeyCode.F7, this::spellCheckCurrentArea);
        functionKeyActions.put(KeyCode.F8, this::toggleWordWrap);
        functionKeyActions.put(KeyCode.F9, this::saveCurrentState);
        functionKeyActions.put(KeyCode.F10, this::showAllShortcuts);
        functionKeyActions.put(KeyCode.F11, this::toggleFullscreen);
        functionKeyActions.put(KeyCode.F12, mainApp::copyAllToClipboard);
    }

    /**
     * Installs the function key shortcuts onto the given JavaFX scene.
     * This method binds both the direct function key (e.g., F1) and its Alt-modified version (e.g., Alt+F1).
     * @param scene The JavaFX scene to which the shortcuts will be added.
     */
    public void installFunctionKeyShortcuts(Scene scene) {
        for (Map.Entry<KeyCode, FunctionKeyAction> entry : functionKeyActions.entrySet()) {
            KeyCode keyCode = entry.getKey();
            Runnable action = entry.getValue()::execute;

            scene.getAccelerators().put(new KeyCodeCombination(keyCode), action);
            scene.getAccelerators().put(new KeyCodeCombination(keyCode, KeyCombination.ALT_DOWN), action);
        }
    }

    // ================================
    // Function Key Action Implementations
    // ================================

    private void showHelp() {
        showInfoDialog("Help - Function Keys", buildHelpText());
    }

    private void quickInsertTemplate() {
        try {
            mainApp.insertTemplateIntoFocusedArea(IAMButtonAction.TemplateLibrary.HPI);
            showToast("HPI template inserted.");
        } catch (Exception e) {
            showErrorDialog("Template Insert Error", "Failed to insert template: " + e.getMessage());
        }
    }

    private void findInCurrentArea() {
        showInfoDialog("Find Function", "Find functionality will be implemented in a future version.");
    }

    private void refreshData() {
        showInfoDialog("Refresh Data", "Data refresh functionality will be implemented in a future version.");
    }

    private void spellCheckCurrentArea() {
        showInfoDialog("Spell Check", "Spell check functionality will be implemented in a future version.");
    }

    private void toggleWordWrap() {
        try {
            var textAreas = mainApp.getTextAreaManager().getTextAreas();
            if (!textAreas.isEmpty()) {
                boolean isWrapActive = !textAreas.get(0).isWrapText();
                textAreas.forEach(textArea -> textArea.setWrapText(isWrapActive));
                showToast("Word wrap " + (isWrapActive ? "enabled." : "disabled."));
            }
        } catch (Exception e) {
            showErrorDialog("Word Wrap Error", "Failed to toggle word wrap: " + e.getMessage());
        }
    }

    private void saveCurrentState() {
        showInfoDialog("Save State", "Auto-save functionality will be implemented in a future version.");
    }

    private void showAllShortcuts() {
        showInfoDialog("All Keyboard Shortcuts", buildAllShortcutsText());
    }

    private void toggleFullscreen() {
        try {
            Stage stage = (Stage) mainApp.getTextAreaManager().getTextAreas().get(0).getScene().getWindow();
            boolean isFullScreen = !stage.isFullScreen();
            stage.setFullScreen(isFullScreen);
            showToast("Fullscreen " + (isFullScreen ? "enabled." : "disabled."));
        } catch (Exception e) {
            showErrorDialog("Fullscreen Error", "Failed to toggle fullscreen: " + e.getMessage());
        }
    }

    // ================================
    // Helper and Utility Methods
    // ================================

    private String buildHelpText() {
        return """
            Function Key Shortcuts:

            F1  - Show this help dialog
            F2  - Quick insert HPI template
            F3  - Find in current text area (Not implemented)
            F4  - Open Vital BP & HbA1c window
            F5  - Refresh/Reload data (Not implemented)
            F6  - Format current text area
            F7  - Spell check current area (Not implemented)
            F8  - Toggle word wrap for all areas
            F9  - Save current state (Not implemented)
            F10 - Show all keyboard shortcuts
            F11 - Toggle fullscreen mode
            F12 - Copy all content to clipboard
            """;
    }

    private String buildAllShortcutsText() {
        return buildHelpText() + """

            Other Shortcuts:
            Ctrl+1 to Ctrl+9 - Focus text areas 1-9
            Ctrl+0 - Focus text area 10
            Ctrl+I - Insert current date
            Ctrl+Shift+F - Format current area
            Ctrl+Shift+C - Copy all to clipboard
            """;
    }

    private void showToast(String message) {
        // A toast is a temporary, non-blocking notification. An alert is a simple way to simulate this.
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        // Additional setup to make it more toast-like could be added here.
        alert.showAndWait();
    }

    private void showInfoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle(title);
        alert.getDialogPane().setPrefWidth(500);
        alert.setResizable(true);
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
    // Configuration Methods
    // ================================

    private static final EnumSet<KeyCode> FUNCTION_KEYS = EnumSet.of(
            KeyCode.F1, KeyCode.F2, KeyCode.F3, KeyCode.F4, KeyCode.F5, KeyCode.F6,
            KeyCode.F7, KeyCode.F8, KeyCode.F9, KeyCode.F10, KeyCode.F11, KeyCode.F12
    );

    /**
     * Assigns or updates the action for a specific function key.
     * @param keyCode The function key to modify (must be one of F1-F12).
     * @param action The new action to execute.
     * @throws IllegalArgumentException if the keyCode is not a valid function key.
     */
    public void setFunctionKeyAction(KeyCode keyCode, FunctionKeyAction action) {
        if (!FUNCTION_KEYS.contains(keyCode)) {
            throw new IllegalArgumentException("Key code must be a function key (F1-F12).");
        }
        functionKeyActions.put(keyCode, action);
    }

    /**
     * Removes the action associated with a function key.
     * @param keyCode The function key to clear.
     */
    public void removeFunctionKeyAction(KeyCode keyCode) {
        functionKeyActions.remove(keyCode);
    }

    /**
     * Returns a copy of the current map of function key actions.
     * @return A map of KeyCodes to their assigned FunctionKeyAction.
     */
    public Map<KeyCode, FunctionKeyAction> getFunctionKeyActions() {
        return new HashMap<>(functionKeyActions);
    }
}
