#!/bin/bash
set -euo pipefail

# Complete setup: GitOps operator + Applications 1-5
# This script automates the full installation process

# Validate required environment variables
REQUIRED_VARS=(
  "ENV_NAME"
  "CLUSTER_ID"
  "EXTERNAL_BASE_DOMAIN"
  "AWS_ACCESS_KEY_ID"
  "AWS_SECRET_ACCESS_KEY"
  "AWS_REGION"
  "RHCL_AI_OPENAI_API_KEY"
)

MISSING_VARS=()
for var in "${REQUIRED_VARS[@]}"; do
  if [ -z "${!var:-}" ]; then
    MISSING_VARS+=("  - $var")
  fi
done

if [ ${#MISSING_VARS[@]} -gt 0 ]; then
  echo "ERROR: Missing required environment variables:"
  printf '%s\n' "${MISSING_VARS[@]}"
  echo ""
  echo "Please set all required variables before running setup-and-install.sh:"
  echo "  export ENV_NAME=sandbox483"
  echo "  export CLUSTER_ID=6lrlm"
  echo "  export EXTERNAL_BASE_DOMAIN=sandbox483.opentlc.com"
  echo "  export AWS_ACCESS_KEY_ID=..."
  echo "  export AWS_SECRET_ACCESS_KEY=..."
  echo "  export AWS_REGION=us-east-2"
  echo "  export RHCL_AI_OPENAI_API_KEY=sk-..."
  exit 1
fi

echo "========================================="
echo "Step 1: Installing OpenShift GitOps Operator"
echo "========================================="

# Install GitOps operator
ansible-playbook ansible/playbooks/setup-gitops.yaml

echo ""
echo "========================================="
echo "Step 2: Deploying Applications 1-6"
echo "========================================="

# Deploy Applications 1-5
ansible-playbook ansible/playbooks/install-split-apps.yaml \
  -e env_name="${ENV_NAME}" \
  -e cluster_id="${CLUSTER_ID}" \
  -e external_base_domain="${EXTERNAL_BASE_DOMAIN}" \
  -e aws_access_key_id="${AWS_ACCESS_KEY_ID}" \
  -e aws_secret_access_key="${AWS_SECRET_ACCESS_KEY}" \
  -e aws_region="${AWS_REGION}" \
  -e openai_api_key="${RHCL_AI_OPENAI_API_KEY}"

echo ""
echo "========================================="
echo "Installation Complete!"
echo "========================================="
echo ""
echo "Deployed Applications:"
echo "  1. Operators (Service Mesh, RHCL, Kuadrant, MCP Gateway, RHBK, OpenTelemetry, Grafana, Tempo)"
echo "  2. Platform CRs (ClusterIssuer, Istio, Kuadrant)"
echo "  3. Gateways (4 Gateways + DNS/TLS Policies)"
echo "  4. Demo Apps (rhcl-ai-bot, Keycloak, UI, API, etc.)"
echo "  5. Routes & Policies (28 HTTPRoutes + Auth/OIDC Policies)"
echo "  6-1. Core Observability (Tempo distributed tracing + OpenTelemetry Collector + Grafana)"
echo "    6-2. Kuadrant Observability (ServiceMonitors, PrometheusRules)"
echo "    6-3. Kuadrant Dashboards (Grafana ConfigMaps)"
echo ""
echo "Application URLs:"
echo "  Workshop: https://rhcl-workshop.${EXTERNAL_BASE_DOMAIN}/"
echo "  OIDC:     https://oidc-rhcl-workshop.${EXTERNAL_BASE_DOMAIN}/"
echo "  AI Bot:   https://ai.${EXTERNAL_BASE_DOMAIN}/"
echo "  MCP:      https://mcp.${EXTERNAL_BASE_DOMAIN}/"
echo "  Ext API:  https://external-api.${EXTERNAL_BASE_DOMAIN}/"
echo "  Grafana:  https://grafana.apps.cluster-${CLUSTER_ID}.${CLUSTER_ID}.${EXTERNAL_BASE_DOMAIN}/"
echo ""
echo "ArgoCD Console:"
echo "  oc get route openshift-gitops-server -n openshift-gitops -o jsonpath='{.spec.host}'"
echo ""
