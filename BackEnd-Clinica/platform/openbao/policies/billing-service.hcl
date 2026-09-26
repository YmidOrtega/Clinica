path "secret/data/billing/db/migrator" {
  capabilities = ["read"]
}

path "secret/data/billing/db/app" {
  capabilities = ["read"]
}

path "secret/data/billing/dian/*" {
  capabilities = ["read"]
}

path "transit/keys/billing-seal" {
  capabilities = ["read"]
}

path "transit/sign/billing-seal" {
  capabilities = ["update"]
}

path "transit/keys/billing-service-client" {
  capabilities = ["read"]
}

path "transit/sign/billing-service-client" {
  capabilities = ["update"]
}

path "transit/keys/billing-dian" {
  capabilities = ["read"]
}

path "transit/sign/billing-dian" {
  capabilities = ["update"]
}
