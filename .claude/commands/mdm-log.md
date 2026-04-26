接続中の端末のMDMログを確認する。

手順:
1. `adb devices` で接続端末を確認（emulator除外）
2. 端末のBansukeMdmログを取得して表示
3. $ARGUMENTS が指定された場合はフィルタとして使用（例: `FCM`, `Heartbeat`, `install`）
