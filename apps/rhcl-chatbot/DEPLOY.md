# OpenShift デプロイガイド

## クイックスタート（3ステップ）

```bash
# 1. OpenShiftにログイン
oc login --server=https://api.cluster.example.com:6443

# 2. APIキーを環境変数にセット
export OPENAI_API_KEY=sk-xxxxx

# 3. デプロイ実行
cd apps/rhcl-chatbot
./deploy/deploy.sh
```

完了！アプリケーションURLがコンソールに表示されます。

---

## デプロイスクリプト詳細

### deploy.sh - 自動デプロイ

すべてのデプロイステップを自動実行します。

**基本的な使い方:**
```bash
export OPENAI_API_KEY=sk-xxxxx
./deploy/deploy.sh
```

**カスタム設定:**
```bash
# カスタムネームスペース
NAMESPACE=my-project ./deploy/deploy.sh

# すべてのオプション指定
./deploy/deploy.sh -n my-project -a my-chatbot -t v1.0.0
```

**オプション:**
- `-n, --namespace`: ネームスペース名（デフォルト: rhcl-ai-bot）
- `-a, --app-name`: アプリケーション名（デフォルト: rhcl-chatbot）
- `-t, --tag`: イメージタグ（デフォルト: 1.0.0-SNAPSHOT）
- `-h, --help`: ヘルプ表示

**環境変数:**
- `OPENAI_API_KEY` (必須): OpenAI APIキー
- `NAMESPACE` (オプション): ネームスペース名
- `APP_NAME` (オプション): アプリケーション名
- `IMAGE_TAG` (オプション): イメージタグ

**実行内容:**
1. ✓ OpenShiftログイン確認
2. ✓ プロジェクト作成
3. ✓ Secret作成（APIキー）
4. ✓ コンテナイメージビルド
5. ✓ アプリケーションデプロイ
6. ✓ デプロイメント状態確認
7. ✓ Route URL表示
8. ✓ 最新ログ表示

---

### status.sh - ステータス確認

デプロイメントの状態を確認します。

```bash
./deploy/status.sh

# カスタムネームスペース
NAMESPACE=my-project ./deploy/status.sh
```

**表示内容:**
- Deployment状態
- Pod一覧と状態
- Service情報
- Route情報とURL
- 最近のイベント
- 最新ログ（20行）

---

### logs.sh - ログ確認

リアルタイムログを表示します。

```bash
./deploy/logs.sh

# カスタムネームスペース
NAMESPACE=my-project ./deploy/logs.sh
```

`Ctrl+C` で終了します。

---

### cleanup.sh - リソース削除

デプロイしたリソースを削除します。

```bash
./deploy/cleanup.sh

# カスタムネームスペース
NAMESPACE=my-project ./deploy/cleanup.sh
```

**削除対象:**
- Deployment
- Service
- Route
- Secret
- BuildConfig
- ImageStream
- Project（確認後）

**注意:** 削除前に確認プロンプトが表示されます。

---

## トラブルシューティング

### デプロイが失敗する

**ログインエラー:**
```
Error: Not logged in to OpenShift
```
→ `oc login` を実行してください

**APIキーエラー:**
```
Error: OPENAI_API_KEY is not set
```
→ `export OPENAI_API_KEY=sk-xxxxx` を実行してください

**ビルドエラー:**
```bash
# ビルドログ確認
oc logs -f bc/rhcl-chatbot -n rhcl-ai-bot
```

### Pod が起動しない

**状態確認:**
```bash
./deploy/status.sh

# または手動で
oc get pods -n rhcl-ai-bot
oc describe pod <pod-name> -n rhcl-ai-bot
```

**一般的な原因:**
1. イメージプルエラー → BuildConfigを確認
2. Secret未作成 → `oc get secret rhcl-chatbot-secret -n rhcl-ai-bot`
3. リソース不足 → Pod Eventsを確認

**ログ確認:**
```bash
./deploy/logs.sh

# または手動で
oc logs -f deployment/rhcl-chatbot -n rhcl-ai-bot
```

### アプリケーションにアクセスできない

**Route確認:**
```bash
oc get route rhcl-chatbot -n rhcl-ai-bot

# URL取得
oc get route rhcl-chatbot -n rhcl-ai-bot -o jsonpath='{.spec.host}'
```

**Health Check:**
```bash
# Route URLを取得
ROUTE_URL=$(oc get route rhcl-chatbot -n rhcl-ai-bot -o jsonpath='{.spec.host}')

# Health Checkエンドポイント
curl https://$ROUTE_URL/q/health/live
curl https://$ROUTE_URL/q/health/ready
```

### MCP Gateway接続エラー

アプリケーションログに以下のエラーが表示される場合:
```
Error: MCP initialize failed: status=500
```

**確認項目:**
1. MCP Gateway URLが正しいか
   ```bash
   oc set env deployment/rhcl-chatbot --list -n rhcl-ai-bot | grep MCP
   ```

2. ネットワーク接続
   ```bash
   # Podに入って接続確認
   oc rsh deployment/rhcl-chatbot -n rhcl-ai-bot
   curl http://mcp.sandbox356.opentlc.com/mcp
   ```

### LLM API接続エラー

アプリケーションログに以下のエラーが表示される場合:
```
Error: LLM API error: status=401
```

**確認項目:**
1. APIキーが正しいか
   ```bash
   # Secret確認
   oc get secret rhcl-chatbot-secret -n rhcl-ai-bot -o jsonpath='{.data.openai-api-key}' | base64 -d
   ```

2. APIキーを更新
   ```bash
   # Secret削除
   oc delete secret rhcl-chatbot-secret -n rhcl-ai-bot
   
   # 新しいSecretを作成
   oc create secret generic rhcl-chatbot-secret \
     --from-literal=openai-api-key=sk-xxxxx \
     -n rhcl-ai-bot
   
   # Podを再起動
   oc rollout restart deployment/rhcl-chatbot -n rhcl-ai-bot
   ```

---

## 再デプロイ

コードを変更した後に再デプロイする場合:

```bash
# 方法1: デプロイスクリプトを再実行（推奨）
export OPENAI_API_KEY=sk-xxxxx
./deploy/deploy.sh

# 方法2: ビルドのみ再実行
oc start-build rhcl-chatbot --from-dir=. --follow -n rhcl-ai-bot

# 方法3: Podを再起動（コード変更なし）
oc rollout restart deployment/rhcl-chatbot -n rhcl-ai-bot
```

---

## 環境変数の変更

デプロイ後に環境変数を変更する場合:

```bash
# MCP Gateway URL変更
oc set env deployment/rhcl-chatbot \
  RHCL_MCP_BASE_URL=http://new-mcp.example.com/mcp \
  -n rhcl-ai-bot

# LLM モデル変更
oc set env deployment/rhcl-chatbot \
  RHCL_OPENAI_MODEL=gpt-4 \
  -n rhcl-ai-bot

# 環境変数一覧確認
oc set env deployment/rhcl-chatbot --list -n rhcl-ai-bot
```

環境変数を変更すると、Podが自動的に再起動されます。

---

## リソース設定の変更

CPU/メモリリソースを変更する場合:

```bash
# リソース変更
oc set resources deployment/rhcl-chatbot \
  --requests=cpu=200m,memory=512Mi \
  --limits=cpu=1000m,memory=1Gi \
  -n rhcl-ai-bot
```

---

## スケーリング

レプリカ数を変更する場合:

```bash
# レプリカ数を3に変更
oc scale deployment/rhcl-chatbot --replicas=3 -n rhcl-ai-bot

# 確認
oc get deployment rhcl-chatbot -n rhcl-ai-bot
```

---

## 参考コマンド

```bash
# すべてのリソース確認
oc get all -n rhcl-ai-bot

# ImageStream確認
oc get is rhcl-chatbot -n rhcl-ai-bot

# BuildConfig確認
oc get bc rhcl-chatbot -n rhcl-ai-bot

# ビルド履歴
oc get builds -n rhcl-ai-bot

# イベント確認
oc get events -n rhcl-ai-bot --sort-by='.lastTimestamp'

# Pod内でシェル実行
oc rsh deployment/rhcl-chatbot -n rhcl-ai-bot

# ファイルコピー
oc cp <local-file> rhcl-chatbot-<pod-id>:/tmp/ -n rhcl-ai-bot
```
