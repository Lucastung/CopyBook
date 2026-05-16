package com.calligraphy;

import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Taskbar;
import java.io.InputStream;

public class CalligraphyApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // 設定視窗標題列圖示
        try (InputStream is = getClass().getResourceAsStream("/icon.png")) {
            if (is != null) primaryStage.getIcons().add(new Image(is));
        } catch (Exception ignored) {}

        MainWindow window = new MainWindow(primaryStage);
        window.show();
    }

    public static void main(String[] args) {
        // 設定 macOS Dock 圖示（須在 JavaFX 啟動前呼叫）
        if (Taskbar.isTaskbarSupported()) {
            try {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    try (InputStream is = CalligraphyApp.class.getResourceAsStream("/icon.png")) {
                        if (is != null) {
                            taskbar.setIconImage(ImageIO.read(is));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        launch(args);
    }
}
