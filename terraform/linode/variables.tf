# =============================================================================
# Linode / LKE variables
# =============================================================================

variable "linode_token" {
  description = "Linode API personal access token. Provide via -var / tfvars, or export as TF_VAR_linode_token."
  type        = string
  sensitive   = true
}

variable "cluster_label" {
  description = "Label for the LKE cluster."
  type        = string
  default     = "omcsi"
}

variable "region" {
  description = "Linode region for the cluster (e.g. us-east, eu-west, ap-south)."
  type        = string
  default     = "us-east"
}

variable "k8s_version" {
  description = "Kubernetes version for the LKE cluster. Must be a version currently supported by Linode. Check available versions at https://api.linode.com/v4/lke/versions or via `linode-cli lke versions-list`."
  type        = string
  default     = "1.34"
}

variable "node_type" {
  description = "Linode instance type for worker nodes."
  type        = string
  default     = "g6-standard-4"
}

variable "node_count" {
  description = "Number of worker nodes in the default node pool."
  type        = number
  default     = 2
}

variable "node_pool_autoscaler" {
  description = "Enable autoscaling for the node pool. Set min/max to desired range."
  type = object({
    enabled = bool
    min     = number
    max     = number
  })
  default = {
    enabled = false
    min     = 2
    max     = 4
  }
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
  description = "Container image registry/repository prefix for OMCSI images. Provide only the prefix portion, for example 'your-dockerhub-user' or 'registry.example.com/omcsi'. Do not include a trailing '/', whitespace, or a full image name/tag. When set, all image repositories are overridden to <registry>/open-mc-server-*. Defaults to 'dmccoystephenson' (Docker Hub)."
  type        = string
  default     = "dmccoystephenson"

  validation {
    condition     = length(regexall("\\s", var.image_registry)) == 0 && !endswith(var.image_registry, "/")
    error_message = "image_registry must be a registry/repository prefix only, with no whitespace and no trailing '/'. Examples: 'your-dockerhub-user' or 'registry.example.com/omcsi'."
  }
}

variable "storage_class" {
  description = "Kubernetes StorageClass for PVCs. Defaults to 'linode-block-storage-retain'. Set to a different class if your cluster uses a custom StorageClass."
  type        = string
  default     = "linode-block-storage-retain"
}

variable "helm_values_file" {
  description = "Optional path to a custom Helm values file to merge. Leave empty to use defaults."
  type        = string
  default     = ""
}

# =============================================================================
# Optional: notifications and plugin deployment
# =============================================================================

variable "discord_webhook_url" {
  description = "Discord webhook URL for alert-manager notifications (server start/stop/crash/backup events). When set, Discord alerts are automatically enabled."
  type        = string
  sensitive   = true
  default     = ""
}

variable "deploy_auth_token" {
  description = "Bearer token for the plugin hot-deploy endpoint (POST /api/plugins/deploy). Leave empty to disable authenticated hot-deploy."
  type        = string
  sensitive   = true
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
