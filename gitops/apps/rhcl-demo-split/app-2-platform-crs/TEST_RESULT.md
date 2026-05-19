# Application 2 (platform-crs) - Test Result

## Test Environment
- Cluster: sandbox2689.opentlc.com
- Cluster ID: w6q2p
- Date: 2026-05-19
- ArgoCD Application: rhcl-platform-crs-test

## Test Result: ✅ SUCCESS

### Final Status
- **Sync Status**: Synced
- **Health Status**: Healthy
- **Total Resources**: 7
- **All Resources**: Successfully deployed

### Sync Wave Execution (Verified Order)

#### Wave 10 (Platform CRs)
- ✅ Namespace/demo
- ✅ Namespace/demo-ab
- ✅ Namespace/demo-jwt
- ✅ Namespace/rhcl-ai-bot
- ✅ Namespace/rhcl-oidc-portal
- ✅ ClusterIssuer/letsencrypt-production-ec2 → Healthy
- ✅ RoleBinding/allow-mcp-system-pull-ai-bot-image

### Deployed Resources (Verified)
```
NAMESPACE        RESOURCE TYPE    NAME
demo             Namespace        demo
demo-ab          Namespace        demo-ab
demo-jwt         Namespace        demo-jwt
rhcl-ai-bot      Namespace        rhcl-ai-bot
rhcl-oidc-portal Namespace        rhcl-oidc-portal
-                ClusterIssuer    letsencrypt-production-ec2
rhcl-ai-bot      RoleBinding      allow-mcp-system-pull-ai-bot-image
```

### ClusterIssuer Status
- Name: letsencrypt-production-ec2
- Email: rhpds-admins@redhat.com
- Server: https://acme-v02.api.letsencrypt.org/directory
- DNS01 Solver: Route53
- Health: Healthy

### Key Achievements
1. ✅ **Application namespace creation successful**
   - All 5 namespaces created for demo applications
   
2. ✅ **ClusterIssuer deployed and healthy**
   - Ready to issue certificates via Let's Encrypt
   - Route53 DNS-01 challenge configured

3. ✅ **RBAC configured correctly**
   - MCP system can pull images from rhcl-ai-bot namespace

4. ✅ **No dependency errors**
   - Application 1 (operators) provides cert-manager
   - ClusterIssuer CRD available

### Problems Solved
During testing:
- ❌ Initial issue: Namespaces stuck in OutOfSync
  - Root cause: Empty `labels: {}` field caused ArgoCD to detect drift
  - Fix: Removed empty labels field from Namespace manifests
  - Result: All resources synced successfully

### Dependencies Verified
- ✅ Requires Application 1 (operators)
  - cert-manager Operator must be installed
  - ClusterIssuer CRD must be available

### Next Steps
- Application 3: gateways (Gateway, DNSPolicy, TLSPolicy, Certificate)
- Application 4: demo-apps (Deployments, Services, ConfigMaps)
- Application 5: routes-and-policies (HTTPRoute, AuthPolicy, RateLimitPolicy)
- Application 6: observability (Grafana, Dashboards, OpenTelemetry)

### Test Artifacts
- Application manifest: `test-app-2-platform-crs.yaml`
- Repository: https://github.com/kamorisan/demo-rhcl.git
- Branch: feature/split-applications
- Path: gitops/apps/rhcl-demo-split/app-2-platform-crs
