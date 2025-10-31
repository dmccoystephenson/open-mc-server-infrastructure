#!/bin/bash

# Firewall Configuration Script for Open Minecraft Server Infrastructure
# This script helps configure UFW or iptables firewall rules for secure hosting

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Minecraft Server Firewall Configuration Helper${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

# Check if running as root
if [[ $EUID -ne 0 ]]; then
   echo -e "${RED}This script must be run as root (use sudo)${NC}"
   exit 1
fi

# Detect firewall type
echo -e "${YELLOW}Detecting firewall configuration...${NC}"
if command -v ufw &> /dev/null; then
    FIREWALL="ufw"
    echo -e "${GREEN}✓ UFW detected${NC}"
elif command -v iptables &> /dev/null; then
    FIREWALL="iptables"
    echo -e "${GREEN}✓ iptables detected${NC}"
else
    echo -e "${RED}✗ No supported firewall found (UFW or iptables required)${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}This script will configure your firewall to allow:${NC}"
echo "  • SSH (port 22) - for remote access"
echo "  • Minecraft Server (port 25565) - for game connections"
echo "  • Web Dashboard HTTPS (port 8443) - for secure web access"
echo "  • Optional: HTTP redirect (port 8080)"
echo "  • Optional: BlueMap (port 8100)"
echo "  • Optional: RCON (port 25575) - NOT RECOMMENDED for public access"
echo ""
echo -e "${RED}WARNING: This will modify your firewall rules!${NC}"
read -p "Do you want to continue? (yes/no): " -r
echo
if [[ ! $REPLY =~ ^[Yy]es$ ]]; then
    echo "Aborted."
    exit 0
fi

# Get custom ports from user
echo ""
echo -e "${YELLOW}Port Configuration${NC}"
read -p "Minecraft server port [25565]: " MC_PORT
MC_PORT=${MC_PORT:-25565}

read -p "Web Dashboard HTTPS port [8443]: " WEB_HTTPS_PORT
WEB_HTTPS_PORT=${WEB_HTTPS_PORT:-8443}

read -p "Enable HTTP redirect port 8080? (yes/no) [no]: " ENABLE_HTTP
read -p "Enable BlueMap port 8100? (yes/no) [no]: " ENABLE_BLUEMAP
read -p "Enable RCON port 25575? (NOT RECOMMENDED) (yes/no) [no]: " ENABLE_RCON

# Configure UFW
if [[ $FIREWALL == "ufw" ]]; then
    echo ""
    echo -e "${YELLOW}Configuring UFW...${NC}"
    
    # Backup current rules
    ufw status numbered > /tmp/ufw-backup-$(date +%Y%m%d-%H%M%S).txt
    echo -e "${GREEN}✓ Current rules backed up to /tmp/${NC}"
    
    # Set default policies
    echo "Setting default policies..."
    ufw default deny incoming
    ufw default allow outgoing
    
    # Allow SSH
    echo "Allowing SSH (port 22)..."
    ufw allow 22/tcp comment 'SSH access'
    
    # Allow Minecraft
    echo "Allowing Minecraft (port $MC_PORT)..."
    ufw allow $MC_PORT/tcp comment 'Minecraft Server'
    
    # Allow Web Dashboard HTTPS
    echo "Allowing Web Dashboard HTTPS (port $WEB_HTTPS_PORT)..."
    ufw allow $WEB_HTTPS_PORT/tcp comment 'Web Dashboard HTTPS'
    
    # Optional ports
    if [[ $ENABLE_HTTP =~ ^[Yy]es$ ]]; then
        echo "Allowing HTTP redirect (port 8080)..."
        ufw allow 8080/tcp comment 'Web Dashboard HTTP redirect'
    fi
    
    if [[ $ENABLE_BLUEMAP =~ ^[Yy]es$ ]]; then
        echo "Allowing BlueMap (port 8100)..."
        ufw allow 8100/tcp comment 'BlueMap'
    fi
    
    if [[ $ENABLE_RCON =~ ^[Yy]es$ ]]; then
        echo -e "${RED}WARNING: Allowing RCON port 25575 - ensure you have a strong password!${NC}"
        ufw allow 25575/tcp comment 'RCON (use with caution)'
    fi
    
    # Enable UFW
    echo "Enabling UFW..."
    ufw --force enable
    
    echo ""
    echo -e "${GREEN}✓ UFW configuration complete!${NC}"
    echo ""
    ufw status verbose

# Configure iptables
elif [[ $FIREWALL == "iptables" ]]; then
    echo ""
    echo -e "${YELLOW}Configuring iptables...${NC}"
    
    # Backup current rules
    iptables-save > /tmp/iptables-backup-$(date +%Y%m%d-%H%M%S).rules
    echo -e "${GREEN}✓ Current rules backed up to /tmp/${NC}"
    
    # Flush existing rules (BE CAREFUL)
    echo "Flushing existing rules..."
    iptables -F
    iptables -X
    
    # Set default policies
    echo "Setting default policies..."
    iptables -P INPUT DROP
    iptables -P FORWARD DROP
    iptables -P OUTPUT ACCEPT
    
    # Allow loopback
    echo "Allowing loopback interface..."
    iptables -A INPUT -i lo -j ACCEPT
    
    # Allow established connections
    echo "Allowing established connections..."
    iptables -A INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
    
    # Allow SSH
    echo "Allowing SSH (port 22)..."
    iptables -A INPUT -p tcp --dport 22 -j ACCEPT
    
    # Allow Minecraft with rate limiting
    echo "Allowing Minecraft (port $MC_PORT) with rate limiting..."
    iptables -A INPUT -p tcp --dport $MC_PORT -m conntrack --ctstate NEW -m recent --set
    iptables -A INPUT -p tcp --dport $MC_PORT -m conntrack --ctstate NEW -m recent --update --seconds 60 --hitcount 10 -j DROP
    iptables -A INPUT -p tcp --dport $MC_PORT -j ACCEPT
    
    # Allow Web Dashboard HTTPS with rate limiting
    echo "Allowing Web Dashboard HTTPS (port $WEB_HTTPS_PORT) with rate limiting..."
    iptables -A INPUT -p tcp --dport $WEB_HTTPS_PORT -m conntrack --ctstate NEW -m recent --set
    iptables -A INPUT -p tcp --dport $WEB_HTTPS_PORT -m conntrack --ctstate NEW -m recent --update --seconds 60 --hitcount 20 -j DROP
    iptables -A INPUT -p tcp --dport $WEB_HTTPS_PORT -j ACCEPT
    
    # Optional ports
    if [[ $ENABLE_HTTP =~ ^[Yy]es$ ]]; then
        echo "Allowing HTTP redirect (port 8080)..."
        iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
    fi
    
    if [[ $ENABLE_BLUEMAP =~ ^[Yy]es$ ]]; then
        echo "Allowing BlueMap (port 8100)..."
        iptables -A INPUT -p tcp --dport 8100 -j ACCEPT
    fi
    
    if [[ $ENABLE_RCON =~ ^[Yy]es$ ]]; then
        echo -e "${RED}WARNING: Allowing RCON port 25575 - ensure you have a strong password!${NC}"
        iptables -A INPUT -p tcp --dport 25575 -j ACCEPT
    fi
    
    # Save rules
    echo "Saving iptables rules..."
    if command -v iptables-persistent &> /dev/null || command -v netfilter-persistent &> /dev/null; then
        netfilter-persistent save || iptables-save > /etc/iptables/rules.v4
        echo -e "${GREEN}✓ Rules saved and will persist after reboot${NC}"
    else
        echo -e "${YELLOW}! iptables-persistent not installed${NC}"
        echo "Installing iptables-persistent..."
        apt-get update && apt-get install -y iptables-persistent
        netfilter-persistent save
    fi
    
    echo ""
    echo -e "${GREEN}✓ iptables configuration complete!${NC}"
    echo ""
    iptables -L -v -n
fi

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  Firewall configuration completed successfully!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Test your server connection from a local client"
echo "2. Configure port forwarding on your router"
echo "3. Test external connectivity"
echo "4. Review the SELF-HOSTING.md guide for additional security measures"
echo ""
echo -e "${YELLOW}Backup locations:${NC}"
if [[ $FIREWALL == "ufw" ]]; then
    ls -lh /tmp/ufw-backup-*.txt 2>/dev/null | tail -1
else
    ls -lh /tmp/iptables-backup-*.rules 2>/dev/null | tail -1
fi
echo ""
