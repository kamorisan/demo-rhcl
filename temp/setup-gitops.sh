#!/bin/bash
set -euo pipefail

# Setup OpenShift GitOps Operator
# This script must be run before install-split-apps.sh

echo "========================================="
echo "Setting up OpenShift GitOps Operator"
echo "========================================="

# Run Ansible playbook
ansible-playbook ansible/playbooks/setup-gitops.yaml

echo ""
echo "========================================="
echo "OpenShift GitOps setup complete!"
echo "========================================="
echo ""
echo "Next step: Run install-split-apps.sh with required environment variables"
echo "  export ENV_NAME=..."
echo "  export CLUSTER_ID=..."
echo "  export EXTERNAL_BASE_DOMAIN=..."
echo "  export AWS_ACCESS_KEY_ID=..."
echo "  export AWS_SECRET_ACCESS_KEY=..."
echo "  export AWS_REGION=..."
echo "  ./install-split-apps.sh"
