FROM openbao/openbao:2.6.2

USER root
RUN apk add --no-cache jq openssl \
 && mkdir -p /rendered/patient-db /rendered/clinical-db /rendered/auth-db /rendered/clinical-storage /rendered/kafka-connect \
 && chown -R openbao:openbao /rendered
USER openbao
