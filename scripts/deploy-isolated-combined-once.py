"""One-off, owner-approved replacement of the isolated test Firestore rules.

Never target production. Firebase Rules releases do not expose a documented
compare-and-swap precondition; the double-read reduces but does not eliminate
races with other publishers. Never retry an uncertain PATCH automatically.
No token, UID, or rules contents are printed or persisted.
"""
import hashlib
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request

PROJECT = 'chatbox-test-2c236731bf'
RELEASE = f'projects/{PROJECT}/releases/cloud.firestore'
BASE = 'https://firebaserules.googleapis.com/v1/'
EXPECTED_OLD_TOKEN_DIGEST = '9d00fd8a52a4f9922f90842d123ac0b37009a30c4d5000b78c610e7ac9f12a24'
PATTERN = r'''(?:'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*"|//[^\r\n]*|/\*[\s\S]*?\*/|[A-Za-z_][A-Za-z_0-9]*|[0-9]+|[^\s])'''


def tokens(source):
    return [x for x in re.findall(PATTERN, source) if not x.startswith('//') and not x.startswith('/*')]


def parse_old(source):
    items = tokens(source)
    digest = hashlib.sha256(json.dumps(items, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()
    if digest != EXPECTED_OLD_TOKEN_DIGEST:
        raise ValueError('Published rules do not match the previously inspected baseline; no deployment allowed.')
    prefix = ['function','signedIn','(',')','{','return','request','.','auth','!','=','null','&','&','request','.','auth','.','uid','in','[']
    suffix = [']',';','}']
    indices = [i for i in range(len(items)) if items[i:i+len(prefix)] == prefix]
    if len(indices) != 1:
        raise ValueError('Unexpected UID gate; no deployment allowed.')
    tail = items[indices[0]+len(prefix):]
    if len(tail) < 6 or tail[1] != ',' or tail[3:6] != suffix:
        raise ValueError('Unexpected UID gate shape; no deployment allowed.')
    first, second = tail[0], tail[2]
    valid = re.compile(r"^'[A-Za-z0-9_-]{1,128}'$")
    if not valid.fullmatch(first) or not valid.fullmatch(second) or first == second:
        raise ValueError('Invalid UID gate; no deployment allowed.')
    return first[1:-1], second[1:-1]


def main():
    token = subprocess.run(['gcloud','auth','print-access-token'],check=True,capture_output=True,text=True).stdout.strip()
    def api(method, path, body=None):
        headers={'Authorization':'Bearer '+token}
        data=None
        if body is not None:
            headers['Content-Type']='application/json'
            data=json.dumps(body).encode()
        req=urllib.request.Request(BASE+path,data=data,headers=headers,method=method)
        try:
            with urllib.request.urlopen(req,timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'{method} failed with HTTP {error.code}; response not printed') from None
        except (urllib.error.URLError, TimeoutError):
            raise RuntimeError(f'{method} network failure; write outcome may be unknown; do not retry automatically') from None

    def current_release():
        release=api('GET',RELEASE)
        if release.get('name') != RELEASE:
            raise ValueError('Unexpected release name.')
        name=release.get('rulesetName','')
        if not re.fullmatch(r'projects/'+re.escape(PROJECT)+r'/rulesets/[A-Za-z0-9-]+', name):
            raise ValueError('Unexpected ruleset name.')
        return name

    old_name=current_release()
    previous=api('GET',old_name)
    files=previous.get('source',{}).get('files',[])
    if len(files)!=1 or not isinstance(files[0].get('content'),str):
        raise ValueError('Unexpected published rules source.')
    first,second=parse_old(files[0]['content'])
    result=subprocess.run(['node','scripts/generate-isolated-combined-rules.mjs',first,second],check=True,capture_output=True,text=True)
    candidate=result.stdout
    del first,second
    print('::notice::Baseline matched; previous ruleset for rollback: '+old_name,flush=True)
    if current_release()!=old_name:
        raise ValueError('Release changed during preparation; stopped before ruleset creation.')
    created=api('POST',f'projects/{PROJECT}/rulesets',{'source':{'files':[{'name':'firestore.rules','content':candidate}]}})
    new_name=created.get('name','')
    if not re.fullmatch(r'projects/'+re.escape(PROJECT)+r'/rulesets/[A-Za-z0-9-]+',new_name):
        raise ValueError('Ruleset creation returned an unexpected resource; release not changed.')
    print('::notice::New ruleset created: '+new_name,flush=True)
    if current_release()!=old_name:
        raise ValueError('Release changed during preparation; release not changed by this workflow.')
    # The service does not document a release compare-and-swap. This last read
    # is not atomic with PATCH; concurrent console publishers must not run.
    print('::notice::Publishing test project release; do not automatically retry an uncertain result.',flush=True)
    updated=api('PATCH',RELEASE,{'release':{'name':RELEASE,'rulesetName':new_name},'updateMask':'rulesetName'})
    if updated.get('rulesetName')!=new_name:
        raise ValueError('Unexpected update response; inspect release manually before retrying.')
    actual=current_release()
    if actual!=new_name:
        raise ValueError('Release does not point to the requested ruleset; inspect manually.')
    active=api('GET',actual)
    active_files=active.get('source',{}).get('files',[])
    if len(active_files)!=1 or active_files[0].get('content')!=candidate:
        raise ValueError('Published rules content did not match the candidate; inspect manually.')
    print('::notice::SUCCESS: isolated test Firestore release points to the expected restricted combined ruleset.',flush=True)
    print('::notice::Previous ruleset retained for rollback: '+old_name,flush=True)

if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError:
        print('::error::Local preparation failed; details suppressed.',file=sys.stderr,flush=True)
        sys.exit(1)
    except (ValueError, RuntimeError) as error:
        print('::error::'+str(error),file=sys.stderr,flush=True)
        sys.exit(1)
