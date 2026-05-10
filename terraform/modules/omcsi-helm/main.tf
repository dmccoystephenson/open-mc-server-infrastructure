locals {
  use_custom_registry = toset(var.image_registry != "" ? ["enabled"] : [])
  use_storage_class   = toset(var.storage_class != "" ? ["enabled"] : [])
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
  chart     = "${path.module}/../../../helm/omcsi"
  wait      = true
  timeout   = 600

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

  set_sensitive {
    name  = "secrets.rconPassword"
    value = var.rcon_password
  }

  set_sensitive {
    name  = "secrets.adminPassword"
    value = var.admin_password
  }

  set {
    name  = "minecraftWrapper.service.type"
    value = var.minecraft_service_type
  }

  set {
    name  = "nginx.service.type"
    value = var.nginx_service_type
  }

  dynamic "set" {
    for_each = local.use_storage_class
    content {
      name  = "persistence.mcserver.storageClass"
      value = var.storage_class
    }
  }

  dynamic "set" {
    for_each = local.use_storage_class
    content {
      name  = "persistence.webappData.storageClass"
      value = var.storage_class
    }
  }

  dynamic "set" {
    for_each = local.use_storage_class
    content {
      name  = "persistence.alertManagerData.storageClass"
      value = var.storage_class
    }
  }

  dynamic "set" {
    for_each = local.use_storage_class
    content {
      name  = "persistence.backups.storageClass"
      value = var.storage_class
    }
  }

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

  dynamic "set" {
    for_each = local.use_discord
    content {
      name  = "alertManager.env.DISCORD_ENABLED"
      value = "true"
    }
  }

  dynamic "set_sensitive" {
    for_each = local.use_discord
    content {
      name  = "secrets.discordWebhookUrl"
      value = var.discord_webhook_url
    }
  }

  dynamic "set_sensitive" {
    for_each = local.use_deploy_token
    content {
      name  = "secrets.deployAuthToken"
      value = var.deploy_auth_token
    }
  }

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
