# Guide: Custom Node.js Secret Vault

This repository uses a custom encryption system to secure sensitive `.env` and `.properties` files. This allows us to keep secrets in Git safely without external dependencies.

## 1. How it Works
The script `scripts/vault.js` handles both encryption and decryption using **AES-256-CBC**.

- Requires **Node.js 20+** (`scripts/package.json` marks it as an ES module).
- File format: `[16-byte random IV][ciphertext]`; key = `scrypt(password, 'salt', 32)`.
- `decrypt` only accepts files ending in `.enc` and writes the plaintext next to it with the suffix stripped.

## 2. Encrypting a File
When you create or update a `.env` file, run this command to encrypt it:
```bash
node scripts/vault.js encrypt <path-to-file> <your-password>
```
**Example:**
`node scripts/vault.js encrypt environments/dev/configs/ts-api-engine-service-1606/.env my-password`

**Important**: Delete the original plain `.env` file after encryption!

## 3. Decrypting a File
To view or use the secrets:
```bash
node scripts/vault.js decrypt <path-to-file>.enc <your-password>
```
**Example:**
`node scripts/vault.js decrypt environments/dev/configs/ts-api-engine-service-1606/.env.enc my-password`

## 4. Jenkins Integration
The Jenkins pipeline is configured to automatically decrypt these files during deployment using the `infra-vault-pwd` credential (Secret text). Decryption runs in a throw-away `node:20-alpine` container and the plaintext `.env` is removed in the pipeline's cleanup stage.

## 5. Security Best Practices
- **Password Hygiene**: Choose a strong passphrase and store it in a secure password manager.
- **No Plaintext**: Never commit decrypted `.env` files to the repository (they are git-ignored).
- **Templates**: Keep a `.env.example` with placeholder values next to every `.env.enc` so the expected keys are documented.
