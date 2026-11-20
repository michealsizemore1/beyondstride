var journalCloudClient=null,journalCloudUser=null,journalCloudTimer=null,journalCloudApplying=false,journalCloudBusy=false,journalCloudReady=false,journalCloudPreparedFor='',journalCloudChangeSeq=0;
var journalOriginalFetch=window.fetch.bind(window);
function journalCloudWithTimeout(promise,ms,message){
  var timer;
  return Promise.race([
    promise,
    new Promise(function(resolve,reject){timer=setTimeout(function(){reject(new Error(message||'The connection timed out.'));},ms);})
  ]).finally(function(){clearTimeout(timer);});
}
function journalAIAvailable(){return !!journalAIKey();}
function journalAIKey(){
  try{return String(localStorage.getItem('ai_key')||'').trim();}catch(e){return'';}
}

// ── AI Daily Goals: scan the last 7 days for shortcomings, suggest 3 goals ──
function journalGatherShortfalls(){
  var totals={bible:0,prayer:0,meditation:0,stretch:0,library:0,training:0};
  var days=7;
  for(var i=days-1;i>=0;i--){
    var d=new Date();d.setDate(d.getDate()-i);var key=dk(d),row={};
    try{row=JSON.parse(localStorage.getItem('planner_'+key)||'{}')||{};}catch(e){}
    if(row.spBibleAudio==='green')totals.bible++;
    if(row.spAIPrayer==='green')totals.prayer++;
    if(row.spMeditation==='green')totals.meditation++;
    if(row.exStretch==='green')totals.stretch++;
    if(parseTimedMins(row.gaAudiobookTime)>0||String(row.gaAudiobookText||'').trim())totals.library++;
    try{if(hasActivityOnDate(key))totals.training++;}catch(e){}
  }
  var languageDays={};
  try{
    var since=dk(new Date(Date.now()-6*86400000));
    (getSkillEntries()||[]).forEach(function(entry){if(entry&&entry.date>=since&&entry.category==='Language')languageDays[entry.date]=true;});
  }catch(e){}
  var lines=[
    'Bible reading: '+totals.bible+'/7 days',
    'Prayer: '+totals.prayer+'/7 days',
    'Meditation: '+totals.meditation+'/7 days',
    'Stretching: '+totals.stretch+'/7 days',
    'Training/workouts logged: '+totals.training+'/7 days',
    'Library learning: '+totals.library+'/7 days',
    'Language lessons: '+Object.keys(languageDays).length+'/7 days'
  ];
  try{
    var state=h2State();
    var stats=state.habits.map(function(h){return{name:h.name,rate:h2HabitStats(state,h).rate};}).sort(function(a,b){return a.rate-b.rate;});
    var weakest=stats.slice(0,5).map(function(s){return s.name+' ('+s.rate+'% consistency)';});
    if(weakest.length)lines.push('Weakest tracked habits, 90-day consistency: '+weakest.join(', '));
  }catch(e){}
  return lines.join('\n');
}
function aiSetDailyGoals(force){
  var g0=document.getElementById('dg0t'),g1=document.getElementById('dg1t'),g2=document.getElementById('dg2t');
  if(!g0||!g1||!g2)return;
  var hasExisting=!!(g0.value.trim()||g1.value.trim()||g2.value.trim());
  if(hasExisting&&force&&!confirm('Replace today\'s current goals with new AI-suggested goals?'))return;
  var btn=document.getElementById('aiGoalsBtn'),status=document.getElementById('aiGoalsStatus');
  if(btn){btn.disabled=true;btn.textContent='⏳ Thinking...';}
  if(status){status.textContent='';}
  var prompt='You are a supportive personal accountability coach. Below is a summary of my last 7 days from my personal journal.\n\n'
    +journalGatherShortfalls()
    +'\n\nBased on the areas I am most inconsistent in, suggest exactly 3 specific, actionable goals for TODAY that would help me improve. Each goal must be under 12 words, concrete, and doable today — not vague encouragement. Return ONLY the 3 goals, one per line, no numbering, no bullets, no extra commentary.';
  aiCall(prompt,300,function(text){
    var lines=text.split(/\n+/).map(function(l){return l.replace(/^[\-\*•\d.\)]+\s*/,'').trim();}).filter(Boolean).slice(0,3);
    g0.value=lines[0]||g0.value;g1.value=lines[1]||g1.value;g2.value=lines[2]||g2.value;
    save();
    if(btn){btn.disabled=false;btn.textContent='✨ AI: Suggest Goals';}
    if(status){status.style.color='#16a34a';status.textContent='✓ Suggested '+new Date().toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'});}
    try{localStorage.setItem('ai_goals_auto_'+dk(today),'1');}catch(e){}
  },function(err){
    if(btn){btn.disabled=false;btn.textContent='✨ AI: Suggest Goals';}
    if(status){status.style.color='#b91c1c';status.textContent='Could not generate goals: '+err;}
  });
}
function aiAutoAttemptDailyGoals(){
  try{
    if(dk(today)!==dk(new Date()))return; // only auto-suggest when viewing the real current day
    if(!journalAIAvailable())return;
    if(localStorage.getItem('ai_goals_auto_'+dk(today)))return; // once per real day
    var g0=document.getElementById('dg0t'),g1=document.getElementById('dg1t'),g2=document.getElementById('dg2t');
    if(!g0||!g1||!g2)return;
    if(g0.value.trim()||g1.value.trim()||g2.value.trim())return; // never overwrite goals you already set
    aiSetDailyGoals(false);
  }catch(e){}
}
async function journalProtectedFunction(body){
  var key=journalAIKey();
  if(!key)throw new Error('Add your Anthropic API key in Settings first.');
  var response=await journalCloudWithTimeout(journalOriginalFetch('https://api.anthropic.com/v1/messages',{
    method:'POST',
    headers:{'Content-Type':'application/json','x-api-key':key,'anthropic-version':'2023-06-01','anthropic-dangerous-direct-browser-access':'true'},
    body:JSON.stringify(body||{})
  }),45000,'The Anthropic request timed out. Please try again.');
  var data=await response.json().catch(function(){return{};});
  if(!response.ok)throw new Error((data.error&&data.error.message)||('Anthropic returned error '+response.status+'.'));
  return data;
}
function journalCloudCanSyncKey(k){
  if(!k)return false;
  var lower=String(k).toLowerCase();
  if(journalIsExternalAppKey(k))return false;
  if(lower==='handwriting_2026-07-27'||lower==='handwriting_2026-07-29')return false;
  if(lower==='ai_key'||lower.indexOf('sb-')===0||lower.indexOf('journal_cloud_')===0||lower.indexOf('journal_google_')===0)return false;
  if(lower.indexOf('service_role')>=0||lower.indexOf('sb_secret_')>=0||lower.indexOf('password')>=0)return false;
  return true;
}
async function journalCloudPayload(){
  var out={};
  for(var i=0;i<localStorage.length;i++){
    var k=localStorage.key(i);
    if(journalCloudCanSyncKey(k))out[k]=localStorage.getItem(k);
  }
  try{
    var bibleArchive=_bibleReadingArchiveLargeCache;
    if(!Array.isArray(bibleArchive))bibleArchive=await journalLargeStoreGet('bible_reading_archive')||[];
    if(bibleArchive.length)out.bible_reading_archive=JSON.stringify(bibleArchive);
  }catch(ignore){}
  try{
    var activities=Array.isArray(_activitiesLargeCache)?_activitiesLargeCache:await journalLargeStoreGet('activities_data')||[];
    if(activities.length)out.activities_data=JSON.stringify(activities);
  }catch(ignore2){}
  return out;
}
function journalCloudHasJournalData(){
  if(Array.isArray(_activitiesLargeCache)&&_activitiesLargeCache.length)return true;
  for(var i=0;i<localStorage.length;i++){
    var k=localStorage.key(i)||'';
    if(k.indexOf('planner_')===0||k.indexOf('bible_')===0||k==='activities_data'||k==='library_books'||k==='current_training_plan'||k.indexOf('review_')===0)return true;
  }
  return false;
}
function journalCloudSetStatus(text,state){
  var save=document.getElementById('v26SaveText');if(save)save.textContent=text;
  var status=document.getElementById('cloudSettingsStatus');if(status)status.textContent=text;
  var dot=document.getElementById('cloudSettingsDot');if(dot)dot.className='cloud-status-dot '+(state||'');
}
function journalCloudSchedule(){
  if(journalCloudApplying||!journalCloudUser||!journalCloudReady)return;
  journalCloudChangeSeq++;
  try{localStorage.setItem('journal_cloud_dirty','1');}
  catch(e){journalCloudSetStatus('Storage full — backup recommended','error');return;}
  journalCloudSetStatus('Saved locally — cloud sync pending','busy');
  clearTimeout(journalCloudTimer);
  journalCloudTimer=setTimeout(function(){journalCloudSmartSync();},1800);
}
async function journalCloudUpload(showMessage,expectedRevision){
  if(!journalCloudClient||!journalCloudUser||journalCloudBusy)return false;
  journalCloudBusy=true;journalCloudSetStatus('Synchronizing securely...','busy');
  try{
    var uploadSeq=journalCloudChangeSeq;
    var payload=await journalCloudPayload();
    var values={
      user_id:journalCloudUser.id,
      journal_data:payload,
      last_device:(navigator.platform||'Browser').slice(0,100)
    };
    var result;
    if(typeof expectedRevision==='number'){
      result=await journalCloudClient.from('journal_state').update(values)
        .eq('user_id',journalCloudUser.id).eq('revision',expectedRevision)
        .select('updated_at,revision').maybeSingle();
      if(!result.error&&!result.data){
        var latest=await journalCloudClient.from('journal_state').select('journal_data,updated_at,revision,last_device').eq('user_id',journalCloudUser.id).maybeSingle();
        if(latest.error)throw latest.error;
        journalCloudBusy=false;
        journalCloudShowChoice(
          'Another device changed the journal',
          'The cloud journal changed while this device was preparing its upload. Nothing from this device was overwritten.',
          [
            {label:'Use the newer cloud journal',action:function(){journalCloudApply(latest.data.journal_data,latest.data.updated_at,latest.data.revision);}},
            {label:'Keep this device and overwrite the cloud',secondary:true,action:async function(){journalCloudCloseChoice();await journalCloudUpload(true);}}
          ]
        );
        journalCloudSetStatus('Sync decision needed','error');
        return false;
      }
    }else{
      result=await journalCloudClient.from('journal_state').upsert(values,{onConflict:'user_id'}).select('updated_at,revision').single();
    }
    if(result.error)throw result.error;
    journalCloudApplying=true;
    localStorage.setItem('journal_cloud_last_sync',result.data.updated_at||new Date().toISOString());
    localStorage.setItem('journal_cloud_revision',String(result.data.revision||''));
    if(uploadSeq===journalCloudChangeSeq)localStorage.removeItem('journal_cloud_dirty');
    journalCloudApplying=false;
    journalCloudReady=true;
    if(uploadSeq===journalCloudChangeSeq)journalCloudSetStatus('Saved on this device and cloud','ok');
    else{
      journalCloudSetStatus('New changes waiting to sync','busy');
      clearTimeout(journalCloudTimer);journalCloudTimer=setTimeout(function(){journalCloudSmartSync();},400);
    }
    if(showMessage&&uploadSeq===journalCloudChangeSeq)v26Toast('Cloud sync complete');
    return true;
  }catch(e){
    journalCloudSetStatus(navigator.onLine?'Cloud sync needs attention':'Offline — saved on this device','error');
    if(showMessage)alert('Cloud sync did not complete. Your journal is still safely saved on this device.\n\n'+(e.message||'Please try again.'));
    return false;
  }finally{journalCloudBusy=false;}
}
async function journalCloudApply(payload,updatedAt,revision){
  journalCloudApplying=true;
  var cloudBibleArchive=null,cloudActivities=null;
  Object.keys(payload||{}).forEach(function(k){
    if(k==='bible_reading_archive'){try{cloudBibleArchive=JSON.parse(payload[k]||'[]');}catch(ignore){}return;}
    if(k==='activities_data'){try{cloudActivities=JSON.parse(payload[k]||'[]');}catch(ignore){}return;}
    if(journalCloudCanSyncKey(k))localStorage.setItem(k,String(payload[k]));
  });
  if(Array.isArray(cloudBibleArchive)){
    _bibleReadingArchiveLargeCache=cloudBibleArchive;
    try{await journalLargeStoreSet('bible_reading_archive',cloudBibleArchive);}catch(ignore2){}
  }
  if(Array.isArray(cloudActivities)){
    var localActivitiesForMerge=[];
    try{
      if(Array.isArray(_activitiesLargeCache))localActivitiesForMerge=_activitiesLargeCache;
      else localActivitiesForMerge=await journalLargeStoreGet('activities_data')||[];
    }catch(ignoreLocalAct){}
    try{
      var legacyActivitiesForMerge=JSON.parse(localStorage.getItem('activities_data')||'[]')||[];
      if(Array.isArray(legacyActivitiesForMerge)&&legacyActivitiesForMerge.length)localActivitiesForMerge=localActivitiesForMerge.concat(legacyActivitiesForMerge);
    }catch(ignoreLegacyAct){}
    var mergedActivities=[],seenAct={};
    cloudActivities.concat(localActivitiesForMerge).forEach(function(activity){
      if(!activity)return;
      var id=String(activity.id||[activity.date||'',activity.title||'',activity.type||'',activity.distance||'',activity.duration||''].join('|'));
      if(seenAct[id])return;
      seenAct[id]=true;mergedActivities.push(activity);
    });
    _activitiesLargeCache=mergedActivities;
    try{await journalLargeStoreSet('activities_data',mergedActivities);localStorage.removeItem('activities_data');}catch(ignore3){}
  }
  localStorage.setItem('journal_cloud_last_sync',updatedAt||new Date().toISOString());
  if(revision!=null)localStorage.setItem('journal_cloud_revision',String(revision));
  localStorage.removeItem('journal_cloud_dirty');
  journalCloudApplying=false;
  location.reload();
}
function journalCloudShowChoice(title,text,actions){
  var modal=document.getElementById('cloudFirstSync'),wrap=document.getElementById('cloudFirstSyncActions');
  document.getElementById('cloudFirstSyncTitle').textContent=title;
  document.getElementById('cloudFirstSyncText').textContent=text;
  wrap.innerHTML='';
  actions.forEach(function(a){
    var b=document.createElement('button');b.type='button';b.className='cloud-action'+(a.secondary?' secondary':'');b.textContent=a.label;b.onclick=a.action;wrap.appendChild(b);
  });
  modal.style.display='flex';
}
function journalCloudCloseChoice(){document.getElementById('cloudFirstSync').style.display='none';}
async function journalCloudPrepare(){
  if(!journalCloudUser||journalCloudPreparedFor===journalCloudUser.id)return;
  journalCloudPreparedFor=journalCloudUser.id;
  journalCloudSetStatus('Checking cloud journal...','busy');
  var result=await journalCloudClient.from('journal_state').select('journal_data,updated_at,revision,last_device').eq('user_id',journalCloudUser.id).maybeSingle();
  if(result.error){journalCloudPreparedFor='';journalCloudSetStatus('Cloud connection needs attention','error');return;}
  var cloud=result.data,hasLocal=journalCloudHasJournalData(),lastSync=localStorage.getItem('journal_cloud_last_sync');
  if(!cloud){
    journalCloudShowChoice(
      'Set up your first cloud copy',
      hasLocal?'This device contains journal information. Upload it as the first private cloud copy. Nothing will be removed from this device.':'No cloud journal exists yet. Create your private cloud copy from this device.',
      [{label:'Upload this device to the private cloud',action:async function(){journalCloudCloseChoice();await journalCloudUpload(true);}}]
    );
    journalCloudSetStatus('Waiting for first cloud upload','busy');return;
  }
  if(!hasLocal){journalCloudApply(cloud.journal_data,cloud.updated_at,cloud.revision);return;}
  if(!lastSync){
    journalCloudShowChoice(
      'Journal information exists in both places',
      'For safety, choose which copy should become the current journal. Neither copy will be changed until you choose.',
      [
        {label:'Keep this device and upload it to the cloud',action:async function(){journalCloudCloseChoice();await journalCloudUpload(true);}},
        {label:'Use the existing cloud journal on this device',secondary:true,action:function(){journalCloudApply(cloud.journal_data,cloud.updated_at,cloud.revision);}}
      ]
    );return;
  }
  var cloudTime=new Date(cloud.updated_at).getTime(),localTime=new Date(lastSync).getTime();
  if(cloudTime>localTime+1000){
    if(localStorage.getItem('journal_cloud_dirty')){
      journalCloudShowChoice(
        'Newer cloud copy detected',
        'This device also has unsynchronized changes. Choose which copy should win.',
        [
          {label:'Get the newer cloud journal',action:function(){journalCloudApply(cloud.journal_data,cloud.updated_at,cloud.revision);}},
          {label:'Keep this device and upload it instead',secondary:true,action:async function(){journalCloudCloseChoice();await journalCloudUpload(true);}}
        ]
      );return;
    }
    journalCloudApply(cloud.journal_data,cloud.updated_at,cloud.revision);return;
  }
  journalCloudReady=true;
  if(localStorage.getItem('journal_cloud_dirty'))await journalCloudUpload(false,cloud.revision);
  else journalCloudSetStatus('Saved on this device and cloud','ok');
}
async function journalCloudSignOut(){
  if(!journalCloudClient)return;
  if(!confirm('Sign out of private cloud sync on this device? Your local journal information will remain on this device.'))return;
  await journalCloudClient.auth.signOut();location.reload();
}
async function journalCloudSmartSync(){
  if(!journalCloudUser){alert('Sign in to use cloud sync.');return;}
  if(journalCloudBusy)return;
  var btn=document.getElementById('journalCloudSyncBtn');
  if(btn){btn.disabled=true;btn.textContent='Checking...';}
  journalCloudBusy=true;journalCloudSetStatus('Checking both devices and cloud...','busy');
  try{
    var result=await journalCloudClient.from('journal_state').select('journal_data,updated_at,revision,last_device').eq('user_id',journalCloudUser.id).maybeSingle();
    if(result.error)throw result.error;
    if(!result.data){
      journalCloudBusy=false;
      await journalCloudUpload(true);
      return;
    }
    var cloudTime=new Date(result.data.updated_at).getTime();
    var lastSync=localStorage.getItem('journal_cloud_last_sync');
    var localTime=lastSync?new Date(lastSync).getTime():0;
    var dirty=localStorage.getItem('journal_cloud_dirty')==='1';
    if(cloudTime>localTime+1000){
      if(dirty){
        journalCloudShowChoice(
          'Changes found in both places',
          'This device and the cloud both changed since the last sync. Choose which copy should become current.',
          [
            {label:'Use the newer cloud journal',action:function(){journalCloudApply(result.data.journal_data,result.data.updated_at,result.data.revision);}},
            {label:'Keep this device and upload it instead',secondary:true,action:async function(){journalCloudCloseChoice();journalCloudBusy=false;await journalCloudUpload(true);}}
          ]
        );
      }else{
        journalCloudApply(result.data.journal_data,result.data.updated_at,result.data.revision);
      }
      return;
    }
    if(dirty){
      journalCloudBusy=false;
      await journalCloudUpload(true,result.data.revision);
      return;
    }
    journalCloudSetStatus('Synced just now','ok');
    v26Toast('Journal is up to date');
    setTimeout(function(){if(!localStorage.getItem('journal_cloud_dirty'))journalCloudSetStatus('Saved on this device and cloud','ok');},2400);
  }catch(e){
    journalCloudSetStatus(navigator.onLine?'Sync check needs attention':'Offline — saved on this device','error');
    alert('The sync check did not complete. Your journal remains safely saved on this device.');
  }finally{
    journalCloudBusy=false;
    if(btn){btn.disabled=false;btn.innerHTML='Sync';}
  }
}
function journalCloudInit(){
  journalCloudClient=null;journalCloudUser=null;journalCloudReady=false;
  var localStatus=document.getElementById('v26SaveText');if(localStatus)localStatus.textContent='Saved on this device';
  try{hydrateBibleReadingArchiveStorage().then(function(){compactBibleReadingArchiveStorage();});}catch(ignoreBible){}
  try{hydrateActivitiesStorage();}catch(ignoreActivities){}
}

/* v26 quality-of-life enhancements. Existing storage keys and data remain unchanged. */
function closeHeaderTools(){
  var menu=document.getElementById('headerToolsMenu');if(menu)menu.open=false;
}
function meridianWeatherIcon(code){
  if(code===0)return'&#x2600;&#xFE0F;';
  if(code<=3)return'&#x26C5;';
  if(code===45||code===48)return'&#x1F32B;&#xFE0F;';
  if((code>=51&&code<=67)||(code>=80&&code<=82))return'&#x1F327;&#xFE0F;';
  if(code>=71&&code<=77)return'&#x1F328;&#xFE0F;';
  if(code>=95)return'&#x26C8;&#xFE0F;';
  return'&#x1F321;&#xFE0F;';
}
async function updateMeridianWeather(){
  var display=document.getElementById('meridianWeather');if(!display)return;
  try{
    var response=await fetch('https://api.open-meteo.com/v1/forecast?latitude=43.6121&longitude=-116.3915&current=temperature_2m,weather_code&daily=precipitation_probability_max&forecast_days=1&temperature_unit=fahrenheit&timezone=America%2FBoise',{cache:'no-store'});
    if(!response.ok)throw new Error('Weather unavailable');
    var data=await response.json(),current=data&&data.current;
    if(!current||!Number.isFinite(Number(current.temperature_2m)))throw new Error('Weather unavailable');
    var rainChance=data&&data.daily&&Array.isArray(data.daily.precipitation_probability_max)?Number(data.daily.precipitation_probability_max[0]):NaN;
    var rainLabel=Number.isFinite(rainChance)?' \u2022 \u2614 '+Math.round(rainChance)+'%':'';
    display.innerHTML=meridianWeatherIcon(Number(current.weather_code))+' Meridian '+Math.round(Number(current.temperature_2m))+'&deg;'+rainLabel;
    display.title='Current temperature for Meridian, Idaho'+(Number.isFinite(rainChance)?' \u2022 Today\u2019s chance of precipitation: '+Math.round(rainChance)+'%':'')+(current.time?' \u2022 Updated '+current.time.replace('T',' '):'')+' \u2022 Weather: Open-Meteo';
  }catch(error){
    display.innerHTML='&#x1F321;&#xFE0F; Meridian &mdash;&deg;';
    display.title='Current Meridian temperature is temporarily unavailable';
  }
}
function meridianAqiColor(value){if(value<=50)return'#22c55e';if(value<=100)return'#eab308';if(value<=150)return'#f97316';if(value<=200)return'#ef4444';if(value<=300)return'#a855f7';return'#7f1d1d';}
async function updateMeridianAQI(retry){
  var wrap=document.getElementById('meridianAqi'),text=document.getElementById('meridianAqiText'),dot=document.getElementById('meridianAqiDot');if(!wrap||!text||!dot)return;
  if(!journalCloudUser){if((retry||0)<5)setTimeout(function(){updateMeridianAQI((retry||0)+1);},1800);return;}
  try{
    var data=await journalProtectedFunction({action:'airnow-aqi'}),aqi=Math.round(Number(data.aqi));if(!Number.isFinite(aqi))throw new Error('No current AQI');
    text.textContent='Meridian AQI '+aqi+' · '+String(data.category||'');dot.style.background=meridianAqiColor(aqi);wrap.href=data.sourceUrl||wrap.href;wrap.title='Official monitor-based AQI · '+String(data.pollutant||'AQI')+' · '+String(data.reportingArea||'Meridian area')+(data.observedDate?' · Observed '+data.observedDate+' '+data.observedHour+':00':'')+' · Source: EPA AirNow / Idaho DEQ';
    try{localStorage.setItem('meridian_airnow_cache',JSON.stringify({data:data,savedAt:Date.now()}));}catch(ignore){}
  }catch(error){
    console.warn('Meridian AQI refresh failed:',error&&error.message||error);
    var cached=null;try{cached=JSON.parse(localStorage.getItem('meridian_airnow_cache')||'null');}catch(ignore2){}
    if(cached&&cached.data&&Number.isFinite(Number(cached.data.aqi))){var age=Math.round((Date.now()-Number(cached.savedAt||0))/3600000);text.textContent='Meridian AQI '+Math.round(Number(cached.data.aqi))+' · cached';dot.style.background=meridianAqiColor(Number(cached.data.aqi));wrap.title='Last official EPA AirNow / Idaho DEQ reading · cached '+age+' hour'+(age===1?'':'s')+' ago · Refresh failed: '+((error&&error.message)||'unknown error');}
    else{text.textContent='Meridian AQI unavailable';dot.style.background='#94a3b8';wrap.title=(error&&error.message)||'Official AQI is temporarily unavailable';}
  }
}
function journalSyncFixedHeaderHeight(){
  document.documentElement.style.removeProperty('--journal-sticky-height');
  document.documentElement.style.scrollPaddingTop='';
}
function journalInitFixedHeader(){
  journalSyncFixedHeaderHeight();
  var spacer=document.getElementById('journalStickySpacer');
  if(spacer)spacer.style.display='none';
}
function journalInitFloatingTitle(){
  var original=document.querySelector('.header h1');
  var floating=document.getElementById('journalFloatingTitle');
  if(!original||!floating)return;
  function update(){
    var rect=original.getBoundingClientRect();
    var show=rect.bottom<=8||rect.top>=window.innerHeight;
    floating.classList.toggle('show',show);
    floating.setAttribute('aria-hidden',show?'false':'true');
  }
  update();
  window.addEventListener('scroll',update,{passive:true});
  window.addEventListener('resize',update);
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',journalInitFixedHeader);
else journalInitFixedHeader();
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',journalInitFloatingTitle);
else journalInitFloatingTitle();
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',updateMeridianWeather);
else updateMeridianWeather();
setInterval(updateMeridianWeather,15*60*1000);
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){updateMeridianAQI(0);});
else updateMeridianAQI(0);
setInterval(function(){updateMeridianAQI(0);},30*60*1000);

function v26Toast(message){
  var el=document.getElementById('v26Toast');if(!el)return;
  el.textContent=message;el.classList.add('show');
  clearTimeout(window._v26ToastTimer);window._v26ToastTimer=setTimeout(function(){el.classList.remove('show');},1800);
}
function v26GoToday(){
  today=new Date();
  var dailyButton=document.querySelector('.tab-btn[onclick*="switchTab(\'daily\'"]');
  if(dailyButton&& !document.getElementById('tab-daily').classList.contains('active'))dailyButton.click();
  else if(typeof load==='function')load();
  if(typeof renderTodayWorkoutCard==='function')renderTodayWorkoutCard();
  window.scrollTo({top:0,behavior:'smooth'});
  v26Toast('Showing today — '+today.toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'}));
}
function v26ToggleDensity(){
  var compact=document.body.classList.contains('v26-compact');
  document.body.classList.toggle('v26-compact',!compact);
  document.body.classList.toggle('v26-comfortable',compact);
  localStorage.setItem('v26_density',compact?'roomy':'compact');
  localStorage.removeItem('v26_roomy');
  var b=document.getElementById('v26DensityBtn');if(b){b.textContent=compact?'Relaxed':'Tight';b.title=compact?'Relaxed spacing adds breathing room between journal sections. Click for tight spacing.':'Tight spacing fits more journal content on screen. Click for relaxed spacing.';b.setAttribute('aria-label',compact?'Page spacing: Relaxed. Click for tight spacing.':'Page spacing: Tight. Click for relaxed spacing.');b.setAttribute('aria-pressed',compact?'false':'true');}
  v26Toast(compact?'Relaxed spacing — more breathing room':'Tight spacing — more content on screen');
}
function v26SavedFeedback(){
  var el=document.getElementById('v26SaveText');if(!el)return;
  el.textContent='Saving...';clearTimeout(window._v26SaveTimer);
  window._v26SaveTimer=setTimeout(function(){el.textContent=journalCloudUser?'Saved locally — cloud sync pending':'Saved on this device';},450);
}
window.addEventListener('DOMContentLoaded',function(){
  journalCreateAutomaticBackup(false);
  checkCalendarReminders();
  setInterval(checkCalendarReminders,20000);
  document.addEventListener('visibilitychange',function(){if(!document.hidden)checkCalendarReminders();});
  journalCloudInit();
  relocateDashboardCharts();
  makeHabitsInformational();
  var density=localStorage.getItem('v26_density')||'compact';
  document.body.classList.add(density==='roomy'?'v26-comfortable':'v26-compact');
  var d=document.getElementById('v26DensityBtn');if(d){d.textContent=density==='roomy'?'Relaxed':'Tight';d.title=density==='roomy'?'Relaxed spacing adds breathing room between journal sections. Click for tight spacing.':'Tight spacing fits more journal content on screen. Click for relaxed spacing.';d.setAttribute('aria-label',density==='roomy'?'Page spacing: Relaxed. Click for tight spacing.':'Page spacing: Tight. Click for relaxed spacing.');d.setAttribute('aria-pressed',density==='compact'?'true':'false');}
  renderJournalHome();
  refreshJournalNavStatus();
  window.addEventListener('scroll',function(){var top=document.getElementById('journalFloatTop');if(top)top.style.display=window.scrollY>650?'block':'none';},{passive:true});
  document.addEventListener('input',v26SavedFeedback,{passive:true});
  document.addEventListener('change',v26SavedFeedback,{passive:true});
  document.addEventListener('keydown',function(e){
    var tag=(e.target&&e.target.tagName)||'';var typing=/INPUT|TEXTAREA|SELECT/.test(tag);
    if(!typing&&e.key.toLowerCase()==='t'){e.preventDefault();v26GoToday();}
  });
});
