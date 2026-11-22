import test from 'node:test';
import assert from 'node:assert/strict';
import { createCallController } from '../src/call-controller.js';

function fixture() {
  const listeners = [], sessions = [], changes = [];
  const controller = createCallController({
    onChange: state => changes.push(state),
    watchIncoming: (ctx, next, fail) => { const item = {ctx,next,fail,stopped:false}; listeners.push(item); return () => { item.stopped = true; }; },
    createSession: options => {
      const session = { ...options, id:options.callId || 'outgoing', permissionRequests:0, closed:false,
        async start() { this.permissionRequests++; this.onState('calling'); },
        listen() {},
        async accept() { this.permissionRequests++; this.onState('connecting'); },
        async hangUp() { this.closed=true; this.onState('closed'); },
        close() { this.closed=true; },
      }; sessions.push(session); return session;
    },
  });
  return {controller,listeners,sessions,changes};
}
const conversation = {id:'chat',uid:'bob',peerUid:'alice'};
const offer = {id:'offer-1',callerId:'alice',receiverId:'bob',media:'video'};
test('receiving an offer never opens camera or microphone before explicit acceptance', async () => {
  const {controller,listeners,sessions} = fixture();
  controller.setConversation(conversation);
  listeners[0].next([offer, {...offer,id:'impostor',callerId:'eve'}, {...offer,id:'bad-media',media:'screen'}]);
  assert.deepEqual(controller.getState().incoming.map(x=>x.id), ['offer-1']);
  controller.openIncoming('offer-1');
  assert.equal(sessions[0].permissionRequests, 0);
  assert.equal(controller.getState().status,'loading');
  sessions[0].onIncoming();
  assert.equal(controller.getState().status,'incoming');
  await controller.accept();
  assert.equal(sessions[0].permissionRequests, 1);
  await assert.rejects(controller.accept(), /No call/);
  controller.hangUp();
  assert.equal(sessions[0].closed, true);
  assert.equal(controller.getState().status,'idle');
  controller.dispose();
});
test('changing accounts or conversations closes active media and ignores stale callbacks', async () => {
  const {controller,listeners,sessions} = fixture();
  controller.setConversation(conversation);
  await controller.start('audio');
  assert.equal(controller.getState().status,'calling');
  const old = sessions[0]; old.onLocalStream({id:'local'});
  controller.setConversation({id:'new-chat',uid:'bob',peerUid:'eve'});
  assert.equal(listeners[0].stopped,true);
  assert.equal(old.closed,true);
  assert.equal(controller.getState().localStream,null);
  listeners[0].next([offer]); old.onRemoteStream({id:'stale'}); old.onState('connected');
  assert.equal(controller.getState().status,'idle');
  assert.deepEqual(controller.getState().incoming,[]);
  assert.equal(controller.getState().remoteStream,null);
  controller.dispose(); assert.equal(listeners[1].stopped,true);
});
test('closed session errors are shown without exposing remote details', async () => {
  const {controller,sessions} = fixture();
  controller.setConversation(conversation);
  await controller.start('audio');
  sessions[0].onState('closed');
  sessions[0].onError(new Error('private server details'));
  assert.equal(controller.getState().status,'idle');
  assert.equal(controller.getState().error,'Call could not be completed.');
  sessions[0].onRemoteStream({id:'late'});
  assert.equal(controller.getState().remoteStream,null);
  controller.dispose();
});

test('dispose closes active media even when incoming unsubscribe throws', async () => {
  let closed=false;
  const controller=createCallController({
    watchIncoming:()=>()=>{throw new Error('unsubscribe failed');},
    createSession:()=>({id:'call',start:async()=>{},hangUp:()=>{closed=true;},close:()=>{closed=true;}}),
  });
  controller.setConversation(conversation);
  await controller.start('audio');
  assert.throws(()=>controller.dispose(),/unsubscribe failed/);
  assert.equal(closed,true);
  assert.doesNotThrow(()=>controller.dispose());
});

test('switching conversation closes active media even if unsubscribe fails', async () => {
  let closed=false;
  const controller=createCallController({
    watchIncoming:()=>()=>{throw new Error('unsubscribe failed');},
    createSession:()=>({id:'call',start:async()=>{},hangUp:()=>{closed=true;},close:()=>{closed=true;}}),
  });
  controller.setConversation(conversation);
  await controller.start('audio');
  assert.throws(()=>controller.setConversation({id:'new',uid:'bob',peerUid:'alice'}),/unsubscribe failed/);
  assert.equal(closed,true);
  assert.doesNotThrow(()=>controller.dispose());
});

test('terminal discovery error ends an active call and clears streams', async () => {
  const {controller,listeners,sessions}=fixture();
  controller.setConversation(conversation);
  await controller.start('video');
  sessions[0].onLocalStream({id:'local'});
  listeners[0].fail(new Error('private error'));
  assert.equal(sessions[0].closed,true);
  assert.equal(controller.getState().status,'unavailable');
  assert.equal(controller.getState().localStream,null);
  assert.deepEqual(controller.getState().incoming,[]);
  assert.match(controller.getState().error,/Unable to check incoming calls/);
  await assert.rejects(controller.start('audio'), /Cannot start/);
  controller.hangUp();
  assert.equal(controller.getState().status,'unavailable');
  sessions[0].onRemoteStream({id:'stale'});
  assert.equal(controller.getState().remoteStream,null);
  controller.dispose();
});

function fakeClock() {
  let now=0, id=0;
  const events=new Map(), scheduled=[];
  return {
    scheduled,
    setTimer(fn,delay) {const timer=++id; events.set(timer,{at:now+delay,fn}); scheduled.push({id:timer,fn,delay}); return timer;},
    clearTimer(timer) {events.delete(timer);},
    advance(ms) {
      const target=now+ms;
      while (true) {
        const next=[...events].filter(([,event])=>event.at<=target).sort((a,b)=>a[1].at-b[1].at)[0];
        if (!next) break;
        now=next[1].at; events.delete(next[0]); next[1].fn();
      }
      now=target;
    },
    count:()=>events.size,
  };
}
function timedFixture() {
  const clock=fakeClock(), sessions=[],listeners=[];
  const controller=createCallController({
    setTimer:clock.setTimer,clearTimer:clock.clearTimer,
    watchIncoming:(ctx,next,fail)=>{listeners.push({next,fail}); return ()=>{};},
    createSession:options=>{
      const session={...options,id:options.callId || 'call',closed:false,
        start:async()=>{options.onState('calling');}, listen:()=>{}, accept:async()=>{options.onState('connecting');},
        hangUp:()=>{session.closed=true; options.onState('closed'); return new Promise(()=>{});},
        close:()=>{session.closed=true;},
      }; sessions.push(session);return session;
    },
  });
  controller.setConversation(conversation);
  return {controller,clock,sessions,listeners};
}
test('unanswered setup ends on deadline even if end signaling stalls', async () => {
  const {controller,clock,sessions}=timedFixture();
  await controller.start('video');
  sessions[0].onLocalStream({id:'camera'});
  clock.advance(59_999); assert.equal(sessions[0].closed,false);
  clock.advance(1);
  assert.equal(sessions[0].closed,true);
  assert.equal(controller.getState().localStream,null);
  assert.match(controller.getState().error,/did not connect in time/);
  sessions[0].onState('connected');
  assert.equal(controller.getState().status,'idle');
  controller.dispose();
});
test('receiver review also expires without requesting media', () => {
  const {controller,clock,sessions,listeners}=timedFixture();
  listeners[0].next([offer]); controller.openIncoming(offer.id);
  sessions[0].onIncoming();
  clock.advance(60_000);
  assert.equal(sessions[0].closed,true);
  assert.equal(controller.getState().status,'idle');
  assert.match(controller.getState().error,/did not connect/);
  controller.dispose();
});
test('connection cancels setup deadline; disconnection has its own grace period', async () => {
  const {controller,clock,sessions}=timedFixture();
  await controller.start('audio');
  const oldSetup=clock.scheduled[0];
  sessions[0].onState('connected');
  oldSetup.fn(); // a timer callback already queued cannot end a connected call
  clock.advance(300_000);
  assert.equal(sessions[0].closed,false);
  sessions[0].onState('disconnected');
  clock.advance(19_999); assert.equal(sessions[0].closed,false);
  sessions[0].onState('connected');
  clock.advance(30_000); assert.equal(sessions[0].closed,false);
  sessions[0].onState('disconnected');
  clock.advance(20_000);
  assert.equal(sessions[0].closed,true);
  assert.match(controller.getState().error,/connection was lost/);
  controller.dispose();
});
test('stale deadlines cannot end a later call or survive disposal', async () => {
  const {controller,clock,sessions}=timedFixture();
  await controller.start('audio');
  const old=clock.scheduled[0];
  controller.hangUp();
  await controller.start('video');
  old.fn(); assert.equal(sessions[1].closed,false);
  controller.dispose();
  assert.equal(sessions[1].closed,true);
  assert.equal(clock.count(),0);
});

test('permission granted after setup timeout is stopped without sending an offer', async () => {
  const {CallSession}=await import('../src/call-session.js');
  const clock=fakeClock(); let grant;
  const track={stopped:false,stop(){this.stopped=true;}};
  const signals=[];
  const controller=createCallController({
    setTimer:clock.setTimer,clearTimer:clock.clearTimer,
    watchIncoming:()=>()=>{},
    createSession:options=>Object.assign(new CallSession({...options,mediaDevices:{getUserMedia:()=>new Promise(r=>{grant=r;})},peerFactory:()=>{throw new Error('late peer');},send:async s=>{signals.push(s);}}),{id:'call'}),
  });
  controller.setConversation(conversation);
  const starting=controller.start('video');
  clock.advance(60_000);
  grant({getTracks:()=>[track]});
  await starting;
  assert.equal(track.stopped,true);
  assert.deepEqual(signals.map(s=>s.type),['end']);
  assert.match(controller.getState().error,/did not connect in time/);
  controller.dispose();
});

test('repeated disconnection reports do not postpone teardown', async () => {
  const {controller,clock,sessions}=timedFixture();
  await controller.start('audio');
  sessions[0].onState('connected');
  sessions[0].onState('disconnected');
  clock.advance(19_000);
  sessions[0].onState('disconnected');
  clock.advance(1_000);
  assert.equal(sessions[0].closed,true);
  controller.dispose();
});

function mediaTrack(kind) {
  const callbacks=new Set();
  return {kind,enabled:true,readyState:'live',
    addEventListener(type,fn){if(type==='ended') callbacks.add(fn);},
    removeEventListener(type,fn){if(type==='ended') callbacks.delete(fn);},
    end(){this.readyState='ended'; for(const fn of callbacks) fn();},
    get listeners(){return callbacks.size;},
  };
}
test('local media controls follow live tracks, external end, and session teardown', async () => {
  const {controller,sessions}=timedFixture();
  await controller.start('video');
  const audio=mediaTrack('audio'), video=mediaTrack('video');
  const stream={getTracks:()=>[audio,video]};
  sessions[0].onLocalStream(stream);
  assert.equal(controller.getState().localMedia.audio.enabled,true);
  controller.setLocalEnabled('audio',false); controller.setLocalEnabled('video',false);
  assert.equal(audio.enabled,false); assert.equal(video.enabled,false);
  assert.equal(controller.getState().localMedia.audio.enabled,false);
  assert.equal(controller.getState().localMedia.video.enabled,false);
  controller.setLocalEnabled('audio',true); assert.equal(audio.enabled,true);
  video.end();
  assert.equal(controller.getState().localMedia.video.available,false);
  controller.setLocalEnabled('video',true);
  assert.match(controller.getState().error,/Could not change/);
  assert.equal(video.enabled,false);
  const snapshot=controller.getState(); snapshot.localMedia.audio.enabled=false;
  assert.equal(controller.getState().localMedia.audio.enabled,true);
  controller.hangUp();
  assert.equal(audio.listeners,0); assert.equal(video.listeners,0);
  assert.equal(controller.getState().localMedia.audio.available,false);
  controller.setLocalEnabled('audio',false);
  assert.equal(audio.enabled,true); // no stale controller can alter an ended session
  controller.dispose();
});
test('late ended events and late streams cannot affect a different call', async () => {
  const {controller,sessions}=timedFixture();
  await controller.start('audio');
  const a=mediaTrack('audio'); sessions[0].onLocalStream({getTracks:()=>[a]});
  controller.hangUp();
  await controller.start('video');
  const b=mediaTrack('video'); sessions[1].onLocalStream({getTracks:()=>[b]});
  a.end(); sessions[0].onLocalStream({getTracks:()=>[a]});
  assert.equal(controller.getState().localMedia.video.available,true);
  assert.equal(controller.getState().localMedia.audio.available,false);
  controller.dispose(); assert.equal(b.listeners,0);
});

test('device permission and availability errors show safe guidance', async () => {
  for (const [name, expected] of [
    ['NotAllowedError', /permission was denied/],
    ['NotFoundError', /was not found/],
    ['NotReadableError', /unavailable/],
    ['OtherError', /Call could not be completed/],
  ]) {
    const error = Object.assign(new Error('private device details'), {name});
    const controller = createCallController({
      watchIncoming: () => () => {},
      createSession: () => ({id:'call', start:async () => { throw error; }, hangUp:async () => {}, close:() => {}}),
    });
    controller.setConversation(conversation);
    await controller.start('video');
    assert.match(controller.getState().error, expected);
    assert.doesNotMatch(controller.getState().error, /private device details/);
    controller.dispose();
  }
});

test('closed call cannot be revived by a late transport state, but its error is still reported', async () => {
  const {controller,sessions}=fixture();
  controller.setConversation(conversation);
  await controller.start('audio');
  const session=sessions[0];
  session.onState('closed');
  assert.equal(controller.getState().status,'idle');
  session.onState('connected');
  assert.equal(controller.getState().status,'idle');
  session.onState('disconnected');
  assert.equal(controller.getState().status,'idle');
  session.onError(new Error('private server details'));
  assert.equal(controller.getState().error,'Call could not be completed.');
  controller.dispose();
});

test('cancelling during initial preparing notification never starts the session', async () => {
  let controller, starts=0, ended=0;
  controller=createCallController({
    watchIncoming:()=>()=>{},
    createSession:()=>({id:'call',start:async()=>{starts++;},hangUp:()=>{ended++;},close:()=>{ended++;}}),
    onChange:state=>{if(state.status==='preparing') controller.hangUp();},
  });
  controller.setConversation(conversation);
  await controller.start('video');
  assert.equal(starts,0);
  assert.equal(ended,1);
  assert.equal(controller.getState().status,'idle');
  controller.dispose();
});

test('cancelling during receiver loading never subscribes to the call', () => {
  let controller, next, listens=0, ended=0;
  controller=createCallController({
    watchIncoming:(context,cb)=>{next=cb;return ()=>{};},
    createSession:()=>({id:'offer-1',listen:()=>{listens++;},hangUp:()=>{ended++;},close:()=>{ended++;}}),
    onChange:state=>{if(state.status==='loading') controller.hangUp();},
  });
  controller.setConversation(conversation);
  next([offer]);
  controller.openIncoming(offer.id);
  assert.equal(listens,0);
  assert.equal(ended,1);
  assert.equal(controller.getState().status,'idle');
  controller.dispose();
});

test('a synchronous session error during creation cannot be replaced with preparing', async () => {
  let starts=0, closed=0;
  const controller=createCallController({
    watchIncoming:()=>()=>{},
    createSession:options=>{
      options.onError(new Error('private details'));
      return {id:'call',start:async()=>{starts++;},close:()=>{closed++;},hangUp:()=>{closed++;}};
    },
  });
  controller.setConversation(conversation);
  await controller.start('audio');
  assert.equal(starts,0);
  assert.equal(closed,1);
  assert.equal(controller.getState().status,'idle');
  assert.equal(controller.getState().error,'Call could not be completed.');
  controller.dispose();
});

test('receiver cancellation during accept notification never opens media', async () => {
  let controller, next, session, accepts=0, ended=0;
  controller=createCallController({
    watchIncoming:(ctx,cb)=>{next=cb;return ()=>{};},
    createSession:options=>{
      session={id:'offer-1',...options,listen:()=>{},accept:async()=>{accepts++;},hangUp:()=>{ended++;},close:()=>{ended++;}};
      return session;
    },
    onChange:state=>{if(state.status==='preparing') controller.hangUp();},
  });
  controller.setConversation(conversation);
  next([offer]); controller.openIncoming(offer.id); session.onIncoming();
  await controller.accept();
  assert.equal(accepts,0);
  assert.equal(ended,1);
  assert.equal(controller.getState().status,'idle');
  controller.dispose();
});

test('synchronous closed callback before factory returns cannot revive the session', async () => {
  let started=0, closed=0;
  const controller=createCallController({
    watchIncoming:()=>()=>{},
    createSession:options=>{
      options.onState('closed');
      options.onState('connected');
      return {id:'call',start:async()=>{started++;},close:()=>{closed++;}};
    },
  });
  controller.setConversation(conversation);
  await controller.start('audio');
  assert.equal(started,0);
  assert.equal(closed,1);
  assert.equal(controller.getState().status,'idle');
  controller.dispose();
});
