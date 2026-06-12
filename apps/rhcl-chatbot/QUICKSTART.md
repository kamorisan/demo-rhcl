# RHCL Chatbot - クイックスタート

## OpenShiftへのデプロイ（3ステップ）

### 前提条件
- OpenShiftクラスターへのアクセス
- `oc` CLI インストール済み
- OpenAI互換APIのAPIキー

### デプロイ手順

```bash
# 1. OpenShiftにログイン
oc login --server=https://api.your-cluster.com:6443

# 2. APIキーを環境変数にセット
export OPENAI_API_KEY=sk-your-api-key-here

# 3. リポジトリをクローンしてデプロイ実行
git clone https://github.com/kamorisan/demo-rhcl.git
cd demo-rhcl/apps/rhcl-chatbot
./deploy/deploy.sh
```

デプロイスクリプトが以下を自動実行します：
- ✓ プロジェクト作成（rhcl-ai-bot）
- ✓ Secret作成（APIキー）
- ✓ GitからOpenShift上でイメージビルド
- ✓ アプリケーションデプロイ
- ✓ Route URL表示

### カスタム設定でデプロイ

```bash
# Git URLとブランチを指定
export GIT_URL=https://github.com/your-username/demo-rhcl.git
export GIT_BRANCH=your-branch

# カスタムネームスペースを指定
NAMESPACE=my-project ./deploy/deploy.sh
```

### アクセス

デプロイ完了後、表示されるURL（例: `https://rhcl-chatbot-rhcl-ai-bot.apps.cluster.example.com`）にアクセスしてください。

### ログ確認

```bash
./deploy/logs.sh
```

### ステータス確認

```bash
./deploy/status.sh
```

### 削除

```bash
./deploy/cleanup.sh
```

---

## ローカル開発

### 前提条件
- Java 21
- Maven 3.9+

### 環境変数設定

```bash
export RHCL_MCP_BASE_URL=http://mcp.sandbox356.opentlc.com/mcp
export RHCL_OPENAI_BASE_URL=https://litellm-prod.apps.maas.redhatworkshops.io/v1
export RHCL_OPENAI_MODEL=deepseek-r1-distill-qwen-14b
export RHCL_OPENAI_API_KEY=sk-your-api-key-here
```

### 起動

```bash
cd apps/rhcl-chatbot
mvn quarkus:dev
```

アクセス: http://localhost:8080

---

## 使い方

### 質問例

**日本語:**
- "今年のアーセナルの試合結果を教えて"
- "5月のバルセロナの試合"
- "4月のレイカーズの試合結果"
- "NBAの今日の試合は？"

**英語:**
- "Arsenal matches this year"
- "Barcelona games in May"
- "Lakers results in April"
- "What NBA games are happening today?"

### 対応スポーツ

- **NBA** (全30チーム - 日本語・英語対応)
- **Premier League** (全20チーム - 日本語・英語対応)
- **La Liga** (全20チーム - 日本語・英語対応)
- **NFL** (全32チーム)
- **NHL** (全32チーム)

### 対応する日付表現

- 特定の月: "4月", "May"
- 今年: "今年", "this year"
- 特定の年: "2026年", "in 2026"
- 週指定: "第38節", "week 38"
- 試合数: "直近の10試合", "last 5 games"

---

## トラブルシューティング

詳細は [DEPLOY.md](DEPLOY.md) を参照してください。

### よくある問題

**ビルドエラー:**
```bash
oc logs -f bc/rhcl-chatbot -n rhcl-ai-bot
```

**Pod起動失敗:**
```bash
oc describe pod <pod-name> -n rhcl-ai-bot
oc logs -f deployment/rhcl-chatbot -n rhcl-ai-bot
```

**API接続エラー:**
```bash
# Secret確認
oc get secret rhcl-chatbot-secret -n rhcl-ai-bot -o jsonpath='{.data.openai-api-key}' | base64 -d

# Secret再作成
oc delete secret rhcl-chatbot-secret -n rhcl-ai-bot
oc create secret generic rhcl-chatbot-secret --from-literal=openai-api-key=sk-xxxxx -n rhcl-ai-bot
oc rollout restart deployment/rhcl-chatbot -n rhcl-ai-bot
```

---

## ドキュメント

- **[README.md](README.md)** - 全体概要と詳細ドキュメント
- **[DEPLOY.md](DEPLOY.md)** - デプロイガイドとトラブルシューティング
- **[docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)** - 要件定義書

---

## アーキテクチャ

```
User Browser
    ↓
WebUI (HTML/JS)
    ↓
POST /chat
    ↓
ChatbotService (Java)
    ├→ McpGatewayClient → MCP Gateway → ESPN API
    └→ OpenAiClient → LiteLLM API (DeepSeek R1)
```

---

## 機能一覧

- ✅ 自然言語チャット（日本語・英語）
- ✅ 自動スポーツ判定（NBA/EPL/LaLiga/NFL/NHL）
- ✅ チーム名フィルタリング（日本語・英語対応）
- ✅ 日付範囲指定（月・年・週・試合数）
- ✅ 全角数字正規化
- ✅ LLM推論テキスト除去
- ✅ OpenShift自動デプロイ
- ✅ Gitベースビルド
- ✅ Health Check対応
- ✅ CORS対応

---

## ライセンス

(記入してください)
