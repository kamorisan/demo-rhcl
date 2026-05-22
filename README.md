# Red Hat Connectivity Link (RHCL) デモワークショップ — GitOps対応

このリポジトリは、**OpenShift 4.20** 上で **Red Hat Connectivity Link (Kuadrant)** が **Gateway API** トラフィックにポリシーを適用する方法を示す、**ライブデモ対応**のワークショップです。

目的はシンプルです：1つのUI、いくつかのモジュール、各モジュールは**1分以内**で説明できる**1つのポリシーのメリット**を強調します。

## 機能（モジュール、ポリシー、メリット）

### ポリシープレイグラウンド（APIキー + レート制限）
- **ポリシー**: `AuthPolicy`（APIキー）、`RateLimitPolicy`（アイデンティティベース）、Gateway上の`AuthPolicy`（デフォルト拒否）
- **メリット**: **ゼロトラストをデフォルト**で実装し、チームが必要なものだけを安全に公開できることを実演。**アイデンティティごとの制限**（例：BobはAliceより早く`429`に到達）

### トラフィックシェイピング（A/B + カナリア）
- **ポリシー/オブジェクト**: `HTTPRoute`の重み付けバックエンド
- **メリット**: アプリケーションコードを変更せずにプログレッシブデリバリー
  - **A/B**: ランダム分割 **80/20** (`/ab`)
  - **カナリア**: ロールアウト分割 **90/10** (`/canary`)
  - **カナリアオプトイン**: ヘッダー `x-rhcl-canary: always` → **強制100% v2**（「内部テスター」シナリオに最適）

### 外部API（ESPNプロキシ）
- **オブジェクト**: `Gateway`リスナー + `DNSPolicy`（Route53）+ `Certificate` SANs + `HTTPRoute` + `AuthPolicy`
- **メリット**: サードパーティAPIをGateway経由で**専用ホスト名**（`external-api.<domain>`）で公開し、データがクラスタ外から来る場合でも同じポリシー（認証/レート制限/トラフィック）を適用可能

### オブザーバビリティ（Grafana + OpenShiftコンソールグラフ）
- **オブジェクト**: OpenShift **ユーザーワークロード監視**、Kuadrant `ServiceMonitor`/`PodMonitor`、**Grafana**（匿名）、事前ロード済みダッシュボード
- **メリット**: UIでデモする*同じエンドポイント*の**使用状況、エラー、レイテンシ**を表示：
  - **開発者**: HTTPコード別リクエスト数（200/401/403/404/429/5xx）+ レイテンシパーセンタイル（P90/P95/P99）
  - **プラットフォーム**: トップサービス、コード別リクエスト、レイテンシ（シンプルな「SRE視点」）
  - **ビジネス**: トラフィックサマリー + 選択期間の総リクエスト数
- メインUIは**Grafanaダッシュボード**に焦点を当て、迅速でオーディエンスフレンドリーなオブザーバビリティを提供

### AIチャットボット（ESPNツール + トークン予算）
- **ポリシー**: `TokenRateLimitPolicy`
- **メリット**: **トークン使用量**に基づくレート制限（単なるリクエスト数ではない）、LLMコストと不正使用防止に適合
  - デモ予算は意図的に小さく設定（現在**400トークン / 15秒**）、`429`を確実にトリガー可能
  - チャット補完は**専用AIホスト名**（`ai.<base-domain>`）で実行されるため、`TokenRateLimitPolicy`（およびトラフィック分析）をAI Gatewayにスコープ可能
  - ボットが外部データを必要とする場合、**RHCL MCP Gateway**（`mcp.<base-domain>`）経由でツールを呼び出し
  - UIは同一オリジンプロキシ（`/ai/mcp/*`）経由でMCPツールヘルパーを呼び出すため、ブラウザはMCPホスト名へのクロスオリジンリクエストが不要
  - MCP Gatewayのインストールについては、[MCP Gatewayのインストール](https://docs.redhat.com/en/documentation/red_hat_connectivity_link/1.3/html/installing_the_mcp_gateway/mcp-gateway-install)を参照

### AuthPolicy（Keycloak JWT）— トークン取得、デコード、有無での呼び出し
- **ポリシー**: `AuthPolicy`（JWT検証）
- **メリット**: Gatewayが**KeycloakによるJWT**を検証し、バックエンドコードを変更せずにAPIアクセスを強制
  - UIはトークンを取得、表示/デコードし、保護されたエンドポイントをトークンの**有無で**呼び出し可能

### OIDCポータル（別ホスト名、ブラウザログイン）
- **ポリシー**: `OIDCPolicy`（ブラウザログイン）、`DNSPolicy`（オプションのRoute53レコード）
- **メリット**: クリーンな「Gateway経由のブラウザログイン」シナリオ：
  - 未認証 → **Keycloakへ302リダイレクト**
  - 認証済み → Cookieセット → ポータルは保護されたコンテンツを取得可能

## アーキテクチャ（概要）

- **OpenShift Route（パススルー）** → **Gateway**（`Gateway API`）
- **Connectivity Link / Kuadrant**が以下にポリシーを適用：
  - **Gateway**（デフォルト拒否などのガードレール）
  - 個別の**HTTPRoutes**（認証、レート制限、トラフィックシェイピング）
- TLSは`cert-manager`とGatewayリスナーSecretsで処理
  - このリポジトリは以下の両方をサポート：
    - **直接`Certificate`オブジェクト**（リスナーが参照する明示的なシークレット）
    - **Kuadrant `TLSPolicy`**（推奨）特定のリスナー/ホスト名のcert-manager Certificatesを管理

### このリポジトリのConnectivity Link変更（デモ配線）

- **外部API公開ホスト名**：
  - `Gateway`リスナー: `https-external-public` → `external-api.<base-domain>`
  - `DNSPolicy`: `external-api.<base-domain>`のRoute53レコードを作成/更新
  - `Certificate`: `rhcl-gw-public-tls`に`external-api.<base-domain>`をSANとして含む
  - `HTTPRoute`: `demo/external-proxy-public`がクリーンなパスを公開：
    - `GET /nba`、`/epl`、`/laliga`、`/nfl`、`/nhl`
  - `AuthPolicy`: `demo/external-proxy-public-allow`（ルートレベル許可）でデフォルト拒否をブロックしない

- **MCPツール + UI/ボットアクセス（環境間で安定）**：
  - `Gateway`: `openshift-ingress/rhcl-mcp-gw`が専用MCPホスト名`mcp.<base-domain>`を公開
  - `HTTPRoute`: `mcp-system/rhcl-mcp-gateway`がそのホスト名で`/mcp`を公開
  - `AuthPolicy`: `openshift-ingress/rhcl-mcp-gw-auth`（拒否全て）+ `mcp-system/rhcl-mcp-allow`（`/mcp`を許可）
  - ボットは`https://mcp.<base-domain>/mcp`経由でツールを呼び出し、ツールは`https://external-api.<base-domain>`経由でESPNを消費

> 注意: NGINXは静的UI提供/上流JSONプロキシの実装詳細に過ぎません。ワークショップの価値は**Gateway API + Kuadrantポリシー**の配線であり、Webサーバーの選択ではありません。

## 前提条件

- **cluster-admin**でクラスタにログイン済み
- `oc` CLI利用可能
- `python3`利用可能
- OpenShift GitOpsは**自動的にインストール**されます（インストーラーによって、存在しない場合）
- クラスタに動作する`ClusterIssuer`（インストーラーが自動選択可能、または明示的に設定）

## インストール（推奨）

### インストーラー（setup-and-install.sh）

実行:

```bash
./setup-and-install.sh
```

オプションの環境変数で`GIT_BRANCH`を指定可能（デフォルト: `main`）：

```bash
GIT_BRANCH=feature/my-branch ./setup-and-install.sh
```

デフォルト:
- **メインデモホスト**: `rhcl-workshop.<appsDomain>`
- **OIDCポータルホスト**: `oidc-rhcl-workshop.<appsDomain>`
- **Grafanaホスト**: `grafana.<appsDomain>`（匿名）

便利なオーバーライド:
- **`APPS_DOMAIN`**: クラスタappsドメインを設定（自動検出が失敗した場合）
- **`DEMO_HOSTNAME`**: メインホスト名を設定
- **`OIDC_HOSTNAME`**: OIDCポータルホスト名を設定
- **`GRAFANA_HOSTNAME`**: Grafanaホスト名を設定（オプション）
- **`CLUSTER_ISSUER`**: cert-manager `ClusterIssuer`名を設定
- **`DEFAULT_INGRESS_CERT_SECRET`**: `*.appsDomain`リスナー用のワイルドカード証明書シークレットを設定
- **`EXTERNAL_BASE_DOMAIN`**: 専用公開ホスト用のベースドメイン（デフォルト: `APPS_DOMAIN`の末尾2-3ラベル）
- **`EXTERNAL_API_HOSTNAME`**: 外部APIホスト名をオーバーライド（デフォルト: `external-api.<EXTERNAL_BASE_DOMAIN>`）

### オプション: Route53 DNS自動化（DNSPolicy）

`DNSPolicy`にRoute53レコードを管理させたい場合:
- **AWS CLIがインストール済み**で、環境が既に設定されており`aws sts get-caller-identity`が動作することを確認
- エクスポート:

```bash
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AWS_REGION=...
```

その後インストーラーを再実行。AWS認証を検証し、`openshift-ingress`（および`demo`）に`Secret/route53-credentials`（タイプ`kuadrant.io/aws`）を作成/更新します。

## インストール後: Connectivity Linkコンソールプラグインの有効化

Connectivity Linkオペレータは、OpenShiftコンソール動的プラグインをインストールしますが、デフォルトで無効化されている場合があります。

このワークショップには、同期中に`kuadrant-console-plugin`を**自動的に有効化**するGitOps Jobが含まれています。

### 手動で有効化する必要がある場合
- OpenShiftコンソール → **管理者** → **ホーム → 概要**
- **動的プラグイン → すべて表示**
- **`kuadrant-console-plugin`**を有効化
- コンソールを更新 → 左側ナビに**Connectivity Link**が表示されるはず

詳細は、[Connectivity Link動的プラグインの有効化](https://docs.redhat.com/en/documentation/red_hat_connectivity_link/1.0/html/installing_connectivity_link_on_openshift/enable-openshift-dynamic-plugin_connectivity-link)を参照。

## AIボットの設定（オプション）

AIボットはOpenAI互換エンドポイント（LiteLLM）とモデルを使用します。

### APIキーの設定（GitOpsフレンドリー）

`rhcl-ai-bot`にシークレットを作成:

```bash
oc -n rhcl-ai-bot create secret generic rhcl-ai-bot-llm \
  --from-literal=apiKey='YOUR_KEY' \
  --dry-run=client -o yaml | oc apply -f -
```

### インストーラー経由（推奨）

`./setup-and-install.sh`を実行する前に環境変数をエクスポート:

```bash
export RHCL_AI_OPENAI_API_KEY='YOUR_KEY'
./setup-and-install.sh
```

### 直接環境変数（テスト用クイック方法）

```bash
oc -n rhcl-ai-bot set env deployment/rhcl-ai-bot RHCL_AI_OPENAI_API_KEY='YOUR_KEY'
```

## URL

- メインUI: `https://<DEMO_HOSTNAME>/`
- OIDCポータル: `https://<OIDC_HOSTNAME>/`
- Grafana（匿名）: `https://<GRAFANA_HOSTNAME>/`
- 外部APIホスト名: `https://external-api.<appsDomain>/epl`（他に`/nba`、`/laliga`、`/nfl`、`/nhl`）

## 推奨ライブデモフロー（5〜8分）

### 1) ゼロトラストベースライン + APIキー
- キーなしで`GET /hello`を呼び出し → **401/403**を期待
- APIキーを追加（`IAMALICE`、`IAMBOB`）→ **200**を期待
- Bobをクリックし続ける → より早く**429**に到達（アイデンティティベース`RateLimitPolicy`）

### 2) トラフィックシェイピング
- A/Bサンプル → 観測された**80/20**を表示
- カナリアサンプル → 観測された**90/10**を表示
- カナリア強制（ヘッダー）→ **0/100**（v2のみ）を表示

### 3) Keycloak JWT
- **トークン取得**をクリック → JWTとデコードされたクレームを表示
- トークンなしで保護されたAPIを呼び出し → **401**
- トークンありで呼び出し → **200**

### 4) OIDCポータル
- ポータルを開く（別ホスト名）→ 未認証で**302**からKeycloakへ
- ログイン → ポータルが保護されたコンテンツを表示

### 5) AIボット + トークン予算
- 質問をする → `usage.total_tokens`を表示
- 「Hit 429」を実行 → `TokenRateLimitPolicy`による`429`を表示

### 6) オブザーバビリティ（高速、オーディエンスフレンドリー）
- メインUIで → **オブザーバビリティ**:
  - **サンプルトラフィック生成**をクリック（短いバーストを作成し、グラフがすぐに動く）
  - **開発者 / プラットフォーム / ビジネス**ダッシュボードを開いて更新

## GitOpsレイアウト

このリポジトリは**分割アプリケーションアーキテクチャ**を使用し、7つの独立したArgoCD Applicationとして展開されます：

### Application構成（sync-wave順）

- **App0** (`rhcl-app-0-kuadrant-observability`, wave 0): Kuadrantアップストリームオブザーバビリティ（Grafana Operator + 基本設定）
- **App1** (`rhcl-app-1-operators`, wave 0): オペレータ + RBAC + コンソールプラグイン自動有効化
- **App2** (`rhcl-app-2-platform-crs`, wave 2): プラットフォームカスタムリソース（Namespaces、ClusterIssuer、RBAC）
- **App3** (`rhcl-app-3-gateways`, wave 3): Gateways（Gateway、DNSPolicy、TLSPolicy、Routes）
- **App4** (`rhcl-app-4-demo-apps`, wave 3): デモアプリケーション
- **App5** (`rhcl-app-5-core-observability`, wave 3): コアオブザーバビリティ（Grafanaインスタンス + ダッシュボード）
- **App6** (`rhcl-app-6-routes-policies`, wave 4): ルートとポリシー（HTTPRoutes、AuthPolicy、RateLimitPolicy）

### パス構造

```
gitops/apps/rhcl-demo-split/
├── rhcl-app-0-kuadrant-observability.yaml  # Application manifest
├── rhcl-app-1-operators.yaml               # Application manifest
├── rhcl-app-2-platform-crs.yaml            # Application manifest
├── rhcl-app-3-gateways.yaml                # Application manifest
├── rhcl-app-4-demo-apps.yaml               # Application manifest
├── rhcl-app-5-core-observability.yaml      # Application manifest
├── rhcl-app-6-routes-policies.yaml         # Application manifest
├── app-1-operators/                        # App1リソース
├── app-2-platform-crs/                     # App2リソース
├── app-3-gateways/                         # App3リソース
├── app-4-demo-apps/                        # App4リソース
├── app-5-core-observability/               # App5リソース
└── app-6-routes-policies/                  # App6リソース
```

### Ansibleプレイブック

- `ansible/playbooks/setup-gitops.yaml`: OpenShift GitOpsセットアップ
- `ansible/playbooks/install-split-apps.yaml`: 7つのApplicationを順次デプロイ

詳細なリソース分類については、[RESOURCE_CLASSIFICATION.md](RESOURCE_CLASSIFICATION.md)を参照してください。

## 注意事項（なぜダッシュボードが機能するか）

- **メトリクスソース**: ダッシュボードはPrometheus（OpenShift監視 + ユーザーワークロード監視）とConnectivity Link / Kuadrantメトリクスを使用
- **トレーシング（オプション）**: 分散トレーシングはコアワークショップには不要ですが、有効にすると、Gateway レベルのトレースをポリシー適用レスポンス（例：401/403/429）について検査可能（これらのレスポンスはGatewayで生成されるため）

## 参考資料

- Red Hat Connectivity Linkインストール: [Installing Connectivity Link on OpenShift](https://docs.redhat.com/en/documentation/red_hat_connectivity_link/1.3/html-single/installing_on_openshift_container_platform/index)
- MCP Gateway: [Installing the MCP Gateway](https://docs.redhat.com/en/documentation/red_hat_connectivity_link/1.3/html/installing_the_mcp_gateway/mcp-gateway-install)
- ポリシー: [Configuring and deploying Gateway policies](https://docs.redhat.com/en/documentation/red_hat_connectivity_link/1.3/html-single/configuring_and_deploying_gateway_policies/configuring_and_deploying_gateway_policies)
- オブザーバビリティ: [Observability and Troubleshooting](https://docs.redhat.com/en/documentation/red_hat_connectivity_link/1.3/html/observability_and_troubleshooting/index)
