FROM openjdk:8-jre-alpine 

ARG BUILD_PATH="."

ENV OPENCGA_HOME=/opt/opencga
ENV OPENCGA_CONFIG_DIR=${OPENCGA_HOME}/conf

RUN echo 'http://dl-cdn.alpinelinux.org/alpine/v3.6/main' >> /etc/apk/repositories && echo 'http://dl-cdn.alpinelinux.org/alpine/v3.6/community' >> /etc/apk/repositories

RUN apk --no-cache --update add openssh-client sshpass ca-certificates curl && \
    adduser -D -u 1001 opencga -h opencga 

COPY ${BUILD_PATH} /opt/opencga

WORKDIR /opt/opencga/bin
