#!/bin/bash

# カラー出力用
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

NAMESPACE=${NAMESPACE:-rhcl-ai-bot}
APP_NAME=${APP_NAME:-rhcl-chatbot}

echo -e "${GREEN}=========================================="
echo -e "  RHCL Chatbot - Status"
echo -e "==========================================${NC}"
echo ""
echo "Namespace: $NAMESPACE"
echo "App Name:  $APP_NAME"
echo ""

# Deployment状態
echo -e "${YELLOW}=== Deployment ===${NC}"
oc get deployment $APP_NAME -n $NAMESPACE 2>/dev/null || echo "Not found"
echo ""

# Pod状態
echo -e "${YELLOW}=== Pods ===${NC}"
oc get pods -n $NAMESPACE -l app=$APP_NAME 2>/dev/null || echo "Not found"
echo ""

# Service状態
echo -e "${YELLOW}=== Service ===${NC}"
oc get service $APP_NAME -n $NAMESPACE 2>/dev/null || echo "Not found"
echo ""

# Route状態
echo -e "${YELLOW}=== Route ===${NC}"
oc get route $APP_NAME -n $NAMESPACE 2>/dev/null || echo "Not found"
echo ""

# URL取得
ROUTE_URL=$(oc get route $APP_NAME -n $NAMESPACE -o jsonpath='{.spec.host}' 2>/dev/null)
if [ -n "$ROUTE_URL" ]; then
    echo -e "${GREEN}Application URL:${NC}"
    echo "  https://$ROUTE_URL"
    echo ""
fi

# イベント
echo -e "${YELLOW}=== Recent Events ===${NC}"
oc get events -n $NAMESPACE --sort-by='.lastTimestamp' | tail -10
echo ""

# 最新ログ
echo -e "${YELLOW}=== Recent Logs (last 20 lines) ===${NC}"
oc logs -n $NAMESPACE deployment/$APP_NAME --tail=20 2>/dev/null || echo "No logs available"
