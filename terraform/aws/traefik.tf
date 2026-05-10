# =============================================================================
# Traefik ingress controller (optional — enabled via enable_traefik = true)
# =============================================================================

resource "kubernetes_namespace" "traefik" {
  count = var.enable_traefik ? 1 : 0

  metadata {
    name = "traefik"
  }
}

resource "kubernetes_config_map" "traefik_dynamic_config" {
  count = var.enable_traefik ? 1 : 0

  metadata {
    name      = "traefik-dynamic-config"
    namespace = kubernetes_namespace.traefik[0].metadata[0].name
  }

  data = {
    "routes.yaml" = <<-EOT
      tcp:
        routers:
          minecraft:
            entryPoints: ["minecraft"]
            rule: "HostSNI(`*`)"
            service: minecraft-svc
          nginx-https:
            entryPoints: ["websecure"]
            rule: "HostSNI(`*`)"
            service: nginx-https-svc
            tls:
              passthrough: true
          nginx-http:
            entryPoints: ["web"]
            rule: "HostSNI(`*`)"
            service: nginx-http-svc
        services:
          minecraft-svc:
            loadBalancer:
              servers:
                - address: "omcsi-minecraft-wrapper.${var.helm_namespace}.svc.cluster.local:25565"
          nginx-https-svc:
            loadBalancer:
              servers:
                - address: "omcsi-nginx.${var.helm_namespace}.svc.cluster.local:443"
          nginx-http-svc:
            loadBalancer:
              servers:
                - address: "omcsi-nginx.${var.helm_namespace}.svc.cluster.local:80"
    EOT
  }

  depends_on = [helm_release.omcsi]
}

resource "helm_release" "traefik" {
  count            = var.enable_traefik ? 1 : 0
  name             = "traefik"
  repository       = "https://traefik.github.io/charts"
  chart            = "traefik"
  namespace        = kubernetes_namespace.traefik[0].metadata[0].name
  create_namespace = false
  wait             = true
  timeout          = 300

  values = [<<-EOT
    ports:
      web:
        port: 8000
        expose:
          default: true
        exposedPort: 80
        protocol: TCP
      websecure:
        port: 8443
        expose:
          default: true
        exposedPort: 443
        protocol: TCP
      minecraft:
        port: 25565
        expose:
          default: true
        exposedPort: 25565
        protocol: TCP
    service:
      type: LoadBalancer
    additionalArguments:
      - "--providers.file.directory=/etc/traefik/dynamic"
      - "--providers.file.watch=true"
    deployment:
      additionalVolumes:
        - name: dynamic-config
          configMap:
            name: traefik-dynamic-config
    additionalVolumeMounts:
      - name: dynamic-config
        mountPath: /etc/traefik/dynamic
    logs:
      general:
        level: INFO
  EOT
  ]

  depends_on = [kubernetes_config_map.traefik_dynamic_config]
}
