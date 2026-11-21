/* Picture and file stores (IndexedDB) — opened the same safe way everywhere (Nov 20 2026).
 * The suite's daily Full Backup reads these databases by name. On a device that had never used one yet (BeyondStride's
 * Life tab), that read created the database with a plain store missing its key setup, so every later save there
 * (book covers, book documents, Bible images, uploads, journal archive) quietly failed. ccIdbOpen checks the store and,
 * if it isn't set up right, rebuilds it in place, keeping anything already in it. Nothing is removed. */
(function(){
  var SPECS={BookCoversDB:['covers',{keyPath:'bookId'}],BookDocumentsDB:['docs',{keyPath:'id',autoIncrement:true}],
    BibleImagesDB:['images',{keyPath:'id',autoIncrement:true}],OtherUploadsDB:['files',{keyPath:'id',autoIncrement:true}],
    JournalArchiveDB:['journals',{keyPath:'id',autoIncrement:true}]};
  var cache={};
  function good(db,store,opts){
    if(!db.objectStoreNames.contains(store))return false;
    try{var s=db.transaction(store,'readonly').objectStore(store);return (s.keyPath||null)===(opts.keyPath||null)&&!!s.autoIncrement===!!opts.autoIncrement;}catch(e){return false;}
  }
  function open(name,store,opts){
    return new Promise(function(resolve,reject){
      var r=indexedDB.open(name);
      r.onupgradeneeded=function(e){var db=e.target.result;if(!db.objectStoreNames.contains(store))db.createObjectStore(store,opts);};
      r.onerror=function(){reject(r.error);};
      r.onsuccess=function(e){
        var db=e.target.result;db.onversionchange=function(){try{db.close();}catch(x){}delete cache[name];};
        if(good(db,store,opts)){resolve(db);return;}
        // Rebuild the store with the right key setup, keeping what's there.
        var keep=[];
        function rebuild(){
          var v=db.version;try{db.close();}catch(x){}
          var r2=indexedDB.open(name,v+1);
          r2.onupgradeneeded=function(ev){var d=ev.target.result;if(d.objectStoreNames.contains(store))d.deleteObjectStore(store);var st=d.createObjectStore(store,opts);
            keep.forEach(function(x){try{var val=x.v;if(val&&typeof val==='object'){if(opts.autoIncrement&&val[opts.keyPath]==null)delete val[opts.keyPath];if(val[opts.keyPath]!=null||opts.autoIncrement)st.put(val);}}catch(er){}});};
          r2.onsuccess=function(ev){var d=ev.target.result;d.onversionchange=function(){try{d.close();}catch(x){}delete cache[name];};resolve(d);};
          r2.onerror=function(){reject(r2.error);};
        }
        if(db.objectStoreNames.contains(store)){
          try{var c=db.transaction(store,'readonly').objectStore(store).openCursor();
            c.onsuccess=function(){var cur=c.result;if(cur){keep.push({k:cur.key,v:cur.value});cur.continue();}else rebuild();};c.onerror=rebuild;}catch(x){rebuild();}
        }else rebuild();
      };
    });
  }
  window.ccIdbOpen=function(name){
    var sp=SPECS[name];if(!sp)return Promise.reject(new Error('unknown store '+name));
    if(!cache[name])cache[name]=open(name,sp[0],sp[1]).catch(function(e){delete cache[name];throw e;});
    return cache[name];
  };
  // Check them all right away, before the daily backup (3 s after load) holds them open.
  try{Object.keys(SPECS).forEach(function(n){window.ccIdbOpen(n).catch(function(){});});}catch(e){}
})();
