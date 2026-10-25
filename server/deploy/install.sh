#!/bin/bash
# Installs omniptvd as a systemd service. Idempotent. Run as: sudo ./deploy/install.sh   (read it first)
# Touches only: /opt/omniptv, /etc/omniptv, /var/lib/omniptv, one systemd unit, one logind drop-in, and a LAN-only firewall rule.
set -euo pipefail
cd "$(dirname "$0")/.."
[ "$EUID" -eq 0 ] || { echo "run with sudo"; exit 1; }
id omniptv &>/dev/null || useradd --system --no-create-home --shell /usr/sbin/nologin omniptv
install -d -o root -g root /opt/omniptv /etc/omniptv
install -d -o omniptv -g omniptv /var/lib/omniptv
install -m 0755 server/omniptvd.py /opt/omniptv/omniptvd.py
for m in api library medianame mediaprobe; do install -m 0644 server/$m.py /opt/omniptv/$m.py; done
[ -f /var/lib/omniptv/catalog.json ] || install -o omniptv -g omniptv -m 0644 server/catalog.seed.json /var/lib/omniptv/catalog.json
[ -f /etc/omniptv/config.json ] || cat > /etc/omniptv/config.json <<'C'
{"bind": "0.0.0.0", "port": 8765, "probe_every_s": 1800, "epg_source": ""}
C
# Media for OMNI Cinema lives here (the service cannot see /home: ProtectHome=yes). Put or link films under Movies/ and shows under Shows/.
install -d -m 0755 /srv/omni-media /srv/omni-media/Movies /srv/omni-media/Shows
# The read PIN protects /status and /media. Created once; delete /etc/omniptv/read.pin and re-run to change it.
NEWPIN=""
if [ ! -f /etc/omniptv/read.pin ]; then
  NEWPIN="$(python3 -I -c 'import secrets;print("".join(secrets.choice("0123456789") for _ in range(8)))')"
  ( umask 077; echo "$NEWPIN" > /etc/omniptv/read.pin ); chown omniptv /etc/omniptv/read.pin
fi
# Merge new settings into an existing config without touching the ones already there.
python3 -I - <<'PY'
import json
p = "/etc/omniptv/config.json"; c = json.load(open(p))
c.setdefault("media_root", "/srv/omni-media"); c.setdefault("read_token_file", "/etc/omniptv/read.pin")
json.dump(c, open(p, "w"), indent=1)
PY
[ -f /etc/omniptv/admin.token ] || { umask 077; head -c 24 /dev/urandom | base64 > /etc/omniptv/admin.token; chown omniptv /etc/omniptv/admin.token; }
install -m 0644 deploy/omniptvd.service /etc/systemd/system/omniptvd.service
install -d /etc/systemd/logind.conf.d && install -m 0644 deploy/logind-omniptv.conf /etc/systemd/logind.conf.d/omniptv.conf
systemctl daemon-reload
systemctl enable omniptvd.service
systemctl restart omniptvd.service          # restart so an upgrade runs the new code
# only the home LAN may reach the port; nothing is forwarded to the internet
if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then LAN="${OMNI_LAN:-$(ip -4 route | awk '/proto kernel/ && /src/ {print $1; exit}')}"; [ -n "$LAN" ] && ufw allow from "$LAN" to any port 8765 proto tcp; fi
echo "Installed. Lid handling applies after: systemctl restart systemd-logind  (this ends the current graphical login session, so do it from a console or after saving work)."
[ -n "$NEWPIN" ] && echo "OMNI server read PIN (enter it once in OMNI Cinema on the TV): $NEWPIN" || echo "Read PIN unchanged: sudo cat /etc/omniptv/read.pin"
echo "Check: curl http://$(hostname -I | awk '{print $1}'):8765/health"
