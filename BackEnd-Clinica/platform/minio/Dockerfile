FROM --platform=$BUILDPLATFORM golang:1.24-alpine AS build

ARG MINIO_TAG=RELEASE.2025-09-07T16-13-09Z
ARG MC_TAG=RELEASE.2025-08-13T08-35-41Z
ARG TARGETOS
ARG TARGETARCH

ENV CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH GOFLAGS=-trimpath GOTOOLCHAIN=auto MINIO_RELEASE=RELEASE MC_RELEASE=RELEASE

RUN apk add --no-cache git

WORKDIR /src
RUN git clone --quiet --depth 1 --branch "$MINIO_TAG" https://github.com/minio/minio.git minio \
 && git clone --quiet --depth 1 --branch "$MC_TAG" https://github.com/minio/mc.git mc

RUN cd minio \
 && version=$(echo "${MINIO_TAG#RELEASE.}" | sed -E 's/T([0-9]{2})-([0-9]{2})-([0-9]{2})Z/T\1:\2:\3Z/') \
 && GOOS= GOARCH= go run buildscripts/gen-ldflags.go "$version" > /tmp/minio.ldflags \
 && go build -ldflags "$(cat /tmp/minio.ldflags)" -o /out/minio

RUN cd mc \
 && version=$(echo "${MC_TAG#RELEASE.}" | sed -E 's/T([0-9]{2})-([0-9]{2})-([0-9]{2})Z/T\1:\2:\3Z/') \
 && GOOS= GOARCH= go run buildscripts/gen-ldflags.go "$version" > /tmp/mc.ldflags \
 && go build -ldflags "$(cat /tmp/mc.ldflags)" -o /out/mc

RUN cp minio/LICENSE minio/CREDITS minio/dockerscripts/docker-entrypoint.sh /out/

FROM alpine:3.22

ARG MINIO_TAG=RELEASE.2025-09-07T16-13-09Z

LABEL org.opencontainers.image.title="MinIO" \
      org.opencontainers.image.version="$MINIO_TAG" \
      org.opencontainers.image.description="MinIO server and client built from the upstream AGPL sources for the Clinica stack" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.source="https://github.com/YmidOrtega/Clinica"

ENV MC_CONFIG_DIR=/tmp/.mc

RUN apk add --no-cache ca-certificates curl

COPY --from=build /out/minio /out/mc /usr/bin/
COPY --from=build /out/docker-entrypoint.sh /usr/bin/docker-entrypoint.sh
COPY --from=build /out/LICENSE /out/CREDITS /licenses/

EXPOSE 9000
VOLUME ["/data"]

ENTRYPOINT ["/usr/bin/docker-entrypoint.sh"]
CMD ["minio"]
