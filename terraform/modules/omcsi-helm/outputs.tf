output "helm_release_name" {
  description = "Name of the OMCSI Helm release."
  value       = helm_release.omcsi.name
}

output "helm_namespace" {
  description = "Namespace where OMCSI is deployed."
  value       = helm_release.omcsi.namespace
}

output "status" {
  description = "Status of the OMCSI Helm release."
  value       = helm_release.omcsi.status
}
