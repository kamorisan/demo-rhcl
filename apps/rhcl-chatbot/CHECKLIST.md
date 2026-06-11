# デプロイ前チェックリスト

## 事前準備

- [ ] OpenShiftクラスターへのアクセス権限
- [ ] `oc` CLIインストール済み
- [ ] OpenAI互換APIのAPIキー取得済み
- [ ] Gitリポジトリへのアクセス権限

## デプロイ手順

### 1. OpenShiftログイン

```bash
oc login --server=https://api.your-cluster.com:6443
```

- [ ] ログイン成功確認

### 2. 環境変数設定

```bash
export OPENAI_API_KEY=sk-xxxxx
export GIT_URL=https://github.com/kamorisan/demo-rhcl.git  # オプション
export GIT_BRANCH=investigate/mcp-sse-issue                # オプション
```

- [ ] OPENAI_API_KEY設定完了
- [ ] （オプション）GIT_URL設定（デフォルト: 現在のリポジトリURL）
- [ ] （オプション）GIT_BRANCH設定（デフォルト: 現在のブランチ）

### 3. デプロイ実行

```bash
cd apps/rhcl-chatbot
./deploy/deploy.sh
```

- [ ] スクリプト実行開始
- [ ] プロジェクト作成成功
- [ ] Secret作成成功
- [ ] イメージビルド成功
- [ ] アプリケーションデプロイ成功
- [ ] Route URL表示確認

### 4. 動作確認

```bash
# ステータス確認
./deploy/status.sh

# ログ確認
./deploy/logs.sh
```

- [ ] Pod が Running 状態
- [ ] Route URLにアクセス可能
- [ ] WebUIが正しく表示される
- [ ] チャット機能が動作する

### 5. 機能テスト

WebUIで以下のクエリをテスト：

```
今年のアーセナルの試合結果を教えて
```

- [ ] MCPツールが正しく呼ばれる（epl_scoreboard）
- [ ] チームフィルタリングが動作
- [ ] 日付範囲が正しい（今年）
- [ ] LLM応答が表示される
- [ ] 推論テキストが除去されている

## トラブルシューティング

### ビルド失敗時

```bash
# ビルドログ確認
oc logs -f bc/rhcl-chatbot -n rhcl-ai-bot

# Git URLとブランチ確認
oc get bc rhcl-chatbot -n rhcl-ai-bot -o yaml | grep -A 5 "source:"
```

- [ ] Git URLが正しい
- [ ] ブランチが正しい
- [ ] Dockerfileが存在する
- [ ] context-dirが正しい

### Pod起動失敗時

```bash
# Pod詳細確認
oc describe pod <pod-name> -n rhcl-ai-bot

# イベント確認
oc get events -n rhcl-ai-bot --sort-by='.lastTimestamp'
```

- [ ] イメージプル成功
- [ ] Secret存在確認
- [ ] リソース制限確認

### 接続エラー時

```bash
# MCP Gateway接続確認
oc rsh deployment/rhcl-chatbot -n rhcl-ai-bot
curl http://mcp.sandbox356.opentlc.com/mcp

# LLM API接続確認
curl -H "Authorization: Bearer $OPENAI_API_KEY" \
  https://litellm-prod.apps.maas.redhatworkshops.io/v1/models
```

- [ ] MCP Gatewayに接続可能
- [ ] LLM APIに接続可能
- [ ] APIキーが有効

## クリーンアップ

デプロイをやり直す場合：

```bash
./deploy/cleanup.sh
```

- [ ] 全リソース削除確認
- [ ] プロジェクト削除（オプション）

## 成功基準

- ✅ Pod が Running 状態で安定稼働
- ✅ Route URLでWebUIにアクセス可能
- ✅ チャット機能が正常動作
- ✅ 5スポーツすべてで試合情報取得可能
- ✅ チームフィルタリングが正常動作
- ✅ 日本語・英語両対応

## 参考情報

- **デプロイガイド**: [DEPLOY.md](DEPLOY.md)
- **クイックスタート**: [QUICKSTART.md](QUICKSTART.md)
- **全体ドキュメント**: [README.md](README.md)
