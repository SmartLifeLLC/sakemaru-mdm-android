# sakemaru-mdm-android 実装仕様（完全版）

## 1. 概要

`sakemaru-mdm-android` は、SMART LIFE / 酒丸向け Android MDM Agent アプリです。

アプリ名:

```text
酒丸伴助
```

主目的:

- 谷口・華などの client 端末を一元管理する
- Google Play Store を経由せず、酒丸アプリをインストール・更新する
- FCM push と polling を併用して command を取得する
- 将来的に出勤・退勤・GPS追跡・日報・キオスク・ワイプへ拡張する

このアプリは業務アプリ本体ではなく、端末管理用 MDM Agent です。

## 2. Local Server

local MDM server host:

```text
https://mdm.sakemaru.test
```

API base URL:

```text
https://mdm.sakemaru.test/api
```

Swagger UI:

```text
https://mdm.sakemaru.test/api/documentation
```

OpenAPI JSON:

```text
https://mdm.sakemaru.test/docs
```

Android 実装時は Swagger UI を確認してください。

Health check:

```text
https://mdm.sakemaru.test/api/health
```

エミュレータから PC 上の Laravel 開発サーバへ接続する場合:

```text
http://10.0.2.2:8000/api/health
```

Laravel を `php artisan serve --host=0.0.0.0 --port=8000` で起動して確認します。

## 3. サーバ側の実装済み内容

Laravel MDM server 側には以下が実装済みです。

| 項目 | 内容 |
| --- | --- |
| 端末登録 | `/api/device/register` |
| health check | `/api/health` |
| device token 認証 | `device_access_token` |
| FCM token 保存 | `/api/device/token` |
| heartbeat | `/api/device/heartbeat` |
| installed apps report | `/api/device/apps` |
| command polling | `/api/device/commands` |
| command result | `/api/device/command/result` |
| app update check | `/api/device/app/update-check` |
| app_versions 管理 | Filament 管理画面 |
| Swagger | L5 Swagger |

重要:

- `/api/device/register` だけ未認証
- その他の device API は `device_access_token` 必須
- `device_access_token` は登録レスポンスで 1 回だけ返る
- サーバ DB には token 平文ではなく SHA-256 hash を保存
- Android 側は token を端末内に安全に保存する

## 4. Google Play 非経由方針

Google Play Store 経由の配布は使いません。

ただし、FCM を使う場合は端末に Google Play services が必要です。

| 項目 | 方針 |
| --- | --- |
| Google Play Store | 使わない |
| Managed Google Play | 使わない |
| Google Play services | FCM のため必要 |
| Firebase / Google Cloud | FCM 用に使う |
| MDM Agent 配布 | QR provisioning で自社 APK を配布 |
| 酒丸アプリ配布 | MDM server の APK URL から直接配布 |
| Device Owner 化 | Android OS の QR provisioning |

FCM を使わない完全 Google 非依存構成にする場合は、定期 polling のみで command を取得します。

## 5. Client

現在の client:

| client | client_id | client_code |
| --- | ---: | --- |
| 谷口 | 1 | taniguchi |
| 華 | 6 | hana |

Android 側では `client_code` を使います。

## 6. 端末識別

端末ごとに固定の `device_code` を持ちます。

例:

```text
HANDY-001
HANDY-002
DELIVERY-001
```

端末内に保存する値:

| key | 内容 |
| --- | --- |
| `mdm_base_url` | `https://mdm.sakemaru.test` |
| `client_code` | `taniguchi` / `hana` |
| `device_code` | 端末管理番号 |
| `device_name` | 表示名 |
| `device_access_token` | 登録時にサーバから返る認証 token |
| `fcm_token` | Firebase Messaging token |
| `registered_device_id` | サーバ側 device id |

保存方式:

- DataStore 推奨
- `device_access_token` はログ出力しない
- `device_access_token` は再登録時に更新される可能性がある

### 6.1 登録 reset

`401 Invalid device access token` を受けた場合、Android 側は保存済みのサーバ登録情報を reset し、同じ provisioning config で再登録します。

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

手動復旧では `MainActivity` の `Reset registration` を使います。自動復旧では 401 検知後に `/api/device/register` を再実行し、新しい `device_access_token` を保存します。

## 7. 認証

### 7.1 登録 API

`/api/device/register` は未認証です。

登録成功時に `device_access_token` が返ります。

### 7.2 登録後 API

登録後の device API は token 必須です。

推奨 header:

```http
Authorization: Bearer {device_access_token}
```

代替 header:

```http
X-Device-Access-Token: {device_access_token}
```

body に `device_access_token` を含める方法もサーバ側は対応していますが、Android 実装では header を使ってください。

認証エラー:

| status | 意味 |
| ---: | --- |
| 401 | token 未指定、または不正 |
| 403 | token は存在するが、指定された client/device と一致しない |
| 422 | request validation error |

401 の場合は古い token の retry を続けず、登録 reset 後に再登録してください。

## 8. API 利用方法

共通:

```http
Content-Type: application/json
Accept: application/json
```

base URL:

```text
https://mdm.sakemaru.test/api
```

エミュレータ開発時の base URL:

```text
http://10.0.2.2:8000/api
```

### 8.0 Health check

サーバ接続確認用の未認証 API です。

```http
GET /api/health
```

local:

```text
https://mdm.sakemaru.test/api/health
```

emulator:

```text
http://10.0.2.2:8000/api/health
```

Response:

```json
{
  "status": "ok",
  "service": "sakemaru-mdm",
  "timestamp": "2026-04-22T10:00:00+09:00"
}
```

### 8.1 端末登録

初回起動時、QR provisioning 後に実行します。

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
    "device_access_token": "SAVE_THIS_TOKEN_ON_DEVICE",
    "name": "谷口 Handy 1",
    "status": "active",
    "last_seen_at": "2026-04-22T10:00:00+09:00"
  }
}
```

Android 側の処理:

1. `data.id` を `registered_device_id` として保存
2. `data.device_access_token` を保存
3. 以後の API に `Authorization: Bearer ...` を付ける

注意:

- 再度 register すると token が再発行される
- token 再発行後は古い token が使えなくなる

### 8.2 FCM token 登録

FCM token 取得時・更新時に実行します。

```http
POST /api/device/token
Authorization: Bearer {device_access_token}
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

Response:

```json
{
  "data": {
    "id": 1,
    "device_id": 1,
    "platform": "android",
    "updated_at": "2026-04-22T10:00:00+09:00"
  }
}
```

### 8.3 Heartbeat

定期的に送信します。

```http
POST /api/device/heartbeat
Authorization: Bearer {device_access_token}
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

推奨タイミング:

| タイミング | 内容 |
| --- | --- |
| 起動時 | MDM Agent 起動時 |
| 定期 | MVP は 5 分ごと |
| FCM 受信時 | command 同期前後 |
| network 復帰時 | offline から復帰したとき |
| command 実行後 | 成功・失敗に関係なく送信 |

### 8.4 Installed apps report

1時間ごとに、端末へインストールされているアプリ情報を送信します。

Android 11 以降で全アプリ一覧を取得するため、MDM Agent は `android.permission.QUERY_ALL_PACKAGES` を宣言します。Google Play Store 経由配布ではないため Play policy 審査は前提にしません。

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

Response:

```json
{
  "data": {
    "device_id": 1,
    "reported_count": 42,
    "active_count": 42,
    "removed_count": 1,
    "reported_at": "2026-04-22T10:00:00+09:00"
  }
}
```

取得する値:

| key | 内容 |
| --- | --- |
| `package_name` | Android package name |
| `app_name` | `ApplicationInfo.loadLabel()` の表示名 |
| `version_code` | `PackageInfo.longVersionCode` |
| `version_name` | `PackageInfo.versionName` |
| `is_system` | system app / updated system app |

### 8.5 Command 取得

FCM 受信時、または polling 時に実行します。

```http
GET /api/device/commands?client_code=taniguchi&device_code=HANDY-001
Authorization: Bearer {device_access_token}
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
      "executed_at": null,
      "created_at": "2026-04-22T10:00:00+09:00"
    }
  ]
}
```

重要:

- FCM payload は直接信用しない
- FCM は command 取得のトリガーとしてだけ使う
- 実行対象 command は必ずこの API の結果から判断する

### 8.6 Command result 送信

command 実行後に必ず送信します。

```http
POST /api/device/command/result
Authorization: Bearer {device_access_token}
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
  "error_code": "CHECKSUM_MISMATCH",
  "message": "apk checksum mismatch"
}
```

対応 error code:

| error_code | 意味 |
| --- | --- |
| `DOWNLOAD_FAILED` | APK download 失敗 |
| `CHECKSUM_MISMATCH` | SHA-256 不一致 |
| `INSTALL_FAILED` | PackageInstaller 失敗 |

### 8.7 App update check

起動時、heartbeat 時、`app_update` command 受信時に実行します。

```http
POST /api/device/app/update-check
Authorization: Bearer {device_access_token}
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
    "device": {
      "id": 1,
      "client_id": 1,
      "client_name": "谷口",
      "device_code": "HANDY-001",
      "name": "谷口 Handy 1",
      "status": "active",
      "last_seen_at": "2026-04-22T10:00:00+09:00"
    },
    "app_name": "handy",
    "current_version_code": 100,
    "latest_version_code": 101,
    "update_available": true,
    "force_update": true,
    "latest": {
      "id": 10,
      "app_name": "handy",
      "package_name": "com.smartlife.handy",
      "version_code": 101,
      "version_name": "1.0.1",
      "apk_url": "https://mdm.sakemaru.test/apk/handy-1.0.1.apk",
      "checksum_sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      "release_notes": "不具合修正",
      "is_force_update": true,
      "created_at": "2026-04-22T10:00:00+09:00"
    }
  }
}
```

更新なし:

```json
{
  "data": {
    "app_name": "handy",
    "current_version_code": 101,
    "latest_version_code": 101,
    "update_available": false,
    "force_update": false,
    "latest": null
  }
}
```

## 9. FCM

FCM は通知のみです。

Payload 例:

```json
{
  "type": "command",
  "command_id": "123"
}
```

Android 側処理:

1. FCM 受信
2. `/api/device/commands` を呼ぶ
3. command 一覧を取得
4. dispatcher へ渡す
5. handler 実行
6. result 送信

禁止:

- FCM payload の `command_id` だけで処理を実行しない
- FCM payload の `type` だけで処理を実行しない

## 10. Polling

Polling は必須です。

理由:

- FCM が遅延・欠落する可能性がある
- 端末が offline の間に command が登録される可能性がある
- FCM は起床通知であり、命令本体ではない

推奨:

| タイミング | 処理 |
| --- | --- |
| 起動時 | command sync |
| 5 分ごと | heartbeat + command sync |
| FCM 受信時 | command sync |
| network 復帰時 | heartbeat + command sync |

## 11. Command dispatcher

command 処理は type ごとに handler を分けます。

MVP 対応:

```text
app_update
```

将来追加:

```text
kiosk_enable
kiosk_disable
location_request
wipe
lock
sync_policy
attendance_sync
daily_report_sync
```

推奨構成:

```text
CommandDispatcher
AppUpdateCommandHandler
LocationCommandHandler
KioskCommandHandler
AttendanceCommandHandler
DailyReportCommandHandler
```

未対応 command type を受けた場合:

- install や制御は実行しない
- result=`error`
- error_code=`UNSUPPORTED_COMMAND`
- message に type を含める

## 12. app_update

処理順:

1. command payload から `app_name` を取得
2. 対象 package の現在 `versionCode` を取得
3. `/api/device/app/update-check` を呼ぶ
4. `update_available=false` なら result=`done`
5. `apk_url` から APK download
6. `checksum_sha256` があれば SHA-256 検証
7. checksum 不一致なら install しない
8. PackageInstaller で install
9. 成功なら result=`done`
10. 失敗なら result=`error`

失敗時 result 例:

```json
{
  "result": "error",
  "error_code": "INSTALL_FAILED",
  "message": "PackageInstaller status failure: ..."
}
```

## 13. APK install

Google Play は使いません。

必須:

- HTTPS download
- SHA-256 検証
- Device Owner 権限
- PackageInstaller

禁止:

- checksum 不一致 APK の install
- HTTP URL からの download
- ユーザー操作前提の install flow

## 14. QR Provisioning

端末は factory reset 後に QR provisioning で setup します。

方針:

- QR はサーバ側で生成する
- 固定値 QR は禁止
- QR には端末ごとの `client_code` / `device_code` を含める

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

Device Owner:

- factory reset 必須
- セットアップ済み端末では Device Owner 化できない
- Android 12+ は provisioning mode / policy compliance 対応が必要

Android 12+:

```text
DevicePolicyManager.ACTION_GET_PROVISIONING_MODE
DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE
```

## 15. 推奨 Android 構成

Kotlin 推奨。

| component | 役割 |
| --- | --- |
| `BansukeDeviceAdminReceiver` | Device Owner callback |
| `BansukeFirebaseMessagingService` | FCM 受信・token 更新 |
| `ProvisioningActivity` | provisioning mode / compliance |
| `MainActivity` | 状態表示・手動登録/reset fallback |
| `MdmApiClient` | Retrofit / OkHttp |
| `DeviceRegistrationRepository` | register/token/heartbeat |
| `HeartbeatWorker` | 定期 heartbeat |
| `InstalledAppsReportWorker` | 1時間ごとのインストール済みアプリ報告 |
| `CommandSyncWorker` | polling fallback |
| `CommandDispatcher` | command type 振り分け |
| `AppUpdateCommandHandler` | app_update |
| `ApkDownloader` | APK download |
| `ApkChecksumVerifier` | SHA-256 |
| `PackageInstallManager` | PackageInstaller |
| `DeviceConfigStore` | DataStore |

推奨 library:

| 用途 | 候補 |
| --- | --- |
| Network | Retrofit / OkHttp |
| JSON | kotlinx.serialization or Moshi |
| Background | WorkManager |
| Settings | DataStore |
| FCM | Firebase Messaging |
| DI | Hilt or manual DI |

## 16. 実装順

1. package name を `com.smartlife.sakemaru.bansuke` に設定
2. Firebase Messaging 導入
3. `google-services.json` 配置
4. `DeviceAdminReceiver` 実装
5. Android 12+ provisioning intent 対応
6. QR provisioning extras 読み取り
7. DataStore に `mdm_base_url`, `client_code`, `device_code` を保存
8. `/api/device/register`
9. `device_access_token` 保存
10. `/api/device/token`
11. heartbeat worker
12. installed apps report worker
13. FCM 受信
14. command sync worker
15. command dispatcher
16. `app_update` handler
17. APK download
18. SHA-256 verify
19. PackageInstaller install
20. `/api/device/command/result`
21. factory reset 実機で QR provisioning test

## 17. MVP 完了条件

| 条件 | 内容 |
| --- | --- |
| QR setup | factory reset 後、QR で酒丸伴助を Device Owner として導入 |
| register | MDM server に端末登録 |
| token | `device_access_token` を保存し、認証付き API が使える |
| FCM | FCM token 登録、FCM 受信 |
| polling | FCM なしでも command 取得可能 |
| command | `app_update` command を取得 |
| update | Google Play なしで APK 更新 |
| result | 成功/失敗を server へ返却 |

## 18. MVP ではやらないこと

- Google Play Store 配布
- Managed Google Play
- Android 側管理画面
- 複雑な権限管理
- 完全な kiosk
- remote wipe
- 常時 GPS 追跡
- 出勤・退勤 UI
- 日報 UI

## 19. 参考

| 内容 | URL |
| --- | --- |
| Android Enterprise provisioning | https://developers.google.com/android/work/play/emm-api/prov-devices |
| DevicePolicyManager | https://developer.android.com/reference/android/app/admin/DevicePolicyManager |
| AOSP provisioning | https://source.android.com/docs/devices/admin/provision |
| Firebase Android setup | https://firebase.google.com/docs/android/setup |
| FCM HTTP v1 | https://firebase.google.com/docs/cloud-messaging/migrate-v1 |
