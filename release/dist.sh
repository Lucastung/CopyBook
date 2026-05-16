#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
#  書法字帖產生器 — 跨平台打包腳本
#  用法：bash release/dist.sh
#
#  ✦ 在 macOS 上執行 → 生成 macOS .dmg 安裝包
#  ✦ 在 Windows 上執行（Git Bash）→ 生成 Windows .exe 安裝包
#
#  前置需求：
#    - JDK 21+（含 jpackage）
#    - Maven 3.6+
#    - Python 3 + Pillow（pip install pillow）
#    - macOS 需要：libXrender（已內建）
#    - Windows 需要：WiX Toolset 3.x（生成 .msi 需要）
# ═══════════════════════════════════════════════════════════════════

set -euo pipefail

# ── 應用程式設定 ──────────────────────────────────────────────────
APP_NAME="CalligraphyCopybook"          # 安裝包檔名（英文，無空格）
APP_DISPLAY_NAME="只是想寫寫字"         # macOS 顯示名稱
APP_VERSION="1.0.0"
MAIN_CLASS="com.calligraphy.App"
MAIN_JAR="calligraphy-copybook-1.0-SNAPSHOT.jar"
JAVAFX_VERSION="21.0.2"

# ── 路徑 ──────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MODULE_DIR="$HOME/.m2/repository/org/openjfx"
STAGING="$SCRIPT_DIR/staging"
DIST="$SCRIPT_DIR/dist"
ICONS_DIR="$SCRIPT_DIR/icons"
ICON_PNG="$PROJECT_DIR/calligraphy-copybook/src/main/resources/icon.png"

OS="$(uname -s)"
ARCH="$(uname -m)"

# ── 工具函式 ──────────────────────────────────────────────────────
step()  { echo ""; echo "▶ $*"; }
info()  { echo "  ✓ $*"; }
error() { echo "  ✗ ERROR: $*" >&2; exit 1; }

echo ""
echo "═══════════════════════════════════════════"
echo "  書法字帖產生器  打包腳本"
echo "  平台: $OS / $ARCH"
echo "═══════════════════════════════════════════"

# ── 前置需求檢查 ──────────────────────────────────────────────────
step "0/5 環境檢查"
command -v java    >/dev/null || error "找不到 java，請安裝 JDK 21+"
command -v mvn     >/dev/null || error "找不到 mvn，請安裝 Maven 3.6+"
command -v python3 >/dev/null || error "找不到 python3"

JPACKAGE="${JAVA_HOME:-}/bin/jpackage"
[ -f "$JPACKAGE" ] || JPACKAGE="$(command -v jpackage 2>/dev/null || echo '')"
[ -n "$JPACKAGE" ]  || error "找不到 jpackage（需要 JDK 14+）"

python3 -c "from PIL import Image" 2>/dev/null || {
    echo "  安裝 Pillow..."
    python3 -m pip install pillow 2>/dev/null \
    || python3 -m pip install --break-system-packages pillow \
    || error "無法安裝 Pillow：pip install pillow"
}

info "java:     $(java -version 2>&1 | head -1)"
info "mvn:      $(mvn -version 2>&1 | head -1)"
info "jpackage: $($JPACKAGE --version)"

# ── Step 1: Maven 打包 ────────────────────────────────────────────
step "1/5 Maven 打包"
cd "$PROJECT_DIR/calligraphy-copybook"
mvn package -DskipTests -q

JAR_FILE="target/$MAIN_JAR"
[ -f "$JAR_FILE" ] || error "JAR 不存在: $JAR_FILE"
info "JAR: $(du -sh "$JAR_FILE" | cut -f1) → $JAR_FILE"

# ── Step 2: 準備暫存目錄（fat JAR + native libs）────────────────
step "2/5 準備暫存目錄"
rm -rf "$STAGING"
mkdir -p "$STAGING" "$DIST" "$ICONS_DIR"
cp "$JAR_FILE" "$STAGING/"
info "複製 JAR → staging/"

# 判斷 JavaFX platform classifier
if   [ "$OS" = "Darwin" ] && [ "$ARCH" = "arm64" ]; then FX_CLS="mac-aarch64"; NATIVE_EXT="dylib"
elif [ "$OS" = "Darwin" ];                           then FX_CLS="mac";         NATIVE_EXT="dylib"
elif [[ "$OS" == MINGW* ]] || [[ "$OS" == MSYS* ]]; then FX_CLS="win";         NATIVE_EXT="dll"
else                                                      FX_CLS="linux";       NATIVE_EXT="so"
fi
info "JavaFX classifier: $FX_CLS"
# pom.xml 的 OS profile 會在 mvn package 時自動下載對應 native JARs 到 .m2

# 從 .m2 的 JavaFX JAR 中提取 native 函式庫
for module in javafx-graphics javafx-base javafx-controls javafx-fxml; do
    jar_path="$MODULE_DIR/$module/$JAVAFX_VERSION/${module}-${JAVAFX_VERSION}-${FX_CLS}.jar"
    if [ -f "$jar_path" ]; then
        python3 - "$jar_path" "$STAGING" "$NATIVE_EXT" <<'PYEOF'
import zipfile, os, sys
jar_file, dest_dir, ext = sys.argv[1], sys.argv[2], sys.argv[3]
count = 0
with zipfile.ZipFile(jar_file) as zf:
    for entry in zf.namelist():
        if entry.endswith('.' + ext):
            basename = os.path.basename(entry)
            out_path = os.path.join(dest_dir, basename)
            with zf.open(entry) as src, open(out_path, 'wb') as dst:
                dst.write(src.read())
            count += 1
if count:
    print(f"  extracted {count} .{ext} ← {os.path.basename(jar_file)}")
PYEOF
    fi
done

info "Staging 目錄："
ls -lh "$STAGING/" | tail -20

# ── Step 3: 產生平台圖示 ──────────────────────────────────────────
step "3/5 產生圖示"
if [ "$OS" = "Darwin" ]; then
    # macOS：PNG → iconset → .icns
    ICONSET="$ICONS_DIR/icon.iconset"
    rm -rf "$ICONSET"
    mkdir -p "$ICONSET"
    python3 - "$ICON_PNG" "$ICONSET" <<'PYEOF'
from PIL import Image
import sys
src = Image.open(sys.argv[1]).convert('RGBA')
iconset = sys.argv[2]
specs = [
    (16, 'icon_16x16.png'),    (32, 'icon_16x16@2x.png'),
    (32, 'icon_32x32.png'),    (64, 'icon_32x32@2x.png'),
    (128,'icon_128x128.png'),  (256,'icon_128x128@2x.png'),
    (256,'icon_256x256.png'),  (512,'icon_256x256@2x.png'),
    (512,'icon_512x512.png'),  (1024,'icon_512x512@2x.png'),
]
for size, name in specs:
    src.resize((size, size), Image.LANCZOS).save(f"{iconset}/{name}")
print("iconset generated")
PYEOF
    iconutil -c icns "$ICONSET" -o "$ICONS_DIR/icon.icns"
    ICON_ARG="$ICONS_DIR/icon.icns"
    info "生成 icon.icns"
else
    # Windows：PNG → .ico（多尺寸）
    python3 - "$ICON_PNG" "$ICONS_DIR/icon.ico" <<'PYEOF'
from PIL import Image
import sys
src = Image.open(sys.argv[1]).convert('RGBA')
src.save(sys.argv[2], format='ICO',
         sizes=[(16,16),(32,32),(48,48),(64,64),(128,128),(256,256)])
print("icon.ico generated")
PYEOF
    ICON_ARG="$ICONS_DIR/icon.ico"
    info "生成 icon.ico"
fi

# ── Step 4: jpackage 打包 ─────────────────────────────────────────
step "4/5 jpackage 打包"

# 清除舊版輸出
rm -f "$DIST"/${APP_NAME}*.dmg \
      "$DIST"/${APP_NAME}*.exe \
      "$DIST"/${APP_NAME}*.msi 2>/dev/null || true

JPACKAGE_COMMON=(
    --name          "$APP_NAME"
    --app-version   "$APP_VERSION"
    --vendor        "CalligraphyApp"
    --description   "書法字帖產生器"
    --input         "$STAGING"
    --main-jar      "$MAIN_JAR"
    --main-class    "$MAIN_CLASS"
    --dest          "$DIST"
    --icon          "$ICON_ARG"
    --java-options  "-Dfile.encoding=UTF-8"
    --java-options  "-Djava.library.path=\$APPDIR"
)

if [ "$OS" = "Darwin" ]; then
    "$JPACKAGE" "${JPACKAGE_COMMON[@]}" \
        --type          dmg \
        --mac-package-name "$APP_DISPLAY_NAME" \
        --java-options  "-Xdock:name=$APP_DISPLAY_NAME" \
        --java-options  "-Xdock:icon=\$APPDIR/../Resources/$APP_NAME.icns"

elif [[ "$OS" == MINGW* ]] || [[ "$OS" == MSYS* ]]; then
    "$JPACKAGE" "${JPACKAGE_COMMON[@]}" \
        --type          exe \
        --win-shortcut \
        --win-menu \
        --win-menu-group "書法字帖" \
        --win-dir-chooser \
        --win-upgrade-uuid "c4f8a1b2-d3e4-4f56-89ab-cd1234ef5678"
else
    echo "  ⚠ 不支援的平台: $OS，嘗試 linux deb..."
    "$JPACKAGE" "${JPACKAGE_COMMON[@]}" --type deb
fi

# ── Step 5: 完成 ──────────────────────────────────────────────────
step "5/5 完成"
echo ""
echo "══════════════════════════════════════════"
echo "  安裝包輸出到："
ls -lh "$DIST/" 2>/dev/null
echo ""
if [ "$OS" = "Darwin" ]; then
    echo "  📦 macOS：將 .dmg 給使用者"
    echo "     雙擊 → 拖拉 App 到 Applications → 完成安裝"
else
    echo "  📦 Windows：將 .exe 給使用者"
    echo "     雙擊 → 下一步安裝 → 完成安裝"
fi
echo "══════════════════════════════════════════"
