variable "docker_host" {
  description = "Docker engine address. Windows: npipe:////./pipe/docker_engine. Mac/Linux: unix:///var/run/docker.sock"
  type        = string
  default     = "npipe:////./pipe/docker_engine"
}

variable "app_port" {
  description = "Port on your machine for the API"
  type        = number
  default     = 8080
}

variable "db_password" {
  description = "Postgres password (local only)"
  type        = string
  default     = "local-dev-password"
  sensitive   = true
}

variable "webhook_secret" {
  description = "Shared secret for signed payment webhooks"
  type        = string
  default     = "dev-only-demo-secret"
  sensitive   = true
}
