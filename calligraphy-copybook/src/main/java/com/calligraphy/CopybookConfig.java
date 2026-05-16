package com.calligraphy;

/**
 * 字帖設定資料模型
 */
public class CopybookConfig {

    /** 格子大小（公釐） */
    private double cellSizeMm = 20.0;

    /** 字體名稱（顯示名稱，對應 PdfGenerator.BUNDLED_FONTS 的 key） */
    private String fontFamily = "";

    /** 字佔格比例（0.5 ~ 0.95） */
    private double charRatio = 0.8;

    /** 是否直書（true=直書由右至左, false=橫書由左至右） */
    private boolean vertical = true;

    /** 文字內容 */
    private String text = "";

    /** 頁數（自動計算） */
    private int pageCount = 1;

    // ── Getters / Setters ──────────────────────────────────────────────

    public double getCellSizeMm() { return cellSizeMm; }
    public void setCellSizeMm(double v) { this.cellSizeMm = v; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String v) { this.fontFamily = v; }

    public double getCharRatio() { return charRatio; }
    public void setCharRatio(double v) { this.charRatio = v; }

    public boolean isVertical() { return vertical; }
    public void setVertical(boolean v) { this.vertical = v; }

    public String getText() { return text; }
    public void setText(String v) { this.text = v; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int v) { this.pageCount = v; }

    // ── A4 計算 ────────────────────────────────────────────────────────

    /** A4 可列印寬（公釐），留邊各 15mm */
    public static final double A4_PRINT_W_MM = 210 - 30;
    /** A4 可列印高（公釐），留邊各 20mm */
    public static final double A4_PRINT_H_MM = 297 - 40;

    /** 每頁欄數 */
    public int colsPerPage() {
        return Math.max(1, (int) (A4_PRINT_W_MM / cellSizeMm));
    }

    /** 每頁行數 */
    public int rowsPerPage() {
        return Math.max(1, (int) (A4_PRINT_H_MM / cellSizeMm));
    }

    /** 每頁最大字數 */
    public int charsPerPage() {
        return colsPerPage() * rowsPerPage();
    }

    /** 根據文字長度計算需要幾頁 */
    public int calcPageCount() {
        if (text == null || text.isEmpty()) return 1;
        String clean = text.replaceAll("\\s", "");
        return Math.max(1, (int) Math.ceil((double) clean.length() / charsPerPage()));
    }
}
