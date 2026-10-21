FROM eclipse-temurin:17-jre-jammy@sha256:e17d77fb030dd4b642dc078d048a5fb9efcb3676ee20305d905949105a6ccd5a

ARG VOSK_MODEL_URL=https://hf-mirror.com/localstack/vosk-models/resolve/main/vosk-model-small-cn-0.22.zip
ARG VOSK_MODEL_SHA256=3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba
ARG SKYWALKING_AGENT_VERSION=9.7.0
ARG SKYWALKING_AGENT_URL=https://downloads.apache.org/skywalking/java-agent/9.7.0/apache-skywalking-java-agent-9.7.0.tgz
ARG SKYWALKING_AGENT_SHA512=4b2f8ec00604ed067c44ccadf8800852ecf3d33f4da16ed494a05b4a5ffb45b078cf48e25ebadfda6d1197611fe8a1cb49bf426256c5e194299a1ddd631484ac

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms512m -Xmx2g -Djava.security.egd=file:/dev/./urandom" \
    NUS_VOSK_PYTHON=/opt/nus/venv/bin/python \
    NUS_VOSK_SCRIPT=/opt/nus/scripts/vosk_transcribe.py \
    NUS_VOSK_MODEL_DIR=/opt/nus/models/vosk-model-small-cn-0.22 \
    NUS_AI_REVIEW_WORK_DIR=/app/tmp/nus-ai-review

# Keep native AI tools and the model in the immutable runtime image for offline hosts.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ffmpeg fonts-noto-cjk python3 python3-venv unzip \
    && python3 -m venv /opt/nus/venv \
    && /opt/nus/venv/bin/pip install --no-cache-dir --disable-pip-version-check vosk==0.3.45 \
    && mkdir -p /opt/nus/models /opt/nus/scripts /opt/skywalking /app/tmp/nus-ai-review /app/uploads /app/logs \
    && curl --fail --retry 5 --retry-delay 2 --location --output /tmp/vosk-model.zip "${VOSK_MODEL_URL}" \
    && echo "${VOSK_MODEL_SHA256}  /tmp/vosk-model.zip" | sha256sum --check --strict \
    && unzip -q /tmp/vosk-model.zip -d /opt/nus/models \
    && curl --fail --retry 5 --retry-delay 2 --location --output /tmp/skywalking-agent.tgz "${SKYWALKING_AGENT_URL}" \
    && echo "${SKYWALKING_AGENT_SHA512}  /tmp/skywalking-agent.tgz" | sha512sum --check --strict \
    && tar -xzf /tmp/skywalking-agent.tgz -C /opt/skywalking \
    && mv /opt/skywalking/skywalking-agent /opt/skywalking-agent \
    && find /opt/skywalking-agent/optional-plugins -maxdepth 1 -type f -name 'apm-jdk-threading-plugin*.jar' \
         -exec cp {} /opt/skywalking-agent/plugins/ \; \
    && rm -f /tmp/vosk-model.zip /tmp/skywalking-agent.tgz \
    && useradd --system --uid 10001 --create-home --home-dir /home/nus nus \
    && chown -R nus:nus /opt/nus /opt/skywalking-agent /app \
    && rm -rf /var/lib/apt/lists/*

# Some upstream archives contain macOS resource-fork files named like JARs.
# Remove them before startup so the agent never attempts to load invalid plugins.
RUN find /opt/skywalking-agent -type f -name '._*' -delete \
    && test -f /opt/skywalking-agent/bootstrap-plugins/apm-jdk-threading-plugin-${SKYWALKING_AGENT_VERSION}.jar

WORKDIR /nus-server
COPY ./nus-server/target/nus-server.jar app.jar
COPY ./scripts/vosk_transcribe.py /opt/nus/scripts/vosk_transcribe.py
COPY ./nus-server/docker-entrypoint.sh /usr/local/bin/nus-entrypoint
RUN chmod 0755 /usr/local/bin/nus-entrypoint \
    && chown nus:nus /nus-server/app.jar /opt/nus/scripts/vosk_transcribe.py

USER nus
EXPOSE 48080 48081
ENTRYPOINT ["/usr/local/bin/nus-entrypoint"]
