/* 🔄 Command Center sync (Nov 2026) — keeps Chrome, the computer and BeyondStride's Life tab matched.
 * Uses the same private Firebase spot BeyondStride already uses (the link in "beyondstride_sync"), under nutrition/<key>/ccsync (next to the plans the Command Center already shares there).
 * Each saved item (a day, a review, settings, activities, the Bible reading archive…) is synced on its own:
 *  - a change uploads ~2 s later; other devices pick it up within ~30 s, or right away when the page is opened/shown;
 *  - if the same item changed on two devices, the newer change wins (only that item).
 * The first copy uploaded must be a full one (20+ days) so a new, nearly-empty copy (like a fresh Life tab) can
 * never overwrite your real data: a new device always takes the cloud's version first.
 * Book covers and book documents sync too (Nov 20 2026), one item each (__cover_<book>, __doc_<id>); a document over 3 MB stays on its device.
 * Not synced: other Master App tools' data, AI keys, sign-ins.
 * Nov 21 2026: off inside BeyondStride (the Life tab is disconnected from the website). Chrome <-> computer still syncs.
 * Nothing is removed. */
(function(){
  // Nov 21 2026: BeyondStride's Life tab is disconnected from this website. Inside the app, sync stays off:
  // the Life tab is its own copy, and the website keeps what it had. Chrome and the computer still sync with each other.
  if(window.BSNative){window.ccSync={now:function(){},pending:function(){return 0;},linked:function(){return false;},off:true};return;}
  var META='ccsync_meta', DEV='ccsync_dev', IDB=['activities_data','bible_reading_archive'], SEED_DAYS=20;
  var joinMode='', dirty={}, applying=false, busy=false, timer=null, lastPull=0, started=false;
  var setItem=Storage.prototype.setItem, removeItem=Storage.prototype.removeItem;

  function cfg(){try{return JSON.parse(localStorage.getItem('beyondstride_sync')||'null');}catch(e){return null;}}
  function base(){var c=cfg();return c&&c.db&&c.key?c.db.replace(/\/$/,'')+'/nutrition/'+encodeURIComponent(c.key)+'/ccsync':'';}
  function enc(k){return encodeURIComponent(k).replace(/\./g,'%2E').replace(/\*/g,'%2A').replace(/'/g,'%27').replace(/\(/g,'%28').replace(/\)/g,'%29').replace(/!/g,'%21').replace(/~/g,'%7E');}
  function dec(e){try{return decodeURIComponent(e);}catch(x){return e;}}
  function url(path){return base()+path+'.json';}
  function hash(s){s=String(s);var h=5381;for(var i=0;i<s.length;i++)h=((h<<5)+h+s.charCodeAt(i))|0;return s.length+':'+h;}
  function meta(){try{return JSON.parse(localStorage.getItem(META)||'{}')||{};}catch(e){return {};}}
  function saveMeta(m){try{setItem.call(localStorage,META,JSON.stringify(m));}catch(e){}}
  function syncable(k){
    if(!k||k===META||k===DEV||k.indexOf('ccsync_')===0||k==='beyondstride_sync'||k==='journal_cloud_dirty'||k==='last_backup_ts')return false;
    if(k.indexOf('__idb_')===0)return IDB.indexOf(k.slice(6))>=0;
    if(k.indexOf('__cover_')===0||k.indexOf('__doc_')===0)return true;
    if(typeof window.journalCloudCanSyncKey==='function')return journalCloudCanSyncKey(k);
    var l=k.toLowerCase();return !(l==='ai_key'||l.indexOf('sb-')===0||l.indexOf('password')>=0);
  }
  function status(t,s){try{if(typeof journalCloudSetStatus==='function')journalCloudSetStatus(t,s);}catch(e){}}
  function soon(ms){clearTimeout(timer);timer=setTimeout(run,ms==null?2000:ms);}

  // Notice every change the page makes (only real changes count).
  Storage.prototype.setItem=function(k,v){
    if(this===window.localStorage&&!applying&&syncable(k)){var old=this.getItem(k);setItem.call(this,k,v);if(old!==String(v)){dirty[k]=Date.now();soon();}return;}
    return setItem.call(this,k,v);
  };
  Storage.prototype.removeItem=function(k){
    if(this===window.localStorage&&!applying&&syncable(k)&&this.getItem(k)!==null){removeItem.call(this,k);dirty[k]=Date.now();soon();return;}
    return removeItem.call(this,k);
  };
  function hookLarge(){
    if(typeof window.journalLargeStoreSet!=='function'||window.journalLargeStoreSet.__sync)return;
    var orig=window.journalLargeStoreSet;
    var w=function(key,value){var p=orig.apply(this,arguments);if(!applying&&IDB.indexOf(key)>=0){dirty['__idb_'+key]=Date.now();soon();}return p;};
    w.__sync=true;window.journalLargeStoreSet=w;
  }

  // ── 📚 Book covers and documents (picture stores): one synced item per cover / per document ──
  var DOC_MAX=3e6;
  function docUid(d){return d.uid||('d'+hash(String(d.bookId)+'|'+(d.name||'')+'|'+(d.size||0)+'|'+(d.addedAt||'')).replace(/[^0-9a-z]/gi,''));}
  function bookIdOf(s){return /^\d+$/.test(s)?Number(s):s;}
  function allCovers(){return typeof getAllBookCovers==='function'?getAllBookCovers().catch(function(){return [];}):Promise.resolve([]);}
  function allDocs(){return typeof getAllBookDocuments==='function'?getAllBookDocuments().catch(function(){return [];}):Promise.resolve([]);}
  function hookPictures(){
    if(window.__ccPicHooked||typeof setBookCover!=='function')return;window.__ccPicHooked=true;
    var sc=window.setBookCover,dc=window.deleteBookCover,pd=window.putBookDocument,ad=window.addBookDocument,dd=window.deleteBookDocument;
    window.setBookCover=function(id,u){var p=sc.apply(this,arguments);if(!applying){dirty['__cover_'+id]=Date.now();soon();}return p;};
    window.deleteBookCover=function(id){var p=dc.apply(this,arguments);if(!applying){dirty['__cover_'+id]=Date.now();soon();}return p;};
    window.putBookDocument=function(rec){if(rec&&!rec.uid)rec.uid='d'+Date.now()+Math.floor(Math.random()*1e4);var p=pd.apply(this,arguments);
      if(!applying&&rec&&String(rec.dataUrl||'').length<=DOC_MAX){dirty['__doc_'+rec.uid]=Date.now();soon();}return p;};
    if(ad)window.addBookDocument=function(bookId,file){var p=ad.apply(this,arguments);return p.then(function(rec){if(!applying&&rec){var u=docUid(rec);if(String(rec.dataUrl||'').length<=DOC_MAX){dirty['__doc_'+u]=Date.now();soon();}}return rec;});};
    window.deleteBookDocument=function(docId,bookId){var self=this,args=arguments;
      return allDocs().then(function(all){var d=all.find(function(x){return x.id===docId;});if(d&&!applying){dirty['__doc_'+docUid(d)]=Date.now();soon();}return dd.apply(self,args);});};
  }
  async function localValue(k){
    if(k.indexOf('__idb_')===0){var v=null;try{v=await window.journalLargeStoreGet(k.slice(6));}catch(e){}return v==null?null:JSON.stringify(v);}
    if(k.indexOf('__cover_')===0){var id=k.slice(8),c=(await allCovers()).find(function(x){return String(x.bookId)===id;});return c&&c.dataUrl?c.dataUrl:null;}
    if(k.indexOf('__doc_')===0){var u=k.slice(6),d=(await allDocs()).find(function(x){return docUid(x)===u;});if(!d)return null;
      return JSON.stringify({bookId:d.bookId,name:d.name,type:d.type,size:d.size,addedAt:d.addedAt,dataUrl:d.dataUrl,uid:u});}
    return localStorage.getItem(k);
  }
  async function applyValue(k,v){
    applying=true;
    try{
      if(k.indexOf('__idb_')===0){
        var name=k.slice(6),val=v==null?[]:JSON.parse(v);
        if(name==='activities_data')window._activitiesLargeCache=val;
        if(name==='bible_reading_archive')window._bibleReadingArchiveLargeCache=val;
        try{if(typeof _activitiesLargeCache!=='undefined'&&name==='activities_data')_activitiesLargeCache=val;}catch(e){}
        try{if(typeof _bibleReadingArchiveLargeCache!=='undefined'&&name==='bible_reading_archive')_bibleReadingArchiveLargeCache=val;}catch(e){}
        var orig=window.journalLargeStoreSet;await orig(name,val);
      }else if(k.indexOf('__cover_')===0){var bid=bookIdOf(k.slice(8));
        if(v==null)await deleteBookCover(bid);else await setBookCover(bid,v);
        try{if(window._bsCov)delete _bsCov[String(bid)];}catch(e){}
      }else if(k.indexOf('__doc_')===0){var uid=k.slice(6),all=await allDocs(),ex=all.find(function(x){return docUid(x)===uid;});
        if(v==null){if(ex){var db=await openBookDocsDB();await new Promise(function(r){var tx=db.transaction('docs','readwrite');tx.objectStore('docs').delete(ex.id);tx.oncomplete=r;tx.onerror=r;});}}
        else{var rec=JSON.parse(v);rec.uid=uid;rec.bookId=bookIdOf(String(rec.bookId));if(ex)rec.id=ex.id;await putBookDocument(rec);}
        try{if(window._bsDocs)delete _bsDocs[String(JSON.parse(v||'{}').bookId||'')];}catch(e){}
      }else if(v==null)removeItem.call(localStorage,k);
      else setItem.call(localStorage,k,v);
    }finally{applying=false;}
  }

  async function backup(){
    try{var d={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(syncable(k))d[k]=localStorage.getItem(k);}
      if(typeof window.journalLargeStoreSet==='function')await window.journalLargeStoreSet('ccsync_backup_'+new Date().toISOString().slice(0,10),d);}catch(e){}
  }
  function askJoin(n){
    var text='This device has its own Command Center data, and '+n+' saved item'+(n>1?'s are':' is')+' different from the synced copy. '+
      'A backup of this device was saved first. Which version should win where they differ? (Items only on one side are kept either way.)';
    var ov=document.getElementById('ccSyncJoin');if(ov)ov.remove();
    ov=document.createElement('div');ov.id='ccSyncJoin';
    ov.style.cssText='position:fixed;inset:0;background:rgba(15,23,42,.55);z-index:2147483000;display:flex;align-items:center;justify-content:center;padding:16px;';
    var box=document.createElement('div');box.style.cssText='background:#fff;border-radius:16px;max-width:440px;width:100%;padding:18px;font-family:inherit;box-shadow:0 10px 30px rgba(0,0,0,.25);';
    var h=document.createElement('div');h.textContent='🔄 Turn on Command Center sync';h.style.cssText='font-weight:800;font-size:1.05rem;color:#1f2937;margin-bottom:8px;';
    var p=document.createElement('div');p.textContent=text;p.style.cssText='font-size:.9rem;color:#475467;line-height:1.45;margin-bottom:14px;';
    function btn(label,primary,mode){var b=document.createElement('button');b.type='button';b.textContent=label;
      b.style.cssText='display:block;width:100%;margin-top:8px;padding:11px;border-radius:10px;font-weight:700;font-size:.9rem;cursor:pointer;'+(primary?'background:#4f46e5;color:#fff;border:none;':'background:#fff;color:#4f46e5;border:1px solid #c7d2fe;');
      b.onclick=function(){ov.remove();joinMode=mode;soon(0);};return b;}
    box.appendChild(h);box.appendChild(p);box.appendChild(btn('Use the synced copy (from your other device)',true,'cloud'));box.appendChild(btn('Keep this device\'s versions',false,'mine'));
    ov.appendChild(box);document.body.appendChild(ov);
  }
  async function get(path){var r=await fetch(url(path),{cache:'no-store'});if(!r.ok)throw new Error('sync '+r.status);return r.json();}
  async function patch(path,obj,keep){var r=await fetch(url(path),{method:'PATCH',body:JSON.stringify(obj),keepalive:!!keep});if(!r.ok)throw new Error('sync '+r.status);}

  // Keys whose saved value differs from what was last synced (changes made while sync wasn't running).
  async function scan(m){
    for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(!syncable(k)||dirty[k])continue;
      var e=enc(k),h=hash(localStorage.getItem(k));if(!m[e]||m[e][1]!==h)dirty[k]=dirty[k]||1;}
    for(var j=0;j<IDB.length;j++){var key='__idb_'+IDB[j],v=await localValue(key);if(v==null)continue;var e2=enc(key);if(!m[e2]||m[e2][1]!==hash(v))dirty[key]=dirty[key]||1;}
    try{(await allCovers()).forEach(function(c){if(!c||!c.dataUrl)return;var key='__cover_'+c.bookId,e3=enc(key);if(!m[e3]||m[e3][1]!==hash(c.dataUrl))dirty[key]=dirty[key]||1;});
      (await allDocs()).forEach(function(d){if(!d||String(d.dataUrl||'').length>DOC_MAX)return;var u=docUid(d),key='__doc_'+u,v=JSON.stringify({bookId:d.bookId,name:d.name,type:d.type,size:d.size,addedAt:d.addedAt,dataUrl:d.dataUrl,uid:u}),e4=enc(key);
        if(!m[e4]||m[e4][1]!==hash(v))dirty[key]=dirty[key]||1;});}catch(e){}
  }

  // Covers / documents already on this device but never sent (e.g. added before this sync knew about them).
  async function scanPictures(m){try{(await allCovers()).forEach(function(c){if(!c||!c.dataUrl)return;var key='__cover_'+c.bookId,e=enc(key);if(!m[e]||m[e][1]!==hash(c.dataUrl))dirty[key]=dirty[key]||1;});
    (await allDocs()).forEach(function(d){if(!d||String(d.dataUrl||'').length>DOC_MAX)return;var u=docUid(d),key='__doc_'+u,v=JSON.stringify({bookId:d.bookId,name:d.name,type:d.type,size:d.size,addedAt:d.addedAt,dataUrl:d.dataUrl,uid:u}),e=enc(key);
      if(!m[e]||m[e][1]!==hash(v))dirty[key]=dirty[key]||1;});}catch(e){}}
  async function push(m,keep){
    var keys=Object.keys(dirty);if(!keys.length)return 0;
    var vals={},times={},size=0,n=0,now=Date.now();
    async function flush(){if(!n)return;await patch('/v',vals,keep);await patch('/t',times,keep);vals={};times={};size=0;n=0;}
    for(var i=0;i<keys.length;i++){var k=keys[i],v=await localValue(k),e=enc(k);
      vals[e]=v;times[e]=v==null?-now:now;m[e]=[now,v==null?'':hash(v)];size+=(v||'').length;n++;delete dirty[k];
      if(size>3e6)await flush();}
    await flush();saveMeta(m);return keys.length;
  }

  async function run(){
    if(busy||!base()){return;}busy=true;
    try{
      hookLarge();hookPictures();
      if(!window.__ccPicScanned&&Object.keys(meta()).length){window.__ccPicScanned=true;await scanPictures(meta());}
      var m=meta(),first=!Object.keys(m).length,remote=await get('/t')||{},changed=[];
      if(first)await scan(m);
      if(!Object.keys(remote).length){
        // Nothing in the cloud yet: only a full copy may start it.
        var days=0;for(var i=0;i<localStorage.length;i++)if(/^planner_\d{4}-\d\d-\d\d$/.test(localStorage.key(i)||''))days++;
        if(days<SEED_DAYS){status('Sync waiting for your main Command Center copy','busy');return;}
        await scan(m);var n=await push(m);status('Synced ('+n+' items uploaded)','ok');return;
      }
      if(first){
        // A device joining: compare with the cloud (one download). A nearly-empty copy (e.g. a new Life tab) simply
        // takes the cloud's version; a full copy asks which version to keep where they differ. Either way a backup
        // of this device's data is kept first (large storage: ccsync_backup_<date>).
        var all=await get('/v')||{},diffs=[];
        for(var e in remote){var t=remote[e],k=dec(e);if(!syncable(k))continue;
          var v=t<0?null:(e in all?all[e]:null),lv=await localValue(k);
          if(lv!==v)diffs.push([k,e,t,v]);else{m[e]=[Math.abs(t),v==null?'':hash(v)];delete dirty[k];}}
        var localDays=0;for(var d=0;d<localStorage.length;d++)if(/^planner_\d{4}-\d\d-\d\d$/.test(localStorage.key(d)||''))localDays++;
        if(diffs.length)await backup();
        if(diffs.length&&localDays>=SEED_DAYS&&!joinMode){
          busy=false;askJoin(diffs.length);return;
        }
        for(var x=0;x<diffs.length;x++){var q=diffs[x];
          if(joinMode==='mine'){dirty[q[0]]=Date.now();continue;}
          await applyValue(q[0],q[3]);m[q[1]]=[Math.abs(q[2]),q[3]==null?'':hash(q[3])];delete dirty[q[0]];changed.push(q[0]);}
        saveMeta(m);
      }else{
        for(var e3 in remote){var t3=remote[e3],k3=dec(e3);if(!syncable(k3))continue;
          var known=(m[e3]||[0])[0];if(Math.abs(t3)<=known)continue;
          if(dirty[k3]&&dirty[k3]>Math.abs(t3))continue;          // this device's newer change wins; it uploads below
          var v3=t3<0?null:await get('/v/'+encodeURIComponent(e3));
          await applyValue(k3,v3);m[e3]=[Math.abs(t3),v3==null?'':hash(v3)];delete dirty[k3];changed.push(k3);}
        saveMeta(m);
      }
      var up=await push(m);lastPull=Date.now();
      status(changed.length?'Synced — '+changed.length+' update'+(changed.length>1?'s':'')+' from your other devices':up?'Synced':'Synced just now','ok');
      if(changed.length)refresh(changed);
    }catch(err){status(navigator.onLine?'Sync will retry':'Offline — saved on this device','error');}
    finally{busy=false;}
  }

  // Show what came in. Waits while you're typing so nothing jumps under your fingers.
  var pendingRefresh=false;
  function refresh(keys){
    var a=document.activeElement;
    if(a&&(a.tagName==='INPUT'||a.tagName==='TEXTAREA'||a.isContentEditable)){pendingRefresh=true;return;}
    pendingRefresh=false;
    try{if(typeof load==='function')load();}catch(e){}
    try{if(typeof syncPeriodsToDay==='function')syncPeriodsToDay();}catch(e){}
    try{if(window.BSNative&&BSNative.changed)BSNative.changed();}catch(e){}
  }
  document.addEventListener('focusout',function(){if(pendingRefresh)setTimeout(function(){refresh([]);},300);});

  function start(){
    if(started)return;started=true;hookLarge();hookPictures();
    soon(1500);
    setInterval(function(){if(document.visibilityState==='visible'&&Date.now()-lastPull>25000)run();},5000);
    document.addEventListener('visibilitychange',function(){
      if(document.visibilityState==='visible')soon(200);
      else if(Object.keys(dirty).length&&!busy){busy=true;push(meta(),true).catch(function(){}).finally(function(){busy=false;});}
    });
    window.addEventListener('focus',function(){soon(200);});
  }
  if(document.readyState==='complete')start();else window.addEventListener('load',start);
  window.ccSync={now:function(){soon(0);},pending:function(){return Object.keys(dirty).length;},linked:function(){return !!base();}};
})();
