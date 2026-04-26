# MDM スキルマニュアル

Claude Code カスタムコマンド（`/コマンド名`）で MDM の主要な操作を実行できます。

---

## /mdm-setup

USB接続した端末に MDM アプリをインストールし Device Owner を設定します。

### 基本

```
/mdm-setup
```

接続中の端末を自動検出してインストールします。

### オプション

| オプション | 説明 | 例 |
|-----------|------|-----|
| `-s シリアル` | デバイスシリアル指定 | `/mdm-setup -s NOTE59000000002169` |
| `-a APKパス` | APKファイル指定 | `/mdm-setup -a hana-mdm-v1.1.1.apk` |
| `-e 環境` | 環境指定 (PROD/TEST/LOCAL) | `/mdm-setup -e TEST` |

### 処理内容

1. デバイス接続確認
2. Play Protect 無効化
3. APK インストール（最新の `hana-mdm-v*.apk` を自動選択）
4. Device Owner 設定
5. プロビジョニング実行
6. 設置確認（バージョン、デバイスコード表示）

---

## /mdm-release

新しいリリースバージョンを作成します。APK ビルドから deploy 専用ファイルの生成までを一括実行します。

### 使い方

```
/mdm-release 1.1.2
```

引数にバージョン名（X.Y.Z）を指定します。

### versionCode 規則

`versionCode = X * 10000 + YY * 100 + ZZ`

| versionName | versionCode |
|------------|-------------|
| 1.0.0 | 10000 |
| 1.1.1 | 10101 |
| 1.1.10 | 10110 |
| 1.11.1 | 11101 |
| 2.0.0 | 20000 |

### 処理内容

1. `release/v{バージョン}` ブランチ作成
2. `app/build.gradle.kts` の versionCode / versionName 更新
3. release ブランチでコミット
4. `deploy/hana` にマージ
5. signed APK ビルド (`./gradlew assembleRelease`)
6. APK コピー (`hana-mdm-v{バージョン}.apk`)
7. deploy 専用ファイル更新:
   - `VERSION.md`
   - `hana-{バージョン}.json`（QR用 JSON）
   - `hana-{バージョン}.png`（QR コード画像）
8. S3 アップロードコマンドを表示

---

## /mdm-log

接続中の端末の MDM ログを確認します。

### 基本

```
/mdm-log
```

全ての BansukeMdm ログを表示します。

### フィルタ指定

```
/mdm-log FCM
/mdm-log Heartbeat
/mdm-log install
/mdm-log location
```

引数にキーワードを指定すると、そのキーワードでフィルタします。

---

## 運用フロー例

### 新バージョンリリース → 端末設置

```
/mdm-release 1.1.2          # APK作成
aws s3 cp hana-mdm-v1.1.2.apk s3://sakemaru-public/mdm/hana/  # S3アップロード
/mdm-setup                   # 端末に設置
/mdm-log                     # ログ確認
```

### OTA アップデートテスト

```
/mdm-release 1.1.2          # 新バージョン作成
aws s3 cp ...                # S3アップロード
# サーバから app_update コマンド送信
/mdm-log install             # インストール結果確認
```
