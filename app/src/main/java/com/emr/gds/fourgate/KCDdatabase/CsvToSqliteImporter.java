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
 * A utility class to import data from a CSV file into a SQLite database.
 * This class is designed to be run as a standalone application.
 */
public class CsvToSqliteImporter {

    private static final String CSV_FILE_PATH = "/home/migowj/git/GDSEMR_ver_0.2/app/src/main/resources/database/KCD-9master_4digit.csv";
    private static final String DB_NAME = "/home/migowj/git/GDSEMR_ver_0.2/app/src/main/resources/database/kcd_database.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_NAME;

    public static void main(String[] args) {
        if (!validateCsvFile()) {
            return;
        }

        importData();
    }

    /**
     * Validates the existence of the CSV file and provides debugging information if it's not found.
     * @return true if the file exists, false otherwise.
     */
    private static boolean validateCsvFile() {
        File csvFile = new File(CSV_FILE_PATH);
        if (csvFile.exists()) {
            return true;
        }

        System.err.println("Error: CSV file not found at: " + CSV_FILE_PATH);
        System.err.println("Please check the file path and ensure the file exists.");

        // Provide debugging information about the directory content
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
        return false;
    }

    /**
     * Handles the entire data import process from CSV to SQLite.
     */
    private static void importData() {
        String createTableSql = "CREATE TABLE IF NOT EXISTS kcd_codes (" +
                "classification TEXT, disease_code TEXT, check_field TEXT, " +
                "note TEXT, korean_name TEXT, english_name TEXT);";
        String insertSql = "INSERT INTO kcd_codes(classification, disease_code, check_field, note, korean_name, english_name) VALUES(?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(JDBC_URL);
             BufferedReader br = new BufferedReader(new FileReader(CSV_FILE_PATH))) {

            System.out.println("Connection to SQLite has been established.");

            // Create table if it doesn't exist
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSql);
                System.out.println("Table 'kcd_codes' is ready.");
            }

            // Process CSV and insert data
            processCsvFile(conn, br, insertSql);

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("File I/O error: " + e.getMessage());
        }
    }

    private static void processCsvFile(Connection conn, BufferedReader br, String insertSql) throws SQLException, IOException {
        conn.setAutoCommit(false); // Use transaction for performance

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            String line;
            int lineNumber = 0;
            int successfulInserts = 0;

            br.readLine(); // Skip header line

            System.out.println("Starting to read and insert data from CSV...");
            while ((line = br.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;

                String[] values = line.split(",", -1); // Simple CSV parsing

                if (values.length >= 6) {
                    for (int i = 0; i < 6; i++) {
                        pstmt.setString(i + 1, values[i] != null ? values[i].trim() : "");
                    }
                    pstmt.addBatch();
                    successfulInserts++;
                } else {
                    System.err.println("Warning: Line " + lineNumber + " has insufficient columns (" + values.length + "): " + line);
                }
            }

            if (successfulInserts > 0) {
                int[] updateCounts = pstmt.executeBatch();
                System.out.println("Batch insert complete. " + updateCounts.length + " rows inserted.");
            }

            conn.commit();
            System.out.println("Transaction committed successfully.");

        } catch (SQLException e) {
            conn.rollback();
            System.err.println("Transaction rolled back due to an error: " + e.getMessage());
            throw e; // Re-throw to be caught by the main try-catch block
        }
    }
}
