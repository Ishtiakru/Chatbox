#!/usr/bin/env python3
"""Upload a reviewed archive to one existing, non-public Google Drive file.

Requires an access token in CHATBOX_DRIVE_TOKEN and a file ID in
CHATBOX_DRIVE_FILE_ID. Does not create files or grant permissions. Run only
from a trusted environment; stdout must not contain credentials.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


API = 'https://www.googleapis.com/drive/v3/files/'
UPLOAD = 'https://www.googleapis.com/upload/drive/v3/files/'


def request(url, token, method='GET', data=None, content_type=None):
    headers = {'Authorization': 'Bearer ' + token}
    if content_type:
        headers['Content-Type'] = content_type
    req = Request(url, data=data, method=method, headers=headers)
    try:
        with urlopen(req, timeout=120) as response:
            return json.load(response)
    except (HTTPError, URLError, TimeoutError, ValueError) as exc:
        # Do not print tokens, response bodies, or request URLs.
        kind = f'HTTP {exc.code}' if isinstance(exc, HTTPError) else type(exc).__name__
        raise RuntimeError(f'Drive request failed ({kind}). Check permissions and API configuration.') from None


def main():
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python3 scripts/publish-source-handover.py /path/to/reviewed.zip')
    token = os.environ.get('CHATBOX_DRIVE_TOKEN', '')
    file_id = os.environ.get('CHATBOX_DRIVE_FILE_ID', '')
    archive = Path(sys.argv[1])
    if not token or not re.fullmatch(r'[A-Za-z0-9_-]{10,200}', file_id):
        raise SystemExit('A Drive token and valid destination file ID are required.')
    if not archive.is_file() or archive.suffix.lower() != '.zip':
        raise SystemExit('A ZIP archive is required.')
    # The exporter produces small archives; bound memory and reject empty input.
    size = archive.stat().st_size
    if not 0 < size <= 100 * 1024 * 1024:
        raise SystemExit('The archive must be between 1 byte and 100 MiB.')

    metadata_query = urlencode({'supportsAllDrives': 'true', 'fields': 'id,name,mimeType,trashed,capabilities(canModifyContent)'})
    metadata = request(API + file_id + '?' + metadata_query, token)
    if (metadata.get('id') != file_id or metadata.get('trashed') is not False
            or metadata.get('mimeType') not in ('application/zip', 'application/x-zip-compressed')
            or metadata.get('capabilities', {}).get('canModifyContent') is not True):
        raise SystemExit('Destination is not a writable, untrashed ZIP file.')

    # Fail closed on broad links and domain-wide sharing. The writer must be
    # able to list every page. This is not a replacement for an access audit.
    page = None
    while True:
        params = {'supportsAllDrives': 'true', 'pageSize': '100', 'fields': 'nextPageToken,permissions(type,role,allowFileDiscovery)'}
        if page:
            params['pageToken'] = page
        result = request(API + file_id + '/permissions?' + urlencode(params), token)
        permissions = result.get('permissions')
        if not isinstance(permissions, list) or not permissions:
            raise SystemExit('Cannot verify Drive sharing permissions.')
        if any(p.get('type') not in ('user', 'group') for p in permissions):
            raise SystemExit('Destination has public, domain-wide or unknown access; restrict and review it first.')
        page = result.get('nextPageToken')
        if not page:
            break

    content = archive.read_bytes()
    checksum = hashlib.md5(content, usedforsecurity=False).hexdigest()  # Drive integrity comparison, not security.
    query = urlencode({'uploadType': 'media', 'supportsAllDrives': 'true', 'fields': 'id,md5Checksum'})
    updated = request(UPLOAD + file_id + '?' + query, token, method='PATCH', data=content, content_type='application/zip')
    if updated.get('id') != file_id or updated.get('md5Checksum') != checksum:
        raise SystemExit('Upload result or checksum could not be confirmed. Inspect Drive before retrying.')
    print('Existing private Drive ZIP updated and its content checksum confirmed.')


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        raise SystemExit(str(error))
