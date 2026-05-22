# Red Hat Connectivity Link (RHCL) Resources解説

このドキュメントでは、Red Hat Connectivity Link（Kuadrant）が提供するPolicyリソースとGateway APIリソースについて、役割と実際のデモでの使用方法を解説します。

## 目次

- [Gateway API Resources](#gateway-api-resources)
  - [Gateway](#gateway)
  - [HTTPRoute](#httproute)
  - [ReferenceGrant](#referencegrant)
- [RHCL Policy Resources](#rhcl-policy-resources)
  - [AuthPolicy](#authpolicy)
  - [RateLimitPolicy](#ratelimitpolicy)
  - [TokenRateLimitPolicy](#tokenratelimitpolicy)
  - [OIDCPolicy](#oidcpolicy)
  - [DNSPolicy](#dnspolicy)
  - [TLSPolicy](#tlspolicy)

---

## Gateway API Resources

### Gateway

**役割**: Kubernetes clusterへの外部トラフィックのentry pointを定義。複数のlistenerを持ち、それぞれが異なるprotocol、port、hostnameを処理します。

**主要機能**:
- 複数のlistener定義（HTTP、HTTPS、TLS passthrough等）
- TLS certificate管理（Secret参照）
- Listener単位のhostname filtering
- Cross-namespace routingのサポート

**このデモでの使用例**:

#### 1. メインGateway（rhcl-workshop-gw）
```yaml
# app-3-gateways/resources/02-gateways.yaml
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: rhcl-workshop-gw
  namespace: openshift-ingress
spec:
  gatewayClassName: istio
  listeners:
    - name: https
      protocol: HTTPS
      port: 443
      hostname: "*.sandbox2689.opentlc.com"
```

**デモモジュール**: 
- Policy Playground（API keys + rate limiting）
- Traffic Shaping（A/B + Canary）
- Keycloak JWT demo
- OIDC Portal

#### 2. AI専用Gateway（rhcl-ai-gw）
```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: rhcl-ai-gw
  namespace: openshift-ingress
spec:
  gatewayClassName: istio
  listeners:
    - name: https-ai
      protocol: HTTPS
      port: 443
      hostname: "ai.sandbox2689.opentlc.com"
```

**デモモジュール**: 
- AI Chatbot（token budget rate limiting）

#### 3. 外部API Gateway（rhcl-external-gw）
```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: rhcl-external-gw
  namespace: openshift-ingress
spec:
  gatewayClassName: istio
  listeners:
    - name: https-external-public
      protocol: HTTPS
      port: 443
      hostname: "external-api.sandbox2689.opentlc.com"
```

**デモモジュール**: 
- External API（ESPN proxy）

---

### HTTPRoute

**役割**: Gatewayへのトラフィックを特定のbackend servicesにroutingするルールを定義。path-based、header-based routing、traffic splitting等をサポート。

**主要機能**:
- Path matching（Exact、PathPrefix、RegularExpression）
- Header/Query parameter filtering
- Weight-based traffic splitting（A/B testing、Canary deployment）
- Backend reference（Service、cross-namespace Service）
- Request/Response header manipulation

**このデモでの使用例**:

#### 1. メインUI HTTPRoute
```yaml
# app-6-routes-policies/resources/httproute-ui.yaml
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: rhcl-ui
  namespace: demo
spec:
  parentRefs:
    - name: rhcl-workshop-gw
      namespace: openshift-ingress
  hostnames:
    - "rhcl-workshop.sandbox2689.opentlc.com"
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /
      backendRefs:
        - name: rhcl-ui
          port: 8080
```

**デモモジュール**: 
- すべてのデモの起点となるメインUI

#### 2. A/B Canary HTTPRoute（weighted routing）
```yaml
# app-6-routes-policies/resources/httproute-ab-canary-external.yaml
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: ab-demo
  namespace: demo-ab
spec:
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /ab
      backendRefs:
        - name: ab-canary-v1
          port: 8080
          weight: 80
        - name: ab-canary-v2
          port: 8080
          weight: 20
    - matches:
        - path:
            type: PathPrefix
            value: /canary
      backendRefs:
        - name: ab-canary-v1
          port: 8080
          weight: 90
        - name: ab-canary-v2
          port: 8080
          weight: 10
```

**デモモジュール**: 
- Traffic Shaping（A/B testing 80/20、Canary deployment 90/10）

#### 3. External API HTTPRoute
```yaml
# app-6-routes-policies/resources/httproute-external-proxy-public.yaml
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: external-proxy-public
  namespace: demo
spec:
  parentRefs:
    - name: rhcl-external-gw
      namespace: openshift-ingress
  hostnames:
    - "external-api.sandbox2689.opentlc.com"
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /nba
      backendRefs:
        - name: external-proxy
          port: 8080
```

**デモモジュール**: 
- External API（ESPN sports data proxy）

---

### ReferenceGrant

**役割**: Cross-namespace参照を明示的に許可するためのsecurity mechanism。GatewayとHTTPRouteが異なるnamespaceにある場合に必要。

**主要機能**:
- Namespace間のService参照許可
- 最小権限の原則（specific namespace + specific resource type）
- Gateway APIのsecurity model実装

**このデモでの使用例**:

```yaml
# app-6-routes-policies/resources/referencegrant-oidc-to-external-proxy.yaml
apiVersion: gateway.networking.k8s.io/v1beta1
kind: ReferenceGrant
metadata:
  name: allow-oidc-to-external-proxy
  namespace: demo
spec:
  from:
    - group: gateway.networking.k8s.io
      kind: HTTPRoute
      namespace: rhcl-oidc-portal
  to:
    - group: ""
      kind: Service
      name: external-proxy
```

**デモモジュール**: 
- OIDC Portal（`rhcl-oidc-portal` namespaceから`demo` namespaceのServiceを参照）

---

## RHCL Policy Resources

### AuthPolicy

**役割**: Gateway/HTTPRouteに対してauthentication（認証）とauthorization（認可）を適用。API key validation、JWT validation、custom OPA policyなど多様なauth mechanismをサポート。

**主要機能**:
- API key authentication（header、query parameter）
- JWT validation（Keycloak、その他OIDC provider）
- OPA-based authorization
- Deny-by-default policy（zero-trust）
- Route-levelまたはGateway-level適用
- Custom response（unauthorized/forbidden）

**このデモでの使用例**:

#### 1. Gateway-level Deny-by-Default
```yaml
# app-6-routes-policies/resources/gateway-authpolicy.yaml
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: rhcl-gw-auth
  namespace: openshift-ingress
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: Gateway
    name: rhcl-workshop-gw
  defaults:
    when:
      - predicate: "request.path != '/health'"
    rules:
      authorization:
        deny-all:
          opa:
            rego: "allow = false"
      response:
        unauthorized:
          headers:
            "content-type":
              value: application/json
          body:
            value: |
              {
                "error": "Forbidden",
                "message": "Access denied by default. Route-level AuthPolicy required."
              }
```

**デモモジュール**: 
- Policy Playground（zero-trust baseline）

#### 2. API Key Authentication
```yaml
# app-6-routes-policies/resources/route-authpolicy.yaml
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: demo-api-auth
  namespace: demo
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: demo-api
  overrides:
    rules:
      authentication:
        api-key:
          apiKey:
            selector:
              matchLabels:
                app: demo-api
          credentials:
            authorizationHeader:
              prefix: APIKEY
      authorization:
        api-key-valid:
          opa:
            rego: |
              allow {
                metadata := object.get(auth.identity, "metadata", {})
                metadata.username != null
              }
```

**デモモジュール**: 
- Policy Playground（API key `IAMALICE`, `IAMBOB`でのaccess control）

#### 3. JWT Validation
```yaml
# app-6-routes-policies/resources/jwt-demo-authpolicy.yaml
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: jwt-demo-jwt
  namespace: demo-jwt
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: jwt-demo
  defaults:
    rules:
      authentication:
        jwt:
          jwt:
            issuerUrl: https://rhcl-workshop.sandbox2689.opentlc.com/auth/realms/rhcl
      authorization:
        jwt-valid:
          opa:
            rego: |
              allow {
                claims := object.get(auth.identity, ["preferred_username"], "")
                claims != ""
              }
```

**デモモジュール**: 
- Keycloak JWT（token取得、decode、有無での呼び出しデモ）

---

### RateLimitPolicy

**役割**: HTTPRouteに対してrate limiting（レート制限）を適用。Request countに基づく制限で、identity-based limitsやglobal limitsをサポート。

**主要機能**:
- Per-second/minute/hour/day rate limits
- Identity-based limits（user、API key等で異なる制限）
- Counter expression（custom attribute-based counting）
- Conditional limits（`when` predicate）
- Distributed rate limiting（multi-replica対応）

**このデモでの使用例**:

```yaml
# app-6-routes-policies/resources/route-ratelimitpolicy.yaml
apiVersion: kuadrant.io/v1
kind: RateLimitPolicy
metadata:
  name: demo-api-rlp
  namespace: demo
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: demo-api
  limits:
    general-user:
      rates:
        - limit: 5
          window: 10s
      counters:
        - expression: auth.identity.userid
      when:
        - predicate: "auth.identity.userid != 'bob'"
    bob-limit:
      rates:
        - limit: 2
          window: 10s
      when:
        - predicate: "auth.identity.userid == 'bob'"
```

**動作**:
- Alice（`IAMALICE`）: 10秒で5リクエストまで
- Bob（`IAMBOB`）: 10秒で2リクエストまで
- 制限超過時: `429 Too Many Requests`

**デモモジュール**: 
- Policy Playground（Bobの方が早く429に到達することを実演）

---

### TokenRateLimitPolicy

**役割**: LLM API呼び出しに対して**token使用量ベース**のrate limitingを適用。Request countではなく、actual token consumptionで制限するため、LLMコスト管理に最適。

**主要機能**:
- Token-based rate limiting（input + output tokens）
- LLM API response headerからtoken使用量を抽出
- Per-user token budgets
- Cost management（token = cost）
- Streaming response対応

**このデモでの使用例**:

```yaml
# app-6-routes-policies/resources/ai-bot-ai-tokenratelimitpolicy.yaml
apiVersion: kuadrant.io/v1
kind: TokenRateLimitPolicy
metadata:
  name: ai-bot-ai-trlp
  namespace: rhcl-ai-bot
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: ai-bot-ai
  limits:
    token-budget:
      rates:
        - limit: 400
          window: 15s
      costs:
        default: 1
        overrides:
          - selector: response.headers.@case:lower.x-rh-llm-usage-completion-tokens
            cost: response.headers.@case:lower.x-rh-llm-usage-completion-tokens|0
          - selector: response.headers.@case:lower.x-rh-llm-usage-prompt-tokens
            cost: response.headers.@case:lower.x-rh-llm-usage-prompt-tokens|0
```

**動作**:
- Token budget: 400 tokens / 15秒
- Response headerから`x-rh-llm-usage-*-tokens`を読み取り
- Token使用量がbudgetを超えると`429 Too Many Requests`

**デモモジュール**: 
- AI Chatbot（「Hit 429」buttonで意図的に429をトリガー）

---

### OIDCPolicy

**役割**: Browser-based OIDC authentication flowを実装。HTTPRouteに対してOIDC loginを強制し、unauthenticated userをIdentity Providerへredirect。

**主要機能**:
- OIDC Authorization Code Flow
- Automatic redirect to IdP（Keycloak等）
- Cookie-based session management
- Token refresh
- Custom callback endpoint

**このデモでの使用例**:

```yaml
# app-6-routes-policies/resources/secure-oidcpolicy.yaml
apiVersion: extensions.kuadrant.io/v1alpha1
kind: OIDCPolicy
metadata:
  name: secure-demo-oidc
  namespace: rhcl-oidc-portal
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: secure-demo
  provider:
    clientID: rhcl-ui
    issuerURL: https://oidc-rhcl-workshop.sandbox2689.opentlc.com/auth/realms/rhcl
    authorizationEndpoint: https://oidc-rhcl-workshop.sandbox2689.opentlc.com/auth/realms/rhcl/protocol/openid-connect/auth
    tokenEndpoint: https://oidc-rhcl-workshop.sandbox2689.opentlc.com/auth/realms/rhcl/protocol/openid-connect/token
    redirectURI: https://oidc-rhcl-workshop.sandbox2689.opentlc.com/oidc/callback
  auth:
    tokenSource:
      cookie:
        name: rhcl_token
```

**動作**:
1. Unauthenticated user → `302 redirect`をKeycloakへ
2. User login → Keycloakがtoken発行
3. Callback → Gatewayがcookie設定（`rhcl_token`）
4. 以降のrequest → cookieでauthenticated

**デモモジュール**: 
- OIDC Portal（browser login flow実演）

---

### DNSPolicy

**役割**: GatewayのlistenerにDNS recordを自動的に作成/更新。AWS Route53との統合をサポートし、manual DNS管理を不要に。

**主要機能**:
- AWS Route53 record自動管理
- Listener hostname → DNS record mapping
- Multiple provider support（Route53、その他DNS provider）
- Health check integration（optional）
- LoadBalancer strategy（Simple、Weighted、Geolocation）

**このデモでの使用例**:

```yaml
# app-3-gateways/resources/03-dnspolicy.yaml
apiVersion: kuadrant.io/v1
kind: DNSPolicy
metadata:
  name: rhcl-workshop-dns
  namespace: openshift-ingress
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: Gateway
    name: rhcl-workshop-gw
  routingStrategy: simple
```

**前提条件**:
- AWS credentials（`Secret/route53-credentials`）
- Route53 hosted zone

**動作**:
- Gateway listenerのhostnameからDNS recordを自動作成
- 例: `rhcl-workshop.sandbox2689.opentlc.com` → A/AAAA record
- Gateway削除時にDNS recordも自動削除

**デモモジュール**: 
- すべてのデモ（public hostnameへのアクセスを可能に）

---

### TLSPolicy

**役割**: Gateway listenerのTLS certificateを自動的に管理。cert-managerと連携してcertificate lifecycle（発行、更新、rotation）を自動化。

**主要機能**:
- cert-manager integration
- Automatic certificate issuance
- Certificate renewal（自動更新）
- ClusterIssuer selection
- SAN（Subject Alternative Names）management
- Let's Encrypt、その他CA対応

**このデモでの使用例**:

```yaml
# app-3-gateways/resources/04-tls-policies.yaml
apiVersion: kuadrant.io/v1alpha1
kind: TLSPolicy
metadata:
  name: rhcl-workshop-tls
  namespace: openshift-ingress
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: Gateway
    name: rhcl-workshop-gw
  issuerRef:
    group: cert-manager.io
    kind: ClusterIssuer
    name: letsencrypt-production
```

**動作**:
1. Gateway listenerのhostnameを検出
2. cert-manager `Certificate` resourceを自動作成
3. ClusterIssuerがcertificate発行（例: Let's Encrypt DNS-01 challenge）
4. Gateway listenerがcertificate Secretを参照
5. 期限前に自動renewal

**デモモジュール**: 
- すべてのデモ（HTTPS接続を自動化）

---

## Policy適用の階層とOverride

RHCLのPolicyは**階層的に適用**され、優先順位があります：

### 適用レベル

1. **Gateway-level Policy** (`defaults`)
   - Gatewayに接続されるすべてのHTTPRouteに適用
   - 例: Deny-by-default AuthPolicy

2. **Route-level Policy** (`overrides`)
   - 特定のHTTPRouteにのみ適用
   - Gateway-level policyをオーバーライド可能
   - 例: 特定routeのみAPI key authentication許可

### Override動作

```yaml
# Gateway-level: deny-by-default
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: gateway-default-deny
  namespace: openshift-ingress
spec:
  targetRef:
    kind: Gateway
    name: rhcl-workshop-gw
  defaults:
    rules:
      authorization:
        deny-all:
          opa:
            rego: "allow = false"
---
# Route-level: allow with API key
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: route-allow-apikey
  namespace: demo
spec:
  targetRef:
    kind: HTTPRoute
    name: demo-api
  overrides:
    rules:
      authentication:
        api-key:
          apiKey:
            selector:
              matchLabels:
                app: demo-api
```

**結果**: 
- `/demo-api` → API key authenticationでアクセス可能
- その他すべてのroutes → 403 Forbidden（deny-by-default）

---

## デモモジュール別Resource一覧

### 1. Policy Playground（API Keys + Rate Limiting）

**使用Resources**:
- `Gateway`: `rhcl-workshop-gw`
- `HTTPRoute`: `demo-api`
- `AuthPolicy`: 
  - `rhcl-gw-auth`（Gateway-level deny-by-default）
  - `demo-api-auth`（Route-level API key authentication）
- `RateLimitPolicy`: `demo-api-rlp`（identity-based limits）
- `Secret`: `api-keys`（`IAMALICE`, `IAMBOB`）

**実演内容**:
- Zero-trust baseline（default deny）
- API keyでのaccess control
- Identity-based rate limiting（Bobは制限が厳しい）

---

### 2. Traffic Shaping（A/B + Canary）

**使用Resources**:
- `Gateway`: `rhcl-workshop-gw`
- `HTTPRoute`: `ab-demo`（weighted backend references）
  - `/ab` → 80/20 split
  - `/canary` → 90/10 split
  - `/canary` + header `x-rhcl-canary: always` → 100% v2
- `AuthPolicy`: `ab-authpolicy`（allow access）

**実演内容**:
- A/B testing（random 80/20）
- Canary deployment（progressive 90/10）
- Header-based routing（force v2）

---

### 3. External API（ESPN Proxy）

**使用Resources**:
- `Gateway`: `rhcl-external-gw`
- `HTTPRoute`: `external-proxy-public`
- `DNSPolicy`: `rhcl-external-dns`（`external-api.sandbox2689.opentlc.com`）
- `TLSPolicy`: `rhcl-external-tls`
- `AuthPolicy`: `external-proxy-authpolicy`（allow access）

**実演内容**:
- Third-party API proxyをGateway経由で公開
- Dedicated hostnameでのAPI exposure
- 同じPolicyをexternal dataにも適用可能

---

### 4. AI Chatbot（Token Budget）

**使用Resources**:
- `Gateway`: `rhcl-ai-gw`（dedicated AI hostname）
- `HTTPRoute`: 
  - `ai-bot-main`（メインUI）
  - `ai-bot-ai`（AI API endpoint、`ai.sandbox2689.opentlc.com`）
- `TokenRateLimitPolicy`: 
  - `ai-bot-ai-trlp`（400 tokens / 15s）
  - `ai-bot-main-trlp`
- `AuthPolicy`: `ai-bot-main-authpolicy`、`ai-bot-mcp-authpolicy`

**実演内容**:
- Token-based rate limiting（request countではなくtoken count）
- LLM cost management
- 429エラーを意図的にトリガー

---

### 5. Keycloak JWT

**使用Resources**:
- `Gateway`: `rhcl-workshop-gw`
- `HTTPRoute`: `jwt-demo`
- `AuthPolicy`: `jwt-demo-authpolicy`（JWT validation）

**実演内容**:
- Token取得（Keycloak）
- Token decode（JWTの内容表示）
- Tokenの有無での呼び出し（401 vs 200）

---

### 6. OIDC Portal

**使用Resources**:
- `Gateway`: `rhcl-workshop-gw`
- `HTTPRoute`: 
  - `oidc-portal`（main portal）
  - `oidc-portal-callback`（OIDC callback endpoint）
  - `secure-demo`（protected content）
- `OIDCPolicy`: `secure-demo-oidc`（browser login flow）
- `AuthPolicy`: 
  - `oidc-portal-authpolicy`（allow portal access）
  - `secure-demo-jwt`（JWT validation after login）
- `DNSPolicy`: `rhcl-oidc-dns`（`oidc-rhcl-workshop.sandbox2689.opentlc.com`）

**実演内容**:
- Browser-based OIDC login
- Automatic redirect to Keycloak
- Cookie-based session management

---

### 7. Observability（Grafana Dashboards）

**使用Resources**:
- `HTTPRoute`: `grafana`
- `AuthPolicy`: `grafana-authpolicy`（anonymous access）
- `ServiceMonitor`、`PodMonitor`（Kuadrant metricsから取得）
- `Grafana`、`GrafanaDatasource`、`GrafanaDashboard`

**実演内容**:
- Real-time metrics visualization
- Developer/Platform/Business dashboards
- Policy enforcement metrics（401/403/429 responses）

---

## まとめ

RHCL（Kuadrant）は、**Gateway API上に構築されたPolicy-driven traffic management platform**です：

### Gateway API（標準）
- **Gateway**: Traffic entry point
- **HTTPRoute**: Routing rules（path、header、weight-based）
- **ReferenceGrant**: Cross-namespace security

### RHCL Policy（拡張）
- **AuthPolicy**: Authentication + Authorization（API key、JWT、OPA）
- **RateLimitPolicy**: Request-count based rate limiting（identity-based）
- **TokenRateLimitPolicy**: Token-usage based rate limiting（LLM cost management）
- **OIDCPolicy**: Browser OIDC login flow
- **DNSPolicy**: DNS record自動管理（Route53）
- **TLSPolicy**: Certificate自動管理（cert-manager）

これらのResourcesを組み合わせることで、**zero-trust、progressive delivery、cost management、observability**を実現するmodern API gatewayを構築できます。
