function jaOpenDB(){
  return new Promise(function(resolve,reject){
    var req=indexedDB.open('JournalArchiveDB',1);
    req.onupgradeneeded=function(e){
      var db=e.target.result;
      if(!db.objectStoreNames.contains('journals')){
        db.createObjectStore('journals',{keyPath:'id',autoIncrement:true});
      }
    };
    req.onsuccess=function(e){resolve(e.target.result);};
    req.onerror=function(e){reject(e.target.error);};
  });
}

var JA_TEXT_LIMIT=5*1024*1024; // index first 5MB of text for search

function jaExtractText(file){
  return new Promise(function(resolve){
    var name=file.name.toLowerCase();
    // For plain text types, read a slice (up to 5MB) for search indexing
    var readSliceAsText=function(){
      var slice=file.size>JA_TEXT_LIMIT?file.slice(0,JA_TEXT_LIMIT):file;
      var r=new FileReader();
      r.onload=function(e){resolve((e.target.result||'')+(file.size>JA_TEXT_LIMIT?' [search index limited to first 5MB]':''));};
      r.onerror=function(){resolve('');};
      r.readAsText(slice);
    };
    if(name.endsWith('.txt')||name.endsWith('.md')||name.endsWith('.html')||name.endsWith('.htm')){
      readSliceAsText();
    } else if(name.endsWith('.docx')){
      // Limit DOCX to 50MB for mammoth extraction; larger files skip text extraction
      if(file.size>50*1024*1024){resolve('[File too large for automatic text extraction — file stored and downloadable]');return;}
      var doExtract=function(){
        var r=new FileReader();
        r.onload=function(e){
          if(window.mammoth){
            mammoth.extractRawText({arrayBuffer:e.target.result})
              .then(function(res){var t=res.value||'';resolve(t.slice(0,JA_TEXT_LIMIT)+(t.length>JA_TEXT_LIMIT?' [truncated for search]':''));})
              .catch(function(){resolve('');});
          } else {resolve('');}
        };
        r.onerror=function(){resolve('');};
        r.readAsArrayBuffer(file);
      };
      if(window.mammoth){doExtract();}
      else{
        var s=document.createElement('script');
        s.src='https://cdnjs.cloudflare.com/ajax/libs/mammoth/1.6.0/mammoth.browser.min.js';
        s.onload=doExtract;s.onerror=function(){resolve('');};
        document.head.appendChild(s);
      }
    } else if(name.endsWith('.pdf')){
      // Only attempt text extraction on PDFs under 20MB to avoid timeout
      if(file.size>20*1024*1024){resolve('[Large PDF stored — text search not available for scanned/image PDFs]');return;}
      var slice2=file.slice(0,Math.min(file.size,JA_TEXT_LIMIT));
      var r2=new FileReader();
      r2.onload=function(e){
        try{
          var bytes=new Uint8Array(e.target.result);
          var raw='';for(var i=0;i<bytes.length;i++)raw+=String.fromCharCode(bytes[i]);
          var text='';
          var blocks=raw.match(/BT[\s\S]*?ET/g)||[];
          blocks.forEach(function(b){
            var parts=b.match(/\(([^)]*)\)\s*T[jJ]/g)||[];
            parts.forEach(function(p){var m=p.match(/\(([^)]*)\)/);if(m)text+=m[1].replace(/\\n/g,' ').replace(/\\\(/g,'(').replace(/\\\)/g,')')+' ';});
          });
          var tjBlocks=raw.match(/\[([^\]]*)\]\s*TJ/g)||[];
          tjBlocks.forEach(function(b){var ps=b.match(/\(([^)]*)\)/g)||[];ps.forEach(function(p){text+=p.slice(1,-1)+' ';});});
          resolve(text.trim()||'[PDF stored — text extraction limited; scanned PDFs are not searchable]');
        }catch(e2){resolve('[PDF stored]');}
      };
      r2.onerror=function(){resolve('');};
      r2.readAsArrayBuffer(slice2);
    } else {
      readSliceAsText();
    }
  });
}

function jaUpload(event){
  var files=Array.from(event.target.files||[]);
  if(!files.length)return;
  var status=document.getElementById('jaUploadStatus');
  status.style.display='inline';
  event.target.value='';

  // Process files one at a time to avoid memory issues with large files
  var processed=0;
  function processNext(){
    if(processed>=files.length){
      status.textContent='✓ '+files.length+' file'+(files.length>1?'s':'')+' uploaded! Scroll down to see your files.';
      setTimeout(function(){status.style.display='none';},4000);
      jaRenderList();
      // Scroll the file list into view after a short delay
      setTimeout(function(){
        var el=document.getElementById('jaFileList');
        if(el)el.scrollIntoView({behavior:'smooth',block:'start'});
      },400);
      return;
    }
    var file=files[processed];
    var mb=(file.size/1024/1024).toFixed(1);
    status.textContent='Processing '+(processed+1)+' of '+files.length+': '+file.name+' ('+mb+' MB)...';
    jaExtractText(file).then(function(text){
      jaOpenDB().then(function(db){
        // Store the raw File/Blob directly — no base64 conversion, works for any size
        var record={name:file.name,type:file.type||'application/octet-stream',text:text,blob:file,uploadDate:new Date().toISOString(),size:file.size};
        var tx=db.transaction('journals','readwrite');
        var req=tx.objectStore('journals').add(record);
        req.onsuccess=function(){processed++;processNext();};
        req.onerror=function(e){console.error('Store error',e);status.textContent='Error saving '+file.name;};
      });
    }).catch(function(err){
      console.error(err);
      status.textContent='Error processing '+file.name;
    });
  }
  processNext();
}

function jaDownload(id){
  jaOpenDB().then(function(db){
    var tx=db.transaction('journals','readonly');
    var req=tx.objectStore('journals').get(id);
    req.onsuccess=function(e){
      var r=e.target.result;if(!r)return;
      var blob=r.blob||(r.data?dataURLtoBlob(r.data):null);
      if(!blob){alert('File data not found.');return;}
      var url=URL.createObjectURL(blob instanceof Blob?blob:new Blob([blob],{type:r.type}));
      var a=document.createElement('a');a.href=url;a.download=r.name;a.click();
      setTimeout(function(){URL.revokeObjectURL(url);},5000);
    };
  });
}
function dataURLtoBlob(dataURL){
  var arr=dataURL.split(','),mime=arr[0].match(/:(.*?);/)[1],b=atob(arr[1]),n=b.length,u=new Uint8Array(n);
  for(var i=0;i<n;i++)u[i]=b.charCodeAt(i);
  return new Blob([u],{type:mime});
}

function jaRenderList(){
  jaOpenDB().then(function(db){
    var tx=db.transaction('journals','readonly');
    var req=tx.objectStore('journals').getAll();
    req.onsuccess=function(e){
      var records=e.target.result||[];
      var listDiv=document.getElementById('jaFileList');
      var inner=document.getElementById('jaFileListInner');
      var empty=document.getElementById('jaEmpty');
      if(!records.length){if(listDiv)listDiv.style.display='none';if(empty)empty.style.display='block';return;}
      if(empty)empty.style.display='none';
      if(listDiv)listDiv.style.display='block';
      inner.innerHTML=records.map(function(r){
        var dt=new Date(r.uploadDate).toLocaleDateString();
        var mb=(r.size/1024/1024);var sizeStr=mb>=1?mb.toFixed(1)+' MB':Math.round(r.size/1024)+' KB';
        var icon=r.name.endsWith('.pdf')?'📄':r.name.endsWith('.docx')?'📝':r.name.endsWith('.md')?'📋':'📃';
        var words=r.text&&!r.text.startsWith('[')?r.text.split(/\s+/).filter(Boolean).length:0;
        var note=(r.note||'').replace(/"/g,'&quot;');
        return '<div style="padding:8px 0;border-bottom:1px solid #ccfbf1;">'
          +'<div style="display:flex;align-items:center;gap:8px;">'
          +'<span style="font-size:1.1rem;">'+icon+'</span>'
          +'<div style="flex:1;min-width:0;">'
          +'<div style="font-size:0.85rem;font-weight:600;color:#134e4a;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">'+r.name+'</div>'
          +'<div style="font-size:0.72rem;color:#94a3b8;">'+dt+' · '+(words?words.toLocaleString()+' words indexed':'not indexed')+' · '+sizeStr+'</div>'
          +'</div>'
          +'<button onclick="jaDownload('+r.id+')" style="background:#e0f2fe;color:#0369a1;border:none;border-radius:5px;padding:4px 10px;font-size:0.75rem;cursor:pointer;white-space:nowrap;flex-shrink:0;">&#x2B07; Save</button>'
          +'<button onclick="jaDelete('+r.id+')" style="background:#fee2e2;color:#b91c1c;border:none;border-radius:5px;padding:4px 8px;font-size:0.75rem;cursor:pointer;flex-shrink:0;">&#x1F5D1;</button>'
          +'</div>'
          +'<textarea id="janote_'+r.id+'" rows="2" placeholder="Add a note about this journal..." onblur="jaSaveNote('+r.id+',this.value)" style="width:100%;box-sizing:border-box;margin-top:6px;border:1px solid #99f6e4;border-radius:6px;padding:6px 8px;font-size:0.82rem;font-family:inherit;resize:vertical;outline:none;background:#fff;color:#134e4a;">'+note+'</textarea>'
          +'</div>';
      }).join('');
    };
  });
}

function jaSaveNote(id,value){
  jaOpenDB().then(function(db){
    var tx=db.transaction('journals','readwrite');
    var store=tx.objectStore('journals');
    var req=store.get(id);
    req.onsuccess=function(e){
      var r=e.target.result;if(!r)return;
      r.note=value;
      store.put(r);
    };
  });
}

function jaDelete(id){
  if(!confirm('Delete this journal file?'))return;
  jaOpenDB().then(function(db){
    var tx=db.transaction('journals','readwrite');
    tx.objectStore('journals').delete(id);
    tx.oncomplete=function(){jaRenderList();var sr=document.getElementById('jaSearchResults');if(sr)sr.style.display='none';};
  });
}

function jaSearch(){
  var query=(document.getElementById('jaSearchInput').value||'').trim().toLowerCase();
  var resultsDiv=document.getElementById('jaSearchResults');
  if(!query){resultsDiv.style.display='none';return;}
  jaOpenDB().then(function(db){
    var tx=db.transaction('journals','readonly');
    var req=tx.objectStore('journals').getAll();
    req.onsuccess=function(e){
      var records=e.target.result||[];
      var hits=[];
      records.forEach(function(r){
        if(!r.text)return;
        var text=r.text;
        var lower=text.toLowerCase();
        var idx=lower.indexOf(query);
        if(idx===-1)return;
        // Count occurrences
        var count=0,pos=0;
        while((pos=lower.indexOf(query,pos))!==-1){count++;pos+=query.length;}
        // Build excerpts (up to 3)
        var excerpts=[];
        var start=0;
        var found=lower.indexOf(query,start);
        while(found!==-1&&excerpts.length<3){
          var from=Math.max(0,found-80);
          var to=Math.min(text.length,found+query.length+80);
          var snippet=text.slice(from,to).replace(/\n/g,' ');
          // Highlight the match
          var hiLow=snippet.toLowerCase();
          var qIdx=hiLow.indexOf(query);
          if(qIdx!==-1){
            snippet=(from>0?'…':'')+snippet.slice(0,qIdx)+'<mark style="background:#fef08a;border-radius:2px;">'+snippet.slice(qIdx,qIdx+query.length)+'</mark>'+snippet.slice(qIdx+query.length)+(to<text.length?'…':'');
          }
          excerpts.push(snippet);
          start=found+query.length;
          found=lower.indexOf(query,start+100);
        }
        hits.push({name:r.name,count:count,excerpts:excerpts});
      });
      if(!hits.length){
        resultsDiv.style.display='block';
        resultsDiv.innerHTML='<div style="font-size:0.85rem;color:#94a3b8;text-align:center;padding:10px;">No results found for "'+query+'"</div>';
        return;
      }
      resultsDiv.style.display='block';
      resultsDiv.innerHTML='<div style="font-size:0.75rem;font-weight:700;color:#0f766e;margin-bottom:8px;text-transform:uppercase;letter-spacing:.04em;">Found in '+hits.length+' journal'+(hits.length>1?'s':'')+' — '+hits.reduce(function(a,h){return a+h.count;},0)+' matches</div>'
        +hits.map(function(h){
          return '<div style="background:#fff;border:1px solid #99f6e4;border-radius:8px;padding:10px 12px;margin-bottom:8px;">'
            +'<div style="font-size:0.85rem;font-weight:700;color:#134e4a;margin-bottom:6px;">&#x1F4C4; '+h.name+' <span style="font-weight:400;color:#64748b;font-size:0.75rem;">('+h.count+' match'+(h.count>1?'es':'')+')</span></div>'
            +h.excerpts.map(function(ex){return '<div style="font-size:0.82rem;color:#334155;line-height:1.55;margin-bottom:4px;padding:4px 8px;background:#f0fdfa;border-radius:4px;">'+ex+'</div>';}).join('')
            +'</div>';
        }).join('');
    };
  });
}

window.addEventListener('load',function(){jaRenderList();});
window.addEventListener('load',function(){loadAllTabNotes();});
window.addEventListener('load',function(){
  var bibleTab=document.getElementById('tab-bible');if(!bibleTab)return;
  var autoSaveIds=['bSermonTitle','bSermonSpeaker','bSermonURL','bSermonNotes','bScripture','bKeyVerse','bTheme'];
  bibleTab.addEventListener('input',function(event){if(autoSaveIds.indexOf(event.target.id)!==-1)queueBibleAutoSave();});
  bibleTab.addEventListener('change',function(event){if(autoSaveIds.indexOf(event.target.id)!==-1)queueBibleAutoSave();});
});
window.addEventListener('load',function(){
  migrateLegacySpiritStreaks(); // restore Bible/Prayer streak history from pre-rename field names
  load(); // Always initialize daily fields from localStorage
  var lastTab=localStorage.getItem('last_active_tab');
  if(lastTab==='habits')lastTab='habits2';
  if(lastTab==='dashboard')lastTab='weekly';
  var validTabs=['daily','weekly','monthly','yearly','review','habits2','meditate','bible','library','wellness','file-uploads'];
  if(lastTab&&lastTab!=='daily'&&validTabs.indexOf(lastTab)!==-1){
    if(_hmTabs.includes(lastTab)){hmSwitch(lastTab,null);}
    else{var btn=document.querySelector('[onclick*="switchTab(\''+lastTab+'\'"]');if(btn)btn.click();}
  }
});
/* ========= END PERSONAL JOURNAL ARCHIVE ========= */

/* ========= OTHER FILE UPLOADS ========= */
