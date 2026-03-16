# AWS Deployment Guide

This guide covers deploying the Open Minecraft Server Infrastructure to AWS using the AWS CLI tool.

## Table of Contents

- [Automated Deployment Script](#automated-deployment-script)
  - [Automated Teardown Script](#automated-teardown-script)
- [Prerequisites](#prerequisites)
- [Architecture Overview](#architecture-overview)
- [Step 1: Configure AWS CLI](#step-1-configure-aws-cli)
- [Step 2: Create a Key Pair](#step-2-create-a-key-pair)
- [Step 3: Create a Security Group](#step-3-create-a-security-group)
- [Step 4: Launch an EC2 Instance](#step-4-launch-an-ec2-instance)
- [Step 5: Connect to Your Instance](#step-5-connect-to-your-instance)
- [Step 6: Install Docker and Docker Compose](#step-6-install-docker-and-docker-compose)
- [Step 7: Deploy the Infrastructure](#step-7-deploy-the-infrastructure)
- [Step 8: Configure SSL Certificates](#step-8-configure-ssl-certificates)
- [Step 9: Assign an Elastic IP (Optional)](#step-9-assign-an-elastic-ip-optional)
- [Managing Backups with S3 (Optional)](#managing-backups-with-s3-optional)
- [Monitoring and Maintenance](#monitoring-and-maintenance)
- [Cost Considerations](#cost-considerations)
- [Cleanup](#cleanup)
- [Troubleshooting deploy-aws.sh](#troubleshooting-deploy-awssh)
- [Troubleshooting](#troubleshooting)

## Automated Deployment Script

`deploy-aws.sh` automates the full deployment process described in the step-by-step sections below. It provisions an EC2 instance (or reuses an existing one), installs Docker, clones the repository, configures `.env`, and starts the stack — all in a single command.

**Prerequisites**: AWS CLI v2 configured, `ssh`, and `jq` installed locally.

```bash
# Configure AWS CLI first (if you haven't already)
aws configure

# Run the deployment script from the repo root
./deploy-aws.sh \
  --operator-uuid  "YOUR-MINECRAFT-UUID" \
  --operator-name  "YourMCUsername" \
  --rcon-password  "StrongRconPass!" \
  --admin-username "admin" \
  --admin-password "StrongAdminPass!"
```

The script is safe to re-run: if an instance tagged `omcsi-server` already exists it is reused rather than reprovisioned. Run `./deploy-aws.sh --help` for all available options.

> **Note**: After the script completes, continue with [Step 8: Configure SSL Certificates](#step-8-configure-ssl-certificates) and optionally [Step 9: Assign an Elastic IP](#step-9-assign-an-elastic-ip-optional) and [Managing Backups with S3](#managing-backups-with-s3-optional) to finish hardening the deployment.

### Automated Teardown Script

`teardown-aws.sh` removes all AWS resources created by `deploy-aws.sh` (EC2 instance, security group, key pair, and local key files) in a single command:

```bash
# Preview what will be removed (no changes made)
./teardown-aws.sh --dry-run

# Remove all resources (prompts for confirmation)
./teardown-aws.sh

# Remove all resources without prompting
./teardown-aws.sh --yes
```

> **Warning**: This is irreversible. Back up any world data before running it — see [Managing Backups with S3](#managing-backups-with-s3-optional). S3 buckets and Elastic IPs created manually are **not** removed by this script and must be deleted separately (see [Cleanup](#cleanup)).

The remainder of this guide walks through each step manually, which is useful for customisation or understanding what the script does.

## Prerequisites

- An [AWS account](https://aws.amazon.com/free/)
- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) installed locally
- [Git](https://git-scm.com/downloads)
- A Minecraft player UUID (get from [mcuuid.net](https://mcuuid.net/))

## Architecture Overview

This deployment runs the full infrastructure stack on a single EC2 instance:

| Service | Port | Description |
|---------|------|-------------|
| Minecraft Server | 25565 (TCP) | Game server |
| Web Dashboard (HTTPS) | 8443 (TCP) | Admin dashboard via nginx |
| BlueMap | 8100 (TCP) | 3D map viewer (optional) |
| RCON | 25575 (TCP) | **Internal only — do not expose publicly** |

## Step 1: Configure AWS CLI

If you have not already configured the AWS CLI, run:

```bash
aws configure
```

Enter your AWS Access Key ID, Secret Access Key, default region (e.g., `us-east-1`), and default output format (`json`).

Verify that your credentials are working:

```bash
aws sts get-caller-identity
```

## Step 2: Create a Key Pair

Create an EC2 key pair and save the private key locally. You will use this to SSH into your instance.

```bash
aws ec2 create-key-pair \
  --key-name omcsi-key \
  --query 'KeyMaterial' \
  --output text > omcsi-key.pem

chmod 400 omcsi-key.pem
```

## Step 3: Create a Security Group

Create a security group with the required inbound rules. Replace `<YOUR-PUBLIC-IP>` with your local machine's IP address to restrict SSH access. You can find your public IP by running `curl -s https://checkip.amazonaws.com`.

```bash
# Capture the default VPC ID for the current region
VPC_ID=$(aws ec2 describe-vpcs \
  --filters "Name=isDefault,Values=true" \
  --query 'Vpcs[0].VpcId' \
  --output text)

echo "VPC ID: $VPC_ID"

# Create the security group and capture the GroupId directly from the output
SG_ID=$(aws ec2 create-security-group \
  --group-name omcsi-sg \
  --description "Open MC Server Infrastructure security group" \
  --vpc-id "$VPC_ID" \
  --query 'GroupId' \
  --output text)

echo "Security Group ID: $SG_ID"
```

Add inbound rules:

```bash
# SSH — restrict to your IP for security
aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" \
  --protocol tcp \
  --port 22 \
  --cidr <YOUR-PUBLIC-IP>/32

# Minecraft game server
aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" \
  --protocol tcp \
  --port 25565 \
  --cidr 0.0.0.0/0

# Web dashboard (HTTPS)
aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" \
  --protocol tcp \
  --port 8443 \
  --cidr 0.0.0.0/0

# BlueMap (optional)
aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" \
  --protocol tcp \
  --port 8100 \
  --cidr 0.0.0.0/0
```

> **Note**: Do **not** open port 25575 (RCON) publicly. The default `compose.yml` publishes the RCON port to the EC2 host (e.g. `${HOST_RCON_PORT:-25575}:25575`), so the service listens on the instance network interface even though the security group in this guide does **not** expose it to the internet. For defense in depth, keep port 25575 closed in your security group and consider binding RCON to `localhost` only by setting `HOST_RCON_PORT=127.0.0.1:25575` in your `.env` file.

## Step 4: Launch an EC2 Instance

Minecraft with the full infrastructure stack requires at least 4 GB of RAM. A `t3.medium` instance (2 vCPUs, 4 GB RAM) is the minimum recommended size. For better performance with multiple players, consider `t3.large` (8 GB RAM) or larger.

Find the latest Ubuntu 22.04 LTS AMI ID for your region:

```bash
AMI_ID=$(aws ec2 describe-images \
  --owners 099720109477 \
  --filters \
    "Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*" \
    "Name=state,Values=available" \
  --query 'sort_by(Images, &CreationDate)[-1].ImageId' \
  --output text)

echo "Using AMI: $AMI_ID"
```

Launch the instance:

```bash
INSTANCE_ID=$(aws ec2 run-instances \
  --image-id "$AMI_ID" \
  --instance-type t3.medium \
  --key-name omcsi-key \
  --security-group-ids "$SG_ID" \
  --block-device-mappings '[{"DeviceName":"/dev/sda1","Ebs":{"VolumeSize":30,"VolumeType":"gp3"}}]' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=omcsi-server}]' \
  --query 'Instances[0].InstanceId' \
  --output text)

echo "Launched instance: $INSTANCE_ID"
```

Wait for the instance to reach a running state:

```bash
aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
echo "Instance is running."
```

Retrieve the public IP address:

```bash
PUBLIC_IP=$(aws ec2 describe-instances \
  --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' \
  --output text)

echo "Public IP: $PUBLIC_IP"
```

## Step 5: Connect to Your Instance

Wait a minute for SSH to become available, then connect:

```bash
ssh -i omcsi-key.pem ubuntu@"$PUBLIC_IP"
```

If the connection is refused, wait another minute and try again — the instance may still be initializing.

## Step 6: Install Docker and Docker Compose

Run the following commands **on the EC2 instance** after connecting via SSH:

```bash
# Update package index
sudo apt-get update

# Install required packages (including git and nano for cloning and editing)
sudo apt-get install -y ca-certificates curl gnupg git nano

# Add Docker's official GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Add the Docker repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine and Docker Compose plugin
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Allow the ubuntu user to run Docker commands without sudo
sudo usermod -aG docker ubuntu
newgrp docker

# Verify the installation
docker --version
docker compose version
```

## Step 7: Deploy the Infrastructure

Still on the EC2 instance, clone the repository and configure your environment:

```bash
# Clone the repository
git clone https://github.com/dmccoystephenson/open-mc-server-infrastructure.git
cd open-mc-server-infrastructure

# Create the environment file from the sample
cp sample.env .env
```

Edit `.env` to set the required values:

```bash
nano .env
```

At a minimum, update these settings:

```bash
# Your Minecraft player information
OPERATOR_UUID=YOUR_UUID_HERE
OPERATOR_NAME=YOUR_USERNAME_HERE

# Security — change all defaults
RCON_PASSWORD=YourStrongRconPassword123!
ADMIN_USERNAME=your_admin_user
ADMIN_PASSWORD=YourStrongAdminPassword456!

# Enable Mojang authentication
ONLINE_MODE=true

# Java memory — adjust for your instance type
# t3.medium (4 GB RAM): keep defaults or use -Xmx2G -Xms1G
# t3.large  (8 GB RAM): -Xmx5G -Xms3G
JAVA_OPTS=-Xmx3G -Xms2G
```

Make the management scripts executable and start the server:

```bash
chmod +x up.sh down.sh
./up.sh
```

> **Note**: The first build downloads and compiles Spigot from source. This takes 10–15 minutes. You can monitor progress with:
> ```bash
> docker logs -f open-mc-server
> ```

Once the server is running, verify all containers are healthy:

```bash
docker ps
```

You should see containers for `open-mc-server`, `open-mc-webapp`, `open-mc-nginx`, `open-mc-backup-manager`, `open-mc-alert-manager`, and `open-mc-agent-manager` all with a status of `Up`.

> **Note**: `open-mc-agent-manager` is always started but remains effectively disabled unless `AGENT_ENABLED=true` is set in your `.env`.

Access the web dashboard at:

```
https://<PUBLIC_IP>:8443
```

Your browser will show a security warning for the self-signed certificate. This is expected — see [Step 8](#step-8-configure-ssl-certificates) to replace it with a trusted certificate.

## Step 8: Configure SSL Certificates

Self-signed certificates are included for development. For production, replace them with certificates from [Let's Encrypt](https://letsencrypt.org/).

The AWS CLI commands in this section require `SG_ID` (your security group ID). If you used `deploy-aws.sh`, look it up now:

```bash
SG_ID=$(aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=omcsi-sg" \
  --query 'SecurityGroups[0].GroupId' \
  --output text)
echo "Security Group ID: $SG_ID"
```

If you followed the manual steps, `$SG_ID` is already set from Step 3.

### Option A: Using a Domain Name with Let's Encrypt (Recommended)

If you have a domain name pointing to your EC2 instance's public IP:

1. **Open port 80 temporarily** (from your local machine):

   ```bash
   aws ec2 authorize-security-group-ingress \
     --group-id "$SG_ID" \
     --protocol tcp \
     --port 80 \
     --cidr 0.0.0.0/0
   ```

2. **On the EC2 instance**, install Certbot and obtain a certificate:

   ```bash
   sudo apt-get install -y certbot
   sudo certbot certonly --standalone -d yourdomain.com
   ```

3. **Copy the certificates** into the nginx SSL directory:

   ```bash
   sudo cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem \
     ~/open-mc-server-infrastructure/nginx/ssl/cert.pem
   sudo cp /etc/letsencrypt/live/yourdomain.com/privkey.pem \
     ~/open-mc-server-infrastructure/nginx/ssl/key.pem
   sudo chown ubuntu:ubuntu ~/open-mc-server-infrastructure/nginx/ssl/*.pem
   ```

4. **Close port 80** and restart:

   ```bash
   # From your local machine:
   aws ec2 revoke-security-group-ingress \
     --group-id "$SG_ID" \
     --protocol tcp \
     --port 80 \
     --cidr 0.0.0.0/0

   # On the EC2 instance:
   cd ~/open-mc-server-infrastructure && ./up.sh
   ```

### Option B: DNS Challenge (No Port 80 Needed)

If you cannot open port 80, use the DNS-01 challenge instead:

```bash
sudo certbot certonly --manual --preferred-challenges dns -d yourdomain.com
```

Follow the prompts to add a DNS TXT record, then copy the certificates as shown in Option A.

### Option C: Generate New Self-Signed Certificates

If you want to refresh the self-signed certificate (e.g., with your server's IP as the CN):

```bash
cd ~/open-mc-server-infrastructure
./scripts/generate-ssl-certs.sh
./up.sh
```

## Step 9: Assign an Elastic IP (Optional)

By default, your instance's public IP changes every time it is stopped and started. An Elastic IP provides a permanent address.

```bash
# Allocate an Elastic IP
ALLOC_ID=$(aws ec2 allocate-address \
  --domain vpc \
  --query 'AllocationId' \
  --output text)

echo "Allocation ID: $ALLOC_ID"

# Associate it with your instance
aws ec2 associate-address \
  --instance-id "$INSTANCE_ID" \
  --allocation-id "$ALLOC_ID"

# Get the new static IP
STATIC_IP=$(aws ec2 describe-addresses \
  --allocation-ids "$ALLOC_ID" \
  --query 'Addresses[0].PublicIp' \
  --output text)

echo "Elastic IP: $STATIC_IP"
```

> **Cost note**: Elastic IPs are free when associated with a running instance, but incur a small hourly charge when unassociated or when the instance is stopped.

## Managing Backups with S3 (Optional)

The infrastructure saves backups to the local `./backups/` directory inside the instance. To store backups durably in Amazon S3, set up a scheduled sync from the EC2 instance.

### Create an S3 Bucket

```bash
BUCKET_NAME="omcsi-backups-$(aws sts get-caller-identity --query Account --output text)"
REGION=$(aws configure get region)

# us-east-1 does not accept a LocationConstraint; all other regions require one
if [ "$REGION" = "us-east-1" ]; then
  aws s3api create-bucket \
    --bucket "$BUCKET_NAME" \
    --region "$REGION"
else
  aws s3api create-bucket \
    --bucket "$BUCKET_NAME" \
    --region "$REGION" \
    --create-bucket-configuration LocationConstraint="$REGION"
fi

# Enable versioning for additional protection
aws s3api put-bucket-versioning \
  --bucket "$BUCKET_NAME" \
  --versioning-configuration Status=Enabled

echo "Bucket: $BUCKET_NAME"
```

### Sync Backups to S3

The sync cron job and restore commands below use the `aws` CLI **on the EC2 instance**. Ubuntu 22.04 AMIs include AWS CLI v1 by default; to upgrade to v2 follow the [AWS CLI v2 install guide](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html). Authentication is handled automatically by the IAM instance role attached below — no credentials need to be embedded.

On the EC2 instance, add a cron job to sync the backups directory to S3 daily. First, attach an IAM instance profile with S3 write access, then add to crontab:

```bash
# On the EC2 instance — add to crontab (crontab -e)
# Sync backups to S3 at 3 AM daily (after the 2 AM automated backup).
# NOTE: Without --delete, S3 keeps all uploaded backups even after the local copy is pruned,
#       giving you a longer-lived offsite history. If you want S3 to mirror the local directory
#       exactly (and prune S3 when local backups are removed), add --delete to the command.
0 3 * * * aws s3 sync ~/open-mc-server-infrastructure/backups/ s3://YOUR-BUCKET-NAME/backups/
```

Replace `YOUR-BUCKET-NAME` with the value of `$BUCKET_NAME` from the command above.

> **Tip**: Attach an IAM role with a least-privilege S3 policy to your EC2 instance via the AWS console or CLI so it can access only your backups bucket without embedding credentials. Replace `YOUR-BUCKET-NAME` with your actual bucket name:
>
> ```json
> {
>   "Version": "2012-10-17",
>   "Statement": [
>     {
>       "Effect": "Allow",
>       "Action": ["s3:ListBucket"],
>       "Resource": "arn:aws:s3:::YOUR-BUCKET-NAME",
>       "Condition": {
>         "StringLike": {
>           "s3:prefix": ["backups/*"]
>         }
>       }
>     },
>     {
>       "Effect": "Allow",
>       "Action": ["s3:GetObject", "s3:PutObject"],
>       "Resource": "arn:aws:s3:::YOUR-BUCKET-NAME/backups/*"
>     }
>   ]
> }
> ```
>
> `s3:DeleteObject` is intentionally omitted because the recommended cron sync does not use `--delete`. If you opt into mirror mode (`aws s3 sync --delete`), add `"s3:DeleteObject"` to the second statement.

### Restore from S3

To restore a backup from S3 to a new or existing instance:

```bash
# Download backups from S3
aws s3 sync s3://YOUR-BUCKET-NAME/backups/ ~/open-mc-server-infrastructure/backups/

# List available backup folders (e.g., backup-20240101-000000)
ls ~/open-mc-server-infrastructure/backups

# Stop the server before restoring
cd ~/open-mc-server-infrastructure && ./down.sh

# Extract the backup archive into the /mcserver volume inside the container.
# Backups are stored as mcserver-backup.tar.gz inside each timestamped folder.
docker run --rm \
  -v mcserver:/mcserver \
  -v ~/open-mc-server-infrastructure/backups/<backup-folder>:/backup:ro \
  ubuntu:22.04 \
  tar -xzf /backup/mcserver-backup.tar.gz -C /mcserver

# Start the server
cd ~/open-mc-server-infrastructure && ./up.sh
```

Replace `<backup-folder>` with the name of the folder you want to restore (e.g., `backup-20240101-000000`).

## Monitoring and Maintenance

### View Service Logs

```bash
# Minecraft server
docker logs -f open-mc-server

# Web dashboard
docker logs -f open-mc-webapp

# Nginx reverse proxy
docker logs -f open-mc-nginx

# Backup manager
docker logs -f open-mc-backup-manager
```

### Check Resource Usage

```bash
docker stats
```

### Start and Stop the Server

```bash
# Start all services
cd ~/open-mc-server-infrastructure && ./up.sh

# Stop all services gracefully
cd ~/open-mc-server-infrastructure && ./down.sh
```

### Stop the EC2 Instance to Save Costs

When the server is not needed, stop the instance to avoid compute charges (note: EBS storage charges still apply):

```bash
# From your local machine
aws ec2 stop-instances --instance-ids "$INSTANCE_ID"

# Start it again when needed
aws ec2 start-instances --instance-ids "$INSTANCE_ID"
```

> After starting a stopped instance, retrieve the new public IP unless you have assigned an Elastic IP:
> ```bash
> aws ec2 describe-instances \
>   --instance-ids "$INSTANCE_ID" \
>   --query 'Reservations[0].Instances[0].PublicIpAddress' \
>   --output text
> ```

### Trigger a Manual Backup

```bash
cd ~/open-mc-server-infrastructure && ./trigger-backup.sh
```

### Upgrade the Minecraft Version

```bash
cd ~/open-mc-server-infrastructure && ./upgrade.sh
```

See [UPGRADE-GUIDE.md](UPGRADE-GUIDE.md) for detailed upgrade instructions.

## Cost Considerations

Estimated monthly AWS costs for a typical small server (prices vary by region; see the [AWS Pricing Calculator](https://calculator.aws/)):

| Resource | Specification | Estimated Monthly Cost |
|----------|---------------|----------------------|
| EC2 instance | t3.medium (2 vCPU, 4 GB RAM) | ~$30 |
| EC2 instance | t3.large (2 vCPU, 8 GB RAM) | ~$60 |
| EBS volume | 30 GB gp3 | ~$2.40 |
| Elastic IP | Associated with running instance | Free |
| Elastic IP | Unassociated / stopped instance | ~$3.65 |
| S3 backups | 10 GB storage | ~$0.23 |
| Data transfer | 1 GB outbound per month | ~$0.09 |

> **Tip**: Use [AWS Savings Plans](https://aws.amazon.com/savingsplans/) or Reserved Instances for a 1–3 year commitment to reduce EC2 costs by up to 72%.

## Cleanup

If you deployed with `deploy-aws.sh`, the quickest way to remove all resources is the automated teardown script:

```bash
./teardown-aws.sh --dry-run   # preview first
./teardown-aws.sh             # remove all resources
```

To remove resources manually (or to clean up any resources the teardown script does not manage, such as Elastic IPs and S3 buckets):

```bash
# 1. Stop and remove the EC2 instance
aws ec2 terminate-instances --instance-ids "$INSTANCE_ID"
aws ec2 wait instance-terminated --instance-ids "$INSTANCE_ID"

# 2. Release the Elastic IP (if allocated)
aws ec2 release-address --allocation-id "$ALLOC_ID"

# 3. Delete the security group
aws ec2 delete-security-group --group-id "$SG_ID"

# 4. Delete the key pair
aws ec2 delete-key-pair --key-name omcsi-key
rm -f omcsi-key.pem

# 5. Remove the S3 bucket (if created) — first empty it
aws s3 rm s3://"$BUCKET_NAME" --recursive
aws s3api delete-bucket --bucket "$BUCKET_NAME"
```

## Troubleshooting deploy-aws.sh

### SSH timeout (`Timed out waiting for SSH on <IP>`)

This is the most common failure. When `deploy-aws.sh` can't reach the instance over SSH it prints a diagnostics block showing the security group rules, instance state, and your current public IP. Work through the checklist below:

**1. Check the security group ingress rule for port 22**

```bash
INSTANCE_ID=$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=omcsi-server" \
            "Name=instance-state-name,Values=running" \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)

SG_ID=$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].SecurityGroups[0].GroupId' \
  --output text)

aws ec2 describe-security-groups --group-ids "$SG_ID" \
  --query 'SecurityGroups[0].IpPermissions[?FromPort==`22`]' \
  --output table
```

If port 22 is not listed, or the CIDR doesn't include your current IP, add a rule:

```bash
MY_IP=$(curl -s https://checkip.amazonaws.com)
aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" --protocol tcp --port 22 --cidr "${MY_IP}/32"
```

**2. Your public IP may have changed**

ISPs sometimes reassign your IP between the time the instance is provisioned and when you retry. Re-running `./deploy-aws.sh` (with the same arguments) will detect the new IP and add a fresh ingress rule automatically.

**3. Verify the key file permissions**

SSH refuses connections if the private key file is world-readable:

```bash
chmod 400 omcsi-key.pem
```

**4. Remove a stale known_hosts entry**

If the instance was terminated and a new one was assigned the same IP, the script's dedicated `omcsi-key.pem.known_hosts` file will contain the old host key. Delete it so the next connection accepts the new key:

```bash
rm -f omcsi-key.pem.known_hosts
```

**5. Test SSH manually with verbose output**

```bash
PUBLIC_IP=$(aws ec2 describe-instances \
  --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' \
  --output text)

ssh -v -i omcsi-key.pem ubuntu@"$PUBLIC_IP"
```

Look for `Permission denied`, `Connection refused`, or `Connection timed out` in the verbose output:

- **Connection timed out** → port 22 is blocked by the security group or a network ACL
- **Connection refused** → the instance is up but sshd hasn't started yet — wait a minute and retry
- **Permission denied (publickey)** → wrong key file, or the key file has wrong permissions

**6. Verify the instance passed AWS health checks**

```bash
aws ec2 describe-instance-status \
  --instance-ids "$INSTANCE_ID" \
  --query 'InstanceStatuses[0].[InstanceStatus.Status,SystemStatus.Status]' \
  --output table
```

Both values should read `ok`. If either is `initializing`, wait a few more minutes. If either reads `impaired`, the instance may need to be terminated and re-provisioned.

**7. Re-provision from scratch**

If none of the above resolves the issue, tear down and re-deploy:

```bash
./teardown-aws.sh --yes
./deploy-aws.sh   # supply your original arguments
```

## Troubleshooting

| Symptom | Check |
|---------|-------|
| Cannot SSH into instance | Verify your public IP in the SSH security group rule; ensure the key file has `chmod 400` |
| Players cannot connect | Confirm port 25565 is open in the security group; check `docker ps` and `docker logs open-mc-server` |
| Web dashboard inaccessible | Confirm port 8443 is open; check `docker logs open-mc-nginx`; verify SSL cert files exist in `nginx/ssl/` |
| Server crashes on startup | Increase instance type or reduce `JAVA_OPTS` memory; check `docker logs open-mc-server` |
| High latency for players | Choose an AWS region closer to your players; consider upgrading to a `t3.large` or `c5` instance |
| Disk space full | Run `df -h` and `docker system prune`; check `./backups/` size; increase EBS volume via AWS console |
| Instance IP changed | Assign an Elastic IP (see [Step 9](#step-9-assign-an-elastic-ip-optional)) |
