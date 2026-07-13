# Deployment Plan — Self-Hosted on a Free Cloud VM

Goal: JobMaster reachable over the internet from any device, for $0/month in
hosting cost (AI API usage is separate — self-hosting doesn't change what
OpenAI/Anthropic charge per call).

## Why a cloud VM, not a home server

You want to reach it from anywhere without depending on a machine at home
staying powered on and awake. That rules out the laptop-plus-tunnel option —
a rented (but free) always-on VM is the right shape.

## Why Oracle Cloud specifically

It's the only major cloud whose free tier is **free forever**, not a
12-month trial (AWS/Azure/GCP free tiers expire or are tiny). The "Always
Free" Ampere A1 shape gives up to 4 ARM CPUs / 24GB RAM — heavily
over-provisioned for this stack (Postgres + Spring Boot + nginx + Caddy idles
under ~1GB RAM). The tradeoffs: signup asks for a credit card for identity
verification (Always Free resources are never billed unless you explicitly
upgrade to Pay-As-You-Go), and in some regions Ampere capacity is temporarily
unavailable at signup — retry a different region/availability domain if so.

## Architecture: Caddy in front, everything else unchanged

```
Internet → Caddy (:443, TLS + Basic Auth) → frontend (nginx, existing) → backend (existing) → postgres (existing)
```

- **Caddy** is the only container reachable from the internet. It terminates
  HTTPS (automatic Let's Encrypt certificate, auto-renewed) and gates the
  entire app behind HTTP Basic Auth — one username/password, since this is a
  single-user app and full account/login infrastructure would be pure
  overhead.
- **frontend/backend/postgres** keep working exactly as before, but their
  host ports now default to binding `127.0.0.1` only (was `0.0.0.0`), so they
  are invisible from outside the VM — only reachable from Caddy over the
  internal Docker network by service name. This closes the gap where, right
  now, the app has **no authentication at all**: anyone who found the URL
  could read your profile, burn your API credits generating CVs, or delete
  applications. Basic Auth in front is the fix.
- Added via a second compose file (`compose.prod.yaml`), so local development
  (`docker compose up`) is completely unaffected — Caddy only starts when you
  explicitly include that file, which you only do on the server.

No CORS changes needed: the browser always talks to a single origin
(your domain via Caddy); everything past that is internal container-to-container
traffic, same pattern as the existing Vite dev proxy.

## What's in the repo now vs. what you do manually

I can't create cloud accounts, VMs, or DNS records on your behalf — those
need your credit card / phone verification / web console clicks. What's
implemented and tested locally:

- `Caddyfile` — reverse proxy + auto HTTPS + Basic Auth config
- `compose.prod.yaml` — adds the Caddy service (only used on the server)
- `compose.yaml` — port bindings now default to `127.0.0.1` (override with
  `BIND_ADDRESS=0.0.0.0` in `.env` if you want LAN access during local dev,
  e.g. testing from your phone on the same WiFi)
- `.env` / `.env.example` — new variables: `DOMAIN`, `ACME_EMAIL`,
  `BASIC_AUTH_USER`, `BASIC_AUTH_HASH`
- `deploy.sh` — one-command redeploy (`git pull` + rebuild) for the server

## Runbook — steps you run yourself

### 1. Create the VM (Oracle Cloud Console, ~10 min)

1. Sign up at oracle.com/cloud/free.
2. Compute → Instances → Create Instance.
3. Image: **Ubuntu 22.04** (or 24.04). Shape: **VM.Standard.A1.Flex**
   (Ampere/ARM, Always Free eligible) — 2 OCPU / 12GB RAM is plenty.
4. Add your SSH public key (generate one locally first if you don't have one:
   `ssh-keygen -t ed25519`). Oracle is key-only auth, no root password.
5. Create. Note the **public IP** once it's running. In Networking, consider
   reserving that IP as a permanent "Reserved Public IP" so it never changes.

### 2. Open the firewall (the classic Oracle gotcha)

Oracle has **two** firewalls, both must allow traffic or nothing works even
though everything "looks" configured right:

- **Security List** (Console → Networking → Virtual Cloud Networks → your
  VCN → Security Lists): add Ingress Rules for TCP 80 and TCP 443 from
  `0.0.0.0/0`. (22/SSH is usually already allowed — consider restricting its
  source CIDR to your home IP for safety.)
- **OS-level firewall** on the VM itself (Oracle's Ubuntu images sometimes
  ship with restrictive `iptables`/`netfilter-persistent` rules): SSH in and
  run:
  ```bash
  sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
  sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
  sudo netfilter-persistent save   # if that tool is installed; otherwise your rules persist via iptables-persistent
  ```

### 3. Install Docker

```bash
ssh ubuntu@<your-vm-ip>
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# log out and back in so the group membership takes effect
```

### 4. Get a free domain (DuckDNS)

Oracle's IP alone won't get you a trusted HTTPS cert — Let's Encrypt needs a
domain name.

1. Go to duckdns.org, sign in (GitHub/Google/etc.).
2. Create a subdomain, e.g. `yourname` → `yourname.duckdns.org`.
3. Paste your VM's public IP into the dashboard and click "update ip". Since
   the IP is reserved/static, you only need to do this once.

### 5. Get the code onto the VM

The project isn't in a git repo yet. Simplest path: initialize git locally,
push to a **private** GitHub repo, then clone it on the VM — this also gives
you the `deploy.sh` one-liner for future updates. (Tell me if you want help
setting up the GitHub repo — I can prepare the local commit but won't create
or push to a remote without your go-ahead.) Alternative: `scp -r` the folder
directly to the VM, no git needed, just more manual for future updates.

### 6. Configure production secrets

On the VM, create `.env` (copy `.env.example`, then fill in):

```properties
OPENAI_API_KEY=sk-...
DB_PASSWORD=<pick something that isn't "password">
DOMAIN=yourname.duckdns.org
ACME_EMAIL=you@example.com
BASIC_AUTH_USER=yourname
BASIC_AUTH_HASH=<see below>
```

Generate the Basic Auth password hash (bcrypt) — do this once, anywhere
Docker runs:

```bash
docker run --rm caddy:2-alpine caddy hash-password --plaintext 'yourpassword'
```

Paste the output (starts with `$2a$...`) as `BASIC_AUTH_HASH`. If Caddy ever
fails to read it correctly, double every `$` to `$$` in the `.env` file —
Compose variable interpolation can otherwise misread a lone `$` as the start
of another variable reference.

`chmod 600 .env` — it now holds real secrets.

### 7. First deploy

```bash
docker compose -f compose.yaml -f compose.prod.yaml up -d --build
```

Give it ~30 seconds for Caddy to obtain the Let's Encrypt certificate, then
visit `https://yourname.duckdns.org` — your browser will prompt for the
Basic Auth username/password before showing anything.

### 8. Redeploy after future changes

```bash
./deploy.sh
```

(`git pull` + rebuild + restart. Requires step 5's git setup; without it,
re-run the `scp` + `docker compose ... up -d --build` manually.)

### 9. Backups (do this — Postgres holds your profile, applications, and generated documents)

```bash
mkdir -p ~/backups
crontab -e
# add this line: daily dump at 03:00, kept for 30 days
0 3 * * * docker exec jobmaster-postgres-1 pg_dump -U postgres jobmaster | gzip > ~/backups/jobmaster-$(date +\%F).sql.gz && find ~/backups -mtime +30 -delete
```

Restore if ever needed: `gunzip -c backup.sql.gz | docker exec -i jobmaster-postgres-1 psql -U postgres jobmaster`.

## If Oracle signup doesn't work out

Ampere capacity is occasionally unavailable in a region at signup time —
retrying later or picking a different availability domain usually resolves
it. If you'd rather not deal with it at all, the fallback is a ~$4-6/month
VPS (Hetzner, DigitalOcean) — same Caddy/Compose setup applies unchanged,
just not free.
