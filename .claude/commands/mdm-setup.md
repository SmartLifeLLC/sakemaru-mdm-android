USB接続中の端末にMDMアプリをインストールしDevice Ownerを設定する。

手順:
1. `adb devices` で接続端末を確認
2. 端末が1台の場合はそのまま、複数の場合はユーザーに選択させる（emulatorは除外）
3. `./scripts/mdm-setup.sh -s {シリアル}` を実行
4. 結果を報告

引数 $ARGUMENTS が指定された場合はそのままスクリプトに渡す（例: `-a path/to/apk` `-e TEST`）
