# AWS Deployment Guide

This guide covers deploying the Open Minecraft Server Infrastructure to AWS using the AWS CLI tool.

## Table of Contents

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
- [Troubleshooting](#troubleshooting)

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
# Create the security group
aws ec2 create-security-group \
  --group-name omcsi-sg \
  --description "Open MC Server Infrastructure security group"
```

The command outputs a `GroupId`. Store it in a variable for the next commands:

```bash
SG_ID=$(aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=omcsi-sg" \
  --query 'SecurityGroups[0].GroupId' \
  --output text)
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

> **Note**: Do **not** open port 25575 (RCON) publicly. It is accessible only through the web dashboard running inside the Docker network.

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

# Install required packages
sudo apt-get install -y ca-certificates curl gnupg

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

You should see containers for `open-mc-server`, `open-mc-webapp`, `open-mc-nginx`, `open-mc-backup-manager`, and `open-mc-alert-manager` all with a status of `Up`.

Access the web dashboard at:

```
https://<PUBLIC_IP>:8443
```

Your browser will show a security warning for the self-signed certificate. This is expected — see [Step 8](#step-8-configure-ssl-certificates) to replace it with a trusted certificate.

## Step 8: Configure SSL Certificates

Self-signed certificates are included for development. For production, replace them with certificates from [Let's Encrypt](https://letsencrypt.org/).

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

aws s3api create-bucket \
  --bucket "$BUCKET_NAME" \
  --region us-east-1

# Enable versioning for additional protection
aws s3api put-bucket-versioning \
  --bucket "$BUCKET_NAME" \
  --versioning-configuration Status=Enabled

echo "Bucket: $BUCKET_NAME"
```

### Sync Backups to S3

On the EC2 instance, add a cron job to sync the backups directory to S3 daily. First, attach an IAM instance profile with S3 write access, then add to crontab:

```bash
# On the EC2 instance — add to crontab (crontab -e)
# Sync backups to S3 at 3 AM daily (after the 2 AM automated backup)
0 3 * * * aws s3 sync ~/open-mc-server-infrastructure/backups/ s3://YOUR-BUCKET-NAME/backups/ --delete
```

Replace `YOUR-BUCKET-NAME` with the value of `$BUCKET_NAME` from the command above.

> **Tip**: Attach an IAM role with the `AmazonS3FullAccess` policy (or a least-privilege custom policy) to your EC2 instance via the AWS console or CLI to allow the instance to access S3 without embedding credentials.

### Restore from S3

To restore a backup from S3 to a new or existing instance:

```bash
# Download a specific backup from S3
aws s3 sync s3://YOUR-BUCKET-NAME/backups/ ~/open-mc-server-infrastructure/backups/

# Then restore using Docker
docker cp ~/open-mc-server-infrastructure/backups/<backup-folder> open-mc-server:/mcserver
docker compose -f ~/open-mc-server-infrastructure/compose.yml restart
```

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

To remove all AWS resources created in this guide:

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
