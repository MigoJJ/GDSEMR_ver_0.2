package com.emr.gds.fourgate.KCDdatabase;

public class KCDRecord {
    private final String classification, diseaseCode, checkField, koreanName, englishName, note;

    public KCDRecord(String classification, String diseaseCode, String checkField, String koreanName, String englishName, String note) {
        this.classification = classification;
        this.diseaseCode = diseaseCode;
        this.checkField = checkField;
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.note = note;
    }

    public String getClassification() { return classification; }
    public String getDiseaseCode() { return diseaseCode; }
    public String getCheckField() { return checkField; }
    public String getKoreanName() { return koreanName; }
    public String getEnglishName() { return englishName; }
    public String getNote() { return note; }

    public Object[] toArray() {
        return new Object[]{classification, diseaseCode, checkField, koreanName, englishName, note};
    }

    public String toFormattedString() {
        return String.format("[%s] %s (%s)", diseaseCode, koreanName, englishName);
    }

    public String toEMRFormat() {
        return toFormattedString();
    }
}