#!/data/data/com.termux/files/usr/bin/sh
# Starts Jarvis in Termux and opens it in the browser.
cd "$(dirname "$0")"
command -v termux-wake-lock >/dev/null && termux-wake-lock
(sleep 1; command -v termux-open-url >/dev/null && termux-open-url http://localhost:8080) &
exec node server.js
