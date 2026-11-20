/* ✅ Auto checkmarks (Nov 2026): when a Daily item is filled in, its box turns green on its own.
 * Meds stay manual. A box you've already marked (any color) is never changed, and once a box has been auto-ticked
 * for a day it isn't re-ticked if you clear it yourself. */
(function(){
  // check key → input id (+ when it counts as done)
  var ITEMS=[
    ['wBP','wBPVal',function(v){return /\d{2,3}\s*\/\s*\d{2,3}/.test(v);}],
    ['wWght','wWghtVal',function(v){return parseFloat(v)>0;}],
    ['wSleep','wSleepVal',function(v){return /\d/.test(v);}],
    ['exDogWalk','exDogWalkVal',function(v){return v.trim()!=='';}],
    ['exStepsCheck','exSteps',function(v){return Number(v)>=10000;}]
  ];
  function dayKey(){try{return typeof dk==='function'&&typeof today!=='undefined'?dk(today):'';}catch(e){return '';}}
  function check(){
    var d=dayKey();if(!d)return;var changed=false;
    ITEMS.forEach(function(it){
      var box=document.querySelector('.tri-check[data-key="'+it[0]+'"]'),inp=document.getElementById(it[1]);
      if(!box||!inp)return;
      var flag='autoTick_'+d+'_'+it[0];
      // Marked red automatically for a missed dog walk, then filled in later: turn it green.
      try{if(box.dataset.state==='red'&&localStorage.getItem('autoRed_'+d+'_'+it[0])&&it[2](String(inp.value||''))){
        if(typeof applyTriState==='function')applyTriState(box,'green');localStorage.removeItem('autoRed_'+d+'_'+it[0]);localStorage.setItem(flag,'1');changed=true;return;}}catch(e){}
      if(box.dataset.state)return;                       // already marked by you (or before)
      try{if(localStorage.getItem(flag))return;}catch(e){}
      if(!it[2](String(inp.value||'')))return;
      if(typeof applyTriState==='function')applyTriState(box,'green');else{box.dataset.state='green';box.classList.add('green');box.textContent='✓';}
      var row=box.closest('.task-row,.goal-row');if(row)row.classList.add('done');
      try{localStorage.setItem(flag,'1');}catch(e){}
      changed=true;
    });
    if(changed)refresh();
  }
  function refresh(){
    try{if(typeof save==='function')save();}catch(e){}
    try{if(typeof renderDailyCheckOverview==='function')renderDailyCheckOverview();}catch(e){}
    try{if(typeof updateCompactSectionCounts==='function')updateCompactSectionCounts();}catch(e){}
  }
  /* 🐕 When the Daily AI Review is written (AI Fill, the 11:50 PM automatic one, or the phone's copy):
   * no Dog Walk entered and the box still blank → red checkmark. Entering a walk later turns it green. */
  window.autoTickReviewWritten=function(){
    try{
      var d=dayKey();if(!d)return;var changed=false;
      // Every blank Wellness / Movement / Audiobook check that isn't done → red. Meds stays yours.
      ITEMS.concat([['gaAudiobook','',function(){return window.audiobookDone&&window.audiobookDone();}]]).forEach(function(it){
        var box=document.querySelector('.tri-check[data-key="'+it[0]+'"]');if(!box||box.dataset.state)return;
        var inp=it[1]?document.getElementById(it[1]):null;if(it[2](String(inp?inp.value||'':'')))return;
        // Planned rest day: steps short of 10,000 are N/A, not missed.
        if(it[0]==='exStepsCheck'){try{if(typeof journalTodayTrainingSummary==='function'&&journalTodayTrainingSummary()==='Rest Day'){applyTriState(box,'black');changed=true;return;}}catch(eR){}}
        if(typeof applyTriState==='function')applyTriState(box,'red');else{box.dataset.state='red';box.classList.add('red');box.textContent='✓';}
        try{localStorage.setItem('autoRed_'+d+'_'+it[0],'1');}catch(e){}
        changed=true;
      });
      if(changed)refresh();
    }catch(e){}
  };
  var t=null;function soon(){clearTimeout(t);t=setTimeout(check,400);}
  document.addEventListener('input',function(e){if(e.target&&ITEMS.some(function(it){return it[1]===e.target.id;}))soon();},true);
  document.addEventListener('change',function(e){if(e.target&&ITEMS.some(function(it){return it[1]===e.target.id;}))soon();},true);
  // Values filled by the phone, by loading a day, or by changing the date.
  setInterval(check,5000);
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){setTimeout(check,1500);});else setTimeout(check,1500);
  window.autoTickCheck=check;
})();
/* 🎯 Today's Focus opens each time the page loads (tap the header to fold it for now). */
(function(){
  var userClosed=false;
  document.addEventListener('click',function(e){if(e.isTrusted&&e.target&&e.target.closest&&e.target.closest('[aria-controls="todayFocusBody"]'))userClosed=true;},true);
  function openFocus(){
    if(userClosed)return;
    var b=document.getElementById('todayFocusBody'),a=document.getElementById('todayFocusArrow'),t=document.querySelector('[aria-controls="todayFocusBody"]');
    if(!b)return;
    if(b.style.display!=='block'){b.style.display='block';if(a)a.innerHTML='&#x25BC;';if(t)t.setAttribute('aria-expanded','true');}
  }
  function run(){openFocus();[800,2500,6000].forEach(function(ms){setTimeout(openFocus,ms);});}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',run);else run();
  window.addEventListener('pageshow',function(){userClosed=false;run();});
})();

/* ✨ AI Tools from the ☰ Tools menu (any tab). */
window.openAiTool=function(which){
  try{
    var bar=document.getElementById('aiToolbar'),body=document.getElementById('aiToolsBody'),tg=document.getElementById('aiToolsToggle');
    // Search and Saved open as pop-ups; Ask AI opens its panel under the header.
    if(which==='search'&&typeof openJournalSearch==='function'){openJournalSearch();return;}
    if(which==='saved'&&typeof openAIArchive==='function'){openAIArchive();return;}
    if(!bar||!body)return;
    window.__aiToolsOpen=true;bar.style.display='block';body.style.display='block';if(tg)tg.style.display='none';
    if(which==='ask'&&typeof openAskJournal==='function')openAskJournal();
    else if(which==='search'&&typeof openJournalSearch==='function')openJournalSearch();
    else if(which==='saved'&&typeof openAIArchive==='function')openAIArchive();
    setTimeout(function(){try{bar.scrollIntoView({behavior:'smooth',block:'start'});var i=document.getElementById(which==='ask'?'askJournalInput':'journalSearchInput');if(i&&i.offsetParent)i.focus();}catch(e){}},80);
  }catch(e){}
};
window.closeAiTools=function(){window.__aiToolsOpen=false;var bar=document.getElementById('aiToolbar');if(bar)bar.style.display='none';};

/* 🧹 One-time cleanup (Nov 15 2026): two leftover keys from the old food tracker that nothing reads anymore
 * (favorites_data, healthy_foods_7plus — nutrition lives in BeyondStride now). A copy is kept in the browser's
 * large storage first ("cleanup_2026-11-15"), then they're removed to free journal space. */
(function(){
  var FLAG='cleanup_food_leftovers_2026-11-15',KEYS=['favorites_data','healthy_foods_7plus'];
  function run(){
    try{
      if(localStorage.getItem(FLAG))return;
      var copy={},any=false;KEYS.forEach(function(k){var v=localStorage.getItem(k);if(v!=null){copy[k]=v;any=true;}});
      if(!any){localStorage.setItem(FLAG,'1');return;}
      if(typeof journalLargeStoreSet!=='function')return;
      Promise.resolve(journalLargeStoreSet('cleanup_2026-11-15',copy)).then(function(){
        KEYS.forEach(function(k){try{localStorage.removeItem(k);}catch(e){}});
        try{localStorage.setItem(FLAG,'1');}catch(e){}
      }).catch(function(){});
    }catch(e){}
  }
  setTimeout(run,4000);
})();

/* 🏃 A task or goal like "Complete scheduled runs" ticks green once today's run is logged (BeyondStride / Activities).
 * Blank or partly-done (purple) boxes only; red / black ones you set are left alone. */
(function(){
  var RUN_ITEM=/\b(complete|finish|do|get in|hit)\b.*\brun|\bscheduled runs?\b|\brun(s)? (today|as planned|done)\b|^\s*run\b/i;
  function check(){
    try{
      if(typeof dk!=='function'||typeof today==='undefined'||typeof hasRunOnDate!=='function')return;
      var d=dk(today);if(!hasRunOnDate(d))return;var changed=false;
      ['pt0','pt1','pt2','dg0','dg1','dg2'].forEach(function(id){
        var inp=document.getElementById(id+'t'),box=document.querySelector('.tri-check[data-key="'+id+'c"]');
        if(!inp||!box||!RUN_ITEM.test(String(inp.value||'')))return;
        var st=box.dataset.state||'';if(st&&st!=='purple')return;
        try{if(localStorage.getItem('autoRun_'+d+'_'+id))return;}catch(e){}   // once a day: if you change it back, it stays
        if(typeof applyTriState==='function')applyTriState(box,'green');
        var row=box.closest('.priority-item');if(row)row.classList.add('done');
        try{localStorage.setItem('autoRun_'+d+'_'+id,'greenx');}catch(e){}
        changed=true;
      });
      if(changed){try{save();}catch(e){}try{renderDailyCheckOverview();}catch(e){}try{updateCompactSectionCounts();}catch(e){}}
    }catch(e){}
  }
  setInterval(check,8000);setTimeout(check,2500);
})();

/* ⏭️ Unfinished tasks / goals carry over: the first time today's page opens, any of yesterday's Priority Tasks or Goals
 * that weren't finished (not green, not N/A) fill today's empty lines. Once per day. */
(function(){
  function run(){
    try{
      if(typeof dk!=='function'||typeof today==='undefined')return;
      var now=new Date(),d=dk(now);if(dk(today)!==d)return;
      var flag='carryOver_'+d;if(localStorage.getItem(flag))return;
      var y=new Date(now);y.setDate(y.getDate()-1);
      var prev={};try{prev=JSON.parse(localStorage.getItem('planner_'+dk(y))||'{}')||{};}catch(e){}
      var moved=0;
      [['pt0','pt1','pt2'],['dg0','dg1','dg2']].forEach(function(group){
        var left=group.map(function(id){return {t:String(prev[id+'t']||'').trim(),st:prev[id+'c']||''};}).filter(function(x){return x.t&&x.st!=='green'&&x.st!=='black';});
        var have=group.map(function(id){var e=document.getElementById(id+'t');return e?String(e.value||'').trim():'';});
        left=left.filter(function(x){return have.indexOf(x.t)===-1;});
        group.forEach(function(id){
          var e=document.getElementById(id+'t');if(!e||String(e.value||'').trim()||!left.length)return;
          e.value=left.shift().t;e.dispatchEvent(new Event('input',{bubbles:true}));moved++;
        });
      });
      localStorage.setItem(flag,String(moved));
      if(moved){try{save();}catch(e){}try{updateCompactSectionCounts();}catch(e){}if(typeof v26Toast==='function')v26Toast('⏭️ Carried over '+moved+' unfinished item'+(moved>1?'s':'')+' from yesterday');}
    }catch(e){}
  }
  setTimeout(run,2200);
})();

/* 🎧 Audiobook: 30+ minutes logged today (Library / Reading log, or the Audiobook time box) → Audiobook check green,
 * and a goal like "Audible for 30 minutes" ticks once that many minutes are logged. */
(function(){
  function minsToday(){
    var m=0;
    try{var d=dk(today);(getSkillEntries()||[]).forEach(function(e){if(e&&e.date===d&&/^(reading \/ audiobook|reading|audiobook)$/i.test(String(e.category||'')))m+=parseSkillTimeMins(e.time)||0;});}catch(e){}
    try{var t=document.getElementById('gaAudiobookTime');if(t&&t.value)m=Math.max(m,parseSkillTimeMins(t.value)||0);}catch(e){}
    return m;
  }
  window.audiobookDone=function(){return minsToday()>=30;};
  function check(){
    try{
      if(typeof dk!=='function'||typeof today==='undefined')return;
      var d=dk(today),m=minsToday();if(m<=0)return;var changed=false;
      var box=document.querySelector('.tri-check[data-key="gaAudiobook"]');
      if(box&&m>=30){
        var st=box.dataset.state||'',wasAutoRed=st==='red'&&localStorage.getItem('autoRed_'+d+'_gaAudiobook');
        if((!st||wasAutoRed)&&!localStorage.getItem('autoTick_'+d+'_gaAudiobook')){applyTriState(box,'green');localStorage.setItem('autoTick_'+d+'_gaAudiobook','1');localStorage.removeItem('autoRed_'+d+'_gaAudiobook');changed=true;}
      }
      ['pt0','pt1','pt2','dg0','dg1','dg2'].forEach(function(id){
        var inp=document.getElementById(id+'t'),b=document.querySelector('.tri-check[data-key="'+id+'c"]');if(!inp||!b)return;
        var t=String(inp.value||'');if(!/audible|audiobook|listen/i.test(t))return;
        var need=parseInt((t.match(/(\d+)\s*(?:min|minutes)/i)||[])[1]||'30',10);
        var st=b.dataset.state||'';if((st&&st!=='purple')||m<need||localStorage.getItem('autoBook_'+d+'_'+id))return;
        applyTriState(b,'green');localStorage.setItem('autoBook_'+d+'_'+id,'1');changed=true;
      });
      if(changed){try{save();}catch(e){}try{renderDailyCheckOverview();}catch(e){}try{updateCompactSectionCounts();}catch(e){}}
    }catch(e){}
  }
  setInterval(check,8000);setTimeout(check,3000);
})();

/* 😴 Planned rest day: run goals / tasks are marked N/A (black) instead of counting as missed (the 10,000-step goal
 * becomes N/A at review time if it wasn't reached). Only blank boxes; once a day. */
(function(){
  var RUN_ITEM=/\b(complete|finish|do|get in|hit)\b.*\brun|\bscheduled runs?\b|\brun(s)? (today|as planned|done)\b|^\s*run\b/i;
  function check(){
    try{
      if(typeof dk!=='function'||typeof journalTodayTrainingSummary!=='function')return;
      if(journalTodayTrainingSummary()!=='Rest Day')return;
      var d=dk(today),changed=false;
      function na(box,key){if(!box||box.dataset.state||localStorage.getItem('autoRest_'+d+'_'+key))return;applyTriState(box,'black');localStorage.setItem('autoRest_'+d+'_'+key,'1');changed=true;}
      ['pt0','pt1','pt2','dg0','dg1','dg2'].forEach(function(id){var inp=document.getElementById(id+'t');if(inp&&RUN_ITEM.test(String(inp.value||'')))na(document.querySelector('.tri-check[data-key="'+id+'c"]'),id);});
      if(changed){try{save();}catch(e){}try{renderDailyCheckOverview();}catch(e){}try{updateCompactSectionCounts();}catch(e){}}
    }catch(e){}
  }
  setInterval(check,15000);setTimeout(check,3500);
})();
