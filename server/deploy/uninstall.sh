#!/bin/bash
# Reverses install.sh. Keeps /var/lib/omniptv (your catalogue and state) unless you pass --purge.
set -euo pipefail
[ "$EUID" -eq 0 ] || { echo "run with sudo"; exit 1; }
systemctl disable --now omniptvd.service 2>/dev/null || true
rm -f /etc/systemd/system/omniptvd.service /etc/systemd/logind.conf.d/omniptv.conf /opt/omniptv/omniptvd.py
systemctl daemon-reload
[ "${1:-}" = "--purge" ] && rm -rf /var/lib/omniptv /etc/omniptv
echo "Removed. Restart systemd-logind to restore default lid behaviour."
