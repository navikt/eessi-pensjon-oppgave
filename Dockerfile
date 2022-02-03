FROM navikt/java:11-appdynamics

COPY build/libs/eessi-pensjon-oppgave.jar /app/app.jar

ENV APPD_NAME eessi-pensjon
ENV APPD_TIER oppgave
ENV APPD_ENABLED true
