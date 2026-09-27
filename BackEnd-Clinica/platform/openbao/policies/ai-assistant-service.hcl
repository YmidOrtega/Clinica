path "secret/data/assistant/db/migrator" {
  capabilities = ["read"]
}

path "secret/data/assistant/db/app" {
  capabilities = ["read"]
}

path "transit/keys/ai-assistant-service-client" {
  capabilities = ["read"]
}

path "transit/sign/ai-assistant-service-client" {
  capabilities = ["update"]
}
