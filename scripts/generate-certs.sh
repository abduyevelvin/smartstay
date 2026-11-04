#!/bin/bash
# ─────────────────────────────────────────────────────────
#  Generate RSA keypair for JWT RS256 signing
#  Run once: bash scripts/generate-certs.sh
# ─────────────────────────────────────────────────────────

set -e

CERTS_DIR="$(dirname "$0")/../certs"
mkdir -p "$CERTS_DIR"

echo "Generating RSA 2048-bit keypair (PKCS8)..."

# Generate private key directly in PKCS8 format (required by Java KeyFactory / PKCS8EncodedKeySpec)
# Header will be: -----BEGIN PRIVATE KEY-----  (NOT "RSA PRIVATE KEY")
openssl genpkey -algorithm RSA -out "$CERTS_DIR/private.pem" -pkeyopt rsa_keygen_bits:2048

# Extract public key (X.509 format, required by Java X509EncodedKeySpec)
openssl pkey -in "$CERTS_DIR/private.pem" -pubout -out "$CERTS_DIR/public.pem"

echo "Done! Keys written to:"
echo "  Private key: $CERTS_DIR/private.pem"
echo "  Public key:  $CERTS_DIR/public.pem"
echo ""
echo "IMPORTANT: certs/ is in .gitignore — never commit private.pem"
