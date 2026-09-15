path "secret/data/auth/db/migrator" {
  capabilities = ["read"]
}

path "secret/data/auth/db/app" {
  capabilities = ["read"]
}

path "secret/data/auth/bootstrap" {
  capabilities = ["read"]
}

path "transit/keys/auth-jwt" {
  capabilities = ["read"]
}

path "transit/sign/auth-jwt" {
  capabilities = ["update"]
}

path "transit/keys/api-gateway-client" {
  capabilities = ["read"]
}
