# sakemaru-mdm-android

SMART LIFE / 酒丸向け Android MDM Agent アプリ。

アプリ名: 酒丸伴助

## 目的

`sakemaru-mdm-android` は、谷口・華などのクライアント端末を一元管理するための MDM Agent です。

主目的は、Google Play Store を経由せずに酒丸関連アプリを端末へインストール・更新することです。

このアプリ自体は業務アプリ本体ではなく、端末を管理する基盤アプリです。Handy / Delivery などの業務アプリは、酒丸伴助が管理・更新する対象アプリとして扱います。

## 重要方針

Google Play Store 経由の配布は使いません。

ただし、FCM を使う場合は端末に Google Play services が必要です。

切り分け:

| 項目 | 使用方針 |
| --- | --- |
| Google Play Store | 使わない |
| Managed Google Play | 使わない |
| Google Play services | FCM 利用のため必要 |
| Firebase / Google Cloud | FCM 用に使う |
| MDM Agent 配布 | QR provisioning で自社 APK を配布 |
| 酒丸アプリ配布 | MDM サーバから APK を直接配布 |
| Device Owner 化 | Android OS の QR provisioning で行う |

完全に Google 依存をなくす場合は、FCM を使わず定期 polling に切り替える必要があります。

## アプリ情報

推奨 package name:

```text
com.smartlife.sakemaru.bansuke
```

表示名:

```text
酒丸伴助
```

主な責務:

| 責務 | 内容 |
| --- | --- |
| 端末登録 | MDM サーバへ client/device 情報を登録 |
| FCM token 登録 | Firebase Messaging token をサーバへ送信 |
| Heartbeat | 端末状態・最終通信を定期送信 |
| アプリ棚卸 | 端末別インストール済みアプリ情報を1時間ごとに送信 |
| FCM 受信 | コマンド通知を受け取る |
| コマンド取得 | MDM サーバから未処理コマンドを取得 |
| コマンド実行 | command type に応じて処理 |
| APK 更新 | APK download、checksum 検証、PackageInstaller 実行 |
| 結果送信 | コマンド実行結果をサーバへ返却 |
| Device Owner policy | 将来のキオスク、ワイプ、制限設定に対応 |

## クライアント

現在の対象 client:

| client | client_id | client_code |
| --- | ---: | --- |
| 谷口 | 1 | taniguchi |
| 華 | 6 | hana |

Android 側は基本的に `client_code` を使います。`client_id` はサーバ内部 ID として扱います。

## MDM サーバ

Laravel MDM server:

```text
repository: sakemaru-mdm
```

開発 URL 例:

```text
https://mdm.sakemaru.test
```

API documentation:

```text
Swagger UI: /api/documentation
OpenAPI JSON: /docs
```

Android 実装時は Swagger UI を確認してください。

## Firebase / Google Cloud

Firebase project:

```text
sakemaru-mdm-prod
```

Google Cloud / Firebase で設定するもの:

| 項目 | 内容 |
| --- | --- |
| Firebase project | `sakemaru-mdm-prod` |
| FCM HTTP v1 API | 有効化済み |
| Android app | package name を登録 |
| google-services.json | Android project の app module に配置 |
| service account JSON | Laravel サーバ側 FCM 送信用 |

Android app 登録時の package name:

```text
com.smartlife.sakemaru.bansuke
```

Google Cloud / Firebase で設定しないもの:

| 項目 | 理由 |
| --- | --- |
| Google Play 登録 | Play Store 経由配布を使わないため |
| MDM Agent 配布 | QR provisioning の APK URL で配布するため |
| Device Owner 化 | Android OS の provisioning 機能で行うため |
| 酒丸アプリ APK 配布 | MDM サーバの `app_versions` で管理するため |

FCM payload 例:

```json
{
  "type": "command",
  "command_id": "123"
}
```

FCM は起床通知のみです。FCM payload を直接信用して実行してはいけません。必ず MDM サーバの `/api/device/commands` から命令を取得して実行します。

## QR Provisioning

端末は factory reset 後に QR provisioning でセットアップします。

流れ:

1. 端末を factory reset
2. Android 初期セットアップ画面で QR provisioning
3. `酒丸伴助` APK を MDM サーバから download
4. checksum 検証
5. Device Owner として有効化
6. provisioning extras から設定を受け取る
7. MDM サーバへ端末登録
8. FCM token 登録
9. heartbeat / command polling / app inventory report 開始

QR payload 例:

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.smartlife.sakemaru.bansuke/.BansukeDeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://mdm.sakemaru.test/downloads/bansuke.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "URL_SAFE_BASE64_SHA256",
  "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
    "mdm_base_url": "https://mdm.sakemaru.test",
    "client_code": "taniguchi",
    "device_code": "HANDY-001"
  }
}
```

注意:

| 項目 | 内容 |
| --- | --- |
| Device Owner 化 | 未セットアップ端末のみ可能 |
| セットアップ済み端末 | factory reset が必要 |
| APK checksum | URL-safe Base64 SHA-256 が必要 |
| Android 12+ | provisioning mode / policy compliance 対応が必要 |

Android 12 以降で必要な対応:

```text
DevicePolicyManager.ACTION_GET_PROVISIONING_MODE
DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE
```

APK checksum 生成例:

```bash
shasum -a 256 -b bansuke.apk \
  | awk '{print $1}' \
  | xxd -r -p \
  | base64 \
  | tr '+/' '-_' \
  | tr -d '='
```

## 端末識別

端末ごとに固定の `device_code` を持ちます。

例:

```text
HANDY-001
HANDY-002
DELIVERY-001
```

`device_code` は QR provisioning extras で渡します。MVP では手入力 fallback があっても構いません。

端末内に保存する設定:

| key | 内容 |
| --- | --- |
| `mdm_base_url` | MDM サーバ URL |
| `client_code` | `taniguchi` / `hana` |
| `device_code` | 端末管理番号 |
| `device_name` | 表示名 |
| `device_access_token` | 登録時にサーバから返る認証 token |
| `fcm_token` | Firebase Messaging token |
| `registered_device_id` | サーバ登録後の device id |

保存方式は DataStore 推奨です。MVP では Room は不要です。
`device_access_token` はログ出力しません。サーバDB refresh や再登録で無効になった場合は、古い token を使い続けず reset します。

### 登録 reset

`401 Invalid device access token` を受けた場合、Android 側はローカル登録情報を reset し、同じ provisioning config で再登録します。

reset で削除する値:

| key | 扱い |
| --- | --- |
| `device_access_token` | 削除 |
| `registered_device_id` | 削除 |
| `mdm_base_url` | 維持 |
| `client_code` | 維持 |
| `device_code` | 維持 |
| `device_name` | 維持 |
| `fcm_token` | 維持 |

手動復旧では `MainActivity` の `Reset registration` を使います。reset 後は `/api/device/register` を再実行し、新しい `device_access_token` を保存します。

## API

共通:

| 項目 | 内容 |
| --- | --- |
| Content-Type | `application/json` |
| 端末識別 | `client_code` + `device_code` |
| 推奨 | Android 側は `client_code` を使用 |

### 端末登録

初回起動時に実行します。

```http
POST /api/device/register
```

Request:

```json
{
  "client_code": "taniguchi",
  "device_code": "HANDY-001",
  "name": "谷口 Handy 1",
  "fcm_token": "FCM_TOKEN",
  "platform": "android"
}
```

Response:

```json
{
  "data": {
    "id": 1,
    "client_id": 1,
    "client_name": "谷口",
    "device_code": "HANDY-001",
    "name": "谷口 Handy 1",
    "status": "active",
    "last_seen_at": "2026-04-21T10:00:00+09:00"
  }
}
```

### FCM token 登録

FCM token 取得時・更新時に実行します。

```http
POST /api/device/token
```

Request:

```json
{
  "client_code": "taniguchi",
  "device_code": "HANDY-001",
  "fcm_token": "NEW_FCM_TOKEN",
  "platform": "android"
}
```

### Heartbeat

定期的に送信します。

```http
POST /api/device/heartbeat
```

Request:

```json
{
  "client_code": "taniguchi",
  "device_code": "HANDY-001",
  "name": "谷口 Handy 1",
  "status": "active"
}
```

推奨送信タイミング:

| タイミング | 内容 |
| --- | --- |
| 定期 | MVP は 5 分ごと |
| 起動時 | アプリ起動時 |
| コマンド実行後 | 成功・失敗に関係なく送信 |
| network 復帰時 | offline から復帰したとき |

### インストール済みアプリ報告

1時間ごとに端末内のアプリ情報を送信します。Android 11 以降で全アプリ一覧を取得するため、`QUERY_ALL_PACKAGES` を宣言します。

```http
POST /api/device/apps
Authorization: Bearer {device_access_token}
```

Request:

```json
{
  "client_code": "taniguchi",
  "device_code": "HANDY-001",
  "apps": [
    {
      "package_name": "com.smartlife.handy",
      "app_name": "酒丸Handy",
      "version_code": 120,
      "version_name": "1.2.0",
      "is_system": false
    }
  ]
}
```

取得元:

| key | 取得元 |
| --- | --- |
| `package_name` | `PackageInfo.packageName` |
| `app_name` | `ApplicationInfo.loadLabel()` |
| `version_code` | `PackageInfo.longVersionCode` |
| `version_name` | `PackageInfo.versionName` |
| `is_system` | `ApplicationInfo.FLAG_SYSTEM` / `FLAG_UPDATED_SYSTEM_APP` |

### コマンド取得

FCM 受信時、または定期 polling 時に実行します。

```http
GET /api/device/commands?client_code=taniguchi&device_code=HANDY-001
```

Response:

```json
{
  "data": [
    {
      "id": 123,
      "device_id": 1,
      "type": "app_update",
      "payload": {
        "app_name": "handy"
      },
      "status": "pending",
      "created_at": "2026-04-21T10:00:00+09:00"
    }
  ]
}
```

### コマンド結果送信

コマンド実行後に必ず送信します。

```http
POST /api/device/command/result
```

成功:

```json
{
  "client_code": "taniguchi",
  "device_code": "HANDY-001",
  "command_id": 123,
  "result": "done",
  "message": "updated"
}
```

失敗:

```json
{
  "client_code": "taniguchi",
  "device_code": "HANDY-001",
  "command_id": 123,
  "result": "error",
  "message": "apk checksum mismatch"
}
```

### アプリ更新チェック

起動時、heartbeat 時、`app_update` コマンド受信時に実行します。

```http
POST /api/device/app/update-check
```

Request:

```json
{
  "client_code": "taniguchi",
  "device_code": "HANDY-001",
  "app_name": "handy",
  "version_code": 100
}
```

Response:

```json
{
  "data": {
    "app_name": "handy",
    "current_version_code": 100,
    "latest_version_code": 101,
    "update_available": true,
    "force_update": true,
    "latest": {
      "app_name": "handy",
      "package_name": "com.smartlife.handy",
      "version_code": 101,
      "version_name": "1.0.1",
      "apk_url": "https://mdm.sakemaru.test/apk/handy-1.0.1.apk",
      "checksum_sha256": "..."
    }
  }
}
```

## APK 自動更新

Google Play は使いません。

更新フロー:

1. `/api/device/app/update-check`
2. `update_available=true` を確認
3. `apk_url` から APK download
4. `checksum_sha256` があれば SHA-256 検証
5. Device Owner 権限で PackageInstaller 実行
6. 成功/失敗を `/api/device/command/result` に送信

重要:

| 項目 | 内容 |
| --- | --- |
| checksum 不一致 | 絶対に install しない |
| download | HTTPS のみ |
| install 失敗 | reason を result message に入れる |
| force_update | 将来的に対象アプリ起動制限を検討 |
| install 実行 | MDM Agent 側の責務 |

## MVP コマンド

最初に対応する command type:

```text
app_update
```

処理:

1. payload の `app_name` を読む
2. 現在の versionCode を取得
3. `/api/device/app/update-check`
4. 更新ありなら APK download
5. checksum verify
6. PackageInstaller で install
7. result 送信

今後追加候補:

| command type | 内容 |
| --- | --- |
| `kiosk_enable` | キオスク有効化 |
| `kiosk_disable` | キオスク解除 |
| `location_request` | 位置情報取得 |
| `wipe` | リモートワイプ |
| `lock` | 端末ロック |
| `sync_policy` | ポリシー同期 |
| `attendance_sync` | 出勤・退勤同期 |
| `daily_report_sync` | 日報同期 |

## 推奨 Android 構成

Kotlin 推奨。

推奨 components:

| component | 役割 |
| --- | --- |
| `BansukeDeviceAdminReceiver` | Device Owner / policy callback |
| `BansukeFirebaseMessagingService` | FCM 受信・token 更新 |
| `ProvisioningActivity` | provisioning mode / compliance 対応 |
| `MainActivity` | 状態表示・手動登録/reset fallback |
| `MdmApiClient` | Retrofit / OkHttp API client |
| `DeviceRegistrationRepository` | 登録・token・heartbeat |
| `HeartbeatWorker` | 定期 heartbeat |
| `InstalledAppsReportWorker` | 1時間ごとのアプリ棚卸送信 |
| `CommandSyncWorker` | polling fallback |
| `CommandDispatcher` | command type 振り分け |
| `AppUpdateCommandHandler` | `app_update` 実行 |
| `ApkDownloader` | APK download |
| `ApkChecksumVerifier` | SHA-256 検証 |
| `PackageInstallManager` | PackageInstaller 実行 |
| `DeviceConfigStore` | DataStore 設定保存 |

推奨 library:

| 用途 | 候補 |
| --- | --- |
| Network | Retrofit / OkHttp |
| JSON | kotlinx.serialization or Moshi |
| Background | WorkManager |
| Settings | DataStore |
| FCM | Firebase Messaging |
| DI | Hilt or manual DI |

## Device Owner で使う主な機能

MVP:

| 機能 | 用途 |
| --- | --- |
| `DeviceAdminReceiver` | Device Owner callback |
| `DevicePolicyManager` | policy 制御 |
| `PackageInstaller` | APK install |
| `WorkManager` | heartbeat / polling / app inventory |
| `FirebaseMessagingService` | FCM |

将来:

| 機能 | 用途 |
| --- | --- |
| lock task | キオスク |
| setApplicationRestrictions | 業務アプリ設定 |
| setPermissionGrantState | 権限自動付与 |
| setUninstallBlocked | アプリ削除制限 |
| setLockTaskPackages | キオスク対象指定 |
| wipeData | リモートワイプ |
| foreground service | GPS 追跡 |

## 実装順

1. Android project 初期設定
2. package name を `com.smartlife.sakemaru.bansuke` に設定
3. Firebase Messaging 導入
4. `google-services.json` 配置
5. `DeviceAdminReceiver` 実装
6. Android 12+ provisioning intent 対応
7. QR provisioning extras 読み取り
8. DataStore に設定保存
9. `/api/device/register`
10. `/api/device/token`
11. heartbeat worker
12. installed apps report worker
13. FCM 受信
14. `/api/device/commands`
15. command dispatcher
16. `app_update` handler
17. APK download
18. SHA-256 verify
19. PackageInstaller install
20. `/api/device/command/result`
21. 実機 factory reset + QR provisioning test

## MVP の完了条件

MVP のゴール:

| 条件 | 内容 |
| --- | --- |
| QR setup | factory reset 後、QR で酒丸伴助を Device Owner として導入できる |
| 登録 | MDM サーバに端末登録できる |
| FCM | FCM token を登録し、通知を受けられる |
| command | サーバから `app_update` コマンドを取得できる |
| update | Google Play なしで酒丸アプリ APK を更新できる |
| result | 成功/失敗を MDM サーバへ返せる |

## 非目標

MVP では以下は実装しません。

| 項目 | 理由 |
| --- | --- |
| Google Play 配布 | 今回の主目的と逆 |
| Managed Google Play | MVP では不要 |
| 複雑な権限管理 | 後で policy 機能として追加 |
| Android 側管理画面 | 管理は Laravel / Filament 側 |
| 顧客別ポリシー UI | 後続フェーズ |
| 完全なキオスク制御 | 後続フェーズ |
| リモートワイプ | 後続フェーズ |
| 常時 GPS 追跡 | 後続フェーズ |
| 日報 UI | 後続フェーズ |
| 出勤・退勤 UI | 後続フェーズ |

## 将来機能

今後追加する機能:

| 機能 | 内容 |
| --- | --- |
| 出勤 | 作業者の出勤打刻 |
| 退勤 | 作業者の退勤打刻 |
| GPS 追跡 | 配送・現場端末の位置管理 |
| 日報 | 作業日報登録 |
| キオスク | 業務アプリ固定 |
| リモートワイプ | 紛失時の初期化 |
| ポリシー管理 | 権限・制限・アプリ管理 |

設計上は command type ごとに handler を分けます。

例:

```text
AppUpdateCommandHandler
LocationCommandHandler
KioskCommandHandler
AttendanceCommandHandler
DailyReportCommandHandler
```

## 参考

公式確認ポイント:

| 内容 | URL |
| --- | --- |
| Android Enterprise provisioning | https://developers.google.com/android/work/play/emm-api/prov-devices |
| DevicePolicyManager provisioning extras | https://developer.android.com/reference/android/app/admin/DevicePolicyManager |
| AOSP device management provisioning | https://source.android.com/docs/devices/admin/provision |
| Firebase Android setup | https://firebase.google.com/docs/android/setup |
| FCM HTTP v1 server auth | https://firebase.google.com/docs/cloud-messaging/migrate-v1 |
