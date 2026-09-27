path "secret/data/contracting/db/migrator" {
  capabilities = ["read"]
}

path "secret/data/contracting/db/app" {
  capabilities = ["read"]
}

path "transit/keys/contracting-service-client" {
  capabilities = ["read"]
}

path "transit/sign/contracting-service-client" {
  capabilities = ["update"]
}

path "secret/data/eureka/client" {
  capabilities = ["read"]
}
