output "api_url" {
  description = "Where the API is listening"
  value       = "http://localhost:${var.app_port}"
}

output "try_search" {
  description = "A request to try once it's up"
  value       = "curl \"http://localhost:${var.app_port}/search?checkIn=2027-08-10&checkOut=2027-08-13\""
}
