# =============================================================================
# Kubernetes + Helm providers – configured from the LKE kubeconfig
# =============================================================================

provider "kubernetes" {
  host                   = yamldecode(base64decode(linode_lke_cluster.omcsi.kubeconfig)).clusters[0].cluster.server
  token                  = yamldecode(base64decode(linode_lke_cluster.omcsi.kubeconfig)).users[0].user.token
  cluster_ca_certificate = base64decode(yamldecode(base64decode(linode_lke_cluster.omcsi.kubeconfig)).clusters[0].cluster["certificate-authority-data"])
}

provider "helm" {
  kubernetes {
    host                   = yamldecode(base64decode(linode_lke_cluster.omcsi.kubeconfig)).clusters[0].cluster.server
    token                  = yamldecode(base64decode(linode_lke_cluster.omcsi.kubeconfig)).users[0].user.token
    cluster_ca_certificate = base64decode(yamldecode(base64decode(linode_lke_cluster.omcsi.kubeconfig)).clusters[0].cluster["certificate-authority-data"])
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
  chart     = "${path.module}/../helm/omcsi"
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
