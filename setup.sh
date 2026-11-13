
#!/usr/bin/env bash

set -e

echo "================================"
echo "       YmcChat Setup"
echo "================================"

# Dossiers principaux
mkdir -p android/app
mkdir -p server/src
mkdir -p database/migrations
mkdir -p docs
mkdir -p assets/icons
mkdir -p assets/images

# Modules du serveur
for module in auth users messages friends groups servers channels websocket notifications moderation
do
    mkdir -p "server/src/$module"
    touch "server/src/$module/.gitkeep"
done

# Android
mkdir -p android/app/src/main/java/com/ymcchat/app
mkdir -p android/app/src/main/res/drawable
mkdir -p android/app/src/main/res/mipmap-hdpi
mkdir -p android/app/src/main/res/values

# Tests
mkdir -p server/tests
mkdir -p android/app/src/test

# GitHub Actions
mkdir -p .github/workflows

# Configuration
touch README.md
touch .gitignore
touch .env.example

echo ""
echo "YmcChat directory structure created!"
echo "================================"
