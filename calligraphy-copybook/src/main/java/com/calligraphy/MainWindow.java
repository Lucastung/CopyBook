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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private ComboBox<String> presetCombo;
    private final LinkedHashMap<String, String> presets = new LinkedHashMap<>();
    private final Set<String> userImportedFonts = new HashSet<>();

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
        // 嘗試用內嵌楷體；若無則 fallback 到系統楷體
        Font titleFont = null;
        String kaiPath = PdfGenerator.BUNDLED_FONTS.get("標楷體");
        if (kaiPath != null) {
            try (InputStream is = getClass().getResourceAsStream(kaiPath)) {
                if (is != null) titleFont = Font.loadFont(is, 28);
            } catch (Exception ignored) {}
        }
        if (titleFont == null) titleFont = Font.font("Kai", javafx.scene.text.FontPosture.ITALIC, 28);
        Label title = new Label("只是想寫寫字");
        title.setFont(titleFont);
        title.setStyle("-fx-font-size: 26px; -fx-font-style: italic; -fx-text-fill: #2c1a0e;");
        HBox bar = new HBox(title);
        bar.setAlignment(Pos.CENTER_LEFT);
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
        String defaultFont = fonts.isEmpty() ? "" : fonts.get(0);
        fontCombo.setValue(defaultFont);
        config.setFontFamily(defaultFont);
        HBox.setHgrow(fontCombo, Priority.ALWAYS);
        fontCombo.setMaxWidth(Double.MAX_VALUE);
        fontCombo.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null) { config.setFontFamily(nv); refreshPreview(); }
        });
        Button btnAddFont = new Button("+");
        btnAddFont.setTooltip(new Tooltip("匯入字型檔案"));
        btnAddFont.setOnAction(e -> importFont());
        Button btnRemoveFont = new Button("−");
        btnRemoveFont.setTooltip(new Tooltip("移除已匯入的字型"));
        btnRemoveFont.setOnAction(e -> removeFont());
        HBox fontRow = new HBox(6, fontCombo, btnAddFont, btnRemoveFont);
        fontRow.setAlignment(Pos.CENTER_LEFT);

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
        presetCombo = new ComboBox<>();
        presetCombo.setPromptText("— 選擇已存預設 —");
        HBox.setHgrow(presetCombo, Priority.ALWAYS);
        presetCombo.setMaxWidth(Double.MAX_VALUE);
        presetCombo.setOnAction(e -> {
            String sel = presetCombo.getValue();
            if (sel != null && presets.containsKey(sel)) {
                textArea.setText(presets.get(sel));
            }
        });
        Button btnSavePreset = new Button("儲存");
        btnSavePreset.setTooltip(new Tooltip("把目前文字內容存為預設"));
        btnSavePreset.setOnAction(e -> savePreset());
        HBox presetRow = new HBox(6, presetCombo, btnSavePreset);
        presetRow.setAlignment(Pos.CENTER_LEFT);
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
            lbFont, fontRow,
            new Separator(),
            lbRatio, charRatioSlider, charRatioLabel,
            new Separator(),
            lbDir, rbVertical, rbHorizontal,
            new Separator(),
            lbText, presetRow, textArea,
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
            try {
                InputStream is = resourcePath.startsWith("file:")
                    ? new java.io.FileInputStream(resourcePath.substring(5))
                    : getClass().getResourceAsStream(resourcePath);
                if (is != null) {
                    try (is) {
                        Font loaded = Font.loadFont(is, size);
                        if (loaded != null) {
                            previewFontCache.put(displayName, loaded);
                            return loaded;
                        }
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

    // ── 文字預設 ──────────────────────────────────────────────────────

    private void savePreset() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("儲存文字預設");
        dialog.setHeaderText(null);
        dialog.setContentText("請輸入預設名稱：");
        dialog.showAndWait().ifPresent(name -> {
            name = name.strip();
            if (!name.isEmpty()) {
                presets.put(name, textArea.getText());
                if (!presetCombo.getItems().contains(name)) {
                    presetCombo.getItems().add(name);
                }
                presetCombo.setValue(name);
            }
        });
    }

    // ── 字體匯入／移除 ────────────────────────────────────────────────

    private void importFont() {
        FileChooser fc = new FileChooser();
        fc.setTitle("選擇字型檔案");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("字型檔案 (*.ttf, *.otf)", "*.ttf", "*.otf"));
        File file = fc.showOpenDialog(stage);
        if (file == null) return;

        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            Font loaded = Font.loadFont(fis, 1.0);
            if (loaded == null) {
                showAlert(Alert.AlertType.ERROR, "匯入失敗", "無法載入字型，請確認檔案格式正確。");
                return;
            }
            String base = file.getName().replaceFirst("\\.[^.]+$", "");
            String name = base;
            int n = 1;
            while (PdfGenerator.BUNDLED_FONTS.containsKey(name) && !userImportedFonts.contains(name)) {
                name = base + " (" + (++n) + ")";
            }
            PdfGenerator.BUNDLED_FONTS.put(name, "file:" + file.getAbsolutePath());
            previewFontCache.put(name, loaded);
            userImportedFonts.add(name);
            if (!fontCombo.getItems().contains(name)) {
                fontCombo.getItems().add(name);
            }
            fontCombo.setValue(name);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "匯入失敗", e.getMessage());
        }
    }

    private void removeFont() {
        String selected = fontCombo.getValue();
        if (selected == null) return;
        if (!userImportedFonts.contains(selected)) {
            showAlert(Alert.AlertType.WARNING, "無法刪除", "只能刪除自行匯入的字型，內建字型無法移除。");
            return;
        }
        PdfGenerator.BUNDLED_FONTS.remove(selected);
        previewFontCache.remove(selected);
        userImportedFonts.remove(selected);
        fontCombo.getItems().remove(selected);
        if (!fontCombo.getItems().isEmpty()) {
            fontCombo.setValue(fontCombo.getItems().get(0));
        }
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
