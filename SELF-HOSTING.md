# Self-Hosting Guide for Home Deployment

This guide will help you securely deploy and run the Open Minecraft Server Infrastructure on your home PC or server.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Hardware Requirements](#hardware-requirements)
- [Network Configuration](#network-configuration)
- [Security Best Practices](#security-best-practices)
- [Firewall Configuration](#firewall-configuration)
- [DDoS Protection and Rate Limiting](#ddos-protection-and-rate-limiting)
- [Dynamic DNS Setup](#dynamic-dns-setup)
- [SSL Certificates for Public Access](#ssl-certificates-for-public-access)
- [Monitoring and Maintenance](#monitoring-and-maintenance)
- [Troubleshooting](#troubleshooting)
- [Advanced Security: Using OPNsense or pfSense](#advanced-security-using-opnsense-or-pfsense)

## Prerequisites

Before you begin, ensure you have:

- A computer dedicated to running the server (or sufficient resources to run alongside other tasks)
- A stable internet connection with sufficient upload bandwidth (minimum 5 Mbps upload per 10 concurrent players)
- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) installed
- Administrative access to your router for port forwarding
- Basic understanding of networking concepts

## Hardware Requirements

### Minimum Requirements
- **CPU**: 4 cores / 8 threads (e.g., Intel i5 or AMD Ryzen 5)
- **RAM**: 8 GB (4 GB for the server, 4 GB for the OS)
- **Storage**: 20 GB SSD for fast read/write operations
- **Network**: 100 Mbps internet connection (10 Mbps upload minimum)

### Recommended Requirements
- **CPU**: 6+ cores / 12+ threads (e.g., Intel i7/i9 or AMD Ryzen 7/9)
- **RAM**: 16 GB+ (allocate 6-8 GB to the server)
- **Storage**: 50+ GB NVMe SSD
- **Network**: 500 Mbps+ internet connection (50 Mbps+ upload)
- **UPS**: Uninterruptible Power Supply to prevent data corruption during power outages

### Operating System
- **Recommended**: Ubuntu Server 22.04 LTS or newer
- **Alternatives**: Debian, CentOS, Fedora, Windows 10/11 with WSL2, or macOS

## Network Configuration

### 1. Static Local IP Address

Assign a static IP address to your server to ensure port forwarding works consistently.

#### On Linux (Ubuntu/Debian)

Edit the netplan configuration:

```bash
sudo nano /etc/netplan/01-netcfg.yaml
```

Example configuration:

```yaml
network:
  version: 2
  renderer: networkd
  ethernets:
    eth0:
      dhcp4: no
      addresses:
        - 192.168.1.100/24
      gateway4: 192.168.1.1
      nameservers:
        addresses: [8.8.8.8, 8.8.4.4]
```

Apply the configuration:

```bash
sudo netplan apply
```

#### On Windows

1. Open Network Connections (Control Panel > Network and Sharing Center)
2. Right-click your network adapter > Properties
3. Select "Internet Protocol Version 4 (TCP/IPv4)" > Properties
4. Select "Use the following IP address"
5. Enter your static IP (e.g., 192.168.1.100), subnet mask (255.255.255.0), and gateway (your router's IP)

### 2. Router Port Forwarding

Forward the necessary ports from your router to your server's static IP address.

#### Required Ports

| Port  | Protocol | Service                  | Priority |
|-------|----------|--------------------------|----------|
| 25565 | TCP      | Minecraft Server         | Required |
| 8443  | TCP      | Web Dashboard (HTTPS)    | Recommended |
| 25575 | TCP      | RCON (admin access)      | Optional |
| 8100  | TCP      | BlueMap                  | Optional |

#### Port Forwarding Steps

1. Access your router's admin interface (typically at `192.168.1.1` or `192.168.0.1`)
2. Navigate to Port Forwarding or Virtual Server settings
3. Create a new port forwarding rule for each port:
   - **External Port**: The port number (e.g., 25565)
   - **Internal Port**: Same as external port
   - **Internal IP**: Your server's static IP (e.g., 192.168.1.100)
   - **Protocol**: TCP
4. Save the configuration and restart your router if necessary

**Security Note**: Only forward ports that you actually need. Do NOT forward port 25575 (RCON) to the public internet unless you have a specific need and strong password protection.

### 3. Verify Port Forwarding

Use online tools to verify your ports are accessible:

```bash
# From the server, check which ports are listening
sudo netstat -tulpn | grep -E '(25565|8443|25575|8100)'

# Or using ss command
sudo ss -tulpn | grep -E '(25565|8443|25575|8100)'
```

Use external tools like [CanYouSeeMe.org](https://canyouseeme.org/) or [PortChecker.co](https://portchecker.co/) to verify external access.

## Security Best Practices

### 1. Strong Passwords

Change all default passwords in your `.env` file:

```bash
# Strong RCON password (16+ characters, mixed case, numbers, symbols)
RCON_PASSWORD=YourStrongPasswordHere123!@#

# Strong admin credentials
ADMIN_USERNAME=your_admin_user
ADMIN_PASSWORD=YourStrongAdminPassword456!@#
```

### 2. Enable Online Mode

Unless you have a specific reason not to, enable Mojang authentication:

```bash
ONLINE_MODE=true
```

This prevents unauthorized users from joining with fake accounts.

### 3. Use SSL Certificates

For public web dashboard access, use proper SSL certificates instead of self-signed ones:

#### Option A: Let's Encrypt (Free, Recommended)

Install Certbot:

```bash
sudo apt-get update
sudo apt-get install certbot
```

Obtain a certificate (requires domain name):

```bash
sudo certbot certonly --standalone -d yourdomain.com
```

Copy certificates to the nginx directory:

```bash
sudo cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem ./nginx/ssl/cert.pem
sudo cp /etc/letsencrypt/live/yourdomain.com/privkey.pem ./nginx/ssl/key.pem
sudo chown $USER:$USER ./nginx/ssl/*.pem
```

Set up automatic renewal:

```bash
sudo certbot renew --dry-run
```

### 4. Regular Updates

Keep your system and Docker images updated:

```bash
# Update system packages
sudo apt-get update && sudo apt-get upgrade -y

# Update Docker images
docker compose pull
./up.sh
```

### 5. Backup Regularly

Use the automated backup script:

```bash
# Run weekly or after major changes
./backup.sh
```

Store backups in multiple locations (external drive, cloud storage, etc.).

### 6. Limit RCON Access

NEVER expose RCON (port 25575) to the public internet. If you need remote administration:

- Use the web dashboard (port 8443) which provides encrypted access
- Use SSH tunneling: `ssh -L 25575:localhost:25575 user@your-server-ip`
- Use a VPN to access your home network

## Firewall Configuration

### Using UFW (Uncomplicated Firewall) on Linux

UFW is the simplest firewall solution for Ubuntu/Debian systems.

#### 1. Install and Enable UFW

```bash
sudo apt-get install ufw
```

#### 2. Configure Default Policies

```bash
# Deny all incoming connections by default
sudo ufw default deny incoming

# Allow all outgoing connections
sudo ufw default allow outgoing
```

#### 3. Allow Necessary Ports

```bash
# SSH access (change 22 if using custom port)
sudo ufw allow 22/tcp

# Minecraft server
sudo ufw allow 25565/tcp

# Web dashboard HTTPS
sudo ufw allow 8443/tcp

# Optional: HTTP redirect (if needed)
sudo ufw allow 8080/tcp

# Optional: BlueMap (only if you want it publicly accessible)
sudo ufw allow 8100/tcp
```

#### 4. Enable the Firewall

```bash
sudo ufw enable
```

#### 5. Verify Firewall Status

```bash
sudo ufw status verbose
```

### Using iptables (Advanced)

For more granular control, use iptables directly:

```bash
# Save current rules
sudo iptables-save > /tmp/iptables-backup.rules

# Flush existing rules
sudo iptables -F

# Set default policies
sudo iptables -P INPUT DROP
sudo iptables -P FORWARD DROP
sudo iptables -P OUTPUT ACCEPT

# Allow loopback
sudo iptables -A INPUT -i lo -j ACCEPT

# Allow established connections
sudo iptables -A INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

# Allow SSH (change port if needed)
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# Allow Minecraft
sudo iptables -A INPUT -p tcp --dport 25565 -j ACCEPT

# Allow Web Dashboard HTTPS
sudo iptables -A INPUT -p tcp --dport 8443 -j ACCEPT

# Save rules
sudo apt-get install iptables-persistent
sudo netfilter-persistent save
```

### Using Windows Firewall

On Windows, use the built-in Windows Defender Firewall:

1. Open Windows Defender Firewall with Advanced Security
2. Click "Inbound Rules" > "New Rule"
3. Select "Port" > Next
4. Select "TCP" and enter the port number (e.g., 25565)
5. Select "Allow the connection" > Next
6. Select all network types (Domain, Private, Public) > Next
7. Name the rule (e.g., "Minecraft Server") > Finish
8. Repeat for each required port

## DDoS Protection and Rate Limiting

### 1. Connection Rate Limiting with iptables

Limit connection attempts to prevent SYN flood attacks:

```bash
# Limit Minecraft connections (max 10 per minute from same IP)
sudo iptables -A INPUT -p tcp --dport 25565 -m conntrack --ctstate NEW -m recent --set
sudo iptables -A INPUT -p tcp --dport 25565 -m conntrack --ctstate NEW -m recent --update --seconds 60 --hitcount 10 -j DROP

# Limit web dashboard connections
sudo iptables -A INPUT -p tcp --dport 8443 -m conntrack --ctstate NEW -m recent --set
sudo iptables -A INPUT -p tcp --dport 8443 -m conntrack --ctstate NEW -m recent --update --seconds 60 --hitcount 20 -j DROP

# Save rules
sudo netfilter-persistent save
```

### 2. Using Fail2Ban

Install and configure Fail2Ban to automatically ban IPs with suspicious activity:

```bash
sudo apt-get install fail2ban
```

Create a filter for Minecraft: `/etc/fail2ban/filter.d/minecraft.conf`

```ini
[Definition]
failregex = ^.*\[INFO\].*<HOST>.*lost connection.*$
            ^.*\[WARNING\].*<HOST>.*was kicked.*$
ignoreregex =
```

Create a jail: `/etc/fail2ban/jail.local`

```ini
[minecraft]
enabled = true
port = 25565
filter = minecraft
logpath = /var/lib/docker/volumes/mcserver/_data/logs/latest.log
maxretry = 5
bantime = 3600
findtime = 600
```

Restart Fail2Ban:

```bash
sudo systemctl restart fail2ban
sudo fail2ban-client status minecraft
```

### 3. Using Cloudflare for Web Dashboard

For the web dashboard, consider using Cloudflare's free tier for DDoS protection:

1. Sign up for a free Cloudflare account
2. Add your domain
3. Update your domain's nameservers to Cloudflare's
4. Enable "Proxy" (orange cloud) for your dashboard subdomain
5. Configure Cloudflare firewall rules to limit access

### 4. Nginx Rate Limiting

The nginx configuration can be enhanced with rate limiting. Create an enhanced nginx config:

```nginx
# In the http block
limit_req_zone $binary_remote_addr zone=webapp_limit:10m rate=10r/s;
limit_conn_zone $binary_remote_addr zone=webapp_conn:10m;

# In the server block
location / {
    limit_req zone=webapp_limit burst=20 nodelay;
    limit_conn webapp_conn 10;
    # ... rest of proxy configuration
}
```

## Dynamic DNS Setup

If your ISP assigns dynamic IP addresses, use a Dynamic DNS service to maintain a consistent domain name.

### Popular DDNS Providers

- [No-IP](https://www.noip.com/) (Free tier available)
- [DuckDNS](https://www.duckdns.org/) (Free)
- [Dynu](https://www.dynu.com/) (Free)
- [FreeDNS](https://freedns.afraid.org/) (Free)

### Setting Up DDNS on Linux

#### Using ddclient

1. Install ddclient:

```bash
sudo apt-get install ddclient
```

2. Configure ddclient: Edit `/etc/ddclient.conf`

```conf
# For DuckDNS
protocol=duckdns
server=www.duckdns.org
login=nouser
password=your-duckdns-token
yourdomain.duckdns.org

# For No-IP
# protocol=noip
# server=dynupdate.no-ip.com
# login=your-username
# password='your-password'
# your-hostname.no-ip.org
```

3. Start ddclient service:

```bash
sudo systemctl enable ddclient
sudo systemctl start ddclient
sudo systemctl status ddclient
```

### Setting Up DDNS on Router

Many modern routers have built-in DDNS support:

1. Log into your router's admin interface
2. Navigate to DDNS or Dynamic DNS settings
3. Select your DDNS provider
4. Enter your credentials
5. Save and enable the service

## SSL Certificates for Public Access

### Using Certbot with DDNS Domain

Once you have a DDNS domain set up:

1. Stop nginx temporarily:

```bash
docker compose stop nginx
```

2. Obtain certificate:

```bash
sudo certbot certonly --standalone -d yourdomain.duckdns.org
```

3. Copy certificates:

```bash
sudo cp /etc/letsencrypt/live/yourdomain.duckdns.org/fullchain.pem ./nginx/ssl/cert.pem
sudo cp /etc/letsencrypt/live/yourdomain.duckdns.org/privkey.pem ./nginx/ssl/key.pem
sudo chown $USER:$USER ./nginx/ssl/*.pem
```

4. Restart services:

```bash
./up.sh
```

5. Set up automatic renewal:

```bash
# Create renewal hook
sudo bash -c 'cat > /etc/letsencrypt/renewal-hooks/deploy/01-copy-certs.sh << EOF
#!/bin/bash
cp /etc/letsencrypt/live/yourdomain.duckdns.org/fullchain.pem /path/to/open-mc-server-infrastructure/nginx/ssl/cert.pem
cp /etc/letsencrypt/live/yourdomain.duckdns.org/privkey.pem /path/to/open-mc-server-infrastructure/nginx/ssl/key.pem
docker compose -f /path/to/open-mc-server-infrastructure/compose.yml restart nginx
EOF'

sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/01-copy-certs.sh
```

## Monitoring and Maintenance

### 1. System Monitoring

Install monitoring tools:

```bash
# Install htop for resource monitoring
sudo apt-get install htop

# Install ncdu for disk usage analysis
sudo apt-get install ncdu

# Monitor Docker containers
watch -n 5 docker stats
```

### 2. Log Monitoring

Monitor server logs regularly:

```bash
# View Minecraft server logs
docker logs -f open-mc-server

# View web dashboard logs
docker logs -f open-mc-webapp

# View nginx logs
docker logs -f open-mc-nginx

# Check system logs
sudo journalctl -f
```

### 3. Automated Health Checks

Create a health check script: `/home/runner/scripts/health-check.sh`

```bash
#!/bin/bash

# Check if containers are running
if ! docker ps | grep -q open-mc-server; then
    echo "ERROR: Minecraft server container is not running!"
    # Send notification (email, Discord webhook, etc.)
fi

# Check disk space
DISK_USAGE=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')
if [ "$DISK_USAGE" -gt 80 ]; then
    echo "WARNING: Disk usage is at ${DISK_USAGE}%"
fi

# Check memory usage
MEM_USAGE=$(free | grep Mem | awk '{print ($3/$2) * 100.0}' | cut -d'.' -f1)
if [ "$MEM_USAGE" -gt 90 ]; then
    echo "WARNING: Memory usage is at ${MEM_USAGE}%"
fi
```

Schedule with cron:

```bash
crontab -e
# Add: */15 * * * * /home/runner/scripts/health-check.sh
```

### 4. Regular Maintenance Tasks

Create a maintenance schedule:

**Daily:**
- Monitor server performance and player activity
- Check logs for errors or warnings

**Weekly:**
- Run backups: `./backup.sh`
- Review disk space usage
- Check for Docker image updates

**Monthly:**
- Update system packages: `sudo apt-get update && sudo apt-get upgrade`
- Review and rotate logs
- Test backup restoration
- Review firewall rules and blocked IPs

**Quarterly:**
- Update Minecraft version (use `./upgrade.sh`)
- Review and update plugins
- Clean up old backups
- Security audit

## Troubleshooting

### Players Cannot Connect

1. **Verify server is running:**
   ```bash
   docker ps | grep open-mc-server
   ```

2. **Check port forwarding:**
   - Test from external network using online port checkers
   - Verify router settings haven't reset
   - Confirm static IP hasn't changed

3. **Check firewall:**
   ```bash
   sudo ufw status
   # Ensure port 25565 is allowed
   ```

4. **Verify server properties:**
   ```bash
   docker exec open-mc-server cat /mcserver/server.properties
   # Confirm server-port=25565 and server-ip is not set to localhost
   ```

### Web Dashboard Not Accessible

1. **Check nginx container:**
   ```bash
   docker ps | grep nginx
   docker logs open-mc-nginx
   ```

2. **Verify SSL certificates:**
   ```bash
   ls -l nginx/ssl/
   # Ensure cert.pem and key.pem exist and are readable
   ```

3. **Test local access:**
   ```bash
   curl -k https://localhost:8443
   ```

### High Latency or Lag

1. **Check server resources:**
   ```bash
   docker stats open-mc-server
   ```

2. **Review allocated memory:**
   - Increase JAVA_OPTS in `.env`: `JAVA_OPTS=-Xmx4G -Xms4G`

3. **Check network bandwidth:**
   ```bash
   sudo apt-get install iftop
   sudo iftop -i eth0
   ```

4. **Optimize server.properties:**
   - Reduce view-distance
   - Adjust entity activation range
   - Enable optimizations in spigot.yml

### Server Crashes or Restarts

1. **Check Docker logs:**
   ```bash
   docker logs open-mc-server --tail 100
   ```

2. **Review system logs:**
   ```bash
   sudo journalctl -u docker -f
   ```

3. **Check for OOM (Out of Memory) errors:**
   ```bash
   dmesg | grep -i "out of memory"
   ```

4. **Increase memory allocation if needed**

## Advanced Security: Using OPNsense or pfSense

For advanced users wanting enterprise-grade security, consider using OPNsense or pfSense as a dedicated firewall router.

### OPNsense Setup Overview

OPNsense is a free, open-source firewall and routing platform based on FreeBSD.

#### Hardware Requirements for OPNsense

- **Minimum**: Dual-core CPU, 4 GB RAM, 8 GB storage
- **Recommended**: Quad-core CPU, 8 GB RAM, 120 GB SSD
- **Network**: At least 2 network interfaces (WAN and LAN)

#### Basic OPNsense Configuration

1. **Install OPNsense** on dedicated hardware or a VM
2. **Configure network interfaces:**
   - WAN: Connected to your ISP modem
   - LAN: Connected to your internal network
3. **Set up firewall rules** via the web interface (default: https://192.168.1.1)

#### Port Forwarding in OPNsense

1. Navigate to **Firewall > NAT > Port Forward**
2. Add a new rule:
   - **Interface**: WAN
   - **Protocol**: TCP
   - **Destination Port**: 25565
   - **Redirect Target IP**: Your server's IP (e.g., 192.168.1.100)
   - **Redirect Target Port**: 25565
   - **Description**: Minecraft Server
3. Apply changes

#### Advanced OPNsense Features

1. **Intrusion Detection (IDS/IPS):**
   - Navigate to **Services > Intrusion Detection**
   - Enable IDS mode or IPS mode
   - Download and enable rule sets (e.g., ET Open rules)
   - Create custom rules to detect Minecraft-specific attacks

2. **Traffic Shaping (QoS):**
   - Prioritize Minecraft traffic for better performance
   - Navigate to **Firewall > Shaper**
   - Create a high-priority queue for port 25565

3. **VPN Access:**
   - Set up OpenVPN or WireGuard
   - Access admin services (RCON, web dashboard) only through VPN
   - Navigate to **VPN > OpenVPN** or **VPN > WireGuard**

4. **GeoIP Blocking:**
   - Block connections from specific countries
   - Navigate to **Firewall > Aliases**
   - Create GeoIP alias for countries to block
   - Apply to firewall rules

5. **Connection Limits:**
   - Limit simultaneous connections per IP
   - Navigate to **Firewall > Settings > Advanced**
   - Configure connection limits and timeouts

#### Example OPNsense Firewall Rules

```
Priority | Action | Interface | Proto | Source      | Port  | Destination | Port  | Description
---------|--------|-----------|-------|-------------|-------|-------------|-------|------------------
1        | Pass   | WAN       | TCP   | any         | any   | Server IP   | 25565 | Minecraft Server
2        | Pass   | WAN       | TCP   | any         | any   | Server IP   | 8443  | Web Dashboard
3        | Block  | WAN       | any   | GeoIP:CN,RU | any   | any         | any   | Block unwanted countries
4        | Block  | WAN       | any   | any         | any   | any         | any   | Default deny
```

### pfSense as an Alternative

pfSense is similar to OPNsense with a slightly different interface:

1. **Install pfSense** from [pfsense.org](https://www.pfsense.org/)
2. **Configure** via web interface (default: https://192.168.1.1)
3. **Set up packages:**
   - Snort (IDS/IPS)
   - pfBlockerNG (GeoIP and ad blocking)
   - HAProxy (load balancing if running multiple servers)

### Docker-Based Alternative: Containerized Firewall

For a lighter approach, add a containerized firewall to your compose.yml:

```yaml
services:
  firewall:
    image: alpine:latest
    container_name: open-mc-firewall
    privileged: true
    network_mode: host
    restart: unless-stopped
    volumes:
      - ./firewall-rules.sh:/firewall-rules.sh:ro
    command: /bin/sh /firewall-rules.sh
```

Create `firewall-rules.sh`:

```bash
#!/bin/sh

# Install iptables
apk add --no-cache iptables

# Set default policies
iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT ACCEPT

# Allow loopback
iptables -A INPUT -i lo -j ACCEPT

# Allow established connections
iptables -A INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

# Rate limiting for Minecraft
iptables -A INPUT -p tcp --dport 25565 -m conntrack --ctstate NEW -m recent --set
iptables -A INPUT -p tcp --dport 25565 -m conntrack --ctstate NEW -m recent --update --seconds 60 --hitcount 10 -j DROP
iptables -A INPUT -p tcp --dport 25565 -j ACCEPT

# Rate limiting for web dashboard
iptables -A INPUT -p tcp --dport 8443 -m conntrack --ctstate NEW -m recent --set
iptables -A INPUT -p tcp --dport 8443 -m conntrack --ctstate NEW -m recent --update --seconds 60 --hitcount 20 -j DROP
iptables -A INPUT -p tcp --dport 8443 -j ACCEPT

# Allow HTTP redirect
iptables -A INPUT -p tcp --dport 8080 -j ACCEPT

# Keep container running
tail -f /dev/null
```

**Note**: This approach requires running Docker in privileged mode and may have limitations compared to host-based or dedicated firewall solutions.

## Conclusion

Self-hosting a Minecraft server at home can be a rewarding experience, but it requires careful attention to security and maintenance. By following this guide, you'll have a secure, well-configured server that your friends and community can enjoy.

### Key Takeaways

- ✅ Use strong passwords and enable online mode
- ✅ Configure firewall rules properly
- ✅ Only expose necessary ports
- ✅ Keep software updated
- ✅ Backup regularly
- ✅ Monitor system resources and logs
- ✅ Use SSL certificates for web dashboard
- ✅ Consider using DDNS for dynamic IPs
- ✅ Implement rate limiting and DDoS protection
- ✅ For advanced security, use OPNsense or pfSense

### Additional Resources

- [OPNsense Documentation](https://docs.opnsense.org/)
- [pfSense Documentation](https://docs.netgate.com/pfsense/en/latest/)
- [Minecraft Server Security Best Practices](https://minecraft.fandom.com/wiki/Tutorials/Server_security)
- [UFW Documentation](https://help.ubuntu.com/community/UFW)
- [Let's Encrypt Documentation](https://letsencrypt.org/docs/)
- [Fail2Ban Manual](https://www.fail2ban.org/wiki/index.php/Main_Page)

### Getting Help

If you encounter issues:

1. Check this guide's troubleshooting section
2. Review server logs: `docker logs open-mc-server`
3. Search existing issues on the [GitHub repository](https://github.com/dmccoystephenson/open-mc-server-infrastructure/issues)
4. Create a new issue with detailed information about your problem

**Remember**: Security is an ongoing process. Regularly review and update your configuration, stay informed about new vulnerabilities, and maintain good backup practices.

Happy hosting! 🎮🏠
