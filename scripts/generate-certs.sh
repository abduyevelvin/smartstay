#!/bin/bash
# ─────────────────────────────────────────────────────────
#  Generate RSA keypair for JWT RS256 signing
#  Run once: bash scripts/generate-certs.sh
#
#  What this does:
#    1. Generates private + public key in smartstay/certs/  (gitignored)
#    2. Copies keys to each service's src/main/resources/certs/
#       so `classpath:certs/*.pem` works when running locally (dev profile).
#       Those copies are also gitignored.
# ─────────────────────────────────────────────────────────

set -e

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CERTS_DIR="$ROOT_DIR/certs"
mkdir -p "$CERTS_DIR"

echo "Generating RSA 2048-bit keypair (PKCS8)..."

# Generate private key in PKCS8 format (required by Java PKCS8EncodedKeySpec)
# Header: -----BEGIN PRIVATE KEY-----  (NOT "RSA PRIVATE KEY")
openssl genpkey -algorithm RSA -out "$CERTS_DIR/private.pem" -pkeyopt rsa_keygen_bits:2048

# Extract public key (X.509 format, required by Java X509EncodedKeySpec)
openssl pkey -in "$CERTS_DIR/private.pem" -pubout -out "$CERTS_DIR/public.pem"

echo "Keys generated in certs/"

# ── Copy to service classpath resources (local dev only) ──────────────────────
# auth-service needs both keys (signs + verifies tokens)
AUTH_CERTS="$ROOT_DIR/auth-service/src/main/resources/certs"
mkdir -p "$AUTH_CERTS"
cp "$CERTS_DIR/private.pem" "$AUTH_CERTS/private.pem"
cp "$CERTS_DIR/public.pem"  "$AUTH_CERTS/public.pem"
echo "Copied both keys → auth-service/src/main/resources/certs/"

# api-gateway needs only the public key (validates tokens, never signs)
GATEWAY_CERTS="$ROOT_DIR/api-gateway/src/main/resources/certs"
mkdir -p "$GATEWAY_CERTS"
cp "$CERTS_DIR/public.pem" "$GATEWAY_CERTS/public.pem"
echo "Copied public key  → api-gateway/src/main/resources/certs/"

echo ""
echo "Done. Summary:"
echo "  certs/private.pem                                  ← gitignored, source of truth"
echo "  certs/public.pem                                   ← gitignored"
echo "  auth-service/.../resources/certs/private.pem      ← gitignored, classpath for local dev"
echo "  auth-service/.../resources/certs/public.pem       ← gitignored, classpath for local dev"
echo "  api-gateway/.../resources/certs/public.pem        ← gitignored, classpath for local dev"
echo ""
echo "IMPORTANT: all certs/ paths are in .gitignore — never commit private.pem"
