# One-shot container that generates every secret at first start (INFRA-4, P-04).
FROM alpine:3.24.4
RUN apk add --no-cache openssl \
    && mkdir /secrets \
    && chown 10001:10001 /secrets
COPY --chmod=0755 init-secrets.sh /usr/local/bin/init-secrets.sh
# /secrets is owned by 10001 in the image, so a fresh named volume inherits that ownership
USER 10001:10001
VOLUME /secrets
ENTRYPOINT ["/usr/local/bin/init-secrets.sh"]
