#!/bin/bash
set -euo pipefail

# Validate required environment variables
REQUIRED_VARS=(
  "ENV_NAME"
  "CLUSTER_ID"
  "EXTERNAL_BASE_DOMAIN"
  "AWS_ACCESS_KEY_ID"
  "AWS_SECRET_ACCESS_KEY"
  "AWS_REGION"
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
  echo "Please set all required variables before running install-split-apps.sh:"
  echo "  export ENV_NAME=sandbox2689"
  echo "  export CLUSTER_ID=w6q2p"
  echo "  export EXTERNAL_BASE_DOMAIN=sandbox2689.opentlc.com"
  echo "  export AWS_ACCESS_KEY_ID=..."
  echo "  export AWS_SECRET_ACCESS_KEY=..."
  echo "  export AWS_REGION=us-east-2"
  exit 1
fi

# Run Ansible playbook
ansible-playbook ansible/playbooks/install-split-apps.yaml \
  -e env_name="${ENV_NAME}" \
  -e cluster_id="${CLUSTER_ID}" \
  -e external_base_domain="${EXTERNAL_BASE_DOMAIN}" \
  -e aws_access_key_id="${AWS_ACCESS_KEY_ID}" \
  -e aws_secret_access_key="${AWS_SECRET_ACCESS_KEY}" \
  -e aws_region="${AWS_REGION}"
