"""Owner-approved, one-time *test app* setup. Never touches production.

Permits unrecognized app versions for the isolated Android app without changing
other integrity fields or enforcement. Registers only the current debug APK's
SHA-256 if listing proves it is absent. No retry on an uncertain mutation.
"""
import json, subprocess, sys, urllib.error, urllib.parse, urllib.request

PROJECT='chatbox-test-2c236731bf'
APP='1:1012186851732:android:060ca550459fca7177c781'
RESOURCE='projects/1012186851732/apps/'+APP+'/playIntegrityConfig'
CHECK_URL='https://firebaseappcheck.googleapis.com/v1/'+RESOURCE
SHA_URL='https://firebase.googleapis.com/v1beta1/projects/'+PROJECT+'/androidApps/'+APP+'/sha'
SHA256='FA607FC61AE362D1D66BB365CC0C9A5DA2083A64D513B5B66728865918B6E935'


def main():
    token=subprocess.run(['gcloud','auth','print-access-token'],check=True,capture_output=True,text=True).stdout.strip()
    def call(method,url,body=None):
        data=json.dumps(body).encode() if body is not None else None
        headers={'Authorization':'Bearer '+token}
        if data is not None: headers['Content-Type']='application/json'
        request=urllib.request.Request(url,headers=headers,data=data,method=method)
        try:
            with urllib.request.urlopen(request,timeout=30) as response: return json.load(response)
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'{method} HTTP {error.code}; response suppressed') from None
        except (urllib.error.URLError, TimeoutError):
            raise RuntimeError(f'{method} network failure; mutation result may be unknown; inspect before retrying') from None
    problems=[]
    try:
        current=call('GET',CHECK_URL)
        if current.get('name')!=RESOURCE or not isinstance(current.get('appIntegrity',{}),dict):
            raise ValueError('Unexpected test app configuration; no update attempted')
        prior=current.get('appIntegrity',{}).get('allowUnrecognizedVersion',False)
        if not isinstance(prior,bool): raise ValueError('Unexpected integrity setting')
        print('::notice::Previous test app allowUnrecognizedVersion: '+str(prior),flush=True)
        if not prior:
            if call('GET',CHECK_URL)!=current: raise ValueError('Configuration changed during preparation; stopped')
            print('::notice::Updating only the isolated app recognition requirement.',flush=True)
            call('PATCH', CHECK_URL+'?'+urllib.parse.urlencode({'updateMask':'appIntegrity.allowUnrecognizedVersion'}),
                 {'name':RESOURCE,'appIntegrity':{'allowUnrecognizedVersion':True}})
        after=call('GET',CHECK_URL)
        if after.get('appIntegrity',{}).get('allowUnrecognizedVersion') is not True:
            raise ValueError('Readback did not confirm off-Play support; inspect manually')
        for key in ('deviceIntegrity','accountDetails','tokenTtl'):
            if after.get(key)!=current.get(key): raise ValueError('Other config changed during operation; inspect manually')
        print('::notice::Isolated app allows unrecognized versions; device, account and TTL settings unchanged.',flush=True)
    except (RuntimeError,ValueError) as error:
        print('::warning::App Check setup incomplete: '+str(error),flush=True); problems.append('app-check')
    try:
        listing=call('GET',SHA_URL)
        if not isinstance(listing.get('certificates',[]),list): raise ValueError('Unexpected SHA listing')
        def has_sha(data):
            return any(str(item.get('shaHash','')).replace(':','').upper()==SHA256 and item.get('certType')=='SHA_256' for item in data.get('certificates',[]) if isinstance(item,dict))
        if not has_sha(listing):
            print('::notice::Registering the existing test APK SHA-256 only.',flush=True)
            formatted=':'.join(SHA256[i:i+2] for i in range(0,len(SHA256),2))
            call('POST',SHA_URL,{'shaHash':formatted,'certType':'SHA_256'})
        if not has_sha(call('GET',SHA_URL)): raise ValueError('SHA-256 readback did not confirm registration')
        print('::notice::Existing isolated APK SHA-256 is registered.',flush=True)
    except (RuntimeError,ValueError) as error:
        print('::warning::SHA-256 setup incomplete: '+str(error),flush=True); problems.append('sha256')
    del token
    if problems:
        print('::error::Test setup incomplete: '+', '.join(problems),file=sys.stderr,flush=True)
        return 1
    print('::notice::Test-only configuration readback completed; real device verification is still required.',flush=True)
    return 0

if __name__=='__main__':
    try: sys.exit(main())
    except subprocess.CalledProcessError:
        print('::error::Credential access failed; details suppressed.',file=sys.stderr,flush=True); sys.exit(1)
