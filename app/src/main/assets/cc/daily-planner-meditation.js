var _meditTimerSecs=600,_meditTimerTotal=600,_meditTimerInterval=null,_meditRunning=false;
var _meditAudioCtx=null,_meditAudioNodes={};

function meditGetCtx(){
  if(!_meditAudioCtx)_meditAudioCtx=new(window.AudioContext||window.webkitAudioContext)();
  if(_meditAudioCtx.state==='suspended')_meditAudioCtx.resume();
  return _meditAudioCtx;
}

/* --- Timer --- */
function meditTodayKey(){return dk(new Date());}

function meditSetDuration(m){
  if(_meditRunning)return;
  _meditTimerSecs=_meditTimerTotal=m*60;
  document.querySelectorAll('.medit-preset').forEach(function(b){
    b.classList.toggle('medit-preset-active',b.textContent===m+'m');
  });
  meditUpdateDisplay();
}

function meditUpdateDisplay(){
  var m=Math.floor(_meditTimerSecs/60),s=_meditTimerSecs%60;
  document.getElementById('meditTimerDisplay').textContent=(m<10?'0':'')+m+':'+(s<10?'0':'')+s;
  var circ=352,pct=_meditTimerTotal>0?_meditTimerSecs/_meditTimerTotal:1;
  document.getElementById('meditTimerRing').setAttribute('stroke-dashoffset',circ*(1-pct));
}

function meditStartStop(){
  if(_meditRunning){
    clearInterval(_meditTimerInterval);_meditRunning=false;
    document.getElementById('meditStartBtn').innerHTML='&#x25B6; Resume';
    document.getElementById('meditTimerStatus').textContent='PAUSED';
    return;
  }
  if(_meditTimerSecs<=0)meditReset();
  _meditRunning=true;
  document.getElementById('meditStartBtn').innerHTML='&#x23F8; Pause';
  document.getElementById('meditTimerStatus').textContent='MEDITATING';
  _meditTimerInterval=setInterval(function(){
    _meditTimerSecs--;
    meditUpdateDisplay();
    if(_meditTimerSecs<=0){
      clearInterval(_meditTimerInterval);_meditRunning=false;
      document.getElementById('meditStartBtn').innerHTML='&#x25B6; Start';
      document.getElementById('meditTimerStatus').textContent='COMPLETE ✓';
      meditPlayBell();setTimeout(meditPlayBell,1500);
      meditSaveSession(true);
      // Finishing a full guided session also marks Meditation done on My
      // Schedule, mirroring the Dog Walk -> Walk Buddy bridge, so completing
      // the timer here shows up there without a separate manual tap.
      document.querySelectorAll('.tri-check[data-key="spMeditation"]').forEach(function(el){applyTriState(el,'green');});
      save();
      if(typeof renderJournalSchedule==='function')renderJournalSchedule();
    }
  },1000);
}

function meditReset(){
  clearInterval(_meditTimerInterval);_meditRunning=false;
  _meditTimerSecs=_meditTimerTotal;
  document.getElementById('meditStartBtn').innerHTML='&#x25B6; Start';
  document.getElementById('meditTimerStatus').textContent='READY';
  meditUpdateDisplay();
}

/* --- Bell Sound --- */
function meditPlayBell(){
  var ctx=meditGetCtx(),t=ctx.currentTime;
  [1,2,3].forEach(function(h){
    var o=ctx.createOscillator(),g=ctx.createGain();
    o.connect(g);g.connect(ctx.destination);
    o.type='sine';o.frequency.value=528*h;
    g.gain.setValueAtTime(0,t);
    g.gain.linearRampToValueAtTime(0.3/h,t+0.01);
    g.gain.exponentialRampToValueAtTime(0.001,t+3.5);
    o.start(t);o.stop(t+3.6);
  });
}

/* --- Ambient Sounds --- */
function _meditNoise(ctx,filterFreq,filterType,vol){
  var buf=ctx.createBuffer(2,ctx.sampleRate*3,ctx.sampleRate);
  for(var c=0;c<2;c++){var d=buf.getChannelData(c);for(var i=0;i<d.length;i++)d[i]=Math.random()*2-1;}
  var src=ctx.createBufferSource();src.buffer=buf;src.loop=true;
  var filt=ctx.createBiquadFilter();filt.type=filterType||'bandpass';filt.frequency.value=filterFreq||500;filt.Q.value=0.6;
  var gain=ctx.createGain();gain.gain.value=vol||0.12;
  src.connect(filt);filt.connect(gain);gain.connect(ctx.destination);
  src.start();return {src:src,gain:gain,stop:function(){try{src.stop();}catch(e){}}};
}

function _meditOcean(ctx){
  var nodes=[];
  var buf=ctx.createBuffer(2,ctx.sampleRate*4,ctx.sampleRate);
  for(var c=0;c<2;c++){var d=buf.getChannelData(c);for(var i=0;i<d.length;i++)d[i]=Math.random()*2-1;}
  var src=ctx.createBufferSource();src.buffer=buf;src.loop=true;
  var filt=ctx.createBiquadFilter();filt.type='lowpass';filt.frequency.value=700;
  var masterGain=ctx.createGain();masterGain.gain.value=0.1;
  src.connect(filt);filt.connect(masterGain);masterGain.connect(ctx.destination);
  src.start();nodes.push(src);
  var lfo=ctx.createOscillator();var lfoGain=ctx.createGain();
  lfo.frequency.value=0.11;lfoGain.gain.value=0.07;
  lfo.connect(lfoGain);lfoGain.connect(masterGain.gain);
  lfo.start();nodes.push(lfo);
  return {src:src,lfo:lfo,stop:function(){nodes.forEach(function(n){try{n.stop();}catch(e){}});}};
}

function _meditForest(ctx){
  var nodes=[];
  [800,1400,2200,4000].forEach(function(freq,i){
    var buf=ctx.createBuffer(1,ctx.sampleRate*2,ctx.sampleRate);
    var d=buf.getChannelData(0);for(var j=0;j<d.length;j++)d[j]=Math.random()*2-1;
    var src=ctx.createBufferSource();src.buffer=buf;src.loop=true;
    var filt=ctx.createBiquadFilter();filt.type='bandpass';filt.frequency.value=freq;filt.Q.value=1.5;
    var gain=ctx.createGain();gain.gain.value=0.04-i*0.005;
    src.connect(filt);filt.connect(gain);gain.connect(ctx.destination);src.start();nodes.push(src);
  });
  return {stop:function(){nodes.forEach(function(n){try{n.stop();}catch(e){}});}};
}

function _meditTone(ctx){
  var osc=ctx.createOscillator(),gain=ctx.createGain();
  osc.type='sine';osc.frequency.value=432;
  gain.gain.value=0.08;
  osc.connect(gain);gain.connect(ctx.destination);osc.start();
  return {src:osc,stop:function(){try{osc.stop();}catch(e){}}};
}


function _meditBinaural(ctx){
  var nodes=[];
  var oL=ctx.createOscillator(),oR=ctx.createOscillator();
  var pL=ctx.createStereoPanner(),pR=ctx.createStereoPanner();
  var g=ctx.createGain();g.gain.value=0.1;
  oL.frequency.value=200;oR.frequency.value=210;
  pL.pan.value=-1;pR.pan.value=1;
  oL.connect(pL);pL.connect(g);oR.connect(pR);pR.connect(g);g.connect(ctx.destination);
  oL.start();oR.start();nodes.push(oL,oR);
  return {stop:function(){nodes.forEach(function(n){try{n.stop();}catch(e){}});}};
}

function meditToggleSound(type){
  meditGetCtx();
  var btnId={rain:'sndRain',ocean:'sndOcean',forest:'sndForest',tone:'sndTone',binaural:'sndBinaural'}[type];
  var btn=document.getElementById(btnId);
  if(_meditAudioNodes[type]){
    _meditAudioNodes[type].stop();delete _meditAudioNodes[type];
    if(btn){btn.classList.remove('medit-sound-on');btn.style.filter='';}
    return;
  }
  var ctx=_meditAudioCtx;
  if(type==='rain')_meditAudioNodes[type]=_meditNoise(ctx,500,'bandpass',0.14);
  else if(type==='ocean')_meditAudioNodes[type]=_meditOcean(ctx);
  else if(type==='forest')_meditAudioNodes[type]=_meditForest(ctx);
  else if(type==='tone')_meditAudioNodes[type]=_meditTone(ctx);
  else if(type==='binaural')_meditAudioNodes[type]=_meditBinaural(ctx);
  if(btn){btn.classList.add('medit-sound-on');btn.style.filter='brightness(0.82)';}
}

/* --- Breathing Guide --- */
/* --- YouTube Embed --- */
function meditLoadYT(url){
  var yf=document.getElementById('meditYTFrame');if(!yf)return;
  if(!url||!url.trim()){return;}
  var id='';
  var m=url.match(/(?:youtu\.be\/|youtube\.com\/(?:watch\?v=|embed\/|v\/))([A-Za-z0-9_-]{11})/);
  if(m)id=m[1];
  if(!id){yf.innerHTML='<div style="color:#b45309;font-size:0.8rem;">Could not extract YouTube video ID.</div>';return;}
  yf.innerHTML='<iframe width="100%" height="220" src="https://www.youtube.com/embed/'+id+'?autoplay=1&rel=0" frameborder="0" allow="autoplay;encrypted-media" allowfullscreen style="border-radius:8px;display:block;"></iframe>';
  document.getElementById('meditYTUrl').value=url;
}

var _audiblePending={};
var _bibleSpeechChunks=[],_bibleSpeechIndex=0,_bibleSpeechPaused=false,_bibleSpeechToken=0,_bibleSpeechUtterance=null;
function bibleSpeechSupported(){return'speechSynthesis'in window&&'SpeechSynthesisUtterance'in window;}
function speechVoiceQualityScore(v){
  var name=(v.name||'').toLowerCase();
  var score=0;
  if(v.localService===false)score+=3;
  if(/neural|wavenet|studio|natural|premium|enhanced|plus/.test(name))score+=4;
  if(/online/.test(name))score+=1;
  if(/compact/.test(name))score-=3;
  if(v.default)score+=0.5;
  return score;
}
function speechPickBestVoice(voices,savedId){
  if(!voices||!voices.length)return null;
  if(savedId){var saved=voices.find(function(v){return(v.voiceURI||v.name)===savedId;});if(saved)return saved;}
  var english=voices.filter(function(v){return /^en/i.test(v.lang||'');});
  var pool=english.length?english:voices;
  var best=pool[0],bestScore=speechVoiceQualityScore(best);
  pool.forEach(function(v){var s=speechVoiceQualityScore(v);if(s>bestScore){best=v;bestScore=s;}});
  return best;
}
function bibleSpeechPopulateVoices(){
  var select=document.getElementById('bibleSpeechVoice');if(!select||!bibleSpeechSupported())return;
  var voices=window.speechSynthesis.getVoices().filter(function(v){return /^(en|es)(?:-|_|$)/i.test(v.lang||'');}),saved='';try{saved=localStorage.getItem('bible_speech_voice')||'';}catch(ignore){}
  voices.sort(function(a,b){var al=/^en/i.test(a.lang)?0:1,bl=/^en/i.test(b.lang)?0:1;return al-bl||(speechVoiceQualityScore(b)-speechVoiceQualityScore(a))||a.name.localeCompare(b.name);});
  select.innerHTML='';
  voices.forEach(function(v){var option=document.createElement('option');var star=speechVoiceQualityScore(v)>0?'\u2728 ':'';option.value=v.voiceURI||v.name;option.textContent=star+(/^es/i.test(v.lang)?'Spanish: ':'English: ')+v.name+' ('+v.lang+')';option.dataset.voiceName=v.name;select.appendChild(option);});
  var match=voices.find(function(v){return (v.voiceURI||v.name)===saved||v.name===saved;});
  if(match){select.value=match.voiceURI||match.name;}else if(voices.length){var preferred=speechPickBestVoice(voices.filter(function(v){return /^en/i.test(v.lang);}))||voices[0];select.value=preferred.voiceURI||preferred.name;}
  if(!voices.length){var unavailable=document.createElement('option');unavailable.value='';unavailable.textContent='No English or Spanish voices installed';select.appendChild(unavailable);}
}
function bibleSpeechSavePrefs(){try{localStorage.setItem('bible_speech_voice',(document.getElementById('bibleSpeechVoice')||{}).value||'');localStorage.setItem('bible_speech_rate',(document.getElementById('bibleSpeechRate')||{}).value||'1');}catch(ignore){}}
function bibleSpeechVoiceChanged(){bibleSpeechSavePrefs();if(bibleSpeechSupported()&&window.speechSynthesis.speaking)bibleSpeechRead();}
function bibleSpeechReady(){
  var controls=document.getElementById('bibleSpeechControls');if(!controls)return;controls.style.display='flex';
  if(!bibleSpeechSupported()){controls.innerHTML='<span style="font-size:.74rem;color:#b91c1c;">Text-to-speech is not available in this browser.</span>';return;}
  bibleSpeechPopulateVoices();var rate=document.getElementById('bibleSpeechRate'),saved='';try{saved=localStorage.getItem('bible_speech_rate')||'';}catch(ignore){}if(rate&&saved)rate.value=saved;
}
function bibleSpeechText(){
  var result=document.getElementById('audibleAIResult');if(!result)return'';
  var text=result.innerText
    .replace(/https?:\/\/\S+|www\.\S+/gi,' ')
    .replace(/\b(?:[123]\s+)?(?:Genesis|Exodus|Leviticus|Numbers|Deuteronomy|Joshua|Judges|Ruth|Samuel|Kings|Chronicles|Ezra|Nehemiah|Esther|Job|Psalms?|Proverbs|Ecclesiastes|Song\s+of\s+(?:Solomon|Songs)|Isaiah|Jeremiah|Lamentations|Ezekiel|Daniel|Hosea|Joel|Amos|Obadiah|Jonah|Micah|Nahum|Habakkuk|Zephaniah|Haggai|Zechariah|Malachi|Matthew|Mark|Luke|John|Acts|Romans|Corinthians|Galatians|Ephesians|Philippians|Colossians|Thessalonians|Timothy|Titus|Philemon|Hebrews|James|Peter|Jude|Revelation)\s+\d+(?:\s*[:.]\s*\d+)?(?:\s*[-–—]\s*\d+)?\b/gi,' ')
    .replace(/[.!?,:\-–—]+/g,'\n')
    .replace(/[\p{N}]/gu,' ')
    .replace(/[^\p{L}'\s\n]/gu,' ');
  return text.split(/\n+/).map(function(sentence){return sentence.replace(/\s+/g,' ').trim();}).filter(Boolean).join('\n');
}
function bibleSpeechMakeChunks(text){
  var sentences=text.split(/\n+/),chunks=[];
  sentences.forEach(function(sentence){
    sentence=sentence.trim();if(!sentence)return;
    if(sentence.length<=160){chunks.push(sentence);return;}
    var words=sentence.split(/\s+/),current='';
    words.forEach(function(word){if((current+' '+word).trim().length>160&&current){chunks.push(current);current=word;}else current+=(current?' ':'')+word;});if(current)chunks.push(current);
  });
  return chunks;
}
function bibleSpeechSetState(state){
  var read=document.getElementById('bibleSpeechReadBtn'),pause=document.getElementById('bibleSpeechPauseBtn'),stop=document.getElementById('bibleSpeechStopBtn'),status=document.getElementById('bibleSpeechStatus');
  if(read)read.textContent=state==='speaking'?'Restart':'Read Summary';if(pause){pause.disabled=state==='idle';pause.textContent=_bibleSpeechPaused?'Resume':'Pause';}if(stop)stop.disabled=state==='idle';if(status)status.textContent=state==='speaking'?(_bibleSpeechPaused?'Paused':'Reading summary...'):'Ready to read';
}
var _bibleSpeechKeepAlive=null;
function bibleSpeechPickVoice(voiceId){
  var voices=window.speechSynthesis.getVoices()||[];
  return speechPickBestVoice(voices,voiceId);
}
function bibleSpeechNoVoiceWarning(){
  var status=document.getElementById('bibleSpeechStatus');if(status)status.textContent='No voice responded — check device TTS settings';
  alert('No voice responded, even though the button worked. This usually means the phone itself has no working text-to-speech engine.\n\nOn Android: open Settings → System (or General management) → Languages & input → Text-to-speech output, confirm "Google Text-to-Speech" (or another engine) is selected, tap the gear/settings icon next to it, and install an English voice pack if none is listed. Then reopen this page and try Read Summary again.');
}
function bibleSpeechSpeakNext(token){
  if(token!==_bibleSpeechToken)return;if(_bibleSpeechIndex>=_bibleSpeechChunks.length){bibleSpeechStop(true);return;}
  var utterance=new SpeechSynthesisUtterance(_bibleSpeechChunks[_bibleSpeechIndex]),voiceId=(document.getElementById('bibleSpeechVoice')||{}).value||'',voice=bibleSpeechPickVoice(voiceId);_bibleSpeechUtterance=utterance;if(voice){utterance.voice=voice;utterance.lang=voice.lang;}else{utterance.lang='en-US';}utterance.rate=parseFloat((document.getElementById('bibleSpeechRate')||{}).value)||1;utterance.pitch=1;utterance.volume=1;
  var started=false;
  utterance.onstart=function(){started=true;};
  utterance.onend=function(){if(token!==_bibleSpeechToken)return;started=true;_bibleSpeechUtterance=null;_bibleSpeechIndex++;setTimeout(function(){bibleSpeechSpeakNext(token);},300);};
  utterance.onerror=function(e){started=true;_bibleSpeechUtterance=null;if(e.error==='canceled'||e.error==='interrupted')return;bibleSpeechStop(true);var status=document.getElementById('bibleSpeechStatus');if(status)status.textContent='Voice error: '+(e.error||'playback failed')+'. Try another voice.';};
  try{window.speechSynthesis.resume();window.speechSynthesis.speak(utterance);}catch(e){_bibleSpeechUtterance=null;bibleSpeechStop(true);var status=document.getElementById('bibleSpeechStatus');if(status)status.textContent='Voice playback is blocked in this browser.';}
  // Some Android/Chrome + WebView combos accept speak() but the device has no working
  // TTS engine installed — speechSynthesis then does nothing at all, with no event ever
  // firing. Detect that silent-failure case and surface it instead of staying quiet.
  setTimeout(function(){
    if(token!==_bibleSpeechToken)return;
    if(!started&&!window.speechSynthesis.speaking&&!window.speechSynthesis.pending){
      bibleSpeechStop(true);
      bibleSpeechNoVoiceWarning();
    }
  },2500);
}
function bibleSpeechRead(){
  if(!bibleSpeechSupported()){alert('Text-to-speech is not available in this browser.');return;}if(typeof _cardSpeech!=='undefined'&&_cardSpeech.activeBtn)cardSpeechStop();var text=bibleSpeechText();if(!text){alert('Create the Bible reading summary first.');return;}
  _bibleSpeechToken++;var token=_bibleSpeechToken;
  _bibleSpeechChunks=bibleSpeechMakeChunks(text);_bibleSpeechIndex=0;_bibleSpeechPaused=false;bibleSpeechSavePrefs();bibleSpeechSetState('speaking');
  if(_bibleSpeechKeepAlive){clearInterval(_bibleSpeechKeepAlive);_bibleSpeechKeepAlive=null;}
  // Only defer (and cancel) when something is already speaking/queued — calling speak()
  // synchronously in the click handler is required on iOS/Safari or it plays no audio at all.
  if(window.speechSynthesis.speaking||window.speechSynthesis.pending){
    window.speechSynthesis.cancel();
    setTimeout(function(){bibleSpeechSpeakNext(token);},80);
  }else{
    bibleSpeechSpeakNext(token);
  }
}
function bibleSpeechPauseResume(){if(!bibleSpeechSupported()||!window.speechSynthesis.speaking)return;if(_bibleSpeechPaused){window.speechSynthesis.resume();_bibleSpeechPaused=false;}else{window.speechSynthesis.pause();_bibleSpeechPaused=true;}bibleSpeechSetState('speaking');}
function bibleSpeechStop(finished){if(bibleSpeechSupported())window.speechSynthesis.cancel();if(_bibleSpeechKeepAlive){clearInterval(_bibleSpeechKeepAlive);_bibleSpeechKeepAlive=null;}_bibleSpeechUtterance=null;_bibleSpeechToken++;_bibleSpeechChunks=[];_bibleSpeechIndex=0;_bibleSpeechPaused=false;bibleSpeechSetState('idle');if(!finished){var status=document.getElementById('bibleSpeechStatus');if(status)status.textContent='Stopped';}}
if(bibleSpeechSupported()){window.speechSynthesis.onvoiceschanged=bibleSpeechPopulateVoices;}

function audibleSummarize(){
  var passages=(document.getElementById('audiblePassages')||{}).value||'';
  var day=(document.getElementById('audibleChapter')||{}).value||'';
  var book=(document.getElementById('audibleBook')||{}).value||'';
  var res=document.getElementById('audibleAIResult');
  if(!res)return;
  bibleSpeechStop(true);
  var speechControls=document.getElementById('bibleSpeechControls');if(speechControls)speechControls.style.display='none';
  if(!passages.trim()){
    res.style.display='block';
    res.style.background='#fee2e2';res.style.border='1px solid #fca5a5';res.style.color='#b91c1c';
    res.textContent='⚠️ Please fill in the Passages Listened To field before summarizing.';
    setTimeout(function(){res.style.background='';res.style.border='';res.style.color='';},4000);
    return;
  }
  res.style.background='';res.style.border='';res.style.color='';
  res.style.display='block';res.textContent='⏳ Summarizing your reading...';
  var label=(day?day+' — ':'')+passages.trim();
  var prompt='I am listening to "The One Year Chronological Bible" — New Living Translation (NLT) by Tyndale. '
    +'It reads the entire Bible in 365 daily readings arranged in the order events occurred historically.\n\n'
    +(book?'Book/Title: '+book.trim()+'\n':'')
    +'Today'+(day?' ('+day+')':'')+' I listened to: '+passages.trim()+'\n\n'
    +'Please provide:\n'
    +'1. A clear summary of what happens or is taught in each of these passages\n'
    +'2. The historical/chronological context — what era or period of biblical history this represents\n'
    +'3. The central theme or thread connecting all the readings\n'
    +'4. One or two standout NLT verses worth meditating on today\n'
    +'5. A short personal reflection or life application\n\n'
    +'Use the New Living Translation (NLT) wording when quoting verses. Keep it warm, clear, and spiritually encouraging. Under 380 words.';
  aiCall(prompt,4000,function(text){
    // Store pending data for archive save (avoids putting large text in onclick)
    _audiblePending={text:text,label:label,passages:passages,day:day,book:book};
    res.style.display='block';
    res.innerHTML='<div style="white-space:pre-wrap;line-height:1.6;color:#78350f;">'+escHtml(text)+'</div>';
    bibleSpeechReady();
    _aiLastShown['audibleAIResult']={text:text,lbl:'Bible Reading — '+label};
  },function(err){res.textContent='Error: '+err;});
}

var _bibleReadingArchiveLargeCache=null;
var _activitiesLargeCache=null;
function journalLargeStoreOpen(){
  if(window._journalLargeStorePromise)return window._journalLargeStorePromise;
  window._journalLargeStorePromise=new Promise(function(resolve,reject){
    var req=indexedDB.open('my_life_command_center_large_storage',1);
    req.onupgradeneeded=function(){var db=req.result;if(!db.objectStoreNames.contains('journal'))db.createObjectStore('journal');};
    req.onsuccess=function(){resolve(req.result);};req.onerror=function(){reject(req.error||new Error('Large journal storage could not be opened.'));};
  });
  return window._journalLargeStorePromise;
}
async function journalLargeStoreGet(key){
  var db=await journalLargeStoreOpen();
  return new Promise(function(resolve,reject){var tx=db.transaction('journal','readonly'),req=tx.objectStore('journal').get(key);req.onsuccess=function(){resolve(req.result);};req.onerror=function(){reject(req.error);};});
}
async function journalLargeStoreSet(key,value){
  var db=await journalLargeStoreOpen();
  return new Promise(function(resolve,reject){var tx=db.transaction('journal','readwrite');tx.objectStore('journal').put(value,key);tx.oncomplete=function(){resolve(true);};tx.onerror=function(){reject(tx.error);};});
}
async function journalAutomaticBackups(){
  try{var rows=await journalLargeStoreGet('automatic_daily_backups');return Array.isArray(rows)?rows:[];}catch(error){return[];}
}
async function journalCreateAutomaticBackup(force){
  try{
    var dateKey=dk(new Date()),backups=await journalAutomaticBackups();
    if(!force&&backups.some(function(item){return item.date===dateKey;}))return false;
    var payload=await journalCloudPayload();
    var plannerCount=Object.keys(payload).filter(function(key){return key.indexOf('planner_')===0;}).length;
    var snapshot={id:Date.now(),date:dateKey,createdAt:new Date().toISOString(),plannerCount:plannerCount,payload:payload};
    if(force)backups=backups.filter(function(item){return item.date!==dateKey;});
    backups.unshift(snapshot);backups=backups.sort(function(a,b){return b.id-a.id;}).slice(0,7);
    await journalLargeStoreSet('automatic_daily_backups',backups);
    localStorage.setItem('journal_last_automatic_backup',snapshot.createdAt);
    if(force){v26Toast('Recovery point created');renderAutomaticBackups();}
    return true;
  }catch(error){if(force)alert('The recovery point could not be created. Use Backup Now to save a JSON file instead.');return false;}
}
async function openAutomaticBackups(){
  var modal=document.getElementById('automaticBackupsModal');if(modal)modal.style.display='flex';
  await renderAutomaticBackups();
}
function closeAutomaticBackups(){var modal=document.getElementById('automaticBackupsModal');if(modal)modal.style.display='none';}
async function renderAutomaticBackups(){
  var host=document.getElementById('automaticBackupsList');if(!host)return;
  var backups=await journalAutomaticBackups();
  if(!backups.length){host.innerHTML='<div style="text-align:center;color:#667085;padding:28px;border:1px dashed #cbd5e1;border-radius:10px;">No automatic recovery points exist yet. One will be created today.</div>';return;}
  host.innerHTML=backups.map(function(item){var when=new Date(item.createdAt);return '<div style="display:flex;justify-content:space-between;align-items:center;gap:12px;padding:12px;border:1px solid #dbe3ee;border-radius:10px;margin-bottom:8px;background:#fff;"><div><strong style="display:block;color:#0a3161;">'+escHtml(when.toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric',year:'numeric'}))+'</strong><span style="font-size:.72rem;color:#667085;">'+escHtml(when.toLocaleTimeString('en-US',{hour:'numeric',minute:'2-digit'}))+' &middot; '+Number(item.plannerCount||0)+' daily entries</span></div><button type="button" class="btn-primary" onclick="journalRestoreAutomaticBackup('+Number(item.id)+')">Restore</button></div>';}).join('');
}
async function journalRestoreAutomaticBackup(id){
  var backups=await journalAutomaticBackups(),snapshot=backups.find(function(item){return Number(item.id)===Number(id);});if(!snapshot)return;
  if(!confirm('Restore the recovery point from '+new Date(snapshot.createdAt).toLocaleString()+'?\n\nMatching journal information will be restored. Current information not contained in that backup will be preserved.'))return;
  journalCloudApplying=true;
  var payload=snapshot.payload||{},bibleArchive=null,activities=null;
  Object.keys(payload).forEach(function(key){
    if(key==='bible_reading_archive'){try{bibleArchive=JSON.parse(payload[key]||'[]');}catch(ignore){}return;}
    if(key==='activities_data'){try{activities=JSON.parse(payload[key]||'[]');}catch(ignore2){}return;}
    if(journalCloudCanSyncKey(key))localStorage.setItem(key,String(payload[key]));
  });
  if(Array.isArray(bibleArchive)){_bibleReadingArchiveLargeCache=bibleArchive;await journalLargeStoreSet('bible_reading_archive',bibleArchive);}
  if(Array.isArray(activities)){_activitiesLargeCache=activities;await journalLargeStoreSet('activities_data',activities);}
  localStorage.setItem('journal_cloud_last_sync',new Date().toISOString());
  localStorage.setItem('journal_cloud_dirty','1');
  journalCloudApplying=false;location.reload();
}
async function hydrateActivitiesStorage(){
  var legacy=[];
  try{legacy=JSON.parse(localStorage.getItem('activities_data')||'[]')||[];}catch(e){}
  var stored=[];
  try{stored=await journalLargeStoreGet('activities_data')||[];}catch(e2){}
  var merged=[],seen={};
  stored.concat(legacy).forEach(function(activity){
    if(!activity)return;
    var id=String(activity.id||[activity.date||'',activity.title||'',activity.type||'',activity.distance||'',activity.duration||''].join('|'));
    if(seen[id])return;
    seen[id]=true;merged.push(activity);
  });
  // If the primary activity store is unexpectedly empty, recover non-destructively
  // from the retained daily recovery snapshots. This restores older runs without
  // replacing any activity already present on the device.
  if(!merged.length){
    try{
      var backups=await journalAutomaticBackups();
      backups.forEach(function(snapshot){
        var raw=snapshot&&snapshot.payload&&snapshot.payload.activities_data,rows=[];
        try{rows=typeof raw==='string'?JSON.parse(raw):(Array.isArray(raw)?raw:[]);}catch(ignoreBackup){}
        rows.forEach(function(activity){
          if(!activity)return;
          var id=String(activity.id||[activity.date||'',activity.title||'',activity.type||'',activity.distance||'',activity.duration||''].join('|'));
          if(seen[id])return;
          seen[id]=true;merged.push(activity);
        });
      });
    }catch(ignoreBackups){}
  }
  _activitiesLargeCache=merged;
  try{
    await journalLargeStoreSet('activities_data',merged);
    localStorage.removeItem('activities_data');
  }catch(ignore){}
  try{var weeklyPanel=document.getElementById('tab-weekly');if(weeklyPanel&&weeklyPanel.classList.contains('active')){renderWeekly();renderGoals();}}catch(ignoreWeekly){}
  // Habits reads activities via getActivities(), which falls back to an empty list until
  // this async IndexedDB hydration finishes. If the Habits screen already rendered before
  // this completed (e.g. the user switched tabs quickly after a reload), it would have
  // computed "no training evidence" against a temporarily-empty activities list. Re-run the
  // Habits sync now that the real data is in memory, so it self-corrects instead of staying
  // wrong until the next unrelated re-render.
  try{if(typeof h2Render==='function')h2Render();}catch(ignoreH2){}
  // My Schedule (renderJournalHome/renderJournalSchedule) also reads getActivities() directly
  // for things like "is there a run today" — same stale-empty-list race as Habits above, so
  // re-render it too once the real activities are in memory.
  try{if(typeof renderJournalHome==='function')renderJournalHome();}catch(ignoreHome){}
  return merged;
}
async function hydrateBibleReadingArchiveStorage(){
  var legacy=[];try{legacy=JSON.parse(localStorage.getItem('bible_reading_archive')||'[]')||[];}catch(e){}
  var stored=[];try{stored=await journalLargeStoreGet('bible_reading_archive')||[];}catch(e2){}
  var merged=[],seen={};
  stored.concat(legacy).forEach(function(entry){if(!entry||!entry.text)return;var id=entry.id||entry.ts||entry.text.slice(0,80);if(seen[id])return;seen[id]=true;merged.push(entry);});
  merged.sort(function(a,b){return String(b.ts||'').localeCompare(String(a.ts||''));});
  _bibleReadingArchiveLargeCache=merged.slice(0,365);
  try{await journalLargeStoreSet('bible_reading_archive',_bibleReadingArchiveLargeCache);localStorage.removeItem('bible_reading_archive');}catch(ignore){}
  if(document.getElementById('bibleArchiveList'))renderBibleArchive();
  return _bibleReadingArchiveLargeCache;
}
function readBibleReadingArchive(){
  var merged=[],seen={};
  function add(entry){
    if(!entry||!entry.text)return;
    var id=entry.id||entry.ts||[entry.journalDate||'',entry.day||'',entry.passages||'',entry.text.slice(0,80)].join('|');
    if(seen[id])return;
    entry.id=id;seen[id]=true;merged.push(entry);
  }
  (_bibleReadingArchiveLargeCache||[]).forEach(add);
  try{(JSON.parse(localStorage.getItem('bible_reading_archive')||'[]')||[]).forEach(add);}catch(e){}
  for(var i=0;i<localStorage.length;i++){
    var key=localStorage.key(i)||'';
    if(key.indexOf('planner_')!==0)continue;
    try{
      var dayData=JSON.parse(localStorage.getItem(key)||'{}');
      (dayData.bibleReadingArchiveEntries||[]).forEach(add);
    }catch(e){}
  }
  merged.sort(function(a,b){return String(b.ts||'').localeCompare(String(a.ts||''));});
  if(merged.length>365)merged.length=365;
  return merged;
}

async function writeBibleReadingArchive(arch){
  _bibleReadingArchiveLargeCache=Array.isArray(arch)?arch.slice(0,365):[];
  await journalLargeStoreSet('bible_reading_archive',_bibleReadingArchiveLargeCache);
  var dirtyMarked=false;
  try{localStorage.removeItem('bible_reading_archive');localStorage.setItem('journal_cloud_dirty','1');dirtyMarked=true;}catch(ignore){}
  if(journalCloudUser&&journalCloudReady){
    if(dirtyMarked)journalCloudSchedule();
    else setTimeout(function(){journalCloudUpload(false);},0);
  }
}

async function compactBibleReadingArchiveStorage(){
  var changed=false,arch=readBibleReadingArchive(),keys=[];
  for(var i=0;i<localStorage.length;i++){
    var key=localStorage.key(i)||'';
    if(key.indexOf('planner_')===0)keys.push(key);
  }
  keys.forEach(function(key){
    try{
      var dayData=JSON.parse(localStorage.getItem(key)||'{}');
      if(!Array.isArray(dayData.bibleReadingArchiveEntries))return;
      delete dayData.bibleReadingArchiveEntries;
      localStorage.setItem(key,JSON.stringify(dayData));
      changed=true;
    }catch(e){}
  });
  try{
    await writeBibleReadingArchive(arch);
    if(changed)localStorage.setItem('journal_cloud_dirty','1');
  }catch(e){}
  return changed;
}

async function saveBibleReadingToArchive(btn){
  var p=_audiblePending||{};
  var passages=((document.getElementById('audiblePassages')||{}).value||p.passages||'').trim();
  var day=((document.getElementById('audibleChapter')||{}).value||p.day||'').trim();
  var book=((document.getElementById('audibleBook')||{}).value||p.book||'').trim();
  var result=document.querySelector('#audibleAIResult > div');
  var summary=(p.text||(result&&result.textContent)||'').trim();
  if(!passages&&!summary){
    if(btn){var original=btn.textContent;btn.textContent='Add passages first';setTimeout(function(){btn.textContent=original;btn.disabled=false;},2000);}
    return;
  }
  var journalDate=dk(today),entry={
    id:'bible-'+journalDate+'-'+Date.now(),
    label:(day?day+' — ':'')+(passages||book||'Bible Reading'),
    text:summary||['Bible Reading',book&&'Book: '+book,day&&'Day: '+day,passages&&'Passages: '+passages].filter(Boolean).join('\n'),
    passages:passages,day:day,book:book,
    journalDate:journalDate,
    date:today.toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'}),
    ts:new Date().toISOString()
  };
  var arch=readBibleReadingArchive();
  var duplicate=arch.some(function(saved){return saved.journalDate===journalDate&&String(saved.passages||'').trim()===passages&&String(saved.book||'').trim()===book;});
  if(duplicate){
    if(btn){var duplicateLabel=btn.textContent;btn.textContent='✓ Already saved';setTimeout(function(){btn.textContent=duplicateLabel;btn.disabled=false;},2000);}
    return;
  }
  arch.unshift(entry);
  while(arch.length>365)arch.pop();
  try{
    await writeBibleReadingArchive(arch);
  }catch(e){
    if(btn){
      var failedLabel=btn.textContent;
      btn.textContent='Storage full — not saved';
      btn.disabled=false;
      setTimeout(function(){btn.textContent=failedLabel;},3000);
    }
    alert('The Bible reading could not be saved because this browser’s journal storage is full. Use Backup, then remove large stored uploads or transcripts and try again.');
    return;
  }
  if(btn){
    var savedLabel=btn.textContent,savedBackground=btn.style.background;
    btn.textContent='✓ Saved!';btn.disabled=true;btn.style.background='#27ae60';
    setTimeout(function(){btn.textContent=savedLabel;btn.disabled=false;btn.style.background=savedBackground;},1800);
  }
  var archivePanel=document.getElementById('bibleArchivePanel');
  var archiveArrow=document.getElementById('bibleArchiveArrow');
  if(archivePanel)archivePanel.style.display='block';
  if(archiveArrow)archiveArrow.innerHTML='&#x25BC;';
  renderBibleArchive();
}

function toggleBibleArchive(){
  var panel=document.getElementById('bibleArchivePanel');
  var arrow=document.getElementById('bibleArchiveArrow');
  if(!panel)return;
  var open=panel.style.display==='none';
  panel.style.display=open?'block':'none';
  if(arrow)arrow.innerHTML=open?'&#x25BC;':'&#x25B6;';
  if(open)renderBibleArchive();
}

function renderBibleArchive(){
  var arch=readBibleReadingArchive();
  var list=document.getElementById('bibleArchiveList');
  var empty=document.getElementById('bibleArchiveEmpty');
  if(!list)return;
  if(!arch.length){list.innerHTML='';if(empty)empty.style.display='block';return;}
  if(empty)empty.style.display='none';
  list.innerHTML=arch.map(function(a,i){
    return '<div style="border:1px solid #fde68a;border-radius:8px;padding:10px 12px;margin-bottom:8px;background:#fff;">'
      +'<div style="display:flex;align-items:flex-start;justify-content:space-between;gap:8px;margin-bottom:6px;">'
      +'<div>'
      +'<div style="font-size:0.82rem;font-weight:700;color:#92400e;">&#x1F4D6; '+(a.day||a.date||'')+(a.passages?' &mdash; <span style="font-weight:400;color:#b45309;">'+escHtml(a.passages)+'</span>':'')+'</div>'
      +(a.book?'<div style="font-size:0.72rem;color:#b45309;margin-top:1px;">'+escHtml(a.book)+'</div>':'')
      +'<div style="font-size:0.68rem;color:#d4a05a;margin-top:1px;">'+escHtml(a.date||'')+'</div>'
      +'</div>'
      +'<div style="display:flex;gap:5px;flex-shrink:0;">'
      +'<button onclick="toggleBibleArchiveItem(this,'+i+')" style="background:#fff3e0;color:#b45309;border:1px solid #fcd34d;border-radius:6px;padding:3px 9px;font-size:0.72rem;cursor:pointer;white-space:nowrap;">&#x25B6; Expand</button>'
      +'<button onclick="deleteBibleArchiveEntry('+i+')" style="background:none;border:none;color:#e74c3c;cursor:pointer;font-size:1rem;line-height:1;padding:0 3px;">&#x2715;</button>'
      +'</div>'
      +'</div>'
      +'<div id="bra_'+i+'" style="display:none;font-size:0.82rem;color:#78350f;white-space:pre-wrap;line-height:1.6;padding-top:6px;border-top:1px solid #fde68a;">'+escHtml(a.text||'')+'</div>'
      +'</div>';
  }).join('');
}

function toggleBibleArchiveItem(btn,idx){
  var el=document.getElementById('bra_'+idx);
  if(!el)return;
  var open=el.style.display==='none';
  el.style.display=open?'block':'none';
  btn.innerHTML=open?'&#x25BC; Collapse':'&#x25B6; Expand';
}

async function deleteBibleArchiveEntry(idx){
  if(!confirm('Delete this entry?'))return;
  var arch=readBibleReadingArchive(),removed=arch[idx];
  if(!removed)return;
  arch.splice(idx,1);
  await writeBibleReadingArchive(arch);
  renderBibleArchive();
}

/* --- AI Prayer Archive --- */
var _meditPending={};
function readMeditArchive(){
  try{
    var parsed=JSON.parse(localStorage.getItem('meditation_archive')||'[]');
    return Array.isArray(parsed)?parsed:[];
  }catch(e){return[];}
}
function writeMeditArchive(entries){
  var arr=(Array.isArray(entries)?entries:[]).slice();
  var trimmed=false;
  while(true){
    var value=JSON.stringify(arr);
    try{
      localStorage.setItem('meditation_archive',value);
      break;
    }catch(writeErr){
      var isQuota=writeErr&&(writeErr.name==='QuotaExceededError'||writeErr.code===22||writeErr.code===1014||/quota/i.test(writeErr.message||''));
      if(!isQuota||!arr.length)throw writeErr;
      arr.pop();
      trimmed=true;
    }
  }
  var check=readMeditArchive();
  if(!check.length&&entries&&entries.length)throw new Error('The prayer could not be verified in device storage.');
  writeMeditArchive._trimmed=trimmed;
  return check;
}
function saveToMeditArchive(btn){
  var p=_meditPending||{};
  var displayed='';
  if(btn&&btn.parentElement&&btn.parentElement.previousElementSibling)displayed=(btn.parentElement.previousElementSibling.textContent||'').trim();
  var prayer=String(p.text||displayed||'').trim();
  if(!prayer){if(btn){btn.textContent='Nothing to save';setTimeout(function(){btn.textContent='📁 Save to AI Prayer Archive';},2000);}return;}
  try{
    var arch=readMeditArchive(),stamp=new Date().toISOString();
    arch.unshift({label:p.label||'Guided Prayer',text:prayer,type:p.type||'prayer',date:p.date||new Date().toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'}),ts:stamp});
    while(arch.length>60)arch.pop();
    var saved=writeMeditArchive(arch);
    if(!saved.some(function(e){return e&&e.ts===stamp;}))throw new Error('The saved prayer could not be found after writing it.');
    var panel=document.getElementById('meditArchivePanel'),arrow=document.getElementById('meditArchiveArrow');
    if(panel)panel.style.display='block';
    if(arrow)arrow.innerHTML='&#x25BC;';
    renderMeditArchive();
    if(btn){btn.textContent='✓ Saved to AI Prayer Archive';btn.disabled=true;btn.style.background='#16a34a';}
    if(typeof v26Toast==='function')v26Toast(writeMeditArchive._trimmed?'Saved — removed some older entries to free up storage space':'Prayer saved to AI Prayer Archive');
  }catch(e){
    if(btn){btn.textContent='Save failed — try again';btn.disabled=false;btn.style.background='#b91c1c';}
    alert('The prayer could not be saved. Press Sync, then try again.\n\n'+(e&&e.message?e.message:''));
  }
}
function toggleMeditArchive(){
  var panel=document.getElementById('meditArchivePanel');
  var arrow=document.getElementById('meditArchiveArrow');
  if(!panel)return;
  var open=panel.style.display==='none';
  panel.style.display=open?'block':'none';
  if(arrow)arrow.innerHTML=open?'&#x25BC;':'&#x25B6;';
  if(open)renderMeditArchive();
}
function renderMeditArchive(){
  var arch=readMeditArchive();
  var list=document.getElementById('meditArchiveList');
  var empty=document.getElementById('meditArchiveEmpty');
  if(!list)return;
  if(!arch.length){list.innerHTML='';if(empty)empty.style.display='block';return;}
  if(empty)empty.style.display='none';
  var typeIcons={reflection:'🌿',prayer:'🙏',lectio:'📖',gratitude:'💛',body:'🧘',verse:'📜'};
  list.innerHTML=arch.map(function(e,i){
    return '<div style="border:1px solid #e9d5ff;border-radius:8px;margin-bottom:8px;overflow:hidden;">'
      +'<div style="display:flex;align-items:center;justify-content:space-between;background:#f3e8ff;padding:7px 10px;gap:6px;flex-wrap:wrap;">'
      +'<span style="font-weight:700;font-size:0.82rem;color:#6d28d9;">'+(typeIcons[e.type]||'✨')+' '+escHtml(e.label)+'</span>'
      +'<span style="font-size:0.75rem;color:#7c3aed;white-space:nowrap;">'+escHtml(e.date)+'</span>'
      +'<div style="display:flex;gap:5px;margin-left:auto;">'
      +'<button onclick="toggleMeditArchiveItem(this,'+i+')" style="background:#ede9fe;color:#7c3aed;border:1px solid #c4b5fd;border-radius:6px;padding:3px 9px;font-size:0.72rem;cursor:pointer;white-space:nowrap;">&#x25B6; Expand</button>'
      +'<button onclick="deleteMeditArchiveEntry('+i+')" style="background:#fee2e2;color:#b91c1c;border:1px solid #fca5a5;border-radius:6px;padding:3px 8px;font-size:0.72rem;cursor:pointer;">&#x1F5D1;</button>'
      +'</div></div>'
      +'<div id="meditArchItem_'+i+'" style="display:none;padding:10px 12px;font-size:0.82rem;color:#3b0764;white-space:pre-wrap;line-height:1.6;background:#faf5ff;">'+escHtml(e.text)+'</div>'
      +'</div>';
  }).join('');
}
function toggleMeditArchiveItem(btn,idx){
  var div=document.getElementById('meditArchItem_'+idx);
  if(!div)return;
  var open=div.style.display==='none';
  div.style.display=open?'block':'none';
  btn.innerHTML=open?'&#x25BC; Collapse':'&#x25B6; Expand';
}
function deleteMeditArchiveEntry(idx){
  if(!confirm('Delete this entry?'))return;
  var arch=readMeditArchive();
  arch.splice(idx,1);
  writeMeditArchive(arch);
  renderMeditArchive();
}

/* ===== One Year Chronological Bible NLT — 365-day reading schedule ===== */
var NYB_PLAN={
'1/1':'Genesis 1:1-3:24',
'1/2':'Genesis 4:1-5:32; 1 Chronicles 1:1-4; Genesis 6:1-22',
'1/3':'Genesis 7:1-10:5; 1 Chronicles 1:5-7; Genesis 10:6-20; 1 Chronicles 1:8-16; Genesis 10:21-30; 1 Chronicles 1:17-23; Genesis 10:31-32',
'1/4':'Genesis 11:1-26; 1 Chronicles 1:24-27; Genesis 11:27-14:24',
'1/5':'Genesis 15:1-17:27',
'1/6':'Genesis 18:1-21:7',
'1/7':'Genesis 21:8-23:20; Genesis 11:32; Genesis 24:1-67',
'1/8':'Genesis 25:1-4; 1 Chronicles 1:32-33; Genesis 25:5-6, 12-18; 1 Chronicles 1:28-31, 34; Genesis 25:19-26, 7-11',
'1/9':'Genesis 25:27-28:5',
'1/10':'Genesis 28:6-30:24',
'1/11':'Genesis 30:25-31:55',
'1/12':'Genesis 32:1-35:27',
'1/13':'Genesis 36:1-19; 1 Chronicles 1:35-37; Genesis 36:20-30; 1 Chronicles 1:38-42; Genesis 36:31-43; 1 Chronicles 1:43-2:2',
'1/14':'Genesis 37:1-38:30; 1 Chronicles 2:3-6, 8; Genesis 39:1-23',
'1/15':'Genesis 40:1-23; Genesis 35:28-29; Genesis 41:1-57',
'1/16':'Genesis 42:1-45:15',
'1/17':'Genesis 45:16-47:27',
'1/18':'Genesis 47:28-50:26',
'1/19':'Job 1:1-4:21',
'1/20':'Job 5:1-7:21',
'1/21':'Job 8:1-11:20',
'1/22':'Job 12:1-14:22',
'1/23':'Job 15:1-18:21',
'1/24':'Job 19:1-21:34',
'1/25':'Job 22:1-25:6',
'1/26':'Job 26:1-29:25',
'1/27':'Job 30:1-31:40',
'1/28':'Job 32:1-34:37',
'1/29':'Job 35:1-37:24',
'1/30':'Job 38:1-40:5',
'1/31':'Job 40:6-42:17',
'2/1':'Exodus 1:1-2:25; 1 Chronicles 6:1-3a; Exodus 3:1-4:17',
'2/2':'Exodus 4:18-7:13',
'2/3':'Exodus 7:14-9:35',
'2/4':'Exodus 10:1-12:51',
'2/5':'Exodus 13:1-15:27',
'2/6':'Exodus 16:1-19:25',
'2/7':'Exodus 20:1-22:15',
'2/8':'Exodus 22:16-24:18',
'2/9':'Exodus 25:1-28:43',
'2/10':'Exodus 29:1-31:18',
'2/11':'Exodus 32:1-34:35',
'2/12':'Exodus 35:1-36:38',
'2/13':'Exodus 37:1-39:31',
'2/14':'Exodus 39:32-40:38; Numbers 9:15-23',
'2/15':'Numbers 7:1-89',
'2/16':'Numbers 8:1-9:14; Leviticus 1:1-3:17',
'2/17':'Leviticus 4:1-6:30',
'2/18':'Leviticus 7:1-8:36',
'2/19':'Leviticus 9:1-11:47',
'2/20':'Leviticus 12:1-14:32',
'2/21':'Leviticus 14:33-16:34',
'2/22':'Leviticus 17:1-19:37',
'2/23':'Leviticus 20:1-22:33',
'2/24':'Leviticus 23:1-25:23',
'2/25':'Leviticus 25:24-26:46',
'2/26':'Leviticus 27:1-34; Numbers 1:1-54',
'2/27':'Numbers 2:1-3:51',
'2/28':'Numbers 4:1-5:31',
'3/1':'Numbers 6:1-27; Numbers 10:1-36',
'3/2':'Numbers 11:1-13:33',
'3/3':'Numbers 14:1-15:41',
'3/4':'Numbers 16:1-18:32 ',
'3/5':'Numbers 19:1-21:35 ',
'3/6':'Numbers 22:1-24:25',
'3/7':'Numbers 25:1-26:65',
'3/8':'Numbers 27:1-29:40',
'3/9':'Numbers 30:1-31:54',
'3/10':'Numbers 32:1-33:56',
'3/11':'Numbers 34:1-36:13',
'3/12':'Deuteronomy 1:1-3:20',
'3/13':'Deuteronomy 3:21-5:33',
'3/14':'Deuteronomy 6:1-9:29',
'3/15':'Deuteronomy 10:1-12:32',
'3/16':'Deuteronomy 13:1-16:17',
'3/17':'Deuteronomy 16:18-21:9',
'3/18':'Deuteronomy 21:10-25:19',
'3/19':'Deuteronomy 26:1-29:1',
'3/20':'Deuteronomy 29:2-31:29',
'3/21':'Deuteronomy 31:30-32:52; Psalm 90',
'3/22':'Deuteronomy 33:1-34:12; Joshua 1:1-2:24',
'3/23':'Joshua 3:1-6:27',
'3/24':'Joshua 7:1; 1 Chronicles 2:7; Joshua 7:2-9:27',
'3/25':'Joshua 10:1-12:6',
'3/26':'Joshua 12:7-15:19',
'3/27':'Joshua 15:20-17:18',
'3/28':'Joshua 18:1-19:48',
'3/29':'Joshua 19:49-21:45; 1 Chronicles 6:54-81',
'3/30':'Joshua 22:1-24:33',
'3/31':'Judges 1:1-3:30',
'4/1':'Judges 3:31-6:40',
'4/2':'Judges 7:1-9:21',
'4/3':'Judges 9:22-11:28',
'4/4':'Judges 11:29-15:20',
'4/5':'Judges 16:1-18:31',
'4/6':'Judges 19:1-21:25',
'4/7':'Ruth 1:1-4:12',
'4/8':'Ruth 4:13-22; 1 Chronicles 2:9-55; 1 Chronicles 4:1-23; 1 Samuel 1:1-8',
'4/9':'1 Samuel 1:9-4:11',
'4/10':'1 Samuel 4:12-8:22',
'4/11':'1 Samuel 9:1-12:25',
'4/12':'1 Chronicles 9:35-39; 1 Samuel 13:1-5, 19-23,; 6-18; 1 Samuel 14:1-52',
'4/13':'1 Samuel 15:1-17:31',
'4/14':'1 Samuel 17:32-19:17; Psalm 59; 1 Samuel 19:18-24',
'4/15':'1 Samuel 20:1-21:15; Psalm 34',
'4/16':'1 Samuel 22:1-2; Psalm 57; Psalm 142; 1 Chronicles 12:8-18; 1 Samuel 22:3-23; Psalm 52; 1 Samuel 23:1-12',
'4/17':'1 Samuel 23:13-29; Psalm 54; 1 Samuel 24:1-25:44',
'4/18':'1 Samuel 26:1-27:7; 1 Chronicles 12:1-7; 1 Samuel 27:8-29:11; 1 Chronicles 12:19; Psalm 56',
'4/19':'1 Samuel 30:1-31; 1 Chronicles 12:20-22; 1 Samuel 31:1-13; 1 Chronicles 10:1-14; 1 Chronicles 9:40-44; 2 Samuel 4:4; 2 Samuel 1:1-27',
'4/20':'2 Samuel 2:1-3:5; 1 Chronicles 3:1-4a; 2 Samuel 23:8-17; 1 Chronicles 11:10-19; 2 Samuel 23:18-39; 1 Chronicles 11:20-47',
'4/21':'2 Samuel 3:6-4:12',
'4/22':'2 Samuel 5:1-3; 1 Chronicles 11:1-3; 1 Chronicles 12:23-40; 2 Samuel 5:17-25; 1 Chronicles 14:8-17; 2 Samuel 5:6-10; 1 Chronicles 11:4-9; 1 Chronicles 3:4b; 2 Samuel 5:13, 4-5, 11-12; 1 Chronicles 14:1-2; 1 Chronicles 13:1-5; 2 Samuel 6:1-11; 1 Chronicles 13:6-14',
'4/23':'2 Samuel 6:12a; 1 Chronicles 15:1-28; 2 Samuel 6:12b-16; 1 Chronicles 15:29; 2 Samuel 6:17-19a; 1 Chronicles 16:1-43; 2 Samuel 6:19b-23',
'4/24':'2 Samuel 7:1-17; 1 Chronicles 17:1-15; 2 Samuel 7:18-29; 1 Chronicles 17:16-27; 2 Samuel 8:1-14; 1 Chronicles 18:1-13; Psalm 60',
'4/25':'2 Samuel 8:15-18; 1 Chronicles 18:14-17; 1 Chronicles 6:16-30,; 50-53, 31-48; 2 Samuel 9:1-10:19; 1 Chronicles 19:1-19',
'4/26':'1 Chronicles 20:1; 2 Samuel 11:1-12:14; Psalm 51; 2 Samuel 12:15-25; 2 Samuel 5:14-16; 1 Chronicles 14:3-7; 1 Chronicles 3:5-9',
'4/27':'2 2 Samuel 12:26-31; 1 Chronicles 20:2-3; 2 Samuel 13:1-14:33',
'4/28':'2 Samuel 15:1-17:14',
'4/29':'2 Samuel 17:15-29; Psalm 3; Psalm 63; 2 Samuel 18:1-19:30',
'4/30':'2 Samuel 19:31-20:26; Psalm 7; 2 Samuel 21:1-22; 1 Chronicles 20:4-8',
'5/1':'2 Samuel 22:1-51; Psalm 18',
'5/2':'2 Samuel 24:1-9; 1 Chronicles 21:1-6; 2 Samuel 24:10-17; 1 Chronicles 21:7-17; 2 Samuel 24:18-25; 1 Chronicles 21:18-22:19',
'5/3':'1 Chronicles 23:1-25:31',
'5/4':'1 Chronicles 26:1-28:21',
'5/5':'1 Chronicles 29:1-22; 1 Kings 1:1-53',
'5/6':'1 Kings 2:1-9; 2 Samuel 23:1-7; 1 Kings 2:10-12; 1 Chronicles 29:26-30; Psalms 4-6; Psalms 8-9, 11',
'5/7':'Psalms 12-17; Psalms 19-21',
'5/8':'Psalms 22-26',
'5/9':'Psalms 27-32',
'5/10':'Psalms 35-38',
'5/11':'Psalms 39-41; Psalms 53, 55, 58',
'5/12':'Psalms 61-62; Psalms 64-67',
'5/13':'Psalms 68-70; Psalm 86; Psalm 101',
'5/14':'Psalm 103; Psalms 108-110; Psalms 122, 124',
'5/15':'Psalms 131, 133; Psalms 138-141, 143',
'5/16':'Psalms 144-145; Psalms 88-89',
'5/17':'Psalm 50; Psalms 73-74 ',
'5/18':'Psalms 75-78',
'5/19':'Psalms 79-82 ',
'5/20':'Psalm 83 ; 1 Chronicles 29:23-25; 2 Chronicles 1:1 ; 1 Kings 2:13-3:4; 2 Chronicles 1:2-6; 1 Kings 3:5-15; 2 Chronicles 1:7-13',
'5/21':'1 Kings 3:16-28; 1 Kings 5:1-18; 2 Chronicles 2:1-18; 1 Kings 6:1-13; 2 Chronicles 3:1-14; 1 Kings 6:14-38',
'5/22':'1 Kings 7:1-51; 2 Chronicles 3:15-4:22 ',
'5/23':'1 Kings 8:1-11; 2 Chronicles 5:1-14; 1 Kings 8:12-21; 2 Chronicles 6:1-11; 1 Kings 8:22-53; 2 Chronicles 6:12-42',
'5/24':'1 Kings 8:54-66; 2 Chronicles 7:1-10; 1 Kings 9:1-9; 2 Chronicles 7:11-22; 1 Kings 9:10-14',
'5/25':'2 Chronicles 8:1-18; 1 Kings 9:15-10:13; 2 Chronicles 9:1-12; 1 Kings 10:14-29; 2 Chronicles 9:13-28; 2 Chronicles 1:14-17',
'5/26':'1 Kings 4:1-34; Psalm 72; Psalm 127',
'5/27':'Proverbs 1:1-4:27',
'5/28':'Proverbs 5:1-7:27',
'5/29':'Proverbs 8:1-10:32',
'5/30':'Proverbs 11:1-13:25',
'5/31':'Proverbs 14:1-16:33',
'6/1':'Proverbs 17:1-19:29',
'6/2':'Proverbs 20:1-22:16',
'6/3':'Proverbs 22:17-24:34',
'6/4':'Song of Songs 1:1-8:14',
'6/5':'1 Kings 11:1-43; 2 Chronicles 9:29-31; Ecclesiastes 1:1-11',
'6/6':'Ecclesiastes 1:12-6:12',
'6/7':'Ecclesiastes 7:1-11:6',
'6/8':'Ecclesiastes 11:7-12:14; 1 Kings 12:1-20; 2 Chronicles 10:1-19; 1 Kings 12:21-24; 2 Chronicles 11:1-4; 1 Kings 12:25-33; 2 Chronicles 11:5-17',
'6/9':'1 Kings 13:1-14:24; 2 Chronicles 12:13-14; 2 Chronicles 11:18-23; 2 Chronicles 12:1-12; 1 Kings 14:25-28; 2 Chronicles 12:15-16; 1 Kings 14:29-15:5; 2 Chronicles 13:1-22; 1 Kings 15:6-8; 2 Chronicles 14:1-8; 1 Kings 15:9-15; 1 Kings 14:19-20; 1 Kings 15:25-34; 2 Chronicles 14:9-15:19',
'6/10':'1 Kings 15:16-22; 2 Chronicles 16:1-10; 1 Kings 16:1-34; 1 Kings 15:23-24; 2 Chronicles 16:11-17:19; 1 Kings 17:1-7',
'6/11':'1 Kings 17:8-20:22',
'6/12':'1 Kings 20:23-22:9; 2 Chronicles 18:1-8',
'6/13':'1 Kings 22:10-28; 2 Chronicles 18:9-27; 1 Kings 22:29-35; 2 Chronicles 18:28-34; 1 Kings 22:36-40, 51-53; 2 Chronicles 19:1-20:30',
'6/14':'2 Kings 1:1-18; 2 Kings 3:1-27; 1 Kings 22:41-49; 2 Chronicles 20:31-37; 1 Kings 22:50; 2 Chronicles 21:1-4; 2 Kings 8:16-22; 2 Chronicles 21:5-7',
'6/15':'2 Kings 2:1-25; 2 Kings 4:1-44',
'6/16':'2 Kings 5:1-8:15',
'6/17':'2 Chronicles 21:8-20; 2 Kings 8:23-29; 2 Chronicles 22:1-7; 2 Kings 9:1-10:17; 2 Chronicles 22:8-9; 2 Kings 10:18-31',
'6/18':'2 Kings 11:1-3; 2 Chronicles 22:10-12; 2 Kings 11:4-12; 2 Chronicles 23:1-11; 2 Kings 11:13-16; 2 Chronicles 23:12-15; 9 2 Kings 11:17-21; 2 Chronicles 23:16-21; 2 Kings 12:1-16; 2 Chronicles 24:1-22; 2 Kings 10:32-36',
'6/19':'2 Kings 13:1-11; 2 Kings 12:17-21; 2 Chronicles 24:23-27; 2 Kings 13:14-25',
'6/20':'2 Kings 14:1-14; 2 Chronicles 25:1-24; 2 Kings 13:12-13; 2 Kings 14:15-16, 23-27; 2 Chronicles 25:25-28; 2 Kings 14:17-22; 2 Kings 15:1-5; 2 Chronicles 26:1-21; Jonah 1:1-4:11',
'6/21':'Amos 1:1-6:14',
'6/22':'Amos 7:1-9:15; 2 Kings 14:28-29; 2 Kings 15:8-29, 6-7; 2 Chronicles 26:22-23; Isaiah 6:1-13',
'6/23':'2 Kings 15:32-38; 2 Chronicles 27:1-9; Micah 1:1-16; 2 Kings 16:1-9; 2 Chronicles 28:1-15; Isaiah 7:1-25',
'6/24':'Isaiah 8:1-11:16',
'6/25':'Isaiah 12:1-6; Isaiah 17:1-14; 2 Chronicles 28:16-21; 2 Kings 16:10-18; 2 Chronicles 28:22-25; 2 Kings 18:1-8; 2 Chronicles 29:1-2; 2 Kings 15:30-31; 2 Kings 17:1-4; Hosea 1:1-2:13',
'6/26':'Hosea 2:14-8:14',
'6/27':'Hosea 9:1-14:9',
'6/28':'Isaiah 28:1-29; 2 Kings 17:5; 2 Kings 18:9-12; 2 Kings 17:6-41; Isaiah 1:1-20',
'6/29':'Isaiah 1:21-5:30',
'6/30':'2 Kings 16:19-20; 2 Chronicles 28:26-27; Isaiah 13:1-16:14',
'7/1':'2 Chronicles 29:3-31:21',
'7/2':'Proverbs 25:1-29:27',
'7/3':'Proverbs 30:1-31:31',
'7/4':'Psalms 42-46',
'7/5':'Psalms 47-49; Psalms 84-85, 87',
'7/6':'Psalms 1-2; Psalm 10; Psalm 33; Psalm 71; Psalm 91',
'7/7':'Psalms 92-97',
'7/8':'Psalms 98-100; Psalms 102, 104',
'7/9':'Psalms 105-106',
'7/10':'Psalm 107; Psalms 111-114',
'7/11':'Psalms 115-118',
'7/12':'Psalm 119',
'7/13':'Psalms 120-121, 123; Psalms 125-126',
'7/14':'Psalms 128-130, 132; Psalms 134-135',
'7/15':'Psalm 136; Psalms 146-150 ',
'7/16':'Isaiah 18:1-23:18',
'7/17':'Isaiah 24:1-27:13; Isaiah 29:1-24',
'7/18':'Isaiah 30:1-33:24',
'7/19':'Isaiah 34:1-35:10; Micah 2:1-5:15',
'7/20':'Micah 6:1-7:20; 2 Chronicles 32:1-8; 2 Kings 18:13-18; Isaiah 36:1-3 ; 2 Kings 18:19-37; Isaiah 36:4-22 ',
'7/21':'2 Kings 19:1-19; Isaiah 37:1-20; 2 Chronicles 32:9-19; 2 Kings 19:20-37; Isaiah 37:21-38; 2 Chronicles 32:20-23',
'7/22':'2 Kings 20:1-11; Isaiah 38:1-8; 2 Chronicles 32:24-31; Isaiah 38:9-22; 2 Kings 20:12-19; Isaiah 39:1-8',
'7/23':'Isaiah 40:1-44:5',
'7/24':'Isaiah 44:6-48:11',
'7/25':'Isaiah 48:12-52:12',
'7/26':'Isaiah 52:13-57:21',
'7/27':'Isaiah 58:1-63:14',
'7/28':'Isaiah 63:15-66:24; 2 Kings 20:20-21; 2 Chronicles 32:32-33',
'7/29':'2 Kings 21:1-9; 2 Chronicles 33:1-9; 2 Kings 21:10-17; 2 Chronicles 33:10-19; 2 Kings 21:18; 2 Chronicles 33:20; 2 Kings 21:19-26; 2 Chronicles 33:21-25; 2 Kings 22:1-2; 2 Chronicles 34:1-7; Jeremiah 1:1-2:22',
'7/30':'Jeremiah 2:23-5:19',
'7/31':'Jeremiah 5:20-6:30; 2 Kings 22:3-20; 2 Chronicles 34:8-28',
'8/1':'2 Kings 23:1-20; 2 Chronicles 34:29-33; 2 Kings 23:21-28; 2 Chronicles 35:1-19; Nahum 1:1-3:19',
'8/2':'Habakkuk 1:1-3:19; Zephaniah 1:1-2:7',
'8/3':'Zephaniah 2:8-3:20; 2 Chronicles 35:20-27; 2 Kings 23:29-30; Jeremiah 47:1-48:47',
'8/4':'2 Chronicles 36:1-4; 2 Kings 23:31-37; 2 Chronicles 36:5; Jeremiah 22:1-23; Jeremiah 26:1-24; 2 Kings 24:1-4; Jeremiah 25:1-14',
'8/5':'Jeremiah 25:15-38; Jeremiah 36:1-32; Jeremiah 45:1-46:28',
'8/6':'Jeremiah 19:1-20:18; Daniel 1:1-21',
'8/7':'Daniel 2:1-3:30; Jeremiah 7:1-8:3',
'8/8':'Jeremiah 8:4-11:23',
'8/9':'Jeremiah 12:1-15:21',
'8/10':'Jeremiah 16:1-18:23; Jeremiah 35:1-19',
'8/11':'Jeremiah 49:1-33; 2 Kings 24:5-7; 2 Chronicles 36:6-8; 2 Kings 24:8-9; 2 Chronicles 36:9; Jeremiah 22:24-23:32',
'8/12':'Jeremiah 23:33-24:10; Jeremiah 29:1-31:14',
'8/13':'Jeremiah 31:15-40; Jeremiah 49:34-51:14',
'8/14':'Jeremiah 51:15-58; 2 Kings 24:10-17; 2 Chronicles 36:10; 1 Chronicles 3:10-16; 2 Chronicles 36:11-14; Jeremiah 52:1-3a; 2 Kings 24:18-20a; Jeremiah 37:1-10',
'8/15':'Jeremiah 37:11-38:28; Ezekiel 1:1-3:15',
'8/16':'Ezekiel 3:16-4:17; Jeremiah 27:1-28:17; Jeremiah 51:59-64',
'8/17':'Ezekiel 5:1-9:11',
'8/18':'Ezekiel 10:1-13:23',
'8/19':'Ezekiel 14:1-16:63',
'8/20':'Ezekiel 17:1-19:14',
'8/21':'Ezekiel 20:1-22:16',
'8/22':'Ezekiel 22:17-23:49; 2 Kings 24:20b-25:2; Jeremiah 52:3b-5; Jeremiah 39:1; Ezekiel 24:1-14',
'8/23':'Ezekiel 24:15-25:17; Jeremiah 34:1-22; Jeremiah 21:1-14; Ezekiel 29:1-16; Ezekiel 30:20-31:18',
'8/24':'Jeremiah 32:1-33:26; Ezekiel 26:1-14',
'8/25':'Ezekiel 26:15-28:26; 2 Kings 25:3-7; Jeremiah 52:6-11; Jeremiah 39:2-10',
'8/26':'Jeremiah 39:11-18; Jeremiah 40:1-6; 2 Kings 25:8-21; Jeremiah 52:12-27; 2 Chronicles 36:15-21; Lamentations 1:1-22',
'8/27':'Lamentations 2:1-4:22',
'8/28':'Lamentations 5:1-22; Obadiah 1:1-21; 2 Kings 25:22-26; Jeremiah 40:7-41:18',
'8/29':'Jeremiah 42:1-44:30; Ezekiel 33:21-33',
'8/30':'Ezekiel 34:1-36:38',
'8/31':'Ezekiel 37:1-39:29; Ezekiel 32:1-16',
'9/1':'Ezekiel 32:17-33:20; Jeremiah 52:28-30; Psalm 137; 1 Chronicles 4:24-5:17',
'9/2':'1 Chronicles 5:18-26; 1 Chronicles; 6:3b, 49, 4-15; 1 Chronicles 7:1-8:28',
'9/3':'1 Chronicles 8:29-9:1a; Daniel 4:1-37; Ezekiel 40:1-37',
'9/4':'Ezekiel 40:38-43:27',
'9/5':'Ezekiel 44:1-46:24',
'9/6':'Ezekiel 47:1-48:35; Ezekiel 29:17-30:19; 2 Kings 25:27-30; Jeremiah 52:31-34',
'9/7':'Daniel 7:1-8:27; Daniel 5:1-31',
'9/8':'Daniel 6:1-28; Daniel 9:1-27; 2 Chronicles 36:22-23; Ezra 1:1-11; 1 Chronicles 3:17-19a',
'9/9':'Ezra 2:1-4:5; 1 Chronicles 3:19b-24',
'9/10':'Daniel 10:1-12:13; Ezra 4:24-5:1; Haggai 1:1-15',
'9/11':'Haggai 2:1-9; Zechariah 1:1-6; Haggai 2:10-19; Ezra 5:2; Haggai 2:20-23; Zechariah 1:7-5:11',
'9/12':'Zechariah 6:1-15; Ezra 5:3-6:14a; Zechariah 7:1-8:23',
'9/13':'Zechariah 9:1-14:21',
'9/14':'Ezra 6:14b-22; Ezra 4:6; Esther 1:1-4:17',
'9/15':'Esther 5:1-10:3',
'9/16':'Ezra 4:7-23; Ezra 7:1-8:36',
'9/17':'Ezra 9:1-10:44; Nehemiah 1:1-2:20 ',
'9/18':'Nehemiah 3:1-7:3',
'9/19':'Nehemiah 7:4-8:12',
'9/20':'Nehemiah 8:13-10:39',
'9/21':'Nehemiah 11:1-12:26; 1 Chronicles 9:1b-34',
'9/22':'Nehemiah 12:27-13:6; Nehemiah 5:14-19; Nehemiah 13:7-31; Malachi 1:1-2:9',
'9/23':'Malachi 2:10-4:6; Joel 1:1-3:21',
'9/24':'Mark 1:1a; Luke 1:1-4; John 1:1-18; Matthew 1:1-17; Luke 3:23b-38; Luke 1:5-38',
'9/25':'Luke 1:39-80; Matthew 1:18-25; Luke 2:1-40',
'9/26':'Matthew 2:1-23; Luke 2:41-52; Mark 1:1b-8; Matthew 3:1-12; Luke 3:1-18; Mark 1:9-11; Matthew 3:13-17; Luke 3:21-22',
'9/27':'Mark 1:12-13; Matthew 4:1-11; Luke 4:1-15; John 1:19-2:25',
'9/28':'John 3:1-4:45; Luke 3:19-20',
'9/29':'Mark 1:14-15; Matthew 4:12-17; Luke 3:23a; John 4:46-54; Luke 4:16-30; Mark 1:16-20; Matthew 4:18-22; Mark 1:21-28; Luke 4:31-37; Mark 1:29-34; Matthew 8:14-17; Luke 4:38-41; Mark 1:35-39; Luke 4:42-44; Matthew 4:23-25',
'9/30':'Luke 5:1-11; Mark 1:40-45; Matthew 8:1-4; Luke 5:12-16; Mark 2:1-12; Matthew 9:1-8; Luke 5:17-26; Mark 2:13-17; Matthew 9:9-13; Luke 5:27-32; Mark 2:18-22; Matthew 9:14-17; Luke 5:33-39',
'10/1':'John 5:1-47; Mark 2:23-28; Matthew 12:1-8; Luke 6:1-5; Mark 3:1-6; Matthew 12:9-14; Luke 6:6-11; Matthew 12:15-21',
'10/2':'Mark 3:7-19; Luke 6:12-16; Matthew 5:1-12; Luke 6:17-26; Matthew 5:13-48; Luke 6:27-36; Matthew 6:1-4',
'10/3':'Matthew 6:5-7:6; Luke 6:37-42; Matthew 7:7-20; Luke 6:43-45; Matthew 7:21-29; Luke 6:46-49',
'10/4':'Matthew 8:5-13; Luke 7:1-17; Matthew 11:1-19; Luke 7:18-35; Matthew 11:20-30; Luke 7:36-50',
'10/5':'Luke 8:1-3; Mark 3:20-30; Matthew 12:22-45; Mark 3:31-35; Matthew 12:46-50; Luke 8:19-21; Mark 4:1-9; Matthew 13:1-9; Luke 8:4-8; Mark 4:10-20',
'10/6':'Matthew 13:10-23; Luke 8:9-18; Mark 4:21-29; Matthew 13:24-30; Mark 4:30-34; Matthew 13:31-52; Mark 4:35-41; Matthew 8:23-27; Luke 8:22-25',
'10/7':'Mark 5:1-20; Matthew 8:28-34; Luke 8:26-39; Mark 5:21-43; Matthew 9:18-26; Luke 8:40-56',
'10/8':'Matthew 9:27-34; Mark 6:1-6; Matthew 13:53-58; Matthew 9:35-38; Mark 6:7-13; Matthew 10:1-42; Luke 9:1-6',
'10/9':'Luke 9:7-9; Mark 6:14-29; Matthew 14:1-21; Mark 6:30-44; Luke 9:10-17; John 6:1-15; Mark 6:45-52; Matthew 14:22-33; John 6:16-21; Mark 6:53-56; Matthew 14:34-36',
'10/10':'John 6:22-71; Mark 7:1-23; Matthew 15:1-20',
'10/11':'Mark 7:24-30; Matthew 15:21-28; Mark 7:31-37; Matthew 15:29-31; Mark 8:1-10; Matthew 15:32-16:4; Mark 8:11-21; Matthew 16:5-12',
'10/12':'Mark 8:22-30; Matthew 16:13-20; Luke 9:18-20; Mark 8:31-9:1; Matthew 16:21-28; Luke 9:21-27; Mark 9:2-13; Matthew 17:1-13; Luke 9:28-36',
'10/13':'Mark 9:14-29; Matthew 17:14-21; Luke 9:37-43a; Mark 9:30-32; Matthew 17:22-23; Luke 9:43b-45; Matthew 17:24-27; Mark 9:33-37; Matthew 18:1-6; Luke 9:46-48; Mark 9:38-41; Luke 9:49-50; Mark 9:42-50; Matthew 18:7-35',
'10/14':'John 7:1-9; Luke 9:51-56; Matthew 8:18-22; Luke 9:57-62; John 7:10-8:20',
'10/15':'John 8:21-59; Luke 10:1-11:13',
'10/16':'Luke 11:14-12:34',
'10/17':'Luke 12:35-13:21; John 9:1-41',
'10/18':'John 10:1-42; Luke 13:22-14:24',
'10/19':'Luke 14:25-17:10; John 11:1-37',
'10/20':'John 11:38-57; Luke 17:11-18:8',
'10/21':'Luke 18:9-14; Mark 10:1-12; Matthew 19:1-12; Mark 10:13-16; Matthew 19:13-15; Luke 18:15-17; Mark 10:17-31; Matthew 19:16-30; Luke 18:18-30',
'10/22':'Matthew 20:1-16; Mark 10:32-34 ; Matthew 20:17-19; Luke 18:31-34; Mark 10:35-45; Matthew 20:20-34; Mark 10:46-52; Luke 18:35-19:27',
'10/23':'Mark 14:3-9; Matthew 26:6-13; John 12:1-11; Mark 11:1-11; Matthew 21:1-11 ; Luke 19:28-40; John 12:12-19; Luke 19:41-44; John 12:20-36',
'10/24':'John 12:37-50; Mark 11:12-14 ; Matthew 21:18-22; Mark 11:15-19; Matthew 21:12-17; Luke 19:45-48; Mark 11:20-33; Matthew 21:23-27; Luke 20:1-8',
'10/25':'Matthew 21:28-32; Mark 12:1-12; Matthew 21:33-46; Luke 20:9-19; Matthew 22:1-14; Mark 12:13-17; Matthew 22:15-22; Luke 20:20-26; Mark 12:18-27; Matthew 22:23-33; Luke 20:27-40',
'10/26':'Mark 12:28-34; Matthew 22:34-40; Mark 12:35-37; Matthew 22:41-46; Luke 20:41-44; Mark 12:38-40; Matthew 23:1-12; Luke 20:45-47; Matthew 23:13-39; Mark 12:41-44; Luke 21:1-4',
'10/27':'Mark 13:1-23; Matthew 24:1-25; Luke 21:5-24; Mark 13:24-31; Matthew 24:26-35; Luke 21:25-33',
'10/28':'Mark 13:32-37; Matthew 24:36-51; Luke 21:34-38; Matthew 25:1-46',
'10/29':'Mark 14:1-2; Matthew 26:1-5; Luke 22:1-2; Mark 14:10-11; Matthew 26:14-16; Luke 22:3-6; Mark 14:12-16; Matthew 26:17-19; Luke 22:7-13; John 13:1-17; Mark 14:17-26; Matthew 26:20-30; Luke 22:14-30; John 13:18-30',
'10/30':'John 13:31-38; Mark 14:27-31; Matthew 26:31-35; Luke 22:31-38; John 14:1-15:17',
'10/31':'John 15:18-17:26',
'11/1':'John 18:1-2; Mark 14:32-42; Matthew 26:36-46; Luke 22:39-46; Mark 14:43-52; Matthew 26:47-56; Luke 22:47-53; John 18:3-24',
'11/2':'Mark 14:53-65; Matthew 26:57-68; Mark 14:66-72; Matthew 26:69-75; Luke 22:54-62; John 18:25-27; Mark 15:1; Matthew 27:1-2; Luke 22:66-71; Matthew 27:3-10',
'11/3':'Mark 15:2-5; Matthew 27:11-14; Luke 23:1-12; John 18:28-40; Mark 15:6-15; Matthew 27:15-26; Luke 23:13-25; John 19:1-16; Mark 15:16-20; Matthew 27:27-31; Luke 22:63-65',
'11/4':'Mark 15:21-24; Matthew 27:32-34; Luke 23:26-31; John 19:17; Mark 15:25-32; Matthew 27:35-44; Luke 23:32-43; John 19:18-27; Mark 15:33-41; Matthew 27:45-56; Luke 23:44-49; John 19:28-37',
'11/5':'Mark 15:42-47; Matthew 27:57-61; Luke 23:50-56; John 19:38-42; Matthew 27:62-66; Mark 16:1-8; Matthew 28:1-7; Luke 24:1-12; Mark 16:9-11; John 20:1-18; Matthew 28:8-15',
'11/6':'Luke 24:13-43; Mark 16:12-13; John 20:19-23; Mark 16:14; John 20:24-21:25; Matthew 28:16-20; Mark 16:15-18; Luke 24:44-49',
'11/7':'Mark 16:19-20; Luke 24:50-53; Acts 1:1-2:47',
'11/8':'Acts 3:1-5:42',
'11/9':'Acts 6:1-8:1a',
'11/10':'Acts 8:1b-9:43',
'11/11':'Acts 10:1-12:5',
'11/12':'Acts 12:6-14:20',
'11/13':'Acts 14:21-28; Galatians 1:1-3:23',
'11/14':'Galatians 3:24-6:18; Acts 15:1-21',
'11/15':'Acts 15:22-17:15',
'11/16':'Acts 17:16-18:3; 1 Thessalonians 1:1-5:11',
'11/17':'1 Thessalonians 5:12-28; 2 Thessalonians 1:1-3:18; Acts 18:4-23',
'11/18':'Acts 18:24-19:20; 1 Corinthians 1:1-3:23',
'11/19':'1 Corinthians 4:1-7:40',
'11/20':'1 Corinthians 8:1-11:1',
'11/21':'1 Corinthians 11:2-13:13',
'11/22':'1 Corinthians 14:1-15:58',
'11/23':'1 Corinthians 16:1-24; Acts 19:21-20:6; Romans 1:1-32',
'11/24':'Romans 2:1-4:25',
'11/25':'Romans 5:1-8:17',
'11/26':'Romans 8:18-10:21',
'11/27':'Romans 11:1-14:23',
'11/28':'Romans 15:1-16:27; 2 Corinthians 1:1-2:4',
'11/29':'2 Corinthians 2:5-6:13',
'11/30':'2 Corinthians 6:14-10:18',
'12/1':'2 Corinthians 11:1-13:14; Acts 20:7-12',
'12/2':'Acts 20:13-21:36',
'12/3':'Acts 21:37-23:35',
'12/4':'Acts 24:1-26:32',
'12/5':'Acts 27:1-44',
'12/6':'Acts 28:1-32; Ephesians 1:1-2:22',
'12/7':'Ephesians 3:1-5:14',
'12/8':'Ephesians 5:15-6:24; Colossians 1:1-23',
'12/9':'Colossians 1:24-4:18',
'12/10':'Philemon 1:1-25; Philippians 1:1-2:11',
'12/11':'Philippians 2:12-4:23',
'12/12':'James 1:1-3:18',
'12/13':'James 4:1-5:20; 1 Timothy 1:1-2:15',
'12/14':'1 Timothy 3:1-6:10',
'12/15':'1 Timothy 6:11-21; Titus 1:1-3:15; 2 Timothy 1:1-18',
'12/16':'2 Timothy 2:1-4:18',
'12/17':'2 Timothy 4:19-22; Hebrews 1:1-4:13',
'12/18':'Hebrews 4:14-7:28',
'12/19':'Hebrews 8:1-10:39',
'12/20':'Hebrews 11:1-12:29',
'12/21':'Hebrews 13:1-25; 1 Peter 1:1-2:3',
'12/22':'1 Peter 2:4-5:11',
'12/23':'1 Peter 5:12-14; 2 Peter 1:1-3:18',
'12/24':'1 John 1:1-4:6',
'12/25':'1 John 4:7-5:21; 2 John 1:1-13; 3 John 1:1-15',
'12/26':'Jude 1:1-25; Revelation 1:1-2:29',
'12/27':'Revelation 3:1-6:17',
'12/28':'Revelation 7:1-10:11',
'12/29':'Revelation 11:1-14:20',
'12/30':'Revelation 15:1-18:24',
'12/31':'Revelation 19:1-22:21'
};
var NYB_MONTHS=['January','February','March','April','May','June','July','August','September','October','November','December'];
function nybAutoFill(force){
  var now=new Date();
  var key=(now.getMonth()+1)+'/'+now.getDate();
  var passage=NYB_PLAN[key]||'';
  var bookEl=document.getElementById('audibleBook');
  var dayEl=document.getElementById('audibleChapter');
  var passEl=document.getElementById('audiblePassages');
  if(!bookEl||!dayEl||!passEl)return;
  var BOOK_TITLE='The One Year Chronological Bible NLT (One Year Bible: NLT Book 1) by New Living Translation';
  if(force||!bookEl.value.trim())bookEl.value=BOOK_TITLE;
  if(force||!dayEl.value.trim())dayEl.value=NYB_MONTHS[now.getMonth()]+' '+now.getDate();
  if((force||!passEl.value.trim())&&passage)passEl.value=passage;
  meditSaveAudible();
}
/* ===== END One Year Bible plan ===== */

function meditSaveAudible(){
  var book=document.getElementById('audibleBook');
  var ch=document.getElementById('audibleChapter');
  var ps=document.getElementById('audiblePassages');
  var notes=document.getElementById('audibleNotes');
  localStorage.setItem('audible_book',book?book.value:'');
  localStorage.setItem('audible_chapter',ch?ch.value:'');
  localStorage.setItem('audible_passages',ps?ps.value:'');
  if(notes)localStorage.setItem('audible_notes',notes.value);
}

function meditClearYT(){
  var yf=document.getElementById('meditYTFrame');if(yf)yf.innerHTML='';
  var inp=document.getElementById('meditYTUrl');if(inp)inp.value='';
}

/* --- Verse Explainer --- */
function meditVerseSummary(){
  var verse=(localStorage.getItem('medit_scripture')||'').trim()||'"Be still, and know that I am God." — Psalm 46:10';
  var box=document.getElementById('meditVerseExplain');
  if(!box)return;
  box.style.display='block';
  box.textContent='⏳ Looking up this verse...';
  var prompt='Give me a brief, devotional explanation of this Bible verse:\n\n"'+verse+'"\n\nInclude:\n1. The historical/biblical context in 1-2 sentences\n2. The core spiritual meaning (2-3 sentences)\n3. One practical way to apply it today\n\nKeep the tone warm, encouraging, and grounded in Christian faith. Under 200 words.';
  aiCall(prompt,2000,function(text){
    _meditPending={text:text,type:'verse',label:'Verse Explanation',date:new Date().toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'})};
    box.style.display='block';
    box.innerHTML='<div style="white-space:pre-wrap;line-height:1.6;">'+escHtml(text)+'</div>';
  },function(err){box.textContent='Error: '+err;});
}

/* --- AI Reflection --- */
function meditAIPrompt(type){
  var res=document.getElementById('meditAIResult');
  if(!res)return;
  res.style.display='block';res.textContent='⏳ Preparing your meditation guide...';
  var verse=localStorage.getItem('medit_scripture')||'Psalm 46:10 — Be still, and know that I am God.';
  var todayKey=meditTodayKey();
  var plannerData=localStorage.getItem('planner_'+todayKey)||'';
  var gratitude='';try{var pd=JSON.parse(plannerData||'{}');gratitude=pd.gratitude||pd.spGratitude||'';}catch(e){}
  var prompts={
    reflection:'You are a gentle Christian meditation guide. Today\'s scripture focus is: "'+verse+'"\n\nCreate a brief, peaceful 5-minute guided meditation reflection based on this verse. Include: 1) A moment of stillness opening, 2) A slow prayerful reading of the verse, 3) A few contemplative questions to sit with, 4) A closing breath prayer. Keep it warm, intimate, and spiritually grounding. Under 250 words.',
    prayer:'You are a gentle Christian prayer guide. Today\'s verse: "'+verse+'"\n'+(gratitude?'The person is grateful for: '+gratitude+'\n':'')+'Write a personal, heartfelt guided prayer that flows naturally from this verse. Include thanksgiving, confession, petition, and surrender. Spoken in first person. Warm and authentic. Under 200 words.',
    lectio:'Guide a Lectio Divina (sacred reading) for: "'+verse+'"\n\nWalk through the 4 movements:\n1. LECTIO (Read) — Read the verse slowly\n2. MEDITATIO (Reflect) — What word or phrase stands out?\n3. ORATIO (Respond) — What is your heart\'s response to God?\n4. CONTEMPLATIO (Rest) — Simply rest in God\'s presence\n\nKeep each movement brief and inviting. Under 220 words.',
    gratitude:'Create a 5-minute Christian gratitude meditation. Today\'s verse: "'+verse+'"'+(gratitude?'\nThings to be grateful for today: '+gratitude:'')+'.\n\nGuide through: body gratitude, relationship gratitude, spiritual gratitude, and closing with a prayer of praise. Warm, peaceful tone. Under 220 words.',
    body:'Create a gentle Christian body scan meditation opening with: "'+verse+'"\n\nGuide awareness slowly from head to toe, inviting the person to release tension and offer each part of their body to God. Include a breath prayer at each major body region. Peaceful, slow, restorative. Under 250 words.'
  };
  var prompt=prompts[type]||prompts.reflection;
  var typeLabels={reflection:'Daily Reflection',prayer:'Guided Prayer',lectio:'Lectio Divina',gratitude:'Gratitude Focus',body:'Body Scan'};
  var typeLabel=typeLabels[type]||'Meditation Guide';
  aiCall(prompt,3000,function(text){
    _meditPending={text:text,type:type,label:typeLabel,date:new Date().toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'})};
    res.style.display='block';
    res.innerHTML='<div style="white-space:pre-wrap;line-height:1.6;color:#3b0764;">'+escHtml(text)+'</div>';
  },function(err){res.textContent='Error: '+err;});
}

/* --- Guided Worship (Bible & Worship) --- */
function bibleWorshipAIPrompt(){
  var res=document.getElementById('bibleWorshipAIResult');
  if(!res)return;
  res.style.display='block';res.textContent='⏳ Preparing your worship moment...';
  var verse=(localStorage.getItem('medit_scripture')||'').trim()||'"Be still, and know that I am God." — Psalm 46:10';
  var prompt='You are a gentle Christian worship guide. Today\'s scripture focus is: "'+verse+'"\n\nCreate a brief, guided worship moment (3-5 minutes) centered on praise rather than petition. Include: 1) A short call to worship, 2) A few lines connecting the verse to who God is, 3) A simple sung or spoken praise response the person can say or sing aloud, 4) A closing blessing. Keep it warm, reverent, and easy to follow. Under 220 words.';
  aiCall(prompt,2200,function(text){
    res.style.display='block';
    res.innerHTML='<div style="white-space:pre-wrap;line-height:1.6;color:#3b0764;">'+escHtml(text)+'</div>';
  },function(err){res.textContent='Error: '+err;});
}

/* --- Session Logging --- */
function meditSaveSession(auto){
  var notes=document.getElementById('meditNotes')?document.getElementById('meditNotes').value:'';
  var dur=Math.round((_meditTimerTotal-_meditTimerSecs)/60);
  var verse=localStorage.getItem('medit_scripture')||'';
  var todayKey=meditTodayKey();
  var sessions=JSON.parse(localStorage.getItem('medit_sessions')||'[]');
  // upsert: update today's entry if it exists, otherwise add new
  var idx=sessions.findIndex(function(s){return s.date===todayKey;});
  if(idx>=0){sessions[idx]=Object.assign(sessions[idx],{dur:dur,notes:notes,verse:verse,ts:Date.now()});}
  else{sessions.unshift({date:todayKey,dur:dur,notes:notes,verse:verse,ts:Date.now()});}
  if(sessions.length>60)sessions=sessions.slice(0,60);
  localStorage.setItem('medit_sessions',JSON.stringify(sessions));
  var msg=document.getElementById('meditSaveMsg');
  if(msg){msg.textContent=auto?'Session auto-saved ✓':'Saved ✓';setTimeout(function(){msg.textContent='';},2500);}
  renderMeditLog();
}

function meditDeleteSession(idx){
  if(!confirm('Delete this session?'))return;
  var sessions=JSON.parse(localStorage.getItem('medit_sessions')||'[]');
  sessions.splice(idx,1);
  localStorage.setItem('medit_sessions',JSON.stringify(sessions));
  renderMeditLog();
}

function meditEditSession(idx){
  var sessions=JSON.parse(localStorage.getItem('medit_sessions')||'[]');
  var s=sessions[idx];if(!s)return;
  var el=document.getElementById('meditSessionLog');if(!el)return;
  var row=el.querySelector('[data-edit-idx="'+idx+'"]');if(!row)return;
  row.innerHTML=
    '<td colspan="4" style="padding:8px;">'
    +'<div style="display:flex;flex-direction:column;gap:6px;">'
    +'<div style="display:flex;gap:6px;flex-wrap:wrap;align-items:center;">'
    +'<label style="font-size:0.72rem;color:#555;font-weight:600;">Date</label>'
    +'<input id="meditEditDate_'+idx+'" value="'+s.date+'" style="border:1px solid #c4b5fd;border-radius:5px;padding:3px 7px;font-size:0.8rem;width:110px;outline:none;">'
    +'<label style="font-size:0.72rem;color:#555;font-weight:600;">Duration (min)</label>'
    +'<input id="meditEditDur_'+idx+'" type="number" value="'+(s.dur||0)+'" style="border:1px solid #c4b5fd;border-radius:5px;padding:3px 7px;font-size:0.8rem;width:60px;outline:none;">'
    +'</div>'
    +'<textarea id="meditEditNotes_'+idx+'" rows="2" style="width:100%;border:1px solid #c4b5fd;border-radius:6px;padding:6px 8px;font-size:0.82rem;box-sizing:border-box;outline:none;resize:vertical;">'+((s.notes||'')).replace(/</g,'&lt;')+'</textarea>'
    +'<div style="display:flex;gap:6px;">'
    +'<button onclick="meditSaveEdit('+idx+')" style="background:#8b5cf6;color:#fff;border:none;border-radius:6px;padding:5px 13px;font-size:0.78rem;cursor:pointer;font-weight:600;">&#x2713; Save</button>'
    +'<button onclick="renderMeditLog()" style="background:#f3f4f6;color:#555;border:1px solid #d1d5db;border-radius:6px;padding:5px 10px;font-size:0.78rem;cursor:pointer;">Cancel</button>'
    +'</div></div></td>';
}

function meditSaveEdit(idx){
  var sessions=JSON.parse(localStorage.getItem('medit_sessions')||'[]');
  var s=sessions[idx];if(!s)return;
  var d=document.getElementById('meditEditDate_'+idx);
  var du=document.getElementById('meditEditDur_'+idx);
  var n=document.getElementById('meditEditNotes_'+idx);
  if(d)s.date=d.value.trim();
  if(du)s.dur=parseInt(du.value)||0;
  if(n)s.notes=n.value;
  sessions[idx]=s;
  localStorage.setItem('medit_sessions',JSON.stringify(sessions));
  renderMeditLog();
}

function renderMeditLog(){
  var el=document.getElementById('meditSessionLog');if(!el)return;
  var sessions=JSON.parse(localStorage.getItem('medit_sessions')||'[]');
  if(!sessions.length){el.innerHTML='<div style="color:#aaa;font-style:italic;">No sessions logged yet.</div>';return;}
  var html='<table style="width:100%;border-collapse:collapse;">'
    +'<thead><tr style="border-bottom:2px solid #e5e7eb;">'
    +'<th style="text-align:left;padding:5px 8px;font-size:0.73rem;color:#555;font-weight:700;">Date</th>'
    +'<th style="text-align:center;padding:5px 8px;font-size:0.73rem;color:#555;font-weight:700;">Duration</th>'
    +'<th style="text-align:left;padding:5px 8px;font-size:0.73rem;color:#555;font-weight:700;">Notes</th>'
    +'<th style="padding:5px 8px;"></th>'
    +'</tr></thead><tbody>';
  sessions.slice(0,20).forEach(function(s,i){
    var bg=i%2===0?'':'background:#f9fafb;';
    html+='<tr data-edit-idx="'+i+'" style="border-bottom:1px solid #f3f4f6;'+bg+'">'
      +'<td style="padding:6px 8px;font-size:0.8rem;color:#374151;white-space:nowrap;">'+s.date+'</td>'
      +'<td style="padding:6px 8px;font-size:0.8rem;color:#7c3aed;text-align:center;font-weight:600;">'+(s.dur||0)+'m</td>'
      +'<td style="padding:6px 8px;font-size:0.78rem;color:#6b7280;">'+(s.notes||s.verse||'—').slice(0,70)+'</td>'
      +'<td style="padding:6px 8px;white-space:nowrap;text-align:right;">'
      +'<button onclick="meditEditSession('+i+')" style="background:#ede9fe;color:#7c3aed;border:1px solid #c4b5fd;border-radius:5px;padding:3px 9px;font-size:0.72rem;cursor:pointer;margin-right:4px;">&#x270E; Edit</button>'
      +'<button onclick="meditDeleteSession('+i+')" style="background:#fef2f2;color:#dc2626;border:1px solid #fca5a5;border-radius:5px;padding:3px 9px;font-size:0.72rem;cursor:pointer;">&#x2715; Delete</button>'
      +'</td>'
      +'</tr>';
  });
  html+='</tbody></table>';
  el.innerHTML=html;
}

function _initScriptureFocus(){
  var inp=document.getElementById('spDailyBreadText');
  var disp=document.getElementById('meditScriptureDisplay');
  var verse=(inp&&inp.value)||localStorage.getItem('medit_scripture')||'';
  // try loading from today's planner Daily Bread / Bible notes
  if(!verse){
    var todayKey=meditTodayKey();
    try{
      var pd=JSON.parse(localStorage.getItem('planner_'+todayKey)||'{}');
      verse=pd.spDailyBreadText||getSermonNotes(todayKey)||'';
      if(verse&&verse.length>120)verse=verse.slice(0,120)+'…';
    }catch(e){}
  }
  if(inp&&!inp.value)inp.value=verse;
  if(disp)disp.textContent=verse||'“Be still, and know that I am God.” — Psalm 46:10';
}
function meditInitTab(){
  var dl=document.getElementById('meditTabDate');
  if(dl)dl.textContent=new Date().toLocaleDateString('en-US',{weekday:'long',month:'long',day:'numeric',year:'numeric'});
  _initScriptureFocus();
  var todayKey=meditTodayKey();
  var notes=localStorage.getItem('medit_notes_'+todayKey)||'';
  var na=document.getElementById('meditNotes');if(na)na.value=notes;
  var ab=document.getElementById('audibleBook');if(ab)ab.value=localStorage.getItem('audible_book')||'';
  var ac=document.getElementById('audibleChapter');if(ac)ac.value=localStorage.getItem('audible_chapter')||'';
  var ap=document.getElementById('audiblePassages');if(ap)ap.value=localStorage.getItem('audible_passages')||'';
  var an=document.getElementById('audibleNotes');if(an)an.value=localStorage.getItem('audible_notes')||'';
  // Auto-fill book/day/passages if all three are empty (first time today)
  var ab2=document.getElementById('audibleBook');
  var ac2=document.getElementById('audibleChapter');
  var ap2=document.getElementById('audiblePassages');
  if(ab2&&ac2&&ap2&&!ab2.value.trim()&&!ac2.value.trim()&&!ap2.value.trim()){
    nybAutoFill(false);
  }
  meditUpdateDisplay();
  renderMeditLog();
}
/* ========= END MEDITATION JS ========= */

function renderGrowthActivityReport(hostId,periods){
  var host=document.getElementById(hostId);if(!host)return;
  var entries=getSkillEntries(),specs=[['language','Language'],['guitar','Guitar']];
  function kind(entry){var cat=String(entry&&entry.category||'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();if(cat==='financial videos'||cat==='retirement videos')return'financial';if(cat==='udemy')return'udemy';if(cat==='gun range'||cat==='shooting')return'gun';if(cat==='language'||cat==='spanish'||cat==='language learning')return'language';if(cat==='guitar')return'guitar';if(cat==='reading audiobook'||cat==='reading'||cat==='audiobook')return'reading';return'';}
  function totals(period){var latest={};entries.forEach(function(entry){if(!entry||!entry.date||entry.date<period.start||entry.date>period.end)return;var k=kind(entry);if(k)latest[entry.date+'|'+k]=entry;});var out={};Object.keys(latest).forEach(function(key){var k=kind(latest[key]);out[k]=(out[k]||0)+parseSkillTimeMins(latest[key].time);});var cursor=new Date(period.start+'T12:00:00'),finish=new Date(period.end+'T12:00:00');while(cursor<=finish){var dateKey=dk(cursor);if(!latest[dateKey+'|reading']){try{var saved=JSON.parse(localStorage.getItem('planner_'+dateKey)||'{}'),legacy=parseTimedMins(saved.gaAudiobookTime);if(legacy)out.reading=(out.reading||0)+legacy;}catch(ignore){}}cursor.setDate(cursor.getDate()+1);}return out;}
  var grand={},rows=periods.map(function(period){var values=totals(period);specs.forEach(function(spec){grand[spec[0]]=(grand[spec[0]]||0)+(values[spec[0]]||0);});return'<tr><td>'+escHtml(period.label)+'</td>'+specs.map(function(spec){return'<td>'+((values[spec[0]]||0)?fmtMinsSeconds(values[spec[0]]):'-')+'</td>';}).join('')+'</tr>';});
  rows.push('<tr style="background:#f8f9fa;font-weight:700"><td>TOTAL</td>'+specs.map(function(spec){return'<td>'+((grand[spec[0]]||0)?fmtMinsSeconds(grand[spec[0]]):'-')+'</td>';}).join('')+'</tr>');
  host.innerHTML='<table class="summary-table"><thead><tr><th>Period</th>'+specs.map(function(spec){return'<th>'+spec[1]+'</th>';}).join('')+'</tr></thead><tbody>'+rows.join('')+'</tbody></table>';
}
function renderWeeklyGrowthTable(start,end,days,labels){
  var host=document.getElementById('wGrowth');if(!host)return;
  removeObsoleteQuickGrowthEntries();
  var skills=getSkillEntries();
  function skillCategory(entry){return String(entry&&entry.category||'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();}
  function skillMatches(entry,category){
    var cat=skillCategory(entry),search=String([entry&&entry.name,entry&&entry.resource,entry&&entry.notes].filter(Boolean).join(' ')).toLowerCase();
    if(category==='udemy')return cat==='udemy'||/\budemy\b/.test(search);
    if(category==='range')return cat==='gun range'||cat==='shooting'||(!cat&&/gun range|shooting|firearm/.test(search));
    if(category==='retirement')return cat==='retirement videos'||cat==='financial videos'||(!cat&&/retirement video|financial video/.test(search));
    if(category==='reading')return cat==='reading audiobook'||cat==='reading'||cat==='audiobook';
    return cat==='language'||cat==='spanish'||cat==='language learning'||(!cat&&/language|spanish/.test(search));
  }
  function skillDisplay(minutes,count,isTotal){if(minutes)return fmtMinsSeconds(minutes);if(!count)return'-';return isTotal?count+' session'+(count===1?'':'s'):'&#x2705;';}
  function financialVideoTimeDisplay(minutes,count){if(minutes)return fmtMinsSeconds(minutes);return count?'Time not entered':'-';}
  var udemyTotal=0,rangeTotal=0,languageTotal=0,retirementTotal=0,readingTotal=0,udemySessions=0,rangeSessions=0,languageSessions=0,retirementSessions=0,readingSessions=0,rows=[];
  for(var i=0;i<7;i++){
    var date=new Date(start);date.setDate(start.getDate()+i);var key=dk(date);
    var udemyEntries=skills.filter(function(entry){return entry.date===key&&skillMatches(entry,'udemy');});
    var rangeEntries=skills.filter(function(entry){return entry.date===key&&skillMatches(entry,'range');});
    var languageEntries=skills.filter(function(entry){return entry.date===key&&skillMatches(entry,'language');});if(languageEntries.length>1)languageEntries=[languageEntries[languageEntries.length-1]];
    var retirementEntries=skills.filter(function(entry){return entry.date===key&&skillMatches(entry,'retirement');});
    var readingEntries=skills.filter(function(entry){return entry.date===key&&skillMatches(entry,'reading');});
    var udemy=udemyEntries.reduce(function(total,entry){return total+parseSkillTimeMins(entry.time);},0);
    var range=rangeEntries.reduce(function(total,entry){return total+parseSkillTimeMins(entry.time);},0);
    var language=languageEntries.length?(parseSkillTimeMins(languageEntries[0].time)||15):0;
    var dedicatedFinancial=(days[i]||{}).financialVideos||0;
    var legacyFinancial=retirementEntries.reduce(function(total,entry){return total+parseSkillTimeMins(entry.time);},0);
    // The dedicated Daily tracker is now the authoritative Financial Videos source.
    // Fall back to older Skills entries only on days without dedicated minutes so
    // migrated history remains visible without counting the same session twice.
    var retirement=dedicatedFinancial>0?dedicatedFinancial:legacyFinancial;
    var financialSessions=(dedicatedFinancial>0||(days[i]||{}).financialVideosDone)?1:retirementEntries.length;
    var dedicatedReading=(days[i]||{}).audiobook||0,loggedReading=readingEntries.reduce(function(total,entry){return total+parseSkillTimeMins(entry.time);},0),reading=dedicatedReading>0?dedicatedReading:loggedReading,readingCount=dedicatedReading>0?1:readingEntries.length;
    udemyTotal+=udemy;rangeTotal+=range;languageTotal+=language;retirementTotal+=retirement;readingTotal+=reading;udemySessions+=udemyEntries.length;rangeSessions+=rangeEntries.length;languageSessions+=languageEntries.length;retirementSessions+=financialSessions;readingSessions+=readingCount;
    rows.push('<tr><td>'+escHtml(labels[i])+'</td><td>'+financialVideoTimeDisplay(retirement,financialSessions)+'</td><td>'+skillDisplay(udemy,udemyEntries.length,false)+'</td><td>'+skillDisplay(range,rangeEntries.length,false)+'</td><td>'+skillDisplay(language,languageEntries.length,false)+'</td><td>'+skillDisplay(reading,readingCount,false)+'</td></tr>');
  }
  rows.push('<tr style="background:#f8f9fa;font-weight:700"><td>TOTAL</td><td>'+financialVideoTimeDisplay(retirementTotal,retirementSessions)+'</td><td>'+skillDisplay(udemyTotal,udemySessions,true)+'</td><td>'+skillDisplay(rangeTotal,rangeSessions,true)+'</td><td>'+skillDisplay(languageTotal,languageSessions,true)+'</td><td>'+skillDisplay(readingTotal,readingSessions,true)+'</td></tr>');
  var startKey=dk(start),endKey=dk(end),weekDetails=skills.filter(function(entry){if(!entry||entry.date<startKey||entry.date>endKey)return false;return['udemy','range','language','retirement','reading'].some(function(category){return skillMatches(entry,category);});}).sort(function(a,b){return String(b.date||'').localeCompare(String(a.date||''));});
  var detailHtml=weekDetails.length?'<details style="margin-top:10px;border:1px solid #ddd6fe;border-radius:8px;padding:8px 10px;background:#faf5ff;"><summary style="cursor:pointer;font-size:.78rem;font-weight:800;color:#6d28d9;">Saved Growth / Skill Details ('+weekDetails.length+')</summary><div style="display:grid;gap:7px;margin-top:8px;">'+weekDetails.map(function(entry){var link=skillSafeLink(entry.link);return'<div style="background:#fff;border:1px solid #ede9fe;border-radius:7px;padding:8px;font-size:.75rem;"><strong>'+escHtml(entry.name||entry.category||'Growth entry')+'</strong><div style="color:#64748b;margin-top:2px;">'+escHtml(entry.date||'')+' &bull; '+escHtml(entry.category||'')+(entry.time?' &bull; '+escHtml(entry.time):'')+'</div>'+(entry.resource?'<div style="margin-top:3px;">'+escHtml(entry.resource)+'</div>':'')+(link?'<a href="'+escHtml(link)+'" target="_blank" rel="noopener noreferrer" style="display:inline-block;margin-top:4px;color:#6d28d9;font-weight:700;">Open saved link</a>':'')+(entry.notes?'<div style="margin-top:4px;color:#475569;white-space:pre-wrap;">'+escHtml(entry.notes)+'</div>':'')+'</div>';}).join('')+'</div></details>':'';
  host.innerHTML='<table class="summary-table"><thead><tr><th>Period</th><th>Financial Videos</th><th>Udemy</th><th>Gun Range</th><th>Language</th><th>Reading / Audiobook</th></tr></thead><tbody>'+rows.join('')+'</tbody></table>'+detailHtml;
}

var _hmTabs=['habits2','meditate','bible','library','wellness','file-uploads'];
var _cardTabs=['weekly','monthly','yearly','dashboard'].concat(_hmTabs);
function toggleHamburger(e){
  if(e)e.stopPropagation();
  var sidebar=document.getElementById('hmSidebar');
  var overlay=document.getElementById('hmOverlay');
  if(sidebar.classList.contains('open')){
    sidebar.classList.remove('open');overlay.classList.remove('open');
  } else {
    sidebar.classList.add('open');overlay.classList.add('open');
  }
}
function closeHamburger(){
  document.getElementById('hmSidebar').classList.remove('open');
  document.getElementById('hmOverlay').classList.remove('open');
}
function hmSwitch(tab,e){
  if(e)e.stopPropagation();
  closeHamburger();
  document.querySelectorAll('.hm-nav-btn').forEach(b=>b.classList.remove('hm-active'));
  var hb=document.getElementById('hm-'+tab);if(hb)hb.classList.add('hm-active');
  switchTab(tab,null);
}
function makeHabitsInformational(){
  var panel=document.getElementById('tab-habits');
  if(!panel)return;
  panel.querySelectorAll('.tri-check,input[type="checkbox"]').forEach(function(el){el.remove();});
}
function switchTab(tab,e){
  // The former Skills screen is retained only as a private legacy data store.
  // Never expose that retired screen through old links or saved navigation state.
  if(tab==='skill-log')tab='daily';
  _cardTabs.forEach(function(t){document.body.classList.toggle('tab-'+t+'-active',tab===t);});
  saveTabNote();_currentNoteTab=tab;loadTabNote(tab);
  document.querySelectorAll('.tab-panel').forEach(p=>p.classList.remove('active'));
  document.querySelectorAll('.tab-btn').forEach(b=>b.classList.remove('active'));
  // Reset sidebar active state if switching to a main tab
  if(!_hmTabs.includes(tab)){
    document.querySelectorAll('.hm-nav-btn').forEach(b=>b.classList.remove('hm-active'));
  }
  document.getElementById('tab-'+tab).classList.add('active');
  if(e)e.target.classList.add('active');
  const isDaily=tab==='daily';
  if(isDaily){load();renderDailyCheckOverview();}
  document.getElementById('streakBar').style.display=isDaily?'flex':'none';
  // AI Tools now open from ☰ Tools on any tab (and stay open across tabs until closed).
  document.getElementById('aiToolbar').style.display=window.__aiToolsOpen?'block':'none';
  var jc=document.getElementById('journalCalendar');if(jc)jc.style.display=isDaily?'':'none';
  // Show the week / month / year that holds the header's date.
  try{var _n=new Date();weekOff2=Math.round((wkStartOf(today)-wkStartOf(_n))/(7*864e5));monthOff2=(today.getFullYear()*12+today.getMonth())-(_n.getFullYear()*12+_n.getMonth());yearOff2=today.getFullYear()-_n.getFullYear();}catch(eOff){}
  if(tab==='weekly')renderWeekly();if(tab==='monthly')renderMonthly();
  if(tab==='yearly')renderYearly();if(tab==='dashboard')renderDashboard();
  if(tab==='review')renderReview();if(tab==='library'){renderLibrary();renderLibrarySectionCharts();}
  if(tab==='bible')renderBibleTab();
  if(tab==='habits2')h2Render();
  if(tab==='meditate'){meditInitTab();}
  if(tab==='wellness'){var wdl=document.getElementById('wellnessTabDate');if(wdl)wdl.textContent=today.toLocaleDateString('en-US',{weekday:'long',month:'long',day:'numeric',year:'numeric'});renderWellnessSectionCharts();}
  if(tab==='weekly'){renderGoals();}
  localStorage.setItem('last_active_tab',tab);
}
/* ========= PERSONAL JOURNAL ARCHIVE ========= */
