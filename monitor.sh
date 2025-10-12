#!/bin/bash
set -euo pipefail

# Minecraft Server Resource Monitoring Script
# This script monitors resource usage over time to identify bottlenecks

cd "$(dirname "$0")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default values
INTERVAL=5
DURATION=60
LOG_FILE=""
CONTAINER_NAME=""
ANALYZE_FILE=""

# Function to print colored messages
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to get container name from .env
get_container_name() {
    local container_name="private-mc-server"
    if [ -f .env ]; then
        local env_name=$(grep "^CONTAINER_NAME=" .env | cut -d '=' -f2- || echo "")
        if [ -n "$env_name" ]; then
            container_name="$env_name"
        fi
    fi
    echo "$container_name"
}

# Function to check if container is running
check_container() {
    local container=$1
    if ! docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        log_error "Container '$container' is not running"
        log_info "Start the server with: ./up.sh"
        exit 1
    fi
}

# Function to print usage
print_usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Monitor Minecraft server resource usage over time to identify bottlenecks.

OPTIONS:
    -i, --interval SECONDS    Sampling interval in seconds (default: 5)
    -d, --duration SECONDS    Total monitoring duration in seconds (default: 60)
                              Use 0 for continuous monitoring (Ctrl+C to stop)
    -l, --log FILE           Log metrics to specified file
    -c, --container NAME     Container name (default: from .env or 'private-mc-server')
    -a, --analyze FILE       Analyze existing log file for bottlenecks
    -h, --help               Show this help message

EXAMPLES:
    # Monitor for 5 minutes with 10-second intervals
    $0 -i 10 -d 300

    # Monitor continuously and log to file
    $0 -i 5 -d 0 -l monitoring.log

    # Analyze existing log file
    $0 -a monitoring.log

    # Monitor specific container
    $0 -c private-mc-server-dev2

METRICS COLLECTED:
    - CPU Usage (%)
    - Memory Usage (MB and %)
    - Network I/O (MB)
    - Block I/O (MB)
    - PIDs count

EOF
}

# Function to format bytes to MB
bytes_to_mb() {
    local bytes=$1
    echo "scale=2; $bytes / 1024 / 1024" | bc
}

# Function to parse docker stats output
parse_stats() {
    local stats_line=$1
    local timestamp=$2
    
    # Parse CPU percentage (remove % sign)
    local cpu=$(echo "$stats_line" | awk '{print $3}' | tr -d '%')
    
    # Parse memory usage (format: 123.4MiB / 2GiB)
    local mem_usage=$(echo "$stats_line" | awk '{print $4}')
    local mem_limit=$(echo "$stats_line" | awk '{print $6}')
    local mem_percent=$(echo "$stats_line" | awk '{print $7}' | tr -d '%')
    
    # Parse network I/O (format: 1.23MB / 4.56MB)
    local net_in=$(echo "$stats_line" | awk '{print $8}')
    local net_out=$(echo "$stats_line" | awk '{print $10}')
    
    # Parse block I/O (format: 12.3MB / 45.6MB)
    local block_in=$(echo "$stats_line" | awk '{print $11}')
    local block_out=$(echo "$stats_line" | awk '{print $13}')
    
    # Parse PIDs
    local pids=$(echo "$stats_line" | awk '{print $14}')
    
    echo "$timestamp,$cpu,$mem_usage,$mem_limit,$mem_percent,$net_in,$net_out,$block_in,$block_out,$pids"
}

# Function to monitor resources
monitor_resources() {
    local container=$1
    local interval=$2
    local duration=$3
    local log_file=$4
    
    log_info "Starting resource monitoring for container: $container"
    log_info "Interval: ${interval}s, Duration: ${duration}s (0 = continuous)"
    
    if [ -n "$log_file" ]; then
        log_info "Logging to: $log_file"
        echo "Timestamp,CPU%,MemUsage,MemLimit,Mem%,NetIn,NetOut,BlockIn,BlockOut,PIDs" > "$log_file"
    fi
    
    echo ""
    printf "%-20s %-10s %-20s %-8s %-20s %-20s\n" "TIME" "CPU%" "MEMORY" "MEM%" "NET I/O" "BLOCK I/O"
    echo "--------------------------------------------------------------------------------------------------------"
    
    local elapsed=0
    local max_cpu=0
    local max_mem=0
    local samples=0
    
    while true; do
        local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        
        # Get docker stats (no-stream to get single sample)
        local stats=$(docker stats "$container" --no-stream --format "table {{.Container}}\t{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}\t{{.PIDs}}" 2>/dev/null | tail -n 1)
        
        if [ -n "$stats" ]; then
            # Parse stats
            local cpu=$(echo "$stats" | awk '{print $3}' | tr -d '%')
            local mem_usage=$(echo "$stats" | awk '{print $4}')
            local mem_percent=$(echo "$stats" | awk '{print $7}' | tr -d '%')
            local net_io=$(echo "$stats" | awk '{print $8" / "$10}')
            local block_io=$(echo "$stats" | awk '{print $11" / "$13}')
            
            # Display formatted output
            printf "%-20s %-10s %-20s %-8s %-20s %-20s\n" \
                "$timestamp" \
                "${cpu}%" \
                "$mem_usage" \
                "${mem_percent}%" \
                "$net_io" \
                "$block_io"
            
            # Update max values
            if (( $(echo "$cpu > $max_cpu" | bc -l) )); then
                max_cpu=$cpu
            fi
            if (( $(echo "$mem_percent > $max_mem" | bc -l) )); then
                max_mem=$mem_percent
            fi
            
            # Log to file if specified
            if [ -n "$log_file" ]; then
                local csv_line=$(parse_stats "$stats" "$timestamp")
                echo "$csv_line" >> "$log_file"
            fi
            
            samples=$((samples + 1))
        fi
        
        # Check if duration reached
        if [ "$duration" -ne 0 ]; then
            elapsed=$((elapsed + interval))
            if [ "$elapsed" -ge "$duration" ]; then
                break
            fi
        fi
        
        sleep "$interval"
    done
    
    echo ""
    echo "=========================================="
    log_success "Monitoring completed"
    echo "  Samples collected: $samples"
    echo "  Peak CPU usage: ${max_cpu}%"
    echo "  Peak Memory usage: ${max_mem}%"
    
    if [ -n "$log_file" ]; then
        echo "  Log file: $log_file"
    fi
    echo "=========================================="
    
    # Provide analysis recommendations
    echo ""
    log_info "Resource Analysis:"
    
    if (( $(echo "$max_cpu > 80" | bc -l) )); then
        log_warning "CPU usage exceeded 80% - Consider upgrading CPU or optimizing server settings"
    elif (( $(echo "$max_cpu > 60" | bc -l) )); then
        log_info "CPU usage is moderate (>60%) - Monitor during peak hours"
    else
        log_success "CPU usage is healthy (<60%)"
    fi
    
    if (( $(echo "$max_mem > 85" | bc -l) )); then
        log_warning "Memory usage exceeded 85% - Consider increasing RAM or JAVA_OPTS memory allocation"
    elif (( $(echo "$max_mem > 70" | bc -l) )); then
        log_info "Memory usage is moderate (>70%) - Monitor during peak hours"
    else
        log_success "Memory usage is healthy (<70%)"
    fi
}

# Function to analyze log file
analyze_log() {
    local log_file=$1
    
    if [ ! -f "$log_file" ]; then
        log_error "Log file not found: $log_file"
        exit 1
    fi
    
    log_info "Analyzing log file: $log_file"
    echo ""
    
    # Skip header line and analyze data
    local total_lines=$(tail -n +2 "$log_file" | wc -l)
    
    if [ "$total_lines" -eq 0 ]; then
        log_error "No data found in log file"
        exit 1
    fi
    
    # Calculate statistics
    local avg_cpu=$(tail -n +2 "$log_file" | awk -F',' '{sum+=$2; count++} END {if(count>0) print sum/count; else print 0}')
    local max_cpu=$(tail -n +2 "$log_file" | awk -F',' '{if($2>max) max=$2} END {print max+0}')
    local avg_mem=$(tail -n +2 "$log_file" | awk -F',' '{sum+=$5; count++} END {if(count>0) print sum/count; else print 0}')
    local max_mem=$(tail -n +2 "$log_file" | awk -F',' '{if($5>max) max=$5} END {print max+0}')
    
    # Count high usage samples
    local high_cpu_count=$(tail -n +2 "$log_file" | awk -F',' '$2 > 80 {count++} END {print count+0}')
    local high_mem_count=$(tail -n +2 "$log_file" | awk -F',' '$5 > 85 {count++} END {print count+0}')
    
    echo "=========================================="
    echo "RESOURCE USAGE ANALYSIS"
    echo "=========================================="
    echo ""
    echo "Dataset: $total_lines samples"
    echo ""
    echo "CPU Usage:"
    printf "  Average: %.2f%%\n" "$avg_cpu"
    printf "  Peak:    %.2f%%\n" "$max_cpu"
    echo "  Samples >80%: $high_cpu_count"
    echo ""
    echo "Memory Usage:"
    printf "  Average: %.2f%%\n" "$avg_mem"
    printf "  Peak:    %.2f%%\n" "$max_mem"
    echo "  Samples >85%: $high_mem_count"
    echo ""
    echo "=========================================="
    echo ""
    
    # Bottleneck detection
    log_info "BOTTLENECK ANALYSIS:"
    echo ""
    
    local bottleneck_found=false
    
    # CPU bottleneck detection
    if (( $(echo "$max_cpu > 90" | bc -l) )); then
        log_error "CRITICAL: CPU bottleneck detected (peak: ${max_cpu}%)"
        echo "  Recommendations:"
        echo "    - Upgrade to a VM with more CPU cores"
        echo "    - Reduce view-distance in server.properties"
        echo "    - Reduce simulation-distance in server.properties"
        echo "    - Limit number of entities and mob farms"
        echo "    - Remove or optimize resource-intensive plugins"
        bottleneck_found=true
    elif (( $(echo "$avg_cpu > 70" | bc -l) )); then
        log_warning "CPU usage is consistently high (average: ${avg_cpu}%)"
        echo "  Recommendations:"
        echo "    - Monitor during peak player activity"
        echo "    - Consider CPU upgrade if performance degrades"
        echo "    - Optimize server settings (view-distance, entity limits)"
        bottleneck_found=true
    fi
    
    echo ""
    
    # Memory bottleneck detection
    if (( $(echo "$max_mem > 90" | bc -l) )); then
        log_error "CRITICAL: Memory bottleneck detected (peak: ${max_mem}%)"
        echo "  Recommendations:"
        echo "    - Upgrade VM RAM immediately"
        echo "    - Increase JAVA_OPTS memory allocation (-Xmx, -Xms)"
        echo "    - Check for memory leaks in plugins"
        echo "    - Reduce loaded chunks (lower view-distance)"
        bottleneck_found=true
    elif (( $(echo "$avg_mem > 75" | bc -l) )); then
        log_warning "Memory usage is consistently high (average: ${avg_mem}%)"
        echo "  Recommendations:"
        echo "    - Monitor for memory growth over time"
        echo "    - Consider RAM upgrade for future growth"
        echo "    - Review plugin memory usage"
        bottleneck_found=true
    fi
    
    echo ""
    
    if [ "$bottleneck_found" = false ]; then
        log_success "No critical bottlenecks detected"
        echo "  Your server resources are healthy"
        echo "  Continue monitoring during peak hours for best results"
    fi
    
    echo ""
    echo "=========================================="
    
    # Find peak usage times
    echo ""
    log_info "Top 5 CPU usage peaks:"
    tail -n +2 "$log_file" | sort -t',' -k2 -rn | head -5 | while IFS=',' read -r timestamp cpu rest; do
        printf "  %s - %.2f%%\n" "$timestamp" "$cpu"
    done
    
    echo ""
    log_info "Top 5 Memory usage peaks:"
    tail -n +2 "$log_file" | sort -t',' -k5 -rn | head -5 | while IFS=',' read -r timestamp cpu mem_usage mem_limit mem_percent rest; do
        printf "  %s - %.2f%% (%s)\n" "$timestamp" "$mem_percent" "$mem_usage"
    done
    
    echo ""
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -i|--interval)
            INTERVAL="$2"
            shift 2
            ;;
        -d|--duration)
            DURATION="$2"
            shift 2
            ;;
        -l|--log)
            LOG_FILE="$2"
            shift 2
            ;;
        -c|--container)
            CONTAINER_NAME="$2"
            shift 2
            ;;
        -a|--analyze)
            ANALYZE_FILE="$2"
            shift 2
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            print_usage
            exit 1
            ;;
    esac
done

# Main execution
if [ -n "$ANALYZE_FILE" ]; then
    # Analyze mode
    analyze_log "$ANALYZE_FILE"
else
    # Monitor mode
    if [ -z "$CONTAINER_NAME" ]; then
        CONTAINER_NAME=$(get_container_name)
    fi
    
    check_container "$CONTAINER_NAME"
    monitor_resources "$CONTAINER_NAME" "$INTERVAL" "$DURATION" "$LOG_FILE"
fi
