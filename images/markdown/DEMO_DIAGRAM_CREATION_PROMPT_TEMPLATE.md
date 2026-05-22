# RHCL デモ用ネットワーク経路図作成プロンプトテンプレート

## 概要

このドキュメントは、Red Hat Connectivity Link (RHCL) / Kuadrant / Gateway API を使ったデモ環境のネットワーク経路図を PlantUML で作成する際の参考プロンプトテンプレートです。

Policy Playground 図作成時の経験と、2つのレビューファイルのアドバイスを統合しています。

---

## Claude Code に渡す基本プロンプト

```text
Red Hat Connectivity Link (RHCL) / Kuadrant / Gateway API のデモ用ネットワーク経路図を PlantUML 形式で作成してください。

対象デモ: [デモ名を記載]
例: Policy Playground (API keys + rate limiting)

以下の要件に従ってください。

## 1. 基本構成要素

### インフラ構成
- AWS (package)
  - Route53 Public DNS (OpenShift Cluster の外側、AWS の内側)
  - AWS Load Balancer (OpenShift Cluster の外側、AWS の内側)
  - OpenShift Cluster (package)
    - OpenShift Router / Ingress Controller (Cluster 内)
    - Gateway (Cluster 内)
    - HTTPRoute (Cluster 内)
    - Backend Service + Pod (Cluster 内)

### cert-manager 構成 (TLS を使用する場合)
- Certificate resource
- ClusterIssuer
- TLS Secret

### Policy リソース (デモに応じて選択)
- DNSPolicy
- TLSPolicy
- AuthPolicy (Gateway-level / Route-level)
- RateLimitPolicy
- TokenRateLimitPolicy
- OIDCPolicy

## 2. Data Plane と Control Plane の明確な分離

### Data Plane (実線)
Runtime request / response path のみ

```text
Browser
  -> DNS Query / Response (Route53 Public DNS)
  -> HTTPS Request (Internet)
  -> AWS Load Balancer
  -> OpenShift Router / Ingress Controller (SNI passthrough)
  -> Gateway (TLS termination at Envoy)
  -> HTTPRoute (route match)
  -> Backend Service
  -> Pod
  -> Response (逆順で戻る)
```

### Control Plane (点線)
Policy attachment / controller reconcile / management

```text
DNSPolicy
  -> Route53 API (DNS record management)
  -> Gateway (watches hostname / address)

TLSPolicy
  -> Certificate resource (requests certificate)
  -> ClusterIssuer (uses issuer)
  -> TLS Secret (stores cert/key)
  -> Gateway listener (certificateRefs)

AuthPolicy / RateLimitPolicy / その他 Policy
  -> Gateway または HTTPRoute (applies to)
```

## 3. 重要な設計原則

### DNS 関連
- **Browser は DNSPolicy に問い合わせない**
- DNS Query は Route53 Public DNS に向ける (実線)
- DNSPolicy は Route53 API により DNS レコードを管理する (点線、Control Plane)

注記例:
```text
**Route53 Public DNS**
- Browser queries Route53 for DNS
- Returns AWS LB IP
- NOT queried via DNSPolicy

**DNSPolicy (Control Plane)**
- Manages DNS records via Route53 API
- Watches Gateway hostname/address
- Creates/updates DNS records
```

### TLS 関連
- **OpenShift Router は TLS を終端しない** (SNI passthrough)
- **Gateway / Envoy が TLS を終端する**
- Router → Gateway の矢印には以下の補足を追加:
  ```text
  SNI passthrough
  via OpenShift Route
  → Gateway Service
  ```

注記例:
```text
**OpenShift Router**
- SNI passthrough (does NOT terminate TLS)
- Routes to Gateway Service
- OpenShift Route resource generated for Gateway listener hostname

**Gateway / Envoy**
- TLS termination
- Uses Secret from cert-manager
- Decrypts HTTPS traffic
```

### cert-manager 関連
- **Certificate が ClusterIssuer を参照する** (uses)
- **cert-manager が Certificate resource を reconcile する**
- **Certificate resource が TLS Secret を作成する**
- **Gateway listener が Secret を certificateRefs で参照する**

注記例:
```text
**cert-manager**
- Certificate uses ClusterIssuer
- cert-manager reconciles Certificate resource
- Creates TLS Secret
- DNS-01 challenge via Route53 (if applicable)
- Auto renewal
```

### Policy 関連
- **Policy はリクエストが直接通過するコンポーネントではない**
- Gateway / Envoy / Kuadrant 関連コンポーネントに適用される設定リソース
- Policy の箱をデータパス上のプロキシのように配置しない
- Gateway / HTTPRoute に "applies to" する関係として点線で接続

注記例:
```text
**Gateway-level Policy**
- Default protection
- deny-all by default
- Applies to all HTTPRoutes
- Can be overridden by route-specific policies

**Route-level Policy**
- Route-specific authentication
- API Key validation (例)
- Missing/invalid key → 401
- Authenticated but denied → 403
```

### レスポンスコード
401 / 403 / 429 の意味を明確に分ける

```text
Missing or invalid API key -> 401 Unauthorized
Authenticated but not allowed -> 403 Forbidden
Rate limit exceeded -> 429 Too Many Requests
Success -> 200 OK
```

## 4. PlantUML スタイル設定

```plantuml
skinparam defaultTextAlignment center
skinparam componentStyle rectangle
skinparam backgroundColor white
skinparam shadowing false
skinparam nodesep 60
skinparam ranksep 80
```

- `nodesep`: コンポーネント間の水平方向の間隔
- `ranksep`: コンポーネント間の垂直方向の間隔
- 文字と文字が重ならないように調整

## 5. 図の方向性

**上から下への流れ (top-to-bottom)** を推奨

- 左から右への流れ (left-to-right) は見づらくなる傾向
- 上から下の方が、ブラウザ → バックエンドの流れが自然

## 6. AWS とクラスタの配置関係

```plantuml
package "AWS" {
  cloud "Route53\n(Public DNS)" as route53 #Khaki
  component "AWS Load\nBalancer" as awslb #Orange

  package "OpenShift Cluster" {
    component "OpenShift Router\nIngress Controller\n(SNI passthrough)" as router #LightBlue
    
    package "Gateway\n(namespace)" {
      component "Gateway\n..." as gateway #LightGreen
      ...
    }
    
    package "Backend\n(namespace)" {
      component "Service\n..." as service #LightSteelBlue
      component "Pod\n..." as pod #LightGray
    }
  }
}
```

- AWS Load Balancer は OpenShift Cluster の外側、AWS の内側
- Route53 は OpenShift Cluster の外側、AWS の内側
- OpenShift Router / Ingress Controller は OpenShift Cluster の内側
- Gateway / HTTPRoute / Policy / Backend は OpenShift Cluster の内側

## 7. 凡例 (Legend)

必ず図の下部に凡例を追加

```plantuml
legend bottom left
**Flow Types:**
━━━ Solid line = Data Plane (runtime request/response)
┄┄┄ Dashed line = Control Plane (policy/management/reconcile)

**Response Codes:**
• 200 = Success
• 401 = Unauthorized (missing/invalid credentials)
• 403 = Forbidden (authenticated but denied)
• 429 = Too Many Requests (rate limit exceeded)

**Architecture:**
• AWS Load Balancer: Outside OpenShift Cluster
• Route53: AWS managed DNS service
• OpenShift Router: Inside OpenShift Cluster
• Gateway/HTTPRoute/Backend: Inside OpenShift Cluster

**Steps:**
① DNS Query → Response (Route53)
② HTTPS Request
③ SNI routing (AWS LB → Router)
④ TLS passthrough (Router → Gateway via OpenShift Route)
⑤ Route matching (Gateway → HTTPRoute)
⑥ Forward to Backend
endlegend
```

## 8. タイトル

図の目的を明確にするタイトルを付ける

```plantuml
title [デモ名] Complete Data Flow\n([主要な Policy または機能を列挙])
```

例:
```plantuml
title Policy Playground Complete Data Flow\n(DNS → TLS → API Keys → Rate Limiting)
```

## 9. ファイル出力

### PlantUML ファイル
- ファイル名: `[demo-name]-flow.puml`
- パス: `/Users/kamori/vscode/redhat-blog/202604_Connectivity Link/github/demo-rhcl/images/`

### SVG ファイル生成
```bash
cd "/Users/kamori/vscode/redhat-blog/202604_Connectivity Link/github/demo-rhcl/images"
plantuml -tsvg [demo-name]-flow.puml
```

## 10. レビューポイント

作成後、以下をセルフチェック:

### Data Plane と Control Plane の分離
- [ ] 実線は Runtime request / response path のみ
- [ ] 点線は Policy / controller / management のみ

### DNS
- [ ] Browser → Route53 (実線)
- [ ] DNSPolicy → Route53 API (点線)
- [ ] Browser → DNSPolicy という線がない

### TLS
- [ ] Router は SNI passthrough と明記
- [ ] Gateway は TLS termination と明記
- [ ] Router → Gateway の矢印に "via OpenShift Route" の補足

### Policy
- [ ] Policy は applies to として点線で接続
- [ ] Policy が Data Plane 上のコンポーネントとして配置されていない

### cert-manager
- [ ] Certificate uses ClusterIssuer
- [ ] cert-manager reconciles Certificate resource
- [ ] Certificate creates TLS Secret
- [ ] Gateway listener references Secret via certificateRefs

### AWS / OpenShift Cluster の配置
- [ ] AWS Load Balancer は OpenShift Cluster の外側
- [ ] Route53 は OpenShift Cluster の外側
- [ ] OpenShift Router は OpenShift Cluster の内側

### レスポンスコード
- [ ] 401 / 403 / 429 / 200 の意味が分かれている

### 凡例
- [ ] 実線と点線の意味が説明されている
- [ ] Architecture の配置関係が説明されている
- [ ] Steps が番号付きで説明されている

---

## 使用例

### Traffic Shaping デモの図を作成する場合

```text
Red Hat Connectivity Link (RHCL) / Kuadrant / Gateway API のデモ用ネットワーク経路図を PlantUML 形式で作成してください。

対象デモ: Traffic Shaping (A/B + Canary)

このデモでは以下の機能を実演します:
- HTTPRoute の重み付け backend による progressive delivery
- A/B テスト: ランダム分割 80/20 (/ab)
- Canary rollout: 90/10 split (/canary)
- Canary opt-in: header x-rhcl-canary: always で強制 100% v2

特記事項:
- このデモでは AuthPolicy や RateLimitPolicy は使用しません
- HTTPRoute の weighted backend 機能を強調してください
- backend が v1 と v2 の 2 つの Service / Pod に分かれることを明示してください
- header による強制ルーティングの仕組みを注記で説明してください

/Users/kamori/vscode/redhat-blog/202604_Connectivity Link/github/demo-rhcl/images/markdown/DEMO_DIAGRAM_CREATION_PROMPT_TEMPLATE.md
の設計原則に従ってください。

ファイル出力先:
- PlantUML: /Users/kamori/vscode/redhat-blog/202604_Connectivity Link/github/demo-rhcl/images/traffic-shaping-flow.puml
- SVG: 同ディレクトリに生成
```

---

## 参考資料

- Policy Playground 図: `policy-playground-flow.puml` / `policy-playground-flow.svg`
- レビューファイル:
  - `rhcl_network_flow_review_for_claude_code.md` (初回レビュー)
  - `policy_playground_flow_review_for_claude_code.md` (最終レビュー)
