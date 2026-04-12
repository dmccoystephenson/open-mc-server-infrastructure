# =============================================================================
# Existing cluster connection variables
# =============================================================================

variable "kubeconfig_path" {
  description = "Path to the kubeconfig file for the existing Kubernetes cluster. Defaults to ~/.kube/config."
  type        = string
  default     = "~/.kube/config"
}

variable "kubeconfig_context" {
  description = "Context to use from the kubeconfig file. Leave empty to use the current-context."
  type        = string
  default     = ""
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
  description = "Kubernetes StorageClass for PVCs. Leave empty to use the cluster default StorageClass. Override if your cluster requires a specific class (e.g., 'gp2', 'linode-block-storage-retain', 'local-path')."
  type        = string
  default     = ""
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
