
#!/usr/bin/env bash
set -euo pipefail

echo "================================"
echo "       YmcChat Setup"
echo "================================"

# Crée un dossier que Git pourra conserver
keep_dir() {
    mkdir -p "$1"
    touch "$1/.gitkeep"
}

# Android
keep_dir "android/app/src/main/java/com/ymcchat/app"
keep_dir "android/app/src/main/res/drawable"
keep_dir "android/app/src/main/res/mipmap-hdpi"
keep_dir "android/app/src/main/res/values"
keep_dir "android/app/src/test"

# Backend
for module in auth users messages friends groups servers channels websocket notifications moderation
do
    keep_dir "server/src/$module"
done

keep_dir "server/tests"

# Database
keep_dir "database/migrations"

# Assets
keep_dir "assets/icons"
keep_dir "assets/images"

# Documentation
keep_dir "docs"

# GitHub
keep_dir ".github/workflows"

# Configuration
touch README.md
touch .gitignore
touch .env.example

echo ""
echo "YmcChat directory structure created!"
echo "================================"
