package com.emr.gds.diagnosis.thyroid;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.layout.Region; // Import for Region if not already there

public class Thyroid extends Application {

    // New method to create and return the main UI content for the Thyroid disorders
    // This method is now PUBLIC, allowing other classes to call it.
    public VBox createThyroidUI() {
        VBox contentBox = new VBox(10); // Spacing between categories
        contentBox.setPadding(new Insets(15));
        contentBox.setAlignment(Pos.TOP_LEFT); // Align content to the top left

        // Add categories and their items
        addCategory(contentBox, "1. HYPOTHYROIDISM", getHypothyroidismItems());
        addGap(contentBox);
        addCategory(contentBox, "2. HYPERTHYROIDISM (Thyrotoxicosis)", getHyperthyroidismItems());
        addGap(contentBox);
        addCategory(contentBox, "3. THYROIDITIS", getThyroiditisItems());
        addGap(contentBox);
        addCategory(contentBox, "4. GOITER (Thyroid Enlargement)", getGoiterItems());
        addGap(contentBox);
        addCategory(contentBox, "5. THYROID NODULES", getNodulesItems());
        addGap(contentBox);
        addCategory(contentBox, "6. THYROID CANCER", getCancerItems());
        addGap(contentBox);
        addCategory(contentBox, "7. CONGENITAL AND DEVELOPMENTAL DISORDERS", getCongenitalItems());
        addGap(contentBox);
        addCategory(contentBox, "8. SICK EUTHYROID SYNDROME", getSickEuthyroidItems());
        addGap(contentBox);
        addCategory(contentBox, "9. THYROID HORMONE RESISTANCE SYNDROMES", getResistanceItems());
        addGap(contentBox);
        addCategory(contentBox, "10. PREGNANCY-RELATED THYROID DISORDERS", getPregnancyItems());
        addGap(contentBox);

        return contentBox;
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Thyroid Disorders GDS");

        VBox root = createThyroidUI(); // Use the new method to get the UI content
        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true); // Allow content to expand to the width of the scroll pane
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // Show vertical scrollbar only when needed

        Scene scene = new Scene(scrollPane, 800, 600); // Initial window size
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Method to add a category with its items as a ComboBox
    private void addCategory(VBox parent, String categoryTitle, String[] items) {
        Label titleLabel = new Label(categoryTitle);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-padding: 5 0 0 0;"); // Styling for category titles

        ComboBox<String> itemComboBox = new ComboBox<>();
        itemComboBox.getItems().addAll(items);
        itemComboBox.setPromptText("Select a condition...");
        itemComboBox.setMaxWidth(Double.MAX_VALUE); // Allow combobox to expand horizontally

        // Adding a listener for selection changes
        itemComboBox.setOnAction(event -> {
            String selectedItem = itemComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                System.out.println("Selected: " + selectedItem + " from " + categoryTitle);
                // Here you can execute another method based on the selection
                executeDiagnosisMethod(selectedItem);
            }
        });

        VBox categoryContainer = new VBox(5); // Spacing between title and combobox
        categoryContainer.getChildren().addAll(titleLabel, itemComboBox);
        parent.getChildren().add(categoryContainer);
    }

    // Helper method to add a visual gap between categories
    private void addGap(VBox parent) {
        Region spacer = new Region();
        spacer.setMinHeight(15); // Adjust height for the gap
        parent.getChildren().add(spacer);
    }

    // This method would contain the logic to be executed when an item is selected.
    // Replace with your actual diagnostic or information display logic.
    private void executeDiagnosisMethod(String selectedCondition) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Condition Selected");
        alert.setHeaderText("Detailed Information for: " + selectedCondition);
        alert.setContentText("Here you would display detailed information, diagnostic criteria, " +
                             "treatment options, or trigger another UI component for '" + selectedCondition + "'.");
        alert.showAndWait();
        // For example:
        // openDetailedView(selectedCondition);
        // loadClinicalGuidelines(selectedCondition);
    }

    // --- Data Methods for Thyroid Conditions ---

    private String[] getHypothyroidismItems() {
        return new String[]{
                "Primary hypothyroidism (thyroid gland failure)",
                " └─ Hashimoto's thyroiditis (chronic autoimmune thyroiditis)",
                " └─ Iodine deficiency",
                " └─ Post-ablative (radioiodine therapy, thyroidectomy)",
                " └─ Drug-induced (lithium, amiodarone, interferon-alpha)",
                " └─ Congenital hypothyroidism",
                " └─ Infiltrative diseases (amyloidosis, sarcoidosis, hemochromatosis)",
                "Secondary hypothyroidism (pituitary TSH deficiency)",
                "Tertiary hypothyroidism (hypothalamic TRH deficiency)",
                "Subclinical hypothyroidism"
        };
    }
    private String[] getHyperthyroidismItems() {
        return new String[]{
                "Graves' disease (diffuse toxic goiter)",
                "Toxic multinodular goiter (Plummer's disease)",
                "Toxic adenoma (solitary autonomous nodule)",
                "Thyroiditis-associated thyrotoxicosis",
                " └─ Subacute (de Quervain's) thyroiditis",
                " └─ Silent (painless) thyroiditis",
                " └─ Postpartum thyroiditis",
                "Iodine-induced hyperthyroidism (Jod-Basedow phenomenon)",
                "TSH-secreting pituitary adenoma",
                "hCG-mediated thyrotoxicosis (gestational, trophoblastic tumors)",
                "Factitious thyrotoxicosis (exogenous thyroid hormone)",
                "Subclinical hyperthyroidism"
        };
    }
    private String[] getThyroiditisItems() {
        return new String[]{
                "Acute (suppurative) thyroiditis",
                "Subacute (de Quervain's) thyroiditis",
                "Chronic autoimmune (Hashimoto's) thyroiditis",
                "Silent (painless) thyroiditis",
                "Postpartum thyroiditis",
                "Drug-induced thyroiditis",
                "Riedel's thyroiditis (fibrous thyroiditis)"
        };
    }
    private String[] getGoiterItems() {
        return new String[]{
                "Simple (nontoxic) goiter",
                "Endemic goiter (iodine deficiency)",
                "Sporadic goiter",
                "Multinodular goiter (toxic and nontoxic)",
                "Diffuse goiter (Graves' disease, thyroiditis)"
        };
    }
    private String[] getNodulesItems() {
        return new String[]{
                "Benign thyroid nodules",
                " └─ Colloid nodules",
                " └─ Follicular adenoma",
                " └─ Thyroid cysts",
                "Malignant thyroid nodules (see thyroid cancer)"
        };
    }
    private String[] getCancerItems() {
        return new String[]{
                "Differentiated thyroid cancer",
                " └─ Papillary thyroid carcinoma (most common)",
                " └─ Follicular thyroid carcinoma",
                " └─ Hürthle cell carcinoma",
                "Medullary thyroid carcinoma (from C cells)",
                "Anaplastic (undifferentiated) thyroid carcinoma",
                "Primary thyroid lymphoma",
                "Metastatic disease to thyroid"
        };
    }
    private String[] getCongenitalItems() {
        return new String[]{
                "Congenital hypothyroidism",
                "Thyroid dysgenesis (agenesis, ectopic thyroid)",
                "Dyshormonogenesis (defects in thyroid hormone synthesis)",
                "Thyroglossal duct cyst",
                "Lingual thyroid"
        };
    }
    private String[] getSickEuthyroidItems() {
        return new String[]{
                "Nonthyroidal illness syndrome",
                "Low T3 syndrome"
        };
    }
    private String[] getResistanceItems() {
        return new String[]{
                "Resistance to thyroid hormone (RTH)",
                "TSH receptor mutations"
        };
    }
    private String[] getPregnancyItems() {
        return new String[]{
                "Gestational thyrotoxicosis",
                "Postpartum thyroiditis",
                "Hypothyroidism in pregnancy",
                "Hyperthyroidism in pregnancy"
        };
    }

    public static void main(String[] args) {
        launch(args);
    }
}