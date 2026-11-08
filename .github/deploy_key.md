# Deploy Key for CI/CD

This directory contains the deploy key used by GitHub Actions for pushing releases.

**Note:** The private key is stored as a GitHub Secret (`DEPLOY_KEY`) and should NOT be committed to the repository.

## Setup

1. Generate a deploy key (already done)
2. Store the private key as a GitHub Secret named `DEPLOY_KEY`
3. Add the public key to the repository's deploy keys settings

## Security

- The deploy key is read-only (no write access to repo contents)
- Use `DEPLOY_KEY_READONLY=true` in the Actions workflow for read-only access
- For write access, create a separate deploy key with write permissions
