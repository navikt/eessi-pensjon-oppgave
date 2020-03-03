![](https://github.com/navikt/eessi-pensjon-oppgave/workflows/Bygg%20og%20deploy%20Q2/badge.svg)
![](https://github.com/navikt/eessi-pensjon-oppgave/workflows/Manuell%20deploy/badge.svg)

# eessi-pensjon-oppgave
Opprette oppgave

# Utvikling

Kjør med java 1.8

## Komme i gang

```
./gradlew build
```

## Oppdatere avhengigheter

Sjekke om man har utdaterte avhengigheter (forsøker å unngå milestones og beta-versjoner):

```
./gradlew dependencyUpdates
```

Dersom du er supertrygg på testene kan du forsøke en oppdatering av alle avhengighetene:

```
./gradlew useLatestVersions && ./gradlew useLatestVersionsCheck
```
