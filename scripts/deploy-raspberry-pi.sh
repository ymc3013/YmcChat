#!/usr/bin/env bash
set -Eeuo pipefail

REPO="ymc3013/YmcChat"
BRANCH="feature/direct-messaging"
RAW_BASE="https://raw.githubusercontent.com/${REPO}/${BRANCH}"
say(){ printf '\n\033[1;36m[YmcChat]\033[0m %s\n' "$*"; }
die(){ printf '\n\033[1;31m[STOP]\033[0m %s\n' "$*" >&2; exit 1; }
ask_yes(){ local answer; read -r -p "$1 [y/N] " answer; [[ "$answer" == "y" || "$answer" == "Y" ]]; }

say "YmcChat Raspberry Pi deployment"
say "This script will NOT merge the draft pull request and will never overwrite .env."

command -v git >/dev/null || die "Git is missing."
command -v node >/dev/null || die "Node.js is missing."
command -v npm >/dev/null || die "npm is missing."
command -v pg_dump >/dev/null || die "PostgreSQL client tools (pg_dump/psql) are missing."
command -v psql >/dev/null || die "PostgreSQL client tools (pg_dump/psql) are missing."
command -v gh >/dev/null || die "GitHub CLI (gh) is missing. Install it and run: gh auth login"
gh auth status >/dev/null 2>&1 || die "GitHub CLI is not authenticated. Run: gh auth login"

ROOT=""
if git rev-parse --show-toplevel >/dev/null 2>&1; then
  ROOT="$(git rev-parse --show-toplevel)"
else
  for candidate in "$HOME/YmcChat-github" "$HOME/YmcChat"; do
    if [[ -d "$candidate/.git" ]]; then ROOT="$candidate"; break; fi
  done
fi
if [[ -z "$ROOT" ]]; then
  read -r -p "Enter the full path to your YmcChat Git repository: " ROOT
  [[ -d "$ROOT/.git" ]] || die "That folder is not a Git repository."
fi
cd "$ROOT"
REMOTE="$(git remote get-url origin 2>/dev/null || true)"
[[ "$REMOTE" == *"ymc3013/YmcChat"* ]] || die "The origin remote is not ymc3013/YmcChat. Found: $REMOTE"

NODE_MAJOR="$(node -p 'Number(process.versions.node.split(".")[0])')"
(( NODE_MAJOR >= 20 )) || die "Node.js 20 or newer is required (found $(node --version))."

if [[ -n "$(git status --porcelain)" ]]; then
  die "Your repository has local changes. Commit or safely back them up first; nothing was changed."
fi

PREVIOUS_COMMIT="$(git rev-parse HEAD)"
PREVIOUS_BRANCH="$(git branch --show-current)"
say "Current commit: $PREVIOUS_COMMIT"
say "Target branch: $BRANCH"
say "The target is a draft PR branch, not necessarily merged into main."
ask_yes "Continue by deploying this exact development branch to the Raspberry Pi?" || exit 0

git fetch origin "$BRANCH"
git switch "$BRANCH" 2>/dev/null || git switch -c "$BRANCH" --track "origin/$BRANCH"
git pull --ff-only origin "$BRANCH"
DEPLOY_COMMIT="$(git rev-parse HEAD)"
[[ -f server/package-lock.json && -f database/migrations/001_direct_messaging.sql ]] || die "Expected server lockfile or messaging migration is missing."

ENV_FILE=""
for candidate in "$ROOT/server/.env" "$ROOT/.env"; do
  if [[ -f "$candidate" ]]; then ENV_FILE="$candidate"; break; fi
done
[[ -n "$ENV_FILE" ]] || die "No .env found in server/.env or repository root. Create the server environment file first."
chmod 600 "$ENV_FILE" 2>/dev/null || true
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

: "${DATABASE_HOST:?DATABASE_HOST is missing from .env}"
: "${DATABASE_NAME:?DATABASE_NAME is missing from .env}"
: "${DATABASE_USER:?DATABASE_USER is missing from .env}"
: "${DATABASE_PASSWORD:?DATABASE_PASSWORD is missing from .env}"

BACKUP_DIR="$HOME/YmcChat-backups"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
DB_BACKUP="$BACKUP_DIR/ymcchat-${STAMP}.dump"
export PGPASSWORD="$DATABASE_PASSWORD"
say "Creating PostgreSQL backup..."
pg_dump -h "$DATABASE_HOST" -p "${DATABASE_PORT:-5432}" -U "$DATABASE_USER" -d "$DATABASE_NAME" -Fc -f "$DB_BACKUP"
unset PGPASSWORD
[[ -s "$DB_BACKUP" ]] || die "Database backup is missing or empty. Migration cancelled."
say "Database backup saved: $DB_BACKUP"

say "Installing server dependencies..."
npm --prefix server ci
say "Building server..."
npm --prefix server run build

say "Applying the idempotent direct-messaging migration..."
export PGPASSWORD="$DATABASE_PASSWORD"
psql -h "$DATABASE_HOST" -p "${DATABASE_PORT:-5432}" -U "$DATABASE_USER" -d "$DATABASE_NAME" -v ON_ERROR_STOP=1 -f database/migrations/001_direct_messaging.sql
unset PGPASSWORD

PORT_VALUE="${PORT:-3000}"
HEALTH_URL="http://127.0.0.1:${PORT_VALUE}/health"
say "Choose how the existing server process is managed:"
printf '  1) PM2\n  2) systemd\n  3) I will restart it manually\n'
read -r -p "Choice [1/2/3]: " MANAGER
RESTARTED=0
case "$MANAGER" in
  1)
    command -v pm2 >/dev/null || die "PM2 is not installed."
    pm2 status
    read -r -p "Exact PM2 process name or id: " PM2_NAME
    [[ -n "$PM2_NAME" ]] || die "A PM2 process name is required."
    pm2 restart "$PM2_NAME" --update-env
    RESTARTED=1
    ;;
  2)
    command -v systemctl >/dev/null || die "systemctl is unavailable."
    read -r -p "Exact systemd service name (for example ymcchat.service): " SERVICE_NAME
    [[ -n "$SERVICE_NAME" ]] || die "A service name is required."
    sudo systemctl restart "$SERVICE_NAME"
    RESTARTED=1
    ;;
  3)
    say "Build and migration are complete. Restart your server manually, then re-run this script's health check later."
    ;;
  *)
    die "Invalid choice."
    ;;
esac

if (( RESTARTED )); then
  say "Checking API and database health..."
  HEALTH_OK=0
  for attempt in $(seq 1 20); do
    if curl -fsS "$HEALTH_URL" | grep -q '"database":"connected"'; then HEALTH_OK=1; break; fi
    sleep 2
  done
  if (( ! HEALTH_OK )); then
    say "Health check failed. Rolling the Git working tree back to the previous commit."
    git reset --hard "$PREVIOUS_COMMIT"
    if [[ "$MANAGER" == "1" ]]; then
      pm2 restart "$PM2_NAME" --update-env || true
    else
      sudo systemctl restart "$SERVICE_NAME" || true
    fi
    die "Rollback of application code attempted. The additive database migration was kept; backup: $DB_BACKUP"
  fi
  say "API and PostgreSQL are healthy."
fi

say "Looking for the latest successful Android build on $BRANCH..."
RUN_JSON="$(gh run list --repo "$REPO" --workflow build-android.yml --branch "$BRANCH" --status success --limit 1 --json databaseId,headSha 2>/dev/null || true)"
RUN_ID="$(printf '%s' "$RUN_JSON" | jq -r '.[0].databaseId // empty' 2>/dev/null || true)"
RUN_SHA="$(printf '%s' "$RUN_JSON" | jq -r '.[0].headSha // empty' 2>/dev/null || true)"
if [[ -n "$RUN_ID" ]]; then
  if [[ "$RUN_SHA" == "$DEPLOY_COMMIT" ]]; then
    APK_DIR="$HOME/YmcChat-apk/$RUN_ID"
    mkdir -p "$APK_DIR"
    gh run download "$RUN_ID" --repo "$REPO" --name YmcChat-debug --dir "$APK_DIR"
    APK_PATH="$(find "$APK_DIR" -type f -name '*.apk' -print -quit)"
    if [[ -n "$APK_PATH" && -s "$APK_PATH" ]]; then
      cp "$APK_PATH" "$HOME/YmcChat-debug.apk"
      say "APK downloaded: $HOME/YmcChat-debug.apk"
    else
      say "Artifact downloaded but no APK file was found."
    fi
  else
    say "Latest successful APK belongs to a different commit ($RUN_SHA), not the deployed commit. It was NOT downloaded as the matching build."
    say "Open the GitHub Actions tab and wait for the build for commit $DEPLOY_COMMIT to succeed."
  fi
else
  say "No successful Android workflow run found for this branch yet. The server deployment can still finish; the APK is not available yet."
fi

say "Deployment routine finished."
say "Code commit: $DEPLOY_COMMIT"
say "Database backup: $DB_BACKUP"
say "Previous branch was: $PREVIOUS_BRANCH"
