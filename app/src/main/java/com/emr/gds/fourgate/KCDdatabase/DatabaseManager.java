package com.emr.gds.fourgate.KCDdatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A manager class for handling all database operations for KCD records.
 */
class DatabaseManager {
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(KCDDatabaseManagerJavaFX.JDBC_URL);
    }

    public static List<KCDRecord> getAllRecords() throws SQLException {
        List<KCDRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM kcd_codes ORDER BY disease_code";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                records.add(new KCDRecord(
                        rs.getString("classification"),
                        rs.getString("disease_code"),
                        rs.getString("check_field"),
                        rs.getString("korean_name"),
                        rs.getString("english_name"),
                        rs.getString("note")
                ));
            }
        }
        return records;
    }

    public static void addRecord(KCDRecord record) throws SQLException {
        String sql = "INSERT INTO kcd_codes(classification, disease_code, check_field, korean_name, english_name, note) VALUES(?,?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, record.getClassification());
            pstmt.setString(2, record.getDiseaseCode());
            pstmt.setString(3, record.getCheckField());
            pstmt.setString(4, record.getKoreanName());
            pstmt.setString(5, record.getEnglishName());
            pstmt.setString(6, record.getNote());
            pstmt.executeUpdate();
        }
    }

    public static void updateRecord(String originalDiseaseCode, KCDRecord record) throws SQLException {
        String sql = "UPDATE kcd_codes SET classification=?, disease_code=?, check_field=?, korean_name=?, english_name=?, note=? WHERE disease_code=?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, record.getClassification());
            pstmt.setString(2, record.getDiseaseCode());
            pstmt.setString(3, record.getCheckField());
            pstmt.setString(4, record.getKoreanName());
            pstmt.setString(5, record.getEnglishName());
            pstmt.setString(6, record.getNote());
            pstmt.setString(7, originalDiseaseCode);
            pstmt.executeUpdate();
        }
    }

    public static void deleteRecord(String diseaseCode) throws SQLException {
        String sql = "DELETE FROM kcd_codes WHERE disease_code = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, diseaseCode);
            pstmt.executeUpdate();
        }
    }
}