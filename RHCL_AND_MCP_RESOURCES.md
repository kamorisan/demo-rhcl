# Red Hat Connectivity Link (RHCL) and MCP Gateway Resources解説

このドキュメントでは、Red Hat Connectivity Link（Kuadrant）が提供するPolicyリソース、Gateway APIリソース、およびMCP Gatewayについて、役割と実際のデモでの使用方法を解説します。

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
- [MCP Gateway](#mcp-gateway)
  - [MCP Gatewayとは](#mcp-gatewayとは)
  - [Model Context Protocol (MCP)](#model-context-protocol-mcp)
  - [MCP Gatewayのアーキテクチャ](#mcp-gatewayのアーキテクチャ)
  - [MCP Gatewayが使用するRHCLリソース](#mcp-gatewayが使用するrhclリソース)
  - [AI ChatbotデモでのMCP Gateway](#ai-chatbotデモでのmcp-gateway)
  - [構成例](#構成例)

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

#### 3. MCP Gateway（rhcl-mcp-gw）
```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: rhcl-mcp-gw
  namespace: openshift-ingress
spec:
  gatewayClassName: istio
  listeners:
    - name: https-mcp
      protocol: HTTPS
      port: 443
      hostname: "mcp.sandbox2689.opentlc.com"
```

**デモモジュール**: 
- AI Chatbot（MCP tools endpoint）

#### 4. 外部API Gateway（rhcl-external-gw）
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
- AI Chatbot（MCP toolsが使用）

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

#### 3. MCP Gateway HTTPRoute
```yaml
# MCP Gateway経由でMCP toolsを公開
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: rhcl-mcp-gateway
  namespace: mcp-system
spec:
  parentRefs:
    - name: rhcl-mcp-gw
      namespace: openshift-ingress
  hostnames:
    - "mcp.sandbox2689.opentlc.com"
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /mcp
      backendRefs:
        - name: mcp-gateway
          port: 8080
```

**デモモジュール**: 
- AI Chatbot（LLM toolsへのアクセス）

#### 4. External API HTTPRoute
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
- AI Chatbot（MCP toolsが内部的に使用）

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

#### 3. MCP Gateway AuthPolicy（Deny-by-Default + Route Allow）
```yaml
# MCP Gateway-level deny-by-default
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: rhcl-mcp-gw-auth
  namespace: openshift-ingress
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: Gateway
    name: rhcl-mcp-gw
  defaults:
    rules:
      authorization:
        deny-all:
          opa:
            rego: "allow = false"
---
# Route-level allow for /mcp
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: rhcl-mcp-allow
  namespace: mcp-system
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: rhcl-mcp-gateway
  overrides:
    rules:
      authorization:
        allow-all:
          opa:
            rego: "allow = true"
```

**デモモジュール**: 
- AI Chatbot（MCP tools accessを選択的に許可）

#### 4. JWT Validation
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

## MCP Gateway CRD Resources

MCP Gateway Operator は、以下の Custom Resource Definitions (CRDs) を提供し、Model Context Protocol (MCP) を実装した Gateway を構築します。

---

### MCPGatewayExtension

**役割**: Gateway API Gateway を拡張して MCP protocol を処理。Gateway listener に対して MCP broker と router を deploy し、MCP トラフィックを処理可能にする。

**主要機能**:
- Broker-router Deployment と Service の自動 deploy
- EnvoyFilter による MCP トラフィックのルーティング設定
- Envoy proxy への external processor 設定
- Upstream MCP servers からの tool 集約（MCPServerRegistration 経由）
- Session management（in-memory または Redis）
- JWT-based tool filtering（trusted headers）

**このデモでの使用例**:

```yaml
apiVersion: mcp.kuadrant.io/v1alpha1
kind: MCPGatewayExtension
metadata:
  name: rhcl-mcp-extension
  namespace: mcp-system
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: Gateway
    name: rhcl-mcp-gw
    namespace: openshift-ingress
    sectionName: https-mcp
  backendPingIntervalSeconds: 60
  httpRouteManagement: Enabled
```

**主要フィールド**:
- `targetRef`: MCP protocol をサポートする Gateway listener を指定
  - `sectionName`: Gateway の listener 名
- `backendPingIntervalSeconds`: Upstream MCP servers への ping 間隔（10-7200秒、default: 60）
- `httpRouteManagement`: HTTPRoute の自動管理（`Enabled` / `Disabled`）
- `sessionStore`: Redis ベースの session storage 設定（optional）
- `trustedHeadersKey`: JWT-based tool filtering 用の key pair 設定（optional）
- `urlElicitation`: URL-based token elicitation の有効化（optional）

**動作**:
1. Broker-router Deployment を指定 namespace に deploy
2. Gateway namespace に EnvoyFilter を作成し、MCP トラフィックを broker へ routing
3. Envoy proxy を external processor として設定
4. MCPServerRegistration リソースから upstream MCP servers を検出
5. Broker が tools を集約し、federated tool list として提供

**デモモジュール**: 
- AI Chatbot（MCP tools による外部データアクセス）

---

### MCPServerRegistration

**役割**: Upstream MCP server を登録し、Gateway 経由で tool federation を実現。Broker が upstream server に接続して tools を検出・集約する。

**主要機能**:
- HTTPRoute 経由での backend MCP server 検出
- Tool prefix による naming conflict 回避
- Broker 専用の credential 管理（tool discovery / session management 用）
- Category-based tool discovery（`discover_tools` meta-tool）
- Per-user token elicitation（URL-based）
- Custom MCP endpoint path 指定

**このデモでの使用例**:

```yaml
apiVersion: mcp.kuadrant.io/v1alpha1
kind: MCPServerRegistration
metadata:
  name: espn-mcp-server
  namespace: mcp-system
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: espn-mcp-route
    namespace: mcp-system
  prefix: espn_
  path: /mcp
  category:
    - sports
    - data
  hint: "Provides live sports scores from ESPN API"
  credentialRef:
    name: espn-mcp-credentials
    key: token
```

**主要フィールド**:
- `targetRef`: Backend MCP server を指す HTTPRoute
- `prefix`: Tool 名の prefix（例: `espn_nba_scoreboard`）。Immutable
- `path`: MCP endpoint path（default: `/mcp`）
- `credentialRef`: Broker が tool discovery に使用する credential（client の `tools/call` には inject されない）
- `category`: Tool discovery filtering 用の category（max 3、max 128 chars）
- `hint`: Server の説明（max 256 chars、`discover_tools` で返される）
- `tokenURLElicitation`: Per-user token collection の設定（optional）

**動作**:
1. Controller が HTTPRoute の backend service を検出
2. Broker が upstream MCP server に接続（`credentialRef` を使用）
3. Broker が `tools/list` を呼び出して tools を取得
4. Tools に `prefix` を付けて federated tool list に追加
5. Client の `tools/call` request は router が直接 backend へ routing（Broker は経由しない）

**Credential の使い分け**:
- **`credentialRef`**: Broker → upstream（tool discovery / session management）
- **Client → upstream**: AuthPolicy、URL token elicitation、または client-provided headers

**デモモジュール**: 
- AI Chatbot（ESPN tool registration）

---

### MCPVirtualServer

**役割**: 特定の tool set のみを公開する仮想 server を定義。Tool-level access control と federation を実現。

**主要機能**:
- Tool 単位のアクセス制御
- 特定 tools のみを公開する virtual endpoint
- Prompts の公開制御（optional）
- 複数の upstream servers から選択的に tools を集約

**このデモでの使用例**:

```yaml
apiVersion: mcp.kuadrant.io/v1alpha1
kind: MCPVirtualServer
metadata:
  name: sports-tools-only
  namespace: mcp-system
spec:
  description: "Virtual server exposing only sports-related tools"
  tools:
    - espn_nba_scoreboard
    - espn_epl_scoreboard
  prompts:
    - sports_summary
```

**主要フィールド**:
- `description`: Virtual server の説明（human-readable）
- `tools`: 公開する tool 名のリスト（**required**、minimum 1 item）
- `prompts`: 公開する prompt 名のリスト（optional、omit で全 prompts 公開）

**動作**:
1. Broker が federated tool list から指定された tools のみを filter
2. Virtual server endpoint 経由での `tools/list` は filtered list を返す
3. 指定されていない tools は、この virtual server 経由ではアクセス不可

**Use case**:
- 特定 user/team に特定 tools のみを公開
- 異なる AuthPolicy を適用した複数の virtual endpoints（例: internal users vs external partners）
- Tool の段階的ロールアウト（一部 users のみに新 tool を公開）

**デモモジュール**: 
- 今回のデモでは使用していない（MCPGatewayExtension と MCPServerRegistration のみ使用）

---

## MCP Gateway

### MCP Gatewayとは

**MCP Gateway**は、**Model Context Protocol (MCP)** を実装したGatewayで、Large Language Models (LLMs) が外部データソースやツールにアクセスするための標準化されたインターフェースを提供します。

Red Hat Connectivity Link (RHCL) / Kuadrantと統合することで、MCP toolsへのアクセスに対しても、通常のHTTPトラフィックと同じPolicy（認証、認可、rate limiting等）を適用できます。

**主要な利点**:
- **標準化されたツールアクセス**: LLMが外部APIを呼び出すための統一インターフェース
- **セキュリティ**: RHCL AuthPolicyによるアクセス制御
- **コスト管理**: RateLimitPolicyによる使用量制限
- **集中管理**: すべての外部API呼び出しがGateway経由で可視化・制御可能
- **環境間での安定性**: 専用hostname（`mcp.<domain>`）により、環境依存を最小化

---

### Model Context Protocol (MCP)

**MCP（Model Context Protocol）**は、LLMがcontext-aware toolsにアクセスするためのopen protocolです。

**主要コンセプト**:
- **Tools**: LLMが呼び出し可能な関数（例: `get_nba_scores()`, `get_weather()`）
- **Context**: Toolsが提供する外部データ
- **Protocol**: LLMとtools間の標準化された通信方式

**MCP Gatewayの役割**:
```
LLM → MCP Gateway → MCP Tools → External APIs
```

**MCPを使用しない場合の課題**:
- 各LLMアプリケーションが個別にexternal API統合を実装
- 認証・認可ロジックがアプリケーション側に散在
- Rate limiting、observabilityが困難

**MCPを使用する利点**:
- 標準化されたtool定義
- Gateway経由で一元管理
- RHCLのPolicyを適用可能

---

### MCP Gatewayのアーキテクチャ

```
┌─────────────────────────────────────────────────────────────┐
│ Browser (UI)                                                │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│ AI Gateway (ai.<domain>)                                    │
│ - TLS termination                                           │
│ - TokenRateLimitPolicy (400 tokens / 15s)                   │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│ AI Bot Backend                                              │
│ - LLM API呼び出し                                            │
│ - Tool call判定                                              │
└─────────┬───────────────────────────────┬───────────────────┘
          │                               │
          │ (LLM request)                 │ (Tool call)
          ▼                               ▼
┌──────────────────────┐      ┌──────────────────────────────┐
│ External LLM API     │      │ MCP Gateway (mcp.<domain>)   │
│ (OpenAI-compatible)  │      │ - TLS termination            │
└──────────────────────┘      │ - AuthPolicy (deny + allow)  │
                              └───────────┬──────────────────┘
                                          │
                                          ▼
                              ┌──────────────────────────────┐
                              │ MCP Tools (ESPN tool)        │
                              └───────────┬──────────────────┘
                                          │
                                          ▼
                              ┌──────────────────────────────┐
                              │ External API Gateway         │
                              │ (external-api.<domain>)      │
                              │ - ESPN Proxy                 │
                              └───────────┬──────────────────┘
                                          │
                                          ▼
                              ┌──────────────────────────────┐
                              │ External ESPN API            │
                              └──────────────────────────────┘
```

**データフロー**:

1. **Chat Request**: Browser → AI Gateway → AI Bot → LLM
2. **LLM判定**: Tool callが必要（例: ESPN scores）
3. **Tool Call**: AI Bot → MCP Gateway (`mcp.<domain>/mcp/tools/espn`)
4. **Tool実行**: MCP Tool → External API Gateway (`external-api.<domain>/nba`)
5. **ESPN Proxy**: External API Gateway → External ESPN API
6. **Data Return**: ESPN data → MCP Tool → AI Bot
7. **Second LLM Call**: AI Bot → LLM (with tool result)
8. **Final Answer**: LLM → AI Bot → Browser

---

### MCP Gatewayが使用するRHCLリソース

#### 1. Gateway（専用hostname）
```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: rhcl-mcp-gw
  namespace: openshift-ingress
spec:
  gatewayClassName: istio
  listeners:
    - name: https-mcp
      protocol: HTTPS
      port: 443
      hostname: "mcp.sandbox2689.opentlc.com"
      tls:
        mode: Terminate
        certificateRefs:
          - name: rhcl-mcp-tls
```

**特徴**:
- **専用hostname**: `mcp.<domain>`
- **TLS termination**: Gateway/Envoy側で実施
- **Isolation**: AI Gatewayとは別のGateway

#### 2. HTTPRoute（/mcp endpoint）
```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: rhcl-mcp-gateway
  namespace: mcp-system
spec:
  parentRefs:
    - name: rhcl-mcp-gw
      namespace: openshift-ingress
  hostnames:
    - "mcp.sandbox2689.opentlc.com"
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /mcp
      backendRefs:
        - name: mcp-gateway
          port: 8080
```

**特徴**:
- **Path**: `/mcp` 配下すべて
- **Backend**: MCP tools service

#### 3. AuthPolicy（Gateway deny-all + Route allow）
```yaml
# Gateway-level deny-by-default
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: rhcl-mcp-gw-auth
  namespace: openshift-ingress
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: Gateway
    name: rhcl-mcp-gw
  defaults:
    rules:
      authorization:
        deny-all:
          opa:
            rego: "allow = false"
---
# Route-level allow for /mcp
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: rhcl-mcp-allow
  namespace: mcp-system
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: rhcl-mcp-gateway
  overrides:
    rules:
      authorization:
        allow-all:
          opa:
            rego: "allow = true"
```

**パターン**: External API Gatewayと同じoverride pattern
- Gateway-level: deny-all（default protection）
- Route-level: allow（selective override for `/mcp`）

**目的**:
- MCP tools以外のendpointへのアクセスを防ぐ
- `/mcp` endpointのみを選択的に許可

#### 4. TLSPolicy（certificate管理）
```yaml
apiVersion: kuadrant.io/v1alpha1
kind: TLSPolicy
metadata:
  name: rhcl-mcp-tls
  namespace: openshift-ingress
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: Gateway
    name: rhcl-mcp-gw
  issuerRef:
    group: cert-manager.io
    kind: ClusterIssuer
    name: letsencrypt-production
```

**動作**:
- `mcp.<domain>` のTLS certificateを自動管理
- Let's Encrypt DNS-01 challenge
- 自動renewal

#### 5. DNSPolicy（DNS record管理）
```yaml
apiVersion: kuadrant.io/v1
kind: DNSPolicy
metadata:
  name: rhcl-mcp-dns
  namespace: openshift-ingress
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: Gateway
    name: rhcl-mcp-gw
  routingStrategy: simple
```

**動作**:
- Route53に`mcp.<domain>`のA/AAAA recordを自動作成
- Gateway削除時に自動削除

---

### AI ChatbotデモでのMCP Gateway

#### デモフロー

**Step 1: ユーザーが質問**
```
Browser → AI Gateway (ai.<domain>) → AI Bot
```

**Step 2: AI BotがLLMを呼び出し**
```
AI Bot → External LLM API
LLM: "I need ESPN data. Calling tool: get_nba_scores()"
```

**Step 3: AI BotがMCP Gatewayを呼び出し**
```
AI Bot → MCP Gateway (mcp.<domain>/mcp/tools/espn)
MCP Gateway: AuthPolicy check (allow for /mcp)
→ Forward to MCP Tool
```

**Step 4: MCP ToolがExternal API Gatewayを呼び出し**
```
MCP Tool → External API Gateway (external-api.<domain>/nba)
External API Gateway: AuthPolicy check (allow for /nba)
→ Forward to ESPN Proxy
```

**Step 5: ESPN ProxyがExternal ESPN APIを呼び出し**
```
ESPN Proxy → External ESPN API
ESPN API: Returns NBA scores (JSON)
```

**Step 6: Dataが逆順で戻る**
```
ESPN API → ESPN Proxy → MCP Tool → AI Bot
```

**Step 7: AI BotがLLMに結果を渡す（2nd call）**
```
AI Bot → External LLM API (with tool result)
LLM: "Here are today's NBA scores: ..."
```

**Step 8: 最終回答をBrowserへ**
```
AI Bot → Browser
UI: Displays answer + token usage
```

#### 使用されるすべてのRHCLリソース

**AI Gateway**:
- `Gateway`: `rhcl-ai-gw` (`ai.<domain>`)
- `HTTPRoute`: `ai-bot-ai`
- `TokenRateLimitPolicy`: `ai-bot-ai-trlp` (400 tokens / 15s)
- `TLSPolicy`: `rhcl-ai-tls`
- `DNSPolicy`: `rhcl-ai-dns`

**MCP Gateway**:
- `Gateway`: `rhcl-mcp-gw` (`mcp.<domain>`)
- `HTTPRoute`: `rhcl-mcp-gateway` (`/mcp`)
- `AuthPolicy`: 
  - `rhcl-mcp-gw-auth` (Gateway deny-all)
  - `rhcl-mcp-allow` (Route allow for `/mcp`)
- `TLSPolicy`: `rhcl-mcp-tls`
- `DNSPolicy`: `rhcl-mcp-dns`

**External API Gateway**:
- `Gateway`: `rhcl-external-gw` (`external-api.<domain>`)
- `HTTPRoute`: `external-proxy-public` (`/nba`, `/epl`, etc.)
- `AuthPolicy`:
  - `rhcl-gw-auth` (Gateway deny-all、shared)
  - `external-proxy-public-allow` (Route allow)
- `TLSPolicy`: `rhcl-external-tls`
- `DNSPolicy`: `rhcl-external-dns`

---

### 構成例

#### MCP Tool Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mcp-espn-tool
  namespace: mcp-system
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mcp-espn-tool
  template:
    metadata:
      labels:
        app: mcp-espn-tool
    spec:
      containers:
        - name: mcp-tool
          image: quay.io/example/mcp-espn-tool:latest
          ports:
            - containerPort: 8080
          env:
            - name: EXTERNAL_API_URL
              value: "https://external-api.sandbox2689.opentlc.com"
---
apiVersion: v1
kind: Service
metadata:
  name: mcp-gateway
  namespace: mcp-system
spec:
  selector:
    app: mcp-espn-tool
  ports:
    - port: 8080
      targetPort: 8080
```

#### MCP Tool実装例（概念）
```python
# MCP ESPN Tool (Python example)
from mcp_sdk import Tool
import httpx

class ESPNTool(Tool):
    name = "get_nba_scores"
    description = "Get current NBA scores"
    
    async def execute(self):
        # Call External API Gateway
        async with httpx.AsyncClient() as client:
            response = await client.get(
                "https://external-api.sandbox2689.opentlc.com/nba"
            )
            return response.json()
```

#### UI/Bot側のMCP呼び出し例
```python
# AI Bot calling MCP Gateway
async def call_mcp_tool(tool_name: str, params: dict):
    async with httpx.AsyncClient() as client:
        response = await client.post(
            f"https://mcp.sandbox2689.opentlc.com/mcp/tools/{tool_name}",
            json=params
        )
        return response.json()

# LLM tool call handling
if llm_response.tool_calls:
    for tool_call in llm_response.tool_calls:
        tool_result = await call_mcp_tool(
            tool_call.name,
            tool_call.parameters
        )
        # Second LLM call with result
        final_answer = await llm.complete(
            messages + [{"role": "tool", "content": tool_result}]
        )
```

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

### MCP GatewayでのOverride例

```yaml
# MCP Gateway: deny-by-default
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: rhcl-mcp-gw-auth
  namespace: openshift-ingress
spec:
  targetRef:
    kind: Gateway
    name: rhcl-mcp-gw
  defaults:
    rules:
      authorization:
        deny-all:
          opa:
            rego: "allow = false"
---
# MCP Route: allow for /mcp
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: rhcl-mcp-allow
  namespace: mcp-system
spec:
  targetRef:
    kind: HTTPRoute
    name: rhcl-mcp-gateway
  overrides:
    rules:
      authorization:
        allow-all:
          opa:
            rego: "allow = true"
```

**結果**:
- `/mcp` → アクセス許可（MCP tools）
- その他すべてのpaths → 403 Forbidden

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

### 4. AI Chatbot（Token Budget + MCP Tools）

**使用Resources**:

**AI Gateway**:
- `Gateway`: `rhcl-ai-gw`（`ai.sandbox2689.opentlc.com`）
- `HTTPRoute`: `ai-bot-ai`
- `TokenRateLimitPolicy`: `ai-bot-ai-trlp`（400 tokens / 15s）
- `TLSPolicy`: `rhcl-ai-tls`
- `DNSPolicy`: `rhcl-ai-dns`

**MCP Gateway**:
- `Gateway`: `rhcl-mcp-gw`（`mcp.sandbox2689.opentlc.com`）
- `HTTPRoute`: `rhcl-mcp-gateway`（`/mcp`）
- `AuthPolicy`: 
  - `rhcl-mcp-gw-auth`（Gateway deny-all）
  - `rhcl-mcp-allow`（Route allow）
- `TLSPolicy`: `rhcl-mcp-tls`
- `DNSPolicy`: `rhcl-mcp-dns`

**External API Gateway（再利用）**:
- `Gateway`: `rhcl-external-gw`（`external-api.sandbox2689.opentlc.com`）
- `HTTPRoute`: `external-proxy-public`（`/nba`, `/epl`, etc.）

**実演内容**:
- Token-based rate limiting（request countではなくtoken count）
- LLM cost management
- MCP toolsによるexternal data access
- Multi-hop data flow（Browser → AI → LLM → MCP → External API → ESPN）

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

### RHCL（Kuadrant）の構成要素

**Gateway API（標準）**:
- **Gateway**: Traffic entry point（複数のlistener、dedicated hostnames）
- **HTTPRoute**: Routing rules（path、header、weight-based）
- **ReferenceGrant**: Cross-namespace security

**RHCL Policy（拡張）**:
- **AuthPolicy**: Authentication + Authorization（API key、JWT、OPA）
- **RateLimitPolicy**: Request-count based rate limiting（identity-based）
- **TokenRateLimitPolicy**: Token-usage based rate limiting（LLM cost management）
- **OIDCPolicy**: Browser OIDC login flow
- **DNSPolicy**: DNS record自動管理（Route53）
- **TLSPolicy**: Certificate自動管理（cert-manager）

### MCP Gateway（LLM Tools統合）

**役割**:
- LLMがexternal toolsにアクセスするための標準化されたインターフェース
- RHCLのPolicyを適用可能（認証、認可、rate limiting）

**アーキテクチャ**:
- 専用Gateway（`mcp.<domain>`）
- AuthPolicy override pattern（Gateway deny-all + Route allow）
- External API Gatewayとの連携（ESPN proxy経由でexternal data取得）

**使用例**:
- AI Chatbot: Browser → AI Gateway → LLM → MCP Gateway → External API → ESPN

### 統合の利点

これらのResourcesを組み合わせることで、以下を実現：

1. **Zero-trust Security**: Default deny + selective allow
2. **Progressive Delivery**: A/B testing、Canary deployment
3. **Cost Management**: Token-based rate limiting for LLMs
4. **Centralized Control**: すべてのtrafficがGateway経由、Policyで一元管理
5. **Observability**: Real-time metrics、policy enforcement tracking
6. **LLM Integration**: MCP Gatewayによる標準化されたtool access

**結果**: Modern API gatewayとして、internal/external/LLM trafficすべてを統一的に管理できるplatform。
