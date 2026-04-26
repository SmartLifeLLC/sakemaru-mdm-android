新しいリリースバージョンを作成する。

$ARGUMENTS にバージョン名を指定する（例: `1.1.2`）

手順:
1. 前バージョンのreleaseブランチから `release/v{バージョン名}` を作成
2. `app/build.gradle.kts` の versionCode と versionName を更新
   - **versionCode規則（必須）**: `X * 10000 + YY * 100 + ZZ`（例: 1.1.2 → 10102）
3. releaseブランチでコミット
4. `deploy/hana` にマージ
5. deploy/hanaで `./gradlew assembleRelease` でビルド
6. APKをコピー: `hana-mdm-v{バージョン名}.apk`
7. `VERSION.md` を更新・コミット
8. 結果を報告（versionCode, versionName, APKパス, S3アップロードコマンド）
