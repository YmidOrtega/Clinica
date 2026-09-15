path "secret/data/gateway/redis" {
  capabilities = ["read"]
}

path "transit/keys/api-gateway-client" {
  capabilities = ["read"]
}

path "transit/sign/api-gateway-client" {
  capabilities = ["update"]
}
