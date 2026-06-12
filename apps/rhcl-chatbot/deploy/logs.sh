#!/bin/bash

# カラー出力用
GREEN='\033[0;32m'
NC='\033[0m'

NAMESPACE=${NAMESPACE:-rhcl-ai-bot}
APP_NAME=${APP_NAME:-rhcl-chatbot}

echo -e "${GREEN}Fetching logs from $APP_NAME in namespace $NAMESPACE...${NC}"
echo ""

oc logs -f deployment/$APP_NAME -n $NAMESPACE
