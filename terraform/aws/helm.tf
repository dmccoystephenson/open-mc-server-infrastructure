# =============================================================================
# Kubernetes + Helm providers – configured from the EKS cluster
# =============================================================================

provider "kubernetes" {
  host                   = aws_eks_cluster.omcsi.endpoint
  cluster_ca_certificate = base64decode(aws_eks_cluster.omcsi.certificate_authority[0].data)
  token                  = data.aws_eks_cluster_auth.omcsi.token
}

provider "helm" {
  kubernetes {
    host                   = aws_eks_cluster.omcsi.endpoint
    cluster_ca_certificate = base64decode(aws_eks_cluster.omcsi.certificate_authority[0].data)
    token                  = data.aws_eks_cluster_auth.omcsi.token
  }
}

data "aws_eks_cluster_auth" "omcsi" {
  name = aws_eks_cluster.omcsi.name
}

# =============================================================================
# Deploy the OMCSI Helm chart
# =============================================================================

locals {
  use_custom_registry = var.image_registry != "" ? [1] : []
  use_discord         = toset(var.discord_webhook_url != "" ? ["enabled"] : [])
  use_deploy_token    = toset(var.deploy_auth_token != "" ? ["enabled"] : [])
}

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

  depends_on = [
    aws_eks_node_group.omcsi,
    aws_eks_addon.ebs_csi_driver,
  ]

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

  # Service types
  set {
    name  = "minecraftWrapper.service.type"
    value = var.minecraft_service_type
  }

  set {
    name  = "nginx.service.type"
    value = var.nginx_service_type
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

  # Discord alerts (auto-enabled when webhook URL is provided)
  dynamic "set" {
    for_each = local.use_discord
    content {
      name  = "alertManager.env.DISCORD_ENABLED"
      value = "true"
    }
  }

  set_sensitive {
    name  = "secrets.discordWebhookUrl"
    value = var.discord_webhook_url
  }

  # Plugin hot-deploy token
  set_sensitive {
    name  = "secrets.deployAuthToken"
    value = var.deploy_auth_token
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
