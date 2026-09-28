# IcTools_AlarmClock — メディア音アラーム

イヤホンで ASMR などを聴きながら寝てもアラームが聞こえるように、
**アラーム音を「メディア音」として再生する** Android 目覚まし時計アプリです。
イヤホン接続中はイヤホンから鳴り、YouTube などの再生は自動で停止します。

## ダウンロード

[Releases](../../releases) から APK をダウンロードしてインストールしてください。

| ファイル | 説明 |
| --- | --- |
| `MediaAlarm-x.y.z-release.apk` | 通常利用向け（署名済み・最適化済み） |
| `MediaAlarm-x.y.z-debug.apk` | デバッグ版（別アプリとして共存インストール可） |
| `SHA256SUMS.txt` | 改ざん確認用のハッシュ |

- `main` へのプッシュごとに **latest（プレリリース）** が更新されます。
- `v1.2.3` のようなタグをプッシュすると正式リリースが作成されます。
- ブランチのビルドは Actions の実行結果（Artifacts → `apk`）から取得できます。

## 機能

- **タイマー**: +1/+5/+10 分の加算式、開始/一時停止、削除（リセット）、残り時間のリング表示、終了予定時刻の表示、前回値の記憶
- **アラーム**: 複数登録、メモ、曜日指定、毎週繰り返し/1回のみ、日付指定、バイブ可否、アラーム音の個別設定
  - グループ化（グループごとの ON/OFF、折りたたみ）
  - タップで編集、**長押しで次回スキップ**（その時刻を過ぎると自動解除。時刻前にもう一度長押しで取り消し）
- **設定**: 既定アラーム音（内蔵音 / 端末のアラーム音 / 任意の音声ファイル）と音量 1〜100、スヌーズ、自動消音、音量を徐々に上げる、イヤホン未接続時の動作（スピーカー / バイブのみ / 鳴らさない）、バイブ、24h/12h 表示、テーマ、権限チェック、デバッグコンソール（ログのエクスポート）、バージョン・利用規約・ライセンス

## セキュリティ / プライバシー

- **INTERNET 権限なし**（通信を一切行いません）
- データはアプリ専用領域にのみ保存し、クラウドバックアップ対象外（`allowBackup=false`）
- 外部公開コンポーネントはランチャー画面と、システムの保護ブロードキャストのみ受信する BootReceiver のみ
- PendingIntent はすべて `FLAG_IMMUTABLE`
- 音声ファイルは SAF 経由で**読み取り権限のみ**取得（ストレージ権限不要）
- ログにアラームのメモ内容は記録しません
- CI は最小権限（`contents: read`、リリースジョブのみ `write`）。署名鍵は GitHub Secrets で管理し、リポジトリには含めません

## 使用ライブラリとライセンス評価

APK に含まれるのは Google / JetBrains 公式のライブラリのみです。

| ライブラリ | 提供元 | ライセンス | 評価 |
| --- | --- | --- | --- |
| AndroidX Core / Activity / Lifecycle | Google (AOSP) | Apache-2.0 | Android 公式。MIT アプリへの同梱に問題なし |
| Jetpack Compose (UI / Foundation / Material3 / Icons) | Google (AOSP) | Apache-2.0 | 同上 |
| Kotlin stdlib / kotlinx.coroutines | JetBrains | Apache-2.0 | Kotlin 公式 |
| JUnit 4（テストのみ・APK 非同梱） | JUnit | EPL-1.0 | 配布物に含まれないため影響なし |

- Apache-2.0 の全文と一覧はアプリ内「設定 → このアプリについて」で表示できます。
- 内蔵アラーム音はコード上で波形を合成して生成しており、第三者の音源を含みません。
- GitHub Actions は GitHub 公式（`actions/*`）と Gradle 公式（`gradle/actions`）のみを使用。Dependabot で更新を追跡します。

## ビルド

GitHub Actions（`ubuntu-latest` に同梱の Android SDK）でビルドします。ローカルでは Android SDK を用意して:

```sh
./gradlew testDebugUnitTest assembleDebug
```

### リリース署名の設定（初回のみ）

リリース APK を署名するには、リポジトリの **Settings → Secrets and variables → Actions** に以下を登録します。
未設定の場合、リリース APK は未署名（インストール不可）となり、デバッグ APK のみ利用できます。

```sh
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias mediaalarm
base64 -w0 release.jks > release.jks.b64   # この内容を SIGNING_KEYSTORE_BASE64 に登録
```

| Secret | 内容 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | keystore を base64 化した文字列 |
| `SIGNING_STORE_PASSWORD` | keystore のパスワード |
| `SIGNING_KEY_ALIAS` | 鍵のエイリアス（例: `mediaalarm`） |
| `SIGNING_KEY_PASSWORD` | 鍵のパスワード |

> keystore ファイルは**絶対にコミットしないでください**（`.gitignore` で除外済み）。紛失するとアップデートできなくなるため安全な場所に保管してください。

## 初回起動後のおすすめ設定

「設定 → 権限」で、通知・正確なアラーム・全画面通知・バッテリー最適化の除外をすべて OK にしてください。
「設定 → デバッグ → 10秒後にテスト」で、画面オフ・イヤホン接続状態での鳴動を確認できます。

## ライセンス

[MIT License](LICENSE)
