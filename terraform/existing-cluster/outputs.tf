# =============================================================================
# Outputs
# =============================================================================

output "helm_release_name" {
  description = "Name of the OMCSI Helm release."
  value       = helm_release.omcsi.name
}

output "helm_release_namespace" {
  description = "Namespace of the OMCSI Helm release."
  value       = helm_release.omcsi.namespace
}

output "helm_release_status" {
  description = "Status of the OMCSI Helm release."
  value       = helm_release.omcsi.status
}
