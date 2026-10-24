#!/usr/bin/env bash
# One-time setup on the Mac for Fleet mode with your own Developer app. Each subcommand is idempotent.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SECRETS="$ROOT/secrets"
CONFIG="$SECRETS/config.env"
KEY_PATH=".well-known/appspecific/com.tesla.3p.public-key.pem"
# Partner registration is per region: register in both so any account works.
FLEET_REGIONS=("https://fleet-api.prd.na.vn.cloud.tesla.com" "https://fleet-api.prd.eu.vn.cloud.tesla.com")
TOKEN_URL="https://fleet-auth.prd.vn.cloud.tesla.com/oauth2/v3/token"
SCOPES="openid offline_access vehicle_device_data vehicle_cmds vehicle_charging_cmds"
PUBLIC_PACKAGE="io.github.giack.wearfortesla"

say() { printf '\n==> %s\n' "$*"; }
die() { printf '\nERRORE: %s\n' "$*" >&2; exit 1; }
ask() { local answer; read -r -p "$1 [s/N] " answer; [[ "$answer" =~ ^[sSyY]$ ]]; }
json() { python3 -I -c "import json,sys; d=json.load(sys.stdin); print($1)"; }

load_config() {
  [[ -f "$CONFIG" ]] || die "manca $CONFIG: esegui prima 'setup.sh keys'"
  # shellcheck disable=SC1090
  source "$CONFIG"
  [[ -n "${GITHUB_USER:-}" ]] || die "manca GITHUB_USER in $CONFIG: esegui 'setup.sh keys'"
  # PACKAGE_OVERRIDE targets another build of the app (e.g. the Public one next to a Personal one).
  PACKAGE="${PACKAGE_OVERRIDE:-${PACKAGE:-$PUBLIC_PACKAGE}}"
  DOMAIN="$(printf '%s' "$GITHUB_USER" | tr '[:upper:]' '[:lower:]').github.io"
  REPO="$GITHUB_USER/$DOMAIN"
  REDIRECT="https://wear.googleapis.com/3p_auth/$PACKAGE"
}

# Writes KEY=value into the config, replacing an existing value.
set_config() {
  mkdir -p "$SECRETS" && chmod 700 "$SECRETS"
  touch "$CONFIG" && chmod 600 "$CONFIG"
  grep -v "^$1=" "$CONFIG" > "$CONFIG.tmp" || true
  printf '%s=%q\n' "$1" "$2" >> "$CONFIG.tmp" && mv "$CONFIG.tmp" "$CONFIG"
}

# Prints "<scalar hex> <public hex>" for a SEC1 PEM private key.
key_hex() {
  openssl ec -in "$1" -text -noout 2>/dev/null | python3 -I -c '
import sys, re
text = sys.stdin.read()
def block(name):
    m = re.search(name + r":\s*\n((?:\s+[0-9a-f:]+\n)+)", text)
    return re.sub(r"[^0-9a-f]", "", m.group(1))
priv = block("priv").rjust(64, "0")[-64:]
print(priv, block("pub"))'
}

cmd_keys() {
  mkdir -p "$SECRETS" && chmod 700 "$SECRETS"
  if ! grep -q '^GITHUB_USER=' "$CONFIG" 2>/dev/null; then
    local user package
    user="$(gh api user -q .login 2>/dev/null || true)"
    local answer; read -r -p "Utente GitHub che pubblicherà la chiave pubblica [${user}]: " answer; user="${answer:-$user}"
    [[ -n "$user" ]] || die "serve un utente GitHub"
    read -r -p "Pacchetto dell'app sull'orologio [$PUBLIC_PACKAGE]: " package
    set_config GITHUB_USER "$user"
    set_config PACKAGE "${package:-$PUBLIC_PACKAGE}"
  fi
  if [[ -f "$SECRETS/private.pem" ]]; then
    say "Chiave già presente: $SECRETS/private.pem"
  else
    say "Genero la coppia di chiavi EC prime256v1"
    openssl ecparam -name prime256v1 -genkey -noout -out "$SECRETS/private.pem"
    chmod 600 "$SECRETS/private.pem"
  fi
  openssl ec -in "$SECRETS/private.pem" -pubout -out "$SECRETS/public.pem" 2>/dev/null
  say "OK chiavi in $SECRETS"
}

cmd_pages() {
  load_config
  [[ -f "$SECRETS/public.pem" ]] || die "esegui prima 'setup.sh keys'"
  local work; work="$(mktemp -d)"
  if gh repo view "$REPO" >/dev/null 2>&1; then
    say "Il repository $REPO esiste: lo aggiorno"
  else
    ask "Creo il repository PUBBLICO $REPO (conterrà solo la chiave pubblica)?" || die "annullato"
    gh repo create "$REPO" --public --description "Chiave pubblica per Tesla Fleet API" >/dev/null
  fi
  gh repo clone "$REPO" "$work/site" -- --quiet 2>/dev/null || git init -q -b main "$work/site"
  cd "$work/site"
  git remote get-url origin >/dev/null 2>&1 || git remote add origin "https://github.com/$REPO.git"
  mkdir -p "$(dirname "$KEY_PATH")"
  cp "$ROOT/setup/pages/.nojekyll" "$ROOT/setup/pages/index.html" .
  cp "$SECRETS/public.pem" "$KEY_PATH"
  git add -A
  if git diff --cached --quiet; then
    say "Nessuna modifica da pubblicare"
  else
    git commit -q -m "Publish Tesla Fleet API public key"
    git push -q -u origin HEAD:main
  fi
  cd "$ROOT"; rm -rf "$work"
  gh api -X POST "repos/$REPO/pages" -f "source[branch]=main" -f "source[path]=/" >/dev/null 2>&1 || true
  say "Attendo che GitHub Pages pubblichi la chiave (fino a 10 minuti)"
  local url="https://$DOMAIN/$KEY_PATH"
  for _ in $(seq 1 60); do
    if curl -fsS "$url" 2>/dev/null | diff -q - "$SECRETS/public.pem" >/dev/null; then
      say "OK chiave pubblicata: $url"; return 0
    fi
    sleep 10
  done
  die "la chiave non è ancora raggiungibile su $url"
}

cmd_portal() {
  load_config
  cat <<TXT

Crea la tua Developer app su https://developer.tesla.com (Dashboard → Create application):
  • Nome:             a scelta (uso personale)
  • Allowed origin:   https://$DOMAIN
  • Redirect URI:     $REDIRECT
  • Scope:            Vehicle Information, Vehicle Commands, Vehicle Charging Management
Poi copia qui Client ID e Client Secret. Il VIN è nell'app Tesla (Software → info veicolo).
TXT
  local id secret vin
  read -r -p "Client ID: " id
  read -r -s -p "Client Secret: " secret; echo
  read -r -p "VIN: " vin
  [[ ${#vin} -eq 17 ]] || die "il VIN deve avere 17 caratteri"
  set_config CLIENT_ID "$id"
  set_config CLIENT_SECRET "$secret"
  set_config VIN "$vin"
  say "OK salvato in $CONFIG"
}

partner_token() { # <regional Fleet API base>
  # The secret goes through stdin so it never appears in the process list.
  printf '%s' "$CLIENT_SECRET" | curl -fsS -X POST "$TOKEN_URL" \
    --data-urlencode "grant_type=client_credentials" \
    --data-urlencode "client_id=$CLIENT_ID" \
    --data-urlencode "client_secret@-" \
    --data-urlencode "scope=openid vehicle_device_data vehicle_cmds vehicle_charging_cmds" \
    --data-urlencode "audience=$1" | json 'd["access_token"]'
}

cmd_register() {
  load_config
  local fleet token registered expected
  expected="$(key_hex "$SECRETS/private.pem" | cut -d' ' -f2)"
  for fleet in "${FLEET_REGIONS[@]}"; do
    token="$(partner_token "$fleet")" || die "token partner non ottenuto: controlla Client ID/Secret"
    say "Registro il dominio $DOMAIN su $fleet"
    curl -fsS -X POST "$fleet/api/1/partner_accounts" \
      -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
      -d "{\"domain\":\"$DOMAIN\"}" >/dev/null || die "registrazione partner fallita su $fleet"
    registered="$(curl -fsS "$fleet/api/1/partner_accounts/public_key?domain=$DOMAIN" -H "Authorization: Bearer $token" \
      | json 'd["response"]["public_key"]')"
    [[ "$registered" == "$expected" ]] || die "la chiave registrata su $fleet non coincide con public.pem"
  done
  say "OK partner registrato in tutte le regioni e chiave verificata"
}

cmd_pair() {
  cat <<TXT

Sul telefono, nell'app Tesla con la tua auto selezionata, apri:

    https://tesla.com/_ak/$DOMAIN

Conferma l'aggiunta della chiave. (Puoi anche farlo dall'orologio: "3. Abbina la chiave".)
TXT
  read -r -p "Premi Invio quando la chiave risulta aggiunta in auto (Controlli → Blocchi)… " _
  say "OK abbinamento confermato"
}

tesla_control() {
  local bin; bin="$(go env GOPATH)/bin/tesla-control"
  if [[ ! -x "$bin" ]]; then
    say "Installo tesla-control (SDK ufficiale Tesla)"
    go install github.com/teslamotors/vehicle-command/cmd/tesla-control@latest
  fi
  "$bin" "$@"
}

cmd_ble_check() {
  load_config
  say "Prova via Bluetooth con la chiave del dominio (stai vicino all'auto)"
  if tesla_control -ble -vin "$VIN" -key-file "$SECRETS/private.pem" flash-lights; then
    say "OK le luci hanno lampeggiato: la stessa chiave funziona anche via Bluetooth"
    rm -f "$SECRETS/ble-private.pem" "$SECRETS/ble-public.pem"
    return 0
  fi
  say "La chiave del dominio non comanda via Bluetooth: serve una seconda chiave (ruolo driver)"
  ask "Genero e abbino una chiave solo-Bluetooth?" || die "annullato"
  [[ -f "$SECRETS/ble-private.pem" ]] || {
    openssl ecparam -name prime256v1 -genkey -noout -out "$SECRETS/ble-private.pem"
    chmod 600 "$SECRETS/ble-private.pem"
  }
  openssl ec -in "$SECRETS/ble-private.pem" -pubout -out "$SECRETS/ble-public.pem" 2>/dev/null
  tesla_control -ble -vin "$VIN" add-key-request "$SECRETS/ble-public.pem" driver cloud_key
  read -r -p "Appoggia la keycard sul tunnel e conferma sullo schermo dell'auto, poi premi Invio… " _
  tesla_control -ble -vin "$VIN" -key-file "$SECRETS/ble-private.pem" flash-lights \
    || die "la chiave Bluetooth non funziona ancora"
  say "OK chiave Bluetooth abbinata"
}

cmd_push_watch() {
  local serial="${1:-}"
  [[ -n "$serial" ]] || die "uso: setup.sh push-watch <seriale adb dell'orologio>"
  load_config
  local key pub ble_key="" ble_pub=""
  read -r key pub < <(key_hex "$SECRETS/private.pem")
  if [[ -f "$SECRETS/ble-private.pem" ]]; then read -r ble_key ble_pub < <(key_hex "$SECRETS/ble-private.pem"); fi
  local tmp; tmp="$(mktemp)"; chmod 600 "$tmp"
  trap 'rm -f "$tmp"' RETURN
  KEY="$key" PUB="$pub" BLE_KEY="$ble_key" BLE_PUB="$ble_pub" VIN="$VIN" CLIENT_ID="$CLIENT_ID" CLIENT_SECRET="$CLIENT_SECRET" \
    DOMAIN="$DOMAIN" RENDER_OPTIONS="${RENDER_OPTIONS:-}" python3 -I -c 'import json, os; print(json.dumps({k.lower(): os.environ[k] for k in
      ["KEY", "PUB", "BLE_KEY", "BLE_PUB", "VIN", "CLIENT_ID", "CLIENT_SECRET", "DOMAIN", "RENDER_OPTIONS"]}))' > "$tmp"
  say "Invio i segreti all'orologio $serial (cartella privata dell'app)"
  # Secrets travel over stdin into the app's private files dir; the app imports and deletes them.
  adb -s "$serial" exec-in run-as "$PACKAGE" sh -c 'mkdir -p files && cat > files/import.json' < "$tmp"
  # Classes keep the dev.giack.teslawear namespace whatever the build's application id.
  adb -s "$serial" shell am start -n "$PACKAGE/dev.giack.teslawear.setup.ImportSecretsActivity" >/dev/null
  say "OK controlla sull'orologio il messaggio 'Segreti importati'"
}

usage() {
  cat <<TXT
Uso: setup.sh <comando>
  keys                 genera la coppia di chiavi in secrets/
  pages                pubblica la chiave pubblica su https://$DOMAIN
  portal               istruzioni per developer.tesla.com e salvataggio credenziali
  register             registra il dominio sul Fleet API e verifica la chiave
  pair                 abbina la chiave all'auto con il link tesla.com/_ak
  ble-check            prova un comando Bluetooth (vicino all'auto); se serve abbina una chiave BLE
  push-watch <serial>  invia chiavi e credenziali all'app sull'orologio
TXT
}

case "${1:-}" in
  keys) cmd_keys ;;
  pages) cmd_pages ;;
  portal) cmd_portal ;;
  register) cmd_register ;;
  pair) cmd_pair ;;
  ble-check) cmd_ble_check ;;
  push-watch) shift; cmd_push_watch "$@" ;;
  *) usage; exit 1 ;;
esac
