#!/usr/bin/env python3
"""Create a source snapshot plus the complete reachable history for one branch.

Only committed tracked files are exported; ignored files and credentials are not
included. This script never uploads the archive or changes Drive permissions.
"""
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def main():
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python3 scripts/export-source-handover.py /path/to/output.zip')
    output = Path(sys.argv[1]).resolve()
    root = Path(git('rev-parse', '--show-toplevel'))
    os.chdir(root)
    if git('rev-parse', '--is-shallow-repository') != 'false':
        raise SystemExit('Full Git history is required; fetch the complete history first.')
    branch = git('symbolic-ref', '--quiet', '--short', 'HEAD')
    commit = git('rev-parse', 'HEAD')
    ref = f'refs/heads/{branch}'
    if not branch or git('rev-parse', ref) != commit:
        raise SystemExit('A checked-out branch at HEAD is required.')
    if output.suffix.lower() != '.zip':
        raise SystemExit('Output must end in .zip')
    output.parent.mkdir(parents=True, exist_ok=True)
    # Keep the temporary archive on the destination filesystem for atomic replace.
    with tempfile.TemporaryDirectory(dir=output.parent) as directory:
        bundle = Path(directory) / 'Chatbox-history.bundle'
        archive = Path(directory) / 'handover.zip'
        subprocess.run(['git', 'archive', '--format=zip', '--prefix=Chatbox/', '-o', str(archive), commit], check=True)
        subprocess.run(['git', 'bundle', 'create', str(bundle), ref], check=True)
        subprocess.run(['git', 'bundle', 'verify', str(bundle)], check=True)
        note = f'''CHATBOX SOURCE HANDOVER\n\nBranch: {branch}\nCommit: {commit}\n\nThis is a snapshot of tracked files at the commit above, and a Git bundle of all commits reachable from that branch. Uncommitted, ignored and external files are not included. This archive does not update itself.\n\nExtract the ZIP, then clone the history with:\n  git clone -b {branch} Chatbox/history/Chatbox-history.bundle Chatbox-history\n\nSee Chatbox/README.md and .env.example. Dependencies, databases, private Firebase configuration, signing keys and cloud state must be managed separately and securely. The app contains experimental features and is not a production release. Share the archive only with authorized developers.\n'''
        with zipfile.ZipFile(archive, 'a', compression=zipfile.ZIP_DEFLATED) as z:
            z.write(bundle, 'Chatbox/history/Chatbox-history.bundle')
            z.writestr('Chatbox/DEVELOPER-HANDOVER.txt', note)
        # Replace only after creating the archive successfully.
        os.replace(archive, output)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    print(f'Archive: {output}\nCommit: {commit}\nSHA-256: {digest}')
    if git('status', '--porcelain'):
        print('Note: uncommitted changes were not included.', file=sys.stderr)


if __name__ == '__main__':
    main()
