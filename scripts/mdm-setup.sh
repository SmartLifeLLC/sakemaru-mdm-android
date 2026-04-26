#!/bin/bash
#
# MDM初期設置スクリプト
# USB接続した端末にMDMアプリをインストールしDevice Ownerを設定する
#
# 使い方:
#   ./scripts/mdm-setup.sh                          # 接続中の端末に最新APKをインストール
#   ./scripts/mdm-setup.sh -s NOTE59000000002155    # シリアル指定
#   ./scripts/mdm-setup.sh -a path/to/app.apk      # APK指定
#   ./scripts/mdm-setup.sh -e TEST                  # 環境指定 (PROD/TEST/LOCAL)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

COMPONENT="com.smartlife.sakemaru.bansuke/.BansukeDeviceAdminReceiver"
PACKAGE="com.smartlife.sakemaru.bansuke"
PROVISIONING_ACTIVITY="com.smartlife.sakemaru.bansuke/.provisioning.ProvisioningActivity"

MDM_ENVIRONMENT="PROD"
MDM_BASE_URL="https://mdm.lw-hana.net"
APK_PATH=""
DEVICE_SERIAL=""

while getopts "s:a:e:h" opt; do
    case $opt in
        s) DEVICE_SERIAL="$OPTARG" ;;
        a) APK_PATH="$OPTARG" ;;
        e) MDM_ENVIRONMENT="${OPTARG^^}" ;;
        h)
            echo "使い方: $0 [-s デバイスシリアル] [-a APKパス] [-e 環境(PROD/TEST/LOCAL)]"
            exit 0
            ;;
        *) exit 1 ;;
    esac
done

case "$MDM_ENVIRONMENT" in
    PROD)  MDM_BASE_URL="https://mdm.lw-hana.net" ;;
    TEST)  MDM_BASE_URL="https://mdm.sakemaru.click" ;;
    LOCAL) MDM_BASE_URL="http://10.0.2.2:8000" ;;
esac

ADB_TARGET=""
if [ -n "$DEVICE_SERIAL" ]; then
    ADB_TARGET="-s $DEVICE_SERIAL"
fi

adb_cmd() {
    adb $ADB_TARGET "$@"
}

if [ -z "$APK_PATH" ]; then
    APK_PATH=$(find "$PROJECT_DIR" -maxdepth 1 -name "hana-mdm-v*.apk" | sort -V | tail -1)
    if [ -z "$APK_PATH" ]; then
        echo "ERROR: APKが見つかりません。-a オプションでAPKパスを指定してください。"
        exit 1
    fi
fi

echo "========================================"
echo "  MDM初期設置"
echo "========================================"
echo "APK:  $APK_PATH"
echo "環境: $MDM_ENVIRONMENT ($MDM_BASE_URL)"
echo "========================================"
echo ""

echo "[1/6] デバイス接続確認..."
adb_cmd wait-for-device
SERIAL=$(adb_cmd shell getprop ro.serialno | tr -d '\r')
echo "  デバイス: $SERIAL"

echo "[2/6] Play Protect無効化..."
adb_cmd shell settings put global package_verifier_enable 0
adb_cmd shell settings put global verifier_verify_adb_installs 0
echo "  完了"

echo "[3/6] APKインストール..."
INSTALLED=$(adb_cmd shell pm list packages 2>/dev/null | grep "$PACKAGE" || true)
if [ -n "$INSTALLED" ]; then
    echo "  既存アプリを上書きインストール"
    adb_cmd install -r "$APK_PATH"
else
    adb_cmd install "$APK_PATH"
fi
echo "  完了"

echo "[4/6] Device Owner設定..."
OWNER=$(adb_cmd shell dpm list-owners 2>/dev/null | grep "$PACKAGE" || true)
if [ -n "$OWNER" ]; then
    echo "  既にDevice Ownerに設定済み"
else
    ACCOUNTS=$(adb_cmd shell dumpsys account 2>/dev/null | grep "Accounts:" | head -1 | tr -d ' ')
    if [ "$ACCOUNTS" != "Accounts:0" ]; then
        echo "  WARNING: Googleアカウントが設定されています。Device Owner設定に失敗する可能性があります。"
    fi
    adb_cmd shell dpm set-device-owner "$COMPONENT"
fi
echo "  完了"

echo "[5/7] バッテリー最適化除外..."
adb_cmd shell dumpsys deviceidle whitelist +"$PACKAGE"
echo "  完了"

echo "[6/7] プロビジョニング実行..."
adb_cmd shell am start -n "$PROVISIONING_ACTIVITY" \
    --es mdm_environment "$MDM_ENVIRONMENT" \
    --es mdm_base_url "$MDM_BASE_URL"
echo "  完了"

echo "[7/7] 設置確認中..."
sleep 5
VERSION=$(adb_cmd shell dumpsys package "$PACKAGE" | grep versionName | tr -d ' ' | head -1)
DEVICE_CODE=$(adb_cmd logcat -d | grep "device=HANA-" | tail -1 | sed 's/.*device=\(HANA-[0-9]*\).*/\1/' || echo "確認中")

echo ""
echo "========================================"
echo "  設置完了"
echo "========================================"
echo "シリアル:     $SERIAL"
echo "バージョン:   $VERSION"
echo "デバイスコード: $DEVICE_CODE"
echo "環境:         $MDM_ENVIRONMENT ($MDM_BASE_URL)"
echo "========================================"
