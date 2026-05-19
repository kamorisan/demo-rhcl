#!/usr/bin/env bash
set -euo pipefail

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }; }

need python3
need oc

check_env_vars() {
  local missing=()

  [[ -z "${ENV_NAME:-}" ]] && missing+=("ENV_NAME")
  [[ -z "${CLUSTER_ID:-}" ]] && missing+=("CLUSTER_ID")
  [[ -z "${EXTERNAL_BASE_DOMAIN:-}" ]] && missing+=("EXTERNAL_BASE_DOMAIN")
  [[ -z "${AWS_ACCESS_KEY_ID:-}" ]] && missing+=("AWS_ACCESS_KEY_ID")
  [[ -z "${AWS_SECRET_ACCESS_KEY:-}" ]] && missing+=("AWS_SECRET_ACCESS_KEY")
  [[ -z "${AWS_REGION:-}" ]] && missing+=("AWS_REGION")
  [[ -z "${RHCL_AI_OPENAI_API_KEY:-}" ]] && missing+=("RHCL_AI_OPENAI_API_KEY")

  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "ERROR: Missing required environment variables:" >&2
    printf '  - %s\n' "${missing[@]}" >&2
    echo >&2
    echo "Please set all required variables before running install-applicationset.sh:" >&2
    echo "  export ENV_NAME=sandbox589" >&2
    echo "  export CLUSTER_ID=cftzv" >&2
    echo "  export EXTERNAL_BASE_DOMAIN=sandbox589.opentlc.com" >&2
    echo "  export AWS_ACCESS_KEY_ID=..." >&2
    echo "  export AWS_SECRET_ACCESS_KEY=..." >&2
    echo "  export AWS_REGION=us-east-2" >&2
    echo "  export RHCL_AI_OPENAI_API_KEY=sk-..." >&2
    echo >&2
    echo "Optional:" >&2
    echo "  export RHCL_REQUIRED_CONTEXT=\"\"  # Disable context check" >&2
    exit 1
  fi
}

check_env_vars

require_context() {
  local expected="${1:-}"
  [[ -n "${expected}" ]] || return 0
  local current
  current="$(oc config current-context 2>/dev/null || true)"
  if [[ "${current}" != "${expected}" ]]; then
    echo "ERROR: oc context must be '${expected}' (current: '${current:-<none>}')." >&2
    echo "Run: oc config use-context ${expected}" >&2
    exit 1
  fi
}

# Context check disabled by default for ApplicationSet mode (multi-environment)
# Set RHCL_REQUIRED_CONTEXT to enforce a specific context
if [[ "${RHCL_REQUIRED_CONTEXT+set}" != "set" ]]; then
  RHCL_REQUIRED_CONTEXT=""
fi

if [[ -n "${RHCL_REQUIRED_CONTEXT}" ]]; then
  require_context "${RHCL_REQUIRED_CONTEXT}"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="${VENV_DIR:-${ROOT_DIR}/.venv}"

if [[ ! -d "${VENV_DIR}" ]]; then
  python3 -m venv "${VENV_DIR}"
fi

# shellcheck disable=SC1090
source "${VENV_DIR}/bin/activate"

python3 -m pip install --upgrade pip >/dev/null
python3 -m pip install -r "${ROOT_DIR}/ansible/requirements.txt" >/dev/null

export ANSIBLE_ROLES_PATH="${ROOT_DIR}/ansible/roles:${ANSIBLE_ROLES_PATH:-}"
exec ansible-playbook -i localhost, -c local "${ROOT_DIR}/ansible/playbooks/install-applicationset.yaml" "$@"
