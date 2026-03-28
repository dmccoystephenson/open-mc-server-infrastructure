# =============================================================================
# Linode Kubernetes Engine (LKE) Cluster
# =============================================================================

provider "linode" {
  token = var.linode_token
}

resource "linode_lke_cluster" "omcsi" {
  label       = var.cluster_label
  k8s_version = var.k8s_version
  region      = var.region

  pool {
    type  = var.node_type
    count = var.node_count

    dynamic "autoscaler" {
      for_each = var.node_pool_autoscaler.enabled ? [1] : []
      content {
        min = var.node_pool_autoscaler.min
        max = var.node_pool_autoscaler.max
      }
    }
  }
}

# =============================================================================
# Kubeconfig – written to a local file for kubectl / helm CLI access
# =============================================================================

resource "local_sensitive_file" "kubeconfig" {
  content         = base64decode(linode_lke_cluster.omcsi.kubeconfig)
  filename        = "${path.module}/kubeconfig.yaml"
  file_permission = "0600"
}
