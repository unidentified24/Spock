const {readFileSync}=require('node:fs');
const {join}=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const source=readFileSync(join(__dirname,'../app/src/main/assets/spock_web_playback.js'),'utf8');
function browser(origin='https://open.spotify.com') {
  const requests=[],timers=new Map();let serial=0;
  class Media {
    constructor(){this.paused=true;this.ended=false;this.muted=false;this.volume=1;this.duration=180;this.currentTime=0;this.readyState=4;this.listeners={};}
    play(){this.paused=false;return Promise.resolve();}
    pause(){this.paused=true;}
    addEventListener(name,fn){this.listeners[name]=fn;}
  }
  const media=new Media();
  const transport={getAttribute:()=>media.paused?'Play':'Pause',click:()=>{if(media.paused)media.play();else media.pause();}};
  const context={URL,Headers,DOMException,HTMLMediaElement:Media,HTMLInputElement:class{},Event:class{},
    navigator:{platform:'Android',maxTouchPoints:5},
    location:{origin,pathname:'/track/one',href:origin+'/track/one',assign(url){this.href=url;}},
    fetch:async(input,init)=>{requests.push({input,init});return{ok:true};},
    document:{dispatchEvent:()=>{},querySelector:s=>s.includes('now-playing-widget')?{href:'https://open.spotify.com/track/one'}:s.includes('control-button-playpause')?transport:s.includes('play-button')?{getAttribute:()=> 'Play',click:()=>media.play()}:null,querySelectorAll:()=>[media]},
    setTimeout:fn=>{const n=++serial;timers.set(n,fn);return n;},clearTimeout:n=>timers.delete(n)
  };
  context.window=context;context.top=context;
  vm.runInNewContext(source,context);
  return{context,media,requests,timers,bridge:context.__spockPlayback};
}
const settle=()=>new Promise(r=>setImmediate(r));
(async()=>{
  let tests=0;
  const other=browser('https://example.test');assert.equal(other.bridge,undefined);tests++;
  const b=browser();assert.equal(b.context.navigator.platform,'Win32');assert.equal(b.context.navigator.maxTouchPoints,0);tests++;
  await assert.rejects(b.media.play(),{name:'AbortError'});assert.equal(b.media.paused,true);tests++;
  await b.context.fetch('https://gew4-spclient.spotify.com/track-playback/v1/devices',{
    method:'POST',headers:{Authorization:'Bearer fixture','Client-Token':'fixture'},body:JSON.stringify({device:{device_id:'fixture-device',name:'Web Player',device_info:{name:'Web Player'}}})
  });
  const body=JSON.parse(b.requests.at(-1).init.body);assert.equal(body.device.name,'Spock');assert.equal(body.device.device_info.name,'Spock');tests++;
  await b.context.fetch('https://example.test/track-playback/v1/devices',{body:'untouched'});assert.equal(b.requests.at(-1).init.body,'untouched');tests++;
  b.bridge.start('spotify:track:one',10);await settle();await b.media.play();b.media.currentTime=1;
  assert.equal(b.bridge.snapshot().uri,'spotify:track:one');assert.equal(b.bridge.snapshot().playing,true);tests++;
  b.bridge.stop();assert.equal(b.media.paused,true);await assert.rejects(b.media.play(),{name:'AbortError'});assert.equal(b.timers.size,0);tests++;
  b.bridge.start('spotify:track:one',11);b.bridge.stop();await settle();assert.equal(b.media.paused,true);assert.equal(b.timers.size,0);tests++;
  b.bridge.resume(20);await settle();assert.equal(b.bridge.snapshot().token,20);assert.equal(b.media.paused,false);tests++;
  b.media.listeners.ended();assert.equal(b.bridge.snapshot().ended,true);await assert.rejects(b.media.play(),{name:'AbortError'});tests++;
  const canvas=browser();canvas.bridge.start('spotify:track:one',30);await settle();canvas.media.muted=true;await canvas.media.play();canvas.media.listeners.ended();assert.equal(canvas.bridge.snapshot().ended,false);tests++;
  console.log(`Web playback bridge: ${tests} checks passed`);
})().catch(error=>{console.error(error);process.exitCode=1;});
