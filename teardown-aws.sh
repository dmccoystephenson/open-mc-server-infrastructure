#!/bin/bash
set -euo pipefail

# teardown-aws.sh — Remove all AWS resources created by deploy-aws.sh
#
# Terminates the EC2 instance tagged 'omcsi-server', deletes the 'omcsi-sg'
# security group, and deletes the 'omcsi-key' key pair.  Optionally removes
# the local key file and known_hosts file.
#
# Usage:
#   ./teardown-aws.sh [OPTIONS]
#
# Options:
#   --key-file PATH   Path to the local SSH key file (default: ./omcsi-key.pem)
#   --yes             Skip the confirmation prompt
#   --dry-run         Show what would be deleted without making any changes
#   --help            Show this help message

cd "$(dirname "$0")"

# ─── Colour helpers ──────────────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1" >&2; }
log_step()    { echo -e "\n${BOLD}${BLUE}──────────────────────────────────────${NC}"; echo -e "${BOLD}$1${NC}"; echo -e "${BOLD}${BLUE}──────────────────────────────────────${NC}"; }

# ─── Constants (must match deploy-aws.sh) ────────────────────────────────────

KEY_NAME="omcsi-key"
SG_NAME="omcsi-sg"
INSTANCE_TAG="omcsi-server"

# ─── Defaults ────────────────────────────────────────────────────────────────

KEY_FILE="./omcsi-key.pem"
YES=false
DRY_RUN=false

# ─── Usage ───────────────────────────────────────────────────────────────────

usage() {
    cat <<EOF
${BOLD}Usage:${NC} $0 [OPTIONS]

Removes all AWS resources created by deploy-aws.sh:
  - EC2 instance tagged '${INSTANCE_TAG}'
  - Security group '${SG_NAME}'
  - Key pair '${KEY_NAME}'
  - Local key file and known_hosts file

${BOLD}Options:${NC}
  --key-file PATH   Path to the local SSH private key file (default: ./omcsi-key.pem)
  --yes             Skip the confirmation prompt
  --dry-run         Show what would be deleted without making any changes
  --help            Show this help message

${BOLD}WARNING:${NC}
  This is a destructive, irreversible operation.  The EC2 instance and its
  attached EBS volume will be permanently terminated.  Back up any data
  you need before running this script.

${BOLD}Example:${NC}
  $0 --yes
  $0 --dry-run
EOF
}

# ─── Argument parsing ────────────────────────────────────────────────────────

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --key-file) KEY_FILE="$2"; shift 2 ;;
            --yes|-y)   YES=true;      shift   ;;
            --dry-run)  DRY_RUN=true;  shift   ;;
            --help|-h)  usage; exit 0           ;;
            *) log_error "Unknown option: $1"; usage; exit 1 ;;
        esac
    done
}

# ─── Prerequisite checks ─────────────────────────────────────────────────────

check_prerequisites() {
    log_step "Checking prerequisites"

    local missing=false
    for cmd in aws jq; do
        if ! command -v "$cmd" &>/dev/null; then
            log_error "Required tool not found: $cmd"
            missing=true
        else
            log_success "$cmd is available"
        fi
    done

    if [[ "$missing" == "true" ]]; then
        log_error "Install the missing tools and try again."
        echo "  - AWS CLI v2: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html"
        echo "  - jq:         https://jqlang.github.io/jq/download/"
        exit 1
    fi

    if ! aws sts get-caller-identity &>/dev/null; then
        log_error "AWS credentials are not configured or are invalid."
        log_info  "Run: aws configure"
        exit 1
    fi
    log_success "AWS credentials are valid"

    REGION=$(aws configure get region || true)
    if [[ -z "$REGION" ]]; then
        log_error "No AWS default region configured. Run: aws configure"
        exit 1
    fi
    log_success "AWS region: $REGION"
}

# ─── Discovery ───────────────────────────────────────────────────────────────

discover_resources() {
    log_step "Discovering resources"

    INSTANCE_ID=$(aws ec2 describe-instances \
        --filters \
            "Name=tag:Name,Values=${INSTANCE_TAG}" \
            "Name=instance-state-name,Values=pending,running,stopping,stopped" \
        --query 'Reservations[0].Instances[0].InstanceId' \
        --output text 2>/dev/null | grep -v '^None$' || true)

    SG_ID=$(aws ec2 describe-security-groups \
        --filters "Name=group-name,Values=${SG_NAME}" \
        --query 'SecurityGroups[0].GroupId' \
        --output text 2>/dev/null | grep -v '^None$' || true)

    KEY_PAIR_EXISTS=$(aws ec2 describe-key-pairs \
        --key-names "$KEY_NAME" \
        --query 'KeyPairs[0].KeyName' \
        --output text 2>/dev/null | grep -v '^None$' || true)

    echo ""
    if [[ -n "$INSTANCE_ID" ]]; then
        local state
        state=$(aws ec2 describe-instances \
            --instance-ids "$INSTANCE_ID" \
            --query 'Reservations[0].Instances[0].State.Name' \
            --output text)
        log_info "EC2 instance:   $INSTANCE_ID  (state: $state)"
    else
        log_info "EC2 instance:   not found"
    fi

    if [[ -n "$SG_ID" ]]; then
        log_info "Security group: $SG_ID  ($SG_NAME)"
    else
        log_info "Security group: not found"
    fi

    if [[ -n "$KEY_PAIR_EXISTS" ]]; then
        log_info "Key pair:       $KEY_NAME"
    else
        log_info "Key pair:       not found"
    fi

    if [[ -f "$KEY_FILE" ]]; then
        log_info "Local key file: $KEY_FILE"
    else
        log_info "Local key file: not found ($KEY_FILE)"
    fi

    local known_hosts_file="${KEY_FILE}.known_hosts"
    if [[ -f "$known_hosts_file" ]]; then
        log_info "Known hosts:    $known_hosts_file"
    else
        log_info "Known hosts:    not found ($known_hosts_file)"
    fi

    if [[ -z "$INSTANCE_ID" && -z "$SG_ID" && -z "$KEY_PAIR_EXISTS" && ! -f "$KEY_FILE" ]]; then
        log_info ""
        log_success "No OMCSI resources found — nothing to tear down."
        exit 0
    fi
}

# ─── Confirmation ─────────────────────────────────────────────────────────────

confirm() {
    if [[ "$DRY_RUN" == "true" ]]; then
        echo ""
        log_warning "DRY RUN — no changes will be made."
        return
    fi

    if [[ "$YES" == "true" ]]; then
        return
    fi

    echo ""
    log_warning "This will PERMANENTLY delete the resources listed above."
    log_warning "The EC2 instance and its EBS volume will be terminated and cannot be recovered."
    echo ""
    printf "Type 'yes' to confirm: "
    local answer
    IFS= read -r answer
    if [[ "$answer" != "yes" ]]; then
        log_info "Teardown cancelled."
        exit 0
    fi
}

# ─── Teardown steps ───────────────────────────────────────────────────────────

terminate_instance() {
    if [[ -z "$INSTANCE_ID" ]]; then
        log_info "No EC2 instance to terminate."
        return
    fi

    log_step "Terminating EC2 instance"

    if [[ "$DRY_RUN" == "true" ]]; then
        log_warning "[DRY RUN] Would terminate instance $INSTANCE_ID"
        return
    fi

    log_info "Terminating $INSTANCE_ID..."
    aws ec2 terminate-instances --instance-ids "$INSTANCE_ID" >/dev/null
    log_info "Waiting for instance to reach 'terminated' state (this may take ~2 minutes)..."
    aws ec2 wait instance-terminated --instance-ids "$INSTANCE_ID"
    log_success "Instance $INSTANCE_ID terminated"
}

delete_security_group() {
    if [[ -z "$SG_ID" ]]; then
        log_info "No security group to delete."
        return
    fi

    log_step "Deleting security group"

    if [[ "$DRY_RUN" == "true" ]]; then
        log_warning "[DRY RUN] Would delete security group $SG_ID ($SG_NAME)"
        return
    fi

    # Retry a few times — AWS may take a moment after instance termination
    # before releasing the security group.
    local attempts=0
    local max_attempts=6
    while (( attempts < max_attempts )); do
        if aws ec2 delete-security-group --group-id "$SG_ID" 2>/dev/null; then
            log_success "Security group $SG_ID deleted"
            return
        fi
        (( ++attempts ))
        log_info "Security group still in use — retrying in 10 s (${attempts}/${max_attempts})..."
        sleep 10
    done
    log_warning "Could not delete security group $SG_ID after ${max_attempts} attempts."
    log_warning "It may still be attached to another resource. Delete it manually:"
    log_warning "  aws ec2 delete-security-group --group-id $SG_ID"
}

delete_key_pair() {
    if [[ -z "$KEY_PAIR_EXISTS" ]]; then
        log_info "No AWS key pair to delete."
        return
    fi

    log_step "Deleting key pair"

    if [[ "$DRY_RUN" == "true" ]]; then
        log_warning "[DRY RUN] Would delete key pair '$KEY_NAME'"
        return
    fi

    aws ec2 delete-key-pair --key-name "$KEY_NAME"
    log_success "Key pair '$KEY_NAME' deleted from AWS"
}

remove_local_files() {
    log_step "Removing local files"

    local known_hosts_file="${KEY_FILE}.known_hosts"

    if [[ "$DRY_RUN" == "true" ]]; then
        [[ -f "$KEY_FILE" ]]          && log_warning "[DRY RUN] Would remove $KEY_FILE"
        [[ -f "$known_hosts_file" ]]  && log_warning "[DRY RUN] Would remove $known_hosts_file"
        return
    fi

    if [[ -f "$KEY_FILE" ]]; then
        rm -f "$KEY_FILE"
        log_success "Removed $KEY_FILE"
    else
        log_info "Local key file not found — skipping ($KEY_FILE)"
    fi

    if [[ -f "$known_hosts_file" ]]; then
        rm -f "$known_hosts_file"
        log_success "Removed $known_hosts_file"
    else
        log_info "Known hosts file not found — skipping ($known_hosts_file)"
    fi
}

# ─── Main ─────────────────────────────────────────────────────────────────────

main() {
    echo ""
    echo -e "${BOLD}============================================${NC}"
    echo -e "${BOLD}  OMCSI AWS Teardown Script${NC}"
    echo -e "${BOLD}============================================${NC}"
    echo ""

    parse_args "$@"
    check_prerequisites
    discover_resources
    confirm
    terminate_instance
    delete_security_group
    delete_key_pair
    remove_local_files

    echo ""
    if [[ "$DRY_RUN" == "true" ]]; then
        echo -e "${BOLD}${YELLOW}============================================${NC}"
        echo -e "${BOLD}${YELLOW}  Dry run complete — no changes made.${NC}"
        echo -e "${BOLD}${YELLOW}============================================${NC}"
    else
        echo -e "${BOLD}${GREEN}============================================${NC}"
        echo -e "${BOLD}${GREEN}  Teardown complete!${NC}"
        echo -e "${BOLD}${GREEN}============================================${NC}"
        echo ""
        log_info "All OMCSI AWS resources have been removed."
        log_info "To redeploy, run: ./deploy-aws.sh"
    fi
    echo ""
}

main "$@"
