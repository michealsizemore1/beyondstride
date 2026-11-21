// ══════════════════════════════════════════════════════
// BEYONDSTRIDE → DAILY / WEEKLY REVIEW (read-only)
// BeyondStride posts a short summary of each day (runs, training plan, sleep / HRV / readiness, nutrition)
// to <db>/nutrition/<key>/beyondstride/daily/<yyyy-mm-dd>. This page only READS it: AI Fill Day Review and
// AI Fill Review include it, and the Daily AI Review shows a "From BeyondStride" box. Nothing is sent back.
// The database link comes from BeyondStride (Settings → Stand-alone & cloud copy → Link the Command Center review),
// which opens this page with #bsr=<base64url "db|key">; a link saved earlier is reused.
// ══════════════════════════════════════════════════════
(function(){
  var CFG_KEY='beyondstride_sync';
  function cfg(){try{return JSON.parse(localStorage.getItem(CFG_KEY)||'null');}catch(e){return null;}}
  (function readLink(){
    var m=(location.hash||'').match(/[#&]bsr?=([A-Za-z0-9_-]+)/);if(!m)return;
    try{var b64=m[1].replace(/-/g,'+').replace(/_/g,'/');while(b64.length%4)b64+='=';
      var parts=atob(b64).split('|');
      if(parts.length===2&&/^https:\/\//.test(parts[0])&&parts[1].length>=16)localStorage.setItem(CFG_KEY,JSON.stringify({db:parts[0].replace(/\/$/,''),key:parts[1]}));
    }catch(e){}
  })();

  function nice(k){try{return new Date(k+'T00:00:00').toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric'});}catch(e){return k;}}

  // Day-by-day text for [from..to] (yyyy-mm-dd), or '' when not linked / nothing there.
  window.bsReviewFetch=function(from,to,cb){
    var c=cfg();if(!c||!c.db||!c.key){cb('');return;}
    var url=c.db+'/nutrition/'+encodeURIComponent(c.key)+'/beyondstride/daily.json?orderBy=%22%24key%22&startAt=%22'+from+'%22&endAt=%22'+to+'%22';
    var done=false,t=setTimeout(function(){if(!done){done=true;cb('');}},12000);
    fetch(url).then(function(r){return r.ok?r.json():null;}).then(function(o){
      if(done)return;done=true;clearTimeout(t);
      if(!o){cb('');return;}
      var keys=Object.keys(o).sort();
      if(keys.length===1){lastDay={key:keys[0],data:o[keys[0]]};var ev=(o[keys[0]]||{}).evening;if(ev)window.bsEvening={date:keys[0],time:ev.time||0,early:!!ev.early};}
      cb(keys.map(function(k){return (keys.length>1?nice(k)+':\n':'')+String((o[k]||{}).text||'').trim();}).join('\n\n'));
    }).catch(function(){if(!done){done=true;clearTimeout(t);cb('');}});
  };

  var lastDay=null;

  // ── Weekly screen: Nutrition and Micronutrients tables (read from BeyondStride's day summaries) ──
  var NU_KEY='bs_daily_nutrition';
  function nuCache(){try{return JSON.parse(localStorage.getItem(NU_KEY)||'{}')||{};}catch(e){return {};}}
  function fmtN(v){return v?Math.round(v).toLocaleString('en-US'):'-';}
  // Same 12 nutrients as BeyondStride's micronutrient panel.
  var MICRO_COLS=[['fiber','Fiber (g)'],['sugar','Sugar (g)'],['sodium','Sodium (mg)'],['potassium','Potassium (mg)'],['calcium','Calcium (mg)'],['magnesium','Magnesium (mg)'],
    ['iron','Iron (mg)'],['zinc','Zinc (mg)'],['vitC','Vit C (mg)'],['vitD','Vit D (mcg)'],['vitB12','Vit B12 (mcg)'],['caffeine','Caffeine (mg)']];
  var ST_COLOR={Low:'#dc2626',Close:'#ca8a04',Met:'#16a34a',High:'#dc2626'};
  function status(v,t){if(!t)return'';var pct=t.t>0?v/t.t*100:0;return (t.mx>0&&v>t.mx)?'High':pct>=100?'Met':pct>=75?'Close':'Low';}
  function microCell(v,t,st){
    if(!v)return'<td>-</td>';
    var val=v<10?(Math.round(v*10)/10):Math.round(v).toLocaleString('en-US');
    if(!t||!t.t)return'<td>'+val+'</td>';
    st=st||status(v,t);var pct=Math.floor(v/t.t*100);
    // High: a small red pill with white text.
    if(st==='High')return'<td>'+val+'<div><span style="display:inline-block;margin-top:2px;padding:1px 8px;border-radius:999px;background:#ef4444;color:#fff;font-size:.72em;font-weight:700;white-space:nowrap;">'+pct+'% · High</span></div></td>';
    return'<td>'+val+'<div style="font-size:.72em;font-weight:600;color:'+(ST_COLOR[st]||'#64748b')+';white-space:nowrap;">'+pct+'% · '+st+'</div></td>';
  }
  function nuTables(keys,labels){
    var cache=nuCache(),el=document.getElementById('wNutrition'),em=document.getElementById('wNutritionMicro');if(!el)return;
    // Colors as in BeyondStride: calories orange, protein red, carbs amber, fat blue, fluids blue; status green / amber / red.
    var hc=function(t,c){return'<th><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:'+c+';margin-right:5px;vertical-align:middle;"></span>'+t+'</th>';};
    var th='<table class="summary-table"><thead><tr><th>Period</th>'+hc('Food Quality','#4ADE80')+hc('Calories','#F39C12')+hc('Protein (g)','#F87171')+hc('Fat (g)','#60A5FA')+hc('Carbs (g)','#FBBF24')+hc('Fluids (oz)','#38BDF8')+'</tr></thead><tbody>';
    var G='#16a34a',A='#d97706',R='#dc2626';
    function sub(txt,c){return'<div style="font-size:.72em;font-weight:600;color:'+c+';white-space:nowrap;">'+txt+'</div>';}
    function calCell(n){if(!n.cal)return'<td>-</td>';var h=fmtN(n.cal);if(!n.target)return'<td>'+h+'</td>';var r=n.cal/n.target,c=(r>=0.9&&r<=1.1)?G:(r>=0.75&&r<=1.25)?A:R;
      return'<td><span style="color:'+c+';font-weight:700;">'+h+'</span> <span style="color:#94a3b8;font-size:.8em;">/ '+fmtN(n.target)+'</span>'+sub(Math.round(r*100)+'% of target',c)+'</td>';}
    function macroCell(g,m){if(!g)return'<td>-</td>';if(!m)return'<td>'+fmtN(g)+'</td>';var c=m.status==='On target'?G:m.status==='High'?R:A;
      if(m.status==='High')return'<td>'+fmtN(g)+'<div><span style="display:inline-block;margin-top:2px;padding:1px 8px;border-radius:999px;background:#ef4444;color:#fff;font-size:.72em;font-weight:700;white-space:nowrap;">'+m.pct.toFixed(1)+'% / '+m.goal+'% · High</span></div></td>';
      return'<td>'+fmtN(g)+sub(m.pct.toFixed(1)+'% / '+m.goal+'% · '+m.status,c)+'</td>';}
    function fluidCell(n){if(!n.water)return'<td>-</td>';if(!n.fluidTarget)return'<td>'+fmtN(n.water)+'</td>';var p=Math.floor(n.water/n.fluidTarget*100),c=p>=100?G:p>=75?A:R;
      return'<td>'+fmtN(n.water)+sub(p+'% of '+n.fluidTarget+' oz',c)+'</td>';}
    function mLabel(v){return !v?'':v>=9?'Excellent':v>=7?'Good':v>=5?'Fair':v>=3?'Low':'Poor';}
    function qCell(q,label){if(!q)return'<td>-</td>';var c=q>=7?G:q>=5?A:R;return'<td><span style="color:'+c+';font-weight:700;">'+q.toFixed(1)+'</span>'+(label?sub(label,c):'')+'</td>';}
    var tot={cal:0,target:0,prot:0,fat:0,carbs:0,water:0,q:0,qn:0,n:0},rows='',mrows='',mt={},lastT={};
    keys.forEach(function(k,i){var n=cache[k];
      if(!n){rows+='<tr><td>'+labels[i]+'</td><td>-</td><td>-</td><td>-</td><td>-</td><td>-</td><td>-</td></tr>';mrows+='<tr><td>'+labels[i]+'</td><td>-</td>'+MICRO_COLS.map(function(){return'<td>-</td>';}).join('')+'</tr>';return;}
      tot.n++;tot.cal+=n.cal||0;tot.target+=n.target||0;tot.prot+=n.prot||0;tot.fat+=n.fat||0;tot.carbs+=n.carbs||0;tot.water+=n.water||0;if(n.quality){tot.q+=n.quality;tot.qn++;}
      var mc=n.macros||{};if(n.fluidTarget)tot.ft=(tot.ft||0)+n.fluidTarget;
      rows+='<tr><td>'+labels[i]+'</td>'+qCell(n.quality,n.qualityLabel)+calCell(n)+macroCell(n.prot,mc.protein)+macroCell(n.fat,mc.fat)+macroCell(n.carbs,mc.carbs)+fluidCell(n)+'</tr>';
      var m=n.micros||{},tg=n.microTargets||{};if(n.microScore){tot.ms=(tot.ms||0)+n.microScore;tot.msn=(tot.msn||0)+1;}
      mrows+='<tr><td>'+labels[i]+'</td>'+qCell(n.microScore||0,mLabel(n.microScore))+MICRO_COLS.map(function(c){var v=m[c[0]],t=tg[c[0]];if(t)lastT[c[0]]=t;if(v)mt[c[0]]=(mt[c[0]]||0)+v;return microCell(v,t,t&&t.s);}).join('')+'</tr>';
    });
    var d=tot.n||1,b='background:#f8f9fa;font-weight:700';
    var avgQ=tot.qn?tot.q/tot.qn:0,ql=avgQ>=9?'Excellent':avgQ>=7?'Good':avgQ>=5?'Fair':avgQ>=3?'Low':avgQ?'Poor':'';
    rows+='<tr style="'+b+'"><td>DAILY AVG</td>'+qCell(avgQ,ql)+(tot.n?calCell({cal:tot.cal/d,target:tot.target/d}):'<td>-</td>')+'<td>'+(tot.n?fmtN(tot.prot/d):'-')+'</td><td>'+(tot.n?fmtN(tot.fat/d):'-')+'</td><td>'+(tot.n?fmtN(tot.carbs/d):'-')+'</td>'
      +(tot.n?fluidCell({water:tot.water/d,fluidTarget:tot.ft?Math.round(tot.ft/d):0}):'<td>-</td>')+'</tr>';
    var avgM=tot.msn?tot.ms/tot.msn:0;
    mrows+='<tr style="'+b+'"><td>DAILY AVG</td>'+qCell(avgM,mLabel(avgM))+MICRO_COLS.map(function(c){var v=mt[c[0]]?mt[c[0]]/d:0;return microCell(v,lastT[c[0]]);}).join('')+'</tr>';
    el.innerHTML=th+rows+'</tbody></table>'+(tot.n?'':'<div style="font-size:.75rem;color:#94a3b8;margin-top:4px;">No BeyondStride nutrition for this week yet.</div>');
    if(em)em.innerHTML='<table class="summary-table"><thead><tr><th>Period</th>'+hc('Micro Quality','#4ADE80')+MICRO_COLS.map(function(c){return'<th>'+c[1]+'</th>';}).join('')+'</tr></thead><tbody>'+mrows+'</tbody></table>';
  }
  // Older summaries (before the phone sent the numbers): read them from the summary text.
  function fromText(t){
    t=String(t||'');var m=t.match(/^Calories:\s*([\d,]+)(?:\s+of\s+([\d,]+)\s+target)?(.*)$/m);if(!m)return null;
    function num(x){return x?parseFloat(String(x).replace(/,/g,'')):0;}
    function g(re,s){var x=(s||'').match(re);return x?num(x[1]):0;}
    var n={cal:num(m[1]),target:num(m[2]),prot:g(/protein\s+([\d.]+)/i,m[3]),carbs:g(/carbs\s+([\d.]+)/i,m[3]),fat:g(/fat\s+([\d.]+)/i,m[3]),micros:{}};
    n.water=g(/^Fluids:\s*([\d.]+)/m,t);n.quality=g(/^Food quality:\s*([\d.]+)/m,t);
    return n.cal?n:null;
  }
  var nuFetched={};
  /** The Day Review BeyondStride wrote on the phone for [k] (yyyy-mm-dd), or null. */
  window.bsPhoneReview=function(k,cb){
    var c=cfg();if(!c||!c.db||!c.key){cb(null);return;}
    fetch(c.db+'/nutrition/'+encodeURIComponent(c.key)+'/beyondstride/review/'+k+'.json').then(function(r){return r.ok?r.json():null;}).then(function(o){cb(o&&(o.wins||o.improve||o.focus||o.lessons)?o:null);}).catch(function(){cb(null);});
  };
  /** Saves Sleeping HR and HRV from BeyondStride's daily summaries; true when something new arrived. */
  function keepWellness(o){
    var changed=false;
    [['bs_daily_sleephr','sleepHr'],['bs_daily_hrv','hrv'],['bs_daily_deep','deep'],['bs_daily_rem','rem'],['bs_daily_sleepscore','sleepScore']].forEach(function(p){
      var c={};try{c=JSON.parse(localStorage.getItem(p[0])||'{}')||{};}catch(e){}var ch=false;
      Object.keys(o||{}).forEach(function(k){var v=((o[k]||{}).wellness||{})[p[1]];if(v&&c[k]!==v){c[k]=v;ch=true;}});
      if(ch){try{localStorage.setItem(p[0],JSON.stringify(c));}catch(e1){}changed=true;}
    });
    return changed;
  }
  // Monthly / Yearly Wellness: read the days it hasn't seen (at most every 10 minutes per range).
  var wFetched={};
  window.bsWellnessFetch=function(keys,redraw){
    var c=cfg();if(!c||!c.db||!c.key||!keys||!keys.length)return;
    var ks=keys.slice().sort(),from=ks[0],to=ks[ks.length-1],today=new Date().toISOString().slice(0,10);if(to>today)to=today;if(from>to)return;
    var sig=from+to;if(wFetched[sig]&&Date.now()-wFetched[sig]<10*60000)return;wFetched[sig]=Date.now();
    fetch(c.db+'/nutrition/'+encodeURIComponent(c.key)+'/beyondstride/daily.json?orderBy=%22%24key%22&startAt=%22'+from+'%22&endAt=%22'+to+'%22').then(function(r){return r.ok?r.json():null;}).then(function(o){
      if(o&&keepWellness(o)&&redraw)redraw();
    }).catch(function(){});
  };
  window.bsWeeklyNutrition=function(keys,labels){
    nuTables(keys,labels);
    var c=cfg();if(!c||!c.db||!c.key||!keys.length)return;
    var from=keys[0],to=keys[keys.length-1],sig=from+to;if(nuFetched[sig]&&Date.now()-nuFetched[sig]<2*60000)return;nuFetched[sig]=Date.now();
    fetch(c.db+'/nutrition/'+encodeURIComponent(c.key)+'/beyondstride/daily.json?orderBy=%22%24key%22&startAt=%22'+from+'%22&endAt=%22'+to+'%22').then(function(r){return r.ok?r.json():null;}).then(function(o){
      if(!o)return;var cache=nuCache(),changed=false;
      // Sleeping HR and HRV for the Wellness tables.
      if(keepWellness(o)){try{if(typeof renderWeekly==='function')renderWeekly();}catch(e2){}}
      Object.keys(o).forEach(function(k){var n=(o[k]||{}).nutrition||fromText((o[k]||{}).text);if(n&&JSON.stringify(cache[k])!==JSON.stringify(n)){cache[k]=n;changed=true;}});
      if(!changed)return;
      // Keep about four months.
      var cut=new Date();cut.setDate(cut.getDate()-120);var ck=cut.toISOString().slice(0,10);Object.keys(cache).forEach(function(k){if(k<ck)delete cache[k];});
      try{localStorage.setItem(NU_KEY,JSON.stringify(cache));}catch(e){}
      nuTables(keys,labels);
    }).catch(function(){});
  };

  // Wellness: Sleep, Weight, Resting HR, Blood pressure + Pulse (cuff) and Steps (Samsung, under Dog Walk) fill in from BeyondStride. A value you typed yourself is never replaced;
  // one BeyondStride filled earlier is kept up to date.
  var FIELDS={sleep:'wSleepVal',weight:'wWghtVal',restHr:'wRestHRVal',steps:'exSteps',bp:'wBPVal',pulse:'wPulseVal',sleepScore:'wSleepScore'};
  // 😴 Last night's breakdown (read-only) under Sleep Score: BeyondStride's score, deep / REM / light / awake, bed–wake.
  function showSleepDetail(w){
    try{
      var sc=document.getElementById('wSleepScore');if(!sc)return;var row=sc.closest('.task-row')||sc.parentNode;
      var box=document.getElementById('bsSleepDetail');
      if(!box){box=document.createElement('div');box.id='bsSleepDetail';box.style.cssText='font-size:0.82rem;color:#6b7280;margin:2px 0 6px 34px;line-height:1.4;';row.parentNode.insertBefore(box,row.nextSibling);}
      function hm(m){m=Math.round(Number(m)||0);return m>=60?Math.floor(m/60)+'h '+(m%60)+'m':m+'m';}
      var parts=[];
      if(w&&w.sleepScore)parts.push('BeyondStride score '+w.sleepScore);
      if(w&&(w.deep||w.rem)){parts.push('Deep '+hm(w.deep));parts.push('REM '+hm(w.rem));if(w.light)parts.push('Light '+hm(w.light));}
      if(w&&w.awake!=null&&w.awake!=='')parts.push('Awake '+hm(w.awake));
      if(w&&w.bed&&w.wake)parts.push(w.bed+'–'+w.wake);
      box.textContent=parts.length?'😴 '+parts.join(' · '):'';box.style.display=parts.length?'':'none';
    }catch(e){}
  }
  function fillWellness(k,data){
    var w=data&&data.wellness;if(typeof dk==='function'&&dk(today)===k)showSleepDetail(w);
    if(!w||typeof dk!=='function'||dk(today)!==k)return;
    var store='bsAutoWellness_'+k,prev={};try{prev=JSON.parse(localStorage.getItem(store)||'{}');}catch(e){}
    var changed=false;
    // One-time (Nov 14, 2026, asked for): replace today's typed BP / pulse with the average of today's readings,
    // once the phone sends the averaged value (bpN = how many readings it averaged).
    var forceBp=k==='2026-11-14'&&w.bpN&&!localStorage.getItem('bsForceBp_'+k);
    if(forceBp){prev.bp=(document.getElementById('wBPVal')||{}).value||'';prev.pulse=(document.getElementById('wPulseVal')||{}).value||'';try{localStorage.setItem('bsForceBp_'+k,'1');}catch(eF){}}
    Object.keys(FIELDS).forEach(function(f){
      if(w[f]==null||w[f]==='')return;
      var el=document.getElementById(FIELDS[f]);if(!el)return;
      var v=String(w[f]),cur=el.value.trim();
      // Steps only go up during the day, so a higher count from BeyondStride always wins. (The "typed by hand" memory
      // below is kept per browser, so a count filled on another device or restored by cloud sync looked hand-typed
      // and froze the field.) A higher number typed by hand is still kept.
      var stepsUp=f==='steps'&&Number(v)>(parseFloat(cur.replace(/,/g,''))||0);
      if(!stepsUp&&cur!==''&&cur!==String(prev[f]||'').trim())return;   // typed by hand: leave it
      if(cur!==v){el.value=v;el.dispatchEvent(new Event('input',{bubbles:true}));changed=true;}
      prev[f]=v;
    });
    // Blood pressure taken: tick BP (unless you've marked it yourself).
    try{var bc=document.querySelector('.tri-check[data-key="wBP"]');
      if(bc&&w.bp&&!bc.dataset.state){bc.dataset.state='green';if(typeof save==='function')save();if(typeof renderDailyCheckOverview==='function')renderDailyCheckOverview();changed=true;}}catch(eB){}
    // 10,000 steps reached: tick the goal (unless you've marked it yourself).
    try{var sc=document.querySelector('.tri-check[data-key="exStepsCheck"]');
      if(sc&&Number(w.steps)>=10000&&!sc.dataset.state){sc.dataset.state='green';if(typeof save==='function')save();if(typeof renderDailyCheckOverview==='function')renderDailyCheckOverview();changed=true;}}catch(eS){}
    try{localStorage.setItem(store,JSON.stringify(prev));}catch(e){}
    if(changed&&typeof v26Toast==='function')v26Toast('Filled from BeyondStride (sleep / sleep score / weight / resting HR / steps / BP)');
  }

  // BeyondStride's phone app hands today's numbers straight in (no trip through the cloud): same rules as above —
  // a value you typed yourself is never replaced.
  window.bsFillWellnessNow=function(k,w){try{if(w&&typeof w==='object')fillWellness(k,{wellness:w});return true;}catch(e){return false;}};

  // "From BeyondStride" box in the Daily AI Review (collapsed; tap to open).
  var shownFor='',open=false;
  function box(){
    var sec=document.getElementById('dayReviewSection');if(!sec||!cfg())return null;
    var b=document.getElementById('bsDayBox');
    if(!b){b=document.createElement('div');b.id='bsDayBox';
      b.style.cssText='margin:0 0 12px;padding:10px 12px;border-radius:10px;background:#eef2ff;border:1px solid #c7d2fe;font-size:0.85rem;color:#312e81;';
      var grid=sec.querySelector('.review-grid');sec.insertBefore(b,grid||null);
      b.addEventListener('click',function(){open=!open;render(b._text||'');});}
    return b;
  }
  function esc(t){return String(t).replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];});}
  var GROUPS=[
    ['&#x1F3C3; Run',/^(workout|planned vs done)/i],
    ['&#x1F4C5; Plan',/^(planned|tomorrow|plan week|race day)/i],
    ['&#x1F634; Recovery',/^(sleep|morning check|readiness|hrv|resting)/i],
    ['&#x1F37D;&#xFE0F; Nutrition',/^(calories|food quality|micronutrients|low|over limit|fluids|protein|carbs)/i],
    ['&#x2696;&#xFE0F; Body',/^(weight)/i]];
  function chip(t,c){return '<span style="display:inline-block;margin:3px 4px 0 0;padding:3px 9px;border-radius:999px;background:'+(c||'#fff')+';border:1px solid #c7d2fe;font-size:.88rem;white-space:nowrap;">'+esc(t)+'</span>';}
  function row(label,val){return '<div style="padding:7px 0;border-top:1px solid #dbe2ff;text-align:left;"><div style="font-weight:600;color:#4338ca;font-size:.8rem;text-transform:uppercase;letter-spacing:.03em;margin-bottom:2px;">'+esc(label)+'</div><div style="text-align:left;">'+val+'</div></div>';}
  function workout(v){
    var notes='',i=v.indexOf(' — notes: ');if(i>=0){notes=v.slice(i+10);v=v.slice(0,i);}
    var parts=v.split(' · '),title=parts.slice(0,2).join(' · ');
    var h='<div style="font-weight:700;font-size:1rem;">'+esc(title)+'</div><div>'+parts.slice(2).map(function(x){return chip(x);}).join('')+'</div>';
    if(notes)h+='<div style="margin-top:8px;padding:8px 10px;border-left:3px solid #818cf8;background:#fff;border-radius:6px;font-style:italic;line-height:1.45;">'+esc(notes)+'</div>';
    return h;}
  function value(label,v){
    if(/^workout/i.test(label))return workout(v);
    if(/^planned vs done/i.test(label)){var up=v.indexOf('▲')>=0;return '<span style="font-weight:600;color:'+(up?'#b45309':'#047857')+';">'+esc(v)+'</span>';}
    if(/^low/i.test(label))return v.split(', ').map(function(x){return chip(x,'#fff4e5');}).join('');
    if(/^over limit/i.test(label))return v.split(', ').map(function(x){return chip(x,'#ffe4e6');}).join('');
    if(v.indexOf(' · ')>=0&&v.length<120&&v.indexOf('(')<0)return v.split(' · ').map(function(x,j){return j===0?'<span style="font-weight:600;">'+esc(x)+'</span><br>':chip(x);}).join('');
    return '<span style="line-height:1.45;">'+esc(v)+'</span>';}
  function pretty(text){
    var buckets=GROUPS.map(function(){return [];}),other=[];
    text.split('\n').forEach(function(l){l=l.trim();if(!l)return;
      var m=l.match(/^([^:]{1,24}):\s*(.*)$/),label=m?m[1]:l,val=m?m[2]:'';
      if(!m){var w=l.match(/^(plan week)\s*(.*)$/i);if(w){label='Plan week';val=w[2];}}
      var gi=-1;for(var g=0;g<GROUPS.length;g++){if(GROUPS[g][1].test(label)){gi=g;break;}}
      (gi>=0?buckets[gi]:other).push([label,val]);});
    var out='';
    GROUPS.forEach(function(g,gi){if(!buckets[gi].length)return;
      out+='<div style="margin-top:12px;padding:8px 12px 4px;background:#f8f9ff;border:1px solid #dbe2ff;border-radius:10px;"><div style="font-weight:700;font-size:.95rem;margin-bottom:4px;">'+g[0]+'</div>'+
        buckets[gi].map(function(r){return row(r[0],value(r[0],r[1]));}).join('')+'</div>';});
    if(other.length)out+='<div style="margin-top:12px;padding:8px 12px 4px;background:#f8f9ff;border:1px solid #dbe2ff;border-radius:10px;"><div style="font-weight:700;font-size:.95rem;margin-bottom:4px;">More</div>'+other.map(function(r){return row(r[0],value(r[0],r[1]));}).join('')+'</div>';
    return out;}
  // The box is no longer shown (AI Fill still reads the same information).
  var SHOW_BOX=false;
  function render(text){
    if(!SHOW_BOX)return;
    var b=box();if(!b)return;b._text=text;
    var head='&#x1F3C3; <strong>From BeyondStride</strong> '+(text?'<span style="opacity:.7">('+(open?'tap to hide':'tap to see what AI Fill uses')+')</span>':'<span style="opacity:.7">— nothing shared for this day yet</span>');
    b.innerHTML=head+(open&&text?'<div style="font-size:.95rem;color:#1e1b4b;text-align:left;">'+pretty(text)+'</div>':'');
  }
  // Training plan days for My Schedule (BeyondStride holds the plan now).
  var planAt=0;
  var actsAt=0;
  function fetchActs(){
    var c=cfg();if(!c||!c.db||!c.key||Date.now()-actsAt<2*60000)return;actsAt=Date.now();
    fetch(c.db+'/nutrition/'+encodeURIComponent(c.key)+'/beyondstride/activities.json').then(function(r){return r.ok?r.json():null;}).then(function(o){
      if(!o||!o.list)return;var now=JSON.stringify({list:o.list,at:o.at||0});if(localStorage.getItem('bs_activities')===now)return;
      try{localStorage.setItem('bs_activities',now);}catch(e){return;}
      try{if(typeof renderWeekly==='function')renderWeekly();}catch(e1){}
      try{if(typeof renderGoals==='function')renderGoals();}catch(e2){}
      try{if(typeof renderJournalSchedule==='function')renderJournalSchedule();}catch(e3){}
    }).catch(function(){});
  }
  function fetchPlan(){
    var c=cfg();if(!c||!c.db||!c.key||Date.now()-planAt<2*60000)return;planAt=Date.now();
    fetch(c.db+'/nutrition/'+encodeURIComponent(c.key)+'/beyondstride/plan.json').then(function(r){return r.ok?r.json():null;}).then(function(o){
      if(!o||!o.days)return;var before=localStorage.getItem('bs_plan_days'),now=JSON.stringify({days:o.days,at:o.at||0});
      if(before===now)return;try{localStorage.setItem('bs_plan_days',now);}catch(e){}
      try{if(typeof renderJournalSchedule==='function')renderJournalSchedule();}catch(e2){}
    }).catch(function(){});
  }
  // Until the phone shares its plan days, take today's workout from the day's summary ("Planned: …").
  function fromSummary(k,text){
    var m=String(text||'').match(/^Planned:\s*(.+)$/m);if(!m)return;
    var bs=null;try{bs=JSON.parse(localStorage.getItem('bs_plan_days')||'null');}catch(e){}
    if(bs&&bs.at)return;   // the full plan feed is there
    bs=bs||{days:{},at:0};if(bs.days[k]===m[1])return;bs.days[k]=m[1];
    try{localStorage.setItem('bs_plan_days',JSON.stringify(bs));}catch(e2){}
    try{if(typeof renderJournalSchedule==='function')renderJournalSchedule();}catch(e3){}
  }
  var fetchedAt=0;
  function refresh(force){
    if(typeof dk!=='function'||typeof today==='undefined')return;
    // Also look again every 5 minutes while the page stays open (sleep can arrive later in the morning).
    var k=dk(today);if(!force&&k===shownFor&&Date.now()-fetchedAt<5*60000)return;shownFor=k;fetchedAt=Date.now();
    if(!cfg())return;
    fetchPlan();fetchActs();
    window.bsReviewFetch(k,k,function(text){render(text);fromSummary(k,text);if(lastDay&&lastDay.key===k)fillWellness(k,lastDay.data);});
  }
  setInterval(function(){refresh(false);},3000);
  document.addEventListener('visibilitychange',function(){if(!document.hidden)refresh(true);});
  setTimeout(function(){refresh(true);},1500);
})();
