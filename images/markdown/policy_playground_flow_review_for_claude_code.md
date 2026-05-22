# Policy Playground Flow 図レビュー（Claude Code 連携用）

## 目的

このドキュメントは、`policy-playground-flow.jpeg` の修正版ネットワーク経路図について、Claude Code に追加修正を依頼するためのレビュー内容を整理したものです。

対象図は、Red Hat Connectivity Link / Kuadrant / OpenShift / AWS Route53 / TLS / Gateway API / Policy / Backend までを含むデモ用のネットワーク経路図です。

---

## 総評

修正版は、前回版と比較してかなり改善されています。

特に、以下の点が明確になっています。

- `AWS Load Balancer` と `OpenShift Router Ingress Controller` が分離されている
- Router が `SNI passthrough` する構成として明記されている
- Gateway 側で `TLS Termination` することが示されている
- `TLS Secret` と `Gateway certificateRefs` の関係が追加されている
- `DNSPolicy -> Route53 API` が Control Plane の管理経路として表現されている
- `AuthPolicy / RateLimitPolicy applies to HTTPRoute` が点線で表現されている
- 凡例により、実線と点線の意味が明確になっている
- 401 / 403 / 429 / 200 のレスポンスパターンが整理されている

この修正版は、デモ説明用の構成図としてかなり妥当です。

---

## 良くなった点

### 1. Data Plane と Control Plane の区別が明確になった

前回版では、ブラウザからの実通信と、DNSPolicy / TLSPolicy / AuthPolicy / RateLimitPolicy などの制御系の関係が同じレイヤーに見えやすい状態でした。

修正版では、以下のように整理されています。

- 実線: 実リクエストの通信経路
- 点線: Policy / Controller / Reconcile などの制御経路
- 注記: Policy の評価結果やレスポンスコード

これは非常に良い修正です。

---

### 2. TLS の責務分担が分かりやすくなった

修正版では、以下の関係が明確になっています。

```text
Browser
  -> AWS Load Balancer
  -> OpenShift Router / Ingress Controller
  -> SNI passthrough
  -> Gateway / Envoy
  -> TLS Termination
```

この表現により、OpenShift Router が TLS を終端するのではなく、Gateway / Envoy 側で TLS を終端する構成であることが分かりやすくなっています。

---

### 3. TLS Secret と certificateRefs の関係が追加された

Gateway listener が TLS Secret を `certificateRefs` で参照する関係が追加されており、TLSPolicy / cert-manager / Gateway の関係が理解しやすくなっています。

概念的には以下の流れです。

```text
TLSPolicy
  -> Certificate resource / cert-manager
  -> ClusterIssuer
  -> Let's Encrypt
  -> TLS Secret
  -> Gateway listener certificateRefs
```

---

### 4. DNSPolicy と Route53 API の関係が Control Plane として表現された

修正版では、DNS Query と DNSPolicy の関係が混ざらず、次の2つが分けて表現されています。

#### 実行時の DNS 解決

```text
Browser
  -> DNS query
  -> Route53 Public DNS
  -> DNS response
```

#### RHCL / Kuadrant による DNS レコード管理

```text
DNSPolicy
  -> controller reconcile
  -> Route53 API
  -> DNS record creation / update
```

この分離は正しいです。

---

### 5. Gateway-level Policy と Route-level Policy の関係が説明されている

Gateway 側の deny-all policy と、HTTPRoute 側の API Key 認証 / RateLimitPolicy の関係が注記されており、初見の人が疑問に思いやすいポイントを補えています。

特に、以下の意図が図中に残っているのは重要です。

```text
Gateway-level policy:
  - default protection
  - deny-all by default
  - applies to all HTTPRoutes
  - can be overridden / specialized by route-specific policies
```

---

### 6. API Key 認証とレスポンスコードの整理が改善された

以下のように、401 / 403 / 429 の違いが整理されています。

```text
Missing / invalid API key -> 401 Unauthorized
Authenticated but denied -> 403 Forbidden
Rate limit exceeded -> 429 Too Many Requests
Success -> 200 OK
```

この整理は妥当です。

---

## 追加で直すとさらに良い点

### 1. AWS Load Balancer は OpenShift Cluster の外に配置する

現在の図では、`AWS Load Balancer` が OpenShift Cluster の内側に見える可能性があります。

より正確には、AWS Load Balancer は AWS 環境内にありますが、OpenShift Cluster の外側にあるものとして表現するのが自然です。

推奨配置は以下です。

```text
AWS
├─ AWS Load Balancer
└─ OpenShift Cluster
   └─ OpenShift Router / Ingress Controller
```

### Claude Code への修正指示例

```text
AWS Load Balancer を OpenShift Cluster の外側、AWS の内側に移動してください。
OpenShift Router / Ingress Controller は OpenShift Cluster 内に配置してください。
```

---

### 2. Route53 Public DNS も OpenShift Cluster の外に配置する

`Route53 Public DNS` は AWS のマネージドサービスなので、OpenShift Cluster の外側に配置する方が正確です。

推奨配置は以下です。

```text
AWS
├─ Route53 Public DNS
├─ Route53 API
├─ AWS Load Balancer
└─ OpenShift Cluster
```

ただし、Route53 を「インターネット上の DNS 解決先」として見せたい場合は、AWS 外枠の近くに置いても構いません。

### Claude Code への修正指示例

```text
Route53 Public DNS と Route53 API は OpenShift Cluster の外側に配置してください。
可能であれば AWS 外枠の内側、OpenShift Cluster 外側に置いてください。
```

---

### 3. Router から Gateway への接続関係を少し補足する

Router から Gateway へ TLS passthrough される表現は良いですが、見る人によっては「Router から Gateway へどの Kubernetes リソース経由で到達するのか」が分かりにくい可能性があります。

図中に次のような補足を入れると分かりやすくなります。

```text
OpenShift Route generated for Gateway listener hostname
```

または、

```text
OpenShift Route -> Gateway Service
```

### Claude Code への修正指示例

```text
OpenShift Router / Ingress Controller から Gateway / Envoy への矢印に、
"OpenShift Route / Gateway Service" または
"Route generated for Gateway listener hostname"
という補足ラベルを追加してください。
```

---

### 4. cert-manager 周りの表現をさらに正確にする

現在の `ClusterIssuer -> Certificate -> TLS Secret` の流れは概念図として十分分かりやすいです。

より正確にするなら、次のような表現にできます。

```text
Certificate resource
  uses ClusterIssuer
  reconciled by cert-manager
  creates TLS Secret
```

つまり、ClusterIssuer が直接 Certificate を作るというより、Certificate リソースが ClusterIssuer を参照し、cert-manager が Secret を作成する、という関係です。

### Claude Code への修正指示例

```text
cert-manager の表現を以下の関係に寄せてください。

Certificate resource -> uses ClusterIssuer
cert-manager reconciles Certificate resource
Certificate resource -> creates TLS Secret

ただし、図が複雑になりすぎる場合は、現状の簡略表現のままでも構いません。
```

---

## 図の用途別の改善案

### 詳細版として使う場合

現在の図は、詳細構成図としてはかなり完成度が高いです。

以下の目的にはこのまま使えます。

- 社内レビュー
- デモ構成の説明
- ブログ記事の補足図
- Claude Code による継続的な図修正
- RHCL / Kuadrant / Gateway API の構成理解

ただし、情報量は多いため、初見の人には少し重い可能性があります。

---

### 簡略版を別に用意する場合

ブログやプレゼンでは、先に簡略版を見せ、その後に今回の詳細版を見せると理解しやすくなります。

簡略版の例は以下です。

```text
Browser
  -> DNS query / response
  -> Route53 Public DNS
  -> HTTPS request
  -> AWS Load Balancer
  -> OpenShift Router / SNI passthrough
  -> Gateway / Envoy / TLS termination
  -> AuthPolicy check
  -> RateLimitPolicy check
  -> HTTPRoute
  -> Backend Service
  -> Pod
```

### Claude Code への追加依頼例

```text
詳細図とは別に、README やブログ冒頭に載せるための簡略フロー図も作成してください。
形式は Mermaid の flowchart で構いません。
```

---

## 修正優先度

### 優先度 高

1. `AWS Load Balancer` を OpenShift Cluster の外、AWS の内側に配置する
2. `Route53 Public DNS` / `Route53 API` を OpenShift Cluster の外、AWS の内側に配置する

### 優先度 中

3. `OpenShift Router / Ingress Controller -> Gateway` の矢印に、`OpenShift Route / Gateway Service` の補足を追加する
4. cert-manager 周りの表現を `Certificate resource uses ClusterIssuer` に寄せる

### 優先度 低

5. 詳細図とは別に簡略版 Mermaid 図を追加する
6. ブログ用に、Data Plane と Control Plane を別図に分ける

---

## Claude Code 向けまとめ指示

以下を Claude Code にそのまま渡す想定の修正指示です。

```text
policy-playground-flow の PlantUML 図を以下の方針で修正してください。

1. AWS Load Balancer は OpenShift Cluster の外側、AWS の内側に配置してください。
2. Route53 Public DNS と Route53 API も OpenShift Cluster の外側、AWS の内側に配置してください。
3. OpenShift Router / Ingress Controller は OpenShift Cluster 内に配置してください。
4. Browser からの実通信は以下の順序で表現してください。
   Browser
     -> Route53 Public DNS
     -> AWS Load Balancer
     -> OpenShift Router / Ingress Controller
     -> Gateway / Envoy
     -> HTTPRoute
     -> Backend Service
     -> Pod
5. OpenShift Router / Ingress Controller から Gateway / Envoy への矢印には、
   "SNI passthrough" に加えて、
   "OpenShift Route / Gateway Service" または
   "Route generated for Gateway listener hostname"
   の補足を入れてください。
6. Gateway / Envoy では "TLS termination" を明記してください。
7. DNSPolicy から Route53 API への関係は Control Plane の点線で表現してください。
8. TLSPolicy / cert-manager / Certificate / ClusterIssuer / TLS Secret / certificateRefs の関係は Control Plane の点線で表現してください。
9. AuthPolicy / RateLimitPolicy は HTTPRoute に直接通信が流れる箱ではなく、
   "applies to HTTPRoute" の関係として点線で表現してください。
10. Gateway-level deny-all policy と Route-level API key policy の関係が分かる注記を残してください。
11. Missing / invalid API key -> 401、
    authenticated but denied -> 403、
    rate limit exceeded -> 429、
    success -> 200
    の注記を残してください。
12. 図の凡例には、
    solid line = data plane request flow
    dashed line = control plane / policy / reconcile relationship
    を明記してください。
```

---

## 最終評価

修正版はかなり妥当です。

前回指摘した主要論点はほぼ反映されています。

最後に直すなら、主にリソースの配置関係です。

- AWS Load Balancer は OpenShift Cluster 外
- Route53 は OpenShift Cluster 外
- OpenShift Router / Ingress Controller は OpenShift Cluster 内
- Gateway / HTTPRoute / Policy / Backend は OpenShift Cluster 内

この配置にすると、ネットワーク経路図としてかなり完成度が高くなります。
