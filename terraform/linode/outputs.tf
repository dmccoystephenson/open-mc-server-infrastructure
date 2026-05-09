# =============================================================================
# Outputs
# =============================================================================

output "cluster_id" {
  description = "LKE cluster ID."
  value       = linode_lke_cluster.omcsi.id
}

output "cluster_label" {
  description = "LKE cluster label."
  value       = linode_lke_cluster.omcsi.label
}

output "cluster_status" {
  description = "LKE cluster status."
  value       = linode_lke_cluster.omcsi.status
}

output "cluster_api_endpoints" {
  description = "Kubernetes API endpoints for the LKE cluster."
  value       = linode_lke_cluster.omcsi.api_endpoints
}

output "kubeconfig_path" {
  description = "Path to the generated kubeconfig file."
  value       = local_sensitive_file.kubeconfig.filename
}

output "helm_release_status" {
  description = "Status of the OMCSI Helm release."
  value       = helm_release.omcsi.status
}

output "get_nginx_ip_command" {
  description = "Run after apply to retrieve the public nginx LoadBalancer IP (server address and dashboard endpoint)."
  value       = "kubectl get svc -n ${var.helm_namespace} --kubeconfig ${local_sensitive_file.kubeconfig.filename}"
}
