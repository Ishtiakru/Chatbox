// Local, fake-media-only UI + controller harness. No production services.
import { initializeApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, signInAnonymously } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, doc, setDoc, updateDoc, serverTimestamp } from 'firebase/firestore';
import { createCallController } from '../src/call-controller.js';
import { createFirestoreCall, watchIncomingCalls } from '../src/firestore-call-transport.js';
import { experimentalCallPanel } from '../src/call-view.js';
const pause = ms => new Promise(resolve=>setTimeout(resolve,ms));
async function until(fn,label) { const deadline=Date.now()+20000; while(Date.now()<deadline){const v=fn();if(v)return v;await pause(30);} throw Error('Timed out: '+label); }
export async function run() {
  const cfg={projectId:'demo-chatbox',apiKey:'fake-key',authDomain:'demo-chatbox.firebaseapp.com'};
  const apps=[initializeApp(cfg,'call-ui-a'),initializeApp(cfg,'call-ui-b')];
  const auth=apps.map(app=>getAuth(app)), db=apps.map(app=>getFirestore(app));
  auth.forEach(a=>connectAuthEmulator(a,'http://127.0.0.1:9099',{disableWarnings:true}));
  db.forEach(d=>connectFirestoreEmulator(d,'127.0.0.1',8080));
  const users=await Promise.all(auth.map(a=>signInAnonymously(a).then(r=>r.user.uid)));
  await Promise.all(users.map((uid,i)=>setDoc(doc(db[i],'profiles',uid),{uid,displayName:i?'Receiver':'Caller'})));
  await setDoc(doc(db[0],'conversations','call-ui-test'),{participants:users,createdBy:users[0],status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
  await updateDoc(doc(db[1],'conversations','call-ui-test'),{status:'active',updatedAt:serverTimestamp()});
  const controllers=[], requests=[0,0], streams=[], histories=[[],[]], errors=[];
  for (let i=0;i<2;i++) {
    const root=document.createElement('section'); root.id='test-user-'+i; document.body.append(root);
    let controller;
    const render = state => {
      histories[i].push(state.status);
      if (state.error) errors.push(state.error);
      root.innerHTML=experimentalCallPanel(state);
      for (const [id,stream] of [['call-local',state.localStream],['call-remote',state.remoteStream]]) {
        const el=root.querySelector('#'+id); if(el && stream){el.srcObject=stream;el.play().catch(()=>{});}
      }
    };
    controller=createCallController({onChange:render,
      watchIncoming:(ctx,next,fail)=>watchIncomingCalls(db[i],ctx.id,ctx.uid,next,fail),
      createSession:opts=>createFirestoreCall({...opts,db:db[i],conversationId:opts.id,
        mediaDevices:{getUserMedia:async constraints=>{requests[i]++;const stream=await navigator.mediaDevices.getUserMedia(constraints);streams.push(stream);return stream;}},
        peerFactory:config=>new RTCPeerConnection(config),iceServers:[]})});
    root.addEventListener('click',event=>{const el=event.target.closest('button');if(!el)return;
      if(el.dataset.callOpen) controller.openIncoming(el.dataset.callOpen);
      else if(el.dataset.action==='call-video') controller.start('video').catch(e=>errors.push(e.message));
      else if(el.dataset.action==='call-accept') controller.accept().catch(e=>errors.push(e.message));
      else if(el.dataset.action==='call-mic-toggle' || el.dataset.action==='call-camera-toggle') {
        const kind=el.dataset.action==='call-mic-toggle'?'audio':'video';
        const media=controller.getState().localMedia[kind];
        if(media.available) controller.setLocalEnabled(kind,!media.enabled);
      }
      else if(el.dataset.action==='call-hangup') controller.hangUp();
    });
    controller.setConversation({id:'call-ui-test',uid:users[i],peerUid:users[1-i]});controllers.push(controller);
  }
  const [a,b]=controllers;
  try {
    document.querySelector('#test-user-0 [data-action="call-video"]').click();
    const open=await until(()=>document.querySelector('#test-user-1 [data-call-open]'),'incoming UI');
    if(requests[1]) throw Error('Receiver media opened before review');
    open.click(); await until(()=>document.querySelector('#test-user-1 [data-action="call-accept"]'),'consent UI');
    if(requests[1]) throw Error('Receiver media opened before acceptance');
    document.querySelector('#test-user-1 [data-action="call-accept"]').click();
    await until(()=>a.getState().status==='connected' && b.getState().status==='connected' && a.getState().remoteStream && b.getState().remoteStream,'connected remote media');
    const local=streams[0];
    const mic=local.getAudioTracks()[0], camera=local.getVideoTracks()[0];
    if (!mic || !camera) throw Error('Missing local fake media tracks');
    document.querySelector('#test-user-0 [data-action="call-mic-toggle"]').click();
    document.querySelector('#test-user-0 [data-action="call-camera-toggle"]').click();
    if(mic.enabled || camera.enabled || mic.readyState!=='live' || camera.readyState!=='live') throw Error('Mute controls did not disable local tracks');
    if (!document.querySelector('#test-user-0 [data-action="call-mic-toggle"]').textContent.includes('Unmute')) throw Error('Mute UI not updated');
    document.querySelector('#test-user-0 [data-action="call-mic-toggle"]').click();
    document.querySelector('#test-user-0 [data-action="call-camera-toggle"]').click();
    if(!mic.enabled || !camera.enabled) throw Error('Unmute controls did not enable local tracks');
    document.querySelector('#test-user-1 [data-action="call-hangup"]').click();
    await until(()=>a.getState().status==='idle' && streams.flatMap(s=>s.getTracks()).every(t=>t.readyState==='ended'),'hangup cleanup');
    return {connected:true,consentBeforeMedia:true,localControls:true, requests,trackCount:streams.flatMap(s=>s.getTracks()).length,tracksStopped:true,histories,errors};
  } finally {controllers.forEach(c=>c.dispose());document.querySelectorAll('#test-user-0,#test-user-1').forEach(el=>el.remove());}
}
