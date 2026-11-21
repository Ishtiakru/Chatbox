#!/usr/bin/env python3
"""Register only Cloud Shell's Android debug certificate for Chatbox.

Uses the active gcloud account locally. Never prints or writes the access token.
Does not enable billing, modify production Firestore rules, build, or deploy.
The generated debug keystore is not suitable for production release signing.
"""
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

BASE = 'https://firebase.googleapis.com/v1beta1/projects/chatbox-afb8c/androidApps/1:1030482946047:android:d4cc66c5e17ae1bc8229ca'


def request(token, path, body=None):
    headers = {'Authorization': 'Bearer ' + token, 'Accept': 'application/json'}
    data = None
    if body is not None:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(body).encode('utf-8')
    try:
        with urlopen(Request(BASE + path, data=data, headers=headers), timeout=60) as response:
            return json.load(response)
    except HTTPError as error:
        # Only status is shown; never dump request headers, credentials or config.
        raise RuntimeError(f'Firebase Management API returned HTTP {error.code} for {path}. Check project access/API setup.') from None
    except URLError:
        raise RuntimeError('Unable to reach the Firebase Management API.') from None


def fingerprints(output):
    values = {}
    for key, hex_length, kind in [('SHA1', 40, 'SHA_1'), ('SHA256', 64, 'SHA_256')]:
        match = re.search(r'^\s*' + key + r':\s*([0-9A-Fa-f:]+)\s*$', output, re.MULTILINE)
        digest = match.group(1).replace(':', '').upper() if match else ''
        if len(digest) != hex_length or not re.fullmatch('[0-9A-F]+', digest):
            raise RuntimeError('Could not read the debug certificate fingerprints.')
        values[kind] = digest
    return values


def main():
    keystore = Path.home() / '.android' / 'debug.keystore'
    if not keystore.is_file():
        raise RuntimeError('Android debug keystore not found. Generate it first.')
    keytool = subprocess.run(['keytool', '-list', '-v', '-keystore', str(keystore), '-storepass', 'android', '-alias', 'androiddebugkey'], capture_output=True, text=True, check=True, env={**os.environ, 'LC_ALL': 'C'})
    values = fingerprints(keytool.stdout)
    token = subprocess.run(['gcloud', 'auth', 'print-access-token'], capture_output=True, text=True, check=True).stdout.strip()
    if not token:
        raise RuntimeError('No active Cloud Shell Google credentials found.')
    existing = request(token, '/sha').get('certificates', [])
    for kind, digest in values.items():
        present = any(item.get('certType') == kind and item.get('shaHash', '').replace(':', '').upper() == digest for item in existing)
        if present:
            print(kind + ': already registered')
        else:
            request(token, '/sha', {'shaHash': digest, 'certType': kind})
            print(kind + ': registered')
    result = request(token, '/config')
    raw = base64.b64decode(result['configFileContents'], validate=True)
    config = json.loads(raw)
    if config.get('project_info', {}).get('project_id') != 'chatbox-afb8c':
        raise RuntimeError('Unexpected Firebase project in downloaded config.')
    client = next((c for c in config.get('client', []) if c.get('client_info', {}).get('android_client_info', {}).get('package_name') == 'com.chatbox.apk'), None)
    if not client:
        raise RuntimeError('Expected Android package missing from downloaded config.')
    output = Path.home() / 'chatbox-firebase' / 'google-services.json'
    output.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    # Do not expose configuration contents in the terminal or screenshots.
    fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(raw)
    print('Android config saved locally at ~/chatbox-firebase/google-services.json')
    has_web = any(c.get('client_type') == 3 and c.get('client_id') for c in client.get('oauth_client', []))
    print('Web OAuth client present:', 'yes' if has_web else 'no; Google sign-in configuration needs attention')
    print('No APK was built or deployed.')
    if not has_web:
        sys.exit(2)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, KeyError, ValueError, OSError) as error:
        print('Setup failed:', str(error) if isinstance(error, RuntimeError) else type(error).__name__, file=sys.stderr)
        sys.exit(1)
