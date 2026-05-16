package com.calligraphy;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主視窗（仿附圖二設計）
 *
 * 上方：標題列「只想寫毛筆字」
 * 左側：設定區
 * 右側：多頁 Tab 預覽
 * 底部：匯出 PDF 按鈕
 */
public class MainWindow {

    private static final double PREVIEW_SCALE = 2.0; // 預覽縮放比（px/mm）

    /** 預覽用字體快取：顯示名稱 → JavaFX Font（以字體大小 1 載入再 derive） */
    private final Map<String, Font> previewFontCache = new HashMap<>();

    private final Stage stage;
    private final CopybookConfig config = new CopybookConfig();

    // ── 設定控制項 ─────────────────────────────────────────────────────
    private Spinner<Double> cellSizeSpinner;
    private ComboBox<String> fontCombo;
    private Slider charRatioSlider;
    private Label charRatioLabel;
    private RadioButton rbHorizontal;
    private RadioButton rbVertical;
    private TextArea textArea;
    private Label infoLabel;

    // ── 預覽 ──────────────────────────────────────────────────────────
    private TabPane tabPane;

    public MainWindow(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        stage.setTitle("書法字帖產生器");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f0e8;");

        // 標題列
        root.setTop(buildTitleBar());

        // 中間：左設定 + 右預覽
        SplitPane center = new SplitPane();
        center.setDividerPositions(0.30);
        center.getItems().addAll(buildSettingsPanel(), buildPreviewPanel());
        root.setCenter(center);

        // 底部匯出按鈕
        root.setBottom(buildBottomBar());

        Scene scene = new Scene(root, 1100, 820);
        stage.setScene(scene);
        stage.setMinWidth(860);
        stage.setMinHeight(650);
        stage.show();

        // 初始預覽
        refreshPreview();
    }

    // ── 標題列 ────────────────────────────────────────────────────────

    private HBox buildTitleBar() {
        Label title = new Label("只是想寫寫字");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #2c1a0e; -fx-font-family: 'Serif';");
        HBox bar = new HBox(title);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(14, 20, 14, 20));
        bar.setStyle("-fx-background-color: #d4b896; -fx-border-color: #b8966a; -fx-border-width: 0 0 2 0;");
        return bar;
    }

    // ── 設定面板 ──────────────────────────────────────────────────────

    private ScrollPane buildSettingsPanel() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: #faf6ef;");

        // 1. 格子大小
        Label lbCell = sectionLabel("1. 字格大小（公釐）");
        cellSizeSpinner = new Spinner<>(8.0, 60.0, config.getCellSizeMm(), 1.0);
        cellSizeSpinner.setEditable(true);
        cellSizeSpinner.setMaxWidth(Double.MAX_VALUE);
        cellSizeSpinner.valueProperty().addListener((o, ov, nv) -> {
            config.setCellSizeMm(nv);
            refreshPreview();
        });

        // 2. 字體
        Label lbFont = sectionLabel("2. 字體");
        fontCombo = new ComboBox<>();
        List<String> fonts = getChineseFonts();
        fontCombo.getItems().addAll(fonts);
        // 預設選第一個內嵌字體（或清單第一項）
        String defaultFont = fonts.isEmpty() ? "" : fonts.get(0);
        fontCombo.setValue(defaultFont);
        config.setFontFamily(defaultFont);
        fontCombo.setMaxWidth(Double.MAX_VALUE);
        fontCombo.valueProperty().addListener((o, ov, nv) -> {
            config.setFontFamily(nv);
            refreshPreview();
        });

        // 3. 字佔格比例
        Label lbRatio = sectionLabel("3. 字佔格比例");
        charRatioSlider = new Slider(0.4, 0.98, config.getCharRatio());
        charRatioSlider.setShowTickLabels(true);
        charRatioSlider.setShowTickMarks(true);
        charRatioSlider.setMajorTickUnit(0.2);
        charRatioLabel = new Label(String.format("%.0f%%", config.getCharRatio() * 100));
        charRatioLabel.setStyle("-fx-font-size: 13px;");
        charRatioSlider.valueProperty().addListener((o, ov, nv) -> {
            config.setCharRatio(nv.doubleValue());
            charRatioLabel.setText(String.format("%.0f%%", nv.doubleValue() * 100));
            refreshPreview();
        });

        // 4. 排列方向
        Label lbDir = sectionLabel("4. 排列方向");
        rbVertical = new RadioButton("直書（由右至左）");
        rbHorizontal = new RadioButton("橫書（由左至右）");
        ToggleGroup tg = new ToggleGroup();
        rbVertical.setToggleGroup(tg);
        rbHorizontal.setToggleGroup(tg);
        rbVertical.setSelected(config.isVertical());
        rbHorizontal.setSelected(!config.isVertical());
        tg.selectedToggleProperty().addListener((o, ov, nv) -> {
            config.setVertical(rbVertical.isSelected());
            refreshPreview();
        });

        // 5. 文字內容
        Label lbText = sectionLabel("5. 文字內容");
        textArea = new TextArea();
        textArea.setPromptText("請輸入要練習的文字...");
        textArea.setPrefRowCount(8);
        textArea.setWrapText(true);
        textArea.textProperty().addListener((o, ov, nv) -> {
            config.setText(nv);
            refreshPreview();
        });

        // 資訊列
        infoLabel = new Label();
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        infoLabel.setWrapText(true);

        box.getChildren().addAll(
            lbCell, cellSizeSpinner,
            new Separator(),
            lbFont, fontCombo,
            new Separator(),
            lbRatio, charRatioSlider, charRatioLabel,
            new Separator(),
            lbDir, rbVertical, rbHorizontal,
            new Separator(),
            lbText, textArea,
            new Separator(),
            infoLabel
        );

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent;");
        return sp;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #3a2010;");
        return l;
    }

    // ── 預覽面板 ──────────────────────────────────────────────────────

    private VBox buildPreviewPanel() {
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: #e8e0d0;");

        VBox vb = new VBox(tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        vb.setPadding(new Insets(8));
        vb.setStyle("-fx-background-color: #e8e0d0;");
        return vb;
    }

    // ── 底部列 ────────────────────────────────────────────────────────

    private HBox buildBottomBar() {
        Button exportBtn = new Button("匯出 PDF");
        exportBtn.setStyle("-fx-font-size: 15px; -fx-background-color: #8b4513; -fx-text-fill: white; "
            + "-fx-padding: 8 28; -fx-background-radius: 6;");
        exportBtn.setOnAction(e -> exportPdf());

        HBox bar = new HBox(exportBtn);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(10, 20, 10, 20));
        bar.setStyle("-fx-background-color: #d4b896; -fx-border-color: #b8966a; -fx-border-width: 2 0 0 0;");
        return bar;
    }

    // ── 刷新預覽 ──────────────────────────────────────────────────────

    private void refreshPreview() {
        int pages = config.calcPageCount();
        config.setPageCount(pages);

        int cols = config.colsPerPage();
        int rows = config.rowsPerPage();
        infoLabel.setText(String.format(
            "每頁 %d 欄 × %d 行 = %d 格　共 %d 頁",
            cols, rows, cols * rows, pages));

        // 重建 tabs
        tabPane.getTabs().clear();
        String cleanText = config.getText() == null ? "" : config.getText().replaceAll("\\s", "");
        int charsPerPage = config.charsPerPage();

        for (int p = 0; p < pages; p++) {
            String pageChars = "";
            if (!cleanText.isEmpty()) {
                int start = p * charsPerPage;
                int end = Math.min(start + charsPerPage, cleanText.length());
                if (start < cleanText.length()) {
                    pageChars = cleanText.substring(start, end);
                }
            }
            Canvas canvas = renderPage(pageChars, cols, rows);
            ScrollPane sp = new ScrollPane(canvas);
            sp.setStyle("-fx-background-color: #888;");
            Tab tab = new Tab("第 " + (p + 1) + " 頁", sp);
            tabPane.getTabs().add(tab);
        }
    }

    /**
     * 在 Canvas 上繪製字帖預覽（等比縮小預覽）
     */
    private Canvas renderPage(String chars, int cols, int rows) {
        double s = PREVIEW_SCALE;
        double cellPx = config.getCellSizeMm() * s;
        double marginX = 15 * s;
        double marginY = 20 * s;

        double canvasW = CopybookConfig.A4_PRINT_W_MM * s + marginX * 2;
        double canvasH = CopybookConfig.A4_PRINT_H_MM * s + marginY * 2;

        Canvas canvas = new Canvas(canvasW, canvasH);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // 白色背景（模擬紙張）
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvasW, canvasH);

        // 外框
        gc.setStroke(Color.web("#555"));
        gc.setLineWidth(1.5);
        gc.strokeRect(marginX, marginY, cols * cellPx, rows * cellPx);

        // 計算字元索引起點（直書時由右欄開始）
        char[] charArr = chars.toCharArray();
        int charIdx = 0;

        double fontSize = cellPx * config.getCharRatio();
        gc.setFont(loadPreviewFont(config.getFontFamily(), fontSize));
        gc.setFill(Color.web("#c8c8c8")); // 淺灰色範本字
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        if (config.isVertical()) {
            // 直書：由右至左排列欄，每欄由上至下
            for (int col = cols - 1; col >= 0; col--) {
                double x = marginX + col * cellPx;
                for (int row = 0; row < rows; row++) {
                    double y = marginY + row * cellPx;
                    // 格線
                    gc.setStroke(Color.web("#aaaaaa"));
                    gc.setLineWidth(0.8);
                    gc.strokeRect(x, y, cellPx, cellPx);
                    // 米字格參考線（淡）
                    drawMiziGuide(gc, x, y, cellPx);
                    // 字
                    if (charIdx < charArr.length) {
                        gc.setFill(Color.web("#c8c8c8"));
                        gc.fillText(String.valueOf(charArr[charIdx]), x + cellPx / 2, y + cellPx / 2);
                        charIdx++;
                    }
                }
            }
        } else {
            // 橫書：由左至右，每行由左至右
            for (int row = 0; row < rows; row++) {
                double y = marginY + row * cellPx;
                for (int col = 0; col < cols; col++) {
                    double x = marginX + col * cellPx;
                    gc.setStroke(Color.web("#aaaaaa"));
                    gc.setLineWidth(0.8);
                    gc.strokeRect(x, y, cellPx, cellPx);
                    drawMiziGuide(gc, x, y, cellPx);
                    if (charIdx < charArr.length) {
                        gc.setFill(Color.web("#c8c8c8"));
                        gc.fillText(String.valueOf(charArr[charIdx]), x + cellPx / 2, y + cellPx / 2);
                        charIdx++;
                    }
                }
            }
        }

        return canvas;
    }

    /** 繪製米字格輔助線 */
    private void drawMiziGuide(GraphicsContext gc, double x, double y, double size) {
        gc.setStroke(Color.web("#dddddd"));
        gc.setLineWidth(0.4);
        // 橫中線
        gc.strokeLine(x, y + size / 2, x + size, y + size / 2);
        // 縱中線
        gc.strokeLine(x + size / 2, y, x + size / 2, y + size);
        // 斜線
        gc.strokeLine(x, y, x + size, y + size);
        gc.strokeLine(x + size, y, x, y + size);
    }

    // ── 字體清單（來自 resources/fonts/index.properties）──────────────

    private List<String> getChineseFonts() {
        return List.copyOf(PdfGenerator.BUNDLED_FONTS.keySet());
    }

    /**
     * 從 JAR classpath 載入內嵌字體供 JavaFX Canvas 預覽用。
     * Font.loadFont 第二個參數是大小，cache key 只用名稱，每次重新指定大小。
     */
    private Font loadPreviewFont(String displayName, double size) {
        // 若 cache 裡已有此字體（以 size=1 載入過），用 Font.font(family, size) 重新指定大小
        Font cached = previewFontCache.get(displayName);
        if (cached != null) {
            return Font.font(cached.getFamily(), size);
        }
        String resourcePath = PdfGenerator.BUNDLED_FONTS.get(displayName);
        if (resourcePath != null) {
            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    Font loaded = Font.loadFont(is, size);
                    if (loaded != null) {
                        previewFontCache.put(displayName, loaded);
                        return loaded;
                    }
                }
            } catch (Exception ignored) {}
        }
        return Font.font(size); // fallback
    }

    // ── 匯出 PDF ──────────────────────────────────────────────────────

    private void exportPdf() {
        FileChooser fc = new FileChooser();
        fc.setTitle("儲存字帖 PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF 檔案", "*.pdf"));
        fc.setInitialFileName("calligraphy_copybook.pdf");
        File file = fc.showSaveDialog(stage);
        if (file == null) return;

        try {
            PdfGenerator gen = new PdfGenerator(config);
            gen.generate(file);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("匯出成功");
            alert.setHeaderText(null);
            alert.setContentText("PDF 已儲存至：\n" + file.getAbsolutePath());
            alert.showAndWait();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("匯出失敗");
            alert.setHeaderText("無法生成 PDF");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }
}
