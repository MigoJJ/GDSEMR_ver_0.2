package com.emr.gds.fourgate.vaccine;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * A custom JavaFX VBox component that provides a ComboBox for selecting vaccines.
 * It uses a predefined list of vaccines from {@link VaccineConstants} and handles
 * header and action items appropriately.
 */
public class VaccineSelector extends VBox {

    private final ComboBox<String> comboBox;
    private Consumer<String> onSelectedCallback;

    public VaccineSelector() {
        // Initialize the ComboBox with items from the constants file
        comboBox = new ComboBox<>(FXCollections.observableArrayList(VaccineConstants.UI_ELEMENTS));
        comboBox.setPromptText("Select a vaccine...");

        // Customize the rendering of ComboBox cells to handle headers and actions
        configureCellFactory();
        configureButtonCell();

        // Set the action to be performed when a vaccine is selected
        comboBox.setOnAction(e -> {
            String selectedItem = comboBox.getValue();
            if (selectedItem != null && !isHeaderOrAction(selectedItem) && onSelectedCallback != null) {
                onSelectedCallback.accept(selectedItem);
            }
        });

        getChildren().addAll(new Label("Vaccine Selection:"), comboBox);
        setSpacing(10);
    }

    /**
     * Configures the cell factory for the ComboBox dropdown list.
     * Headers and actions are rendered as disabled, styled text.
     */
    private void configureCellFactory() {
        comboBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setDisable(false);
                    setStyle("");
                } else if (isHeaderOrAction(item)) {
                    setText(stripHeader(item));
                    setStyle("-fx-font-weight: bold; -fx-background-color: #f0f0f0;");
                    setDisable(true); // Make headers unselectable
                } else {
                    setText(item);
                    setDisable(false);
                    setStyle("");
                }
            }
        });
    }

    /**
     * Configures the button cell of the ComboBox (the part that is always visible).
     * It ensures that headers or actions are not displayed as the selected item.
     */
    private void configureButtonCell() {
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || isHeaderOrAction(item)) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });
    }

    // ================================
    // Public API for Fluent Configuration
    // ================================

    /**
     * Registers a callback to be executed when a vaccine is selected.
     * @param handler The consumer to handle the selected vaccine string.
     * @return This VaccineSelector instance for method chaining.
     */
    public VaccineSelector onSelected(Consumer<String> handler) {
        this.onSelectedCallback = handler;
        return this;
    }

    /**
     * Binds the selector to a TextField, replacing its content on selection.
     * @param field The TextField to bind to.
     * @return This VaccineSelector instance for method chaining.
     */
    public VaccineSelector bindTo(TextField field) {
        return onSelected(field::setText);
    }

    /**
     * Binds the selector to a TextField, appending the selection to its content.
     * @param field The TextField to append to.
     * @return This VaccineSelector instance for method chaining.
     */
    public VaccineSelector bindAppend(TextField field) {
        return onSelected(selection -> {
            String previousText = field.getText();
            if (previousText == null || previousText.isBlank()) {
                field.setText(selection);
            } else {
                field.setText(previousText + ", " + selection);
            }
        });
    }

    /**
     * Gets the currently selected vaccine.
     * @return The selected vaccine string, or null if none is selected.
     */
    public String getSelectedVaccine() {
        return comboBox.getValue();
    }

    // ================================
    // Internal Utility Methods
    // ================================

    /**
     * Checks if a given string is a header or an action item.
     */
    private static boolean isHeaderOrAction(String s) {
        return s != null && (s.startsWith("###") || "Side Effect".equals(s) || "Quit".equals(s));
    }

    /**
     * Removes the "###" prefix from header strings.
     */
    private static String stripHeader(String s) {
        return s.startsWith("###") ? s.substring(3).trim() : s;
    }
}
