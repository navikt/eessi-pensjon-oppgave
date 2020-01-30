#!/usr/bin/env bash

echo "Sjekker eessi-pensjon-oppgave srvPassord"
if test -f /var/run/secrets/nais.io/srveessio/password;
then
  echo "Setter eessi-pensjon-oppgave srvPassord"
    export srvpassword=$(cat /var/run/secrets/nais.io/srveessio/password)
fi

echo "Sjekker eessi-pensjon-oppgave srvUsername"
if test -f /var/run/secrets/nais.io/srveessio/username;
then
    echo "Setter eessi-pensjon-oppgave srvUsername"
    export srvusername=$(cat /var/run/secrets/nais.io/srveessio/username)
fi