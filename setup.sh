
#!/usr/bin/env bash

set -euo pipefail

echo "================================"
echo "       YmcChat Setup"
echo "================================"

# Fonction pour créer un dossier conservé par Git
keep_dir() {
    mkdir -p "$1"
    touch "$1/.gitkeep"
}

echo "[1/6] Creating Android structure..."

keep_dir "android/app/src/main/java/com/ymcchat/app"
keep_dir "android/app/src/main/res/drawable"
keep_dir "android/app/src/main/res/mipmap-hdpi"
keep_dir "android/app/src/main/res/values"
keep_dir "android/app/src/test"

echo "[2/6] Creating backend structure..."

for module in auth users messages friends groups servers channels websocket notifications moderation
do
    keep_dir "server/src/$module"
done

keep_dir "server/tests"

echo "[3/6] Creating database structure..."

keep_dir "database/migrations"

echo "[4/6] Creating assets structure..."

keep_dir "assets/icons"
keep_dir "assets/images"

echo "[5/6] Creating documentation structure..."

keep_dir "docs"

echo "[6/6] Creating configuration files..."

touch README.md
touch .gitignore
touch .env.example

echo ""
echo "================================"
echo " YmcChat structure created!"
echo "================================"
echo ""
echo "Android, Backend, Database,"
echo "Assets and Docs are ready."
echo ""
