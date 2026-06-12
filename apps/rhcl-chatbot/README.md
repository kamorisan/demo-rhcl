# RHCL Chatbot - MCP Gateway Demo

MCP Gateway機能をデモンストレーションするための、WebUI統合型チャットボットアプリケーション。

## 概要

- **シンプルなチャットUI**: 入力欄と会話履歴表示のみ
- **自動ツール選択**: ユーザー入力から適切なMCPツールを自動判断
- **多言語対応**: 日本語・英語の両対応
- **スポーツ情報取得**: NBA, EPL, LaLiga, NFL, NHLの試合情報を提供

## 技術スタック

- **Backend**: Quarkus 3.17.6 + Java 21
- **Frontend**: HTML + Vanilla JavaScript (フレームワークなし)
- **MCP**: Model Context Protocol v2024-11-05
- **LLM**: OpenAI互換API (DeepSeek R1)

## ローカル開発

### 前提条件

- Java 21
- Maven 3.9+
- Podman 4.x+ (MacBook Air M2)

### 環境変数設定

```bash
export RHCL_MCP_BASE_URL=http://mcp.sandbox356.opentlc.com/mcp
export RHCL_OPENAI_BASE_URL=https://litellm-prod.apps.maas.redhatworkshops.io/v1
export RHCL_OPENAI_MODEL=deepseek-r1-distill-qwen-14b
export RHCL_OPENAI_API_KEY=sk-your-api-key-here
```

### Quarkus Dev Mode

```bash
cd apps/rhcl-chatbot
mvn quarkus:dev
```

アクセス: http://localhost:8080

**Quarkus Dev UI**: http://localhost:8080/q/dev

### ビルド

```bash
# Uber JAR ビルド
mvn clean package -DskipTests

# 実行
java -jar target/quarkus-app/quarkus-run.jar
```

## Podman コンテナビルド

### イメージビルド

```bash
cd apps/rhcl-chatbot

# Podman でビルド
podman build -t rhcl-chatbot:1.0.0-SNAPSHOT .
```

### ローカル実行

```bash
podman run -d \
  --name rhcl-chatbot \
  -p 8080:8080 \
  -e RHCL_MCP_BASE_URL=http://mcp.sandbox356.opentlc.com/mcp \
  -e RHCL_OPENAI_BASE_URL=https://litellm-prod.apps.maas.redhatworkshops.io/v1 \
  -e RHCL_OPENAI_MODEL=deepseek-r1-distill-qwen-14b \
  -e RHCL_OPENAI_API_KEY=sk-... \
  rhcl-chatbot:1.0.0-SNAPSHOT

# ログ確認
podman logs -f rhcl-chatbot

# 停止・削除
podman stop rhcl-chatbot
podman rm rhcl-chatbot
```

アクセス: http://localhost:8080

## OpenShift デプロイ

### クイックスタート（自動デプロイスクリプト）

**推奨方法**: デプロイスクリプトを使用すると、すべてのステップを自動実行できます。

```bash
cd apps/rhcl-chatbot

# 1. OpenShiftにログイン
oc login --server=https://api.cluster.example.com:6443

# 2. APIキーを環境変数にセット
export OPENAI_API_KEY=sk-your-api-key-here

# 3. デプロイ実行（プロジェクト作成、ビルド、デプロイを自動実行）
./deploy/deploy.sh

# オプション: カスタム設定
NAMESPACE=my-project ./deploy/deploy.sh
```

**デプロイスクリプトが実行する内容:**
1. OpenShiftログイン確認
2. プロジェクト（Namespace）作成
3. Secret作成（API Key）
4. コンテナイメージビルド
5. アプリケーションデプロイ
6. デプロイメント確認
7. Route URL表示

### その他の便利なスクリプト

```bash
# ステータス確認
./deploy/status.sh

# ログ確認（リアルタイム）
./deploy/logs.sh

# 全リソース削除
./deploy/cleanup.sh
```

### 手動デプロイ（ステップバイステップ）

自動スクリプトを使わない場合は、以下の手順で手動デプロイできます。

#### 1. Secret 作成

```bash
# API キーを含む Secret を作成
oc create secret generic rhcl-chatbot-secret \
  --from-literal=openai-api-key=sk-your-api-key-here \
  -n rhcl-ai-bot
```

または、`deploy/rhcl-chatbot.yaml` の Secret セクションを編集:

```yaml
stringData:
  openai-api-key: "YOUR_ACTUAL_API_KEY_HERE"
```

#### 2. イメージビルド & プッシュ

```bash
# OpenShift ImageStream を使う場合
cd apps/rhcl-chatbot

# OpenShift にログイン
oc login ...

# プロジェクト作成 (なければ)
oc new-project rhcl-ai-bot

# Binary Build でイメージ作成
oc new-build --name rhcl-chatbot \
  --binary \
  --strategy docker \
  -n rhcl-ai-bot

# ビルド実行
oc start-build rhcl-chatbot \
  --from-dir=. \
  --follow \
  -n rhcl-ai-bot
```

#### 3. アプリケーションデプロイ

```bash
# デプロイ前にYAML内のイメージ名を更新
# image: rhcl-chatbot:1.0.0-SNAPSHOT
# ↓
# image: image-registry.openshift-image-registry.svc:5000/rhcl-ai-bot/rhcl-chatbot:latest

# YAML でデプロイ
oc apply -f deploy/rhcl-chatbot.yaml -n rhcl-ai-bot

# デプロイ確認
oc get pods -n rhcl-ai-bot
oc get route rhcl-chatbot -n rhcl-ai-bot

# Route URL 取得
oc get route rhcl-chatbot -n rhcl-ai-bot -o jsonpath='{.spec.host}'
```

アクセス: https://rhcl-chatbot-rhcl-ai-bot.apps.{cluster-domain}

#### 4. ログ確認

```bash
# Pod ログ
oc logs -f deployment/rhcl-chatbot -n rhcl-ai-bot

# Health チェック
oc get deployment rhcl-chatbot -n rhcl-ai-bot
```

## API エンドポイント

### POST /chat

チャットメッセージを送信して応答を取得します。

**Request:**
```json
{
  "message": "NBAの今日の試合は？"
}
```

**Response:**
```json
{
  "response": "本日のNBA試合情報をお知らせします...",
  "tool_used": "nba_scoreboard",
  "timestamp": "2026-06-11T12:34:56Z"
}
```

**Error Response:**
```json
{
  "response": "申し訳ございません。エラーが発生しました: ...",
  "tool_used": "nba_scoreboard",
  "error": "MCP tools/call failed: status=500",
  "timestamp": "2026-06-11T12:34:56Z"
}
```

### Health Checks

- **Liveness**: `GET /q/health/live`
- **Readiness**: `GET /q/health/ready`

## 使い方

### WebUI

1. ブラウザで http://localhost:8080 (またはOpenShiftのRoute URL) にアクセス
2. テキスト入力欄にメッセージを入力
3. "Send" ボタンをクリック
4. Chatbotが自動的に適切なMCPツールを選択して情報を取得
5. 結果が自然言語で表示される
6. 使用されたツール名も表示される

### 質問例

**英語:**
- "What NBA games are happening today?"
- "Show me recent Premier League matches"
- "La Liga results from last week"

**日本語:**
- "NBAの今日の試合は？"
- "Premier Leagueの最近の試合は？"
- "ラリーガの最近の試合結果を教えて"

## ツール選択ロジック

Chatbotは以下のキーワードから適切なツールを自動選択します:

| キーワード | ツール名 |
|-----------|---------|
| NBA, バスケ, basketball | `nba_scoreboard` |
| Premier League, EPL, プレミア | `epl_scoreboard` |
| La Liga, LaLiga, ラリーガ | `laliga_scoreboard` |
| NFL, アメフト, football | `nfl_scoreboard` |
| NHL, ホッケー, hockey | `nhl_scoreboard` |

明示的なキーワードがない場合はデフォルトで `nba_scoreboard` を使用します。

## 環境変数

### 必須

| 変数名 | 説明 | 例 |
|--------|------|-----|
| `RHCL_MCP_BASE_URL` | MCP Gateway URL | `http://mcp.sandbox356.opentlc.com/mcp` |
| `RHCL_OPENAI_BASE_URL` | OpenAI互換API URL | `https://litellm-prod.apps.maas.redhatworkshops.io/v1` |
| `RHCL_OPENAI_MODEL` | LLMモデル名 | `deepseek-r1-distill-qwen-14b` |
| `RHCL_OPENAI_API_KEY` | LLM APIキー | `sk-...` |

### オプション

| 変数名 | デフォルト値 | 説明 |
|--------|------------|------|
| `RHCL_MCP_TIMEOUT_SECONDS` | `10` | MCP Gateway タイムアウト |
| `RHCL_OPENAI_TIMEOUT_SECONDS` | `15` | LLM API タイムアウト |
| `RHCL_CHATBOT_DEFAULT_DATES_RANGE` | `7` | デフォルト日付範囲（±N日） |
| `QUARKUS_HTTP_PORT` | `8080` | HTTP ポート |

## トラブルシューティング

### MCP Gateway 接続エラー

```
Error: MCP initialize failed: status=500
```

**対処法:**
- `RHCL_MCP_BASE_URL` が正しいか確認
- MCP Gateway が稼働しているか確認
- ネットワーク接続を確認

### LLM API エラー

```
Error: LLM API error: status=401
```

**対処法:**
- `RHCL_OPENAI_API_KEY` が正しいか確認
- APIキーの有効期限を確認
- APIキーの権限を確認

### SSE パースエラー

```
Error: Unrecognized token 'event'
```

**原因:** MCP Gateway が SSE モードで応答している

**対処法:**
- `notifications/initialized` を送っていないか確認
- `McpGatewayClient.java` の実装を確認
- [MCP_GATEWAY_ISSUE_RHCL1.3.3.md](../../MCP_GATEWAY_ISSUE_RHCL1.3.3.md) を参照

## アーキテクチャ

```
User (Browser)
  │
  └─> WebUI (index.html)
        │
        └─> POST /chat
              │
              ├─> ChatResource (JAX-RS)
              │     │
              │     └─> ChatbotService
              │           │
              │           ├─> McpGatewayClient (MCP Protocol)
              │           │     └─> MCP Gateway (External)
              │           │           └─> ESPN API (via Backend MCP Server)
              │           │
              │           └─> OpenAiClient (LLM)
              │                 └─> LiteLLM API
              │
              └─> JSON Response
```

## 参考資料

- [要件定義書](docs/REQUIREMENTS.md)
- [MCP Specification](https://spec.modelcontextprotocol.io/specification/)
- [Quarkus Documentation](https://quarkus.io/guides/)
- [RHCL 1.3.3 MCP Gateway 既知の問題](../../MCP_GATEWAY_ISSUE_RHCL1.3.3.md)

## ライセンス

(記入してください)

## 作成者

(記入してください)
