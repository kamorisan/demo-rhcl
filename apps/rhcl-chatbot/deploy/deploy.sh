#!/bin/bash
set -e

# カラー出力用
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# デフォルト値
NAMESPACE=${NAMESPACE:-rhcl-ai-bot}
APP_NAME=${APP_NAME:-rhcl-chatbot}
IMAGE_TAG=${IMAGE_TAG:-1.0.0-SNAPSHOT}

# 環境変数チェック
check_env() {
    if [ -z "$OPENAI_API_KEY" ]; then
        echo -e "${RED}Error: OPENAI_API_KEY is not set${NC}"
        echo "Please set the environment variable:"
        echo "  export OPENAI_API_KEY=sk-xxxxx"
        exit 1
    fi
}

# OpenShiftログインチェック
check_oc_login() {
    if ! oc whoami &> /dev/null; then
        echo -e "${RED}Error: Not logged in to OpenShift${NC}"
        echo "Please login first:"
        echo "  oc login --server=https://api.cluster.example.com:6443"
        exit 1
    fi
    echo -e "${GREEN}✓ Logged in as: $(oc whoami)${NC}"
}

# プロジェクト作成
create_project() {
    if oc get project $NAMESPACE &> /dev/null; then
        echo -e "${YELLOW}Project '$NAMESPACE' already exists${NC}"
    else
        echo -e "${GREEN}Creating project '$NAMESPACE'...${NC}"
        oc new-project $NAMESPACE
    fi
    oc project $NAMESPACE
}

# Secret作成
create_secret() {
    echo -e "${GREEN}Creating/updating secret...${NC}"

    # 既存のSecretを削除
    oc delete secret rhcl-chatbot-secret -n $NAMESPACE --ignore-not-found=true

    # 新しいSecretを作成
    oc create secret generic rhcl-chatbot-secret \
        --from-literal=openai-api-key=$OPENAI_API_KEY \
        -n $NAMESPACE

    echo -e "${GREEN}✓ Secret created${NC}"
}

# イメージビルド
build_image() {
    echo -e "${GREEN}Building container image...${NC}"

    # Git URLとブランチを取得
    GIT_URL=${GIT_URL:-$(git config --get remote.origin.url 2>/dev/null || echo "")}
    GIT_BRANCH=${GIT_BRANCH:-$(git branch --show-current 2>/dev/null || echo "main")}
    GIT_CONTEXT_DIR=${GIT_CONTEXT_DIR:-"apps/rhcl-chatbot"}

    if [ -z "$GIT_URL" ]; then
        echo -e "${RED}Error: Git repository URL not found${NC}"
        echo "Please set GIT_URL environment variable or ensure you are in a git repository"
        exit 1
    fi

    echo "Git URL: $GIT_URL"
    echo "Git Branch: $GIT_BRANCH"
    echo "Context Dir: $GIT_CONTEXT_DIR"
    echo ""

    # BuildConfigが存在するかチェック
    if oc get bc $APP_NAME -n $NAMESPACE &> /dev/null; then
        # 既存のBuildConfigの種類を確認
        BUILD_TYPE=$(oc get bc $APP_NAME -n $NAMESPACE -o jsonpath='{.spec.source.type}')
        if [ "$BUILD_TYPE" = "Binary" ]; then
            echo -e "${YELLOW}Existing BuildConfig is Binary type, recreating as Git...${NC}"
            oc delete bc $APP_NAME -n $NAMESPACE
            sleep 2
        fi
    fi

    # BuildConfigが存在しない、または削除された場合は新規作成
    if ! oc get bc $APP_NAME -n $NAMESPACE &> /dev/null; then
        echo -e "${GREEN}Creating BuildConfig from Git...${NC}"

        # Git URL with branch
        GIT_URL_WITH_BRANCH="${GIT_URL}#${GIT_BRANCH}"

        oc new-build $GIT_URL_WITH_BRANCH \
            --name $APP_NAME \
            --context-dir=$GIT_CONTEXT_DIR \
            --strategy=docker \
            -n $NAMESPACE
    else
        echo -e "${YELLOW}BuildConfig already exists, updating source...${NC}"
        oc patch bc/$APP_NAME -n $NAMESPACE -p "{\"spec\":{\"source\":{\"git\":{\"uri\":\"$GIT_URL\",\"ref\":\"$GIT_BRANCH\"}}}}"
    fi

    # ビルド実行
    echo -e "${GREEN}Starting build from Git repository...${NC}"
    oc start-build $APP_NAME --follow -n $NAMESPACE

    echo -e "${GREEN}✓ Image built successfully${NC}"
}

# デプロイメント適用
deploy_app() {
    echo -e "${GREEN}Deploying application...${NC}"

    # YAMLファイルのパスを取得
    YAML_FILE="$(dirname "$0")/rhcl-chatbot.yaml"

    # YAMLファイルを一時的にコピーして、イメージ名を更新
    TMP_YAML="/tmp/rhcl-chatbot-deploy.yaml"
    sed "s|image: rhcl-chatbot:1.0.0-SNAPSHOT|image: image-registry.openshift-image-registry.svc:5000/$NAMESPACE/$APP_NAME:latest|g" \
        "$YAML_FILE" > "$TMP_YAML"

    # デプロイ実行
    oc apply -f "$TMP_YAML" -n $NAMESPACE

    # 一時ファイル削除
    rm -f "$TMP_YAML"

    echo -e "${GREEN}✓ Application deployed${NC}"
}

# デプロイメント確認
check_deployment() {
    echo -e "${GREEN}Waiting for deployment to be ready...${NC}"

    # Podが起動するまで待機
    oc wait --for=condition=available --timeout=300s \
        deployment/$APP_NAME -n $NAMESPACE || true

    # Pod状態確認
    echo ""
    echo "=== Pod Status ==="
    oc get pods -n $NAMESPACE -l app=$APP_NAME

    # Route URL取得
    echo ""
    echo "=== Application URL ==="
    ROUTE_URL=$(oc get route $APP_NAME -n $NAMESPACE -o jsonpath='{.spec.host}' 2>/dev/null || echo "Route not found")
    if [ "$ROUTE_URL" != "Route not found" ]; then
        echo -e "${GREEN}https://$ROUTE_URL${NC}"
    else
        echo -e "${YELLOW}Route not yet created${NC}"
    fi
}

# ログ表示
show_logs() {
    echo ""
    echo "=== Recent Logs ==="
    oc logs -n $NAMESPACE deployment/$APP_NAME --tail=50 || echo "No logs available yet"
}

# メイン処理
main() {
    echo "=========================================="
    echo "  RHCL Chatbot - OpenShift Deployment"
    echo "=========================================="
    echo ""
    echo "Namespace: $NAMESPACE"
    echo "App Name:  $APP_NAME"
    echo "Image Tag: $IMAGE_TAG"
    echo ""

    check_env
    check_oc_login
    create_project
    create_secret
    build_image
    deploy_app
    check_deployment
    show_logs

    echo ""
    echo -e "${GREEN}=========================================="
    echo -e "  Deployment Complete!"
    echo -e "==========================================${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Check logs:  oc logs -f deployment/$APP_NAME -n $NAMESPACE"
    echo "  2. Get URL:     oc get route $APP_NAME -n $NAMESPACE"
    echo "  3. Access WebUI at the route URL"
    echo ""
}

# ヘルプ表示
show_help() {
    cat << EOF
Usage: $0 [OPTIONS]

Deploy RHCL Chatbot to OpenShift

OPTIONS:
    -h, --help          Show this help message
    -n, --namespace     Namespace (default: rhcl-ai-bot)
    -a, --app-name      Application name (default: rhcl-chatbot)
    -t, --tag           Image tag (default: 1.0.0-SNAPSHOT)

ENVIRONMENT VARIABLES:
    OPENAI_API_KEY      Required. OpenAI API key
    NAMESPACE           Optional. Override default namespace
    APP_NAME            Optional. Override default app name
    IMAGE_TAG           Optional. Override default image tag

EXAMPLES:
    # Basic deployment
    export OPENAI_API_KEY=sk-xxxxx
    $0

    # Custom namespace
    NAMESPACE=my-project $0

    # With all options
    $0 -n my-project -a my-chatbot -t v1.0.0

PREREQUISITES:
    - oc CLI installed
    - Logged in to OpenShift cluster
    - OPENAI_API_KEY environment variable set

EOF
}

# 引数パース
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -n|--namespace)
            NAMESPACE="$2"
            shift 2
            ;;
        -a|--app-name)
            APP_NAME="$2"
            shift 2
            ;;
        -t|--tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use -h or --help for usage information"
            exit 1
            ;;
    esac
done

# メイン実行
main
