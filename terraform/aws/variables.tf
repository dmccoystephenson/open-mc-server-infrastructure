# =============================================================================
# AWS / EKS variables
# =============================================================================

variable "aws_region" {
  description = "AWS region for the EKS cluster."
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "Name for the EKS cluster."
  type        = string
  default     = "omcsi"
}

variable "cluster_version" {
  description = "Kubernetes version for the EKS cluster."
  type        = string
  default     = "1.31"
}

variable "node_instance_type" {
  description = "EC2 instance type for EKS worker nodes."
  type        = string
  default     = "t3.large"
}

variable "node_desired_count" {
  description = "Desired number of worker nodes in the managed node group."
  type        = number
  default     = 2
}

variable "node_min_count" {
  description = "Minimum number of worker nodes (autoscaling)."
  type        = number
  default     = 1
}

variable "node_max_count" {
  description = "Maximum number of worker nodes (autoscaling)."
  type        = number
  default     = 4
}

variable "eks_public_access_cidrs" {
  description = "CIDR blocks allowed to reach the EKS API public endpoint. Default is unrestricted (0.0.0.0/0). Restrict to your administrator IP range or VPC CIDR in production."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# =============================================================================
# OMCSI Helm chart variables
# =============================================================================

variable "helm_release_name" {
  description = "Helm release name for the OMCSI chart."
  type        = string
  default     = "omcsi"
}

variable "helm_namespace" {
  description = "Kubernetes namespace for the OMCSI deployment."
  type        = string
  default     = "omcsi"
}

variable "rcon_password" {
  description = "RCON password for the Minecraft server (required)."
  type        = string
  sensitive   = true
}

variable "admin_password" {
  description = "Admin password for the OMCSI web dashboard (required)."
  type        = string
  sensitive   = true
}

variable "image_registry" {
  description = "Container image registry/repository prefix for OMCSI images. Provide only the prefix portion, for example 'your-dockerhub-user' or '123456789.dkr.ecr.us-east-1.amazonaws.com/omcsi'. Do not include a trailing '/', whitespace, or a full image name/tag. When set, all image repositories are overridden to <registry>/open-mc-server-*. Defaults to 'dmccoystephenson' (Docker Hub)."
  type        = string
  default     = "dmccoystephenson"

  validation {
    condition     = length(regexall("\\s", var.image_registry)) == 0 && !endswith(var.image_registry, "/")
    error_message = "image_registry must be a registry/repository prefix only, with no whitespace and no trailing '/'. Examples: 'your-dockerhub-user' or '123456789.dkr.ecr.us-east-1.amazonaws.com/omcsi'."
  }
}

variable "storage_class" {
  description = "Kubernetes StorageClass for PVCs. Defaults to 'gp2' (AWS EBS). Set to a different class if your cluster uses a custom StorageClass."
  type        = string
  default     = "gp2"
}

variable "helm_values_file" {
  description = "Optional path to a custom Helm values file to merge. Leave empty to use defaults."
  type        = string
  default     = ""
}

# =============================================================================
# Optional: agent-manager
# =============================================================================

variable "agent_manager_enabled" {
  description = "Enable the agent-manager (Discord AI bot) Deployment."
  type        = bool
  default     = false
}

variable "agent_discord_bot_token" {
  description = "Discord bot token (required when agent_manager_enabled = true)."
  type        = string
  sensitive   = true
  default     = ""
}

variable "agent_discord_channel_id" {
  description = "Discord channel ID (required when agent_manager_enabled = true)."
  type        = string
  sensitive   = true
  default     = ""
}

variable "agent_anthropic_api_key" {
  description = "Anthropic API key (required when agent_manager_enabled = true)."
  type        = string
  sensitive   = true
  default     = ""
}
