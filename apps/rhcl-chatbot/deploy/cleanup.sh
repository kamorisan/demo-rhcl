#!/bin/bash
set -e

# カラー出力用
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

NAMESPACE=${NAMESPACE:-rhcl-ai-bot}
APP_NAME=${APP_NAME:-rhcl-chatbot}

echo -e "${YELLOW}=========================================="
echo -e "  RHCL Chatbot - Cleanup"
echo -e "==========================================${NC}"
echo ""
echo "Namespace: $NAMESPACE"
echo "App Name:  $APP_NAME"
echo ""

# 確認
read -p "Are you sure you want to delete all resources? (yes/no): " -r
echo
if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    echo -e "${GREEN}Cleanup cancelled${NC}"
    exit 0
fi

echo -e "${YELLOW}Deleting resources...${NC}"

# Deployment削除
if oc get deployment $APP_NAME -n $NAMESPACE &> /dev/null; then
    echo "Deleting Deployment..."
    oc delete deployment $APP_NAME -n $NAMESPACE
fi

# Service削除
if oc get service $APP_NAME -n $NAMESPACE &> /dev/null; then
    echo "Deleting Service..."
    oc delete service $APP_NAME -n $NAMESPACE
fi

# Route削除
if oc get route $APP_NAME -n $NAMESPACE &> /dev/null; then
    echo "Deleting Route..."
    oc delete route $APP_NAME -n $NAMESPACE
fi

# Secret削除
if oc get secret rhcl-chatbot-secret -n $NAMESPACE &> /dev/null; then
    echo "Deleting Secret..."
    oc delete secret rhcl-chatbot-secret -n $NAMESPACE
fi

# BuildConfig削除
if oc get bc $APP_NAME -n $NAMESPACE &> /dev/null; then
    echo "Deleting BuildConfig..."
    oc delete bc $APP_NAME -n $NAMESPACE
fi

# ImageStream削除
if oc get is $APP_NAME -n $NAMESPACE &> /dev/null; then
    echo "Deleting ImageStream..."
    oc delete is $APP_NAME -n $NAMESPACE
fi

echo ""
echo -e "${GREEN}✓ Cleanup complete${NC}"
echo ""

# プロジェクト削除の確認
read -p "Do you want to delete the entire project '$NAMESPACE'? (yes/no): " -r
echo
if [[ $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    echo "Deleting project..."
    oc delete project $NAMESPACE
    echo -e "${GREEN}✓ Project deleted${NC}"
else
    echo -e "${GREEN}Project '$NAMESPACE' kept${NC}"
fi
