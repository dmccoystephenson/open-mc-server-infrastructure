# =============================================================================
# Kubernetes + Helm providers – configured from the LKE kubeconfig
# =============================================================================

locals {
  kubeconfig          = yamldecode(base64decode(linode_lke_cluster.omcsi.kubeconfig))
  use_custom_registry = var.image_registry != "" ? [1] : []
}

provider "kubernetes" {
  host                   = local.kubeconfig.clusters[0].cluster.server
  token                  = local.kubeconfig.users[0].user.token
  cluster_ca_certificate = base64decode(local.kubeconfig.clusters[0].cluster["certificate-authority-data"])
}

provider "helm" {
  kubernetes {
    host                   = local.kubeconfig.clusters[0].cluster.server
    token                  = local.kubeconfig.users[0].user.token
    cluster_ca_certificate = base64decode(local.kubeconfig.clusters[0].cluster["certificate-authority-data"])
  }
}

# =============================================================================
# Deploy the OMCSI Helm chart
# =============================================================================

resource "kubernetes_namespace" "omcsi" {
  metadata {
    name = var.helm_namespace
  }
}

resource "helm_release" "omcsi" {
  name      = var.helm_release_name
  namespace = kubernetes_namespace.omcsi.metadata[0].name
  chart     = "${path.module}/../../helm/omcsi"
  wait      = true
  timeout   = 600

  # Fail early when agent-manager is enabled but required secrets are missing
  lifecycle {
    precondition {
      condition     = !var.agent_manager_enabled || var.agent_discord_bot_token != ""
      error_message = "agent_discord_bot_token is required when agent_manager_enabled is true."
    }
    precondition {
      condition     = !var.agent_manager_enabled || var.agent_discord_channel_id != ""
      error_message = "agent_discord_channel_id is required when agent_manager_enabled is true."
    }
    precondition {
      condition     = !var.agent_manager_enabled || var.agent_anthropic_api_key != ""
      error_message = "agent_anthropic_api_key is required when agent_manager_enabled is true."
    }
  }

  # Required secrets
  set_sensitive {
    name  = "secrets.rconPassword"
    value = var.rcon_password
  }

  set_sensitive {
    name  = "secrets.adminPassword"
    value = var.admin_password
  }

  # Storage class for all PVCs
  set {
    name  = "persistence.mcserver.storageClass"
    value = var.storage_class
  }

  set {
    name  = "persistence.webappData.storageClass"
    value = var.storage_class
  }

  set {
    name  = "persistence.alertManagerData.storageClass"
    value = var.storage_class
  }

  set {
    name  = "persistence.backups.storageClass"
    value = var.storage_class
  }

  # Image registry overrides (when image_registry is set)
  dynamic "set" {
    for_each = local.use_custom_registry
    content {
      name  = "minecraftWrapper.image.repository"
      value = "${var.image_registry}/open-mc-server"
    }
  }

  dynamic "set" {
    for_each = local.use_custom_registry
    content {
      name  = "webapp.image.repository"
      value = "${var.image_registry}/open-mc-server-webapp"
    }
  }

  dynamic "set" {
    for_each = local.use_custom_registry
    content {
      name  = "nginx.image.repository"
      value = "${var.image_registry}/open-mc-server-nginx"
    }
  }

  dynamic "set" {
    for_each = local.use_custom_registry
    content {
      name  = "backupManager.image.repository"
      value = "${var.image_registry}/open-mc-server-backup-manager"
    }
  }

  dynamic "set" {
    for_each = local.use_custom_registry
    content {
      name  = "alertManager.image.repository"
      value = "${var.image_registry}/open-mc-server-alert-manager"
    }
  }

  dynamic "set" {
    for_each = local.use_custom_registry
    content {
      name  = "agentManager.image.repository"
      value = "${var.image_registry}/open-mc-server-agent-manager"
    }
  }

  # Agent-manager (optional)
  set {
    name  = "agentManager.enabled"
    value = var.agent_manager_enabled
  }

  set_sensitive {
    name  = "secrets.agentDiscordBotToken"
    value = var.agent_discord_bot_token
  }

  set_sensitive {
    name  = "secrets.agentDiscordChannelId"
    value = var.agent_discord_channel_id
  }

  set_sensitive {
    name  = "secrets.agentAnthropicApiKey"
    value = var.agent_anthropic_api_key
  }

  values = var.helm_values_file != "" ? [file(var.helm_values_file)] : []
}
