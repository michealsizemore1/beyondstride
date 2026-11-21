// ---------- Shared "Full Backup" system for the whole My Life Master App suite ----------
// Included by every core page in the suite (this home page, Budget & Net Worth, Retirement & Credit,
// and the Daily Planner) so there is exactly one definition of "what a full backup covers" instead of
// several copies that can drift apart, and so a real backup file gets produced automatically once a
// day no matter which of these pages happens to be opened first that day.
//
// MASTER_BACKUP_IDB_SOURCES is the full list of IndexedDB databases/stores this suite writes to.
// Leaving a new one out here means that app's photos/documents silently never make it into a backup,
// with no warning to anyone -- add a row here whenever a new app-level IndexedDB store is introduced
// anywhere in the suite.
var MASTER_BACKUP_IDB_SOURCES=[
  {db:'my_life_command_center_large_storage',store:'journal',label:'journal entries'},
  {db:'meridian_money_receipts',store:'receipts',label:'receipt photos (Budget & Net Worth)'},
  {db:'BibleImagesDB',store:'images',label:'Bible reading images'},
  {db:'BookCoversDB',store:'covers',label:'book cover images'},
  {db:'BookDocumentsDB',store:'docs',label:'book documents'},
  {db:'OtherUploadsDB',store:'files',label:'other file uploads'},
  {db:'JournalArchiveDB',store:'journals',label:'archived journal entries'},
  {db:'retirementPlannerVaultDB_v1',store:'documents',label:'Document Vault files (Retirement & Credit)'}
];

function masterBackupStatus(message,isError){
  var el=document.getElementById('masterBackupStatus');
  if(!el)return;
  el.textContent=message;
  el.style.color=isError?'#b42318':'#2457bf';
}
function masterBackupIdbOpen(dbName,storeName){
  return new Promise(function(resolve,reject){
    var req=indexedDB.open(dbName);
    req.onupgradeneeded=function(){var db=req.result;if(!db.objectStoreNames.contains(storeName))db.createObjectStore(storeName);};
    req.onsuccess=function(){resolve(req.result);};
    req.onerror=function(){reject(req.error);};
  });
}
function masterBackupIdbGetAll(dbName,storeName){
  return masterBackupIdbOpen(dbName,storeName).then(function(db){
    return new Promise(function(resolve,reject){
      if(!db.objectStoreNames.contains(storeName)){resolve({});return;}
      var tx=db.transaction(storeName,'readonly'),store=tx.objectStore(storeName),out={};
      var req=store.openCursor();
      req.onsuccess=function(){var cursor=req.result;if(cursor){out[cursor.key]=cursor.value;cursor.continue();}else{resolve(out);}};
      req.onerror=function(){reject(req.error);};
    });
  })['catch'](function(){return{};});
}
function masterBackupIdbSetAll(dbName,storeName,obj){
  if(!obj||!Object.keys(obj).length)return Promise.resolve(true);
  return masterBackupIdbOpen(dbName,storeName).then(function(db){
    return new Promise(function(resolve,reject){
      var tx=db.transaction(storeName,'readwrite'),store=tx.objectStore(storeName);
      var inline=!!store.keyPath;
      Object.keys(obj).forEach(function(key){
        if(inline)store.put(obj[key]);
        else store.put(obj[key],key);
      });
      tx.oncomplete=function(){resolve(true);};
      tx.onerror=function(){reject(tx.error);};
    });
  })['catch'](function(){return false;});
}
async function downloadMasterBackup(){
  try{
    var saved={};
    for(var i=0;i<localStorage.length;i++){
      var key=localStorage.key(i);
      saved[key]=localStorage.getItem(key);
    }
    var indexedDBData={},extra=0;
    for(var s=0;s<MASTER_BACKUP_IDB_SOURCES.length;s++){
      var src=MASTER_BACKUP_IDB_SOURCES[s];
      var data=await masterBackupIdbGetAll(src.db,src.store);
      var n=Object.keys(data).length;
      if(n){
        if(!indexedDBData[src.db])indexedDBData[src.db]={};
        indexedDBData[src.db][src.store]=data;
        extra+=n;
      }
    }
    var backup={
      format:'my-life-master-app-backup',
      version:3,
      createdAt:new Date().toISOString(),
      origin:location.origin,
      itemCount:Object.keys(saved).length,
      localStorage:saved,
      indexedDB:indexedDBData
    };
    var stamp=new Date().toISOString().slice(0,10);
    var blob=new Blob([JSON.stringify(backup,null,2)],{type:'application/json'});
    var url=URL.createObjectURL(blob),link=document.createElement('a');
    link.href=url;link.download='my-life-master-app-backup-'+stamp+'.json';
    document.body.appendChild(link);link.click();link.remove();URL.revokeObjectURL(url);
    masterBackupStatus('Full backup downloaded: '+backup.itemCount+' saved items'+(extra?' plus '+extra+' file'+(extra===1?'':'s')+' from photos, documents, and archives across every app.':'.'));
    return true;
  }catch(error){
    masterBackupStatus('The backup could not be created. Please try again.',true);
    return false;
  }
}
function restoreMasterBackup(input){
  var file=input.files&&input.files[0];
  if(!file)return;
  var reader=new FileReader();
  reader.onload=async function(){
    try{
      var backup=JSON.parse(reader.result);
      if(!backup||backup.format!=='my-life-master-app-backup'||!backup.localStorage||typeof backup.localStorage!=='object')throw new Error('Invalid backup');
      var idb=backup.indexedDB||{};
      var idbJobs=[],idbCount=0;
      MASTER_BACKUP_IDB_SOURCES.forEach(function(src){
        var storeData=idb[src.db]&&idb[src.db][src.store];
        if(storeData&&Object.keys(storeData).length){
          idbCount+=Object.keys(storeData).length;
          idbJobs.push({db:src.db,store:src.store,data:storeData});
        }
      });
      var count=Object.keys(backup.localStorage).length+idbCount;
      if(!confirm('Restore '+count+' saved items from this Master App backup?\n\nThis will replace matching information currently saved in this browser.')){input.value='';return;}
      Object.keys(backup.localStorage).forEach(function(key){
        var value=backup.localStorage[key];
        if(typeof value==='string')localStorage.setItem(key,value);
      });
      for(var j=0;j<idbJobs.length;j++){
        await masterBackupIdbSetAll(idbJobs[j].db,idbJobs[j].store,idbJobs[j].data);
      }
      masterBackupStatus('Full backup restored. Open an app or refresh it to see the restored information.');
    }catch(error){
      masterBackupStatus('That file is not a valid My Life Master App backup.',true);
    }
    input.value='';
  };
  reader.onerror=function(){masterBackupStatus('The backup file could not be read.',true);input.value='';};
  reader.readAsText(file);
}

// ---------- Daily automatic backup ----------
// Once per calendar day -- checked on whichever of the core pages happens to be opened first that
// day, since MASTER_AUTO_BACKUP_DATE_KEY is a single shared localStorage flag -- silently save a real
// "Full Backup" JSON file, the same file the manual "Download Full Backup" button produces. This is
// deliberately a real downloaded file rather than an in-browser snapshot: a browser-level data clear
// (clearing cookies/site data, or losing the device) wipes everything inside the browser's own
// storage, so only a copy that already left the browser survives that. The date check is written
// first, before the backup itself runs, so two pages opened moments apart don't both try at once.
var MASTER_AUTO_BACKUP_DATE_KEY='masterAutoBackupLastDate_v1';
function checkAndRunDailyAutoBackup(){
  try{
    var today=new Date().toISOString().slice(0,10);
    if(localStorage.getItem(MASTER_AUTO_BACKUP_DATE_KEY)===today)return;
    localStorage.setItem(MASTER_AUTO_BACKUP_DATE_KEY,today);
    downloadMasterBackup().then(function(ok){
      if(!ok&&localStorage.getItem(MASTER_AUTO_BACKUP_DATE_KEY)===today)localStorage.removeItem(MASTER_AUTO_BACKUP_DATE_KEY);
    }).catch(function(){
      if(localStorage.getItem(MASTER_AUTO_BACKUP_DATE_KEY)===today)localStorage.removeItem(MASTER_AUTO_BACKUP_DATE_KEY);
    });
  }catch(e){}
}
(function(){
  function start(){setTimeout(checkAndRunDailyAutoBackup,3000);}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);
  else start();
})();
