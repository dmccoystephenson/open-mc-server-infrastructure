#!/bin/bash
set -euo pipefail

# deploy-aws.sh — Automated AWS deployment for Open MC Server Infrastructure
#
# Provisions a new EC2 instance (or reuses an existing one tagged 'omcsi-server')
# and deploys the full OMCSI stack on it, including Docker installation, repository
# clone, .env configuration, and stack startup.
#
# Usage:
#   ./deploy-aws.sh [OPTIONS]
#
# Options:
#   --operator-uuid UUID         Minecraft player UUID (required)
#   --operator-name NAME         Minecraft player username (required)
#   --rcon-password PASSWORD     RCON password (required)
#   --admin-username USERNAME    Web dashboard admin username (required)
#   --admin-password PASSWORD    Web dashboard admin password (required)
#   --instance-type TYPE         EC2 instance type (default: t3.medium)
#   --key-file PATH              Path to store/load the SSH key (default: ./omcsi-key.pem)
#   --help                       Show this help message

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

# ─── Constants ───────────────────────────────────────────────────────────────

KEY_NAME="omcsi-key"
SG_NAME="omcsi-sg"
INSTANCE_TAG="omcsi-server"
REPO_URL="https://github.com/dmccoystephenson/open-mc-server-infrastructure.git"
REMOTE_DIR="/home/ubuntu/open-mc-server-infrastructure"
UBUNTU_OWNER="099720109477"   # Canonical's AWS account ID

# ─── Defaults (overridable via flags) ────────────────────────────────────────

OPERATOR_UUID=""
OPERATOR_NAME=""
RCON_PASSWORD=""
ADMIN_USERNAME=""
ADMIN_PASSWORD=""
INSTANCE_TYPE="t3.medium"
KEY_FILE="./omcsi-key.pem"

# ─── Usage ───────────────────────────────────────────────────────────────────

usage() {
    cat <<EOF
${BOLD}Usage:${NC} $0 [OPTIONS]

Provisions an EC2 instance (if one does not already exist) and deploys the
Open Minecraft Server Infrastructure stack on it.

${BOLD}Options:${NC}
  --operator-uuid UUID         Minecraft player UUID (required)
  --operator-name NAME         Minecraft player username (required)
  --rcon-password PASSWORD     RCON password (required; use a strong value)
  --admin-username USERNAME    Web dashboard admin username (required)
  --admin-password PASSWORD    Web dashboard admin password (required)
  --instance-type TYPE         EC2 instance type (default: t3.medium)
  --key-file PATH              Path to store/load the SSH private key
                               (default: ./omcsi-key.pem)
  --help                       Show this help message

${BOLD}Prerequisites:${NC}
  - AWS CLI v2 installed and configured (aws configure)
  - ssh client available locally
  - jq installed (for JSON parsing)
  - curl available locally (for public IP detection)

${BOLD}Example:${NC}
  $0 \\
    --operator-uuid  "abc123-..." \\
    --operator-name  "YourMCUsername" \\
    --rcon-password  "StrongRconPass!" \\
    --admin-username "admin" \\
    --admin-password "StrongAdminPass!"

For a full walkthrough of what this script does, see AWS-DEPLOYMENT.md.
EOF
}

# ─── Argument parsing ────────────────────────────────────────────────────────

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --operator-uuid)   OPERATOR_UUID="$2";   shift 2 ;;
            --operator-name)   OPERATOR_NAME="$2";   shift 2 ;;
            --rcon-password)   RCON_PASSWORD="$2";   shift 2 ;;
            --admin-username)  ADMIN_USERNAME="$2";  shift 2 ;;
            --admin-password)  ADMIN_PASSWORD="$2";  shift 2 ;;
            --instance-type)   INSTANCE_TYPE="$2";   shift 2 ;;
            --key-file)        KEY_FILE="$2";         shift 2 ;;
            --help|-h)         usage; exit 0 ;;
            *) log_error "Unknown option: $1"; usage; exit 1 ;;
        esac
    done
}

# ─── Interactive prompts (used when values are not supplied via flags) ────────

prompt_if_empty() {
    local var_name="$1"
    local prompt_text="$2"
    local secret="${3:-false}"

    if [[ -z "${!var_name}" ]]; then
        local value
        if [[ "$secret" == "true" ]]; then
            IFS= read -r -s -p "$prompt_text" value
            echo ""
        else
            IFS= read -r -p "$prompt_text" value
        fi
        # Assign value to the named variable via printf -v (avoids SC2229)
        printf -v "$var_name" '%s' "$value"
    fi

    if [[ -z "${!var_name}" ]]; then
        log_error "$var_name is required."
        exit 1
    fi
}

collect_required_inputs() {
    log_step "Configuration"
    echo "Provide the required values for your deployment."
    echo "Tip: get your Minecraft UUID at https://mcuuid.net/"
    echo ""
    prompt_if_empty OPERATOR_UUID   "Minecraft player UUID:         "
    prompt_if_empty OPERATOR_NAME   "Minecraft player username:     "
    prompt_if_empty RCON_PASSWORD   "RCON password (hidden):        " true
    prompt_if_empty ADMIN_USERNAME  "Web dashboard admin username:  "
    prompt_if_empty ADMIN_PASSWORD  "Web dashboard admin password (hidden): " true
}

# ─── Prerequisite checks ─────────────────────────────────────────────────────

check_prerequisites() {
    log_step "Checking prerequisites"

    local missing=false

    for cmd in aws ssh jq curl; do
        if ! command -v "$cmd" &>/dev/null; then
            log_error "Required tool not found: $cmd"
            missing=true
        else
            log_success "$cmd is available"
        fi
    done

    if [[ "$missing" == "true" ]]; then
        echo ""
        log_error "Install the missing tools and try again."
        echo "  - AWS CLI v2: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html"
        echo "  - jq:         https://jqlang.github.io/jq/download/"
        echo "  - curl:       https://curl.se/download.html"
        exit 1
    fi

    # Verify AWS credentials
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

# ─── EC2 provisioning helpers ─────────────────────────────────────────────────

# Detects the caller's public IP address.  Returns empty string and logs a
# warning if both detection services are unreachable.
get_my_public_ip() {
    local ip
    ip=$(curl -sf --max-time 5 https://checkip.amazonaws.com 2>/dev/null \
        || curl -sf --max-time 5 https://api.ipify.org 2>/dev/null \
        || true)
    if [[ -z "$ip" ]]; then
        log_warning "Could not auto-detect public IP (both checkip.amazonaws.com and api.ipify.org were unreachable)."
    fi
    printf '%s' "$ip"
}

# Returns the InstanceId of an existing OMCSI instance (any non-terminated state),
# or empty string if none exists.
find_existing_instance() {
    aws ec2 describe-instances \
        --filters \
            "Name=tag:Name,Values=${INSTANCE_TAG}" \
            "Name=instance-state-name,Values=pending,running,stopping,stopped" \
        --query 'Reservations[0].Instances[0].InstanceId' \
        --output text 2>/dev/null | grep -v '^None$' || true
}

ensure_key_pair() {
    if [[ -f "$KEY_FILE" ]]; then
        log_info "SSH key file already exists: $KEY_FILE"
        # Check whether the corresponding key pair exists in AWS
        local existing
        existing=$(aws ec2 describe-key-pairs \
            --key-names "$KEY_NAME" \
            --query 'KeyPairs[0].KeyName' \
            --output text 2>/dev/null | grep -v '^None$' || true)
        if [[ -n "$existing" ]]; then
            log_success "AWS key pair '$KEY_NAME' already exists — reusing"
            return
        fi
        log_warning "Key file exists locally but key pair not found in AWS — recreating"
    fi

    log_info "Creating AWS key pair '$KEY_NAME'..."
    aws ec2 create-key-pair \
        --key-name "$KEY_NAME" \
        --query 'KeyMaterial' \
        --output text > "$KEY_FILE"
    chmod 400 "$KEY_FILE"
    log_success "Key pair created and saved to $KEY_FILE"
}

ensure_security_group() {
    # Check if a security group with the tag already exists in the default VPC
    local existing_sg
    existing_sg=$(aws ec2 describe-security-groups \
        --filters "Name=group-name,Values=${SG_NAME}" "Name=vpc-id,Values=${VPC_ID}" \
        --query 'SecurityGroups[0].GroupId' \
        --output text 2>/dev/null | grep -v '^None$' || true)

    if [[ -n "$existing_sg" ]]; then
        SG_ID="$existing_sg"
        log_success "Security group '$SG_NAME' already exists: $SG_ID — reusing"
        return
    fi

    log_info "Creating security group '$SG_NAME'..."
    SG_ID=$(aws ec2 create-security-group \
        --group-name "$SG_NAME" \
        --description "Open MC Server Infrastructure security group" \
        --vpc-id "$VPC_ID" \
        --query 'GroupId' \
        --output text)
    log_success "Security group created: $SG_ID"

    log_info "Adding inbound rules..."

    # SSH — restricted to the caller's public IP
    local my_ip
    my_ip=$(get_my_public_ip)
    if [[ -z "$my_ip" ]]; then
        log_warning "SSH rule will allow 0.0.0.0/0 — review the security group and restrict it when your IP is known."
        my_ip="0.0.0.0"
    fi
    local ssh_cidr="${my_ip}/32"
    [[ "$my_ip" == "0.0.0.0" ]] && ssh_cidr="0.0.0.0/0"

    aws ec2 authorize-security-group-ingress --group-id "$SG_ID" \
        --protocol tcp --port 22 --cidr "$ssh_cidr"
    log_info "  SSH (22) allowed from $ssh_cidr"

    aws ec2 authorize-security-group-ingress --group-id "$SG_ID" \
        --protocol tcp --port 25565 --cidr 0.0.0.0/0
    log_info "  Minecraft (25565) open"

    aws ec2 authorize-security-group-ingress --group-id "$SG_ID" \
        --protocol tcp --port 8443 --cidr 0.0.0.0/0
    log_info "  Web dashboard (8443) open"

    aws ec2 authorize-security-group-ingress --group-id "$SG_ID" \
        --protocol tcp --port 8100 --cidr 0.0.0.0/0
    log_info "  BlueMap (8100) open"

    log_success "Inbound rules added"
}

launch_instance() {
    log_info "Looking up latest Ubuntu 22.04 LTS AMI in $REGION..."
    AMI_ID=$(aws ec2 describe-images \
        --owners "$UBUNTU_OWNER" \
        --filters \
            "Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*" \
            "Name=state,Values=available" \
        --query 'sort_by(Images, &CreationDate)[-1].ImageId' \
        --output text)
    log_info "Using AMI: $AMI_ID"

    log_info "Launching EC2 instance ($INSTANCE_TYPE)..."
    INSTANCE_ID=$(aws ec2 run-instances \
        --image-id "$AMI_ID" \
        --instance-type "$INSTANCE_TYPE" \
        --key-name "$KEY_NAME" \
        --security-group-ids "$SG_ID" \
        --block-device-mappings '[{"DeviceName":"/dev/sda1","Ebs":{"VolumeSize":30,"VolumeType":"gp3"}}]' \
        --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${INSTANCE_TAG}}]" \
        --query 'Instances[0].InstanceId' \
        --output text)
    log_success "Launched instance: $INSTANCE_ID"

    log_info "Waiting for instance to reach running state..."
    aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
    log_success "Instance is running"
}

get_public_ip() {
    PUBLIC_IP=$(aws ec2 describe-instances \
        --instance-ids "$INSTANCE_ID" \
        --query 'Reservations[0].Instances[0].PublicIpAddress' \
        --output text)

    if [[ -z "$PUBLIC_IP" || "$PUBLIC_IP" == "None" ]]; then
        log_error "Could not retrieve public IP for instance $INSTANCE_ID."
        exit 1
    fi
    log_success "Public IP: $PUBLIC_IP"
}

provision_ec2() {
    log_step "EC2 Provisioning"

    # Default VPC
    VPC_ID=$(aws ec2 describe-vpcs \
        --filters "Name=isDefault,Values=true" \
        --query 'Vpcs[0].VpcId' \
        --output text)

    if [[ -z "$VPC_ID" || "$VPC_ID" == "None" ]]; then
        log_error "No default VPC found in region $REGION."
        log_info  "Create a default VPC with: aws ec2 create-default-vpc"
        exit 1
    fi
    log_info "Default VPC: $VPC_ID"

    # Check for existing instance
    INSTANCE_ID=$(find_existing_instance)
    if [[ -n "$INSTANCE_ID" ]]; then
        log_success "Found existing instance: $INSTANCE_ID — skipping provisioning"

        # Resume stopped instance if necessary
        INSTANCE_STATE=$(aws ec2 describe-instances \
            --instance-ids "$INSTANCE_ID" \
            --query 'Reservations[0].Instances[0].State.Name' \
            --output text)
        if [[ "$INSTANCE_STATE" == "stopped" ]]; then
            log_info "Instance is stopped — starting it..."
            aws ec2 start-instances --instance-ids "$INSTANCE_ID" >/dev/null
            aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
            log_success "Instance started"
        elif [[ "$INSTANCE_STATE" == "stopping" ]]; then
            log_info "Waiting for instance to finish stopping before restarting..."
            aws ec2 wait instance-stopped --instance-ids "$INSTANCE_ID"
            aws ec2 start-instances --instance-ids "$INSTANCE_ID" >/dev/null
            aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
            log_success "Instance started"
        fi

        # Ensure we have the key file so we can SSH in
        if [[ ! -f "$KEY_FILE" ]]; then
            log_error "Instance $INSTANCE_ID already exists but SSH key file '$KEY_FILE' is missing."
            log_info  "Restore the original key file or terminate the instance and re-run this script."
            exit 1
        fi

        # Retrieve the security group so we can ensure SSH is accessible
        SG_ID=$(aws ec2 describe-instances \
            --instance-ids "$INSTANCE_ID" \
            --query 'Reservations[0].Instances[0].SecurityGroups[0].GroupId' \
            --output text)

        # Ensure the current operator IP has SSH access (the original rule may have
        # used a different IP; duplicate-rule errors from AWS are silently ignored).
        local current_ip
        current_ip=$(get_my_public_ip)
        if [[ -n "$current_ip" ]]; then
            local current_cidr="${current_ip}/32"
            log_info "Ensuring SSH access from $current_cidr..."
            if aws ec2 authorize-security-group-ingress --group-id "$SG_ID" \
                    --protocol tcp --port 22 --cidr "$current_cidr" 2>/dev/null; then
                log_success "SSH ingress rule added for $current_cidr"
            else
                log_info "SSH rule for $current_cidr already exists (or could not be added)"
            fi
        else
            log_warning "Could not detect current public IP — ensure port 22 is open in security group $SG_ID before connecting."
        fi
    else
        ensure_key_pair
        ensure_security_group
        launch_instance
    fi

    get_public_ip
}

# ─── SSH helpers ──────────────────────────────────────────────────────────────

# SSH_OPTS is set in main() after parse_args() so KEY_FILE is final.
SSH_OPTS=""

remote() {
    # Run a command on the remote instance.
    # Arguments are intentionally expanded on the client side (SC2029).
    # shellcheck disable=SC2086,SC2029
    ssh $SSH_OPTS "ubuntu@${PUBLIC_IP}" "$@"
}

remote_script() {
    # Pipe a here-doc script to the remote instance via bash
    # shellcheck disable=SC2086
    ssh $SSH_OPTS "ubuntu@${PUBLIC_IP}" bash -s
}

wait_for_ssh() {
    log_step "Waiting for SSH"
    log_info "This may take up to 4 minutes while the instance initialises..."
    local attempts=0
    local max_attempts=24  # 24 x 10 s = 4 min
    while (( attempts < max_attempts )); do
        # shellcheck disable=SC2086
        if ssh $SSH_OPTS "ubuntu@${PUBLIC_IP}" true 2>/dev/null; then
            log_success "SSH is ready"
            return
        fi
        (( attempts++ ))
        log_info "Retrying SSH (${attempts}/${max_attempts})..."
        sleep 10
    done
    log_error "Timed out waiting for SSH on $PUBLIC_IP"
    exit 1
}

# ─── Instance bootstrap ───────────────────────────────────────────────────────

bootstrap_docker() {
    log_step "Installing Docker on the instance"

    # Check if Docker and the Compose plugin are already installed
    if remote "command -v docker &>/dev/null && docker compose version &>/dev/null 2>&1"; then
        log_success "Docker and Docker Compose plugin are already installed — skipping"
        return
    fi

    log_info "Installing Docker Engine and Docker Compose plugin..."
    remote_script <<'ENDSSH'
set -euo pipefail
sudo apt-get update -qq
sudo apt-get install -y -qq ca-certificates curl gnupg git nano

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
    sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update -qq
sudo apt-get install -y -qq \
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo usermod -aG docker ubuntu
ENDSSH

    log_success "Docker installed"
}

clone_or_update_repo() {
    log_step "Deploying repository"

    if remote "test -d ${REMOTE_DIR}/.git"; then
        log_info "Repository already cloned — pulling latest changes..."
        remote "cd ${REMOTE_DIR} && git pull --ff-only"
        log_success "Repository updated"
    else
        log_info "Cloning repository..."
        remote "git clone ${REPO_URL} ${REMOTE_DIR}"
        log_success "Repository cloned to ${REMOTE_DIR}"
    fi
}

configure_env() {
    log_step "Configuring .env"

    if remote "test -f ${REMOTE_DIR}/.env"; then
        log_info ".env already exists on the instance — skipping creation"
        log_warning "To reconfigure, edit ${REMOTE_DIR}/.env on the instance directly."
        return
    fi

    log_info "Creating .env from sample.env..."
    remote "cp ${REMOTE_DIR}/sample.env ${REMOTE_DIR}/.env"

    log_info "Patching .env with supplied values..."
    # Base64-encode each value locally so that passwords with special characters
    # (quotes, backslashes, dollar signs, etc.) are safe to embed in the heredoc and
    # cross the SSH boundary without any shell-interpretation issues.
    # Base64 output only contains [A-Za-z0-9+/=], which is safe in any shell context.
    local b64_uuid b64_name b64_rcon b64_admin_u b64_admin_p
    b64_uuid=$(printf '%s' "$OPERATOR_UUID" | base64 | tr -d '\n')
    b64_name=$(printf '%s' "$OPERATOR_NAME" | base64 | tr -d '\n')
    b64_rcon=$(printf '%s' "$RCON_PASSWORD" | base64 | tr -d '\n')
    b64_admin_u=$(printf '%s' "$ADMIN_USERNAME" | base64 | tr -d '\n')
    b64_admin_p=$(printf '%s' "$ADMIN_PASSWORD" | base64 | tr -d '\n')

    # The unquoted heredoc expands ${b64_*} and ${REMOTE_DIR} locally.
    # All other $ references are escaped with \$ so they are evaluated on the remote.
    remote_script <<ENDSSH
set -euo pipefail
ENV_FILE="${REMOTE_DIR}/.env"

# Decode values on the remote (base64 transport handles all special characters)
V_UUID=\$(printf '%s' "${b64_uuid}" | base64 -d)
V_NAME=\$(printf '%s' "${b64_name}" | base64 -d)
V_RCON=\$(printf '%s' "${b64_rcon}" | base64 -d)
V_ADM_U=\$(printf '%s' "${b64_admin_u}" | base64 -d)
V_ADM_P=\$(printf '%s' "${b64_admin_p}" | base64 -d)

patch_key() {
    local key="\$1" value="\$2"
    # Escape characters that are special in the sed replacement string (pipe delimiter)
    local esc
    esc=\$(printf '%s' "\$value" | sed 's/[\\\\&|]/\\\\&/g')
    sed -i "s|^\${key}=.*|\${key}=\${esc}|" "\$ENV_FILE"
}

patch_key OPERATOR_UUID  "\$V_UUID"
patch_key OPERATOR_NAME  "\$V_NAME"
patch_key RCON_PASSWORD  "\$V_RCON"
patch_key ADMIN_USERNAME "\$V_ADM_U"
patch_key ADMIN_PASSWORD "\$V_ADM_P"
patch_key ONLINE_MODE    "true"
patch_key HOST_RCON_PORT "127.0.0.1:25575"
ENDSSH

    log_success ".env configured"
}

start_stack() {
    log_step "Starting the stack"

    log_info "Making management scripts executable..."
    remote "chmod +x ${REMOTE_DIR}/up.sh ${REMOTE_DIR}/down.sh"

    # Re-login to pick up the docker group membership applied during bootstrap.
    # sg docker runs the command in a new session belonging to the docker group.
    log_info "Starting Docker Compose stack (this may take 10-15 minutes on first run)..."
    remote "sg docker -c 'cd ${REMOTE_DIR} && ./up.sh'"
    log_success "Stack started"

    log_info "Waiting 15 seconds for containers to initialise..."
    sleep 15

    log_info "Container status:"
    remote "sg docker -c 'docker ps --format \"table {{.Names}}\t{{.Status}}\"'" || true
}

# ─── Main ─────────────────────────────────────────────────────────────────────

main() {
    echo ""
    echo -e "${BOLD}============================================${NC}"
    echo -e "${BOLD}  OMCSI AWS Deployment Script${NC}"
    echo -e "${BOLD}============================================${NC}"
    echo ""

    parse_args "$@"

    # Build SSH options now that KEY_FILE is final (may have been overridden via --key-file).
    # accept-new: automatically accept new host keys on first connection, but reject
    # changed keys (safer than StrictHostKeyChecking=no which silently accepts anything).
    # A dedicated known_hosts file per key avoids conflicts when an instance is reprovisioned.
    KNOWN_HOSTS_FILE="${KEY_FILE}.known_hosts"
    SSH_OPTS="-i ${KEY_FILE} -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=${KNOWN_HOSTS_FILE} -o ConnectTimeout=10 -o BatchMode=yes"

    check_prerequisites
    collect_required_inputs
    provision_ec2
    wait_for_ssh
    bootstrap_docker
    clone_or_update_repo
    configure_env
    start_stack

    echo ""
    echo -e "${BOLD}${GREEN}============================================${NC}"
    echo -e "${BOLD}${GREEN}  Deployment complete!${NC}"
    echo -e "${BOLD}${GREEN}============================================${NC}"
    echo ""
    log_info "Instance:       $INSTANCE_ID"
    log_info "Public IP:      $PUBLIC_IP"
    log_info "SSH key:        $KEY_FILE"
    log_info "Known hosts:    $KNOWN_HOSTS_FILE"
    echo ""
    log_info "Connect via SSH:"
    echo "  ssh -i $KEY_FILE ubuntu@$PUBLIC_IP"
    echo ""
    log_info "Access the web dashboard at:"
    echo "  https://$PUBLIC_IP:8443"
    echo "  (Your browser will warn about the self-signed certificate — this is expected.)"
    echo ""
    log_info "Monitor the Minecraft server startup:"
    echo "  ssh -i $KEY_FILE ubuntu@$PUBLIC_IP"
    echo "  docker logs -f open-mc-server"
    echo ""
    log_warning "Next steps:"
    echo "  1. Replace the self-signed SSL certificate — see AWS-DEPLOYMENT.md Step 8"
    echo "  2. Assign an Elastic IP for a permanent address — see AWS-DEPLOYMENT.md Step 9"
    echo "  3. (Optional) Set up S3 backups — see AWS-DEPLOYMENT.md 'Managing Backups with S3'"
    echo ""
}

main "$@"
