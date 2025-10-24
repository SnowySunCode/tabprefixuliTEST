#!/bin/bash
set -e
OUT="plugins/TabPrefix"
mkdir -p "$OUT"
KEYSTORE="$OUT/keystore.jks"
PASSWORD="tabprefix"
echo "Creating keystore at $KEYSTORE with password $PASSWORD"
keytool -genkeypair -alias tabprefix -keyalg RSA -keysize 2048 -storetype JKS -keystore "$KEYSTORE" -storepass "$PASSWORD" -keypass "$PASSWORD" -validity 3650 -dname "CN=TabPrefix, OU=TabPrefix, O=Local, L=Local, ST=Local, C=US"
echo "Keystore created. Use password: $PASSWORD"
