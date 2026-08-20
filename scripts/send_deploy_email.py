#!/usr/bin/env python3
"""
Gmail SMTP Email Reporter for DNSFilt Continuous Deployment (CD).
Sends high-priority HTML & text alerts when a service deployment completes (SUCCESS or FAILED).
"""

import os
import sys
import smtplib
import time
from datetime import datetime, timezone
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from typing import Optional

# ---------------------------------------------------------------------------
# SMTP Configuration
# ---------------------------------------------------------------------------
SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.environ.get("SMTP_PORT", 587))
GMAIL_SENDER = os.environ.get("GMAIL_SENDER", "iamkaushik2014@gmail.com")
GMAIL_RECIPIENT = os.environ.get("GMAIL_RECIPIENT", "iamkaushik2014@gmail.com")


def _get_app_password() -> Optional[str]:
    """Retrieve Gmail App Password from environment or known .env files."""
    pwd = (
        os.environ.get("GMAIL_APP_PASSWORD")
        or os.environ.get("GMAIL_PASSWORD_TOKEN")
        or os.environ.get("GMAIL_PASSWORD")
    )
    if pwd:
        return pwd.strip()

    # Attempt to load from known platform .env files on OCI VM
    candidate_paths = [
        "/opt/platform/.env",
        "/opt/platform/dnsfilt/.env",
        "/opt/platform/dnsfilt/dnsfilt-admin-backend/.env",
        os.path.expanduser("~/dnsfilt/.env"),
        os.path.expanduser("~/.env"),
    ]
    for env_path in candidate_paths:
        if os.path.exists(env_path):
            try:
                with open(env_path, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line.startswith("#") or "=" not in line:
                            continue
                        k, v = line.split("=", 1)
                        k = k.strip()
                        v = v.strip().strip("'\"")
                        if k in ("GMAIL_APP_PASSWORD", "GMAIL_PASSWORD_TOKEN", "GMAIL_PASSWORD") and v:
                            return v
            except Exception:
                pass
    return None


def send_deploy_report(
    service_name: str,
    status: str,
    image_tag: str = "latest",
    port: str = "N/A",
    host: str = "OCI ARM64 VM",
    error_message: Optional[str] = None,
) -> bool:
    """Sends a high-priority email report after service deployment."""
    app_password = _get_app_password()
    if not app_password:
        print("[CD Email] ⚠️ GMAIL_APP_PASSWORD / GMAIL_PASSWORD_TOKEN not found — skipping email.")
        return False

    is_success = status.upper() in ("SUCCESS", "OK", "HEALTHY", "TRUE")
    status_str = "SUCCESS" if is_success else "FAILED"
    status_color = "#2e7d32" if is_success else "#c62828"
    status_icon = "✅" if is_success else "❌"
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime())

    subject = f"🚀 [{status_str}] DNSFilt CD Deployment — {service_name} ({image_tag}) — {timestamp}"

    error_block = ""
    if error_message:
        error_block = f"""
            <div style="background:#ffebee;border-left:4px solid #c62828;
                        padding:15px;margin-bottom:20px;font-family:monospace;
                        font-size:12px;color:#b71c1c;white-space:pre-wrap;overflow-x:auto;">
                <strong>Error & Diagnostics:</strong><br>{error_message}
            </div>"""

    tip_text = (
        f"🎉 <strong>{service_name}</strong> is LIVE, healthy, and accepting traffic on port <strong>{port}</strong>."
        if is_success
        else f"⚠️ Deployment failed for <strong>{service_name}</strong>. Automatic rollback was triggered. Please inspect container logs."
    )

    html_body = f"""<!DOCTYPE html>
<html>
<head>
  <style>
    body {{
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      background-color: #f4f6f9;
      margin: 0; padding: 20px; color: #333;
    }}
    .card {{
      max-width: 680px; margin: 0 auto; background: #ffffff;
      border-radius: 12px; box-shadow: 0 4px 16px rgba(0,0,0,0.08); overflow: hidden;
      border: 1px solid #e0e0e0;
    }}
    .header {{
      background-color: {status_color}; color: #ffffff;
      padding: 22px; text-align: center;
    }}
    .header h1 {{ margin: 0; font-size: 20px; font-weight: 700; letter-spacing: 0.5px; }}
    .content {{ padding: 25px; }}
    .meta-table {{ width: 100%; border-collapse: collapse; margin-bottom: 20px; }}
    .meta-table td {{
      padding: 10px 14px; border-bottom: 1px solid #eeeeee; font-size: 13px;
    }}
    .meta-table td.label {{
      font-weight: 600; width: 32%; background-color: #f8f9fa; color: #555;
    }}
    .meta-table code {{
      background: #f1f3f5; padding: 2px 6px; border-radius: 4px; font-family: monospace; font-size: 12px;
    }}
    .tip {{
      background: #f8f9fa; border-left: 4px solid {'#2e7d32' if is_success else '#c62828'};
      padding: 14px 16px; font-size: 13px; color: #444; border-radius: 0 6px 6px 0;
    }}
  </style>
</head>
<body>
  <div class="card">
    <div class="header">
      <h1>{status_icon} DEPLOYMENT {status_str}: {service_name}</h1>
    </div>
    <div class="content">
      <table class="meta-table">
        <tr><td class="label">Service</td><td><strong>{service_name}</strong></td></tr>
        <tr><td class="label">Image Tag</td><td><code>{image_tag}</code></td></tr>
        <tr><td class="label">Status</td>
            <td style="color:{status_color};font-weight:bold;">{status_str}</td></tr>
        <tr><td class="label">Bound Port</td><td><code>{port}</code></td></tr>
        <tr><td class="label">Target Host</td><td>{host}</td></tr>
        <tr><td class="label">Timestamp</td><td>{timestamp}</td></tr>
      </table>

      {error_block}

      <div class="tip">
        {tip_text}
      </div>
    </div>
  </div>
</body>
</html>"""

    err_section = f"\nError Detail:\n{error_message}\n\n" if error_message else ""
    text_body = f"""DNSFilt Continuous Deployment Report
====================================================
Service     : {service_name}
Image Tag   : {image_tag}
Status      : {status_str}
Port        : {port}
Host        : {host}
Timestamp   : {timestamp}
====================================================
{err_section}{tip_text.replace('<strong>', '').replace('</strong>', '')}
"""

    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"] = GMAIL_SENDER
    msg["To"] = GMAIL_RECIPIENT
    msg["X-Priority"] = "1"
    msg["X-MSMail-Priority"] = "High"
    msg["Importance"] = "High"

    msg.attach(MIMEText(text_body, "plain", "utf-8"))
    msg.attach(MIMEText(html_body, "html", "utf-8"))

    try:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as server:
            server.starttls()
            server.login(GMAIL_SENDER, app_password)
            server.sendmail(GMAIL_SENDER, [GMAIL_RECIPIENT], msg.as_string())
        print(f"[CD Email] ✅ Deployment report sent to {GMAIL_RECIPIENT} for {service_name} ({status_str})")
        return True
    except Exception as exc:
        print(f"[CD Email] ⚠️ Failed to send email alert: {exc}")
        return False


if __name__ == "__main__":
    # CLI Invocation:
    # python3 send_deploy_email.py <SERVICE> <STATUS> [TAG] [PORT] [ERROR_MSG]
    if len(sys.argv) < 3:
        print("Usage: send_deploy_email.py <SERVICE> <STATUS> [TAG] [PORT] [ERROR_MSG]")
        sys.exit(0)

    svc = sys.argv[1]
    stat = sys.argv[2]
    tg = sys.argv[3] if len(sys.argv) > 3 else "latest"
    prt = sys.argv[4] if len(sys.argv) > 4 else "N/A"
    err = sys.argv[5] if len(sys.argv) > 5 else None

    send_deploy_report(
        service_name=svc,
        status=stat,
        image_tag=tg,
        port=prt,
        error_message=err,
    )
