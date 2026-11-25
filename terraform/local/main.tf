# Step 1: run stay-booking-java on your own machine with Terraform's Docker provider.
# Same ideas as a cloud deploy (resources, variables, outputs, state), no cloud bill.
#
#   terraform init      # downloads the Docker provider
#   terraform plan      # shows what would be created
#   terraform apply     # creates network + Postgres + app
#   terraform destroy   # removes all of it

terraform {
  required_version = ">= 1.6"
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0"
    }
  }
}

provider "docker" {
  host = var.docker_host
}

# A private network so the app reaches Postgres by name ("db"), not by IP.
resource "docker_network" "stay" {
  name = "stay-booking"
}

resource "docker_image" "postgres" {
  name         = "postgres:17"
  keep_locally = true
}

resource "docker_container" "db" {
  name  = "stay-booking-db"
  image = docker_image.postgres.image_id

  networks_advanced {
    name    = docker_network.stay.name
    aliases = ["db"]
  }

  env = [
    "POSTGRES_DB=staybooking",
    "POSTGRES_USER=staybooking",
    "POSTGRES_PASSWORD=${var.db_password}",
  ]

  healthcheck {
    test     = ["CMD-SHELL", "pg_isready -U staybooking -d staybooking"]
    interval = "2s"
    retries  = 30
  }
}

# Builds the app image from the repo's Dockerfile (needs `./mvnw package` first).
resource "docker_image" "app" {
  name = "stay-booking-java:local"
  build {
    context = "${path.module}/../.."
  }
  # Rebuild when the jar changes.
  triggers = {
    jar = filesha256("${path.module}/../../target/stay-booking-0.0.1-SNAPSHOT.jar")
  }
}

resource "docker_container" "app" {
  name  = "stay-booking-app"
  image = docker_image.app.image_id

  networks_advanced {
    name = docker_network.stay.name
  }

  ports {
    internal = 8080
    external = var.app_port
  }

  env = [
    "SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/staybooking",
    "SPRING_DATASOURCE_USERNAME=staybooking",
    "SPRING_DATASOURCE_PASSWORD=${var.db_password}",
    "PAYMENTS_WEBHOOK_SECRET=${var.webhook_secret}",
  ]

  # Start the app only after Postgres reports healthy.
  depends_on = [docker_container.db]
  restart    = "on-failure"
}
