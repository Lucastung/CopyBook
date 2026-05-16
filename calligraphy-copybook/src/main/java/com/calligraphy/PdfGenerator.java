package com.calligraphy;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 使用 Apache PDFBox 3.x 產生書法字帖 PDF
 *
 * 字體載入優先順序：
 *   1. JAR 內嵌資源 /fonts/<name>.ttf
 *   2. 系統字體目錄
 *   3. 退回 Helvetica（不支援中文，但不崩潰）
 */
public class PdfGenerator {

    private static final float PT_PER_MM = 2.8346f;
    private static final float MARGIN_X  = 15 * PT_PER_MM;
    private static final float MARGIN_Y  = 20 * PT_PER_MM;

    private static final float GRID_R  = 0.4f,  GRID_G  = 0.4f,  GRID_B  = 0.4f;
    private static final float GUIDE_R = 0.80f, GUIDE_G = 0.80f, GUIDE_B = 0.80f;
    private static final float CHAR_R  = 0.75f, CHAR_G  = 0.75f, CHAR_B  = 0.75f;

    /**
     * JAR 內嵌字體：顯示名稱 → 資源路徑
     * 由 /fonts/index.properties 動態載入，格式：檔案名稱=顯示名稱
     */
    public static final Map<String, String> BUNDLED_FONTS = new LinkedHashMap<>();
    static {
        Properties props = new Properties();
        try (InputStream is = PdfGenerator.class.getResourceAsStream("/fonts/index.properties");
             InputStreamReader reader = (is != null) ? new InputStreamReader(is, StandardCharsets.UTF_8) : null) {
            if (reader != null) {
                props.load(reader);
                for (String filename : props.stringPropertyNames()) {
                    String displayName = props.getProperty(filename);
                    BUNDLED_FONTS.put(displayName, "/fonts/" + filename);
                }
            }
        } catch (Exception ignored) {}
    }

    private final CopybookConfig config;

    public PdfGenerator(CopybookConfig config) {
        this.config = config;
    }

    // ── 主入口 ────────────────────────────────────────────────────────

    public void generate(File outputFile) throws IOException {
        String cleanText = config.getText() == null ? "" : config.getText().replaceAll("\\s", "");
        int pages        = config.calcPageCount();
        int cols         = config.colsPerPage();
        int rows         = config.rowsPerPage();
        int charsPerPage = config.charsPerPage();
        float cellPt     = (float) config.getCellSizeMm() * PT_PER_MM;
        float fontSize   = cellPt * (float) config.getCharRatio();

        try (PDDocument doc = new PDDocument()) {
            PDFont font = loadFont(doc, config.getFontFamily());

            for (int p = 0; p < pages; p++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);

                String pageChars = "";
                if (!cleanText.isEmpty()) {
                    int start = p * charsPerPage;
                    int end   = Math.min(start + charsPerPage, cleanText.length());
                    if (start < cleanText.length()) {
                        pageChars = cleanText.substring(start, end);
                    }
                }

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    drawGrid(cs, cols, rows, cellPt);
                    drawChars(cs, font, fontSize, pageChars, cols, rows, cellPt);
                }
            }
            doc.save(outputFile);
        }
    }

    // ── 格線 + 米字格 ────────────────────────────────────────────────

    private void drawGrid(PDPageContentStream cs, int cols, int rows, float cellPt) throws IOException {
        float ox = MARGIN_X;
        float oy = MARGIN_Y;

        // 米字輔助線（先畫）
        cs.setStrokingColor(GUIDE_R, GUIDE_G, GUIDE_B);
        cs.setLineWidth(0.3f);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x = ox + c * cellPt;
                float y = oy + r * cellPt;
                cs.moveTo(x,              y + cellPt / 2); cs.lineTo(x + cellPt, y + cellPt / 2); cs.stroke();
                cs.moveTo(x + cellPt / 2, y);              cs.lineTo(x + cellPt / 2, y + cellPt); cs.stroke();
                cs.moveTo(x,              y);              cs.lineTo(x + cellPt, y + cellPt);      cs.stroke();
                cs.moveTo(x + cellPt,     y);              cs.lineTo(x,          y + cellPt);      cs.stroke();
            }
        }

        // 格線
        cs.setStrokingColor(GRID_R, GRID_G, GRID_B);
        cs.setLineWidth(0.8f);
        for (int r = 0; r <= rows; r++) {
            float y = oy + r * cellPt;
            cs.moveTo(ox, y); cs.lineTo(ox + cols * cellPt, y); cs.stroke();
        }
        for (int c = 0; c <= cols; c++) {
            float x = ox + c * cellPt;
            cs.moveTo(x, oy); cs.lineTo(x, oy + rows * cellPt); cs.stroke();
        }
    }

    // ── 範本字 ────────────────────────────────────────────────────────

    private void drawChars(PDPageContentStream cs, PDFont font, float fontSize,
                           String chars, int cols, int rows, float cellPt) throws IOException {
        if (chars == null || chars.isEmpty()) return;

        cs.setNonStrokingColor(CHAR_R, CHAR_G, CHAR_B);
        float ox  = MARGIN_X;
        float oy  = MARGIN_Y;
        char[] arr = chars.toCharArray();
        int    idx = 0;

        if (config.isVertical()) {
            outer:
            for (int col = cols - 1; col >= 0; col--) {
                for (int row = 0; row < rows; row++) {
                    if (idx >= arr.length) break outer;
                    float cellLeft   = ox + col * cellPt;
                    float cellBottom = oy + (rows - 1 - row) * cellPt;
                    drawChar(cs, font, fontSize, arr[idx++], cellLeft, cellBottom, cellPt);
                }
            }
        } else {
            outer:
            for (int row = rows - 1; row >= 0; row--) {
                for (int col = 0; col < cols; col++) {
                    if (idx >= arr.length) break outer;
                    float cellLeft   = ox + col * cellPt;
                    float cellBottom = oy + row * cellPt;
                    drawChar(cs, font, fontSize, arr[idx++], cellLeft, cellBottom, cellPt);
                }
            }
        }
    }

    /** 每個字獨立 beginText/endText，避弍跌行狀態殘留，並使字居中於格内 */
    private void drawChar(PDPageContentStream cs, PDFont font, float fontSize,
                          char ch, float cellLeft, float cellBottom, float cellPt) throws IOException {
        // 水平置中：用字寬
        float charWidth = fontSize; // fallback
        try {
            charWidth = font.getStringWidth(String.valueOf(ch)) / 1000f * fontSize;
        } catch (Exception ignored) {}
        float x = cellLeft + (cellPt - charWidth) / 2f;

        // 垂直置中：用字體 ascent/descent 計算 baseline 位置
        PDFontDescriptor desc = font.getFontDescriptor();
        float ascent  = (desc != null ? desc.getAscent()  : 880f) / 1000f * fontSize;
        float descent = (desc != null ? desc.getDescent() : -120f) / 1000f * fontSize; // 負值
        float emCenter = (ascent + descent) / 2f; // baseline 以上多少是 em 中心
        float y = cellBottom + cellPt / 2f - emCenter;

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        try {
            cs.showText(String.valueOf(ch));
        } catch (Exception ignored) {
            // 字體不支援此字元，略過
        }
        cs.endText();
    }

    // ── 字體載入 ─────────────────────────────────────────────────────

    public static PDFont loadFont(PDDocument doc, String fontFamily) throws IOException {
        String resourcePath = BUNDLED_FONTS.get(fontFamily);
        if (resourcePath == null) {
            throw new IOException("找不到字體：" + fontFamily + "\n請確認 resources/fonts/index.properties 設定正確。");
        }
        if (resourcePath.startsWith("file:")) {
            File fontFile = new File(resourcePath.substring(5));
            if (!fontFile.exists()) {
                throw new IOException("字體檔案不存在：" + fontFile.getAbsolutePath());
            }
            try (InputStream fis = new java.io.FileInputStream(fontFile)) {
                return PDType0Font.load(doc, fis, true);
            }
        }
        InputStream is = PdfGenerator.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("字體檔案不存在：" + resourcePath + "\n請將字體檔放入 src/main/resources/fonts/ 並重新編譯。");
        }
        return PDType0Font.load(doc, is, true);
    }
}
