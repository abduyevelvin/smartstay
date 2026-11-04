#!/bin/bash
# ─────────────────────────────────────────────────────────
#  Generate RSA keypair for JWT RS256 signing
#  Run once: bash scripts/generate-certs.sh
# ─────────────────────────────────────────────────────────

set -e

CERTS_DIR="$(dirname "$0")/../certs"
mkdir -p "$CERTS_DIR"

echo "Generating RSA 2048-bit keypair..."

# Generate private key
openssl genrsa -out "$CERTS_DIR/private.pem" 2048

# Extract public key
openssl rsa -in "$CERTS_DIR/private.pem" -pubout -out "$CERTS_DIR/public.pem"

echo "Done! Keys written to:"
echo "  Private key: $CERTS_DIR/private.pem"
echo "  Public key:  $CERTS_DIR/public.pem"
echo ""
echo "IMPORTANT: certs/ is in .gitignore — never commit private.pem"
