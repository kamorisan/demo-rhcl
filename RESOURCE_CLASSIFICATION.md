# RHCL Resource Classification for Split Applications

## Application 1: operators (wave 0-5)

### Wave 0 - Namespaces
- `install/resources/kuadrant-namespace.yaml` - kuadrant-system Namespace
- `install/resources/observability-and-grafana.yaml` - monitoring, gateway-system Namespaces
- `appsets/mcp/resources/namespace-mcp-system.yaml` - mcp-system Namespace

### Wave 0 - Operator Subscriptions (base operators)
- `install/resources/observability-and-grafana.yaml` - cluster-observability-operator, servicemeshoperator3 Subscriptions

### Wave 1 - RHCL Operator
- `install/resources/rhcl-operator.yaml` - OperatorGroup, Subscription

### Wave 1-2 - MCP Gateway Operator
- `appsets/mcp/resources/mcp-gateway-olm.yaml` - OperatorGroup (wave 1), Subscription (wave 2)

### Wave 2 - Kuadrant CR
- `install/resources/kuadrant-cr.yaml` - Kuadrant CR

### Wave 4-5 - RHBK Operator
- `base/resources/apps/rhbk-operator.yaml` - OperatorGroup (wave 4), Subscription (wave 5)

### Wave 5 - Console Notification
- `base/resources/apps/console-banner.yaml` - ConsoleNotification (wave 5)

---

## Application 2: platform-crs (wave 10-14)

### Wave 10 - Namespaces
- `base/resources/namespaces/namespace-demo.yaml`
- `base/resources/namespaces/namespace-demo-ab.yaml`
- `base/resources/namespaces/namespace-demo-jwt.yaml`
- `base/resources/namespaces/namespace-rhcl-ai-bot.yaml`
- `base/resources/namespaces/namespace-rhcl-keycloak.yaml`
- `base/resources/namespaces/namespace-rhcl-oidc-portal.yaml`

### Wave 10 - ClusterIssuer
- `base/resources/gateway/clusterissuer.yaml` - ClusterIssuer for cert-manager

### Wave 10 - RoleBindings
- `appsets/mcp/resources/allow-mcp-system-pull-ai-bot-image.yaml` - RoleBinding

---

## Application 3: gateways (wave 14-22)

### Wave 14 - Certificates
- `base/resources/gateway/gateway-public-certificate.yaml` - Certificate for public gateway
- `overlays/rhcl2/certificate-external-sandbox.yaml` - Certificate for external-api
- Additional certificates from rhcl2 overlay

### Wave 15 - Gateways
- `base/resources/gateway/gateway.yaml` - rhcl-gw Gateway
- `base/resources/gateway/ai-gateway.yaml` - rhcl-ai-gw Gateway

### Wave 17-19 - DNSPolicy, Routes, ReferenceGrants
- `base/resources/gateway/gateway-dnspolicy.yaml` - DNSPolicy (wave 17)
- `base/resources/gateway/gateway-public-dnspolicy-main.yaml` - DNSPolicy (wave 17)
- `base/resources/gateway/route-passthrough.yaml` - Route (wave 18)
- `base/resources/gateway/route-oidc-passthrough.yaml` - Route (wave 18)
- `base/resources/routes/referencegrant-jwt-to-external-proxy.yaml` - ReferenceGrant (wave 19)
- `base/resources/routes/referencegrant-oidc-to-external-proxy.yaml` - ReferenceGrant (wave 19)
- `base/resources/policies/gateway-authpolicy.yaml` - AuthPolicy for Gateway (wave 19)

### Wave 20-22 - MCP Gateway
- `appsets/mcp/resources/mcp-gateway-gateway.yaml` - Gateway (wave 20)
- `appsets/mcp/resources/mcp-gateway-config-secret.yaml` - Secret (wave 21)
- `appsets/mcp/resources/mcp-gateway-public-dnspolicy.yaml` - DNSPolicy (wave 22)

### Wave 21-22 - TLSPolicy, Telemetry
- `overlays/rhcl2/tls-policies.yaml` - TLSPolicy resources
- `overlays/rhcl2/istio-telemetry-gateways.yaml` - Telemetry (wave 22)
- `overlays/rhcl2/istio-tracing.yaml` - Istio tracing config

### Wave 23-24 - MCP Gateway Extension
- `appsets/mcp/resources/mcp-gateway-referencegrant.yaml` - ReferenceGrant (wave 23)
- `appsets/mcp/resources/mcp-gateway-authpolicy.yaml` - AuthPolicy (wave 23)
- `appsets/mcp/resources/mcp-gateway-extension.yaml` - MCPGatewayExtension (wave 24)

---

## Application 4: demo-apps (wave 11-12, 30-35)

### Wave 11 - ConfigMaps, Secrets, PVCs
- `base/resources/apps/ab-canary.yaml` - ConfigMap (wave 11)
- `base/resources/apps/external-proxy.yaml` - ConfigMap (wave 11)
- `base/resources/apps/oidc-ui.yaml` - ConfigMap (wave 11)
- `base/resources/apps/ui.yaml` - ConfigMap (wave 11)
- `base/resources/apps/keycloak.yaml` - Secret, PVC (wave 11)

### Wave 12 - Deployments, Services, ImageStreams, BuildConfigs
- `base/resources/apps/ab-canary.yaml` - Deployment (wave 12)
- `base/resources/apps/ai-bot.yaml` - ImageStream, BuildConfig (wave 12)
- `base/resources/apps/demo-api.yaml` - Deployment, Service (wave 12)
- `base/resources/apps/external-proxy.yaml` - Deployment (wave 12)
- `base/resources/apps/keycloak.yaml` - Deployment, Service (wave 12)
- `base/resources/apps/oidc-callback.yaml` - Deployment, Service (wave 12)
- `base/resources/apps/oidc-ui.yaml` - Deployment (wave 12)
- `base/resources/apps/ui.yaml` - Deployment (wave 12)

### Wave 30-35 - MCP Tools
- `appsets/mcp/resources/rhcl-mcp-tools.yaml` - Deployment (wave 30), Service (wave 31)
- `appsets/mcp/resources/rhcl-mcp-tools-bridge.yaml` - ConfigMap (wave 34), Deployment (wave 35)

---

## Application 5: routes-and-policies (wave 20, 25, 30-33)

### Wave 20 - HTTPRoutes
- `base/resources/routes/httproute-ab-canary-external.yaml`
- `base/resources/routes/httproute-ai-bot-ai.yaml`
- `base/resources/routes/httproute-ai-bot-main.yaml`
- `base/resources/routes/httproute-api.yaml`
- `base/resources/routes/httproute-external-proxy-public.yaml`
- `base/resources/routes/httproute-external-proxy.yaml`
- `base/resources/routes/httproute-http-to-https.yaml`
- `base/resources/routes/httproute-jwt-demo.yaml`
- `base/resources/routes/httproute-keycloak.yaml`
- `base/resources/routes/httproute-oidc-portal.yaml`
- `base/resources/routes/httproute-ui.yaml`

### Wave 25 - MCP Public HTTPRoute
- `appsets/mcp/resources/mcp-gateway-public-httproute.yaml`

### Wave 30 - AuthPolicy, OIDCPolicy, Secrets
- `base/resources/policies/ab-authpolicy.yaml`
- `base/resources/policies/ai-bot-main-authpolicy.yaml`
- `base/resources/policies/api-keys.yaml` - Secrets (wave 30)
- `base/resources/policies/external-proxy-authpolicy.yaml`
- `base/resources/policies/jwt-demo-authpolicy.yaml`
- `base/resources/policies/keycloak-authpolicy.yaml`
- `base/resources/policies/oidc-portal-authpolicy.yaml`
- `base/resources/policies/route-authpolicy.yaml`
- `base/resources/policies/secure-jwt-authpolicy.yaml`
- `base/resources/policies/secure-oidcpolicy.yaml`
- `base/resources/policies/ui-authpolicy.yaml`

### Wave 31-32 - RateLimitPolicy, TokenRateLimitPolicy
- `base/resources/policies/route-ratelimitpolicy.yaml` - RateLimitPolicy (wave 31)
- `base/resources/policies/ai-bot-ai-tokenratelimitpolicy.yaml` - TokenRateLimitPolicy (wave 32)
- `base/resources/policies/ai-bot-main-tokenratelimitpolicy.yaml` - TokenRateLimitPolicy (wave 32)

### Wave 32-33 - MCP HTTPRoutes and Policies
- `appsets/mcp/resources/httproute-rhcl-mcp-tools-internal.yaml` - HTTPRoute (wave 32)
- `appsets/mcp/resources/httproute-rhcl-mcp-tools.yaml` - HTTPRoute (wave 32)
- `appsets/mcp/resources/httproute-rhcl-mcp-tools-upstream.yaml` - HTTPRoute (wave 32)
- `appsets/mcp/resources/mcp-authpolicy.yaml` - AuthPolicy (wave 32)
- `appsets/mcp/resources/authpolicy-rhcl-mcp-tools-internal.yaml` - AuthPolicy (wave 33)
- `appsets/mcp/resources/authpolicy-rhcl-mcp-tools-upstream.yaml` - AuthPolicy (wave 33)
- `appsets/mcp/resources/mcp-tools-authpolicy.yaml` - AuthPolicy (wave 33)
- `appsets/mcp/resources/referencegrant-demo-to-mcp-tools-bridge.yaml` - ReferenceGrant (wave 33)

---

## Application 6: observability (wave 30, 40, 50, 59-97)

### Wave 30 - Grafana Instance
- From overlays/rhcl2/patch-rhcl2-values.yaml - Grafana resource (wave 30)

### Wave 40 - GrafanaDatasource
- From overlays/rhcl2/patch-rhcl2-values.yaml - GrafanaDatasource (wave 40)

### Wave 50 - GrafanaDashboards
- From overlays/rhcl2/patch-rhcl2-values.yaml - GrafanaDashboard resources (wave 50)

### Wave 59-60 - OpenTelemetry
- `appsets/mcp/resources/mcp-otel-collector.yaml` - ServiceAccount (wave 59), OpenTelemetryCollector (wave 60)
- `overlays/rhcl2/otel-collector.yaml` - OpenTelemetryCollector

### Wave 79-80 - OTel RBAC and Jobs
- `appsets/mcp/resources/mcp-gateway-otel-rbac.yaml` - ServiceAccount, Role (wave 79)
- `appsets/mcp/resources/mcp-gateway-otel-env-job.yaml` - Job (wave 80)

### Wave 97 - Grafana Dashboard ConfigMaps
- `install/resources/grafana-dashboard-configmaps.yaml` - ConfigMaps (wave 97)

### Other Observability Resources
- `overlays/rhcl2/tempo-minio.yaml` - Tempo/Minio resources
- `overlays/rhcl2/tracing-console.yaml` - Tracing console
- `overlays/rhcl2/mcp-vanity.yaml` - MCP vanity resources

---

## Notes

- rhcl2 overlay patches (patch-rhcl2-values.yaml) contain patches for multiple applications
- Need to split patches by target application
- gateway-public-certificate.yaml in rhcl2 is an empty override (items: [])
