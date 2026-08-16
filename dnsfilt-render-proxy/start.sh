#!/bin/sh

envsubst '${PORT}' < /etc/nginx/nginx.conf > /tmp/nginx.conf

exec nginx -c /tmp/nginx.conf -g "daemon off;"