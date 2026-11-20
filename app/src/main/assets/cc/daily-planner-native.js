/* Bridge for BeyondStride's native Life tab (Nov 2026).
 * BeyondStride shows some Command Center sections as its own screens. They read and change the SAME data through
 * window.ccNative, so everything the Command Center does on a change (save, auto-checks, counts, My Schedule,
 * cloud sync) still happens exactly as if you'd tapped it here. In Chrome this file does nothing visible.
 * Nothing is removed. */
(function(){
  var IDS=['pt0','pt1','pt2','dg0','dg1','dg2'];
  var STATES=['','green','black','purple','red'];
  function realKey(){return dk(new Date());}
  function onRealDay(){try{return dk(today)===realKey();}catch(e){return false;}}
  function stored(){try{return JSON.parse(localStorage.getItem('planner_'+realKey())||'{}')||{};}catch(e){return {};}}
  function refresh(){
    ['updateCompactSectionCounts','renderDailyCheckOverview','renderJournalSchedule','updateTodayGlance'].forEach(function(f){try{if(typeof window[f]==='function')window[f]();}catch(e){}});
  }
  // Change today's saved day directly (when the page is showing a different date).
  function writeStored(fn){
    var d=stored();fn(d);
    try{localStorage.setItem('planner_'+realKey(),JSON.stringify(d));}catch(e){return false;}
    try{if(typeof journalCloudSchedule==='function')journalCloudSchedule();}catch(e){}
    return true;
  }
  // The native Today screen always shows the real today; bring the page there if it's on another date.
  function goRealDay(){if(!onRealDay()){try{today=new Date();load();if(typeof syncPeriodsToDay==='function')syncPeriodsToDay();}catch(e){}}}
  function checks(){
    goRealDay();
    var list=typeof collectDailyChecks==='function'?collectDailyChecks():[];
    return list.filter(function(i){return !/^(?:pt|dg)[0-2]c$/.test(i.key||'');}).map(function(i){
      return {key:i.key||'',id:i.id||'',group:i.group,label:String(i.label||'').replace(/\s*-\s*Actual\s*$/i,''),state:i.status==='blank'?'':i.status};});
  }
  function schedule(){
    goRealDay();
    try{renderJournalSchedule();}catch(e){}
    var map=window._journalScheduleItemsById||{},items=Object.keys(map).map(function(id){return map[id];});
    var now=new Date(),cur=now.getHours()*60+now.getMinutes(),next=-1;
    for(var i=0;i<items.length;i++){if(!items[i].fixed&&!items[i].done&&Number(items[i].mins)>=cur){next=i;break;}}
    if(next<0)next=items.findIndex(function(it){return !it.fixed&&!it.done;});
    return items.map(function(it,i){
      var st=it.scheduleStatus||(it.done?'green':'');if(st==='blank')st='';
      return {id:it.id,time:it.time||'',label:String(it.label||''),detail:String(it.detail||'').replace(/<[^>]*>/g,''),done:!!it.done,
        partial:!!it.partial,fixed:!!it.fixed,state:st,next:i===next};});
  }
  var REV=[['dayRevWins','Wins Today'],['dayRevImprove','What to Improve'],['dayRevFocus','Focus for Tomorrow'],['dayRevLessons','Lessons Learned']];
  function review(){
    goRealDay();var btn=document.getElementById('aiFillDayReviewBtn');
    var saved={};try{saved=JSON.parse(localStorage.getItem('dayreview_'+realKey())||'{}')||{};}catch(e){}
    var fields=REV.map(function(r){var el=document.getElementById(r[0]),v=el?el.value:'';return {id:r[0],label:r[1],value:v,unsaved:(saved[r[0]]||'')!==v};});
    return {fields:fields,busy:!!(btn&&btn.disabled),hasKey:typeof journalAIKey==='function'&&!!journalAIKey()};
  }
  // Wellness tab: each check with the numbers that go with it.
  var WELL=[
    ['wBP','BP',[['wBPVal','BP','120/80'],['wPulseVal','Pulse','bpm'],['wRestHRVal','Morning HR','bpm']]],
    ['wMeds','Meds',[['wMedsVal','Meds','HCTZ, Rosuvastatin…']]],
    ['wWght','Weight',[['wWghtVal','Weight','lbs']]],
    ['wSleep','Sleep',[['wSleepVal','Sleep','hrs (7.5 or 6:30)'],['wSleepScore','Sleep score','0-100']]],
    ['exDogWalk','Dog Walk',[['exDogWalkVal','Dog walk','duration / notes']]],
    ['exStepsCheck','10,000 steps',[['exSteps','Steps','steps']]]];
  var WELL_IDS={};WELL.forEach(function(w){w[2].forEach(function(f){WELL_IDS[f[0]]=1;});});
  function wellness(){
    goRealDay();
    return WELL.map(function(w){
      var ck=document.querySelector('.tri-check[data-key="'+w[0]+'"]');
      return {key:w[0],label:w[1],state:ck?(ck.dataset.state||''):'',fields:w[2].map(function(f){var el=document.getElementById(f[0]);
        return {id:f[0],label:f[1],hint:f[2],value:el?el.value:'',number:!!(el&&el.type==='number')};})};
    }).filter(function(w){return document.querySelector('.tri-check[data-key="'+w.key+'"]');});
  }
  function openTab(name){
    var b=document.getElementById('hm-'+name)||document.querySelector('button.tab-btn[onclick*="\''+name+'\'"]');
    if(b)b.click();else{try{switchTab(name,{currentTarget:null,target:null,preventDefault:function(){}});}catch(e){}}
    window.scrollTo(0,0);
  }
  function bible(){try{return window.bibleStudy&&bibleStudy.info?bibleStudy.info():null;}catch(e){return null;}}
  function meditation(){
    goRealDay();var ck=document.querySelector('.tri-check[data-key="spMeditation"]'),line='',streak='';
    try{line=journalMeditationTodayLine();}catch(e){}
    var mins=0,n=0;try{var ss=JSON.parse(localStorage.getItem('medit_sessions')||'[]'),wk=typeof wkStartOf==='function'?dk(wkStartOf(new Date())):'';
      ss.forEach(function(x){if(x&&x.date&&(!wk||x.date>=wk)){mins+=Number(x.dur)||0;n++;}});}catch(e){}
    return {state:ck?(ck.dataset.state||''):'',line:line,weekMins:mins,weekSessions:n,scripture:localStorage.getItem('medit_scripture')||''};
  }
  var WREV=[['revWins','Wins This Week'],['revImprove','What to Improve'],['revFocus','Focus for Next Week'],['revLessons','Lessons Learned']];
  function weekly(){
    try{renderReview();}catch(e){}
    var btn=document.getElementById('aiFillReviewBtn'),lab=document.getElementById('revLabel'),stats=document.getElementById('revStats');
    var saved={};try{var ws=wkStart(revOff2);saved=JSON.parse(localStorage.getItem('review_'+dk(ws))||'{}')||{};}catch(e){}
    return {label:lab?lab.textContent:'',off:typeof revOff2==='number'?revOff2:0,stats:stats?String(stats.innerText||'').replace(/\s+/g,' ').replace(/(\d)([A-Za-z])/g,'$1 $2').replace(/([a-z])\s?(\d)/g,'$1 · $2').trim().slice(0,400):'',
      busy:!!(btn&&btn.disabled),hasKey:typeof journalAIKey==='function'&&!!journalAIKey(),
      fields:WREV.map(function(r){var el=document.getElementById(r[0]),v=el?el.value:'';return {id:r[0],label:r[1],value:v,unsaved:(saved[r[0]]||'')!==v};})};
  }
  function journalText(){goRealDay();var el=document.getElementById('jNotes');return el?el.value:'';}
  // 📆 Monthly / Yearly: the same tables and heat map as those tabs, for any month/year offset (0 = this one).
  function tableData(id){
    var el=document.getElementById(id),t=el&&el.querySelector('table');if(!t)return null;
    var head=[].map.call(t.querySelectorAll('thead th'),function(th){return th.textContent.trim();});
    var trs=t.querySelectorAll('tbody tr, tfoot tr');
    var rows=[].map.call(trs,function(tr){return [].map.call(tr.children,function(td){return td.textContent.trim();});});
    // The same cells with their colors: each cell is lines of {t, c (text color), bg (pill), b (bold), s (small)}.
    function rich(td){
      var lines=[[]];
      function walk(n,st){
        if(n.nodeType===3){var x=n.textContent.replace(/\s+/g,' ');var L=lines[lines.length-1];if(x.trim())L.push({t:x,c:st.c||'',bg:st.bg||'',b:!!st.b,s:!!st.s});else if(L.length&&!/ $/.test(L[L.length-1].t))L[L.length-1].t+=' ';return;}
        if(n.nodeType!==1)return;var cs=n.style||{},fw=cs.fontWeight,fs=cs.fontSize;
        var ns={c:cs.color||st.c,bg:(n!==td&&cs.backgroundColor)||st.bg,b:st.b||fw==='bold'||Number(fw)>=600,s:st.s||(fs&&/em$/.test(fs)&&parseFloat(fs)<1)};
        var block=n!==td&&/^(DIV|P|BR)$/.test(n.tagName);
        if(block&&lines[lines.length-1].length)lines.push([]);
        [].forEach.call(n.childNodes,function(ch){walk(ch,ns);});
        if(block&&lines[lines.length-1].length)lines.push([]);
      }
      walk(td,{});
      return lines.filter(function(l){return l.length;}).map(function(l){if(l.length){l[0].t=l[0].t.replace(/^\s+/,'');l[l.length-1].t=l[l.length-1].t.replace(/\s+$/,'');}return l;});
    }
    var cells=[].map.call(trs,function(tr){return [].map.call(tr.children,rich);});
    var headColors=[].map.call(t.querySelectorAll('thead th'),function(th){var dot=th.querySelector('span');return dot&&dot.style.backgroundColor||'';});
    return {head:head,rows:rows,cells:cells,headColors:headColors};
  }
  function period(kind,off){
    off=Number(off)||0;var out={kind:kind,off:off,sections:[]};
    if(kind==='monthly'){
      var keep=monthOff2;monthOff2=off;
      try{renderMonthly();}catch(e){}
      out.label=(document.getElementById('monthLabel')||{}).textContent||'';
      [['mFitness','🏃 Fitness'],['mWellness','💚 Wellness'],['mSpirituality','📖 Spirituality & Reading']].forEach(function(x){var d=tableData(x[0]);if(d)out.sections.push({title:x[1],head:d.head,rows:d.rows,cells:d.cells,headColors:d.headColors});});
      out.heat=[].map.call(document.querySelectorAll('#hmGrid > .heatmap-cell'),function(c){var cn=c.className||'',m=cn.match(/\bhm(\d)\b/);return /hm-empty/.test(cn)?'e':/hm-future/.test(cn)?'f':(m?m[1]:'0');}).join('');
      monthOff2=keep;try{if(activeTabName()==='monthly')renderMonthly();}catch(e){}
    }else{
      var keepY=yearOff2;yearOff2=off;
      try{renderYearly();}catch(e){}
      out.label=(document.getElementById('yearLabel')||{}).textContent||'';
      [['yFitness','🏃 Fitness'],['yWellness','💚 Wellness'],['ySpirituality','📖 Spirituality & Reading']].forEach(function(x){var d=tableData(x[0]);if(d)out.sections.push({title:x[1],head:d.head,rows:d.rows,cells:d.cells,headColors:d.headColors});});
      yearOff2=keepY;try{if(activeTabName()==='yearly')renderYearly();}catch(e){}
    }
    return out;
  }
  // 📅 Calendar: month grid status + events (yours, training plan, habits, bills) like the Daily page calendar.
  function calMonth(y,m){
    var acts=new Set();try{(getActivities()||[]).forEach(function(a){if(a&&a.date)acts.add(a.date);});}catch(e){}
    var ev=new Set();try{journalCalendarCombinedEvents().forEach(function(e){ev.add(e.date);});}catch(e){}
    var first=new Date(y,m,1),dim=new Date(y,m+1,0).getDate(),days=[];
    for(var d=1;d<=dim;d++){var k=dk(new Date(y,m,d)),st='';try{st=journalCalendarStatus(k,acts);}catch(e){}days.push({d:d,date:k,status:st,event:ev.has(k)});}
    return {title:first.toLocaleDateString('en-US',{month:'long',year:'numeric'}),lead:(first.getDay()+6)%7,days:days,today:dk(new Date())};
  }
  function calEvents(date){
    var list=[];try{list=journalCalendarCombinedEvents().filter(function(e){return e.date===date;});}catch(e){}
    list.sort(function(a,b){return(a.time||'99:99').localeCompare(b.time||'99:99');});
    return list.map(function(e){var r=Number(e.reminderMinutes||0);return {id:e.id,date:e.date,time:e.time||'',timeLabel:e.time?journalFormatEventTime(e.time):'All day',title:e.title||'Untitled event',note:e.note||'',
      kind:e.trainingPlan?'training':e.habitGrowth?'habit':e.moneyEvent?'money':'mine',reminder:(e.noReminder||!e.time)?-1:r};});
  }
  function calSave(list){journalCalendarEventsSave(list);try{decorateMyCalendarEvents();}catch(e){}try{checkCalendarReminders();}catch(e){}}
  // 📊 Weekly tab: goal bars, the week's digest and its tables, for any week offset (0 = this week).
  function weekTab(off){
    off=Number(off)||0;var keep=weekOff2;weekOff2=off;
    try{renderWeekly();}catch(e){}try{renderGoals();}catch(e){}
    var out={off:off,label:(document.getElementById('weekLabel')||{}).textContent||'',goals:[],sections:[]};
    document.querySelectorAll('#goalBars .goal-row').forEach(function(r){
      var sp=r.querySelectorAll('.goal-label span'),fill=r.querySelector('.goal-bar-fill');
      out.goals.push({label:sp[0]?sp[0].textContent.trim():'',value:sp[1]?sp[1].textContent.trim().replace(/\s*\d+%$/,''):'',pct:fill?parseInt(fill.style.width,10)||0:0,reached:r.classList.contains('goal-reached')});
    });
    var dg=document.getElementById('wDigest');out.digest=dg?String(dg.innerText||'').replace(/\n{2,}/g,'\n').trim().slice(0,1200):'';
    [['wFitness','🏃 Fitness'],['wWellness','💚 Wellness'],['wSpirituality','📖 Spirituality & Reading'],['wNutrition','🍎 Nutrition'],['wNutritionMicro','🧪 Micronutrients']].forEach(function(x){var d=tableData(x[0]);if(d&&d.rows.length)out.sections.push({title:x[1],head:d.head,rows:d.rows,cells:d.cells,headColors:d.headColors});});
    try{out.targets=JSON.parse(localStorage.getItem('weekly_goals')||'{}');}catch(e){out.targets={};}
    weekOff2=keep;try{if(activeTabName()==='weekly'){renderWeekly();renderGoals();}}catch(e){}
    return out;
  }
  // 📚 Library: today's listening, the finish-by plan, and your finished books.
  function library(){
    goRealDay();
    var ck=document.querySelector('.tri-check[data-key="gaAudiobook"]');
    try{if(typeof renderAudiobookFinishPlan==='function')renderAudiobookFinishPlan();}catch(e){}
    var pr=document.getElementById('audioPlanResult'),plan=pr&&pr.style.display!=='none'?String(pr.innerText||'').replace(/\n{2,}/g,'\n').trim().slice(0,600):'';
    var books=[];try{books=getBooks();}catch(e){}
    var yr=String(new Date().getFullYear());
    var done=books.filter(function(b){return b.dateCompleted;}).sort(function(a,b){return String(b.dateCompleted).localeCompare(String(a.dateCompleted));});
    return {state:ck?(ck.dataset.state||''):'',text:(document.getElementById('gaAudiobookText')||{}).value||'',time:(document.getElementById('gaAudiobookTime')||{}).value||'',
      plan:plan,count:books.length,year:done.filter(function(b){return String(b.dateCompleted).slice(0,4)===yr;}).length,
      recent:done.slice(0,8).map(function(b){return {title:b.title||'',author:b.author||'',date:b.dateCompleted||'',rating:Number(b.rating)||0};})};
  }
  // 🌱 Habits: today's habits with their controls, like the Habits tab.
  function habits(){
    try{h2ViewDate=new Date();h2SyncFromJournal();}catch(e){}
    var state;try{state=h2State();}catch(e){return null;}
    var key=h2Key(new Date()),now=new Date(),list=[],due=0,doneN=0,best=0;
    state.habits.forEach(function(h){
      var log=h2Log(state,h.id,key),isDue=h2IsDue(h,now,state),done=h2IsComplete(h,log),st={};try{st=h2HabitStats(state,h)||{};}catch(e){}
      if(isDue){due++;if(done)doneN++;}best=Math.max(best,st.streak||0);
      var kind=h.autoSource==='steps'||h.autoSource==='hydration'||h.autoSource==='sleep-routine'||h.autoSource==='udemy'||h.autoSource==='training'?'auto':h.type==='check'?'check':'count';
      var valText=h.autoSource==='sleep-routine'?Number(log.value||0)+' / 6 routine steps':h.autoSource==='steps'?Number(log.value||0).toLocaleString()+' / '+Number(h.target||10000).toLocaleString()+' steps':
        h.autoSource==='hydration'?Number(log.value||0)+' / '+Number(h.target||75)+' oz':kind==='count'?Number(log.value||0)+' / '+Number(h.target||1)+(h.type==='duration'?' min':''):'';
      list.push({id:h.id,name:h.name||'',category:h.category||'',schedule:h2ScheduleLabel(h),type:h2TypeLabel(h),kind:kind,due:isDue,done:done,status:log.status||'',value:valText,streak:st.streak||0});
    });
    return {due:due,done:doneN,best:best,list:list};
  }
  // 📈 Dashboard: day-by-day numbers for the charts (same math as the Dashboard / summary tables).
  function dash(days){
    days=Math.max(7,Math.min(365,Number(days)||30));var out=[],now=new Date();
    for(var i=days-1;i>=0;i--){var d=new Date(now);d.setDate(now.getDate()-i);var k=dk(d),raw=localStorage.getItem('planner_'+k),m={};
      try{m=getDM(raw?JSON.parse(raw):{},k)||{};}catch(e){}
      out.push({d:k,weight:+(m.weight||0).toFixed(1),sleep:+((m.sleep||0)/60).toFixed(2),sleepScore:Math.round(m.sleepScore||0),sys:Math.round(m.bpSys||0),dia:Math.round(m.bpDia||0),
        pulse:Math.round(m.pulse||0),restHR:Math.round(m.restHR||0),miles:+(m.miles||0).toFixed(2),steps:Math.round(m.steps||0)});}
    return out;
  }
  // 🔍 Journal search (same places as the Command Center's search: day entries, Bible notes, activities, archived files).
  function search(q){
    q=String(q||'').toLowerCase().trim();if(q.length<2)return [];var out=[];
    function snip(v){v=String(v);var i=v.toLowerCase().indexOf(q);var a=Math.max(0,i-60);return (a>0?'…':'')+v.slice(a,i+q.length+120).replace(/\s+/g,' ')+(i+q.length+120<v.length?'…':'');}
    for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);
      if(/^planner_\d{4}-\d\d-\d\d$/.test(k)){var d;try{d=JSON.parse(localStorage.getItem(k));}catch(e){continue;}
        var date=k.slice(8),f={'Journal':d.jNotes,'Running Log':d.jRunLog,'Accomplishments':d.jAccomplish,'Improvements':d.jImprov,'Daily Bread':d.spDailyBreadText,'Meditation':d.spMeditationText,
          'Audiobook':d.gaAudiobookText,'Priority':[d.pt0t,d.pt1t,d.pt2t].filter(Boolean).join(' · '),'Goals':[d.dg0t,d.dg1t,d.dg2t].filter(Boolean).join(' · ')};
        try{var rv=JSON.parse(localStorage.getItem('dayreview_'+date)||'{}');f['Daily review']=[rv.dayRevWins,rv.dayRevImprove,rv.dayRevFocus,rv.dayRevLessons].filter(Boolean).join('\n');}catch(e){}
        Object.keys(f).forEach(function(l){if(f[l]&&String(f[l]).toLowerCase().indexOf(q)>=0)out.push({date:date,where:l,text:snip(f[l])});});}
      else if(/^bible_\d{4}-\d\d-\d\d$/.test(k)){var b;try{b=JSON.parse(localStorage.getItem(k));}catch(e){continue;}
        ['sermonTitle','sermonNotes','scripture','keyVerse','theme','notes','application','prayer'].forEach(function(x){if(b[x]&&String(b[x]).toLowerCase().indexOf(q)>=0)out.push({date:k.slice(6),where:'Bible notes',text:snip(b[x])});});}
      else if(/^bstudy_\d{4}-\d\d-\d\d$/.test(k)){var st;try{st=JSON.parse(localStorage.getItem(k));}catch(e){continue;}
        var a=st&&st.answers?[st.answers.s,(st.answers.o||[]).join(' '),(st.answers.a||[]).join(' '),st.answers.p].join('\n'):'';if(a.toLowerCase().indexOf(q)>=0)out.push({date:k.slice(7),where:'Bible Study',text:snip(a)});}
    }
    try{getActivities().forEach(function(a){var t=[a.type,a.notes,a.injuryReport].filter(Boolean).join(' · ');if(t.toLowerCase().indexOf(q)>=0)out.push({date:a.date||'',where:'Activity',text:snip(t)});});}catch(e){}
    out.sort(function(x,y){return String(y.date).localeCompare(String(x.date));});
    return out.slice(0,150);
  }
  // 📁 Files (Uploads page): async — results come back through BSNative.files(json) / BSNative.file(name, type, base64).
  function idb(name,store){return window.ccIdbOpen?ccIdbOpen(name):Promise.reject(new Error('no store'));}
  function all(name,store){return idb(name,store).then(function(db){return new Promise(function(res){var q=db.transaction(store,'readonly').objectStore(store).getAll();q.onsuccess=function(){res(q.result||[]);};q.onerror=function(){res([]);};});}).catch(function(){return [];});}
  function back(fn,arg){try{if(window.BSNative&&BSNative[fn])BSNative[fn].apply(BSNative,arg);}catch(e){}}
  var FILES={ou:['OtherUploadsDB','files'],ja:['JournalArchiveDB','journals']};
  // 🌱 The whole Habits page (any day: off = days from today), like the Command Center's Habits tab.
  // 📖 Bible tab: Scripture Focus, Bible Audio, Worship, AI Prayer (the study hero comes from bibleStudy.info()).
  function biblePage(){
    goRealDay();
    function st(k){var e=document.querySelector('.tri-check[data-key="'+k+'"]');return e?(e.dataset.state||''):'';}
    function txt(id){var e=document.getElementById(id);if(!e||e.style.display==='none')return '';return String(e.innerText||e.textContent||'').trim();}
    var key=false;try{key=!!journalAIKey();}catch(e){}
    return {verse:txt('meditScriptureDisplay')||'“Be still, and know that I am God.” — Psalm 46:10',text:(document.getElementById('spDailyBreadText')||{}).value||'',
      checks:{spDailyBread:st('spDailyBread'),spBibleAudio:st('spBibleAudio'),spWorship:st('spWorship'),spAIPrayer:st('spAIPrayer')},
      explain:txt('meditVerseExplain'),worship:txt('bibleWorshipAIResult'),prayer:txt('meditAIResult'),hasKey:key};
  }
  // 📚 Library tab: today's listening, the finish-by-date plan with progress, this week's listening, and every book.
  function libraryPage(sort){
    var base=library(),books=[];try{books=getBooks().slice();}catch(e){}
    var by={oldest:function(a,c){return String(a.dateCompleted||'').localeCompare(String(c.dateCompleted||''));},
      title:function(a,c){return String(a.title||'').localeCompare(String(c.title||''));},
      author:function(a,c){return String(a.author||'').localeCompare(String(c.author||''));},
      rating:function(a,c){return (Number(c.rating)||0)-(Number(a.rating)||0);}}[sort]||function(a,c){return String(c.dateCompleted||'').localeCompare(String(a.dateCompleted||''));};
    books.sort(by);
    var week=null;try{var wd=getCurrentWeekChartData();week={labels:wd.labels,values:wd.metrics.map(function(m){return Number(m.audiobook)||0;})};}catch(e){}
    var plan=null;try{var p=JSON.parse(localStorage.getItem('audiobook_finish_plan')||'null');
      if(p&&p.startDate&&p.finishDate){
        var start=new Date(p.startDate+'T00:00:00'),end=new Date(p.finishDate+'T00:00:00'),now=new Date();now.setHours(0,0,0,0);
        var thru=now>end?new Date(end):new Date(now),act=0,el=0,tot=Number(p.totalSeconds)||0;
        if(thru>=start){var d=new Date(start);while(d<=thru){var k=dk(d),pd={};try{pd=JSON.parse(localStorage.getItem('planner_'+k)||'{}');}catch(x){}act+=journalAudiobookMinutesForDate(k,pd)*60;d.setDate(d.getDate()+1);}el=Math.floor((thru-start)/86400000)+1;}
        act=Math.min(tot,act);var planned=Math.min(tot,Math.round(tot*(el/Math.max(1,Number(p.days)||1)))),tol=Math.max(300,Math.round((Number(p.dailySeconds)||0)*0.1));
        var label='On Track',color='#16a34a';if(act>=tot&&tot>0){label='Complete';}else if(act>planned+tol){label='Ahead';color='#2563eb';}else if(act+tol<planned){label='Behind';color='#dc2626';}
        var ms=[],cur=new Date(start),w=1;while(cur<=end){var we=new Date(cur);we.setDate(we.getDate()+6);if(we>end)we=new Date(end);var th=Math.floor((we-start)/86400000)+1;
          ms.push({l:'Week '+w+': '+cur.toLocaleDateString('en-US',{month:'short',day:'numeric'})+'–'+we.toLocaleDateString('en-US',{month:'short',day:'numeric'}),pct:Math.min(100,Math.round(th/p.days*100))});cur=new Date(we);cur.setDate(cur.getDate()+1);w++;}
        plan={title:p.title||'',author:p.author||'',total:formatSecondsLong(tot),days:p.days,daily:formatSecondsLong(p.dailySeconds),label:label,color:color,
          pct:tot?Math.min(100,Math.round(act/tot*100)):0,plannedPct:tot?Math.min(100,Math.round(planned/tot*100)):0,
          listened:formatSecondsLong(act),planned:formatSecondsLong(planned),remaining:formatSecondsLong(Math.max(0,tot-act)),milestones:ms,source:p.source||'the audiobook length you entered'};
      }}catch(e){}
    return {state:base.state,text:base.text,time:base.time,count:books.length,year:base.year,week:week,plan:plan,
      books:books.map(function(b){return {id:b.id,title:b.title||'',author:b.author||'',date:b.dateCompleted||'',rating:Number(b.rating)||0,notes:b.notes||'',readingNotes:Array.isArray(b.readingNotes)?b.readingNotes:[]};})};
  }
  function bookIdx(books,id){for(var i=0;i<books.length;i++)if(String(books[i].id)===String(id))return i;return -1;}
  function libDone(){try{renderLibrary();}catch(e){}try{if(typeof journalCloudSchedule==='function')journalCloudSchedule();}catch(e){}return true;}
  function habitsPage(off){
    off=Number(off)||0;var view=new Date();view.setDate(view.getDate()+off);view.setHours(12,0,0,0);
    try{h2ViewDate=new Date(view);h2SyncFromJournal();}catch(e){}
    var state;try{state=h2State();}catch(e){return null;}
    var key=h2Key(view),now=new Date();
    function st(h,d){var log=h2Log(state,h.id,h2Key(d)),due=h2IsDue(h,d,state),c=h2IsComplete(h,log);return !due?'n':c?'d':log.status==='partial'?'p':log.status==='skip'?'s':'o';}
    var due=state.habits.filter(function(h){return h2IsDue(h,view,state);}),comp=due.filter(function(h){return h2IsComplete(h,h2Log(state,h.id,key));});
    var stats=state.habits.map(function(h){return h2HabitStats(state,h);});
    var best=stats.reduce(function(m,x){return Math.max(m,x.streak);},0),mom=stats.length?Math.round(stats.reduce(function(a,x){return a+x.rate;},0)/stats.length):0;
    var perfect=0;for(var p=0;p<30;p++){var pd=new Date();pd.setDate(pd.getDate()-p);var pdue=state.habits.filter(function(h){return h2IsDue(h,pd,state);});if(pdue.length&&pdue.every(function(h){return h2IsComplete(h,h2Log(state,h.id,h2Key(pd)));}))perfect++;}
    var ws=h2StartWeek(view);
    var habits=state.habits.map(function(h,i){
      var log=h2Log(state,h.id,key),done=h2IsComplete(h,log),isDue=h2IsDue(h,view,state),s2=stats[i];
      var week='';for(var w=0;w<7;w++){var d=new Date(ws);d.setDate(d.getDate()+w);week+=st(h,d);}
      var month='',md=0,mdone=0,mp=0,msk=0;for(var m=29;m>=0;m--){var d2=new Date(view);d2.setDate(d2.getDate()-m);var c=st(h,d2);month+=c;if(c!=='n'){md++;if(c==='d')mdone++;else if(c==='p')mp++;else if(c==='s')msk++;}}
      var kind=h.autoSource==='steps'||h.autoSource==='hydration'||h.autoSource==='sleep-routine'||h.autoSource==='udemy'?'auto':h.type==='check'?'check':'count';
      var val=h.autoSource==='sleep-routine'?Number(log.value||0)+' / 6 routine steps':h.autoSource==='udemy'?Number(log.value||0)+' / 20 min from My Schedule':h.autoSource==='steps'?Number(log.value||0).toLocaleString()+' / '+Number(h.target||10000).toLocaleString()+' steps':
        h.autoSource==='hydration'?Number(log.value||0)+' / '+Number(h.target||75)+' oz water':kind==='count'?Number(log.value||0)+' / '+Number(h.target||1)+(h.type==='duration'?' min':''):'';
      return {id:h.id,name:h.name||'',category:h.category||'Other',meta:(h.category||'Other')+' • '+h2ScheduleLabel(h)+' • '+h2TypeLabel(h)+(h.autoSource?' • Journal-linked':'')+(isDue?'':' • Not scheduled'),
        why:h.why||'',status:done?'d':log.status==='partial'?'p':log.status==='skip'?'s':'o',kind:kind,value:val,due:isDue,streak:s2.streak,rate:s2.rate,best:s2.best,week:week,month:month,
        monthText:mdone+' of '+md+' scheduled days complete • '+(md?Math.round(mdone/md*100):0)+'%'+(mp?' • '+mp+' in progress':'')+(msk?' • '+msk+' skipped':'')};
    });
    var heat='',hs=new Date();hs.setDate(hs.getDate()-83);
    for(var q=0;q<84;q++){var hd=new Date(hs);hd.setDate(hd.getDate()+q);var hdue=state.habits.filter(function(h){return h2IsDue(h,hd,state);}),hdone=hdue.filter(function(h){return h2IsComplete(h,h2Log(state,h.id,h2Key(hd)));}).length,r=hdue.length?hdone/hdue.length:0;heat+=r===0?'0':r<.5?'1':r<1?'2':'3';}
    return {off:off,label:off===0?'Today':view.toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'}),due:due.length,done:comp.length,momentum:mom,best:best,perfect:perfect,
      weekStart:h2Key(ws),habits:habits,heat:heat,heatStart:h2Key(hs)};
  }
  window.ccNative={
    version:17,
    biblePage:function(){return JSON.stringify(biblePage());},
    setVerse:function(v){goRealDay();var el=document.getElementById('spDailyBreadText');if(!el)return false;el.value=String(v||'');if(el.oninput)el.oninput.call(el);else{localStorage.setItem('medit_scripture',el.value);save();}return true;},
    bibleAI:function(kind){goRealDay();try{if(!journalAIKey())return 'nokey';}catch(e){return 'nokey';}
      try{if(kind==='suggest')aiDailyBread();else if(kind==='explain')meditVerseSummary();else if(kind==='worship')bibleWorshipAIPrompt();else if(kind==='prayer')meditAIPrompt('prayer');else return false;}catch(e){return false;}return true;},
    bibleHero:function(a){goRealDay();try{var b=document.getElementById('hm-bible');if(b)b.click();if(window.bibleStudy&&typeof bibleStudy[a]==='function'){bibleStudy[a]();return true;}}catch(e){}return false;},
    // Book covers check: how many pictures this phone has, how many match a book now, and the old ids they belong to.
    coverStats:function(){window._bsCovStats=null;try{getAllBookCovers().then(function(all){var ids={};getBooks().forEach(function(b){ids[String(b.id)]=1;});
      var m=0,o=0,h=0,d=0,orph=[];all.forEach(function(c){var k=String(c.bookId);if(ids[k])m++;else{o++;orph.push(k);}if(/^data:/.test(c.dataUrl||''))d++;else h++;});
      var st={total:all.length,matched:m,orphans:o,orphanIds:orph.slice(0,40),data:d,links:h,books:Object.keys(ids).length,idb:'testing'};
      // Can this page store anything in IndexedDB at all? (write, then read back)
      try{var rq=indexedDB.open('BsIdbTest',1);rq.onupgradeneeded=function(e){e.target.result.createObjectStore('t');};
        rq.onerror=function(){st.idb='open failed: '+(rq.error&&rq.error.name);window._bsCovStats=st;};rq.onblocked=function(){st.idb='blocked';window._bsCovStats=st;};
        rq.onsuccess=function(e){var db=e.target.result;try{var tx=db.transaction('t','readwrite');tx.objectStore('t').put('x'+Date.now(),'k');
          tx.oncomplete=function(){var g=db.transaction('t','readonly').objectStore('t').get('k');g.onsuccess=function(){st.idb=g.result?'ok':'read back nothing';window._bsCovStats=st;};g.onerror=function(){st.idb='read failed';window._bsCovStats=st;};};
          tx.onerror=function(){st.idb='write failed: '+(tx.error&&tx.error.name);window._bsCovStats=st;};tx.onabort=function(){st.idb='write aborted: '+(tx.error&&tx.error.name);window._bsCovStats=st;};}catch(x){st.idb='error: '+x;window._bsCovStats=st;}};
      }catch(x){st.idb='no IndexedDB: '+x;window._bsCovStats=st;}
      setTimeout(function(){if(!window._bsCovStats){st.idb='no answer';window._bsCovStats=st;}},4000);}).catch(function(e){window._bsCovStats={error:String(e)};});}catch(e){return 'err:'+e;}return true;},
    coverStatsResult:function(){return window._bsCovStats?JSON.stringify(window._bsCovStats):'';},
    bookCovers:function(){window._bsCov=window._bsCov||{};window._bsCovWait=window._bsCovWait||{};var out={},pend=0;try{getBooks().forEach(function(b){var k=String(b.id);
      if(k in _bsCov){if(_bsCov[k])out[k]=_bsCov[k];if(_bsCovWait[k])pend++;}else{_bsCov[k]='';_bsCovWait[k]=1;pend++;getBookCover(b.id).then(function(d){_bsCov[k]=d||'';delete _bsCovWait[k];}).catch(function(){delete _bsCovWait[k];});}});}catch(e){}
      out.__pending=pend;return JSON.stringify(out);},
    bookCoversReset:function(){window._bsCov={};window._bsCovWait={};return true;},
    libraryPage:function(sort){return JSON.stringify(libraryPage(sort));},
    bookSave:function(id,title,author,date,rating,notes){title=String(title||'').trim();if(!title)return false;var books=getBooks(),i=id?bookIdx(books,id):-1;
      var f={title:title,author:String(author||'').trim(),dateCompleted:String(date||''),rating:Math.max(0,Math.min(5,Number(rating)||0)),notes:String(notes||'').trim()};
      if(i>=0)books[i]=Object.assign({},books[i],f);else books.unshift(Object.assign({id:Date.now()},f));saveBooks(books);return libDone();},
    bookDelete:function(id){var books=getBooks(),i=bookIdx(books,id);if(i<0)return false;books.splice(i,1);saveBooks(books);return libDone();},
    bookNote:function(id,idx,json){var n;try{n=JSON.parse(json);}catch(e){return false;}var books=getBooks(),i=bookIdx(books,id);if(i<0)return false;
      var iso=String(n.isoDate||dk(new Date()));var note={date:new Date(iso+'T00:00:00').toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'}),isoDate:iso};
      ['chapter','quote','summary','insights','application','questions'].forEach(function(k){note[k]=String(n[k]||'').trim();});
      if(!(note.chapter||note.quote||note.summary||note.insights||note.application||note.questions))return false;
      if(!Array.isArray(books[i].readingNotes))books[i].readingNotes=[];idx=Number(idx);if(idx>=0&&idx<books[i].readingNotes.length)books[i].readingNotes[idx]=note;else books[i].readingNotes.unshift(note);
      saveBooks(books);return libDone();},
    bookNoteDelete:function(id,idx){var books=getBooks(),i=bookIdx(books,id);if(i<0||!Array.isArray(books[i].readingNotes))return false;books[i].readingNotes.splice(Number(idx),1);saveBooks(books);return libDone();},
    bookOpen:function(id){try{var b=document.getElementById('hm-library');if(b)b.click();bookCollapsed.delete(Number(id));bookCollapsed.delete(id);renderLibrary();setTimeout(function(){var e=document.getElementById('bookBody_'+id);if(e)e.scrollIntoView({block:'center'});},120);}catch(e){return false;}return true;},
    planCreate:function(title,author,h,m,start,finish){title=String(title||'').trim();author=String(author||'').trim();h=Math.max(0,parseInt(h,10)||0);m=Math.max(0,parseInt(m,10)||0);
      if(!title)return 'Enter the audiobook name first.';if(!author)return 'Enter the audiobook author.';if(m>59)return 'Additional minutes must be between 0 and 59.';
      var tot=(h*60+m)*60;if(tot<=0)return 'Enter the total audiobook length.';if(!start)return 'Choose a start date.';if(!finish)return 'Choose a finish date.';
      var days=Math.floor((new Date(finish+'T00:00:00')-new Date(start+'T00:00:00'))/86400000)+1;if(days<1)return 'The finish date must be on or after the start date.';
      localStorage.setItem('audiobook_finish_plan',JSON.stringify({query:title,queryAuthor:author,title:title,author:author,narrator:'',source:'the audiobook length you entered',startDate:start,finishDate:finish,totalSeconds:tot,dailySeconds:Math.ceil(tot/days),days:days,artwork:'',createdAt:new Date().toISOString()}));
      try{renderAudiobookFinishPlan();}catch(e){}try{if(typeof journalCloudSchedule==='function')journalCloudSchedule();}catch(e){}return 'ok';},
    planDelete:function(){localStorage.removeItem('audiobook_finish_plan');try{renderAudiobookFinishPlan();}catch(e){}return true;},
    meditSound:function(type){try{meditToggleSound(type);if(window._meditAudioCtx&&_meditAudioCtx.resume)_meditAudioCtx.resume();}catch(e){return false;}return true;},
    meditSounds:function(){var o={};try{['rain','ocean','forest'].forEach(function(t){o[t]=!!(_meditAudioNodes&&_meditAudioNodes[t]);});}catch(e){}return JSON.stringify(o);},
    // 🌱 Habit form (Create / Edit) without the page: same fields and rules as h2SaveHabit.
    habitGet:function(id){try{var h=h2State().habits.find(function(x){return x.id===id;});return h?JSON.stringify(h):'';}catch(e){return '';}},
    habitSave:function(json){var f;try{f=JSON.parse(json);}catch(e){return 'bad';}var name=String(f.name||'').trim();if(!name)return 'Please enter a habit name.';
      var state=h2State(),id=f.id||'';var habit={id:id||('h2-'+Date.now()),name:name,category:f.category||'Health',color:f.color||'#0a3161',type:['check','count','duration'].indexOf(f.type)>=0?f.type:'check',
        target:Math.max(1,Number(f.target)||1),schedule:['daily','weekdays','custom','weekly'].indexOf(f.schedule)>=0?f.schedule:'daily',weeklyTarget:Math.max(1,Math.min(7,Number(f.weeklyTarget)||3)),
        days:(f.days||[]).map(Number).filter(function(d){return d>=0&&d<=6;}),why:String(f.why||'').trim(),cue:String(f.cue||'').trim(),minimum:String(f.minimum||'').trim(),created:new Date().toISOString()};
      if(id){var i=state.habits.findIndex(function(h){return h.id===id;});if(i<0)return 'That habit is gone.';habit.created=state.habits[i].created||habit.created;habit.autoSource=state.habits[i].autoSource||'';if(state.habits[i].color&&!f.color)habit.color=state.habits[i].color;state.habits[i]=habit;}
      else state.habits.push(habit);
      if(!h2SaveState(state))return 'Storage is full.';try{h2Render();}catch(e){}return 'ok';},
    // 📚 Book covers: search online, apply, set from a photo, remove (stored where the page keeps them).
    coverSearch:function(id){window._bsCovRes=window._bsCovRes||{};var k=String(id);_bsCovRes[k]=null;var b=getBooks().find(function(x){return String(x.id)===k;});if(!b)return false;
      _findCover(b.title,b.author,function(c){_bsCovRes[k]=(c||[]).map(function(x){return {url:x.url,source:x.source||''};});});return true;},
    coverResults:function(id){var r=(window._bsCovRes||{})[String(id)];return r==null?'':JSON.stringify(r);},
    coverApply:function(id,url){var b=getBooks().find(function(x){return String(x.id)===String(id);});if(!b)return false;var k=String(id);window._bsCov=window._bsCov||{};_bsCov[k]=url;
      // Same as applyBookCover (a small JPEG when the picture allows it, else the link), but it remembers the result here too.
      function put(v){_bsCov[k]=v;setBookCover(b.id,v).then(function(){try{renderLibrary();}catch(e){}});}
      var img=new Image();img.crossOrigin='anonymous';img.onload=function(){try{put(_resizeToDataUrl(img,160,220,0.85));}catch(e){put(url);}};img.onerror=function(){put(url);};img.src=url;return true;},
    coverSet:function(id,dataUrl){var b=getBooks().find(function(x){return String(x.id)===String(id);});if(!b||String(dataUrl).indexOf('data:image')!==0)return false;
      setBookCover(b.id,dataUrl).then(function(){try{renderLibrary();}catch(e){}});if(window._bsCov)_bsCov[String(id)]=dataUrl;return true;},
    coverDelete:function(id){var b=getBooks().find(function(x){return String(x.id)===String(id);});if(!b)return false;deleteBookCover(b.id).then(function(){try{renderLibrary();}catch(e){}});if(window._bsCov)_bsCov[String(id)]='';return true;},
    // 📁 Book documents: list (async → poll docsResult), open (hands the file to BeyondStride), add in parts, delete.
    docsLoad:function(id){window._bsDocs=window._bsDocs||{};var k=String(id),b=getBooks().find(function(x){return String(x.id)===k;});if(!b)return false;
      getBookDocuments(b.id).then(function(ds){_bsDocs[k]=ds.map(function(d){return {id:d.id,name:d.name||'document',type:d.type||'',size:d.size||0,addedAt:d.addedAt||''};});});return true;},
    docsResult:function(id){var r=(window._bsDocs||{})[String(id)];return r==null?'':JSON.stringify(r);},
    docOpen:function(docId){getAllBookDocuments().then(function(all){var d=all.find(function(x){return String(x.id)===String(docId);});if(!d||!d.dataUrl)return;
      var u=String(d.dataUrl),i=u.indexOf(',');try{BSNative.file(d.name||'document',d.type||'application/octet-stream',u.slice(i+1));}catch(e){}});return true;},
    docDelete:function(docId,id){var b=getBooks().find(function(x){return String(x.id)===String(id);});deleteBookDocument(Number(docId),b?b.id:id).catch(function(){});if(window._bsDocs)delete _bsDocs[String(id)];return true;},
    docStart:function(){window.__bsDoc=[];return true;},
    docPart:function(b64){window.__bsDoc.push(b64);return true;},
    docEnd:function(id,name,type,size){var b=getBooks().find(function(x){return String(x.id)===String(id);});if(!b)return false;var data='data:'+(type||'application/octet-stream')+';base64,'+(window.__bsDoc||[]).join('');window.__bsDoc=null;
      window._bsDocs=window._bsDocs||{};delete _bsDocs[String(id)];
      putBookDocument({bookId:b.id,name:name,type:type,size:Number(size)||0,dataUrl:data,addedAt:new Date().toISOString()}).then(function(){ccNative.docsLoad(id);});return true;},
    // ── 💾 Life data for BeyondStride's Backup: everything the Command Center keeps here (saved data + pictures/files),
    // never the AI key or other secrets. Sent to the phone in parts (BSNative.lifePart / lifeDone). ──
    lifeBackup:function(){
      function safe(k){var l=String(k||'').toLowerCase();if(!l)return false;if(l==='ai_key'||l.indexOf('sb-')===0||l.indexOf('journal_cloud_')===0||l.indexOf('journal_google_')===0)return false;
        return !/api[_-]?key|apikey|secret|token|password|service_role/.test(l);}
      function dump(name){return new Promise(function(res){var made=false,r;try{r=indexedDB.open(name);}catch(e){res(null);return;}
        r.onupgradeneeded=function(){made=true;try{r.transaction.abort();}catch(e){}};r.onerror=function(){res(null);};
        r.onsuccess=function(){var db=r.result,out={};if(made){db.close();res(null);return;}var names=[].slice.call(db.objectStoreNames);if(!names.length){db.close();res(null);return;}
          var left=names.length;names.forEach(function(st){var rows=[],tx;try{tx=db.transaction(st,'readonly');}catch(e){if(--left===0){db.close();res(out);}return;}
            var c=tx.objectStore(st).openCursor();c.onsuccess=function(){var cur=c.result;if(cur){rows.push([cur.key,cur.value]);cur.continue();}else{out[st]=rows;if(--left===0){db.close();res(out);}}};
            c.onerror=function(){if(--left===0){db.close();res(out);}};});};});}
      (async function(){try{
        var ls={},n=0;for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(!safe(k))continue;ls[k]=localStorage.getItem(k);n++;}
        var names=['my_life_command_center_large_storage','BookCoversDB','BookDocumentsDB','BibleImagesDB','OtherUploadsDB','JournalArchiveDB'],idb={},files=0;
        for(var j=0;j<names.length;j++){var d=await dump(names[j]);if(d){idb[names[j]]=d;Object.keys(d).forEach(function(st){files+=d[st].length;});}}
        var json=JSON.stringify({format:'beyondstride-life-backup',version:1,createdAt:new Date().toISOString(),items:n,files:files,localStorage:ls,indexedDB:idb});
        for(var p=0;p<json.length;p+=400000)BSNative.lifePart(json.slice(p,p+400000));
        BSNative.lifeDone(n+' items · '+files+' pictures/files');
      }catch(e){try{BSNative.lifeDone('error: '+e);}catch(x){}}})();
      return true;},
    // Restore from that backup (parts in, then lifeRestoreEnd). Replaces matching saved items; the page reloads after.
    lifeRestoreStart:function(){window.__bsLife=[];return true;},
    lifeRestorePart:function(s){window.__bsLife.push(s);return true;},
    lifeRestoreEnd:function(){var o;try{o=JSON.parse((window.__bsLife||[]).join(''));}catch(e){return 'bad file';}window.__bsLife=null;
      if(!o||o.format!=='beyondstride-life-backup')return 'not a Life backup';
      var n=0;Object.keys(o.localStorage||{}).forEach(function(k){var l=k.toLowerCase();if(l==='ai_key')return;try{localStorage.setItem(k,o.localStorage[k]);n++;}catch(e){}});
      var jobs=[];Object.keys(o.indexedDB||{}).forEach(function(name){var stores=o.indexedDB[name];
        var opener=window.ccIdbOpen&&['BookCoversDB','BookDocumentsDB','BibleImagesDB','OtherUploadsDB','JournalArchiveDB'].indexOf(name)>=0?ccIdbOpen(name):new Promise(function(res,rej){var r=indexedDB.open(name);
          r.onupgradeneeded=function(){Object.keys(stores).forEach(function(st){if(!r.result.objectStoreNames.contains(st))r.result.createObjectStore(st);});};r.onsuccess=function(){res(r.result);};r.onerror=function(){rej(r.error);};});
        jobs.push(opener.then(function(db){return Promise.all(Object.keys(stores).map(function(st){return new Promise(function(res){if(!db.objectStoreNames.contains(st)){res();return;}
          var tx=db.transaction(st,'readwrite'),os=tx.objectStore(st);(stores[st]||[]).forEach(function(kv){try{if(os.keyPath)os.put(kv[1]);else os.put(kv[1],kv[0]);}catch(e){}});
          tx.oncomplete=res;tx.onerror=res;tx.onabort=res;});}));}).catch(function(){}));});
      Promise.all(jobs).then(function(){setTimeout(function(){location.reload();},300);});
      return 'ok:'+n;},
    habitsPage:function(off){return JSON.stringify(habitsPage(off));},
    /** On the Habits page's day: log / adjust / move / delete. */
    habitLogOn:function(off,id,status){try{var d=new Date();d.setDate(d.getDate()+(Number(off)||0));h2ViewDate=d;h2SetLog(id,status);}catch(e){return false;}return true;},
    habitAdjustOn:function(off,id,delta){try{var d=new Date();d.setDate(d.getDate()+(Number(off)||0));h2ViewDate=d;h2Adjust(id,Number(delta)||0);}catch(e){return false;}return true;},
    habitMove:function(id,dir){try{var s=h2State(),i=s.habits.findIndex(function(h){return h.id===id;}),j=i+Number(dir);if(i<0||j<0||j>=s.habits.length)return false;
      var t=s.habits[i];s.habits[i]=s.habits[j];s.habits[j]=t;h2SaveState(s);try{h2Render();}catch(e){}}catch(e){return false;}return true;},
    habitDelete:function(id){try{var s=h2State(),rm=s.habits.find(function(h){return h.id===id;});if(rm&&rm.autoSource&&s.dismissedAuto.indexOf(rm.autoSource)===-1)s.dismissedAuto.push(rm.autoSource);s.habits=s.habits.filter(function(h){return h.id!==id;});Object.keys(s.logs).forEach(function(k){if(s.logs[k])delete s.logs[k][id];});
      h2SaveState(s);try{h2Render();}catch(e){}}catch(e){return false;}return true;},
    /** Open the Command Center's own add / edit form for a habit (id empty = new). */
    habitForm:function(id){try{var b=document.getElementById('hm-habits2');if(b)b.click();if(id)h2Edit(id);else h2ToggleForm(true);window.scrollTo(0,0);var fc=document.getElementById('h2FormCard');if(fc)fc.scrollIntoView();}catch(e){return false;}return true;},
    search:function(q){return JSON.stringify(search(q));},
    /** ✨ Saved AI answers. */
    archive:function(){try{return JSON.stringify(JSON.parse(localStorage.getItem('ai_archive')||'[]'));}catch(e){return '[]';}},
    archiveDelete:function(i){var a=JSON.parse(localStorage.getItem('ai_archive')||'[]');a.splice(Number(i),1);localStorage.setItem('ai_archive',JSON.stringify(a));return true;},
    /** 💬 Ask AI about your journal (the Command Center's own Ask AI: all your days, reviews and runs). */
    ask:function(q){q=String(q||'').trim();if(!q)return false;if(!(typeof journalAIKey==='function'&&journalAIKey()))return 'nokey';
      var el=document.getElementById('askJournalInput');if(!el)return false;el.value=q;try{_aiLastShown.askJournalResult=null;}catch(e){}submitAskJournal();return true;},
    askState:function(){var b=document.getElementById('askJournalSubmitBtn'),r=document.getElementById('askJournalResult'),last=(window._aiLastShown||{}).askJournalResult;
      try{last=_aiLastShown.askJournalResult;}catch(e){}
      return JSON.stringify({busy:!!(b&&b.disabled),answer:last&&last.text||'',status:r&&!last?String(r.textContent||'').slice(0,200):''});},
    askSave:function(){try{archiveAIResp('askJournalResult');}catch(e){return false;}return true;},
    /** 🧘 Meditation: recent sessions; log a finished session (ticks Meditation like the timer). */
    meditSessions:function(){try{return JSON.stringify(JSON.parse(localStorage.getItem('medit_sessions')||'[]').slice(0,30));}catch(e){return '[]';}},
    meditLog:function(mins,notes){mins=Math.round(Number(mins)||0);if(mins<=0)return false;goRealDay();var k=dk(new Date()),ss=JSON.parse(localStorage.getItem('medit_sessions')||'[]');
      var i=ss.findIndex(function(x){return x.date===k;}),rec={dur:mins,notes:String(notes||''),verse:localStorage.getItem('medit_scripture')||'',ts:Date.now()};
      if(i>=0)ss[i]=Object.assign(ss[i],rec,{dur:(Number(ss[i].dur)||0)+mins});else ss.unshift(Object.assign({date:k},rec));if(ss.length>60)ss=ss.slice(0,60);
      localStorage.setItem('medit_sessions',JSON.stringify(ss));
      document.querySelectorAll('.tri-check[data-key="spMeditation"]').forEach(function(e){applyTriState(e,'green');});try{save();}catch(e){}refresh();
      try{if(typeof renderMeditLog==='function')renderMeditLog();}catch(e){}return true;},
    /** ⚙️ Settings this Command Center keeps (the AI key stays in this browser only). */
    settings:function(){var h=document.getElementById('journalStorageHealth');try{journalStorageHealth();}catch(e){}
      return JSON.stringify({hasKey:typeof journalAIKey==='function'&&!!journalAIKey(),aiContext:(function(){try{return journalPrefs().aiContext!==false;}catch(e){return true;}})(),
        storage:h?String(h.innerText||'').replace(/\s+/g,' ').trim().slice(0,300):'',sync:window.ccSync?{linked:ccSync.linked(),pending:ccSync.pending()}:null,
        status:(document.getElementById('v26SaveText')||{}).textContent||''});},
    setKey:function(k){k=String(k||'').trim();if(k)localStorage.setItem('ai_key',k);else localStorage.removeItem('ai_key');return true;},
    setAIContext:function(on){var p={};try{p=JSON.parse(localStorage.getItem('journal_ui_prefs')||'{}')||{};}catch(e){}p.aiContext=!!on;localStorage.setItem('journal_ui_prefs',JSON.stringify(p));var el=document.getElementById('settingAIContext');if(el)el.checked=!!on;return true;},
    syncNow:function(){if(window.ccSync)ccSync.now();return true;},
    filesLoad:function(){Promise.all([all.apply(null,FILES.ou),all.apply(null,FILES.ja)]).then(function(r){
      function m(x,src){return {src:src,id:x.id,name:x.name||'file',type:x.type||'',size:x.size||0,date:String(x.uploadDate||'').slice(0,10),note:x.note||''};}
      back('files',[JSON.stringify(r[0].map(function(x){return m(x,'ou');}).concat(r[1].map(function(x){return m(x,'ja');})))]);});return true;},
    fileGet:function(src,id){var f=FILES[src];if(!f)return false;idb(f[0],f[1]).then(function(db){var q=db.transaction(f[1],'readonly').objectStore(f[1]).get(Number(id));
      q.onsuccess=function(){var r=q.result;if(!r)return;var blob=r.blob||(r.data?dataURLtoBlob(r.data):null);if(!blob)return;var fr=new FileReader();
        fr.onload=function(){back('file',[r.name||'file',r.type||'application/octet-stream',String(fr.result).split(',')[1]||'']);};fr.readAsDataURL(blob instanceof Blob?blob:new Blob([blob],{type:r.type}));};});return true;},
    fileDelete:function(src,id){var f=FILES[src];if(!f)return false;idb(f[0],f[1]).then(function(db){db.transaction(f[1],'readwrite').objectStore(f[1]).delete(Number(id)).onsuccess=function(){window.ccNative.filesLoad();try{ouRenderList();jaRenderList();}catch(e){}};});return true;},
    /** Add a file picked on the phone to Uploads (base64 sent in pieces: fileStart → filePart… → fileEnd). */
    fileStart:function(){window.__ccUp=[];return true;},
    filePart:function(b64){window.__ccUp.push(b64);return true;},
    fileEnd:function(name,type){var bin=atob((window.__ccUp||[]).join(''));window.__ccUp=null;var u=new Uint8Array(bin.length);for(var i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);
      var blob=new Blob([u],{type:type||'application/octet-stream'});
      idb('OtherUploadsDB','files').then(function(db){var tx=db.transaction('files','readwrite');tx.objectStore('files').add({name:name,type:blob.type,blob:blob,uploadDate:new Date().toISOString(),size:blob.size,note:''});
        tx.oncomplete=function(){window.ccNative.filesLoad();try{ouRenderList();}catch(e){}};});return true;},
    dash:function(days){return JSON.stringify(dash(days));},
    habits:function(){return JSON.stringify(habits());},
    /** Check habit: status 'done' / 'partial' / 'skip' (tapping the same again clears it). Count / minutes habit: delta ±1. */
    habitLog:function(id,status){try{h2ViewDate=new Date();h2SetLog(id,status);}catch(e){return false;}return true;},
    habitAdjust:function(id,delta){try{h2ViewDate=new Date();h2Adjust(id,Number(delta)||0);}catch(e){return false;}return true;},
    library:function(){return JSON.stringify(library());},
    /** Today's listening: 'gaAudiobookText' (what) or 'gaAudiobookTime' (time, e.g. 45 or 1:10:00). */
    setListen:function(id,value){if(id!=='gaAudiobookText'&&id!=='gaAudiobookTime')return false;goRealDay();var el=document.getElementById(id);if(!el)return false;
      el.value=String(value||'');try{el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}try{save();}catch(e){}refresh();return true;},
    addBook:function(title,author,date,rating){title=String(title||'').trim();if(!title)return false;var b=getBooks();
      b.unshift({id:Date.now(),title:title,author:String(author||'').trim(),dateCompleted:String(date||''),rating:Number(rating)||0,notes:''});saveBooks(b);try{renderLibrary();}catch(e){}return true;},
    weekTab:function(off){return JSON.stringify(weekTab(off));},
    /** Weekly targets: {Miles, Bible, Prayer, Exercise, Audible} (0 / empty = no goal). */
    setGoals:function(json){var g={};try{g=JSON.parse(localStorage.getItem('weekly_goals')||'{}')||{};}catch(e){}
      var n=JSON.parse(json||'{}');['Miles','Bible','Prayer','Exercise','Audible'].forEach(function(k){if(k in n){var v=parseFloat(n[k]);if(v>0)g[k]=v;else delete g[k];}});
      ['Miles','Bible','Prayer','Exercise','Audible'].forEach(function(k){var el=document.getElementById('g'+k);if(el)el.value=g[k]||'';});
      try{saveGoals();}catch(e){localStorage.setItem('weekly_goals',JSON.stringify(g));}return true;},
    calendar:function(y,m){return JSON.stringify(calMonth(Number(y),Number(m)));},
    events:function(date){return JSON.stringify(calEvents(String(date)));},
    /** Add or update one of your events (id empty = new). reminder: minutes before, -1 = none. */
    saveEvent:function(id,date,title,time,note,reminder){
      title=String(title||'').trim();if(!title)return false;time=String(time||'');reminder=time?Number(reminder):-1;
      var patch={date:String(date),time:time,title:title,note:String(note||'').trim(),reminderMinutes:reminder,noReminder:!time||reminder<0};
      var list=journalCalendarEventsAll();
      if(id)list=list.map(function(e){return e.id===id?Object.assign({},e,patch):e;});else list.push(Object.assign({id:'ev-'+Date.now()},patch));
      calSave(list);return true;
    },
    deleteEvent:function(id){calSave(journalCalendarEventsAll().filter(function(e){return e.id!==id;}));return true;},
    period:function(kind,off){return JSON.stringify(period(kind==='yearly'?'yearly':'monthly',off));},
    /** ✍️ Daily Journal (today). */
    setJournal:function(value){goRealDay();var el=document.getElementById('jNotes');if(!el)return false;value=String(value||'');if(el.value===value)return true;
      el.value=value;try{el.dispatchEvent(new Event('input',{bubbles:true}));}catch(e){}try{save();}catch(e){}return true;},
    /** 📖 Bible Study / 🧘 Meditation: open the Command Center's own guided session or meditation screen. */
    bibleOpen:function(){goRealDay();if(!(typeof journalAIKey==='function'&&journalAIKey()))return 'nokey';openTab('bible');try{bibleStudy.open();}catch(e){return false;}return true;},
    meditOpen:function(){goRealDay();openTab('meditate');return true;},
    /** 📅 Weekly review (this week = 0, last week = -1 …). */
    weekly:function(){return JSON.stringify(weekly());},
    weeklyShift:function(n){try{changeRevOff(n);}catch(e){}return JSON.stringify(weekly());},
    setWeekly:function(id,value){if(!WREV.some(function(r){return r[0]===id;}))return false;var el=document.getElementById(id);if(!el)return false;
      el.value=String(value||'');try{if(typeof autoGrowReviewField==='function')autoGrowReviewField(el);}catch(e){}try{saveReview();}catch(e){}return true;},
    saveWeekly:function(){try{saveReview();}catch(e){return false;}return true;},
    weeklyFill:function(){var b=document.getElementById('aiFillReviewBtn');if(!b||b.disabled)return false;if(!(typeof journalAIKey==='function'&&journalAIKey()))return 'nokey';aiFillEntireReview(b);return true;},
    /** Change a Wellness value (BP, weight, sleep, steps…) — the Command Center's auto-checks react as if typed there. */
    setField:function(id,value){
      if(!WELL_IDS[id])return false;goRealDay();
      var el=document.getElementById(id);if(!el)return false;value=String(value==null?'':value);if(el.value===value)return true;
      el.value=value;
      try{el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}
      try{save();}catch(e){}refresh();return true;
    },
    /** Daily AI Review: change a field (saved like typing in the Command Center). */
    setReview:function(id,value){
      if(!REV.some(function(r){return r[0]===id;}))return false;goRealDay();
      var el=document.getElementById(id);if(!el)return false;el.value=String(value||'');
      try{if(typeof autoGrowReviewField==='function')autoGrowReviewField(el);}catch(e){}
      try{saveDayReview(true);}catch(e){}return true;
    },
    saveReview:function(){goRealDay();try{saveDayReview(true);}catch(e){return false;}try{if(window.BSNative&&BSNative.changed)BSNative.changed();}catch(e){}return true;},
    /** ✨ AI Fill Day Review — the Command Center's own button (its AI key, its full prompt). */
    reviewFill:function(){goRealDay();var b=document.getElementById('aiFillDayReviewBtn');if(!b||b.disabled)return false;
      if(!(typeof journalAIKey==='function'&&journalAIKey()))return 'nokey';aiFillEntireDayReview(b);return true;},
    /** Everything the native Today screen shows: {date, focus:[…], checks:[{key|id, group, label, state}]} */
    all:function(){goRealDay();var f=JSON.parse(window.ccNative.focus());f.checks=checks();f.schedule=schedule();f.review=review();f.wellness=wellness();try{var sd=document.getElementById('bsSleepDetail');f.sleepDetail=sd&&sd.style.display!=='none'?sd.textContent:'';}catch(e){f.sleepDetail='';}f.bible=bible();f.meditation=meditation();f.weekly=weekly();f.journal=journalText();f.library=library();return JSON.stringify(f);},
    /** My Schedule: tap cycles done → not done → not marked, exactly like the Command Center's own button. */
    scheduleTap:function(id){goRealDay();try{renderJournalSchedule();journalScheduleCycleStatus(null,id);}catch(e){return false;}refresh();try{if(window.BSNative&&BSNative.changed)BSNative.changed();}catch(e){}return true;},
    /** Set a daily check (tri-check key like 'wBP') to '', green, black, purple or red. */
    setCheck:function(key,state){
      if(!key||STATES.indexOf(state)<0)return false;goRealDay();
      var els=document.querySelectorAll('.tri-check[data-key="'+key+'"]');if(!els.length)return false;
      els.forEach(function(e){applyTriState(e,state);});
      try{save();}catch(e){}refresh();
      try{var wt=document.getElementById('tab-weekly');if(wt&&wt.classList.contains('active')&&typeof renderWeekly==='function')renderWeekly();}catch(e){}
      return true;
    },
    /** Plain checkboxes (Planning & Review). */
    setBox:function(id,on){
      if(id!=='reviewCheck'&&id!=='prepJournalCheck')return false;goRealDay();
      var el=document.getElementById(id);if(!el)return false;
      if(el.checked!==!!on){el.checked=!!on;try{el.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}try{save();}catch(e){}refresh();}
      return true;
    },
    /** Today's Focus for the real today: {date, items:[{id, kind, text, state}]} */
    focus:function(){
      var live=onRealDay(),d=live?null:stored(),out=[];
      IDS.forEach(function(id){
        var text='',state='';
        if(live){
          var row=document.getElementById(id),tx=row&&row.querySelector('input[type=text]'),ck=row&&row.querySelector('.tri-check');
          text=tx?tx.value:'';state=ck?(ck.dataset.state||''):'';
        }else{text=d[id+'t']||'';state=d[id+'c']||'';}
        out.push({id:id,kind:id.charAt(0)==='p'?'task':'goal',text:text,state:state});
      });
      return JSON.stringify({date:realKey(),items:out});
    },
    setText:function(id,value){
      if(IDS.indexOf(id)<0)return false;value=String(value||'');
      if(onRealDay()){
        var row=document.getElementById(id),tx=row&&row.querySelector('input[type=text]');if(!tx)return false;
        if(tx.value===value)return true;
        tx.value=value;try{save();}catch(e){}refresh();return true;
      }
      return writeStored(function(d){d[id+'t']=value;});
    },
    setState:function(id,state){
      if(IDS.indexOf(id)<0||STATES.indexOf(state)<0)return false;
      if(onRealDay()){
        document.querySelectorAll('.tri-check[data-key="'+id+'c"]').forEach(function(e){applyTriState(e,state);});
        var row=document.getElementById(id);if(row)row.classList.toggle('done',state==='green');
        try{save();}catch(e){}refresh();return true;
      }
      return writeStored(function(d){d[id+'c']=state;});
    }
  };
  // Tell BeyondStride when the Command Center saves, so its screens update (auto-checks, carry-over, edits here).
  function hook(){
    if(typeof window.save!=='function'||window.save.__native)return;
    var orig=window.save;
    var wrapped=function(){var r=orig.apply(this,arguments);try{if(window.BSNative&&BSNative.changed)BSNative.changed();}catch(e){}return r;};
    wrapped.__native=true;window.save=wrapped;
  }
  function hookRev(){
    if(typeof window.saveDayReview!=='function'||window.saveDayReview.__native)return;
    var o=window.saveDayReview,w=function(){var r=o.apply(this,arguments);try{if(window.BSNative&&BSNative.changed)BSNative.changed();}catch(e){}return r;};
    w.__native=true;window.saveDayReview=w;
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){hook();hookRev();});else{hook();hookRev();}
  setTimeout(hookRev,1500);
  setTimeout(hook,1500);
})();
