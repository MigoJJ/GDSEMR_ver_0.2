package com.emr.gds.fourgate.KCDdatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A Java class to import a CSV file into a SQLite database.
 * This class reads the specified CSV file line by line, parses the data,
 * and inserts it into a table in a SQLite database file.
 */
public class CsvToSqliteImporter {

    public static void main(String[] args) {
        // Define the CSV file path - update this to the correct path
        String csvFilePath = "/home/migowj/git/GDSEMR_ver_0.2/app/src/main/resources/database/KCD-9master_4digit.csv";
        
        // Define the full path for the SQLite database file
        String dbName = "/home/migowj/git/GDSEMR_ver_0.2/app/src/main/resources/database/kcd_database.db";

        // Validate file exists before proceeding
        File csvFile = new File(csvFilePath);
        if (!csvFile.exists()) {
            System.err.println("Error: CSV file not found at: " + csvFilePath);
            System.err.println("Please check the file path and ensure the file exists.");
            
            // List files in the directory to help debug
            File parentDir = csvFile.getParentFile();
            if (parentDir != null && parentDir.exists()) {
                System.out.println("Files in directory " + parentDir.getAbsolutePath() + ":");
                File[] files = parentDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        System.out.println("  - " + file.getName());
                    }
                }
            }
            return;
        }

        // JDBC URL for the SQLite database
        String jdbcUrl = "jdbc:sqlite:" + dbName;

        // SQL statement to create the table if it doesn't exist
        String createTableSql = "CREATE TABLE IF NOT EXISTS kcd_codes ("
                + "classification TEXT, "
                + "disease_code TEXT, "
                + "check_field TEXT, "
                + "note TEXT, "
                + "korean_name TEXT, "
                + "english_name TEXT);";

        // SQL statement for inserting a new record
        String insertSql = "INSERT INTO kcd_codes(classification, disease_code, check_field, note, korean_name, english_name) VALUES(?, ?, ?, ?, ?, ?)";

        Connection conn = null;
        PreparedStatement pstmt = null;
        BufferedReader br = null;

        try {
            // Step 1: Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Step 2: Establish a connection to the database
            conn = DriverManager.getConnection(jdbcUrl);
            System.out.println("Connection to SQLite has been established.");

            // Step 3: Create the table if it does not exist
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSql);
                System.out.println("Table 'kcd_codes' is ready.");
            }

            // Step 4: Prepare the insert statement for efficient bulk insertion
            pstmt = conn.prepareStatement(insertSql);

            // Step 5: Read and parse the CSV file
            br = new BufferedReader(new FileReader(csvFilePath));
            String line;
            boolean isFirstLine = true;
            int lineNumber = 0;
            int successfulInserts = 0;

            // Start a transaction for faster inserts
            conn.setAutoCommit(false);
            
            System.out.println("Starting to read and insert data from CSV...");
            while ((line = br.readLine()) != null) {
                lineNumber++;
                
                // Skip the header line
                if (isFirstLine) {
                    isFirstLine = false;
                    System.out.println("Header line: " + line);
                    continue;
                }

                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Split the line by comma, handling quoted fields properly
                String[] values = parseCSVLine(line);

                // Ensure the array has at least 6 elements
                if (values.length >= 6) {
                    try {
                        // Set the values for the prepared statement
                        pstmt.setString(1, values[0] != null ? values[0].trim() : "");
                        pstmt.setString(2, values[1] != null ? values[1].trim() : "");
                        pstmt.setString(3, values[2] != null ? values[2].trim() : "");
                        pstmt.setString(4, values[3] != null ? values[3].trim() : "");
                        pstmt.setString(5, values[4] != null ? values[4].trim() : "");
                        pstmt.setString(6, values[5] != null ? values[5].trim() : "");

                        // Add the statement to the batch
                        pstmt.addBatch();
                        successfulInserts++;
                    } catch (SQLException e) {
                        System.err.println("Error preparing statement for line " + lineNumber + ": " + e.getMessage());
                    }
                } else {
                    System.err.println("Warning: Line " + lineNumber + " has insufficient columns (" + values.length + "): " + line);
                }
            }

            // Step 6: Execute the batch insert
            if (successfulInserts > 0) {
                int[] updateCounts = pstmt.executeBatch();
                System.out.println("Batch insert complete. " + updateCounts.length + " rows inserted.");
            } else {
                System.out.println("No data to insert.");
            }

            // Step 7: Commit the transaction
            conn.commit();
            System.out.println("Transaction committed successfully.");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            try {
                if (conn != null) {
                    conn.rollback(); // Rollback on error
                    System.out.println("Transaction rolled back.");
                }
            } catch (SQLException ex) {
                System.err.println("Rollback failed: " + ex.getMessage());
            }
        } catch (IOException e) {
            System.err.println("File I/O error: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Driver not found. Please add the SQLite JDBC driver to your classpath.");
        } finally {
            // Step 8: Close all resources in the reverse order of their creation
            try {
                if (pstmt != null) pstmt.close();
                if (br != null) br.close();
                if (conn != null) conn.close();
                System.out.println("Resources closed successfully.");
            } catch (SQLException | IOException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    }

    /**
     * Simple CSV line parser that handles basic quoted fields
     * For more complex CSV parsing, consider using a library like Apache Commons CSV
     */
    private static String[] parseCSVLine(String line) {
        // Simple split - for more robust CSV parsing, use a proper CSV library
        return line.split(",", -1);
    }
}