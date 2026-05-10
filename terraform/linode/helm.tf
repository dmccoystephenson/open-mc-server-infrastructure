# =============================================================================
# Kubernetes + Helm providers – configured from the LKE kubeconfig
# =============================================================================

locals {
  kubeconfig = yamldecode(base64decode(linode_lke_cluster.omcsi.kubeconfig))
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

module "omcsi" {
  source = "../modules/omcsi-helm"

  helm_release_name = var.helm_release_name
  helm_namespace    = var.helm_namespace

  rcon_password  = var.rcon_password
  admin_password = var.admin_password

  image_registry         = var.image_registry
  storage_class          = var.storage_class
  minecraft_service_type = var.minecraft_service_type
  nginx_service_type     = var.nginx_service_type

  discord_webhook_url = var.discord_webhook_url
  deploy_auth_token   = var.deploy_auth_token
  helm_values_file    = var.helm_values_file

  agent_manager_enabled    = var.agent_manager_enabled
  agent_discord_bot_token  = var.agent_discord_bot_token
  agent_discord_channel_id = var.agent_discord_channel_id
  agent_anthropic_api_key  = var.agent_anthropic_api_key
}
