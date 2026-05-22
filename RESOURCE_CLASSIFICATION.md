# RHCL Split Application Resource分類

このドキュメントは、GitOps split application architectureにおける各Applicationのresource構成を説明します。

## 概要

Deploymentは7つの独立したArgoCD Applicationに分割され、sync-wave順に実行されます：

| Application | Sync Wave | 目的 |
|------------|-----------|------|
| App0 (Kuadrant Observability) | 0 | Kuadrant upstream observability（Grafana Operator） |
| App1 (Operators) | 0 | Operators、RBAC、console plugin自動有効化 |
| App2 (Platform CRs) | 2 | Platform Custom Resources |
| App3 (Gateways) | 3 | Gateway、DNSPolicy、TLSPolicy、Routes |
| App4 (Demo Apps) | 3 | Demo applications |
| App5 (Core Observability) | 3 | Grafana instance + dashboards |
| App6 (Routes & Policies) | 4 | HTTPRoute、AuthPolicy、RateLimitPolicy |

---

## Application 0: Kuadrant Observability (wave 0)

Kuadrant upstream repositoryから直接deployされるobservability基盤。

### Source
- Repository: `https://github.com/Kuadrant/kuadrant-operator`
- Revision: `v1.3.0`
- Path: `config/install/configure/observability`

### Deployされるresources
- **Grafana Operator**: Grafana instancesを管理するoperator
- **基本設定**: Grafana operatorの初期設定

このApplicationはApp1と並行して実行され（両方ともwave 0）、後続のapplicationsがGrafana resourcesを作成できるようにします。

---

## Application 1: Operators (wave 0)

Operatorsのinstall、RBAC設定、console pluginの自動有効化を含みます。

### Path
`gitops/apps/rhcl-demo-split/app-1-operators/`

### Resources（sync-wave順）

#### Wave 0 - Namespaces
**File**: `resources/01-namespaces.yaml`
- `kuadrant-system` - Kuadrant core system
- `gateway-system` - Gateway API controller
- `istio-system` - Service Mesh（Istio）
- `openshift-ingress` - Ingress resources
- `mcp-system` - MCP Gateway

#### Wave 1 - Service Mesh Operator
**File**: `resources/02-servicemesh-operator.yaml`
- OperatorGroup: `openshift-operators`
- Subscription: `servicemeshoperator`（Service Mesh 3.x）

#### Wave 2 - RBAC
**File**: `resources/04-rbac.yaml`
- ClusterRole: `gateway-admin`
- ClusterRoleBinding: Gateway管理者権限の割り当て

#### Wave 3 - Istio CR
**File**: `resources/05-istio-cr.yaml`
- Istio Custom Resource（Service Mesh control plane）

#### Wave 4 - RHCL Operator
**File**: `resources/06-rhcl-operator.yaml`
- OperatorGroup: `kuadrant-system`
- Subscription: `rhcl-operator`（Red Hat Connectivity Link）

#### Wave 5 - Kuadrant CR
**File**: `resources/07-kuadrant-cr.yaml`
- Kuadrant Custom Resource（core Kuadrant instance）

#### Wave 6 - MCP Gateway Operator
**File**: `resources/08-mcp-gateway-operator.yaml`
- OperatorGroup: `mcp-system`
- Subscription: `mcp-gateway-operator`

#### Wave 7 - Red Hat Build of Keycloak Operator
**File**: `resources/09-rhbk-operator.yaml`
- Subscription: `rhbk-operator`

#### Wave 9 - Console Banner
**File**: `resources/10-console-banner.yaml`
- ConsoleNotification: Demo環境通知banner

#### Wave 10 - Console Plugin自動有効化（PostSync Hook）
**File**: `resources/11-console-plugin-patch.yaml`

PostSync Hookとして実行されるJob。Kuadrant console pluginを自動的に有効化します。

**含まれるresources**:
- ServiceAccount: `console-plugin-patcher`
- ClusterRole: `console-plugin-patcher`（Console CRへのpatch権限）
- ClusterRoleBinding: ServiceAccountへの権限binding
- Job: `enable-kuadrant-console-plugin`
  - 現在のplugin listを取得
  - `kuadrant-console-plugin`が存在するか確認
  - 存在しない場合、既存pluginsを保持しながら追加
  - `console.operator.openshift.io` resourceにpatch適用

**Annotations**:
```yaml
argocd.argoproj.io/hook: PostSync
argocd.argoproj.io/hook-delete-policy: BeforeHookCreation
argocd.argoproj.io/sync-wave: "10"
```

**動作**:
1. App1のmain resourcesがdeploy完了後に実行
2. 既存のconsole plugins（monitoring-plugin等）を保持
3. `kuadrant-console-plugin`を配列に追加
4. Idempotency：既に有効化されている場合はskip

---

## Application 2: Platform CRs (wave 2)

Platform levelのCustom Resources。

### Path
`gitops/apps/rhcl-demo-split/app-2-platform-crs/`

### Resources

#### Wave 0 - Namespaces
**File**: `resources/01-namespaces.yaml`
- `demo` - メインdemo application
- `demo-ab` - A/B test demo
- `demo-jwt` - JWT demo
- `rhcl-ai-bot` - AI chatbot
- `rhcl-keycloak` - Keycloak identity provider
- `rhcl-oidc-portal` - OIDC portal demo

#### Wave 1 - ClusterIssuer
**File**: `resources/02-clusterissuer.yaml`
- ClusterIssuer: cert-manager用のcertificate issuer（環境に応じて設定）

#### Wave 2 - RBAC
**File**: `resources/03-rbac.yaml`
- RoleBinding: `mcp-system`が`rhcl-ai-bot` imageをpullできるよう権限付与

---

## Application 3: Gateways (wave 3)

Gateway、DNSPolicy、TLSPolicy、Routeの設定。

### Path
`gitops/apps/rhcl-demo-split/app-3-gateways/`

### Resources

#### Wave 0 - Gateways
**File**: `resources/02-gateways.yaml`
- Gateway: `rhcl-workshop-gw`（メインGateway、`openshift-ingress`）
- Gateway: `rhcl-ai-gw`（AI bot専用Gateway、`openshift-ingress`）
- Gateway: `rhcl-external-gw`（外部API公開Gateway、`openshift-ingress`）
- Gateway: `rhcl-mcp-gw`（MCP Gateway、`openshift-ingress`）

#### Wave 1 - DNSPolicy
**File**: `resources/03-dnspolicy.yaml`
- DNSPolicy: 各GatewayのDNS自動設定（Route53対応）

#### Wave 2 - TLSPolicy
**File**: `resources/04-tls-policies.yaml`
- TLSPolicy: 各Gateway listenerのcertificate自動管理

#### Wave 3 - Routes
**File**: `resources/05-routes.yaml`
- Route: `rhcl-workshop`（passthrough、メインGatewayへ）
- Route: `rhcl-ai`（passthrough、AI Gatewayへ）
- Route: `rhcl-external`（passthrough、外部API Gatewayへ）
- Route: `rhcl-mcp`（passthrough、MCP Gatewayへ）
- Route: `rhcl-oidc-workshop`（passthrough、OIDC portalへ）
- Route: `http-to-https-redirect`（HTTPからHTTPSへのredirect）

#### Wave 4 - ReferenceGrants
**File**: `resources/06-referencegrants.yaml`
- ReferenceGrant: Cross-namespace参照の許可設定

#### Wave 5 - Telemetry
**File**: `resources/07-telemetry.yaml`
- Telemetry: Istio/Gateway metrics設定

#### Wave 10 - MCP Gateway AuthPolicy
**File**: `resources/mcp-gateway-authpolicy.yaml`
- AuthPolicy: MCP Gateway全体のdefault deny policy

---

## Application 4: Demo Apps (wave 3)

Demo applicationsのDeployment、Service、ConfigMap等。

### Path
`gitops/apps/rhcl-demo-split/app-4-demo-apps/`

### Resources

すべてのresourcesはwave 0でdeployされます（App4全体がwave 3として実行）。

#### A/B Canary Demo
**File**: `resources/ab-canary.yaml`
- ConfigMap: `ab-canary-v1-html`、`ab-canary-v2-html`
- Deployment: `ab-canary-v1`、`ab-canary-v2`
- Service: `ab-canary-v1`、`ab-canary-v2`

#### AI Bot
**File**: `resources/ai-bot.yaml`
- ImageStream: `rhcl-ai-bot`
- BuildConfig: `rhcl-ai-bot`（Docker build）
- Deployment: `rhcl-ai-bot`
- Service: `rhcl-ai-bot`

#### Demo API
**File**: `resources/demo-api.yaml`
- Deployment: `demo-api`
- Service: `demo-api`

#### External Proxy（ESPN API）
**File**: `resources/external-proxy.yaml`
- ConfigMap: `external-proxy-nginx-conf`
- Deployment: `external-proxy`
- Service: `external-proxy`、`external-proxy-internal`

#### Keycloak
**File**: `resources/keycloak.yaml`
- Secret: `keycloak-admin-credentials`
- PersistentVolumeClaim: `keycloak-data`
- Deployment: `keycloak`
- Service: `keycloak`

#### OIDC Callback
**File**: `resources/oidc-callback-configmap.yaml`、`resources/oidc-callback.yaml`
- ConfigMap: `oidc-callback-js`（Node.js callback server）
- Deployment: `oidc-callback`
- Service: `oidc-callback`

#### OIDC UI
**File**: `resources/oidc-ui-configmap.yaml`、`resources/oidc-ui.yaml`
- ConfigMap: `oidc-ui-html`（静的HTML UI）
- Deployment: `oidc-ui`
- Service: `oidc-ui`

#### Main UI
**File**: `resources/ui-configmap.yaml`、`resources/ui.yaml`
- ConfigMap: `rhcl-ui-html`（メインdemo UI）
- Deployment: `rhcl-ui`
- Service: `rhcl-ui`

---

## Application 5: Core Observability (wave 3)

Grafana instanceとdashboardsの設定。

### Path
`gitops/apps/rhcl-demo-split/app-5-core-observability/`

### Resources

#### Wave 0 - Grafana設定
**File**: `resources/05-observability-and-grafana.yaml`

含まれるresources:
- **ServiceAccount**: `grafana-sa`（Prometheus token access用）
- **ClusterRole**: `grafana-prometheus-reader`
- **ClusterRoleBinding**: ServiceAccountへの権限binding
- **Secret**: `grafana-thanos-token`（Prometheus access token）
- **Grafana**: `grafana-instance`
  - Anonymous access有効
  - Default role: Admin
  - Prometheus datasource設定
- **GrafanaDatasource**: `prometheus-grafanadatasource`
  - OpenShift User Workload Monitoringへの接続
  - ServiceAccount token authentication

#### Wave 1 - Grafana Dashboards
**File**: `resources/06-grafana-dashboard-configmaps.yaml`

含まれるdashboards:
- **Developer Dashboard**: HTTPコード別requests、latency percentiles（P90/P95/P99）
- **Platform Dashboard**: Top services、SRE視点のmetrics
- **Business Dashboard**: Traffic summary、総requests数

各dashboardはConfigMapとして定義され、Grafana instanceに自動importされます。

---

## Application 6: Routes & Policies (wave 4)

HTTPRoute、AuthPolicy、RateLimitPolicy、TokenRateLimitPolicyの設定。

### Path
`gitops/apps/rhcl-demo-split/app-6-routes-policies/`

### Resources

すべてのresourcesはwave 0でdeployされます（App6全体がwave 4として実行）。

#### HTTPRoutes
- `resources/rhcl-ui.yaml` - メインUI HTTPRoute
- `resources/oidc-portal.yaml` - OIDC portal HTTPRoute
- `resources/demo-api.yaml` - Demo API HTTPRoute
- `resources/secure-demo.yaml` - Secure demo HTTPRoute
- `resources/keycloak.yaml` - Keycloak HTTPRoute
- `resources/oidc-portal-callback.yaml` - OIDC callback HTTPRoute
- `resources/oidc-portal-whoami.yaml` - OIDC identity HTTPRoute
- `resources/ai-bot-main.yaml` - AI bot main HTTPRoute
- `resources/ai-bot-ai.yaml` - AI bot専用 HTTPRoute（ai.domain）
- `resources/rhcl-mcp-gateway.yaml` - MCP Gateway HTTPRoute
- `resources/ai-bot-mcp-server.yaml` - AI bot用MCP server HTTPRoute
- `resources/external-proxy.yaml` - 内部external proxy HTTPRoute
- `resources/external-proxy-public.yaml` - 公開external proxy HTTPRoute
- `resources/ab-demo.yaml` - A/B test demo HTTPRoute
- `resources/jwt-demo.yaml` - JWT demo HTTPRoute
- `resources/http-to-https.yaml` - HTTPからHTTPS redirect HTTPRoute

#### AuthPolicy
- `resources/gateway-authpolicy.yaml` - Gateway全体のdefault deny
- `resources/rhcl-gw-auth.yaml` - メインGateway auth policy
- `resources/ab-authpolicy.yaml` - A/B demo auth
- `resources/ai-bot-main-authpolicy.yaml` - AI bot main auth
- `resources/ai-bot-mcp-authpolicy.yaml` - AI bot MCP auth
- `resources/external-proxy-authpolicy.yaml` - External proxy auth
- `resources/jwt-demo-jwt.yaml` - JWT demo JWT validation
- `resources/secure-demo-jwt.yaml` - Secure demo JWT validation

#### OIDCPolicy
- `resources/secure-demo-oidc.yaml` - OIDC browser login policy

#### RateLimitPolicy
- `resources/demo-api-ratelimit.yaml` - Demo API rate limiting（identity-based）

#### TokenRateLimitPolicy
- `resources/ai-bot-ai-tokenratelimitpolicy.yaml` - AI bot専用domain token rate limiting
- `resources/ai-bot-main-tokenratelimitpolicy.yaml` - AI bot main domain token rate limiting

#### Secrets
- `resources/api-keys.yaml` - API key secrets（`IAMALICE`、`IAMBOB`）

#### MCP関連
- `resources/ai-bot-mcp-httproute.yaml` - AI bot用MCP HTTPRoute
- `resources/ai-bot-mcpserverregistration.yaml` - MCP server registration
- `resources/rhcl-mcp-tools-internal.yaml` - 内部MCP tools

#### MCPGatewayExtension
- `resources/rhcl-mcp.yaml` - MCP Gateway extension設定

---

## Kustomize Patches

各Application manifestは、Kustomize patchesを使用して環境固有の値（hostname、Gateway名等）を動的に注入します。

### Patch対象
- **HTTPRoute**: `spec.hostnames`、`spec.parentRefs[].name`
- **MCPGatewayExtension**: `spec.publicHost`
- **OIDCPolicy**: `spec.provider.issuerURL`、`spec.provider.authorizationEndpoint`、`spec.provider.tokenEndpoint`、`spec.provider.redirectURI`
- **AuthPolicy（JWT）**: `spec.defaults.rules.authentication.jwt.jwt.issuerUrl`

Patchesは`rhcl-app-6-routes-policies.yaml` Application manifest内で定義されます。

---

## Sync Wave戦略

Split application architectureは、以下のsync-wave戦略を使用：

1. **Wave 0** (並行実行):
   - App0: Grafana Operator install
   - App1: 全Operators install + RBAC

2. **Wave 2**:
   - App2: Platform CR（Namespaces、ClusterIssuer）

3. **Wave 3** (並行実行):
   - App3: Gateways + DNSPolicy + TLSPolicy
   - App4: Demo applications
   - App5: Grafana instance + dashboards

4. **Wave 4**:
   - App6: HTTPRoutes + Policies（App3-5完了後）

この戦略により、依存関係を尊重しながら可能な限り並行deployを実現し、全体のdeploy時間を短縮します。

---

## 自動化されたSync Retry

すべてのApplicationsは以下の自動sync policyを持ちます：

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

これにより、一時的な失敗（resource作成中のtiming問題等）は自動的にretryされ、手動介入が不要になります。

---

## 注意事項

- すべてのfile pathsは`gitops/apps/rhcl-demo-split/`を基準としています
- 各Applicationは独立してdeploy可能（依存関係を尊重する限り）
- Kustomize patchesは環境固有の値を動的に注入するため、同じmanifestを複数環境で再利用可能
- Console plugin自動有効化により、手動でのplugin有効化が不要
