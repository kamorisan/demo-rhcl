# RHCL Chatbot - 要件定義書

**作成日:** 2026-06-11  
**プロジェクト:** MCP Gateway デモ用チャットボットアプリケーション

---

## 目次

1. [概要](#概要)
2. [アーキテクチャ](#アーキテクチャ)
3. [機能要件](#機能要件)
4. [非機能要件](#非機能要件)
5. [技術スタック](#技術スタック)
6. [環境変数](#環境変数)
7. [デプロイメント](#デプロイメント)
8. [開発環境](#開発環境)

---

## 概要

### プロジェクト目的

Red Hat Connectivity Link (RHCL) 1.3.3 の MCP Gateway 機能をデモンストレーションするための、WebUI 統合型チャットボットアプリケーション。

### 主要機能

- **シンプルなチャット UI**: 入力欄と会話履歴表示のみ
- **自動ツール選択**: ユーザー入力から適切な MCP ツールを自動判断
- **多言語対応**: 日本語・英語の両対応
- **スポーツ情報取得**: NBA, EPL, LaLiga, NFL, NHL の試合情報を提供

---

## アーキテクチャ

### システム構成図

```
┌─────────────────────────────────────────────────────────────┐
│ User (Browser)                                               │
│   │                                                          │
│   └─> WebUI (HTML + Vanilla JS)                            │
│         │                                                    │
│         └─> HTTPS /chat                                     │
│               │                                              │
└───────────────┼──────────────────────────────────────────────┘
                │
┌───────────────▼──────────────────────────────────────────────┐
│ RHCL Chatbot (Quarkus App)                                   │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ChatResource (JAX-RS Endpoint)                       │   │
│  │   - POST /chat                                        │   │
│  │   - 会話履歴なし（ステートレス）                     │   │
│  └──────────────┬───────────────────────────────────────┘   │
│                 │                                             │
│  ┌──────────────▼───────────────────────────────────────┐   │
│  │ ChatbotService                                        │   │
│  │   1. ユーザー入力を解析                              │   │
│  │   2. 適切な MCP ツールを選択                         │   │
│  │   3. MCP Gateway に問い合わせ                        │   │
│  │   4. LLM で結果を自然言語化                          │   │
│  └──────────┬────────────────────┬─────────────────────┘   │
│             │                    │                           │
│  ┌──────────▼──────────┐  ┌─────▼─────────────────────┐   │
│  │ McpGatewayClient    │  │ OpenAiClient              │   │
│  │  - initialize       │  │  - chat (LLM API)         │   │
│  │  - tools/call       │  │  - ツール結果を整形       │   │
│  │  (NO notifications/ │  └───────────────────────────┘   │
│  │   initialized)      │                                    │
│  └──────────┬──────────┘                                    │
└─────────────┼─────────────────────────────────────────────┘
              │
┌─────────────▼─────────────────────────────────────────────┐
│ MCP Gateway (External)                                     │
│   - http://mcp.sandbox356.opentlc.com/mcp                 │
│   - Tools: nba_scoreboard, epl_scoreboard,                │
│            laliga_scoreboard, nfl_scoreboard,              │
│            nhl_scoreboard                                  │
└─────────────┬─────────────────────────────────────────────┘
              │
┌─────────────▼─────────────────────────────────────────────┐
│ OpenAI Compatible LLM API                                  │
│   - https://litellm-prod.apps.maas.redhatworkshops.io/v1 │
│   - Model: deepseek-r1-distill-qwen-14b                   │
└───────────────────────────────────────────────────────────┘
```

### コンポーネント説明

#### 1. WebUI
- **技術:** HTML + Vanilla JavaScript (単一ファイル)
- **機能:**
  - テキスト入力欄
  - 会話履歴表示（ユーザー ↔ Chatbot）
  - 使用された MCP ツール名の表示
  - シンプルで直感的な UI

#### 2. ChatResource (JAX-RS)
- **エンドポイント:** `POST /chat`
- **入出力:**
  ```json
  // Request
  {
    "message": "NBAの今日の試合は？"
  }
  
  // Response
  {
    "response": "本日のNBA試合情報をお知らせします。[試合詳細...]",
    "tool_used": "nba_scoreboard",
    "timestamp": "2026-06-11T12:34:56Z"
  }
  ```

#### 3. ChatbotService
- **責務:**
  - ユーザー入力から適切なツールを判断
  - MCP Gateway との通信
  - LLM による結果整形

**ツール選択ロジック:**
```
入力に含まれるキーワード:
- "NBA", "バスケ" → nba_scoreboard
- "Premier League", "EPL", "プレミア" → epl_scoreboard
- "La Liga", "LaLiga", "ラリーガ" → laliga_scoreboard
- "NFL", "アメフト" → nfl_scoreboard
- "NHL", "ホッケー" → nhl_scoreboard
- 明示的でない場合 → LLM に判断させる
```

#### 4. McpGatewayClient
- **MCP プロトコル実装:**
  1. `initialize` (セッション開始)
  2. **`notifications/initialized` は送らない** (RHCL 1.3.3 問題回避)
  3. `tools/call` (ツール実行)
- **Accept ヘッダー:** `application/json, text/event-stream`
- **セッション管理:** 各リクエストごとに新しいセッション

#### 5. OpenAiClient
- **LLM 利用目的:**
  - MCP ツールの JSON 結果を自然言語に変換
  - ユーザーフレンドリーな回答生成
- **プロンプト例:**
  ```
  ユーザーの質問: "NBAの今日の試合は？"
  
  ツール結果（JSON）: {...ESPN scoreboard data...}
  
  上記の試合情報を、わかりやすい日本語でまとめてください。
  試合の日時、チーム名、スコアを含めてください。
  ```

---

## 機能要件

### FR-1: チャット機能

| ID | 要件 | 優先度 |
|----|------|--------|
| FR-1.1 | ユーザーはテキスト入力欄からメッセージを送信できる | 必須 |
| FR-1.2 | Chatbot は適切な MCP ツールを自動選択して情報を取得する | 必須 |
| FR-1.3 | Chatbot は取得した情報を自然言語で整形して返答する | 必須 |
| FR-1.4 | 回答には使用した MCP ツール名を含める | 必須 |
| FR-1.5 | 会話履歴は画面に表示するが、サーバー側では保存しない（ステートレス） | 必須 |

### FR-2: MCP Gateway 連携

| ID | 要件 | 優先度 |
|----|------|--------|
| FR-2.1 | MCP Gateway の `initialize` でセッションを開始 | 必須 |
| FR-2.2 | `notifications/initialized` は送信しない（RHCL 1.3.3 対応） | 必須 |
| FR-2.3 | 適切なツール名（`nba_scoreboard` など）で `tools/call` を実行 | 必須 |
| FR-2.4 | 日付範囲パラメータ（`dates`）を適切に設定 | 推奨 |
| FR-2.5 | MCP セッションはリクエストごとに新規作成（再利用しない） | 必須 |

### FR-3: スポーツ情報取得

| ID | 要件 | 優先度 |
|----|------|--------|
| FR-3.1 | NBA 試合情報を取得できる | 必須 |
| FR-3.2 | Premier League 試合情報を取得できる | 必須 |
| FR-3.3 | La Liga 試合情報を取得できる | 必須 |
| FR-3.4 | NFL 試合情報を取得できる | 必須 |
| FR-3.5 | NHL 試合情報を取得できる | 必須 |

### FR-4: 多言語対応

| ID | 要件 | 優先度 |
|----|------|--------|
| FR-4.1 | 日本語での質問に日本語で回答できる | 必須 |
| FR-4.2 | 英語での質問に英語で回答できる | 必須 |
| FR-4.3 | LLM が入力言語を検出して同じ言語で返答する | 必須 |

---

## 非機能要件

### NFR-1: パフォーマンス

| ID | 要件 | 目標値 |
|----|------|--------|
| NFR-1.1 | チャット応答時間 | < 5秒（通常時） |
| NFR-1.2 | MCP Gateway タイムアウト | 10秒 |
| NFR-1.3 | LLM API タイムアウト | 15秒 |

### NFR-2: 可用性

| ID | 要件 | 目標値 |
|----|------|--------|
| NFR-2.1 | MCP Gateway エラー時のフォールバック | エラーメッセージをユーザーに表示 |
| NFR-2.2 | LLM API エラー時のフォールバック | ツールの生データを返す |

### NFR-3: セキュリティ

| ID | 要件 | 対策 |
|----|------|------|
| NFR-3.1 | API キーの保護 | 環境変数・Secret で管理（ハードコードしない） |
| NFR-3.2 | HTTPS 通信 | OpenShift では Route で TLS 終端 |
| NFR-3.3 | CORS 対応 | 同一オリジンまたは許可されたオリジンのみ |

### NFR-4: 保守性

| ID | 要件 | 対策 |
|----|------|------|
| NFR-4.1 | 環境ごとの設定変更 | すべて環境変数化 |
| NFR-4.2 | ログ出力 | デバッグ用ログを標準出力 |
| NFR-4.3 | エラーハンドリング | 明確なエラーメッセージ |

---

## 技術スタック

### バックエンド

| 技術 | バージョン | 用途 |
|------|-----------|------|
| Quarkus | 3.x | アプリケーションフレームワーク |
| Java | 21 | プログラミング言語 |
| Maven | 3.9+ | ビルドツール |
| RESTEasy Reactive | (Quarkus標準) | JAX-RS 実装 |
| Jackson | (Quarkus標準) | JSON パース |

### フロントエンド

| 技術 | 用途 |
|------|------|
| HTML5 | マークアップ |
| Vanilla JavaScript | ロジック（フレームワークなし） |
| CSS3 | スタイリング |

### コンテナ

| 技術 | バージョン | 用途 |
|------|-----------|------|
| Podman | 4.x+ | コンテナランタイム（MacBook Air M2） |
| OpenShift | 4.x | 本番環境 |

### 外部サービス

| サービス | 用途 |
|---------|------|
| MCP Gateway | スポーツ情報取得（ESPN API ラッパー） |
| LiteLLM | OpenAI 互換 LLM API |

---

## 環境変数

### 必須環境変数

| 変数名 | 説明 | デフォルト値 | 例 |
|--------|------|------------|-----|
| `RHCL_MCP_BASE_URL` | MCP Gateway URL | なし（必須） | `http://mcp.sandbox356.opentlc.com/mcp` |
| `RHCL_OPENAI_BASE_URL` | OpenAI 互換 API URL | なし（必須） | `https://litellm-prod.apps.maas.redhatworkshops.io/v1` |
| `RHCL_OPENAI_MODEL` | LLM モデル名 | なし（必須） | `deepseek-r1-distill-qwen-14b` |
| `RHCL_OPENAI_API_KEY` | LLM API キー | なし（必須） | `sk-...` |

### オプション環境変数

| 変数名 | 説明 | デフォルト値 |
|--------|------|------------|
| `RHCL_MCP_TIMEOUT_SECONDS` | MCP Gateway タイムアウト（秒） | `10` |
| `RHCL_OPENAI_TIMEOUT_SECONDS` | LLM API タイムアウト（秒） | `15` |
| `RHCL_CHATBOT_DEFAULT_DATES_RANGE` | デフォルト日付範囲（日数） | `7` |
| `QUARKUS_HTTP_PORT` | HTTP ポート | `8080` |
| `QUARKUS_HTTP_CORS` | CORS 有効化 | `true` |

### 環境別設定例

#### ローカル開発 (`.env`)
```bash
RHCL_MCP_BASE_URL=http://mcp.sandbox356.opentlc.com/mcp
RHCL_OPENAI_BASE_URL=https://litellm-prod.apps.maas.redhatworkshops.io/v1
RHCL_OPENAI_MODEL=deepseek-r1-distill-qwen-14b
RHCL_OPENAI_API_KEY=sk-your-api-key-here
```

#### OpenShift (Deployment YAML)
```yaml
env:
  - name: RHCL_MCP_BASE_URL
    value: http://mcp.sandbox356.opentlc.com/mcp
  - name: RHCL_OPENAI_BASE_URL
    value: https://litellm-prod.apps.maas.redhatworkshops.io/v1
  - name: RHCL_OPENAI_MODEL
    value: deepseek-r1-distill-qwen-14b
  - name: RHCL_OPENAI_API_KEY
    valueFrom:
      secretKeyRef:
        name: rhcl-chatbot-secret
        key: openai-api-key
```

---

## デプロイメント

### ローカル開発

```bash
# Quarkus Dev Mode
cd apps/rhcl-chatbot
export RHCL_MCP_BASE_URL=http://mcp.sandbox356.opentlc.com/mcp
export RHCL_OPENAI_BASE_URL=https://litellm-prod.apps.maas.redhatworkshops.io/v1
export RHCL_OPENAI_MODEL=deepseek-r1-distill-qwen-14b
export RHCL_OPENAI_API_KEY=sk-...
mvn quarkus:dev
```

アクセス: http://localhost:8080

### コンテナビルド（Podman）

```bash
# Quarkus コンテナイメージビルド
mvn clean package -Dquarkus.container-image.build=true

# Podman でローカル実行
podman run -d \
  -p 8080:8080 \
  -e RHCL_MCP_BASE_URL=http://mcp.sandbox356.opentlc.com/mcp \
  -e RHCL_OPENAI_BASE_URL=https://litellm-prod.apps.maas.redhatworkshops.io/v1 \
  -e RHCL_OPENAI_MODEL=deepseek-r1-distill-qwen-14b \
  -e RHCL_OPENAI_API_KEY=sk-... \
  localhost/rhcl-chatbot:1.0.0-SNAPSHOT
```

### OpenShift デプロイ

**Namespace:** `rhcl-ai-bot`

**デプロイリソース:**
1. Secret (API キー)
2. Deployment (Quarkus アプリ)
3. Service
4. Route (HTTPS)

```bash
# Secret 作成
oc create secret generic rhcl-chatbot-secret \
  --from-literal=openai-api-key=sk-... \
  -n rhcl-ai-bot

# デプロイ
oc apply -f gitops/apps/rhcl-chatbot/ -n rhcl-ai-bot

# Route 確認
oc get route rhcl-chatbot -n rhcl-ai-bot
```

アクセス: https://rhcl-chatbot.apps.{cluster-domain}

---

## 開発環境

### ローカルマシン

- **OS:** macOS (MacBook Air M2 2022)
- **コンテナ:** Podman 4.x+
- **Java:** OpenJDK 21
- **Maven:** 3.9+

### IDE

- **推奨:** IntelliJ IDEA / VS Code + Java Extensions
- **Quarkus Tools:** Quarkus Dev UI (http://localhost:8080/q/dev)

### デバッグ

**ログレベル設定 (`application.properties`):**
```properties
quarkus.log.level=INFO
quarkus.log.category."com.redhat.rhcl.chatbot".level=DEBUG
```

**MCP Gateway レスポンスログ:**
```java
System.out.println("MCP Response: status=" + response.statusCode() + 
                   " contentType=" + response.headers().firstValue("content-type").orElse("none"));
```

---

## 制約事項・既知の問題

### RHCL 1.3.3 対応

1. **`notifications/initialized` を送らない**
   - 理由: MCP Gateway が SSE モードに切り替わる問題を回避
   - 参照: [MCP_GATEWAY_ISSUE_RHCL1.3.3.md](../../MCP_GATEWAY_ISSUE_RHCL1.3.3.md)

2. **Accept ヘッダー**
   - 必須: `Accept: application/json, text/event-stream`
   - Backend MCP Server が両方を要求

3. **外部 URL 使用**
   - Gateway privateHost ではなく、外部 URL を使用
   - 理由: 本アプリは OpenShift 外からもアクセス可能

---

## 成功基準

### 機能面

- ✅ ユーザーが「NBAの今日の試合は？」と入力すると、適切な試合情報が返ってくる
- ✅ 使用した MCP ツール名（`nba_scoreboard`）が回答に表示される
- ✅ 日本語・英語の両方で正しく動作する
- ✅ 5つのスポーツすべてで情報取得できる

### 非機能面

- ✅ ローカル開発環境（Quarkus Dev Mode）で動作する
- ✅ Podman コンテナイメージがビルドできる
- ✅ OpenShift にデプロイして HTTPS でアクセスできる
- ✅ MCP Gateway エラー時も適切なエラーメッセージが表示される

---

## 参考資料

- [MCP Specification](https://spec.modelcontextprotocol.io/specification/)
- [Quarkus Documentation](https://quarkus.io/guides/)
- [RHCL 1.3.3 MCP Gateway 既知の問題](../../MCP_GATEWAY_ISSUE_RHCL1.3.3.md)
- [ESPN API 非公式ドキュメント](https://espnapi.com/)

---

**文書バージョン:** 1.0  
**最終更新:** 2026-06-11  
**承認者:** （記入してください）
