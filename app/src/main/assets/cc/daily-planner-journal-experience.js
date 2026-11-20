var JOURNAL_UI_CONFIG={
  homeCards:['priorities','checkins','training','wellness','notes','streaks'],
  homeLabels:{priorities:'Priorities & Goals',checkins:'Daily Check-In',training:'Today’s Training',wellness:'Wellness',notes:'Notes & Review',streaks:'Current Streaks'}
};
function journalPrefs(){
  var defaults={homeCards:JOURNAL_UI_CONFIG.homeCards.slice(),aiContext:true,backupDays:7};
  try{
    var prefs=Object.assign(defaults,JSON.parse(localStorage.getItem('journal_ui_prefs')||'{}'));
    if(!localStorage.getItem('journal_home_streaks_migrated')){
      if(Array.isArray(prefs.homeCards)&&prefs.homeCards.indexOf('streaks')<0)prefs.homeCards.push('streaks');
      localStorage.setItem('journal_ui_prefs',JSON.stringify(prefs));localStorage.setItem('journal_home_streaks_migrated','1');
    }
    // Nutrition moved to a different app; drop any leftover 'nutrition' card from a
    // previously-stored preference order so it doesn't leave an invisible phantom entry.
    if(!localStorage.getItem('journal_home_nutrition_removed')){
      if(Array.isArray(prefs.homeCards)){
        var idx=prefs.homeCards.indexOf('nutrition');
        if(idx!==-1){prefs.homeCards.splice(idx,1);localStorage.setItem('journal_ui_prefs',JSON.stringify(prefs));}
      }
      localStorage.setItem('journal_home_nutrition_removed','1');
    }
    return prefs;
  }catch(e){return defaults;}
}
function journalViewData(){
  var data={};try{data=JSON.parse(localStorage.getItem('planner_'+dk(today))||'{}');}catch(e){}
  // My Schedule should reflect the checkmarks currently shown on the selected day.
  // Some sections contain duplicate controls; prefer a visible green state over a
  // stale hidden blank copy instead of showing 0 completed.
  var triRank={'':0,black:1,purple:2,red:3,green:4};
  document.querySelectorAll('.tri-check[data-key]').forEach(function(el){var key=el.dataset.key,next=el.dataset.state||'',current=data[key]||'';if((triRank[next]||0)>(triRank[current]||0))data[key]=next;});
  return data;
}
function journalHomeCard(id,title,body){
  return'<div class="journal-home-card" data-home-card="'+id+'"><h3>'+title+'</h3><div class="home-value">'+body+'</div></div>';
}
function journalTodayTrainingSummary(){
  var dateKey=dk(today);
  var acts=getActivities().filter(function(a){return a.date===dateKey;});
  var completed=acts.filter(function(a){return !/rest\s*day/i.test(String(a.type||a.title||''));});
  if(completed.length){
    var logged=completed[completed.length-1],loggedName=String(logged.title||logged.type||'Activity logged').trim(),loggedDistance=parseFloat(logged.distance)||0;
    return loggedName+(loggedDistance&&!new RegExp('\\b'+loggedDistance+'\\s*(?:mi|mile)','i').test(loggedName)?' - '+loggedDistance+' mi':'');
  }
  if(acts.some(function(a){return /rest\s*day/i.test(String(a.type||a.title||''));}))return'Rest Day';
  var text='';
  try{
    var raw=localStorage.getItem('current_training_plan'),plan=raw?JSON.parse(raw):null;
    // BeyondStride's plan: show its own workout name ("Recovery 3 miles"), not a guessed category.
    if(typeof bsPlanCovers==='function'&&bsPlanCovers(dateKey)){var bt=bsPlanText(dateKey);if(!bt)return'Rest Day';var hl=journalTrainingHeadline(bt);if(hl)return hl;text=bt;}
    else if(plan&&plan.text&&plan.startDate)text=extractWorkoutFromActivePlan(plan,today)||'';
  }catch(e){}
  if(!text)text=localStorage.getItem('workout_cache_'+dateKey)||'';
  if(!text)return acts.length?(acts[0].title||acts[0].type||'Activity logged'):'No workout scheduled';
  var patterns=[
    {re:/\blong\s+run\b/i,label:'Long Run'},{re:/\b(intervals?|repeats?|speed\s*work)\b/i,label:'Interval Run'},
    {re:/\btempo\s+run\b|\bthreshold\b/i,label:'Tempo Run'},{re:/\beasy\s+run\b/i,label:'Easy Run'},
    {re:/\brecovery\s+run\b/i,label:'Recovery Run'},{re:/\bprogression\s+run\b/i,label:'Progression Run'},
    {re:/\brace\b/i,label:'Race'},{re:/\brest\s+day\b|\bno\s+scheduled\s+workout\b/i,label:'Rest Day'},
    {re:/\bcross[- ]training\b/i,label:'Cross-Training'},{re:/\bstrength\b/i,label:'Strength Training'}
  ];
  var lines=String(text).split(/\r?\n/).map(function(line){return line.trim();}).filter(Boolean);
  for(var p=0;p<patterns.length;p++){
    var hit=lines.find(function(line){return patterns[p].re.test(line);});
    if(hit){var distance=hit.match(/\b\d+(?:\.\d+)?\s*(?:miles?|mi)\b/i);return patterns[p].label+(distance?' - '+distance[0]:'');}
  }
  var useful=lines.find(function(line){return !/^(week|day)\s*\d*|^(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b/i.test(line);});
  return useful||lines[0]||'Workout scheduled';
}
function journalAverageRecentPaceMinutes(){
  try{
    var runs=(getActivities()||[]).filter(function(a){return a&&a.pace&&/run/i.test(String(a.type||''));});
    var recent=runs.slice(-10),totalSec=0,n=0;
    recent.forEach(function(a){var p=parsePace(a.pace);if(p>0){totalSec+=p;n++;}});
    return n?(totalSec/n/60):0;
  }catch(e){return 0;}
}
// Estimate how long today's training will realistically take, so My Schedule can push
// later items (stretching, breakfast) back instead of assuming every workout magically
// finishes in under 15 minutes. Prefers a real logged duration when the workout has
// already happened; otherwise estimates from the training-plan text.
function journalTodayTrainingMinutesEstimate(trainingText,dateKey){
  try{
    var acts=(getActivities()||[]).filter(function(a){return a&&a.date===dateKey&&a.type!=='Rest Day';});
    var withDuration=acts.find(function(a){return a.duration&&parseMins(a.duration)>0;});
    if(withDuration)return Math.round(parseMins(withDuration.duration));
  }catch(e){}
  var text=String(trainingText||'');
  if(!text||/rest day/i.test(text)||/no\s+workout\s+scheduled/i.test(text))return 0;
  var distMatch=text.match(/\b(\d+(?:\.\d+)?)\s*(?:miles?|mi)\b/i);
  if(distMatch){
    var miles=parseFloat(distMatch[1])||0;
    if(miles>0){var pace=journalAverageRecentPaceMinutes()||9.5;return Math.round(miles*pace);}
  }
  if(/long\s+run/i.test(text))return 75;
  if(/interval|repeats?|speed\s*work/i.test(text))return 55;
  if(/tempo|threshold/i.test(text))return 50;
  if(/recovery\s+run/i.test(text))return 30;
  if(/easy\s+run/i.test(text))return 40;
  if(/\brace\b/i.test(text))return 90;
  if(/strength/i.test(text))return 45;
  if(/cross[- ]training/i.test(text))return 45;
  return 45;
}
function journalMinsToClock(mins){
  var total=((Math.round(mins)%1440)+1440)%1440,h=Math.floor(total/60),m=total%60,ap=h>=12?'PM':'AM',h12=h%12;if(h12===0)h12=12;
  return h12+':'+String(m).padStart(2,'0')+' '+ap;
}
function journalHomeStreakSummary(){
  var actIdx=_buildActivityDateIndex(),cache={};
  var defs=[
    {label:'Bible',fn:function(d){return d.spBibleAudio==='green';}},
    {label:'Worship',fn:function(d){return d.spWorship==='green';}},
    {label:'Prayer',fn:function(d){return d.spAIPrayer==='green';}},
    {label:'Running',fn:function(d,dateKey){return !!actIdx.run[dateKey];}}
  ];
  return defs.map(function(item){return'<span style="white-space:nowrap;"><strong>'+streak(item.fn,cache)+'</strong> '+item.label+'</span>';}).join(' &nbsp;•&nbsp; ');
}
function journalScheduleGrowth(dateKey){
  var skills=getSkillEntries(),daily={};
  try{daily=JSON.parse(localStorage.getItem('planner_'+dateKey)||'{}');}catch(ignore){}
  var range=skills.some(function(entry){return entry.date===dateKey&&entry.category==='Gun Range';});
  // Gun Range has no "planned" schedule of its own (unlike training runs) — only surface it
  // once it's actually been logged for the day, instead of forcing it onto every Saturday.
  if(range)return{label:'Gun Range',done:true,tab:'daily'};
  return{label:'',done:false,tab:'daily',skip:true};
}
function journalToggleScheduleManual(name){
  var key='schedule_'+name+'_'+dk(today),done=localStorage.getItem(key)==='1';
  if(done)localStorage.removeItem(key);else localStorage.setItem(key,'1');
  renderJournalSchedule();
}
var JOURNAL_SLEEP_STEPS=['Begin winding down by the target time','Put away the computer','Complete nighttime hygiene','Prepare bedroom and set alarm','Get into bed by the target bedtime','Finish journaling, then enable Do Not Disturb'];
var _journalSleepRoutineDrafts={};
var _journalSleepRoutineOpen=false;
var _journalScheduleOpenDetail='',_journalEndSummaryOpen=false;
function journalCheckState(data,key){var value=String(data&&data[key]||'');return['green','purple','red','black'].indexOf(value)!==-1?value:'';}
function journalCheckRecorded(data,key){return!!journalCheckState(data,key);}
function journalLinkedGreenCount(keys,data){
  return keys.filter(function(key){
    var controls=Array.from(document.querySelectorAll('.tri-check[data-key="'+key+'"]'));
    return controls.some(function(control){return(control.dataset.state||'')==='green'||control.classList.contains('green');})||journalCheckState(data,key)==='green';
  }).length;
}
function journalToggleScheduleDetail(kind){_journalScheduleOpenDetail=_journalScheduleOpenDetail===kind?'':kind;renderJournalSchedule();}
// Today's meditation session, if one was logged, so My Schedule can show what
// was actually done instead of the generic reminder text.
function journalMeditationTodayLine(){
  try{
    var sessions=JSON.parse(localStorage.getItem('medit_sessions')||'[]');
    var todayKey=(typeof meditTodayKey==='function')?meditTodayKey():dk(today);
    var entry=sessions.find(function(s){return s.date===todayKey;});
    if(entry&&entry.dur)return entry.dur+' min session logged today';
  }catch(ignore){}
  return'Begin the day with meditation and stillness';
}
// Quick actions for the Meditation card on My Schedule — mark complete or
// jump straight into a timed session without leaving the schedule view.
function journalMeditationQuickToggle(event){
  if(event){event.preventDefault();event.stopPropagation();}
  var next=journalCheckState(journalViewData(),'spMeditation')==='green'?'':'green';
  document.querySelectorAll('.tri-check[data-key="spMeditation"]').forEach(function(el){applyTriState(el,next);});
  save();
  renderJournalSchedule();
}
function journalMeditationQuickStart(event,minutes){
  if(event){event.preventDefault();event.stopPropagation();}
  switchTab('meditate',null);
  setTimeout(function(){
    if(typeof meditSetDuration==='function')meditSetDuration(minutes);
    if(typeof meditStartStop==='function'&&!_meditRunning)meditStartStop();
    var timer=document.getElementById('meditTimerDisplay');
    if(timer)timer.scrollIntoView({behavior:'smooth',block:'center'});
  },80);
}
function journalScheduleDetailHtml(kind,data){
  if(_journalScheduleOpenDetail!==kind)return'';
  if(kind==='meditation'){
    var meditDone=journalCheckState(data,'spMeditation')==='green';
    return'<div class="journal-schedule-detail-panel">'
      +'<button type="button" class="journal-schedule-detail-row" onclick="journalMeditationQuickToggle(event)"><i class="journal-schedule-detail-dot '+(meditDone?'green':'')+'"></i><span>'+(meditDone?'Meditation complete':'Mark meditation complete')+'</span></button>'
      +'<div class="journal-schedule-medit-quickstart"><span class="journal-schedule-medit-label">Start a session:</span>'
      +[5,10,15,20].map(function(m){return'<button type="button" class="journal-schedule-medit-btn" onclick="journalMeditationQuickStart(event,'+m+')">'+m+'m</button>';}).join('')
      +'</div></div>';
  }
  var rows=kind==='faith'?[['Daily Bread','spDailyBread','bible'],['Bible reading','spBibleAudio','bible'],['Worship','spWorship','bible'],['Prayer','spAIPrayer','bible']]:kind==='wellness'?[['Blood pressure','wBP','wellness'],['Medications','wMeds','wellness'],['Weight','wWght','wellness'],['Sleep','wSleep','wellness']]:[['Dog walk','exDogWalk','daily'],['10,000 steps','exStepsCheck','daily']];
  return'<div class="journal-schedule-detail-panel">'+rows.map(function(row){var status=journalCheckState(data,row[1]);return'<button type="button" class="journal-schedule-detail-row" onclick="journalOpenDailyCheck(event,\''+row[2]+'\',\''+row[1]+'\')"><i class="journal-schedule-detail-dot '+status+'"></i><span>'+row[0]+'</span></button>';}).join('')+'</div>';
}
function journalToggleHiddenCheck(event,id){if(event){event.preventDefault();event.stopPropagation();}var target=document.getElementById(id);if(target){target.checked=!target.checked;save();renderJournalSchedule();}}
function journalOpenDailyCheck(event,tab,key,id){if(event){event.preventDefault();event.stopPropagation();}switchTab(tab,null);setTimeout(function(){var target=id?document.getElementById(id):document.querySelector('.tri-check[data-key="'+key+'"]');if(target&&target.type==='checkbox'){target.checked=!target.checked;save();renderJournalSchedule();return;}if(target){target.scrollIntoView({behavior:'smooth',block:'center'});target.focus({preventScroll:true});}},80);}
function journalToggleEndSummary(){_journalEndSummaryOpen=!_journalEndSummaryOpen;renderJournalSchedule();}
function journalEndSummaryOpenScheduled(event,id){
  if(event){event.preventDefault();event.stopPropagation();}
  var card=document.querySelector('.journal-schedule-item[data-schedule-id="'+CSS.escape(id)+'"]');
  if(card){card.scrollIntoView({behavior:'smooth',block:'center'});card.focus({preventScroll:true});}
}
function journalEndSummaryHtml(scheduleItems){
  var scheduled=Array.isArray(scheduleItems)?scheduleItems:Object.keys(_journalScheduleItemsById||{}).map(function(id){return _journalScheduleItemsById[id];});
  var items=scheduled.filter(function(item){return item&&!item.fixed;}).map(function(item){return{id:item.id,label:item.label,status:item.scheduleStatus||'blank'};});
  var complete=items.filter(function(item){return item.status==='green';}).length,partial=items.filter(function(item){return item.status==='purple';}).length,remaining=items.filter(function(item){return item.status===''||item.status==='blank'||item.status==='red';}),unfinished=items.filter(function(item){return item.status!=='green';});
  var colorOrder={purple:0,red:1,blank:2,'':2};unfinished.sort(function(a,b){return(colorOrder[a.status]??3)-(colorOrder[b.status]??3)||a.label.localeCompare(b.label);});
  var rows=_journalEndSummaryOpen?'<div class="journal-end-items">'+unfinished.map(function(item){return'<button type="button" class="journal-end-item '+item.status+'" onclick="journalEndSummaryOpenScheduled(event,\''+escHtml(item.id)+'\')"><i></i><span>'+escHtml(item.label)+'</span></button>';}).join('')+'</div>':'';
  return'<div class="journal-end-summary"><button type="button" onclick="journalToggleEndSummary()"><span>'+(_journalEndSummaryOpen?'&#x25BC;':'&#x25B6;')+' End-of-Day Summary</span><span class="journal-end-counts">'+complete+' complete &bull; '+partial+' in progress &bull; '+remaining.length+' remaining</span></button>'+rows+'</div>';
}
function journalSleepRoutineState(dateKey){
  var values=Object.prototype.hasOwnProperty.call(_journalSleepRoutineDrafts,dateKey)?_journalSleepRoutineDrafts[dateKey].slice():null;
  if(!Array.isArray(values)){try{values=JSON.parse(localStorage.getItem('schedule_sleepRoutine_steps_'+dateKey)||'null');}catch(ignore){}}
  if(!Array.isArray(values)){values=localStorage.getItem('schedule_sleepRoutine_'+dateKey)==='1'?[true,true,true,true,true,false]:[false,false,false,false,false,false];}
  values=JOURNAL_SLEEP_STEPS.map(function(_,index){var value=values[index];return value===true||value===1||value==='true'||value==='green'||value==='checked';});
  return{values:values,count:values.filter(Boolean).length};
}
function journalToggleSleepRoutinePanel(){_journalSleepRoutineOpen=!_journalSleepRoutineOpen;renderJournalSchedule();}
function journalSaveSleepRoutineState(dateKey,values){
  values=JOURNAL_SLEEP_STEPS.map(function(_,index){return values[index]===true;});
  _journalSleepRoutineDrafts[dateKey]=values.slice();
  localStorage.setItem('schedule_sleepRoutine_steps_'+dateKey,JSON.stringify(values));
  if(values.filter(Boolean).length>=4)localStorage.setItem('schedule_sleepRoutine_'+dateKey,'1');else localStorage.removeItem('schedule_sleepRoutine_'+dateKey);
  return values.filter(Boolean).length;
}
function journalToggleSleepRoutineStep(event,index){
  if(event){event.preventDefault();event.stopPropagation();}
  var dateKey=dk(today),state=journalSleepRoutineState(dateKey);
  state.values[index]=!state.values[index];
  journalSaveSleepRoutineState(dateKey,state.values);
  _journalSleepRoutineOpen=true;renderJournalSchedule();
}
function journalSleepRoutineChecklistHtml(dateKey){
  if(!_journalSleepRoutineOpen)return'';var state=journalSleepRoutineState(dateKey);
  return'<div class="journal-sleep-checklist">'+JOURNAL_SLEEP_STEPS.map(function(label,index){var checked=state.values[index];return'<button type="button" class="journal-sleep-step '+(checked?'checked':'')+'" aria-pressed="'+(checked?'true':'false')+'" onclick="journalToggleSleepRoutineStep(event,'+index+')"><i>&#x2713;</i><span>'+escHtml(label)+'</span></button>';}).join('')+'<small>'+state.count+' of 6 completed &bull; Complete the habit with any 4 steps.</small></div>';
}
var _journalScheduleDragged='',_journalScheduleSuppressClick=0;
var _journalScheduleItemsById={};
var _journalGrowthPointer=null;
function journalScheduleOrderKey(dateKey){return'journal_schedule_order_'+dateKey;}
function journalScheduleHiddenKey(dateKey){return'journal_schedule_hidden_'+dateKey;}
function journalScheduleStatusKey(dateKey){return'journal_schedule_status_'+dateKey;}
function journalScheduleStatuses(dateKey){try{var saved=JSON.parse(localStorage.getItem(journalScheduleStatusKey(dateKey))||'{}');return saved&&typeof saved==='object'?saved:{};}catch(ignore){return{};}}
function journalScheduleCycleStatus(event,id){
  if(event){event.preventDefault();event.stopPropagation();}_journalScheduleSuppressClick=Date.now()+600;
  var item=_journalScheduleItemsById[id];if(!item||item.fixed)return;var dateKey=dk(today),statuses=journalScheduleStatuses(dateKey),current=item.scheduleStatus||'',next=current==='green'?'red':current==='red'?'blank':'green';statuses[id]=next;localStorage.setItem(journalScheduleStatusKey(dateKey),JSON.stringify(statuses));
  if(item.manual==='eveningPlanning'){
    var eveningKey='schedule_eveningPlanning_'+dateKey;if(next==='green')localStorage.setItem(eveningKey,'1');else localStorage.removeItem(eveningKey);
    var eveningRow={};try{eveningRow=JSON.parse(localStorage.getItem('planner_'+dateKey)||'{}')||{};}catch(ignoreEvening){}eveningRow.prepJournalCheck=next==='green';localStorage.setItem('planner_'+dateKey,JSON.stringify(eveningRow));
    var eveningControl=document.getElementById('prepJournalCheck');if(eveningControl)eveningControl.checked=next==='green';
  }
  var linkedKey=item.linkedCheckKey||(item.growthKind==='reading'?'gaAudiobook':item.growthKind==='language'?'gaLanguage':item.growthKind==='guitar'?'gaGuitar':'');
  if(linkedKey){document.querySelectorAll('.tri-check[data-key="'+linkedKey+'"]').forEach(function(control){applyTriState(control,next==='blank'?'':next);});save();}
  if(item.checkId){var target=document.getElementById(item.checkId);if(target){target.checked=next==='green';save();}}
  renderJournalSchedule();
}
function journalDeleteScheduleItem(event,id){event.preventDefault();event.stopPropagation();if(!confirm('Delete this item from My Schedule for this day?'))return;var dateKey=dk(today),hidden=[];try{hidden=JSON.parse(localStorage.getItem(journalScheduleHiddenKey(dateKey))||'[]');}catch(ignore){}if(!Array.isArray(hidden))hidden=[];if(hidden.indexOf(id)===-1)hidden.push(id);localStorage.setItem(journalScheduleHiddenKey(dateKey),JSON.stringify(hidden));renderJournalSchedule();v26Toast('Schedule item deleted for this day');}
function journalScheduleGrowthKey(kind,dateKey){return'journal_schedule_growth_'+kind+'_'+dateKey;}
function journalScheduleGrowthId(kind){return{language:'schedule-language-practice',guitar:'schedule-guitar',reading:'schedule-reading-audiobook'}[kind]||'';}
function journalGrowthCategory(kind){return{language:'Language',guitar:'Guitar',reading:'Reading / Audiobook'}[kind]||'Guitar';}
function journalGrowthSavedDetails(kind,dateKey){
  var id=journalScheduleGrowthId(kind),overrides={};try{overrides=JSON.parse(localStorage.getItem(journalScheduleOverridesKey(dateKey))||'{}');}catch(ignore){}
  var saved=overrides[id]&&typeof overrides[id]==='object'?overrides[id]:{};
  var entries=getSkillEntries().filter(function(entry){return(entry.scheduledDate||entry.date)===dateKey&&(entry.growthKind===kind||entry.scheduleId===id);});
  var entry=entries.length?entries[entries.length-1]:null;
  if(!entry){var category=journalGrowthCategory(kind);entries=getSkillEntries().filter(function(candidate){return candidate.date===dateKey&&candidate.category===category;});entry=entries.length?entries[entries.length-1]:null;}
  var combined=Object.assign({},entry||{},saved||{});
  // Older schedule overrides stored only their clock time. Preserve the matching
  // Growth Log duration separately so legacy activities can still display it.
  if(combined.loggedTime==null&&entry&&entry.time)combined.loggedTime=entry.time;
  return combined;
}
function journalGrowthDetailText(name,resource,link,notes,time){
  var parts=[];if(resource&&resource!==name)parts.push(resource);if(time)parts.push(time+' logged');if(link)parts.push(link);if(notes)parts.push(notes);return parts.join(' • ');
}
var _journalGrowthLogKind='';
function journalOpenGrowthLog(kind){
  _journalGrowthLogKind=kind;var modal=document.getElementById('journalGrowthLogModal');if(!modal)return;
  var names={language:'Language',guitar:'Guitar',reading:'Audiobook'},categories={language:'Language',guitar:'Guitar',reading:'Reading / Audiobook'},startTimes={reading:'16:45',language:'19:00',guitar:'19:45'};
  var dateKey=dk(today),saved=journalGrowthSavedDetails(kind,dateKey),savedMins=journalScheduleParseTime(saved.time),savedClock=savedMins===null?'':String(Math.floor(savedMins/60)).padStart(2,'0')+':'+String(savedMins%60).padStart(2,'0');
  document.getElementById('journalGrowthLogTitle').textContent='Schedule / Log '+(names[kind]||'Growth / Skill');
  document.getElementById('journalGrowthLogCategory').value=categories[kind]||'Guitar';document.getElementById('journalGrowthLogTime').value=saved.loggedTime||'';document.getElementById('journalGrowthLogDate').value=dateKey;document.getElementById('journalGrowthLogStartTime').value=savedClock||startTimes[kind]||'19:00';document.getElementById('journalGrowthLogName').value=saved.name||saved.label||'';document.getElementById('journalGrowthLogResource').value=saved.resource||(kind==='reading'?'Audiobook':'');document.getElementById('journalGrowthLogLink').value=saved.link||'';document.getElementById('journalGrowthLogNotes').value=saved.notes||'';modal.style.display='flex';
}
function journalCloseGrowthLog(){var modal=document.getElementById('journalGrowthLogModal');if(modal)modal.style.display='none';_journalGrowthLogKind='';}
function journalSaveGrowthLog(){
  var name=(document.getElementById('journalGrowthLogName').value||'').trim(),time=(document.getElementById('journalGrowthLogTime').value||'').trim();
  var scheduledDate=document.getElementById('journalGrowthLogDate').value,startTime=document.getElementById('journalGrowthLogStartTime').value;if(!scheduledDate){alert('Please choose the scheduled date.');return;}if(!startTime){alert('Please choose the start time.');return;}var startMins=journalScheduleParseTime(startTime);if(startMins===null){alert('Please choose a valid start time.');return;}
  var rawLink=(document.getElementById('journalGrowthLogLink').value||'').trim(),link=skillSafeLink(rawLink);if(rawLink&&!link){alert('Please enter a valid website link.');return;}
  var category=document.getElementById('journalGrowthLogCategory').value||'Guitar',kindByCategory={'Language':'language','Guitar':'guitar','Reading / Audiobook':'reading'},kind=kindByCategory[category]||'guitar';
  if(!name)name=category;
  var resource=(document.getElementById('journalGrowthLogResource').value||'').trim(),notes=(document.getElementById('journalGrowthLogNotes').value||'').trim(),scheduleId=journalScheduleGrowthId(kind),entryId='';
  var entries=getSkillEntries(),existingIndex=entries.findIndex(function(entry){return(entry.scheduledDate||entry.date)===scheduledDate&&(entry.growthKind===kind||entry.scheduleId===scheduleId);});
  if(time){var logged={id:existingIndex>=0?entries[existingIndex].id:'sk_'+Date.now(),date:scheduledDate,scheduledDate:scheduledDate,startTime:startTime,category:category,name:name,time:time,resource:resource,link:link,notes:notes,growthKind:kind,scheduleId:scheduleId};if(existingIndex>=0)entries[existingIndex]=logged;else entries.push(logged);entryId=logged.id;saveSkillEntries(entries);}
  localStorage.setItem(journalScheduleGrowthKey(kind,scheduledDate),'1');var overrides={};try{overrides=JSON.parse(localStorage.getItem(journalScheduleOverridesKey(scheduledDate))||'{}');}catch(ignore){}var prior=overrides[scheduleId]&&typeof overrides[scheduleId]==='object'?overrides[scheduleId]:{};overrides[scheduleId]={time:journalMinsToClock(startMins),mins:startMins,label:name,detail:journalGrowthDetailText(name,resource,link,notes,time),resource:resource,link:link,notes:notes,loggedTime:time,entryId:entryId||prior.entryId||''};localStorage.setItem(journalScheduleOverridesKey(scheduledDate),JSON.stringify(overrides));
  journalCloseGrowthLog();renderSkillLog();renderSkillSectionCharts();renderJournalSchedule();var weekly=document.getElementById('tab-weekly');if(weekly&&weekly.classList.contains('active'))renderWeekly();v26Toast(time?'Growth activity completed, logged and scheduled for '+scheduledDate:'Growth activity scheduled for '+scheduledDate+' — add time after completion');
}
function journalManualScheduleItems(){try{var items=JSON.parse(localStorage.getItem('journal_manual_schedule_items')||'[]');return Array.isArray(items)?items:[];}catch(ignore){return[];}}
function journalOpenManualScheduleItem(){
  var modal=document.getElementById('journalManualScheduleModal');if(!modal)return;document.getElementById('journalManualScheduleDate').value=dk(today);document.getElementById('journalManualScheduleTime').value='';document.getElementById('journalManualScheduleTitle').value='';document.getElementById('journalManualScheduleNote').value='';modal.style.display='flex';setTimeout(function(){document.getElementById('journalManualScheduleTitle').focus();},40);
}
function journalCloseManualScheduleItem(){var modal=document.getElementById('journalManualScheduleModal');if(modal)modal.style.display='none';}
function journalSaveManualScheduleItem(){
  var date=document.getElementById('journalManualScheduleDate').value,time=document.getElementById('journalManualScheduleTime').value,title=(document.getElementById('journalManualScheduleTitle').value||'').trim(),note=(document.getElementById('journalManualScheduleNote').value||'').trim();if(!date){alert('Please choose a date.');return;}if(!time){alert('Please choose a time.');return;}if(!title){alert('Please enter a schedule item.');return;}var mins=journalScheduleParseTime(time);if(mins===null){alert('Please choose a valid time.');return;}
  var items=journalManualScheduleItems();items.push({id:'manual-'+Date.now(),date:date,time:journalMinsToClock(mins),mins:mins,title:title,note:note});localStorage.setItem('journal_manual_schedule_items',JSON.stringify(items));journalCloseManualScheduleItem();renderJournalSchedule();v26Toast('Added to My Schedule for '+date);
}
function journalDeleteManualScheduleItem(event,id,moveId){
  event.preventDefault();event.stopPropagation();if(!confirm('Remove this item from My Schedule?'))return;if(moveId){var moves=journalScheduleMoves().filter(function(move){return move.id!==moveId;});localStorage.setItem('journal_schedule_moves',JSON.stringify(moves));}var items=journalManualScheduleItems().filter(function(item){return item.id!==id;});localStorage.setItem('journal_manual_schedule_items',JSON.stringify(items));renderJournalSchedule();v26Toast('Schedule item removed');
}
function journalScheduleAddGrowth(kind,targetId,after){
  var dateKey=dk(today),id=journalScheduleGrowthId(kind);if(!id)return;localStorage.setItem(journalScheduleGrowthKey(kind,dateKey),'1');
  var cards=Array.from(document.querySelectorAll('#journalScheduleBody .journal-schedule-item')),order=cards.map(function(card){return card.dataset.scheduleId;}).filter(function(value){return value!==id;});
  if(targetId&&order.indexOf(targetId)>=0)order.splice(order.indexOf(targetId)+(after?1:0),0,id);else order.push(id);
  localStorage.setItem(journalScheduleOrderKey(dateKey),JSON.stringify(order));renderJournalSchedule();v26Toast('Growth activity added to schedule');
}
function journalScheduleRemoveGrowth(event,kind,id){
  event.preventDefault();event.stopPropagation();var dateKey=dk(today);localStorage.removeItem(journalScheduleGrowthKey(kind,dateKey));
  var order=[];try{order=JSON.parse(localStorage.getItem(journalScheduleOrderKey(dateKey))||'[]');}catch(ignore){}if(Array.isArray(order))localStorage.setItem(journalScheduleOrderKey(dateKey),JSON.stringify(order.filter(function(value){return value!==id;})));
  var overrides={};try{overrides=JSON.parse(localStorage.getItem(journalScheduleOverridesKey(dateKey))||'{}');}catch(ignore2){}delete overrides[id];localStorage.setItem(journalScheduleOverridesKey(dateKey),JSON.stringify(overrides));renderJournalSchedule();v26Toast('Returned to Available Growth Activities');
}
function journalScheduleToggleLinkedCheck(event,key){event.preventDefault();event.stopPropagation();var control=document.querySelector('.tri-check[data-key="'+key+'"]');if(control)triClick(control);}
function journalGrowthPointerStart(event,kind){
  if(event.button!=null&&event.button!==0)return;event.preventDefault();event.stopPropagation();var handle=event.currentTarget;_journalGrowthPointer={kind:kind,handle:handle,pointerId:event.pointerId,target:null};handle.classList.add('dragging');try{handle.setPointerCapture(event.pointerId);}catch(ignore){}
  document.addEventListener('pointermove',journalGrowthPointerMove,{passive:false});document.addEventListener('pointerup',journalGrowthPointerEnd,{passive:false});document.addEventListener('pointercancel',journalGrowthPointerEnd,{passive:false});
}
function journalGrowthPointerMove(event){
  if(!_journalGrowthPointer||event.pointerId!==_journalGrowthPointer.pointerId)return;event.preventDefault();document.querySelectorAll('.journal-schedule-item.drag-before,.journal-schedule-item.drag-after').forEach(function(el){el.classList.remove('drag-before','drag-after');});
  var el=document.elementFromPoint(event.clientX,event.clientY),card=el&&el.closest&&el.closest('.journal-schedule-item');_journalGrowthPointer.target=card||null;if(card){var rect=card.getBoundingClientRect();card.classList.add(event.clientY>rect.top+rect.height/2?'drag-after':'drag-before');}
}
function journalGrowthPointerEnd(event){
  if(!_journalGrowthPointer||event.pointerId!==_journalGrowthPointer.pointerId)return;event.preventDefault();var state=_journalGrowthPointer,card=state.target,after=false;if(card){var rect=card.getBoundingClientRect();after=event.clientY>rect.top+rect.height/2;}state.handle.classList.remove('dragging');_journalGrowthPointer=null;document.removeEventListener('pointermove',journalGrowthPointerMove);document.removeEventListener('pointerup',journalGrowthPointerEnd);document.removeEventListener('pointercancel',journalGrowthPointerEnd);document.querySelectorAll('.journal-schedule-item.drag-before,.journal-schedule-item.drag-after').forEach(function(el){el.classList.remove('drag-before','drag-after');});if(card)journalScheduleAddGrowth(state.kind,card.dataset.scheduleId,after);
}
function journalScheduleOverridesKey(dateKey){return'journal_schedule_overrides_'+dateKey;}
function journalScheduleMoves(){try{var value=JSON.parse(localStorage.getItem('journal_schedule_moves')||'[]');return Array.isArray(value)?value:[];}catch(ignore){return[];}}
function journalScheduleParseTime(value){
  var text=String(value||'').trim().toUpperCase(),match=text.match(/^(\d{1,2}):(\d{2})\s*(AM|PM)$/);if(match){var h=+match[1],m=+match[2];if(h<1||h>12||m>59)return null;return(h%12+(match[3]==='PM'?12:0))*60+m;}
  match=text.match(/^(\d{1,2}):(\d{2})$/);if(match){var h24=+match[1],m24=+match[2];if(h24>23||m24>59)return null;return h24*60+m24;}return null;
}
function journalScheduleAskCopyDate(label){
  var currentDate=dk(today),tomorrow=new Date(today);tomorrow.setDate(tomorrow.getDate()+1);
  var targetDate=prompt('Copy '+label+' to another day. Enter the date as YYYY-MM-DD.\nTomorrow is '+dk(tomorrow)+'.',dk(tomorrow));
  if(targetDate===null)return'';targetDate=String(targetDate).trim();
  if(!/^\d{4}-\d{2}-\d{2}$/.test(targetDate)||isNaN(new Date(targetDate+'T12:00:00').getTime())){alert('Please enter a valid date in YYYY-MM-DD format.');return'';}
  if(targetDate===currentDate){alert('Please choose a different day.');return'';}return targetDate;
}
function journalScheduleCopyItem(event,id){
  event.preventDefault();event.stopPropagation();_journalScheduleSuppressClick=Date.now()+600;
  var item=_journalScheduleItemsById[id];if(!item)return;var targetDate=journalScheduleAskCopyDate('"'+item.label+'"');if(!targetDate)return;
  if(item.growthKind){
    localStorage.setItem(journalScheduleGrowthKey(item.growthKind,targetDate),'1');
    var growthOverrides={};try{growthOverrides=JSON.parse(localStorage.getItem(journalScheduleOverridesKey(targetDate))||'{}');}catch(ignore){}
    growthOverrides[journalScheduleGrowthId(item.growthKind)]={time:item.time,mins:Number(item.mins),label:item.label,detail:item.detail,resource:item.resource||'',link:item.link||'',notes:item.notes||'',loggedTime:''};localStorage.setItem(journalScheduleOverridesKey(targetDate),JSON.stringify(growthOverrides));
  }else{
    var copies=journalManualScheduleItems();copies.push({id:'manual-'+Date.now(),date:targetDate,time:item.time,mins:Number(item.mins),title:item.label,note:item.detail||'Copied schedule item'});localStorage.setItem('journal_manual_schedule_items',JSON.stringify(copies));
  }
  v26Toast('Copied to '+targetDate);
}
function journalScheduleCopyDay(event){
  if(event){event.preventDefault();event.stopPropagation();}var sourceDate=dk(today),targetDate=journalScheduleAskCopyDate('this schedule');if(!targetDate)return;
  var sourceManual=journalManualScheduleItems().filter(function(item){return item.date===sourceDate;}),allManual=journalManualScheduleItems(),stamp=Date.now();
  sourceManual.forEach(function(item,index){allManual.push({id:'manual-'+stamp+'-'+index,date:targetDate,time:item.time,mins:Number(item.mins),title:item.title,note:item.note||''});});
  localStorage.setItem('journal_manual_schedule_items',JSON.stringify(allManual));
  ['language','guitar','reading'].forEach(function(kind){if(localStorage.getItem(journalScheduleGrowthKey(kind,sourceDate))==='1')localStorage.setItem(journalScheduleGrowthKey(kind,targetDate),'1');});
  ['journal_schedule_order_','journal_schedule_overrides_','journal_schedule_hidden_','journal_schedule_status_'].forEach(function(prefix){var value=localStorage.getItem(prefix+sourceDate);if(value!=null)localStorage.setItem(prefix+targetDate,value);});
  var copiedOverrides={};try{copiedOverrides=JSON.parse(localStorage.getItem(journalScheduleOverridesKey(targetDate))||'{}');}catch(ignoreCopy){}
  Object.keys(copiedOverrides).forEach(function(id){var value=copiedOverrides[id];if(!value||typeof value!=='object')return;value.entryId='';value.loggedTime='';if(/^schedule-(language-practice|guitar|reading-audiobook)$/.test(id))value.detail=journalGrowthDetailText(value.label||'',value.resource||'',value.link||'',value.notes||'','');});
  localStorage.setItem(journalScheduleOverridesKey(targetDate),JSON.stringify(copiedOverrides));
  v26Toast('Entire schedule copied to '+targetDate);
}
function journalScheduleEdit(event,id){
  event.preventDefault();event.stopPropagation();_journalScheduleSuppressClick=Date.now()+600;var item=_journalScheduleItemsById[id];if(!item)return;
  // Use the same complete Growth Activities form whether the activity is
  // opened from the Growth section or from its scheduled card.
  if(item.growthKind){journalOpenGrowthLog(item.growthKind);return;}
  var editedLabel=item.label,editedDetail=item.detail;
  var enteredTime=prompt('Set the time for "'+item.label+'". Use 6:30 PM or 18:30.',item.time);if(enteredTime===null)return;var mins=journalScheduleParseTime(enteredTime);if(mins===null){alert('Please enter a valid time, such as 6:30 PM or 18:30.');return;}
  var currentDate=dk(today),tomorrow=new Date(today);tomorrow.setDate(tomorrow.getDate()+1);var targetDate=prompt('Keep '+currentDate+' or enter another date as YYYY-MM-DD.\nTomorrow is '+dk(tomorrow)+'.',currentDate);if(targetDate===null)return;targetDate=String(targetDate).trim();if(!/^\d{4}-\d{2}-\d{2}$/.test(targetDate)||isNaN(new Date(targetDate+'T12:00:00').getTime())){alert('Please enter a valid date in YYYY-MM-DD format.');return;}
  if(item._moveId){var existingMoves=journalScheduleMoves(),existing=existingMoves.find(function(move){return move.id===item._moveId;});if(existing){existing.targetDate=targetDate;existing.time=journalMinsToClock(mins);existing.mins=mins;existing.item.label=editedLabel;existing.item.detail=editedDetail;existing.item.resource=item.resource||'';existing.item.link=item.link||'';existing.item.notes=item.notes||'';localStorage.setItem('journal_schedule_moves',JSON.stringify(existingMoves));}}
  else if(targetDate!==currentDate){var moves=journalScheduleMoves();moves.push({id:'moved-'+Date.now()+'-'+item.id,sourceDate:currentDate,targetDate:targetDate,originalId:item.id,time:journalMinsToClock(mins),mins:mins,item:{label:editedLabel,detail:editedDetail,resource:item.resource||'',link:item.link||'',notes:item.notes||'',tab:item.tab,fixed:!!item.fixed,manual:item.manual||'',sleepRoutineChecklist:!!item.sleepRoutineChecklist,detailKind:item.detailKind||'',growthKind:item.growthKind||'',manualCreated:!!item.manualCreated,manualItemId:item.manualItemId||''}});localStorage.setItem('journal_schedule_moves',JSON.stringify(moves));}
  else{var overrides={};try{overrides=JSON.parse(localStorage.getItem(journalScheduleOverridesKey(currentDate))||'{}');}catch(ignore){}var existingOverride=overrides[item.id]&&typeof overrides[item.id]==='object'?overrides[item.id]:{};overrides[item.id]=Object.assign({},existingOverride,{time:journalMinsToClock(mins),mins:mins,label:editedLabel,detail:editedDetail,resource:item.resource||existingOverride.resource||'',link:item.link||existingOverride.link||'',notes:item.notes||existingOverride.notes||''});localStorage.setItem(journalScheduleOverridesKey(currentDate),JSON.stringify(overrides));var chronological=Object.keys(_journalScheduleItemsById).map(function(key){return _journalScheduleItemsById[key];});chronological.forEach(function(entry){if(entry.id===item.id){entry.mins=mins;entry.time=journalMinsToClock(mins);}});chronological.sort(function(a,b){var am=Number(a.mins),bm=Number(b.mins);if(!Number.isFinite(am))am=1440;if(!Number.isFinite(bm))bm=1440;return am-bm;});localStorage.setItem(journalScheduleOrderKey(currentDate),JSON.stringify(chronological.map(function(entry){return entry.id;})));}
  renderJournalSchedule();v26Toast(targetDate===currentDate?'Event updated':'Moved to '+targetDate);
}
function journalScheduleApplyOrder(items,dateKey){
  var saved=[];try{saved=JSON.parse(localStorage.getItem(journalScheduleOrderKey(dateKey))||'[]');}catch(ignore){}
  if(!Array.isArray(saved)||!saved.length)return items;
  var byId={};items.forEach(function(item){byId[item.id]=item;});var ordered=[];
  saved.forEach(function(id){if(byId[id]){ordered.push(byId[id]);delete byId[id];}});
  // Any item that isn't part of the saved drag order yet (a brand-new schedule item, a newly
  // due Growth Activity, etc.) is inserted at its correct chronological spot among the
  // already-ordered items -- not appended after everything else regardless of its time.
  items.forEach(function(item){
    if(!byId[item.id])return;
    delete byId[item.id];
    var insertAt=ordered.length,itemMins=Number(item.mins);if(!Number.isFinite(itemMins))itemMins=1440;
    for(var i=0;i<ordered.length;i++){var otherMins=Number(ordered[i].mins);if(!Number.isFinite(otherMins))otherMins=1440;if(otherMins>itemMins){insertAt=i;break;}}
    ordered.splice(insertAt,0,item);
  });
  return ordered;
}
function journalScheduleDragStart(event,id){
  _journalScheduleDragged=id;_journalScheduleSuppressClick=Date.now()+600;event.currentTarget.classList.add('dragging');
  if(event.dataTransfer){event.dataTransfer.effectAllowed='move';event.dataTransfer.setData('text/plain',id);}
}
function journalScheduleDragOver(event){
  event.preventDefault();var card=event.currentTarget;if(card.dataset.scheduleId===_journalScheduleDragged)return;
  document.querySelectorAll('.journal-schedule-item.drag-before,.journal-schedule-item.drag-after').forEach(function(el){el.classList.remove('drag-before','drag-after');});
  var rect=card.getBoundingClientRect(),after=event.clientY>rect.top+rect.height/2;card.classList.add(after?'drag-after':'drag-before');if(event.dataTransfer)event.dataTransfer.dropEffect='move';
}
function journalScheduleDrop(event,targetId){
  event.preventDefault();var sourceId=_journalScheduleDragged||(event.dataTransfer&&event.dataTransfer.getData('text/plain'));if(!sourceId||sourceId===targetId){journalScheduleDragEnd();return;}
  var cards=Array.from(document.querySelectorAll('#journalScheduleBody .journal-schedule-item')),order=cards.map(function(card){return card.dataset.scheduleId;}),from=order.indexOf(sourceId),to=order.indexOf(targetId);
  if(from<0||to<0){journalScheduleDragEnd();return;}
  var rect=event.currentTarget.getBoundingClientRect(),after=event.clientY>rect.top+rect.height/2;order.splice(from,1);to=order.indexOf(targetId)+(after?1:0);order.splice(to,0,sourceId);
  localStorage.setItem(journalScheduleOrderKey(dk(today)),JSON.stringify(order));journalScheduleDragEnd();renderJournalSchedule();v26Toast('Schedule order saved');
}
function journalScheduleDragEnd(){_journalScheduleDragged='';document.querySelectorAll('.journal-schedule-item.dragging,.journal-schedule-item.drag-before,.journal-schedule-item.drag-after').forEach(function(el){el.classList.remove('dragging','drag-before','drag-after');});}
function journalScheduleMoveByArrow(event,id,direction){
  event.preventDefault();event.stopPropagation();_journalScheduleSuppressClick=Date.now()+600;
  var cards=Array.from(document.querySelectorAll('#journalScheduleBody .journal-schedule-item')),order=cards.map(function(card){return card.dataset.scheduleId;}),index=order.indexOf(id),next=index+Number(direction);
  if(index<0||next<0||next>=order.length)return;
  var swap=order[next];order[next]=order[index];order[index]=swap;
  localStorage.setItem(journalScheduleOrderKey(dk(today)),JSON.stringify(order));renderJournalSchedule();v26Toast(direction<0?'Moved up':'Moved down');
}
function journalScheduleCardClick(action){if(Date.now()<_journalScheduleSuppressClick)return;if(action)Function(action)();}
function renderJournalSchedule(){
  var body=document.getElementById('journalScheduleBody'),summary=document.getElementById('journalScheduleSummary');if(!body)return;
  removeObsoleteQuickGrowthEntries();
  var d=journalViewData(),dateKey=dk(today),training=journalTodayTrainingSummary(),growth=journalScheduleGrowth(dateKey);
  var languageEntries=getSkillEntries().filter(function(entry){return entry.date===dateKey&&entry.category==='Language';});if(languageEntries.length>1)languageEntries=[languageEntries[languageEntries.length-1]];
  var languageDone=languageEntries.length>0;
  var languageDetail=languageDone?languageEntries.map(function(entry){return entry.name+(entry.time?' ('+entry.time+')':'');}).join(', '):'Add today\'s language practice from My Schedule';
  var guitarEntries=getSkillEntries().filter(function(entry){return entry.date===dateKey&&/^guitar$/i.test(String(entry.category||''));});
  var readingEntries=getSkillEntries().filter(function(entry){return entry.date===dateKey&&/^(reading \/ audiobook|reading|audiobook)$/i.test(String(entry.category||''));});
  var languageMinutes=languageEntries.reduce(function(total,entry){return total+parseSkillTimeMins(entry.time);},0),guitarMinutes=guitarEntries.reduce(function(total,entry){return total+parseSkillTimeMins(entry.time);},0),readingMinutes=readingEntries.length?parseSkillTimeMins(readingEntries[readingEntries.length-1].time):0;
  var now=new Date(),isSelectedToday=dk(now)===dateKey,currentMinutes=now.getHours()*60+now.getMinutes(),selectedDay=new Date(dateKey+'T00:00:00'),realDay=new Date(now.getFullYear(),now.getMonth(),now.getDate()),isPastDay=selectedDay<realDay;
  var wellnessKeys=['wBP','wMeds','wWght','wSleep'],wellnessRecorded=journalLinkedGreenCount(wellnessKeys,d);
  var faithKeys=['spDailyBread','spBibleAudio','spWorship','spAIPrayer'],faithDone=journalLinkedGreenCount(faithKeys,d);
  var loggedRun=(getActivities()||[]).some(function(activity){return activity&&activity.date===dateKey&&/run/i.test([activity.type,activity.title].filter(Boolean).join(' '));});
  var noTraining=!String(training||'').trim(),restDay=noTraining||/rest day|no workout scheduled/i.test(training),activityDone=loggedRun||restDay;
  // Training-plan text doesn't always use the literal word "run" (e.g. "Secondary Long 7 mi @
  // sustained tempo pace"), so assume any scheduled workout is a run unless it's a rest day or
  // explicitly a non-running session — matching the same assumption journalTodayTrainingMinutesEstimate
  // already makes when it falls back to a default duration.
  var runToday=(!restDay&&!!training&&!/strength/i.test(training)&&!/cross[- ]training/i.test(training))||getActivities().some(function(a){return a&&a.date===dateKey&&a.type==='Run';});
  // Today's training is slotted in the evening (after Dinner); the label/detail/done
  // logic below still pulls live from the actual training plan for the day.
  var trainingStartMins=1260;
  var items=[
    {time:'8:00 AM',mins:480,label:'Review priorities, goals, calendar, email and schedule',detail:'Check complete after your morning review',done:!!d.reviewCheck,tab:'daily',checkId:'reviewCheck'},
    {time:'8:05 AM',mins:485,label:'Personal Hygiene',detail:'Shower, brush teeth, get ready for the day',done:localStorage.getItem('schedule_personalHygiene_'+dateKey)==='1',manual:'personalHygiene'},
    {time:'8:15 AM',mins:495,label:'Meditation',detail:journalMeditationTodayLine(),done:d.spMeditation==='green',tab:'meditate',detailKind:'meditation',linkedCheckKey:'spMeditation'},
    {time:'8:20 AM',mins:500,label:'Wellness check and water',detail:wellnessRecorded+' of 4 wellness items complete',done:wellnessRecorded===4,partial:wellnessRecorded>0&&wellnessRecorded<4,tab:'wellness',detailKind:'wellness'},
    {time:'8:30 AM',mins:510,label:'Bible, worship & prayer',detail:faithDone+' of 4 faith practices complete',done:faithDone===4,partial:faithDone>0&&faithDone<4,tab:'bible',detailKind:'faith'},
    {time:'9:30 AM',mins:570,label:'Work',detail:'9:30 AM – 6:00 PM',done:true,tab:'daily',skip:!(today.getDay()>=1&&today.getDay()<=5),fixed:true},
    {time:'12:00 PM',mins:720,label:'Walk Buddy',detail:'Take Buddy for a walk',done:d.exDogWalk==='green',tab:'daily',linkedCheckKey:'exDogWalk'},
    {time:journalMinsToClock(trainingStartMins),mins:trainingStartMins,label:restDay?'Rest day — no training scheduled':'Today’s training',detail:restDay?'Automatically complete because no workout is scheduled':training,done:activityDone,tab:'daily'},
    {time:'11:30 PM',mins:1410,label:'Prepare priorities for tomorrow',detail:'Close the day intentionally',done:localStorage.getItem('schedule_eveningPlanning_'+dateKey)==='1'||!!d.prepJournalCheck,tab:'daily',manual:'eveningPlanning'}
  ].filter(function(item){return !item.skip;});
  // Growth habits are placed directly onto My Schedule on the days selected in Habits. A manually
  // scheduled activity is also retained even when it is not a normal habit day. Defaults are staggered
  // between 4:00 PM and 7:45 PM; opening Schedule / Log still lets the user choose a different time.
  var dueGrowthKinds=new Set(journalGrowthHabitKindsForDate(dateKey));
  var growthScheduleSpecs={
    reading:{time:'4:45 PM',mins:1005,label:'Audiobook',detail:readingMinutes?fmtMinsSeconds(readingMinutes)+' logged':'Tap Schedule / Log to add listening time',done:readingMinutes>0||d.gaAudiobook==='green',tab:'library',linkedCheckKey:'gaAudiobook'},
    language:{time:'7:00 PM',mins:1140,label:'Language Practice',detail:languageMinutes?fmtMinsSeconds(languageMinutes)+' logged':'Tap to log the language session',done:d.gaLanguage==='green',tab:'skill-log'},
    guitar:{time:'7:45 PM',mins:1185,label:'Guitar',detail:guitarMinutes?fmtMinsSeconds(guitarMinutes)+' logged':'Tap to log guitar practice',done:d.gaGuitar==='green',tab:'skill-log'}
  };
  ['reading'].forEach(function(kind){   // Language / Guitar removed (Nov 2026)
    if(!dueGrowthKinds.has(kind)&&localStorage.getItem(journalScheduleGrowthKey(kind,dateKey))!=='1'&&!growthScheduleSpecs[kind].done)return;
    items.push(Object.assign({id:journalScheduleGrowthId(kind),growthKind:kind},growthScheduleSpecs[kind]));
  });
  journalManualScheduleItems().filter(function(entry){return entry.date===dateKey;}).forEach(function(entry){items.push({id:'schedule-'+entry.id,time:entry.time,mins:Number(entry.mins),label:entry.title,detail:entry.note||'Manual schedule item',done:localStorage.getItem('schedule_manualSchedule_'+entry.id+'_'+dateKey)==='1',tab:'daily',manual:'manualSchedule_'+entry.id,manualCreated:true,manualItemId:entry.id});});
  items.forEach(function(item,index){if(!item.id)item.id='schedule-'+String(item.label||index).toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');});
  var hiddenSchedule=[];try{hiddenSchedule=JSON.parse(localStorage.getItem(journalScheduleHiddenKey(dateKey))||'[]');}catch(ignoreHidden){}if(Array.isArray(hiddenSchedule)&&hiddenSchedule.length)items=items.filter(function(item){return hiddenSchedule.indexOf(item.id)===-1;});
  var scheduleMoves=journalScheduleMoves();
  items=items.filter(function(item){return !scheduleMoves.some(function(move){return move.sourceDate===dateKey&&move.originalId===item.id;});});
  scheduleMoves.filter(function(move){return move.targetDate===dateKey;}).forEach(function(move){var moved=Object.assign({},move.item||{});moved.id=move.id;moved._moveId=move.id;moved.time=move.time;moved.mins=move.mins;moved.done=false;items.push(moved);});
  var scheduleOverrides={};try{scheduleOverrides=JSON.parse(localStorage.getItem(journalScheduleOverridesKey(dateKey))||'{}');}catch(ignore){}
  items.forEach(function(item){var override=item._moveId?scheduleMoves.find(function(move){return move.id===item._moveId;}):scheduleOverrides[item.id];if(override){if(override.time){item.time=override.time;item.mins=Number(override.mins);}if(!item.detailKind&&!item.sleepRoutineChecklist&&typeof override.label==='string'&&override.label.trim())item.label=override.label;if(!item.detailKind&&!item.sleepRoutineChecklist&&typeof override.detail==='string')item.detail=override.detail;if(typeof override.link==='string')item.link=override.link;if(typeof override.resource==='string')item.resource=override.resource;if(typeof override.notes==='string')item.notes=override.notes;}});
  // Sleep-routine completion is always live. A saved time/label edit from an
  // earlier version must never freeze the displayed count at an old value.
  items.forEach(function(item){if(item.sleepRoutineChecklist){var liveSleep=journalSleepRoutineState(dateKey);item.label='Begin sleep routine';item.detail=liveSleep.count+' of 6 routine steps completed';item.done=liveSleep.count>=4;}});
  // Backward compatibility: activities scheduled before detailed overrides existed
  // still have their topic/resource/link/notes in the Growth Log. Join those records
  // by date and Growth kind every time My Schedule renders.
  items.forEach(function(item){
    if(!item.growthKind)return;var saved=journalGrowthSavedDetails(item.growthKind,dateKey);
    if(!saved||!Object.keys(saved).length)return;
    if(saved.name&&(!item.label||/^Language Practice$|^Guitar$|^Audiobook$/.test(item.label)))item.label=saved.name;
    item.resource=saved.resource||item.resource||'';item.link=saved.link||item.link||'';item.notes=saved.notes||item.notes||'';
    var joinedDetail=journalGrowthDetailText(item.label,item.resource,item.link,item.notes,saved.loggedTime||'');
    if(joinedDetail)item.detail=joinedDetail;
  });
  // A manual time override should immediately place the card in chronological order.
  // Modern JavaScript uses a stable sort, so cards sharing the same time retain their
  // existing drag order.
  items.sort(function(a,b){var am=Number(a.mins),bm=Number(b.mins);if(!Number.isFinite(am))am=1440;if(!Number.isFinite(bm))bm=1440;return am-bm;});
  // Drag and arrow order is applied after time sorting so it survives every render.
  // Editing a time rewrites the saved order chronologically before rendering.
  items=journalScheduleApplyOrder(items,dateKey);
  _journalScheduleItemsById={};items.forEach(function(item){_journalScheduleItemsById[item.id]=item;});
  var growthPoolStatus=document.getElementById('financialVideoScheduleStatus');if(growthPoolStatus)growthPoolStatus.textContent='Scheduled activities appear first';
  ['reading'].forEach(function(kind){var card=document.querySelector('.journal-growth-pool-card[data-growth-kind="'+kind+'"]');if(card)card.style.display='grid';});
  var languageStatus=document.getElementById('growthLanguageStatus');if(languageStatus)languageStatus.textContent=(languageMinutes?fmtMinsSeconds(languageMinutes):'No time logged')+(localStorage.getItem(journalScheduleGrowthKey('language',dateKey))==='1'?' • scheduled':'');
  var guitarStatus=document.getElementById('growthGuitarStatus');if(guitarStatus)guitarStatus.textContent=(guitarMinutes?fmtMinsSeconds(guitarMinutes):'No time logged')+(localStorage.getItem(journalScheduleGrowthKey('guitar',dateKey))==='1'?' • scheduled':'');
  var readingStatus=document.getElementById('growthReadingStatus');if(readingStatus)readingStatus.textContent=(readingMinutes?fmtMinsSeconds(readingMinutes):'No Library time logged')+(localStorage.getItem(journalScheduleGrowthKey('reading',dateKey))==='1'?' · scheduled':'');
  // Group scheduled activities first while preserving the original order inside each
  // group. Completion checkmarks only change styling and never move a card.
  var growthPool=document.querySelector('.journal-growth-pool');if(growthPool){
    var growthComplete={language:d.gaLanguage==='green',guitar:d.gaGuitar==='green',reading:readingMinutes>0||d.gaAudiobook==='green'};
    var growthCards=Array.from(growthPool.querySelectorAll('.journal-growth-pool-card')),growthHelp=growthPool.querySelector('.journal-growth-help');
    growthCards.sort(function(a,b){var aScheduled=localStorage.getItem(journalScheduleGrowthKey(a.dataset.growthKind,dateKey))==='1',bScheduled=localStorage.getItem(journalScheduleGrowthKey(b.dataset.growthKind,dateKey))==='1';return Number(bScheduled)-Number(aScheduled);});
    growthCards.forEach(function(card){card.classList.toggle('completed',!!growthComplete[card.dataset.growthKind]);growthPool.insertBefore(card,growthHelp);});
  }
  var scheduleStatuses=journalScheduleStatuses(dateKey);items.forEach(function(item){var hasManual=Object.prototype.hasOwnProperty.call(scheduleStatuses,item.id),manual=hasManual?scheduleStatuses[item.id]:'';item.scheduleStatus=(item.detailKind||item.sleepRoutineChecklist||item.linkedCheckKey||item.manual)?(item.done?'green':item.partial?'purple':journalCheckState(d,item.linkedCheckKey)==='red'?'red':''):hasManual?(manual==='blank'?'':manual):(item.done?'green':'');item.done=item.scheduleStatus==='green';item.partial=item.scheduleStatus==='purple';});
  var trackable=items.filter(function(item){return !item.fixed;});
  var complete=trackable.filter(function(item){return item.done;}).length,next=-1;
  if(isSelectedToday){for(var i=0;i<items.length;i++){if(!items[i].fixed&&!items[i].done&&items[i].mins>=currentMinutes){next=i;break;}}if(next<0)next=items.findIndex(function(item){return !item.fixed&&!item.done;});}
  if(summary)summary.textContent=complete+'/'+trackable.length+' complete';
  var progressPercent=trackable.length?Math.round((complete/trackable.length)*100):0;
  var progressFill=document.getElementById('journalDayProgressFill'),progressText=document.getElementById('journalDayProgressText'),progressTrack=document.getElementById('journalDayProgressTrack');
  if(progressFill)progressFill.style.width=progressPercent+'%';
  if(progressText)progressText.textContent=progressPercent+'% \u2022 '+complete+' of '+trackable.length+' complete';
  if(progressTrack)progressTrack.setAttribute('aria-valuenow',String(progressPercent));
  var nextText=next>=0?'Next: '+items[next].time+' — '+items[next].label:(complete===trackable.length?'Schedule complete':'Review remaining items');
  body.innerHTML='<div class="journal-schedule-status"><strong>'+escHtml(nextText)+'</strong><span>Drag to reorder • Edit or copy events • '+complete+' complete • '+(trackable.length-complete)+' remaining</span><span style="display:flex;gap:5px;flex-wrap:wrap;"><button type="button" onclick="journalScheduleCopyDay(event)" style="border:1px solid #2563eb;background:#fff;color:#1d4ed8;border-radius:6px;padding:5px 9px;font-size:.68rem;font-weight:800;cursor:pointer;">Copy Day</button><button type="button" onclick="event.preventDefault();event.stopPropagation();journalOpenManualScheduleItem()" style="border:1px solid #4f46e5;background:#4f46e5;color:#fff;border-radius:6px;padding:5px 9px;font-size:.68rem;font-weight:800;cursor:pointer;">+ Schedule Item</button></span></div><div class="journal-schedule-list">'+items.map(function(item,index){var inlineGrowth=['language','guitar'].indexOf(item.growthKind)!==-1,action=item.fixed?'':inlineGrowth?'journalOpenGrowthLog(\''+item.growthKind+'\')':item.detailKind?'journalToggleScheduleDetail(\''+item.detailKind+'\')':item.sleepRoutineChecklist?'journalToggleSleepRoutinePanel()':(item.manual?'journalToggleScheduleManual(\''+item.manual+'\')':'switchTab(\''+item.tab+'\',null)');var safeAction=JSON.stringify(action).replace(/"/g,'&quot;'),removeAction=item.manualCreated?'<span class="journal-schedule-remove" role="button" tabindex="0" onclick="journalDeleteManualScheduleItem(event,\''+item.manualItemId+'\',\''+(item._moveId||'')+'\')">Remove</span>':item.growthKind?'<span class="journal-schedule-remove" role="button" tabindex="0" onclick="journalScheduleRemoveGrowth(event,\''+item.growthKind+'\',\''+escHtml(item.id)+'\')">Remove</span>':'',copyAction='<span class="journal-schedule-copy" role="button" tabindex="0" onclick="journalScheduleCopyItem(event,\''+escHtml(item.id)+'\')">Copy</span>',linkedKey=item.growthKind==='reading'?'gaAudiobook':item.growthKind==='language'?'gaLanguage':item.growthKind==='guitar'?'gaGuitar':'',checkClick=item.checkId?' onclick="journalToggleHiddenCheck(event,\''+item.checkId+'\')" title="Toggle completion"':linkedKey?' onclick="journalScheduleToggleLinkedCheck(event,\''+linkedKey+'\')" title="Toggle completion"':'';return '<button type="button" draggable="true" data-schedule-id="'+escHtml(item.id)+'" class="journal-schedule-item '+(item.fixed?'fixed ':(item.done?'done ':''))+(index===next?'next':'')+'" onclick="journalScheduleCardClick('+safeAction+')" ondragstart="journalScheduleDragStart(event,\''+escHtml(item.id)+'\')" ondragover="journalScheduleDragOver(event)" ondrop="journalScheduleDrop(event,\''+escHtml(item.id)+'\')" ondragend="journalScheduleDragEnd()"><span class="journal-schedule-grip" title="Drag to reschedule" aria-hidden="true">&#x22EE;&#x22EE;</span><span class="journal-schedule-time">'+item.time+'</span><span class="journal-schedule-check"'+checkClick+'>'+(item.fixed?'&#x1F4BC;':(item.done?'&#x2713;':''))+'</span><span class="journal-schedule-name">'+escHtml(item.label)+'<span class="journal-schedule-detail">'+escHtml(item.detail)+'</span></span><span class="journal-schedule-actions">'+copyAction+'<span class="journal-schedule-edit" role="button" tabindex="0" onclick="journalScheduleEdit(event,\''+escHtml(item.id)+'\')">Edit</span>'+removeAction+'</span></button>'+(item.detailKind?journalScheduleDetailHtml(item.detailKind,d):'')+(item.sleepRoutineChecklist?journalSleepRoutineChecklistHtml(dateKey):'');}).join('')+'</div>'+journalEndSummaryHtml();
  var scheduleHelp=body.querySelector('.journal-schedule-status span');if(scheduleHelp)scheduleHelp.textContent='Drag, use arrows, or edit the time • '+complete+' complete • '+(trackable.length-complete)+' remaining';
  body.querySelectorAll('.journal-schedule-item').forEach(function(card,index,list){
    var item=_journalScheduleItemsById[card.dataset.scheduleId],actions=card.querySelector('.journal-schedule-actions');if(!item||!actions)return;
    var upClass=index===0?' disabled':'',downClass=index===list.length-1?' disabled':'';
    actions.insertAdjacentHTML('afterbegin','<span class="journal-schedule-move'+upClass+'" role="button" tabindex="0" aria-label="Move '+escHtml(item.label)+' up" data-move-id="'+escHtml(item.id)+'" data-move-direction="-1">&#x2191;</span><span class="journal-schedule-move'+downClass+'" role="button" tabindex="0" aria-label="Move '+escHtml(item.label)+' down" data-move-id="'+escHtml(item.id)+'" data-move-direction="1">&#x2193;</span>');
  });
  body.querySelectorAll('.journal-schedule-move').forEach(function(control){
    var activate=function(event){event.preventDefault();event.stopPropagation();if(control.classList.contains('disabled'))return;journalScheduleMoveByArrow(event,control.dataset.moveId,Number(control.dataset.moveDirection));};
    control.addEventListener('pointerdown',function(event){event.stopPropagation();});
    control.addEventListener('pointerup',activate);
    control.addEventListener('keydown',function(event){if(event.key==='Enter'||event.key===' '){activate(event);}});
  });
  // Every scheduled Growth card opens the same structured Schedule / Log form.
  body.querySelectorAll('.journal-schedule-item').forEach(function(card){var growthItem=_journalScheduleItemsById[card.dataset.scheduleId];if(growthItem&&growthItem.growthKind)card.onclick=function(){journalOpenGrowthLog(growthItem.growthKind);};});
  body.querySelectorAll('.journal-schedule-item').forEach(function(card){var partialItem=_journalScheduleItemsById[card.dataset.scheduleId],partialCheck=card.querySelector('.journal-schedule-check');if(partialItem&&partialItem.partial&&!partialItem.done&&!partialItem.fixed){card.classList.add('partial');if(partialCheck)partialCheck.innerHTML='&#x2713;';}});
  body.querySelectorAll('.journal-schedule-item').forEach(function(card){var item=_journalScheduleItemsById[card.dataset.scheduleId],check=card.querySelector('.journal-schedule-check');if(item&&check&&!item.fixed){check.removeAttribute('onclick');if(item.manual&&item.manual!=='eveningPlanning'){check.title='Tap to mark complete / not complete';check.onclick=function(event){event.preventDefault();event.stopPropagation();_journalScheduleSuppressClick=Date.now()+600;journalToggleScheduleManual(item.manual);};}else{check.title='Tap: green complete, red not completed, then clear';check.onclick=function(event){journalScheduleCycleStatus(event,item.id);};}if(item.scheduleStatus==='red'){card.classList.add('red');check.innerHTML='&#x2713;';}}if(!item||item.manualCreated||item.growthKind)return;var actions=card.querySelector('.journal-schedule-actions');if(actions)actions.insertAdjacentHTML('beforeend','<span class="journal-schedule-remove" role="button" tabindex="0" onclick="journalDeleteScheduleItem(event,\''+escHtml(item.id)+'\')">Delete</span>');});
}
function renderJournalHome(){
  var grid=document.getElementById('journalHomeGrid');if(!grid){renderJournalSchedule();return;}
  var prefs=journalPrefs(),visible=Array.isArray(prefs.homeCards)?prefs.homeCards:JOURNAL_UI_CONFIG.homeCards;
  var d=journalViewData(),cards={};
  var priorityText=['pt0t','pt1t','pt2t'].map(function(k){return(d[k]||'').trim();}).filter(Boolean);
  var goalText=['dg0t','dg1t','dg2t'].map(function(k){return(d[k]||'').trim();}).filter(Boolean);
  cards.priorities=journalHomeCard('priorities','Priorities & Goals','<strong>'+priorityText.length+'/3</strong> priorities set &nbsp;•&nbsp; <strong>'+goalText.length+'/3</strong> goals set');
  var checks=collectDailyChecks(),done=checks.filter(function(i){return i.status==='green';}).length,open=checks.filter(function(i){return i.status==='blank';}).length;
  cards.checkins=journalHomeCard('checkins','Daily Check-In','<strong>'+done+'</strong> complete &nbsp;•&nbsp; <strong>'+open+'</strong> still blank');
  var training=journalTodayTrainingSummary();
  cards.training=journalHomeCard('training','Today’s Training',escHtml(String(training).slice(0,150)));
  var wellness=[d.wBPVal?'BP '+d.wBPVal:'',d.wSleepVal?'Sleep '+d.wSleepVal+' hr':'',d.wWghtVal?'Weight '+d.wWghtVal:''].filter(Boolean);
  cards.wellness=journalHomeCard('wellness','Wellness',wellness.length?escHtml(wellness.join(' • ')):'No wellness metrics entered');
  var hasReflection=[d.jAccomplish,d.jImprov,d.jGratitude,d.jNotes].some(function(v){return v&&String(v).trim();});
  var handwritingRaw=localStorage.getItem('handwriting_'+dk(today)),hasHandwriting=false;
  try{hasHandwriting=Array.isArray(JSON.parse(handwritingRaw||'[]'))&&JSON.parse(handwritingRaw||'[]').length>0;}catch(error){}
  var hasTabNotes=!!String(localStorage.getItem('tabNote_daily')||'').trim();
  var dailyNotes=[hasReflection,hasHandwriting,hasTabNotes].filter(Boolean).length;
  cards.notes=journalHomeCard('notes','Notes & Review','<strong>'+dailyNotes+'</strong> note area'+(dailyNotes===1?'':'s')+' with content');
  cards.streaks=journalHomeCard('streaks','Current Streaks',journalHomeStreakSummary());
  grid.innerHTML=visible.map(function(id){return cards[id]||'';}).join('')||'<div class="home-value" style="padding:8px;color:#667085;">Choose Home cards in Settings.</div>';
  var date=document.getElementById('journalHomeDate');if(date)date.textContent=fmtD(today);
  renderJournalSchedule();
}
function refreshJournalNavStatus(){
  document.querySelectorAll('.tab-btn').forEach(function(btn){
    var m=(btn.getAttribute('onclick')||'').match(/switchTab\('([^']+)'/),tab=m&&m[1];
    btn.classList.toggle('nav-has-note',!!(tab&&localStorage.getItem('tabNote_'+tab)));
  });
  document.querySelectorAll('.hm-nav-btn').forEach(function(btn){
    var tab=btn.id.replace(/^hm-/,'');
    btn.classList.toggle('nav-has-note',!!localStorage.getItem('tabNote_'+tab));
  });
}
function openMobileCheckIn(){
  v26GoToday();
  setTimeout(function(){
    var body=document.getElementById('dailyCheckOverviewBody');
    if(body&&body.style.display!=='block')toggleDailyCheckOverview();
    var box=document.querySelector('.check-overview');if(box)box.scrollIntoView({behavior:'smooth',block:'start'});
  },80);
}
function journalStorageHealth(){
  var bytes=0,externalBytes=0,planner=0,invalid=0,sizes=[];
  for(var i=0;i<localStorage.length;i++){
    var k=localStorage.key(i),v=localStorage.getItem(k)||'';
    var kb=(k.length+v.length)*2;
    if(journalIsExternalAppKey(k)){externalBytes+=kb;continue;}
    bytes+=kb;
    sizes.push({key:k,bytes:kb});
    if(k&&k.indexOf('planner_')===0){planner++;try{JSON.parse(v);}catch(e){invalid++;}}
  }
  sizes.sort(function(a,b){return b.bytes-a.bytes;});
  // For a big daily entry, name the field taking most of the room (a photo or drawing, usually).
  function biggestField(key){try{var o=JSON.parse(localStorage.getItem(key)||'{}'),best='',bl=0;Object.keys(o).forEach(function(f){var l=JSON.stringify(o[f]).length;if(l>bl){bl=l;best=f;}});return best&&bl>20000?' (mostly “'+escHtml(best)+'”, '+Math.round(bl*2/1024)+' KB)':'';}catch(e){return'';}}
  var top=sizes.slice(0,5).filter(function(s){return s.bytes>1024;}).map(function(s){return escHtml(s.key)+': '+(s.bytes/1024).toFixed(0)+' KB'+(s.key.indexOf('planner_')===0?biggestField(s.key):'');}).join('<br>');
  var mb=(bytes/1048576).toFixed(2),last=localStorage.getItem('last_backup_ts'),lastText=last?new Date(last).toLocaleDateString():'Never';
  // Chrome / Samsung Internet allow about 5.2 million characters per site (≈10 MB the way it's counted here, 2 bytes each).
  // The old warning came on at 4.5 MB (under half full). Now it shows how full it really is and warns at 80%.
  var QUOTA=10485760,pctFull=Math.round((bytes+externalBytes)/QUOTA*100),nearQuota=pctFull>=80;
  mb+=' ('+pctFull+'% of the browser\'s space)';
  var el=document.getElementById('journalStorageHealth');
  if(el)el.innerHTML='<strong>'+(invalid?'Attention needed':nearQuota?'Storage nearly full':'Journal healthy')+'</strong><br>'+planner+' daily entries • about '+mb+' MB of journal data • last backup: '+lastText+(externalBytes?'<br><span style="color:#667085;">Separate apps: '+(externalBytes/1048576).toFixed(2)+' MB (not included or synchronized)</span>':'')+(invalid?'<br>'+invalid+' unreadable daily record(s) found.':'')+(nearQuota?'<br><span style="color:#b91c1c;">You are close to the browser\'s storage limit — back up now, then consider trimming old data.</span>':'')+(top?'<div style="margin-top:6px;font-size:.7rem;color:#78716c;"><strong>Largest journal items:</strong><br>'+top+'</div>':'');
  return{bytes:bytes,externalBytes:externalBytes,planner:planner,invalid:invalid,sizes:sizes};
}
function journalConsistencyCheck(){
  var h=journalStorageHealth(),issues=[];
  ['activities_data','library_books','skill_log_data','current_training_plan'].forEach(function(k){
    var v=localStorage.getItem(k);if(v){try{JSON.parse(v);}catch(e){issues.push(k);}}
  });
  alert(!h.invalid&&!issues.length?'Journal check passed. Your saved records are readable.':'Journal check found '+(h.invalid+issues.length)+' item(s) that may need attention. Make a backup before further changes.');
}
function journalStatusTime(value){if(!value)return'No recent update';var d=new Date(value);return isNaN(d.getTime())?'No recent update':d.toLocaleString();}
function renderJournalRecentUpdates(){
  var el=document.getElementById('journalRecentUpdates');if(!el)return;
  var acts=[];try{acts=getActivities()||[];}catch(ignore){}var skills=[];try{skills=getSkillEntries()||[];}catch(ignore2){}
  function newestDate(items,field){var values=items.map(function(x){return x&&x[field];}).filter(Boolean).sort();return values.length?values[values.length-1]:'';}
  var rows=[
    ['Activities',newestDate(acts,'date')],['Skills',newestDate(skills,'date')],
    ['Local save',localStorage.getItem('journal_last_saved')],['Recovery backup',localStorage.getItem('journal_last_automatic_backup')||localStorage.getItem('last_backup_ts')]
  ];
  el.innerHTML='<strong style="display:block;color:#0a3161;margin-bottom:5px;">Recently updated</strong>'+rows.map(function(row){return'<div style="display:flex;justify-content:space-between;gap:10px;padding:3px 0;border-bottom:1px solid #eef2f6;"><span>'+row[0]+'</span><span style="text-align:right;color:#475569;">'+escHtml(journalStatusTime(row[1]))+'</span></div>';}).join('');
}
async function runJournalDiagnostics(){
  var out=document.getElementById('journalDiagnosticResults'),btn=document.getElementById('journalDiagnosticBtn');if(!out)return;if(btn){btn.disabled=true;btn.textContent='Checking...';}out.innerHTML='Checking connections...';
  var checks=[];function add(name,ok,detail){checks.push({name:name,ok:ok,detail:detail});}
  try{
    var estimate=navigator.storage&&navigator.storage.estimate?await navigator.storage.estimate():null;
    var used=estimate&&Number(estimate.usage),quota=estimate&&Number(estimate.quota);add('Browser storage',true,Number.isFinite(used)&&Number.isFinite(quota)?(used/1048576).toFixed(1)+' MB used of '+(quota/1048576).toFixed(0)+' MB reported':'Local storage available');
  }catch(e){add('Browser storage',false,'Could not estimate available space');}
  add('Local storage',true,'Local-only mode active');
  add('AI tools',!!journalAIKey(),journalAIKey()?'Anthropic key saved on this device':'Add your Anthropic key in Settings');
  var aq=null;try{aq=JSON.parse(localStorage.getItem('meridian_airnow_cache')||'null');}catch(ignore4){}var aqAge=aq&&aq.savedAt?Math.round((Date.now()-Number(aq.savedAt))/3600000):null;add('AirNow AQI',!!(aq&&aq.data),aq&&aq.data?'Last reading '+aqAge+' hour'+(aqAge===1?'':'s')+' ago':'No cached reading yet');
  var backup=localStorage.getItem('journal_last_automatic_backup')||localStorage.getItem('last_backup_ts');add('Recovery backup',!!backup,backup?journalStatusTime(backup):'No backup recorded');
  out.innerHTML=checks.map(function(c){return'<div style="display:grid;grid-template-columns:18px minmax(110px,.7fr) 1fr;gap:6px;padding:5px 0;border-bottom:1px solid #eef2f6;"><span style="color:'+(c.ok?'#15803d':'#b91c1c')+';font-weight:900;">'+(c.ok?'&#x2713;':'!')+'</span><strong>'+escHtml(c.name)+'</strong><span>'+escHtml(c.detail)+'</span></div>';}).join('');
  renderJournalRecentUpdates();if(btn){btn.disabled=false;btn.textContent='Run Connection Check';}
}
function loadJournalSettings(){
  var p=journalPrefs();
  document.querySelectorAll('[data-home-pref]').forEach(function(cb){cb.checked=p.homeCards.indexOf(cb.dataset.homePref)>=0;});
  var ai=document.getElementById('settingAIContext');if(ai)ai.checked=p.aiContext!==false;
  var days=document.getElementById('settingBackupDays');if(days)days.value=String(p.backupDays||7);
  var keyInput=document.getElementById('journalAnthropicKey');if(keyInput)keyInput.value=journalAIKey();
  journalStorageHealth();
  renderJournalRecentUpdates();
}
function saveJournalSettings(){
  var cards=Array.from(document.querySelectorAll('[data-home-pref]:checked')).map(function(cb){return cb.dataset.homePref;});
  var ai=document.getElementById('settingAIContext'),days=document.getElementById('settingBackupDays');
  localStorage.setItem('journal_ui_prefs',JSON.stringify({homeCards:cards,aiContext:!ai||ai.checked,backupDays:parseInt(days&&days.value||'7',10)}));
  var keyInput=document.getElementById('journalAnthropicKey'),key=keyInput?keyInput.value.trim():'';
  if(key)localStorage.setItem('ai_key',key);else localStorage.removeItem('ai_key');
  renderJournalHome();closeSettings();v26Toast('Settings saved');
}
function toggleJournalAIKeyVisibility(){
  var input=document.getElementById('journalAnthropicKey');if(!input)return;
  input.type=input.type==='password'?'text':'password';
}
function clearJournalAIKey(){
  var input=document.getElementById('journalAnthropicKey');if(input)input.value='';
  localStorage.removeItem('ai_key');v26Toast('Anthropic key removed from this device');
}
function journalAIContext(){
  if(journalPrefs().aiContext===false)return'';
  var d=journalViewData(),parts=[];
  if(d.wSleepVal)parts.push('sleep '+d.wSleepVal+' hours');
  if(d.wWghtVal)parts.push('weight '+d.wWghtVal);
  if(d.wBPVal)parts.push('BP '+d.wBPVal);
  if(d.jNotes)parts.push('journal note: '+String(d.jNotes).slice(0,240));
  var acts=getActivities().filter(function(a){return a.date===dk(today);});
  if(acts.length)parts.push('today activity: '+[acts[0].title,acts[0].distance?acts[0].distance+' mi':'',acts[0].pace?acts[0].pace+'/mi':''].filter(Boolean).join(', '));
  return parts.length?'\n\nRELEVANT JOURNAL CONTEXT ('+dk(today)+')\n'+parts.join('\n'):'';
}

/* ===== Local-only storage mode ===== */
