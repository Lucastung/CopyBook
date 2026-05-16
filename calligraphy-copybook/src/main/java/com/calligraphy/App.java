package com.calligraphy;

/**
 * Launcher 類別：不繼承 javafx.application.Application，
 * 避免 fat JAR 從 classpath 啟動時 JavaFX 模組找不到的問題。
 */
public class App {
    public static void main(String[] args) {
        CalligraphyApp.main(args);
    }
}
