# =============================================================================
# Traefik ingress controller (optional — enabled via enable_traefik = true)
# =============================================================================

module "traefik" {
  source = "../modules/traefik"

  enable_traefik    = var.enable_traefik
  helm_namespace    = var.helm_namespace
  helm_release_name = var.helm_release_name

  depends_on = [module.omcsi]
}
