FROM navikt/java:8-appdynamics

COPY build/libs/eessi-pensjon-oppgave-*.jar /app/app.jar

COPY nais/export-vault-secrets.sh /init-scripts/
RUN chmod +x /init-scripts/*

ENV APPD_NAME eessi-pensjon-oppgave
ENV APPD_TIER eessipensjon
ENV APPD_ENABLED true