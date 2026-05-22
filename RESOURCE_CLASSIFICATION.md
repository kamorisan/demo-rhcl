# RHCL 分割アプリケーションのリソース分類

このドキュメントは、GitOps分割アプリケーションアーキテクチャにおける各Applicationのリソース構成を説明します。

## 概要

デプロイメントは7つの独立したArgoCD Applicationに分割され、sync-wave順に実行されます：

| Application | Sync Wave | 目的 |
|------------|-----------|------|
| App0 (Kuadrant Observability) | 0 | Kuadrantアップストリームオブザーバビリティ（Grafana Operator） |
| App1 (Operators) | 0 | オペレータ、RBAC、コンソールプラグイン自動有効化 |
| App2 (Platform CRs) | 2 | プラットフォームカスタムリソース |
| App3 (Gateways) | 3 | Gateway、DNSPolicy、TLSPolicy、Routes |
| App4 (Demo Apps) | 3 | デモアプリケーション |
| App5 (Core Observability) | 3 | Grafanaインスタンスとダッシュボード |
| App6 (Routes & Policies) | 4 | HTTPRoute、AuthPolicy、RateLimitPolicy |

---

## Application 0: Kuadrant Observability (wave 0)

Kuadrantアップストリームリポジトリから直接デプロイされるオブザーバビリティ基盤。

### ソース
- リポジトリ: `https://github.com/Kuadrant/kuadrant-operator`
- リビジョン: `v1.3.0`
- パス: `config/install/configure/observability`

### デプロイされるリソース
- **Grafana Operator**: Grafanaインスタンスを管理するオペレータ
- **基本設定**: Grafanaオペレータの初期設定

このApplicationはApp1と並行して実行され（両方ともwave 0）、後続のアプリケーションがGrafanaリソースを作成できるようにします。

---

## Application 1: Operators (wave 0)

オペレータのインストール、RBAC設定、コンソールプラグインの自動有効化を含みます。

### パス
`gitops/apps/rhcl-demo-split/app-1-operators/`

### リソース（sync-wave順）

#### Wave 0 - Namespaces
**ファイル**: `resources/01-namespaces.yaml`
- `kuadrant-system` - Kuadrantコアシステム
- `gateway-system` - Gateway APIコントローラー
- `istio-system` - Service Mesh（Istio）
- `openshift-ingress` - Ingressリソース
- `mcp-system` - MCP Gateway

#### Wave 1 - Service Mesh Operator
**ファイル**: `resources/02-servicemesh-operator.yaml`
- OperatorGroup: `openshift-operators`
- Subscription: `servicemeshoperator`（Service Mesh 3.x）

#### Wave 2 - RBAC
**ファイル**: `resources/04-rbac.yaml`
- ClusterRole: `gateway-admin`
- ClusterRoleBinding: Gateway管理者権限の割り当て

#### Wave 3 - Istio CR
**ファイル**: `resources/05-istio-cr.yaml`
- Istio Custom Resource（Service Meshコントロールプレーン）

#### Wave 4 - RHCL Operator
**ファイル**: `resources/06-rhcl-operator.yaml`
- OperatorGroup: `kuadrant-system`
- Subscription: `rhcl-operator`（Red Hat Connectivity Link）

#### Wave 5 - Kuadrant CR
**ファイル**: `resources/07-kuadrant-cr.yaml`
- Kuadrant Custom Resource（コアKuadrantインスタンス）

#### Wave 6 - MCP Gateway Operator
**ファイル**: `resources/08-mcp-gateway-operator.yaml`
- OperatorGroup: `mcp-system`
- Subscription: `mcp-gateway-operator`

#### Wave 7 - Red Hat Build of Keycloak Operator
**ファイル**: `resources/09-rhbk-operator.yaml`
- Subscription: `rhbk-operator`

#### Wave 9 - Console Banner
**ファイル**: `resources/10-console-banner.yaml`
- ConsoleNotification: デモ環境通知バナー

#### Wave 10 - Console Plugin自動有効化（PostSync Hook）
**ファイル**: `resources/11-console-plugin-patch.yaml`

PostSync Hookとして実行されるJob。Kuadrantコンソールプラグインを自動的に有効化します。

**含まれるリソース**:
- ServiceAccount: `console-plugin-patcher`
- ClusterRole: `console-plugin-patcher`（Console CRへのパッチ権限）
- ClusterRoleBinding: ServiceAccountへの権限バインディング
- Job: `enable-kuadrant-console-plugin`
  - 現在のプラグインリストを取得
  - `kuadrant-console-plugin`が存在するか確認
  - 存在しない場合、既存プラグインを保持しながら追加
  - `console.operator.openshift.io`リソースにパッチ適用

**アノテーション**:
```yaml
argocd.argoproj.io/hook: PostSync
argocd.argoproj.io/hook-delete-policy: BeforeHookCreation
argocd.argoproj.io/sync-wave: "10"
```

**動作**:
1. App1のメインリソースがデプロイ完了後に実行
2. 既存のコンソールプラグイン（monitoring-plugin等）を保持
3. `kuadrant-console-plugin`を配列に追加
4. 冪等性：既に有効化されている場合はスキップ

---

## Application 2: Platform CRs (wave 2)

プラットフォームレベルのカスタムリソース。

### パス
`gitops/apps/rhcl-demo-split/app-2-platform-crs/`

### リソース

#### Wave 0 - Namespaces
**ファイル**: `resources/01-namespaces.yaml`
- `demo` - メインデモアプリケーション
- `demo-ab` - A/Bテストデモ
- `demo-jwt` - JWTデモ
- `rhcl-ai-bot` - AIチャットボット
- `rhcl-keycloak` - Keycloakアイデンティティプロバイダ
- `rhcl-oidc-portal` - OIDCポータルデモ

#### Wave 1 - ClusterIssuer
**ファイル**: `resources/02-clusterissuer.yaml`
- ClusterIssuer: cert-manager用の証明書発行者（環境に応じて設定）

#### Wave 2 - RBAC
**ファイル**: `resources/03-rbac.yaml`
- RoleBinding: `mcp-system`が`rhcl-ai-bot`イメージをPullできるよう権限付与

---

## Application 3: Gateways (wave 3)

Gateway、DNSPolicy、TLSPolicy、Routeの設定。

### パス
`gitops/apps/rhcl-demo-split/app-3-gateways/`

### リソース

#### Wave 0 - Gateways
**ファイル**: `resources/02-gateways.yaml`
- Gateway: `rhcl-workshop-gw`（メインGateway、`openshift-ingress`）
- Gateway: `rhcl-ai-gw`（AIボット専用Gateway、`openshift-ingress`）
- Gateway: `rhcl-external-gw`（外部API公開Gateway、`openshift-ingress`）
- Gateway: `rhcl-mcp-gw`（MCP Gateway、`openshift-ingress`）

#### Wave 1 - DNSPolicy
**ファイル**: `resources/03-dnspolicy.yaml`
- DNSPolicy: 各GatewayのDNS自動設定（Route53対応）

#### Wave 2 - TLSPolicy
**ファイル**: `resources/04-tls-policies.yaml`
- TLSPolicy: 各Gatewayリスナーの証明書自動管理

#### Wave 3 - Routes
**ファイル**: `resources/05-routes.yaml`
- Route: `rhcl-workshop`（パススルー、メインGatewayへ）
- Route: `rhcl-ai`（パススルー、AI Gatewayへ）
- Route: `rhcl-external`（パススルー、外部API Gatewayへ）
- Route: `rhcl-mcp`（パススルー、MCP Gatewayへ）
- Route: `rhcl-oidc-workshop`（パススルー、OIDCポータルへ）
- Route: `http-to-https-redirect`（HTTPからHTTPSへのリダイレクト）

#### Wave 4 - ReferenceGrants
**ファイル**: `resources/06-referencegrants.yaml`
- ReferenceGrant: クロスNamespace参照の許可設定

#### Wave 5 - Telemetry
**ファイル**: `resources/07-telemetry.yaml`
- Telemetry: Istio/Gateway メトリクス設定

#### Wave 10 - MCP Gateway AuthPolicy
**ファイル**: `resources/mcp-gateway-authpolicy.yaml`
- AuthPolicy: MCP Gateway全体のデフォルト拒否ポリシー

---

## Application 4: Demo Apps (wave 3)

デモアプリケーションのDeployment、Service、ConfigMap等。

### パス
`gitops/apps/rhcl-demo-split/app-4-demo-apps/`

### リソース

すべてのリソースはwave 0でデプロイされます（App4全体がwave 3として実行）。

#### A/Bカナリアデモ
**ファイル**: `resources/ab-canary.yaml`
- ConfigMap: `ab-canary-v1-html`、`ab-canary-v2-html`
- Deployment: `ab-canary-v1`、`ab-canary-v2`
- Service: `ab-canary-v1`、`ab-canary-v2`

#### AIボット
**ファイル**: `resources/ai-bot.yaml`
- ImageStream: `rhcl-ai-bot`
- BuildConfig: `rhcl-ai-bot`（Dockerビルド）
- Deployment: `rhcl-ai-bot`
- Service: `rhcl-ai-bot`

#### デモAPI
**ファイル**: `resources/demo-api.yaml`
- Deployment: `demo-api`
- Service: `demo-api`

#### 外部プロキシ（ESPN API）
**ファイル**: `resources/external-proxy.yaml`
- ConfigMap: `external-proxy-nginx-conf`
- Deployment: `external-proxy`
- Service: `external-proxy`、`external-proxy-internal`

#### Keycloak
**ファイル**: `resources/keycloak.yaml`
- Secret: `keycloak-admin-credentials`
- PersistentVolumeClaim: `keycloak-data`
- Deployment: `keycloak`
- Service: `keycloak`

#### OIDCコールバック
**ファイル**: `resources/oidc-callback-configmap.yaml`、`resources/oidc-callback.yaml`
- ConfigMap: `oidc-callback-js`（Node.jsコールバックサーバー）
- Deployment: `oidc-callback`
- Service: `oidc-callback`

#### OIDC UI
**ファイル**: `resources/oidc-ui-configmap.yaml`、`resources/oidc-ui.yaml`
- ConfigMap: `oidc-ui-html`（静的HTML UI）
- Deployment: `oidc-ui`
- Service: `oidc-ui`

#### メインUI
**ファイル**: `resources/ui-configmap.yaml`、`resources/ui.yaml`
- ConfigMap: `rhcl-ui-html`（メインデモUI）
- Deployment: `rhcl-ui`
- Service: `rhcl-ui`

---

## Application 5: Core Observability (wave 3)

Grafanaインスタンスとダッシュボードの設定。

### パス
`gitops/apps/rhcl-demo-split/app-5-core-observability/`

### リソース

#### Wave 0 - Grafana設定
**ファイル**: `resources/05-observability-and-grafana.yaml`

含まれるリソース:
- **ServiceAccount**: `grafana-sa`（Prometheusトークンアクセス用）
- **ClusterRole**: `grafana-prometheus-reader`
- **ClusterRoleBinding**: ServiceAccountへの権限バインディング
- **Secret**: `grafana-thanos-token`（Prometheusアクセストークン）
- **Grafana**: `grafana-instance`
  - 匿名アクセス有効
  - デフォルトロール: Admin
  - Prometheusデータソース設定
- **GrafanaDatasource**: `prometheus-grafanadatasource`
  - OpenShift User Workload Monitoringへの接続
  - ServiceAccountトークン認証

#### Wave 1 - Grafanaダッシュボード
**ファイル**: `resources/06-grafana-dashboard-configmaps.yaml`

含まれるダッシュボード:
- **開発者ダッシュボード**: HTTPコード別リクエスト、レイテンシパーセンタイル（P90/P95/P99）
- **プラットフォームダッシュボード**: トップサービス、SRE視点のメトリクス
- **ビジネスダッシュボード**: トラフィックサマリー、総リクエスト数

各ダッシュボードはConfigMapとして定義され、Grafanaインスタンスに自動インポートされます。

---

## Application 6: Routes & Policies (wave 4)

HTTPRoute、AuthPolicy、RateLimitPolicy、TokenRateLimitPolicyの設定。

### パス
`gitops/apps/rhcl-demo-split/app-6-routes-policies/`

### リソース

すべてのリソースはwave 0でデプロイされます（App6全体がwave 4として実行）。

#### HTTPRoutes
- `resources/rhcl-ui.yaml` - メインUI HTTPRoute
- `resources/oidc-portal.yaml` - OIDCポータル HTTPRoute
- `resources/demo-api.yaml` - デモAPI HTTPRoute
- `resources/secure-demo.yaml` - セキュアデモ HTTPRoute
- `resources/keycloak.yaml` - Keycloak HTTPRoute
- `resources/oidc-portal-callback.yaml` - OIDCコールバック HTTPRoute
- `resources/oidc-portal-whoami.yaml` - OIDCアイデンティティ HTTPRoute
- `resources/ai-bot-main.yaml` - AIボットメイン HTTPRoute
- `resources/ai-bot-ai.yaml` - AIボット専用 HTTPRoute（ai.ドメイン）
- `resources/rhcl-mcp-gateway.yaml` - MCP Gateway HTTPRoute
- `resources/ai-bot-mcp-server.yaml` - AIボット用MCPサーバー HTTPRoute
- `resources/external-proxy.yaml` - 内部外部プロキシ HTTPRoute
- `resources/external-proxy-public.yaml` - 公開外部プロキシ HTTPRoute
- `resources/ab-demo.yaml` - A/Bテストデモ HTTPRoute
- `resources/jwt-demo.yaml` - JWTデモ HTTPRoute
- `resources/http-to-https.yaml` - HTTPからHTTPSリダイレクト HTTPRoute

#### AuthPolicy
- `resources/gateway-authpolicy.yaml` - Gateway全体のデフォルト拒否
- `resources/rhcl-gw-auth.yaml` - メインGateway認証ポリシー
- `resources/ab-authpolicy.yaml` - A/Bデモ認証
- `resources/ai-bot-main-authpolicy.yaml` - AIボットメイン認証
- `resources/ai-bot-mcp-authpolicy.yaml` - AIボットMCP認証
- `resources/external-proxy-authpolicy.yaml` - 外部プロキシ認証
- `resources/jwt-demo-jwt.yaml` - JWTデモJWT検証
- `resources/secure-demo-jwt.yaml` - セキュアデモJWT検証

#### OIDCPolicy
- `resources/secure-demo-oidc.yaml` - OIDCブラウザログインポリシー

#### RateLimitPolicy
- `resources/demo-api-ratelimit.yaml` - デモAPIレート制限（アイデンティティベース）

#### TokenRateLimitPolicy
- `resources/ai-bot-ai-tokenratelimitpolicy.yaml` - AIボット専用ドメイントークンレート制限
- `resources/ai-bot-main-tokenratelimitpolicy.yaml` - AIボットメインドメイントークンレート制限

#### Secrets
- `resources/api-keys.yaml` - APIキーシークレット（`IAMALICE`、`IAMBOB`）

#### MCP関連
- `resources/ai-bot-mcp-httproute.yaml` - AIボット用MCP HTTPRoute
- `resources/ai-bot-mcpserverregistration.yaml` - MCPサーバー登録
- `resources/rhcl-mcp-tools-internal.yaml` - 内部MCPツール

#### MCPGatewayExtension
- `resources/rhcl-mcp.yaml` - MCP Gateway拡張設定

---

## Kustomizeパッチ

各Application manifestは、Kustomizeパッチを使用して環境固有の値（ホスト名、Gateway名等）を動的に注入します。

### パッチ対象
- **HTTPRoute**: `spec.hostnames`、`spec.parentRefs[].name`
- **MCPGatewayExtension**: `spec.publicHost`
- **OIDCPolicy**: `spec.provider.issuerURL`、`spec.provider.authorizationEndpoint`、`spec.provider.tokenEndpoint`、`spec.provider.redirectURI`
- **AuthPolicy（JWT）**: `spec.defaults.rules.authentication.jwt.jwt.issuerUrl`

パッチは`rhcl-app-6-routes-policies.yaml` Applicationマニフェスト内で定義されます。

---

## Sync Wave戦略

分割アプリケーションアーキテクチャは、以下のsync-wave戦略を使用：

1. **Wave 0** (並行実行):
   - App0: Grafana Operatorインストール
   - App1: 全オペレータインストール + RBAC

2. **Wave 2**:
   - App2: プラットフォームCR（Namespaces、ClusterIssuer）

3. **Wave 3** (並行実行):
   - App3: Gateways + DNSPolicy + TLSPolicy
   - App4: デモアプリケーション
   - App5: Grafanaインスタンス + ダッシュボード

4. **Wave 4**:
   - App6: HTTPRoutes + Policies（App3-5完了後）

この戦略により、依存関係を尊重しながら可能な限り並行デプロイを実現し、全体のデプロイ時間を短縮します。

---

## 自動化されたSync再試行

すべてのApplicationは以下の自動Syncポリシーを持ちます：

```yaml
syncPolicy:
  automated:
    prune: true
    selfHeal: true
  retry:
    limit: 10
    backoff:
      duration: 30s
      factor: 2
      maxDuration: 10m
```

これにより、一時的な失敗（リソース作成中のタイミング問題等）は自動的に再試行され、手動介入が不要になります。

---

## 注意事項

- すべてのファイルパスは`gitops/apps/rhcl-demo-split/`を基準としています
- 各Applicationは独立してデプロイ可能（依存関係を尊重する限り）
- Kustomizeパッチは環境固有の値を動的に注入するため、同じマニフェストを複数環境で再利用可能
- Console Plugin自動有効化により、手動でのプラグイン有効化が不要
