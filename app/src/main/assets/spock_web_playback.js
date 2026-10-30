/* Spock's playback-only bridge. No wrapper interface, login UI or ad filters. */
(() => {
  'use strict';
  if (location.origin !== 'https://open.spotify.com' || window !== window.top || window.__spockPlayback) return;
  const state = {allowed:false, uri:'', token:0, ended:false, device:'', auth:'', client:'', base:'', timers:[], media:new Set()};
  // The foreground audio service keeps this playback session active while the app is in the background.
  try {
    const visibility=Object.getOwnPropertyDescriptor(Document.prototype,'visibilityState');
    const hidden=Object.getOwnPropertyDescriptor(Document.prototype,'hidden');
    if(visibility?.get)Object.defineProperty(document,'visibilityState',{configurable:true,get:()=>state.allowed?'visible':visibility.get.call(document)});
    if(hidden?.get)Object.defineProperty(document,'hidden',{configurable:true,get:()=>state.allowed?false:hidden.get.call(document)});
  }catch(_){}
  const rawFetch=window.fetch.bind(window);
  const originalPlay=HTMLMediaElement.prototype.play;
  function pauseMedia() {
    state.media.forEach(m => {try{m.pause();}catch(_){}});
    document.querySelectorAll('audio,video').forEach(m => {try{m.pause();}catch(_){}});
  }
  HTMLMediaElement.prototype.play=function(...args) {
    if(!this.muted && !this.loop)state.media.add(this);
    if (!state.allowed) return Promise.reject(new DOMException('Spock player is inactive','AbortError'));
    if (!this.__spockEndHook) {
      this.__spockEndHook=true;
      this.addEventListener('ended',()=>{if(this.muted||this.loop)return;state.ended=true;state.allowed=false;pauseMedia();});
    }
    return originalPlay.apply(this,args);
  };
  const define=(name,value)=>{try{Object.defineProperty(navigator,name,{configurable:true,get:()=>value});}catch(_){}};
  define('platform','Win32');define('maxTouchPoints',0);
  // Keep browser identity and client hints consistent with the desktop User-Agent.
  if(navigator.userAgentData) {
    const original=navigator.userAgentData;
    const brands=original.brands;
    define('userAgentData',{
      brands,mobile:false,platform:'Windows',
      getHighEntropyValues:async hints=>Object.assign(await original.getHighEntropyValues(hints),{mobile:false,platform:'Windows',platformVersion:'10.0.0',architecture:'x86',bitness:'64',model:''}),
      toJSON:()=>({brands,mobile:false,platform:'Windows'})
    });
  }
  // Observe only the page's existing authenticated session. Tokens never cross the native bridge.
  window.fetch=function(input,init) {
    const url=typeof input==='string'?input:input.url;
    let options=init;
    try {
      const u=new URL(url,location.href);
      const trusted=u.protocol==='https:' && (u.hostname==='spotify.com'||u.hostname.endsWith('.spotify.com'));
      if(trusted) {
        const headers=new Headers((init&&init.headers)||(input&&input.headers)||{});
        const auth=headers.get('authorization'),client=headers.get('client-token');
        if(auth&&auth.startsWith('Bearer '))state.auth=auth;
        if(client)state.client=client;
        if(u.hostname.includes('spclient'))state.base=u.origin;
        if(init&&typeof init.body==='string' && /\/(track-playback|connect-state)\/v1\/devices/.test(u.pathname)) {
          const body=JSON.parse(init.body);
          const device=body.device;
          if(device){
            if(device.device_id)state.device=device.device_id;
            if('name' in device)device.name='Spock';
            if(device.device_info){
              if('name' in device.device_info)device.device_info.name='Spock';
              if('device_name' in device.device_info)device.device_info.device_name='Spock';
            }
            options=Object.assign({},init,{body:JSON.stringify(body)});
          }
        }
      }
    }catch(_){}
    return rawFetch(input,options);
  };
  const cancelTimers=()=>{state.timers.forEach(clearTimeout);state.timers=[];};
  const button=()=>document.querySelector('[data-testid="control-button-playpause"]');
  function clickPlay(token) {
    if(!state.allowed||token!==state.token)return;
    const transport=button();
    if(transport && /pause/i.test(transport.getAttribute('aria-label')||''))return;
    const b=document.querySelector('[data-testid="play-button"]');
    if(b && !/pause/i.test(b.getAttribute('aria-label')||''))b.click();
  }
  function stop(){state.allowed=false;state.token++;cancelTimers();const b=button();if(b&&/pause/i.test(b.getAttribute('aria-label')||''))b.click();pauseMedia();return true;}
  async function command(uri,token) {
    if(!state.device||!state.auth||!state.base)return false;
    try {
      const response=await rawFetch(state.base+'/connect-state/v1/player/command/from/'+encodeURIComponent(state.device)+'/to/'+encodeURIComponent(state.device),{
        method:'POST',headers:{Authorization:state.auth,'Client-Token':state.client,'Content-Type':'application/json'},
        body:JSON.stringify({command:{context:{uri,url:'context://'+uri,metadata:{}},play_origin:{feature_identifier:'track',feature_version:'spock',referrer_identifier:'search'},options:{skip_to:{},player_options_override:{}},endpoint:'play'}})
      });
      if(!state.allowed||state.token!==token)return false;
      return response.ok;
    }catch(_){return false;}
  }
  function start(uri,token) {
    stop();state.uri=uri;state.token=token;state.allowed=true;state.ended=false;
    document.dispatchEvent(new Event('visibilitychange'));
    command(uri,token).then(ok=>{
      if(!state.allowed||state.token!==token)return;
      if(!ok){
        const path='/'+uri.split(':')[1]+'/'+uri.split(':')[2];
        if(location.pathname!==path){location.assign('https://open.spotify.com'+path);return;}
        clickPlay(token);
      }
      [500,1200,2400,4000,6500,9500].forEach(ms=>state.timers.push(setTimeout(()=>clickPlay(token),ms)));
    });
    return true;
  }
  function time(text){return(text||'').trim().split(':').reduce((a,n)=>a*60+(Number(n)||0),0)*1000;}
  function snapshot() {
    const link=document.querySelector('[data-testid="now-playing-widget"] a[href*="/track/"], [data-testid="now-playing-widget"] a[href*="/episode/"]');
    const match=link&&new URL(link.href,location.href).pathname.match(/^\/(track|episode)\/([a-zA-Z0-9]+)/);
    const uri=match?'spotify:'+match[1]+':'+match[2]:'';
    const valid=[...state.media].filter(m=>Number.isFinite(m.duration)&&m.duration>0&&!m.muted);
    const media=valid.find(m=>!m.paused&&!m.ended)||valid[valid.length-1];
    const b=button();
    const playing=state.allowed && (media?!media.paused&&!media.ended&&media.readyState>=2:!!(b&&/pause/i.test(b.getAttribute('aria-label')||'')));
    return {token:state.token,uri,position:media?media.currentTime*1000:time(document.querySelector('[data-testid="playback-position"]')?.textContent),duration:media?media.duration*1000:time(document.querySelector('[data-testid="playback-duration"]')?.textContent),playing,buffering:!!(state.allowed&&media&&!media.paused&&media.readyState<2),ended:state.ended};
  }
  window.__spockPlayback={start,stop,snapshot,
    resume:token=>{cancelTimers();state.token=token;state.allowed=true;state.ended=false;const b=button();if(b&&!/pause/i.test(b.getAttribute('aria-label')||''))b.click();else clickPlay(token);},
    seek:ms=>{
      const media=[...state.media].find(m=>Number.isFinite(m.duration)&&m.duration>0);
      if(media){media.currentTime=Math.max(0,Math.min(ms/1000,media.duration));return;}
      const input=document.querySelector('input[type="range"][aria-label*="progress" i]');
      if(input){const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;const duration=snapshot().duration;setter.call(input,Math.max(0,Math.min(1,ms/duration))*Number(input.max||1));input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));}
    }
  };
})();
