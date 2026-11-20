function copyItemsToNextDay(ids,confirmId){
  save();
  var tomorrow=new Date(today);tomorrow.setDate(tomorrow.getDate()+1);
  var tKey='planner_'+dk(tomorrow);
  var tData={};try{tData=JSON.parse(localStorage.getItem(tKey)||'{}');}catch(e){}
  var copied=0;
  ids.forEach(function(id){
    var el=document.getElementById(id+'t');
    var txt=el?el.value.trim():'';
    var chk=document.querySelector('[data-key="'+id+'c"]');
    var state=chk?chk.dataset.state||'':'';
    if(!txt||state==='green')return; // skip empty or already completed
    if(!tData[id+'t']||!tData[id+'t'].trim()){tData[id+'t']=txt;}
    else{tData[id+'t']=tData[id+'t']+'\n'+txt;}
    tData[id+'c']='';
    copied++;
  });
  if(!copied){alert('Nothing to copy — all items are either empty or already completed.');return;}
  localStorage.setItem(tKey,JSON.stringify(tData));
  var conf=document.getElementById(confirmId);
  if(conf){conf.style.display='inline';setTimeout(function(){conf.style.display='none';},2500);}
}
function askCopyDate(defaultDate){
  var value=prompt('Copy to which date?\n\nEnter as YYYY-MM-DD:',dk(defaultDate));
  if(value===null)return null;
  value=value.trim();
  if(!/^\d{4}-\d{2}-\d{2}$/.test(value)||isNaN(new Date(value+'T12:00:00').getTime())){alert('Please enter a valid date as YYYY-MM-DD.');return null;}
  return value;
}
function copyItemsToDay(ids,confirmId){
  save();
  var tomorrow=new Date(today);tomorrow.setDate(tomorrow.getDate()+1);
  var dateKey=askCopyDate(tomorrow);if(!dateKey)return;
  var tKey='planner_'+dateKey,tData={};try{tData=JSON.parse(localStorage.getItem(tKey)||'{}');}catch(e){}
  var copied=0;
  ids.forEach(function(id){
    var el=document.getElementById(id+'t'),txt=el?el.value.trim():'';
    var chk=document.querySelector('[data-key="'+id+'c"]'),state=chk?chk.dataset.state||'':'';
    if(!txt||state==='green')return;
    if(!tData[id+'t']||!tData[id+'t'].trim())tData[id+'t']=txt;
    else tData[id+'t']+='\n'+txt;
    tData[id+'c']='';copied++;
  });
  if(!copied){alert('Nothing to copy — all tasks are empty or completed.');return;}
  localStorage.setItem(tKey,JSON.stringify(tData));
  var conf=document.getElementById(confirmId);if(conf){conf.textContent='Copied to '+dateKey+'!';conf.style.display='inline';setTimeout(function(){conf.style.display='none';},3000);}
}
function togglePdfMenu(){document.getElementById('pdfMenu').classList.toggle('open');}
document.addEventListener('click',e=>{var menu=document.getElementById('pdfMenu');if(menu&&!e.target.closest('.pdf-wrap'))menu.classList.remove('open');});
document.addEventListener('click',e=>{if(!e.target.closest('.header-tools-menu'))closeHeaderTools();});
function printView(tab){document.querySelectorAll('.tab-panel').forEach(p=>p.classList.remove('active'));if(tab==='weekly')renderWeekly();if(tab==='monthly')renderMonthly();document.getElementById('tab-'+tab).classList.add('active');setTimeout(window.print,200);}
function exportBackup(){
  const d={};
  for(let i=0;i<localStorage.length;i++){
    const k=localStorage.key(i);
    if(k&&(!window.journalCloudCanSyncKey||journalCloudCanSyncKey(k)))d[k]=localStorage.getItem(k);
  }
  Promise.all([getAllBookDocuments(),getAllBookCovers(),journalLargeStoreGet('bible_reading_archive').catch(function(){return[];}),journalLargeStoreGet('activities_data').catch(function(){return getActivities();})]).then(function(results){
    if(results[0]&&results[0].length)d['__idb_book_documents']=JSON.stringify(results[0]);
    if(results[1]&&results[1].length)d['__idb_book_covers']=JSON.stringify(results[1]);
    if(results[2]&&results[2].length)d['__idb_bible_reading_archive']=JSON.stringify(results[2]);
    if(results[3]&&results[3].length)d['__idb_activities_data']=JSON.stringify(results[3]);
    var json=JSON.stringify(d,null,2);
    var filename='planner_backup_'+new Date().toISOString().slice(0,10)+'.json';
    // Use File System Access API (Save As dialog) if available
    if(window.showSaveFilePicker){
      window.showSaveFilePicker({suggestedName:filename,types:[{description:'JSON Backup',accept:{'application/json':['.json']}}]})
      .then(function(fh){return fh.createWritable();})
      .then(function(w){return w.write(json).then(function(){return w.close();});})
      .then(function(){localStorage.setItem('last_backup_ts',new Date().toISOString());})
      .catch(function(err){if(err.name!=='AbortError'){fallbackDownload(json,filename);}});
    } else {
      fallbackDownload(json,filename);
    }
  }).catch(function(){
    var json=JSON.stringify(d,null,2);
    fallbackDownload(json,'planner_backup_'+new Date().toISOString().slice(0,10)+'.json');
  });
}
function fallbackDownload(content,filename){
  var a=document.createElement('a');
  a.href=URL.createObjectURL(new Blob([content],{type:'application/json'}));
  a.download=filename;a.click();
  localStorage.setItem('last_backup_ts',new Date().toISOString());
}

// ── Backup Reminder ──────────────────────────────────────────────────────────
function dismissBackupReminder(){
  var snoozeUntil=new Date();snoozeUntil.setDate(snoozeUntil.getDate()+3);
  localStorage.setItem('backup_reminder_snooze',snoozeUntil.toISOString());
  var bar=document.getElementById('backupReminderBar');if(bar)bar.style.display='none';
}
function checkBackupReminder(){
  var bar=document.getElementById('backupReminderBar');if(!bar)return;
  var snooze=localStorage.getItem('backup_reminder_snooze');
  if(snooze&&new Date(snooze)>new Date())return;
  var lastTs=localStorage.getItem('last_backup_ts');
  var msg;
  if(!lastTs){
    msg="You haven't backed up your journal yet.";
  } else {
    var days=Math.floor((Date.now()-new Date(lastTs).getTime())/86400000);
    var reminderDays=parseInt(journalPrefs().backupDays||7,10);
    if(days<reminderDays)return;
    msg='Your last backup was '+days+' days ago.';
  }
  var txt=document.getElementById('backupReminderText');if(txt)txt.textContent=msg;
  bar.style.display='flex';
}
function importBackup(ev){
  const file=ev.target.files[0];if(!file)return;
  const reader=new FileReader();
  reader.onload=e=>{
    try{
      const d=JSON.parse(e.target.result);
      const plannerCount=Object.keys(d).filter(function(k){return k.indexOf('planner_')===0;}).length;
      const activityCount=(()=>{try{return JSON.parse(d['__idb_activities_data']||d.activities_data||'[]').length;}catch(e2){return 0;}})();
      if(!confirm('Restore preview:\n\n'+plannerCount+' daily entries\n'+activityCount+' activities\n'+Object.keys(d).length+' saved data groups\n\nOverwrite matching current data with this backup?'))return;
      Object.entries(d).forEach(([k,v])=>{if(k==='bible_reading_archive'||k==='activities_data')return;if(!k.startsWith('__idb_')&&(!window.journalCloudCanSyncKey||journalCloudCanSyncKey(k)))localStorage.setItem(k,v);});
      var docPromises=[];var covPromises=[];
      try{if(d['__idb_book_documents']){var docs=JSON.parse(d['__idb_book_documents']);docPromises=docs.map(function(doc){return putBookDocument(doc);});}}catch(e2){}
      try{if(d['__idb_book_covers']){var covs=JSON.parse(d['__idb_book_covers']);covPromises=covs.map(function(c){return setBookCover(c.bookId,c.dataUrl);});}}catch(e3){}
      try{
        var restoredBibleArchive=JSON.parse(d['__idb_bible_reading_archive']||d.bible_reading_archive||'[]');
        if(Array.isArray(restoredBibleArchive)&&restoredBibleArchive.length){_bibleReadingArchiveLargeCache=restoredBibleArchive;docPromises.push(journalLargeStoreSet('bible_reading_archive',restoredBibleArchive));}
      }catch(e4){}
      try{
        var restoredActivities=JSON.parse(d['__idb_activities_data']||d.activities_data||'[]');
        if(Array.isArray(restoredActivities)){_activitiesLargeCache=restoredActivities;docPromises.push(journalLargeStoreSet('activities_data',restoredActivities));localStorage.removeItem('activities_data');}
      }catch(e5){}
      if(docPromises.length||covPromises.length){
        Promise.all(docPromises.concat(covPromises)).then(function(){
          load();alert('Data restored! ('+docPromises.length+' document(s) and '+covPromises.length+' cover(s) included)');
        }).catch(function(){load();alert('Data restored (some library items may not have fully restored).');});
      }else{load();alert('Data restored successfully!');}
    }catch(err){alert('Invalid backup file.');}
  };
  reader.readAsText(file);ev.target.value='';
}


// ── Activities ────────────────────────────────────────────
let editingActivityId=null;
var compareMode=false;
var compareSelected=new Set();
function getActivitiesOwn(){
  if(Array.isArray(_activitiesLargeCache))return _activitiesLargeCache;
  try{const r=localStorage.getItem('activities_data');return r?JSON.parse(r):[];}catch(e){return[];}
}
// BeyondStride's activities (read-only, shared by the phone) are shown alongside the ones saved here.
// They're never written into this page's own list; one already here (same day, same distance) is not repeated.
var _bsActsCache=null,_bsActsRaw=null;
function bsActivities(){
  var raw=null;try{raw=localStorage.getItem('bs_activities');}catch(e){}
  if(raw===_bsActsRaw&&_bsActsCache)return _bsActsCache;
  _bsActsRaw=raw;var list=[];try{var o=JSON.parse(raw||'null');list=(o&&o.list)||[];}catch(e2){}
  _bsActsCache=list.map(function(a){var c=Object.assign({},a);c._bs=true;c.id='bs-'+(a.id||a.date+'-'+a.distance);return c;});
  return _bsActsCache;
}
function getActivities(){
  var own=getActivitiesOwn(),bs=bsActivities();if(!bs.length)return own;
  function key(a){return String(a.date||'').slice(0,10);}
  var extra=bs.filter(function(b){var m=parseFloat(b.distance)||0;return !own.some(function(a){if(key(a)!==key(b))return false;var d=parseFloat(a.distance)||0;return (m<=0&&d<=0&&String(a.type||'')===String(b.type||''))||(m>0&&Math.abs(d-m)/m<0.05);});});
  return extra.length?own.concat(extra):own;
}
function saveActivities(a){
  if(Array.isArray(a))a=a.filter(function(x){return !(x&&x._bs);});
  _activitiesLargeCache=Array.isArray(a)?a:[];
  journalLargeStoreSet('activities_data',_activitiesLargeCache).then(function(){
    try{localStorage.removeItem('activities_data');}catch(ignore){}
    if(journalCloudUser&&journalCloudReady){
      try{localStorage.setItem('journal_cloud_dirty','1');journalCloudSchedule();}
      catch(ignore2){setTimeout(function(){journalCloudUpload(false);},0);}
    }
  }).catch(function(err){
    console.error('Large activity storage error:',err);
    alert('The activity could not be stored on this device. Please use Backup and try again.');
  });
}


function parseDurationSecLoose(s){
  s=(s||'').trim();if(!s)return 0;
  var m=s.match(/^(\d+):(\d{1,2}):(\d{1,2})$/);if(m)return(+m[1])*3600+(+m[2])*60+(+m[3]);
  m=s.match(/^(\d+):(\d{1,2})$/);if(m)return(+m[1])*60+(+m[2]);
  m=s.match(/^(\d+(?:\.\d+)?)\s*(?:min|minutes?|m)$/i);if(m)return Math.round(parseFloat(m[1])*60);
  var n=parseFloat(s);return(isNaN(n)||n<=0)?0:Math.round(n*60);
}
// Weight logged in the Wellness tab for a given day (or the most recent
// day before it, up to 30 days back). Returns 0 if nothing is logged.
function wellnessWeightForDate(dateKey){
  if(!dateKey)return 0;
  for(var offset=0;offset<30;offset++){
    var d=new Date(dateKey+'T12:00:00');if(isNaN(d))return 0;d.setDate(d.getDate()-offset);
    try{var w=parseFloat(JSON.parse(localStorage.getItem('planner_'+dk(d))||'{}').wWghtVal);if(w>70&&w<600)return w;}catch(e){}
  }
  return 0;
}
// Keeps the activity form's "Your weight" field in step with the Wellness
// weight for the activity's date, so calorie estimates use that day's weight.
function getActWeightKg(dateKey){
  var lbs=wellnessWeightForDate(dateKey)||parseFloat(localStorage.getItem('act_weight_lbs'))||160;
  return lbs*0.453592;
}
function estimateCalories(a){
  var durSec=parseDurationSecLoose(a.duration);
  var hours=durSec/3600;
  if(!hours)return 0;
  var type=a.type||'Run';
  if(type==='Rest Day')return 0;
  var weightKg=getActWeightKg(a.date);
  var distMi=parseFloat(a.distance)||0;
  var speedMph=distMi>0?distMi/hours:0;
  var met=5;
  if(type==='Run'||type==='Walk'){
    var speedMmin=speedMph*26.8224;
    var distM=distMi*1609.34,elevM=(parseFloat(a.elevGain)||0)*0.3048;
    var grade=distM>0?elevM/distM:0;
    if(speedMmin>0){
      var vo2=type==='Run'?(0.2*speedMmin+0.9*speedMmin*grade+3.5):(0.1*speedMmin+1.8*speedMmin*grade+3.5);
      met=vo2/3.5;
    }else{
      met=type==='Run'?8:3.5;
    }
  }else if(type==='Ride'){
    var power=parseFloat(a.power)||0;
    if(power>0){
      // ~3.6 kcal of metabolic cost per watt-hour of mechanical output (~24% gross cycling efficiency)
      return Math.round(power*hours*3.6);
    }
    met=speedMph>0?(speedMph<10?4:speedMph<14?6:speedMph<16?8:speedMph<20?10:12):8;
  }else if(type==='Swim'){
    met=7;
  }else if(type==='Strength'){
    met=5;
  }
  return Math.max(0,Math.round(met*weightKg*hours));
}
function syncWeeklyMilesGoal(val){
  var num=parseFloat(val)||0;
  var g=JSON.parse(localStorage.getItem('weekly_goals')||'{}');
  if(num>0)g.Miles=num;else delete g.Miles;
  localStorage.setItem('weekly_goals',JSON.stringify(g));
  var actEl=document.getElementById('actGoal_week');if(actEl)actEl.value=num>0?String(num):'';
  var gEl=document.getElementById('gMiles');if(gEl)gEl.value=num>0?String(num):'';
  if(typeof renderGoals==='function')renderGoals();
}
// One-time-per-load reconciliation for anyone who already had two different
// values saved before these were linked: the Activities card's value wins
// (matching what's shown there), so both places agree immediately.
function reconcileWeeklyMilesGoal(){
  var actVal=localStorage.getItem('act_goal_week_mi');
  var g=JSON.parse(localStorage.getItem('weekly_goals')||'{}');
  if(actVal){
    if((parseFloat(g.Miles)||0)!==(parseFloat(actVal)||0))syncWeeklyMilesGoal(actVal);
  }else if(g.Miles){
    localStorage.setItem('act_goal_week_mi',String(g.Miles));
  }
}
window.addEventListener('load',function(){reconcileWeeklyMilesGoal();});
function toggleActSources(){
  var p=document.getElementById('actSourcesPanel');
  var c=document.getElementById('actSourcesChev');
  if(!p)return;
  var open=p.style.display!=='none';
  p.style.display=open?'none':'block';
  if(c)c.innerHTML=open?'&#x25B6;':'&#x25BC;';
}

function copyTrainingPlan(){
  const t=document.getElementById('tpOutputText');
  if(!t)return;
  navigator.clipboard.writeText(t.textContent).then(()=>alert('Plan copied to clipboard!')).catch(()=>{});
}

function tpCalcWeeks(){
  const rd=document.getElementById('tpRaceDate');
  const wk=document.getElementById('tpWeeks');
  if(!rd||!wk||!rd.value)return;
  const today2=new Date();today2.setHours(0,0,0,0);
  const race=new Date(rd.value+'T00:00:00');
  const weeks=Math.round((race-today2)/(7*86400000));
  if(weeks<=0){rd.setCustomValidity('Race date must be in the future');return;}
  rd.setCustomValidity('');
  // Pick closest option or add custom
  const opts=[4,6,8,10,12,16,20];
  const closest=opts.reduce((a,b)=>Math.abs(b-weeks)<Math.abs(a-weeks)?b:a);
  // Add exact option if not present
  let found=false;
  Array.from(wk.options).forEach(o=>{if(parseInt(o.value)===weeks){o.selected=true;found=true;}});
  if(!found){
    const opt=document.createElement('option');
    opt.value=weeks;opt.text=weeks+' weeks';opt.selected=true;
    wk.appendChild(opt);
  }
}
function trainingPlanDateFromLine(line,fallbackYear){
  var clean=String(line||'').replace(/^[#*\s>-]+/,'').trim(),m,date=null;
  if(!clean||/^week\s*\d+/i.test(clean))return null;
  m=clean.match(/\b(20\d{2})-(\d{1,2})-(\d{1,2})\b/);
  if(m)date=new Date(+m[1],+m[2]-1,+m[3]);
  if(!date){m=clean.match(/(?:^|\s)(\d{1,2})[\/-](\d{1,2})[\/-](\d{2,4})(?:\s|$|[:–—-])/);if(m){var y=+m[3];if(y<100)y+=2000;date=new Date(y,+m[1]-1,+m[2]);}}
  if(!date&&/\b(sun(?:day)?|mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?)\b/i.test(clean)){
    var months={jan:0,january:0,feb:1,february:1,mar:2,march:2,apr:3,april:3,may:4,jun:5,june:5,jul:6,july:6,aug:7,august:7,sep:8,sept:8,september:8,oct:9,october:9,nov:10,november:10,dec:11,december:11};
    m=clean.match(/\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\s+(\d{1,2})(?:,?\s+(20\d{2}))?/i);
    if(m)date=new Date(+(m[3]||fallbackYear),months[m[1].toLowerCase()],+m[2]);
  }
  return date&&!isNaN(date.getTime())?dk(date):null;
}
function buildUploadedTrainingSchedule(text,startDate,weeks){
  var lines=String(text||'').split(/\r?\n/),headers=[],fallbackYear=new Date((startDate||dk(new Date()))+'T00:00:00').getFullYear();
  // A one-off metadata line like "Start date: Sunday, November 1, 2026" or a
  // "RACE DAY:" label with the date on the next line both contain a real date,
  // so trainingPlanDateFromLine() matches them -- but they aren't a per-day
  // schedule entry. Without this guard, a plan with no other dated day headers
  // collapses into a single giant "day" that swallows the entire rest of the
  // plan text under that one date, and every other real day falls back to
  // "Rest Day". Skip any date match that's a metadata label (same line) or
  // immediately follows one (date alone on the next line).
  var metaLabelRe=/^\s*(start\s*date|plan\s*start|race\s*day|target\s*date|finish\s*date)\s*:?\s*$/i;
  lines.forEach(function(line,index){
    var key=trainingPlanDateFromLine(line,fallbackYear);
    if(!key)return;
    if(/^\s*(start\s*date|plan\s*start|race\s*day|target\s*date|finish\s*date)\s*:/i.test(line))return;
    var prevNonEmpty=null;
    for(var p=index-1;p>=0;p--){if(lines[p].trim()!==''){prevNonEmpty=lines[p];break;}}
    if(prevNonEmpty&&metaLabelRe.test(prevNonEmpty))return;
    headers.push({index:index,key:key});
  });
  var schedule={};
  headers.forEach(function(header,index){
    var end=index+1<headers.length?headers[index+1].index:lines.length;
    var sectionLines=lines.slice(header.index,end);
    for(var si=1;si<sectionLines.length;si++){if(/^\s*week\s+\d+\b/i.test(sectionLines[si])){sectionLines=sectionLines.slice(0,si);break;}}
    var section=sectionLines.join('\n').trim();
    if(section)schedule[header.key]=section;
  });
  if(Object.keys(schedule).length)return{schedule:schedule,mode:'dated'};
  var base={text:text,startDate:startDate},days=Math.max(7,(weeks||1)*7),start=new Date(startDate+'T00:00:00');
  for(var i=0;i<days;i++){
    var date=new Date(start);date.setDate(start.getDate()+i);
    var section=extractWorkoutFromActivePlan(base,date,false);
    if(section)schedule[dk(date)]=section;
  }
  return{schedule:schedule,mode:'weekly'};
}
// Extracts the text layer from a PDF using pdf.js (loaded in daily-planner.html).
// Only works for PDFs that have real selectable text -- a scanned/photographed
// page with no text layer will come back empty, which is surfaced to the user
// as an error asking them to use a .txt file instead.
// Reads a File as a base64 data: URL. Used to keep a copy of an uploaded
// PDF's original bytes so it can be opened later and viewed exactly as it
// looked in the PDF, regardless of how well the text extraction below reads.
function toggleAIGenPanel(){
  const panel=document.getElementById('aiGenPanel');
  const arrow=document.getElementById('aiGenArrow');
  if(!panel)return;
  const open=panel.style.display!=='none';
  panel.style.display=open?'none':'block';
  if(arrow)arrow.innerHTML=open?'&#x25B6;':'&#x25BC;';
}
function cancelTrainingPlan(){
  if(window._tpController)window._tpController.abort();
  clearInterval(window._tpCountdownInterval);
  const btn=document.getElementById('tpGenBtn');
  const cancelBtn=document.getElementById('tpCancelBtn');
  const countdown=document.getElementById('tpCountdown');
  if(btn){btn.disabled=false;btn.textContent='✨ Generate Plan';}
  if(cancelBtn)cancelBtn.style.display='none';
  if(countdown)countdown.style.display='none';
}

// ── Migrate old activity dates (M/D/YYYY → YYYY-MM-DD) ──────────────────
function migrateActivityDates(){
  const raw=localStorage.getItem('activities_data');if(!raw)return;
  try{
    const acts=JSON.parse(raw);let changed=false;
    acts.forEach(a=>{
      if(a.date&&/^\d{1,2}\/\d{1,2}\/\d{4}/.test(a.date)){
        const m=a.date.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})/);
        if(m){a.date=m[3]+'-'+m[1].padStart(2,'0')+'-'+m[2].padStart(2,'0');changed=true;}
      }
    });
    if(changed)localStorage.setItem('activities_data',JSON.stringify(acts));
  }catch(e){}
}



// ── Weekly Goal Targets ───────────────────────────────────────────────────────
function loadGoalSettings(){
  const g=JSON.parse(localStorage.getItem('weekly_goals')||'{}');
  if(!g.Miles){const am=localStorage.getItem('act_goal_week_mi');if(am)g.Miles=parseFloat(am)||0;}
  ['Miles','Fin','Bible','Prayer','Exercise','Audible'].forEach(k=>{
    const el=document.getElementById('g'+k);if(el&&g[k])el.value=g[k];
  });
}
function saveGoals(){
  const g={};
  ['Miles','Fin','Bible','Prayer','Exercise','Audible'].forEach(k=>{
    const el=document.getElementById('g'+k);if(el&&el.value)g[k]=parseFloat(el.value)||0;
  });
  localStorage.setItem('weekly_goals',JSON.stringify(g));
  // Miles is shared with the Activities tab's "This Week" goal field -- keep
  // that card's storage and input in sync with what was just saved here.
  localStorage.setItem('act_goal_week_mi',g.Miles?String(g.Miles):'');
  const actEl=document.getElementById('actGoal_week');if(actEl)actEl.value=g.Miles?String(g.Miles):'';
  document.getElementById('goalsSettings').style.display='none';
  renderGoals();
  if(typeof renderMileageSummary==='function')renderMileageSummary();
}
function toggleGoalsSettings(){
  const s=document.getElementById('goalsSettings');
  if(!s)return;
  const open=s.style.display==='block';
  s.style.display=open?'none':'block';
  if(!open)loadGoalSettings();
}
function renderGoals(){
  const g=JSON.parse(localStorage.getItem('weekly_goals')||'{}');
  const ws=wkStart(weekOff2),we=new Date(ws);we.setDate(ws.getDate()+6);const weekRuns=uniqueRunActivities(ws,we);
  let miles=weekRuns.reduce(function(total,run){return total+(parseFloat(run.distance)||parseFloat(run.miles)||0);},0),audioMin=0,bibleD=0,prayerD=0,exD=0;
  for(let i=0;i<7;i++){
    const d=new Date(ws);d.setDate(ws.getDate()+i);
    const dateKey=dk(d),r=localStorage.getItem('planner_'+dateKey),dd=r?JSON.parse(r):{};
    if(hasExerciseOnDate(dateKey,dd))exD++;
    audioMin+=journalAudiobookMinutesForDate(dateKey,dd);
    if(dd.spBibleAudio==='green')bibleD++;
    if(dd.spAIPrayer==='green')prayerD++;
  }
  // Run days: days with a run this week vs the run days in BeyondStride's plan (or your goal).
  const runDays=Object.keys(weekRuns.reduce(function(m,r){m[String(r.date||'').slice(0,10)]=1;return m;},{})).length;
  let planRunDays=0,planMiles=0;
  if(typeof bsPlanText==='function')for(let i=0;i<7;i++){const d=new Date(ws);d.setDate(ws.getDate()+i);const t=bsPlanText(dk(d));
    if(!t||/rest\s*day|no\s+(workout|run)/i.test(t.split('\n')[0]))continue;const hl=(typeof journalTrainingHeadline==='function'?journalTrainingHeadline(t):t);const m=hl.match(/(\d+(?:\.\d+)?)\s*(?:mi|miles?)\b/i);if(m){planRunDays++;planMiles+=parseFloat(m[1]);}}
  const runTarget=planRunDays||g.Exercise||0;
  if(planRunDays)g.Exercise=planRunDays;
  const actuals=[
    {key:'Miles',label:'Miles',actual:parseFloat(miles.toFixed(1)),unit:'mi',fmt:v=>v.toFixed(1)},
    {key:'Bible',label:'Bible',actual:bibleD,unit:'/7',fmt:v=>v+'/7'},
    {key:'Prayer',label:'Prayer',actual:prayerD,unit:'/7',fmt:v=>v+'/7'},
    {key:'Exercise',label:'Run days',actual:runDays,unit:'days',fmt:v=>v+' of '+runTarget+' days'},
    {key:'Audible',label:'Audiobook',actual:Math.round(audioMin),unit:'min',fmt:v=>fmtMins(v)},
  ];
  const wrap=document.getElementById('goalBars');if(!wrap)return;
  const hasAnyGoal=actuals.some(a=>g[a.key]>0);
  if(!hasAnyGoal){wrap.innerHTML='<div style="font-size:0.78rem;color:#aaa;text-align:center;padding:6px 0;">Tap ⚙️ Set Goals to add weekly targets.</div>';return;}
  wrap.innerHTML=actuals.filter(a=>g[a.key]>0).map(a=>{
    const target=g[a.key];const pct=Math.min(100,Math.round((a.actual/target)*100));
    const reached=pct>=100;
    return`<div class="goal-row ${reached?'goal-reached':'goal-progress'}">
      <div class="goal-label"><span style="margin-right:.35em;">${a.label}</span><span>${a.key==='Exercise'?a.fmt(a.actual)+(planRunDays?' (plan)':''):a.fmt(a.actual)+' / '+(a.key==='Bible'||a.key==='Prayer'?target+'/7':a.fmt(target))}${a.key==='Miles'&&planMiles?' · plan '+planMiles.toFixed(1):''} ${reached?'✅':pct+'%'}</span></div>
      <div class="goal-bar-bg"><div class="goal-bar-fill" style="width:${pct}%;background:${reached?'#27ae60':'#667eea'};"></div></div>
    </div>`;
  }).join('');
}
// ── Persistent Training Plan ─────────────────────────────────────────────────
/* Tab Section Notes */
var _currentNoteTab='daily';
function saveTabNote(){
  var ta=document.getElementById('tabNoteTA');
  if(ta)localStorage.setItem('tabNote_'+_currentNoteTab,ta.value);
  updateTabNoteStatus();
}
function loadTabNote(tab){
  var ta=document.getElementById('tabNoteTA');
  if(!ta)return;
  var v=localStorage.getItem('tabNote_'+tab);
  ta.value=v!=null?v:'';
  ta.placeholder=tab.charAt(0).toUpperCase()+tab.slice(1)+' notes...';
  updateTabNoteStatus();
}
function updateTabNoteStatus(){
  var ta=document.getElementById('tabNoteTA'),status=document.getElementById('tabNotesStatus'),bar=document.getElementById('tabNotesBar');
  var hasNote=!!(ta&&ta.value.trim());
  if(status)status.textContent=hasNote?'Notes saved':'No notes';
  if(bar)bar.classList.toggle('has-notes',hasNote);
  refreshJournalNavStatus();
}
function loadAllTabNotes(){
  loadTabNote(_currentNoteTab);
}

/* ===== Journal experience module: home, preferences, navigation, reliability ===== */

// ── Runs from the Galaxy Watch Run app ──
// The phone's Running section opens this page as daily-planner.html#watchruns=<base64url JSON>.
// Each run carries a stable id (its start time), so sending the same run twice never duplicates it.
function importWatchRunsFromLink(){
  var m=(location.hash||'').match(/[#&]watchruns=([A-Za-z0-9_-]+)/);
  if(!m)return;
  var runs;
  try{
    var b64=m[1].replace(/-/g,'+').replace(/_/g,'/');while(b64.length%4)b64+='=';
    var bin=atob(b64),bytes=new Uint8Array(bin.length);
    for(var i=0;i<bin.length;i++)bytes[i]=bin.charCodeAt(i);
    runs=JSON.parse(new TextDecoder().decode(bytes));
  }catch(e){alert('Could not read the runs sent from the watch app: '+e.message);return;}
  if(!Array.isArray(runs)||!runs.length)return;
  importWatchRuns(runs,{fromLink:true});
}
// Adds runs from BeyondStride to Activities (from the link above).
// opts.fromLink: also clear the link and open Activities.
// opts.done(added, updated) runs after saving.
function importWatchRuns(runs,opts){
  opts=opts||{};
  var tries=0;
  (function waitForActivities(){
    // Activities load asynchronously from on-device storage; wait for them first.
    if(!Array.isArray(_activitiesLargeCache)&&tries++<60){setTimeout(waitForActivities,250);return;}
    var acts=getActivities().slice(),added=0,updated=0;
    runs.forEach(function(r){
      if(!r||!r.id||!r.date)return;
      if(!r.calories&&typeof estimateCalories==='function'){
        try{r.calories=estimateCalories({type:r.type||'Run',distance:r.distance||0,duration:r.duration||'',power:r.power||0,elevGain:r.elevGain||0});}catch(ignore){}
      }
      var idx=acts.findIndex(function(a){return String(a.id)===String(r.id);});
      if(idx===-1){acts.push(r);added++;}
      else{
        // Keep anything added by hand (notes, weather, Air Power…); refresh watch-recorded numbers.
        var keep=acts[idx];
        ['distance','duration','pace','power','heartRate','cadence'].forEach(function(k){if(r[k])keep[k]=r[k];});
        if(r.routeUrl&&!keep.routeUrl)keep.routeUrl=r.routeUrl;
        // An automatic name ("Evening Run") is replaced by the planned workout's name; a name you typed is kept.
        if(r.title&&(!keep.title||/^(Morning|Afternoon|Evening|Night) Run$/.test(keep.title)))keep.title=r.title;
        // Stryd extras and weather (temp, humidity, AQI) fill in blanks only, so hand-entered values are never overwritten.
        ['elevGain','temp','humidity','aqi','airPower','rss'].forEach(function(k){if(r[k]&&!keep[k])keep[k]=r[k];});
        // Notes written by the watch app get refreshed; notes you edited by hand are kept.
        if(r.notes&&(!keep.notes||keep.notes.indexOf('Recorded on Galaxy Watch')!==-1))keep.notes=r.notes;
        updated++;
      }
    });
    saveActivities(acts);
    if(opts.fromLink){
      try{history.replaceState(null,'',location.pathname+location.search);}catch(ignore3){}
    }
    var msg=added?(added+' BeyondStride run'+(added===1?'':'s')+' added to Activities'):(opts.fromLink?'Those runs were already in Activities':'');
    if(updated&&added)msg+=' ('+updated+' already there)';
    if(msg){if(typeof v26Toast==='function')v26Toast(msg);else if(opts.fromLink)alert(msg);}
    if(typeof opts.done==='function')opts.done(added,updated);
  })();
}
window.addEventListener('load',function(){setTimeout(importWatchRunsFromLink,300);});
// ── Generic #tab=<name> deep link: jump straight to that tab ──
function openTabFromLink(){
  var m=(location.hash||'').match(/[#&]tab=([a-z0-9]+)/);
  if(!m||typeof hmSwitch!=='function')return;
  try{hmSwitch(m[1],null);window.scrollTo(0,0);}catch(e){}
}
window.addEventListener('load',function(){setTimeout(openTabFromLink,350);});
window.addEventListener('hashchange',openTabFromLink);
window.addEventListener('hashchange',importWatchRunsFromLink);

// ── Send a training plan to BeyondStride on the phone (and from there to the watch) ──
// Opens beyondstride://localhost/plan#<base64url {date: that day's plan text}> — BeyondStride
// turns each day into a guided workout. Only today and later are sent.
