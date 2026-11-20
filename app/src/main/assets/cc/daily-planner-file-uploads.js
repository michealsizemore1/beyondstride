function ouOpenDB(){
  return new Promise(function(resolve,reject){
    var req=indexedDB.open('OtherUploadsDB',1);
    req.onupgradeneeded=function(e){
      var db=e.target.result;
      if(!db.objectStoreNames.contains('files')){
        db.createObjectStore('files',{keyPath:'id',autoIncrement:true});
      }
    };
    req.onsuccess=function(e){resolve(e.target.result);};
    req.onerror=function(){reject(req.error);};
  });
}

function ouUpload(event){
  var files=Array.from(event.target.files||[]);
  if(!files.length)return;
  event.target.value='';
  var status=document.getElementById('ouUploadStatus');
  status.style.display='inline';
  status.textContent='Uploading…';
  var processed=0;
  function processNext(){
    if(processed>=files.length){
      status.textContent='✓ Uploaded '+files.length+' file'+(files.length>1?'s':'')+'!';
      ouRenderList();
      setTimeout(function(){
        var el=document.getElementById('ouFileList');
        if(el)el.scrollIntoView({behavior:'smooth',block:'start'});
        setTimeout(function(){status.style.display='none';},2000);
      },400);
      return;
    }
    var file=files[processed];
    status.textContent='Uploading '+(processed+1)+' of '+files.length+': '+file.name;
    ouOpenDB().then(function(db){
      var record={name:file.name,type:file.type||'application/octet-stream',
                  blob:file,uploadDate:new Date().toISOString(),size:file.size,note:''};
      var tx=db.transaction('files','readwrite');
      tx.objectStore('files').add(record).onsuccess=function(){
        processed++;processNext();
      };
      tx.onerror=function(){processed++;processNext();};
    }).catch(function(){processed++;processNext();});
  }
  processNext();
}

function ouDownload(id){
  ouOpenDB().then(function(db){
    var req=db.transaction('files','readonly').objectStore('files').get(id);
    req.onsuccess=function(e){
      var r=e.target.result;if(!r)return;
      var blob=r.blob instanceof Blob?r.blob:new Blob([r.blob],{type:r.type});
      var url=URL.createObjectURL(blob);
      var a=document.createElement('a');a.href=url;a.download=r.name;a.click();
      setTimeout(function(){URL.revokeObjectURL(url);},5000);
    };
  });
}

function ouDeleteFile(id){
  if(!confirm('Delete this file?'))return;
  ouOpenDB().then(function(db){
    db.transaction('files','readwrite').objectStore('files').delete(id).onsuccess=function(){ouRenderList();};
  });
}

function ouSaveNote(id,value){
  ouOpenDB().then(function(db){
    var tx=db.transaction('files','readwrite');
    var store=tx.objectStore('files');
    var req=store.get(id);
    req.onsuccess=function(e){
      var r=e.target.result;if(!r)return;
      r.note=value;store.put(r);
    };
  });
}

function ouFormatSize(bytes){
  if(bytes>=1073741824)return (bytes/1073741824).toFixed(1)+' GB';
  if(bytes>=1048576)return (bytes/1048576).toFixed(1)+' MB';
  if(bytes>=1024)return (bytes/1024).toFixed(1)+' KB';
  return bytes+' B';
}

function ouRenderList(){
  ouOpenDB().then(function(db){
    var req=db.transaction('files','readonly').objectStore('files').getAll();
    req.onsuccess=function(e){
      var records=e.target.result||[];
      var listDiv=document.getElementById('ouFileList');
      var inner=document.getElementById('ouFileListInner');
      var empty=document.getElementById('ouEmpty');
      if(!records.length){
        if(listDiv)listDiv.style.display='none';
        if(empty)empty.style.display='block';
        return;
      }
      if(listDiv)listDiv.style.display='block';
      if(empty)empty.style.display='none';
      var icons={'image':'🖼','video':'🎬','audio':'🎵','pdf':'📄','spreadsheet':'📊','word':'📝','text':'📃'};
      function getIcon(type,name){
        if(type.startsWith('image/'))return icons.image;
        if(type.startsWith('video/'))return icons.video;
        if(type.startsWith('audio/'))return icons.audio;
        if(type==='application/pdf'||name.endsWith('.pdf'))return icons.pdf;
        if(type.includes('spreadsheet')||name.endsWith('.xlsx')||name.endsWith('.csv'))return icons.spreadsheet;
        if(type.includes('word')||name.endsWith('.docx'))return icons.word;
        if(type.startsWith('text/')||name.endsWith('.txt')||name.endsWith('.md'))return icons.text;
        return '📁';
      }
      inner.innerHTML=records.slice().reverse().map(function(r){
        var icon=getIcon(r.type,r.name);
        var date=r.uploadDate?new Date(r.uploadDate).toLocaleDateString():'';
        var size=ouFormatSize(r.size||0);
        var note=(r.note||'').replace(/"/g,'&quot;');
        return '<div style="background:#fff;border:1px solid #c4b5fd;border-radius:9px;padding:10px 12px;margin-bottom:8px;">'
          +'<div style="display:flex;align-items:flex-start;gap:10px;">'
          +'<span style="font-size:1.4rem;flex-shrink:0;line-height:1;">'+icon+'</span>'
          +'<div style="flex:1;min-width:0;">'
          +'<div style="font-size:0.88rem;font-weight:700;color:#3730a3;word-break:break-word;margin-bottom:2px;">'+r.name+'</div>'
          +'<div style="font-size:0.72rem;color:#94a3b8;">'+size+(date?' · Uploaded '+date:'')+'</div>'
          +'</div>'
          +'<div style="display:flex;gap:5px;flex-shrink:0;">'
          +'<button onclick="ouDownload('+r.id+')" style="background:#4f46e5;color:#fff;border:none;border-radius:6px;padding:5px 10px;font-size:0.75rem;cursor:pointer;font-weight:600;">&#x2B07; Download</button>'
          +'<button onclick="ouDeleteFile('+r.id+')" style="background:#fee2e2;color:#dc2626;border:none;border-radius:6px;padding:5px 8px;font-size:0.75rem;cursor:pointer;">&#x1F5D1;</button>'
          +'</div>'
          +'</div>'
          +'<textarea rows="2" placeholder="Add a note about this file..." onblur="ouSaveNote('+r.id+',this.value)"'
          +' style="width:100%;margin-top:8px;border:1px solid #ddd6fe;border-radius:6px;padding:6px 8px;font-size:0.82rem;box-sizing:border-box;outline:none;resize:vertical;font-family:inherit;color:#3730a3;background:#faf5ff;">'+note+'</textarea>'
          +'</div>';
      }).join('');
    };
  });
}

window.addEventListener('load',function(){ouRenderList();});
window.addEventListener('load',function(){checkBackupReminder();});
/* ========= END OTHER FILE UPLOADS ========= */

/* ========= SHOE TRACKER ========= */
