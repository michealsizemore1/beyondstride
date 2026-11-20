function bibleDateKey(d){
  // d is a date string like "YYYY-MM-DD" or a Date object
  if(typeof d==='string')return d;
  const yr=d.getFullYear(),mo=String(d.getMonth()+1).padStart(2,'0'),dy=String(d.getDate()).padStart(2,'0');
  return yr+'-'+mo+'-'+dy;
}
function getBibleDateInput(){
  const inp=document.getElementById('bibleDate');
  if(!inp)return null;
  if(!inp.value){
    const t=new Date();
    inp.value=bibleDateKey(t);
  }
  return inp.value;
}
function biblePrevDay(){
  const inp=document.getElementById('bibleDate');
  if(!inp)return;
  const d=new Date(inp.value+'T00:00:00');
  d.setDate(d.getDate()-1);
  inp.value=bibleDateKey(d);
  loadBibleEntry();
}
function bibleNextDay(){
  const inp=document.getElementById('bibleDate');
  if(!inp)return;
  const d=new Date(inp.value+'T00:00:00');
  d.setDate(d.getDate()+1);
  inp.value=bibleDateKey(d);
  loadBibleEntry();
}
function openBibleSermonLink(){
  const url=document.getElementById('bSermonURL');
  if(url&&url.value.trim())window.open(url.value.trim(),'_blank');
}
function toggleBibleHistory(){
  var panel=document.getElementById('bibleHistoryPanel');
  var arrow=document.getElementById('bibleHistoryArrow');
  if(!panel)return;
  var open=panel.style.display==='none'||!panel.style.display;
  panel.style.display=open?'block':'none';
  if(arrow)arrow.innerHTML=open?'&#x25BC;':'&#x25B6;';
}
function clearBibleEntry(){
  const fields=['bSermonTitle','bSermonSpeaker','bSermonURL','bSermonNotes','bScripture','bKeyVerse','bTheme','bNotes','bApplication','bPrayer'];
  fields.forEach(id=>{const el=document.getElementById(id);if(el)el.value='';});
}



// ── Book Covers IndexedDB ─────────────────────────────────────────────────────
var _bookCoversDB=null;
function openBookCoversDB(){
  return new Promise(function(resolve,reject){
    if(_bookCoversDB){resolve(_bookCoversDB);return;}
    var req=indexedDB.open('BookCoversDB',1);
    req.onupgradeneeded=function(e){e.target.result.createObjectStore('covers',{keyPath:'bookId'});};
    req.onsuccess=function(e){_bookCoversDB=e.target.result;resolve(_bookCoversDB);};
    req.onerror=function(){reject(req.error);};
  });
}
function setBookCover(bookId,dataUrl){
  return openBookCoversDB().then(function(db){
    return new Promise(function(resolve,reject){
      var tx=db.transaction('covers','readwrite');
      tx.objectStore('covers').put({bookId:bookId,dataUrl:dataUrl});
      tx.oncomplete=resolve;tx.onerror=reject;
    });
  });
}
function getBookCover(bookId){
  return openBookCoversDB().then(function(db){
    return new Promise(function(resolve){
      var req=db.transaction('covers','readonly').objectStore('covers').get(bookId);
      req.onsuccess=function(){resolve(req.result?req.result.dataUrl:null);};
      req.onerror=function(){resolve(null);};
    });
  });
}
function deleteBookCover(bookId){
  return openBookCoversDB().then(function(db){
    return new Promise(function(resolve){
      var tx=db.transaction('covers','readwrite');
      tx.objectStore('covers').delete(bookId);
      tx.oncomplete=resolve;tx.onerror=resolve;
    });
  });
}
function getAllBookCovers(){
  return openBookCoversDB().then(function(db){
    return new Promise(function(resolve){
      var req=db.transaction('covers','readonly').objectStore('covers').getAll();
      req.onsuccess=function(){resolve(req.result||[]);};
      req.onerror=function(){resolve([]);};
    });
  });
}
function migrateBookCovers(){
  var books=getBooks();var needsSave=false;var promises=[];
  books.forEach(function(b){
    if(b.coverUrl){promises.push(setBookCover(b.id,b.coverUrl));delete b.coverUrl;needsSave=true;}
  });
  if(needsSave)saveBooks(books);
  return Promise.all(promises);
}
function _resizeToDataUrl(img,maxW,maxH,quality){
  var w=img.naturalWidth,h=img.naturalHeight;
  var scale=Math.min(maxW/w,maxH/h,1);
  var cw=Math.round(w*scale)||1;var ch=Math.round(h*scale)||1;
  var canvas=document.createElement('canvas');canvas.width=cw;canvas.height=ch;
  canvas.getContext('2d').drawImage(img,0,0,cw,ch);
  return canvas.toDataURL('image/jpeg',quality||0.82);
}
function _storeBookCover(bookId,dataUrl){
  setBookCover(bookId,dataUrl).then(function(){
    var imgEl=document.getElementById('bookCoverImg_'+bookId);
    if(imgEl){imgEl.src=dataUrl;imgEl.style.display='block';}
    else renderLibrary();
  }).catch(function(){renderLibrary();});
}

// ── Book Documents (IndexedDB) ───────────────────────────────────────────────
var _bookDocsDB=null;
function openBookDocsDB(){
  return new Promise(function(resolve,reject){
    if(_bookDocsDB){resolve(_bookDocsDB);return;}
    var req=indexedDB.open('BookDocumentsDB',1);
    req.onupgradeneeded=function(e){
      var db=e.target.result;
      if(!db.objectStoreNames.contains('docs'))
        db.createObjectStore('docs',{keyPath:'id',autoIncrement:true});
    };
    req.onsuccess=function(e){_bookDocsDB=e.target.result;resolve(_bookDocsDB);};
    req.onerror=function(){reject(req.error);};
  });
}
function addBookDocument(bookId,file){
  return new Promise(function(resolve,reject){
    var reader=new FileReader();
    reader.onload=function(e){
      openBookDocsDB().then(function(db){
        var tx=db.transaction('docs','readwrite');
        var rec={bookId:bookId,name:file.name,type:file.type,size:file.size,dataUrl:e.target.result,addedAt:new Date().toISOString()};
        var req=tx.objectStore('docs').add(rec);
        req.onsuccess=function(){rec.id=req.result;resolve(rec);};
        req.onerror=function(){reject(req.error);};
      }).catch(reject);
    };
    reader.readAsDataURL(file);
  });
}
function getBookDocuments(bookId){
  return openBookDocsDB().then(function(db){
    return new Promise(function(resolve){
      var tx=db.transaction('docs','readonly');
      var req=tx.objectStore('docs').getAll();
      req.onsuccess=function(){
        resolve((req.result||[]).filter(function(d){return d.bookId===bookId;}));
      };
      req.onerror=function(){resolve([]);};
    });
  });
}
function deleteBookDocument(docId,bookId){
  return openBookDocsDB().then(function(db){
    return new Promise(function(resolve){
      var tx=db.transaction('docs','readwrite');
      tx.objectStore('docs').delete(docId);
      tx.oncomplete=function(){renderBookDocuments(bookId);resolve();};
    });
  });
}
function getAllBookDocuments(){
  return openBookDocsDB().then(function(db){
    return new Promise(function(resolve){
      var tx=db.transaction('docs','readonly');
      var req=tx.objectStore('docs').getAll();
      req.onsuccess=function(){resolve(req.result||[]);};
      req.onerror=function(){resolve([]);};
    });
  });
}
function putBookDocument(rec){
  return openBookDocsDB().then(function(db){
    return new Promise(function(resolve){
      var tx=db.transaction('docs','readwrite');
      tx.objectStore('docs').put(rec);
      tx.oncomplete=resolve;
    });
  });
}
function renderBookDocuments(bookId){
  var wrap=document.getElementById('bookDocsWrap_'+bookId);
  if(!wrap)return;
  getBookDocuments(bookId).then(function(docs){
    // Auto-show the section whenever we render docs
    var section=document.getElementById('bookDocsSection_'+bookId);
    if(section&&docs.length)section.style.display='block';
    if(!docs.length){
      wrap.innerHTML='<span style="color:#bbb;font-size:0.78rem;">No documents yet.</span>';
      return;
    }
    wrap.innerHTML=docs.map(function(d){
      var icon='&#x1F4C4;';
      if(d.type&&d.type.includes('pdf'))icon='&#x1F4CB;';
      else if(d.type&&d.type.includes('image'))icon='&#x1F5BC;';
      var kbStr=d.size?(Math.round(d.size/1024)+'KB'):'';
      return '<div style="display:flex;align-items:center;gap:6px;padding:5px 0;border-bottom:1px solid #f5f5f5;">'
        +icon+' <a href="'+d.dataUrl+'" download="'+d.name+'" style="flex:1;font-size:0.8rem;color:#667eea;text-decoration:none;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="'+d.name+'">'+d.name+'</a>'
        +(kbStr?'<span style="font-size:0.7rem;color:#aaa;flex-shrink:0;">'+kbStr+'</span>':'')
        +'<button onclick="deleteBookDocument('+d.id+','+bookId+')" style="background:none;border:none;color:#e74c3c;cursor:pointer;font-size:0.75rem;padding:0 2px;" title="Remove">&#x2715;</button>'
        +'</div>';
    }).join('');
  });
}
function uploadBookDocument(bookId,input){
  var files=Array.from(input.files||[]);
  if(!files.length)return;
  // Show the section immediately so user sees progress
  var section=document.getElementById('bookDocsSection_'+bookId);
  if(section)section.style.display='block';
  var promises=files.map(function(f){return addBookDocument(bookId,f);});
  Promise.all(promises).then(function(){renderBookDocuments(bookId);});
  input.value='';
}
function toggleBookDocs(bookId){
  var w=document.getElementById('bookDocsSection_'+bookId);
  if(!w)return;
  var isOpen=w.style.display!=='none';
  w.style.display=isOpen?'none':'block';
  if(!isOpen)renderBookDocuments(bookId);
}

// ── Bible Images (IndexedDB) ─────────────────────────────────────────────────
var _bibleDB=null;
function openBibleDB(){
  return new Promise(function(resolve,reject){
    if(_bibleDB){resolve(_bibleDB);return;}
    var req=indexedDB.open('BibleImagesDB',1);
    req.onupgradeneeded=function(e){e.target.result.createObjectStore('images',{keyPath:'id',autoIncrement:true});};
    req.onsuccess=function(e){_bibleDB=e.target.result;resolve(_bibleDB);};
    req.onerror=function(){reject(req.error);};
  });
}
function addBibleImages(ev){
  var files=Array.from(ev.target.files||[]);
  if(!files.length)return;
  var dateKey=getBibleDateInput();if(!dateKey)return;
  openBibleDB().then(function(db){
    var tx=db.transaction('images','readwrite');
    var st=tx.objectStore('images');
    files.forEach(function(file){
      var reader=new FileReader();
      reader.onload=function(e){
        st.add({dateKey:dateKey,name:file.name,dataUrl:e.target.result,addedAt:new Date().toISOString()});
      };
      reader.readAsDataURL(file);
    });
    tx.oncomplete=function(){renderBibleImages(dateKey);ev.target.value='';};
  }).catch(function(){alert('Could not open image store.');});
}
function renderBibleImages(dateKey){
  var wrap=document.getElementById('bibleImagesWrap');if(!wrap)return;
  if(!dateKey){wrap.innerHTML='';return;}
  openBibleDB().then(function(db){
    var tx=db.transaction('images','readonly');
    var st=tx.objectStore('images');
    var req=st.getAll();
    req.onsuccess=function(){
      var imgs=(req.result||[]).filter(function(r){return r.dateKey===dateKey;});
      if(!imgs.length){wrap.innerHTML='<span style="color:#bbb;font-size:0.78rem;">No images yet.</span>';return;}
      wrap.innerHTML=imgs.map(function(r){
        return '<div style="display:inline-block;position:relative;margin:3px;">'
          +'<img src="'+r.dataUrl+'" style="width:72px;height:72px;object-fit:cover;border-radius:6px;border:1px solid #f0c080;cursor:pointer;" onclick="openBibleImageModal(\''+r.dataUrl+'\')" title="'+r.name+'">'
          +'<button onclick="deleteBibleImage('+r.id+')" style="position:absolute;top:-4px;right:-4px;background:#e74c3c;color:#fff;border:none;border-radius:50%;width:16px;height:16px;font-size:10px;cursor:pointer;line-height:1;padding:0;">&#x2715;</button>'
          +'</div>';
      }).join('');
    };
  });
}
function deleteBibleImage(id){
  if(!confirm('Remove this image?'))return;
  openBibleDB().then(function(db){
    var tx=db.transaction('images','readwrite');
    tx.objectStore('images').delete(id);
    tx.oncomplete=function(){renderBibleImages(getBibleDateInput());};
  });
}
function openBibleImageModal(src){
  var m=document.getElementById('bibleImageModal');
  var img=document.getElementById('bibleImageFull');
  if(m&&img){img.src=src;m.style.display='flex';}
}
function closeBibleImageModal(){
  var m=document.getElementById('bibleImageModal');
  if(m)m.style.display='none';
}

// ── Bible Transcript ─────────────────────────────────────────────────────────
function toggleSermonTranscript(forceOpen){
  var panel=document.getElementById('bTranscriptPanel');
  var arrow=document.getElementById('bTranscriptArrow');
  if(!panel)return;
  var open=typeof forceOpen==='boolean'?forceOpen:(panel.style.display==='none'||!panel.style.display);
  panel.style.display=open?'block':'none';
  if(arrow)arrow.innerHTML=open?'&#x25BC;':'&#x25B6;';
}
function updateTranscriptSummary(fileName,text){
  var summary=document.getElementById('bTranscriptSummary');
  if(!summary)return;
  text=text||'';
  var words=text.trim()?text.trim().split(/\s+/).length:0;
  summary.textContent=text?(fileName||'Saved transcript')+' \u2022 '+words.toLocaleString()+' words':'No transcript saved';
  summary.style.color=text?'#1e6b3a':'#528062';
}
function markTranscriptEdited(){
  var ta=document.getElementById('bTranscriptText');
  var fileName=ta&&ta.dataset?ta.dataset.fileName||'Pasted transcript':'Pasted transcript';
  updateTranscriptSummary(fileName,ta?ta.value:'');
  var status=document.getElementById('bTranscriptStatus');
  if(status){status.style.display='inline';status.style.color='#8a6d3b';status.textContent='Edited \u2014 tap Save Transcript to keep changes';}
}
function persistUploadedTranscript(fileName,text){
  var dateKey=getBibleDateInput();
  if(!dateKey)return;
  var storageKey='bible_'+dateKey;
  var entry={};
  try{entry=JSON.parse(localStorage.getItem(storageKey)||'{}')||{};}catch(err){entry={};}
  entry.transcript=text||'';
  entry.transcriptFileName=fileName||'';
  entry.savedAt=new Date().toISOString();
  localStorage.setItem(storageKey,JSON.stringify(entry));
}
function loadTranscriptFile(ev){
  var file=ev.target.files[0];if(!file)return;
  if(file.type==='application/pdf'||file.name.toLowerCase().endsWith('.pdf')){
    alert('PDF files cannot be read as text. Please save as .txt and re-upload.');
    ev.target.value='';return;
  }
  var reader=new FileReader();
  reader.onload=function(e){
    var text=(e.target.result||'').replace(/\r\n/g,'\n').replace(/\r/g,'\n');
    var ta=document.getElementById('bTranscriptText');
    if(ta){ta.value=text;ta.dataset.fileName=file.name;}
    var status=document.getElementById('bTranscriptStatus');
    if(status){
      var lines=text.split('\n').length;
      var chars=text.length;
      status.style.color='#27ae60';
      status.textContent='Loaded temporarily: '+file.name+' \u2022 '+lines+' lines \u2022 '+chars.toLocaleString()+' characters. AI analysis is starting...';
    }
    setTimeout(aiTranscriptFill,50);
  };
  reader.readAsText(file,'UTF-8');
}
function clearTranscript(){
  var ta=document.getElementById('bTranscriptText');if(ta){ta.value='';ta.dataset.fileName='';}
  var fi=document.getElementById('bTranscriptFile');if(fi)fi.value='';
  var status=document.getElementById('bTranscriptStatus');
  if(status){status.style.display='inline';status.style.color='#528062';status.textContent='Transcript is temporary and will not be saved.';}
  updateTranscriptSummary('','');
}
async function youtubeTranscriptFill(){
  var url=((document.getElementById('bSermonURL')||{}).value||'').trim();
  if(!url){alert('Paste the YouTube sermon link first.');return;}
  if(!/(?:youtube\.com|youtu\.be)/i.test(url)){alert('Please enter a valid YouTube link.');return;}
  var btn=document.getElementById('bYouTubeFillBtn'),status=document.getElementById('bTranscriptStatus');
  if(btn){btn.disabled=true;btn.textContent='Getting captions...';}
  if(status){status.style.display='inline';status.style.color='#667eea';status.textContent='Retrieving available YouTube captions...';}
  try{
    var data=await journalProtectedFunction({action:'youtube-transcript',url:url});
    var text=data.transcript||'';
    if(!text)throw new Error('This video does not have accessible YouTube captions. Upload or paste a transcript instead.');
    var ta=document.getElementById('bTranscriptText');
    var label='YouTube captions'+(data.title?' — '+data.title:'');
    if(ta){ta.value=text;ta.dataset.fileName=label;}
    var sermonURL=document.getElementById('bSermonURL');if(sermonURL)sermonURL.value=url;
    var title=document.getElementById('bSermonTitle');if(title&&data.title&&!title.value)title.value=data.title;
    var speaker=document.getElementById('bSermonSpeaker');if(speaker&&data.author&&!speaker.value)speaker.value=data.author;
    persistUploadedTranscript(label,text);
    updateTranscriptSummary(label,text);
    toggleSermonTranscript(true);
    if(status){status.style.color='#27ae60';status.textContent='Captions saved. AI is filling the sermon fields...';}
    if(btn){btn.textContent='Captions found';}
    aiTranscriptFill();
  }catch(error){
    if(status){status.style.color='#e74c3c';status.textContent=error.message||String(error);}
  }finally{
    setTimeout(function(){if(btn){btn.disabled=false;btn.innerHTML='&#x25B6; Fill from YouTube';}},900);
  }
}
async function aiTranscriptFill(){
  var transcript=(document.getElementById('bTranscriptText')||{}).value||'';
  if(!transcript.trim()){alert('Please paste or upload a transcript first.');return;}
  var key=journalAIKey();
  if(!key){alert('Add your Anthropic API key in Settings first.');return;}
  var btn=document.getElementById('bTranscriptAIBtn');
  var status=document.getElementById('bTranscriptStatus');
  if(btn){btn.disabled=true;btn.textContent='Analyzing...';}
  if(status){status.style.display='inline';status.textContent='AI is reading the transcript...';}
  var maxChars=60000;
  var excerpt=transcript.length>maxChars?transcript.slice(0,maxChars)+'...[truncated]':transcript;
  var requestBody={model:'claude-haiku-4-5-20251001',max_tokens:1800,
    messages:[{role:'user',content:'You are a sermon analysis assistant. Read the following sermon or Bible-study transcript and fill every applicable journal field. Return ONLY a valid JSON object with these exact keys:\n{\n  "sermonTitle": "a concise title taken from or accurately inferred from the message",\n  "speaker": "the speaker or teacher; if not stated, write Not identified in transcript",\n  "sermonNotes": "a clear 3-5 sentence summary of the key points and main message",\n  "scripture": "all principal scripture passages referenced; if none are clear, write Not clearly identified in transcript",\n  "keyVerse": "the most central verse or passage; if no exact verse is stated, identify the closest clearly discussed passage",\n  "mainTheme": "the central theme in 3-6 words",\n  "studyNotes": "important biblical insights, context, or interpretation in 2-4 sentences",\n  "application": "2-4 specific, practical life-application statements"\n}\n\nEvery value must contain useful text. Do not leave any value blank. Do not invent a speaker name or an exact verse citation that the transcript does not support. Return only the JSON, with no markdown or additional text.\n\nTranscript:\n'+excerpt}]};
  try{
    var d;
    try{d=await journalProtectedFunction(requestBody);}
    catch(firstError){
      if(status){status.textContent='Connection interrupted. Retrying securely...';status.style.color='#b45309';}
      await new Promise(function(resolve){setTimeout(resolve,900);});
      d=await journalProtectedFunction(requestBody);
    }
    var text=(d.content&&d.content[0]&&d.content[0].text)||'';
    var jsonMatch=text.match(/\{[\s\S]*\}/);
    if(!jsonMatch)throw new Error('No JSON found in response');
    var fields=JSON.parse(jsonMatch[0]);
    fields.sermonTitle=fields.sermonTitle||'Sermon or Bible Study';
    fields.speaker=fields.speaker||'Not identified in transcript';
    fields.scripture=fields.scripture||'Not clearly identified in transcript';
    fields.keyVerse=fields.keyVerse||'No specific key verse identified';
    fields.mainTheme=fields.mainTheme||'Central message and life application';
    fields.sermonNotes=fields.sermonNotes||'The transcript was reviewed, but a clear summary could not be generated.';
    fields.studyNotes=fields.studyNotes||'Review the transcript alongside the referenced Scripture for additional context.';
    fields.application=fields.application||'Identify one truth from this message to practice this week.';
    var map={sermonTitle:'bSermonTitle',speaker:'bSermonSpeaker',scripture:'bScripture',keyVerse:'bKeyVerse',mainTheme:'bTheme'};
    Object.keys(map).forEach(function(k){
      var el=document.getElementById(map[k]);
      if(el&&fields[k])el.value=fields[k];
    });
    var combinedNotes=[fields.sermonNotes,fields.studyNotes,fields.application].filter(Boolean).join('\n\n');
    var sermonNotesEl=document.getElementById('bSermonNotes');if(sermonNotesEl&&combinedNotes)sermonNotesEl.value=combinedNotes;
    var legacyStudyNotes=document.getElementById('bNotes');if(legacyStudyNotes)legacyStudyNotes.value='';
    var legacyApplication=document.getElementById('bApplication');if(legacyApplication)legacyApplication.value='';
    saveBibleEntry();
    if(btn){btn.disabled=false;btn.innerHTML='&#x1F9E0; AI Fill All Fields';}
    if(status){status.textContent='&#x2713; Fields filled from transcript!';status.style.color='#27ae60';}
  }catch(e){
    if(btn){btn.disabled=false;btn.innerHTML='&#x1F9E0; AI Fill All Fields';}
    var message=e&&e.message?e.message:String(e||'Unknown error');
    if(/failed to fetch|networkerror|load failed/i.test(message))message='Could not reach the protected AI service. Confirm that you are signed in and test from the published GitHub journal.';
    if(status){status.textContent='Error: '+message;status.style.color='#e74c3c';}
  }
}

function saveBibleEntry(manualSave){
  const dateKey=getBibleDateInput();
  if(!dateKey)return;
  let previousBibleEntry={};
  try{previousBibleEntry=JSON.parse(localStorage.getItem('bible_'+dateKey)||'{}')||{};}catch(ignore){}
  const transcriptField=document.getElementById('bTranscriptText');
  const entry={
    sermonTitle:(document.getElementById('bSermonTitle')||{}).value||'',
    sermonSpeaker:(document.getElementById('bSermonSpeaker')||{}).value||'',
    sermonURL:(document.getElementById('bSermonURL')||{}).value||'',
    sermonNotes:(document.getElementById('bSermonNotes')||{}).value||'',
    scripture:(document.getElementById('bScripture')||{}).value||'',
    keyVerse:(document.getElementById('bKeyVerse')||{}).value||'',
    theme:(document.getElementById('bTheme')||{}).value||'',
    notes:(document.getElementById('bNotes')||{}).value||'',
    application:(document.getElementById('bApplication')||{}).value||'',
    prayer:previousBibleEntry.prayer||'',
    transcript:previousBibleEntry.transcript||'',
    transcriptFileName:previousBibleEntry.transcriptFileName||'',
    savedAt:new Date().toISOString(),
    archived:!!manualSave
  };
  if(manualSave){
    var archiveKey=bibleArchiveViewingKey||('bible_sermon_'+dateKey+'_'+Date.now());
    localStorage.setItem(archiveKey,JSON.stringify(entry));
    localStorage.setItem('bible_'+dateKey,JSON.stringify({archived:true,hasArchivedSermons:true,savedAt:entry.savedAt}));
    bibleArchiveViewingKey='';
    bibleArchiveViewingDate='';
  }else localStorage.setItem('bible_'+dateKey, JSON.stringify(entry));
  updateTranscriptSummary(entry.transcriptFileName,entry.transcript);
  const transcriptStatus=document.getElementById('bTranscriptStatus');
  if(transcriptStatus&&entry.transcript){transcriptStatus.style.display='inline';transcriptStatus.style.color='#27ae60';transcriptStatus.textContent='Transcript saved with this Sermon date';}
  renderBibleHistory();
  renderMonthlySermonArchive();
  const autoStatus=document.getElementById('bBibleAutoSaveStatus');
  if(autoStatus){autoStatus.textContent=manualSave?'Saved':'Saved automatically';setTimeout(function(){autoStatus.textContent='';},1400);}
  const saveBtn=document.getElementById('bBibleSaveBtn');
  if(manualSave){
    clearBibleEntry();clearTranscript();
    if(saveBtn){const original=saveBtn.innerHTML;saveBtn.innerHTML='&#x2713; Saved to Archive';saveBtn.disabled=true;setTimeout(function(){saveBtn.innerHTML=original;saveBtn.disabled=false;},1400);}
  }
}
let bibleAutoSaveTimer=null;
let bibleArchiveViewingDate='',bibleArchiveViewingKey='';
function queueBibleAutoSave(){
  clearTimeout(bibleAutoSaveTimer);
  var status=document.getElementById('bBibleAutoSaveStatus');if(status)status.textContent='Saving…';
  bibleAutoSaveTimer=setTimeout(saveBibleEntry,550);
}
function monthlySermonEntries(){
  var month=dk(new Date(today.getFullYear(),today.getMonth(),1)).slice(0,7),legacyPrefix='bible_'+month+'-',archivePrefix='bible_sermon_'+month+'-';
  var entries=[];
  for(var i=0;i<localStorage.length;i++){
    var key=localStorage.key(i)||'',date='';
    if(key.indexOf(archivePrefix)===0)date=key.slice(13,23);
    else if(key.indexOf(legacyPrefix)===0)date=key.slice(6,16);
    else continue;
    try{
      var data=JSON.parse(localStorage.getItem(key)||'{}')||{};
      var meaningful=[data.sermonTitle,data.sermonSpeaker,data.sermonURL,data.sermonNotes,data.scripture,data.keyVerse,data.theme].some(function(value){return String(value||'').trim();});
       if(meaningful&&data.archived!==false)entries.push({storageKey:key,date:date,data:data});
    }catch(ignore){}
  }
  return entries.sort(function(a,b){return b.date.localeCompare(a.date)||(String(b.data.savedAt||'').localeCompare(String(a.data.savedAt||'')));});
}
function renderMonthlySermonArchive(){
  var list=document.getElementById('monthlySermonArchiveList'),title=document.getElementById('monthlySermonArchiveTitle'),count=document.getElementById('monthlySermonArchiveCount'),deleteBtn=document.getElementById('deleteMonthlySermonsBtn');
  if(!list)return;
  var monthLabel=new Date(today.getFullYear(),today.getMonth(),1).toLocaleDateString('en-US',{month:'long',year:'numeric'}),entries=monthlySermonEntries();
  if(title)title.textContent=monthLabel+' Sermon Archive';
  if(count)count.textContent=entries.length+' saved';
  if(deleteBtn)deleteBtn.style.display=entries.length?'inline-block':'none';
  if(!entries.length){list.innerHTML='<div style="font-size:.76rem;color:#98a2b3;padding:9px 0;">No Sermon &amp; Bible Study entries saved for this month.</div>';return;}
  list.innerHTML=entries.map(function(entry){
    var data=entry.data,date=new Date(entry.date+'T00:00:00'),label=date.toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric'}),savedTime=data.savedAt?new Date(data.savedAt).toLocaleTimeString('en-US',{hour:'numeric',minute:'2-digit'}):'',entryTitle=data.sermonTitle||data.theme||data.scripture||'Sermon & Bible Study';
    return '<div style="display:flex;align-items:center;gap:7px;padding:8px 0;border-bottom:1px solid #f3e8dc;">'
      +'<button type="button" onclick="openMonthlySermonEntry(&quot;'+entry.storageKey+'&quot;,&quot;'+entry.date+'&quot;)" style="flex:1;text-align:left;background:none;border:none;padding:0;cursor:pointer;min-width:0;"><span style="display:block;font-size:.76rem;font-weight:800;color:#9a5a24;">'+escHtml(label+(savedTime?' • '+savedTime:''))+'</span><span style="display:block;font-size:.82rem;color:#344054;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">'+escHtml(entryTitle)+'</span></button>'
      +'<button type="button" onclick="deleteMonthlySermonEntry(&quot;'+entry.storageKey+'&quot;,&quot;'+entry.date+'&quot;)" title="Delete this saved entry" style="background:#fff1f2;color:#b42318;border:none;border-radius:5px;padding:5px 7px;font-size:.72rem;cursor:pointer;">Delete</button></div>';
  }).join('');
}
function openMonthlySermonEntry(storageKey,dateKey){
  var entry={};
  try{entry=JSON.parse(localStorage.getItem(storageKey)||'{}')||{};}catch(ignore){}
  if(!Object.keys(entry).length){alert('This saved sermon could not be found.');renderMonthlySermonArchive();return;}
  bibleArchiveViewingDate=dateKey;
  bibleArchiveViewingKey=storageKey;
  var fieldMap={
    bSermonTitle:'sermonTitle',
    bSermonSpeaker:'sermonSpeaker',
    bSermonURL:'sermonURL',
    bScripture:'scripture',
    bKeyVerse:'keyVerse',
    bTheme:'theme'
  };
  Object.keys(fieldMap).forEach(function(id){var el=document.getElementById(id);if(el)el.value=entry[fieldMap[id]]||'';});
  var notes=[entry.sermonNotes,entry.notes,entry.application].filter(function(value,index,values){return value&&values.indexOf(value)===index;}).join('\n\n');
  var notesEl=document.getElementById('bSermonNotes');if(notesEl)notesEl.value=notes;
  var legacyNotes=document.getElementById('bNotes');if(legacyNotes)legacyNotes.value='';
  var legacyApplication=document.getElementById('bApplication');if(legacyApplication)legacyApplication.value='';
  clearTranscript();
  var status=document.getElementById('bTranscriptStatus');
  if(status){status.style.display='inline';status.style.color='#528062';status.textContent='Viewing saved sermon from '+new Date(dateKey+'T00:00:00').toLocaleDateString()+'.';}
  var card=document.getElementById('monthlySermonArchive');if(card)card.open=true;
  var sermonSection=document.getElementById('bSermonTitle');
  if(sermonSection)setTimeout(function(){sermonSection.scrollIntoView({behavior:'smooth',block:'center'});sermonSection.focus({preventScroll:true});},0);
  v26Toast('Saved sermon opened');
}
function deleteMonthlySermonEntry(storageKey,dateKey){
  var data={};try{data=JSON.parse(localStorage.getItem(storageKey)||'{}')||{};}catch(ignore){}
  var name=data.sermonTitle||'this Sermon & Bible Study entry';
  if(!confirm('Delete '+name+' from '+new Date(dateKey+'T00:00:00').toLocaleDateString()+'?'))return;
  localStorage.removeItem(storageKey);
  if(storageKey===bibleArchiveViewingKey){bibleArchiveViewingKey='';bibleArchiveViewingDate='';clearBibleEntry();}
  renderMonthlySermonArchive();renderBibleHistory();
}
function deleteCurrentMonthSermons(){
  var entries=monthlySermonEntries();if(!entries.length)return;
  var monthLabel=new Date(today.getFullYear(),today.getMonth(),1).toLocaleDateString('en-US',{month:'long',year:'numeric'});
  if(!confirm('Delete all '+entries.length+' Sermon & Bible Study entries from '+monthLabel+'?\n\nThis cannot be undone unless you restore a backup.'))return;
  entries.forEach(function(entry){localStorage.removeItem(entry.storageKey);});
  loadBibleEntry();
}
function loadBibleEntry(){
  const dateKey=getBibleDateInput();
  if(!dateKey)return;
  const raw=localStorage.getItem('bible_'+dateKey);
  const fields=['bSermonTitle','bSermonSpeaker','bSermonURL','bSermonNotes','bScripture','bKeyVerse','bTheme','bNotes','bApplication','bPrayer'];
  const keys=['sermonTitle','sermonSpeaker','sermonURL','sermonNotes','scripture','keyVerse','theme','notes','application','prayer'];
  if(raw){
    const e=JSON.parse(raw);
    const showArchived=!e.archived||bibleArchiveViewingDate===dateKey;
    fields.forEach((id,i)=>{const el=document.getElementById(id);if(el)el.value=showArchived?(e[keys[i]]||''):'';});
    const mergedNotes=[e.sermonNotes,e.notes,e.application].filter(function(v,index,arr){return v&&arr.indexOf(v)===index;}).join('\n\n');
    const mergedNotesEl=document.getElementById('bSermonNotes');if(mergedNotesEl)mergedNotesEl.value=showArchived?mergedNotes:'';
    const legacyNotesEl=document.getElementById('bNotes');if(legacyNotesEl)legacyNotesEl.value='';
    const legacyApplicationEl=document.getElementById('bApplication');if(legacyApplicationEl)legacyApplicationEl.value='';
    const transcript=document.getElementById('bTranscriptText');
    if(transcript){transcript.value='';transcript.dataset.fileName='';}
    const youtube=document.getElementById('bTranscriptYouTubeURL');
    if(youtube)youtube.value=/youtu\.?be/i.test(e.sermonURL||'')?(e.sermonURL||''):'';
    updateTranscriptSummary('','');
  } else {
    fields.forEach(id=>{const el=document.getElementById(id);if(el)el.value='';});
    const transcript=document.getElementById('bTranscriptText');
    if(transcript){transcript.value='';transcript.dataset.fileName='';}
    const youtube=document.getElementById('bTranscriptYouTubeURL');if(youtube)youtube.value='';
    updateTranscriptSummary('','');
  }
  const transcriptFile=document.getElementById('bTranscriptFile');if(transcriptFile)transcriptFile.value='';
  bibleArchiveViewingDate='';
  bibleArchiveViewingKey='';
  const transcriptStatus=document.getElementById('bTranscriptStatus');if(transcriptStatus){transcriptStatus.style.display='inline';transcriptStatus.style.color='#528062';transcriptStatus.textContent='Transcript is temporary and will not be saved.';}
  toggleSermonTranscript(false);
  renderBibleHistory();
  renderMonthlySermonArchive();
  renderBibleImages(dateKey);
}
function renderBibleHistory(){
  const el=document.getElementById('bibleHistoryList');
  if(!el)return;
  const entries=[];
  for(let i=0;i<localStorage.length;i++){
    const k=localStorage.key(i);
    if(k&&k.startsWith('bible_')&&!k.startsWith('bible_sermon_')){
      const dateKey=k.slice(6);
      try{
        const e=JSON.parse(localStorage.getItem(k));
        entries.push({dateKey,e});
      }catch(err){}
    }
  }
  entries.sort((a,b)=>b.dateKey.localeCompare(a.dateKey));
  if(entries.length===0){el.textContent='No entries yet.';return;}
  el.innerHTML=entries.slice(0,20).map(({dateKey,e})=>{
    const d=new Date(dateKey+'T00:00:00');
    const label=d.toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric',year:'numeric'});
    const preview=[e.sermonTitle,e.scripture,e.theme].filter(Boolean).join(' · ')||'(no summary)';
    return '<div style="padding:8px 10px;margin-bottom:6px;border:1px solid #f0c080;border-radius:8px;background:#fffaf4;display:flex;align-items:center;gap:8px;">'
      +'<div onclick="jumpBibleDate(&quot;'+dateKey+'&quot;)" style="flex:1;cursor:pointer;">'
      +'<div style="font-weight:600;color:#d35400;">'+label+'</div>'
      +'<div style="color:#777;font-size:0.8rem;">'+preview+'</div>'
      +'</div>'
      +'<button onclick="deleteBibleEntry(&quot;'+dateKey+'&quot;)" style="background:none;border:1px solid #fcc;border-radius:6px;padding:3px 8px;font-size:0.72rem;cursor:pointer;color:#e74c3c;flex-shrink:0;" title="Delete this entry">&#x1F5D1;</button>'
      +'</div>';
  }).join('');
}
function jumpBibleDate(dateKey){
  const inp=document.getElementById('bibleDate');
  if(inp){inp.value=dateKey;loadBibleEntry();}
}
function deleteBibleEntry(dateKey){
  if(!confirm('Delete the Bible entry for '+dateKey+'? This cannot be undone.'))return;
  localStorage.removeItem('bible_'+dateKey);
  const inp=document.getElementById('bibleDate');
  if(inp&&inp.value===dateKey){
    clearBibleEntry();
  }
  renderBibleHistory();
}

// ── Skill Building Log Tab ───────────────────────────────────────────────────
var _editingSkillId=null;
function getSkillEntries(){try{return JSON.parse(localStorage.getItem('skill_log_data')||'[]');}catch(e){return[];}}
function saveSkillEntries(arr){localStorage.setItem('skill_log_data',JSON.stringify(arr));}
function removeObsoleteQuickGrowthEntries(){
  if(localStorage.getItem('growth_quick_time_removed_v1')==='1')return;var entries=getSkillEntries(),clean=entries.filter(function(entry){return !/^quick-growth-(udemy|gun|language)-/.test(String(entry&&entry.id||''));});if(clean.length!==entries.length)saveSkillEntries(clean);localStorage.setItem('growth_quick_time_removed_v1','1');
}

function toggleAddSkill(){
  var f=document.getElementById('addSkillForm');
  if(!f)return;
  var isOpen=f.style.display!=='none';
  if(isOpen){f.style.display='none';_editingSkillId=null;return;}
  // Reset form
  var today2=dk(new Date());
  var el=function(id){return document.getElementById(id);};
  if(el('skDate'))el('skDate').value=today2;
  if(el('skCategory'))el('skCategory').value='Udemy';
  if(el('skName'))el('skName').value='';
  if(el('skTime'))el('skTime').value='';
  if(el('skResource'))el('skResource').value='';
  if(el('skLink'))el('skLink').value='';
  if(el('skNotes'))el('skNotes').value='';
  var title=document.getElementById('skillFormTitle');
  if(title)title.innerHTML='&#x2795; Add Skill Session';
  var btn=document.getElementById('skSaveBtn');
  if(btn)btn.textContent='Save Session';
  f.style.display='block';
  if(el('skName'))el('skName').focus();
}

function editSkillEntry(id){
  var entries=getSkillEntries();
  var entry=entries.find(function(e){return e.id===id;});
  if(!entry)return;
  _editingSkillId=id;
  var f=document.getElementById('addSkillForm');
  if(!f)return;
  var el=function(eid){return document.getElementById(eid);};
  if(el('skDate'))el('skDate').value=entry.date||'';
  if(el('skCategory'))el('skCategory').value=entry.category||'Other';
  if(el('skName'))el('skName').value=entry.name||'';
  if(el('skTime'))el('skTime').value=entry.time||'';
  if(el('skResource'))el('skResource').value=entry.resource||'';
  if(el('skLink'))el('skLink').value=entry.link||'';
  if(el('skNotes'))el('skNotes').value=entry.notes||'';
  var title=document.getElementById('skillFormTitle');
  if(title)title.innerHTML='&#x270F;&#xFE0F; Edit Skill Session';
  var btn=document.getElementById('skSaveBtn');
  if(btn)btn.textContent='Update Session';
  f.style.display='block';
  f.scrollIntoView({behavior:'smooth',block:'nearest'});
}

function saveSkillEntry(){
  var el=function(id){return document.getElementById(id);};
  var date=el('skDate')&&el('skDate').value;
  var name=(el('skName')&&el('skName').value||'').trim();
  if(!date){alert('Please select a date.');return;}
  if(!name){alert('Please enter a skill or topic.');return;}
  var rawLink=(el('skLink')&&el('skLink').value||'').trim(),safeLink=skillSafeLink(rawLink);if(rawLink&&!safeLink){alert('Please enter a valid website link.');return;}
  var entry={
    id:_editingSkillId||('sk_'+Date.now()),
    date:date,
    category:el('skCategory')&&el('skCategory').value||'Other',
    name:name,
    time:(el('skTime')&&el('skTime').value||'').trim(),
    resource:(el('skResource')&&el('skResource').value||'').trim(),
    link:safeLink,
    notes:(el('skNotes')&&el('skNotes').value||'').trim()
  };
  var entries=getSkillEntries();
  if(_editingSkillId){
    var idx=entries.findIndex(function(e){return e.id===_editingSkillId;});
    if(idx>=0)entries[idx]=entry;else entries.push(entry);
  } else {
    entries.push(entry);
  }
  saveSkillEntries(entries);
  _editingSkillId=null;
  var f=document.getElementById('addSkillForm');
  if(f)f.style.display='none';
  renderSkillLog();
  renderSkillSectionCharts();
  renderDailyCheckOverview();
  renderJournalSchedule();
}

function deleteSkillEntry(id,source){
  if(!confirm('Delete this skill session?'))return;
  if(source==='daily'){
    var hidden=JSON.parse(localStorage.getItem('hidden_skill_daily_ids')||'[]');
    if(!hidden.includes(id))hidden.push(id);
    localStorage.setItem('hidden_skill_daily_ids',JSON.stringify(hidden));
  } else {
    var entries=getSkillEntries().filter(function(e){return e.id!==id;});
    saveSkillEntries(entries);
  }
  renderSkillLog();
  renderSkillSectionCharts();
  renderDailyCheckOverview();
}

function convertAndEditDailySkill(id){
  var cache=window._skillEntryCache||{};
  var entry=cache[id];
  if(!entry){alert('Could not find skill entry.');return;}
  var newId='sk_'+Date.now()+'_'+Math.random().toString(36).slice(2,6);
  var newEntry={id:newId,date:entry.date||'',category:entry.category||'Other',name:entry.name||'',time:entry.time||'',resource:entry.resource||'',link:entry.link||'',notes:entry.notes||''};
  var entries=getSkillEntries();entries.push(newEntry);saveSkillEntries(entries);
  var hidden=JSON.parse(localStorage.getItem('hidden_skill_daily_ids')||'[]');
  if(!hidden.includes(id))hidden.push(id);
  localStorage.setItem('hidden_skill_daily_ids',JSON.stringify(hidden));
  renderSkillLog();
  renderSkillSectionCharts();
  editSkillEntry(newId);
}

function exportSkillLog(){
  var entries=getSkillEntries();
  if(!entries.length){alert('No skill entries to export.');return;}
  var a=document.createElement('a');
  a.href=URL.createObjectURL(new Blob([JSON.stringify(entries,null,2)],{type:'application/json'}));
  a.download='skill_log_'+new Date().toISOString().slice(0,10)+'.json';a.click();
}

function importSkillLog(ev){
  var file=ev.target.files[0];if(!file)return;
  var reader=new FileReader();
  reader.onload=function(e){
    try{
      var data=JSON.parse(e.target.result);
      if(!Array.isArray(data))throw new Error('Invalid format');
      var existing=getSkillEntries();
      var existIds=new Set(existing.map(function(x){return x.id;}));
      var added=0;
      data.forEach(function(entry){
        if(!existIds.has(entry.id)){existing.push(entry);added++;}
      });
      saveSkillEntries(existing);
      renderSkillLog();
      renderSkillSectionCharts();
      alert('Imported '+added+' new entries.');
    }catch(err){alert('Import failed: '+err.message);}
  };
  reader.readAsText(file);
  ev.target.value='';
}

var SKILL_CAT_COLORS={
  'Coding':'#2980b9','Udemy':'#7c3aed','Finance':'#27ae60','Language':'#8e44ad','Design':'#e67e22',
  'Writing':'#16a085','Music':'#d35400','Health':'#c0392b','Gun Range':'#8b5e3c',
  'Retirement Videos':'#b45309','Leadership':'#2c3e50','Other':'#7f8c8d'
};

function toggleSkillNotes(id){
  var el=document.getElementById(id);
  var chev=document.getElementById('sk_nc_'+id.replace('sk_n_',''));
  if(!el)return;
  var open=el.style.display!=='none';
  el.style.display=open?'none':'block';
  if(chev)chev.innerHTML=open?'&#x25B6;':'&#x25BC;';
}
function renderSkillLog(){
  var container=document.getElementById('skillLogContainer');
  if(!container)return;
  var sortEl=document.getElementById('skillSortOrder');
  var sort=sortEl?sortEl.value:'newest';

  // Merge: standalone entries + daily planner quick-logs
  var entries=getSkillEntries().map(function(e){return Object.assign({},e,{source:'log'});});
  // Also pull daily planner entries that have skill data
  for(var i=0;i<localStorage.length;i++){
    var k=localStorage.key(i);
    if(!k.startsWith('planner_'))continue;
    try{
      var d=JSON.parse(localStorage.getItem(k));
      if((d.gaSkill==='green'||d.gaSkillText)&&d.gaSkillText){
        var dateKey=k.replace('planner_','');
        // Don't duplicate if a log entry already exists for same date+name
        var exists=entries.some(function(e){return e.date===dateKey&&e.name===d.gaSkillText;});
        if(!exists)entries.push({id:'daily_'+dateKey,date:dateKey,category:'Other',name:d.gaSkillText||'',time:d.gaSkillTime||'',resource:'',notes:'',source:'daily'});
      }
    }catch(err){}
  }

  // Filter out suppressed daily entries
  var _hiddenIds=JSON.parse(localStorage.getItem('hidden_skill_daily_ids')||'[]');
  if(_hiddenIds.length)entries=entries.filter(function(e){return !_hiddenIds.includes(e.id);});

  window._skillEntryCache={};entries.forEach(function(e){window._skillEntryCache[e.id]=e;});
  // Update count
  var countEl=document.getElementById('skillCount');
  if(countEl)countEl.textContent=entries.length+' session'+(entries.length!==1?'s':'');

  if(!entries.length){
    container.innerHTML='<div style="text-align:center;color:#aaa;padding:40px;font-size:0.95rem;">No skill sessions logged yet.<br><span style="font-size:0.82rem;margin-top:6px;display:block;">Click <strong>+ Add Skill</strong> to log your first session.</span></div>';
    var sb=document.getElementById('skillStatsBar');if(sb)sb.style.display='none';
    return;
  }

  // Compute stats
  var totalMins=0,monthMins=0,catMins={};
  var nowMo=new Date().toISOString().slice(0,7);
  entries.forEach(function(e){
    var m=parseMins(e.time);totalMins+=m;
    if(e.date&&e.date.slice(0,7)===nowMo)monthMins+=m;
    var cat=e.category||'Other';
    catMins[cat]=(catMins[cat]||0)+m;
  });
  var topCat='—';var topMins=0;
  Object.keys(catMins).forEach(function(c){if(catMins[c]>topMins){topMins=catMins[c];topCat=c;}});
  var sb=document.getElementById('skillStatsBar');
  if(sb){
    sb.style.display='flex';
    var el=function(id){return document.getElementById(id);};
    if(el('skillStatSessions'))el('skillStatSessions').textContent=entries.length;
    if(el('skillStatHours'))el('skillStatHours').textContent=totalMins?fmtMins(totalMins):'0';
    if(el('skillStatMonth'))el('skillStatMonth').textContent=monthMins?fmtMins(monthMins):'0';
    if(el('skillStatTop'))el('skillStatTop').textContent=topCat;
  }

  // Sort
  if(sort==='newest')entries.sort(function(a,b){return b.date.localeCompare(a.date);});
  else if(sort==='oldest')entries.sort(function(a,b){return a.date.localeCompare(b.date);});
  else if(sort==='category')entries.sort(function(a,b){return (a.category||'').localeCompare(b.category||'')||b.date.localeCompare(a.date);});
  else if(sort==='time')entries.sort(function(a,b){return parseMins(b.time)-parseMins(a.time);});

  // Group by month (or category for category sort)
  var html='';
  if(sort==='category'){
    var cats={};
    entries.forEach(function(e){var c=e.category||'Other';if(!cats[c])cats[c]=[];cats[c].push(e);});
    Object.keys(cats).sort().forEach(function(cat){
      var cc=SKILL_CAT_COLORS[cat]||'#8e44ad';
      var catTotalMins=0;cats[cat].forEach(function(e){catTotalMins+=parseMins(e.time);});
      html+=renderSkillGroupHeader(cat,catTotalMins,cc);
      cats[cat].forEach(function(e){html+=renderSkillCard(e);});
    });
  } else {
    var months={};
    entries.forEach(function(e){var mo=(e.date||'').slice(0,7);if(!months[mo])months[mo]=[];months[mo].push(e);});
    var moKeys=Object.keys(months).sort();
    if(sort!=='oldest')moKeys=moKeys.reverse();
    moKeys.forEach(function(mo){
      var moDate=new Date(mo+'-01T00:00:00');
      var moLabel=moDate.toLocaleDateString('en-US',{month:'long',year:'numeric'});
      var moMins=0;months[mo].forEach(function(e){moMins+=parseMins(e.time);});
      html+=renderSkillGroupHeader(moLabel,moMins,'#8e44ad');
      months[mo].forEach(function(e){html+=renderSkillCard(e);});
    });
  }
  container.innerHTML=html;
}

function renderSkillGroupHeader(label,mins,color){
  return '<div style="margin-bottom:8px;margin-top:4px;padding:8px 12px;background:linear-gradient(135deg,#f5eef8,#fdfbff);border-left:3px solid '+color+';border-radius:0 8px 8px 0;display:flex;align-items:center;gap:10px;">'
    +'<span style="font-weight:700;color:#6c3483;font-size:0.9rem;">'+label+'</span>'
    +(mins?'<span style="font-size:0.78rem;color:#888;">'+fmtMins(mins)+'</span>':'')
    +'</div>';
}

function renderSkillCard(e){
  var cc=SKILL_CAT_COLORS[e.category]||'#8e44ad';
  var dateLabel=e.date?new Date(e.date+'T00:00:00').toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric'}):'';
  var timeStr=e.time?parseMins(e.time)?fmtMins(parseMins(e.time)):e.time:'';
  var srcBadge=e.source==='daily'?'<span style="font-size:0.68rem;background:#f0f0f0;color:#888;border-radius:4px;padding:1px 5px;margin-left:6px;">daily</span>':'';
  var editBtn=e.source==='daily'
    ?'<button onclick="event.stopPropagation();convertAndEditDailySkill(&quot;'+e.id+'&quot;)" style="background:none;border:1px solid #ddd;border-radius:5px;padding:3px 8px;font-size:0.75rem;cursor:pointer;color:#555;" title="Edit">&#x270F;&#xFE0F;</button>'
    :'<button onclick="event.stopPropagation();editSkillEntry(&quot;'+e.id+'&quot;)" style="background:none;border:1px solid #ddd;border-radius:5px;padding:3px 8px;font-size:0.75rem;cursor:pointer;color:#555;" title="Edit">&#x270F;&#xFE0F;</button>';
  var delBtn='<button onclick="event.stopPropagation();deleteSkillEntry(&quot;'+e.id+'&quot;,&quot;'+e.source+'&quot;)" style="background:none;border:1px solid #fcc;border-radius:5px;padding:3px 8px;font-size:0.75rem;cursor:pointer;color:#e74c3c;" title="Delete">&#x1F5D1;</button>';
  var linkUrl=skillSafeLink(e.link),hasBody=!!(timeStr||e.resource||linkUrl||e.notes);
  var cardId='skCard_'+e.id;
  var bodyId='skBody_'+e.id;
  var chevId='skChev_'+e.id;
  return '<div id="'+cardId+'" style="border:1px solid #e8daef;border-radius:10px;margin-bottom:8px;background:#fff;border-left:3px solid '+cc+';overflow:hidden;">'
    +'<div onclick="toggleSkillCard(&quot;'+e.id+'&quot;)" style="cursor:pointer;padding:12px 14px;display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;">'
    +'<div style="flex:1;min-width:0;">'
    +'<div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap;margin-bottom:3px;">'
    +'<span style="font-size:0.72rem;font-weight:700;color:'+cc+';background:'+cc+'18;border-radius:4px;padding:2px 7px;">'+e.category+'</span>'
    +'<span style="font-size:0.78rem;color:#aaa;">'+dateLabel+'</span>'
    +srcBadge+'</div>'
    +'<div style="font-weight:700;color:#2c3e50;font-size:0.95rem;">'+e.name+'</div>'
    +'</div>'
    +'<div style="display:flex;gap:5px;align-items:center;flex-shrink:0;">'
    +editBtn+delBtn
    +(hasBody?'<span id="'+chevId+'" style="color:#aaa;font-size:0.78rem;margin-left:2px;">&#x25B6;</span>':'')
    +'</div>'
    +'</div>'
    +(hasBody
      ?'<div id="'+bodyId+'" style="display:none;padding:0 14px 12px;border-top:1px solid #f0e8ff;">'
        +(timeStr?'<div style="font-size:0.82rem;color:#667eea;font-weight:600;margin-top:8px;">&#x23F1; '+timeStr+'</div>':'')
        +(e.resource?'<div style="font-size:0.78rem;color:#888;margin-top:4px;">&#x1F517; '+e.resource+'</div>':'')
        +(linkUrl?'<div style="font-size:0.78rem;margin-top:5px;"><a href="'+escHtml(linkUrl)+'" target="_blank" rel="noopener noreferrer" onclick="event.stopPropagation()" style="color:#6d28d9;font-weight:700;overflow-wrap:anywhere;">&#x1F517; Open saved link</a></div>':'')
        +(e.notes
          ?'<div style="margin-top:6px;">'
            +'<button onclick="event.stopPropagation();toggleSkillNotes(&quot;sk_n_'+e.id+'&quot;)" style="background:none;border:none;color:#8e44ad;font-size:0.72rem;cursor:pointer;padding:0;font-weight:600;margin-bottom:3px;">&#x1F4CB; Notes <span id="sk_nc_'+e.id+'">&#x25BC;</span></button>'
            +'<div id="sk_n_'+e.id+'" style="font-size:0.82rem;color:#555;margin-top:3px;white-space:pre-wrap;">'+escHtml(e.notes)+'</div>'
            +'</div>'
          :'')
        +'</div>'
      :'')
    +'</div>';
}

function skillSafeLink(value){
  var text=String(value||'').trim();if(!text)return'';if(!/^https?:\/\//i.test(text))text='https://'+text;
  try{var parsed=new URL(text);return(parsed.protocol==='http:'||parsed.protocol==='https:')?parsed.href:'';}catch(ignore){return'';}
}

function toggleSkillCard(id){
  var body=document.getElementById('skBody_'+id);
  var chev=document.getElementById('skChev_'+id);
  if(!body)return;
  var open=body.style.display!=='none';
  body.style.display=open?'none':'block';
  if(chev)chev.innerHTML=open?'&#x25B6;':'&#x25BC;';
}
function renderBibleTab(){
  const inp=document.getElementById('bibleDate');
  if(inp)inp.value=bibleDateKey(today);
  loadBibleEntry();
  renderBibleImages(getBibleDateInput());
  try{_initScriptureFocus();}catch(e){}
}

function openSermonLink(){
  const url=(document.getElementById('spSermonURL')||{}).value||'';
  if(!url){alert('Enter a sermon URL first.');return;}
  window.open(url,'_blank','noopener,noreferrer');
}
function openAskJournal(){
  const box=document.getElementById('askJournalBox');
  if(!box)return;
  var toolsBody=document.getElementById('aiToolsBody'),toolsToggle=document.getElementById('aiToolsToggle'),toolsArrow=document.getElementById('aiToolsArrow');
  if(toolsBody)toolsBody.style.display='block';
  if(toolsToggle)toolsToggle.setAttribute('aria-expanded','true');
  if(toolsArrow)toolsArrow.innerHTML='&#x25BC;';
  box.style.display='block';
  box.scrollIntoView({behavior:'smooth',block:'nearest'});
  setTimeout(function(){var input=document.getElementById('askJournalInput');if(input)input.focus();},50);
}
async function submitAskJournal(){
  const q=(document.getElementById('askJournalInput').value||'').trim();
  if(!q){alert('Enter a question first.');return;}
  var _ajEl=document.getElementById('askJournalResult'),_ajBtn=document.getElementById('askJournalSubmitBtn');
  if(_ajEl){_ajEl.style.display='block';_ajEl.textContent='Preparing your journal information...';}
  if(_ajBtn){_ajBtn.disabled=true;_ajBtn.textContent='Preparing...';}
  await new Promise(function(resolve){setTimeout(resolve,0);});
  // ── Gather ALL planner data (all time) ──
  const dayLines=[];
  const allPlannerKeys=[];
  for(let i=0;i<localStorage.length;i++){const k=localStorage.key(i);if(k&&k.startsWith('planner_'))allPlannerKeys.push(k);}
  allPlannerKeys.sort((a,b)=>b.localeCompare(a));
  for(let ki=0;ki<allPlannerKeys.length;ki++){
    const r=localStorage.getItem(allPlannerKeys[ki]);
    const _pDateKey=allPlannerKeys[ki].replace('planner_','');
    const d=new Date(_pDateKey+'T00:00:00');
    if(!r)continue;
    let dd;try{dd=JSON.parse(r);}catch(ignore){continue;}
    const label=d.toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric',year:'numeric'});
    const parts=[];
    // Spirituality
    if(dd.spBibleAudio==='green')parts.push('Bible Audio: ✓ completed');
    if(dd.spWorship==='green')parts.push('Worship: ✓ completed');
    if(dd.spDailyBreadText)parts.push('Daily Bread: '+dd.spDailyBreadText);
    if(dd.spMeditation==='green'||dd.spMeditationText)parts.push('Meditation: '+(dd.spMeditationText||'✓')+
      (dd.spMeditationVal?' ('+dd.spMeditationVal+' min)':''));
    if(dd.spAIPrayer==='green')parts.push('AI Prayer: ✓ completed');
    const _sermonNotes=getSermonNotes(_pDateKey);
    if(dd.spSermon==='green'||_sermonNotes)parts.push('Sermon: '+(_sermonNotes||'✓'));
    // Growth & Learning
    if(dd.gaAudiobook==='green'||dd.gaAudiobookText)parts.push('Audiobook: '+(dd.gaAudiobookText||'✓')+(dd.gaAudiobookTime?' ('+dd.gaAudiobookTime+')':''));
    if(dd.gaSkill==='green'||dd.gaSkillText)parts.push('Skill Builder: '+(dd.gaSkillText||'✓')+(dd.gaSkillTime?' ('+dd.gaSkillTime+')':''));
    // Wellness
    if(dd.wBPVal)parts.push('BP: '+dd.wBPVal+(dd.wPulseVal?' | Pulse: '+dd.wPulseVal:''));
    if(dd.wWghtVal)parts.push('Weight: '+dd.wWghtVal+' lbs');
    if(dd.wSleepVal)parts.push('Sleep: '+dd.wSleepVal+' hrs');
    if(dd.wMedsVal)parts.push('Meds: '+dd.wMedsVal);
    const waterAmt=parseFloat(dd.waterOz)||(parseFloat(dd.waterCount)*8||0);
    if(waterAmt)parts.push('Water: '+waterAmt+' oz');
    // Exercise
    if(dd.exRun==='green'||dd.exRunVal||dd.exMiles||hasActivityOnDate(_pDateKey)){
      var _runActs=getActivities().filter(function(a){return a.date===_pDateKey&&(a.type||'').toLowerCase().includes('run');});
      let run='Run: '+(_runActs.length?_runActs[0].title||_runActs[0].notes||'✓':(dd.exRunVal||'✓'));
      if(dd.exMiles)run+=' | '+dd.exMiles+' mi';
      if(dd.exPace)run+=' | '+dd.exPace+'/mi';
      if(dd.exTotalTime)run+=' | '+dd.exTotalTime+' min';
      parts.push(run);
    }
    if(dd.exStrength==='green'||dd.exStrengthVal)parts.push('Strength: '+(dd.exStrengthVal||'✓'));
    if(dd.exBike==='green'||dd.exBikeVal)parts.push('Bike: '+(dd.exBikeVal||'✓'));
    if(dd.exStretch==='green'||dd.exStretchVal)parts.push('Stretch: '+(dd.exStretchVal||'✓'));
    if(dd.exMassage==='green'||dd.exMassageVal||dd.exMassageMinutes)parts.push('Massage: '+[dd.exMassageMinutes?dd.exMassageMinutes+' min':'',dd.exMassageVal||''].filter(Boolean).join(' — ')||'✓');
    if(dd.exWalk==='green'||dd.exWalkVal)parts.push('Walk: '+(dd.exWalkVal||'✓'));
    // Priorities & Goals
    ['pt0','pt1','pt2'].forEach(function(k){if(dd[k+'t']){parts.push('Priority: '+dd[k+'t']+(dd[k+'c']==='green'?' ✓':''));}});
    ['dg0','dg1','dg2'].forEach(function(k){if(dd[k+'t']){parts.push('Daily Goal: '+dd[k+'t']+(dd[k+'c']==='green'?' ✓':''));}});
    // Journal
    if(dd.jRunLog)parts.push('Running Log: '+dd.jRunLog);
    if(dd.jAccomplish)parts.push('Accomplishments: '+dd.jAccomplish);
    if(dd.jImprov)parts.push('Improvements: '+dd.jImprov);
    if(dd.jNotes)parts.push('Notes: '+dd.jNotes);
    if(parts.length)dayLines.push('=== '+label+' ===\n'+parts.join('\n'));
  }
  // ── Gather Bible Notes tab (all time) ──
  const bibleLines=[];
  const allBibleKeys=[];
  for(let i=0;i<localStorage.length;i++){const k=localStorage.key(i);if(k&&k.startsWith('bible_'))allBibleKeys.push(k);}
  allBibleKeys.sort((a,b)=>b.localeCompare(a));
  for(let ki=0;ki<allBibleKeys.length;ki++){
    const br=localStorage.getItem(allBibleKeys[ki]);
    const d=new Date(allBibleKeys[ki].replace('bible_','')+'T00:00:00');
    if(!br)continue;
    let b;try{b=JSON.parse(br);}catch(ignore){continue;}
    const parts=[d.toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'})];
    if(b.sermonTitle)parts.push('Sermon Title: '+b.sermonTitle);
    if(b.scripture)parts.push('Scripture: '+b.scripture);
    if(b.theme)parts.push('Theme: '+b.theme);
    if(b.notes)parts.push('Notes: '+b.notes);
    if(b.application)parts.push('Application: '+b.application);
    if(b.prayer)parts.push('Prayer: '+b.prayer);
    if(parts.length>1)bibleLines.push(parts.join(' | '));
  }
  // ── Gather recent activities ──
  const recentActs=(window._tpGarminActs&&window._tpGarminActs.length?window._tpGarminActs:getActivities())
    .slice().sort(function(a,b){return String(b&&b.date||'').localeCompare(String(a&&a.date||''));});
  const actLines=recentActs.map(function(a){
    const p=[a.date,a.type||'Run'];
    if(a.distance)p.push(a.distance+'mi');
    if(a.pace)p.push(a.pace+'/mi');
    if(a.duration)p.push(fmtDuration(a.duration));
    if(a.heartRate)p.push(a.heartRate+' bpm');
    if(a.power)p.push(a.power+'W');
    if(a.elevGain)p.push(a.elevGain+'ft gain');
    if(a.notes)p.push(a.notes);
    if(a.injuryReport)p.push('Injury report: '+a.injuryReport);
    return p.join(' | ');
  });
  // ── Training plan ──
  const planRaw=localStorage.getItem('current_training_plan');
  let planText='';try{planText=planRaw?(JSON.parse(planRaw).text||''):'';}catch(ignore){}
  if(!dayLines.length&&!actLines.length&&!planText&&!bibleLines.length){
    const res=document.getElementById('askJournalResult');
    res.style.display='block';res.textContent='No journal data found. Start filling in your daily planner entries!';if(_ajBtn){_ajBtn.disabled=false;_ajBtn.textContent='Ask';}return;
  }
  let prompt='You are analyzing a comprehensive personal daily planner and journal. Answer the question below using only the data provided. Be specific, cite dates, and include relevant details from any section (spirituality, wellness, exercise, growth, journal notes, Bible study).\n\nQuestion: '+q+'\n\n';
  if(dayLines.length)prompt+='DAILY PLANNER ENTRIES (all time, newest first):\n'+dayLines.join('\n\n')+'\n\n';
  if(bibleLines.length)prompt+='BIBLE STUDY NOTES (all time):\n'+bibleLines.join('\n')+'\n\n';
  if(actLines.length)prompt+='RECORDED ACTIVITIES (all time):\n'+actLines.join('\n')+'\n\n';
  if(planText)prompt+='CURRENT TRAINING PLAN:\n'+planText;
  var _ajMsgs;
  if(_askJournalAttachment){
    var _ajContent=[{type:'text',text:prompt}];
    if(_askJournalAttachment.mime==='application/pdf'){
      _ajContent.unshift({type:'document',source:{type:'base64',media_type:'application/pdf',data:_askJournalAttachment.b64}});
    } else {
      _ajContent.unshift({type:'image',source:{type:'base64',media_type:_askJournalAttachment.mime,data:_askJournalAttachment.b64}});
    }
    _ajMsgs=[{role:'user',content:_ajContent}];
  } else {
    _ajMsgs=[{role:'user',content:prompt}];
  }
  _askJournalCtx=_ajMsgs.slice();
  var _ajEl=document.getElementById('askJournalResult');
  if(_ajEl){_ajEl.style.display='block';_ajEl.textContent='⏳ Thinking...';}
  var _ajKey=journalAIKey(),_ajBtn=document.getElementById('askJournalSubmitBtn');
  if(!_ajKey){if(_ajEl)_ajEl.textContent='Add your Anthropic API key in Settings to use Ask AI.';if(_ajBtn){_ajBtn.disabled=false;_ajBtn.textContent='Ask';}return;}
  if(_ajBtn){_ajBtn.disabled=true;_ajBtn.textContent='Asking...';}
  try{
    var data=await journalProtectedFunction({model:'claude-haiku-4-5-20251001',max_tokens:3000,messages:_ajMsgs});
    if(data.error)throw new Error(data.error.message||'The AI request could not be completed.');
    var reply=(data.content[0].text||'').trim();
    _askJournalCtx.push({role:'assistant',content:reply});
    aiShow('askJournalResult',reply,'Ask AI');
  }catch(e){if(_ajEl)_ajEl.textContent='Error: '+(e&&e.message?e.message:'Could not reach the protected AI service.');}
  finally{if(_ajBtn){_ajBtn.disabled=false;_ajBtn.textContent='Ask';}}
  // Show follow-up input
  setTimeout(function(){
    var fw=document.getElementById('askJournalFollowUpWrap');
    if(fw)fw.style.display='block';
  },300);
}

// ── Run Metrics Panel ────────────────────────────────────────
function toggleRunMetrics(btn){
  var panel=document.getElementById('runMetricsPanel');
  var chev=document.getElementById('metricsChev');
  if(!panel)return;
  var open=panel.style.display==='none';
  if(open){renderRunMetrics();panel.style.display='block';}
  else{panel.style.display='none';}
  if(chev)chev.innerHTML=open?'&#x25B2;':'&#x25BC;';
}
function renderRunMetrics(){
  var panel=document.getElementById('runMetricsPanel');
  if(!panel)return;
  var acts=getActivities();
  var runTypes=['run','running','trail run','trail running','treadmill','treadmill running','track running','road running','virtual run'];
  var runs=acts.filter(function(a){var t=(a.type||'').toLowerCase();return runTypes.some(function(r){return t===r||t.includes('run');});});
  if(!acts.length){panel.innerHTML='<div style="color:#aaa;text-align:center;">No activities recorded yet.</div>';return;}
  // Parse duration string "h:mm:ss" or "m:ss" to seconds
  function durSecs(d){
    if(!d)return 0;
    if(typeof d==='number')return d;
    var p=String(d).trim().split(':').map(Number);
    if(p.length===3)return p[0]*3600+p[1]*60+p[2];
    if(p.length===2)return p[0]*60+p[1];
    return 0;
  }
  function fmtSecs(s){var h=Math.floor(s/3600);var m=Math.floor((s%3600)/60);return h?h+'h '+(m?m+'m':''):m+'m';}
  function fmtDate(dt){return dt?new Date(dt+'T00:00:00').toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'}):'';}
  function fmtDateShort(dt){return dt?new Date(dt+'T00:00:00').toLocaleDateString('en-US',{month:'short',day:'numeric'}):''}
  function maxActivity(key){return acts.reduce(function(best,a){var value=parseFloat(a[key])||0;return value&&(!best||value>(parseFloat(best[key])||0))?a:best;},null);}
  var peakPowerAct=maxActivity('power'),mostElevAct=maxActivity('elevGain'),mostCalAct=maxActivity('calories');
  function recordSub(a){return a?((a.title?escHtml(a.title)+' &bull; ':'')+fmtDateShort(a.date)):'';}

  var totalMiles=0,totalDurSec=0,longRun=0,longRunTitle='',longDate='',bestPaceSec=0,bestPaceTitle='',bestPaceDate='',paceSum=0,paceCount=0;
  var runs10=[],totalElev=0,thisYearMiles=0,thisMonthMiles=0,hrSum=0,hrCount=0,cadenceSum=0,cadenceCount=0;
  var now=new Date();var thisYear=now.getFullYear();var thisMonth=now.getMonth();
  runs.forEach(function(a){
    var mi=parseFloat(a.distance)||0;
    totalMiles+=mi;
    totalDurSec+=durSecs(a.duration);
    totalElev+=parseFloat(a.elevGain)||0;
    if(a.heartRate){hrSum+=parseFloat(a.heartRate)||0;hrCount++;}
    if((parseFloat(a.cadence)||0)>0){cadenceSum+=parseFloat(a.cadence)||0;cadenceCount++;}
    if(mi>=10)runs10.push(a);
    if(mi>longRun){longRun=mi;longDate=a.date||'';longRunTitle=a.title||'';}
    if(a.pace){var ps=parsePace(a.pace);if(ps&&(!bestPaceSec||ps<bestPaceSec)){bestPaceSec=ps;bestPaceDate=a.date||'';bestPaceTitle=a.title||'';}}
    if(a.pace){var ps2=parsePace(a.pace);if(ps2){paceSum+=ps2;paceCount++;}}
    if(a.date){
      var d=new Date(a.date+'T00:00:00');
      if(d.getFullYear()===thisYear){thisYearMiles+=mi;if(d.getMonth()===thisMonth)thisMonthMiles+=mi;}
    }
  });
  // Sort runs10 by distance desc
  runs10.sort(function(a,b){return(b.distance||0)-(a.distance||0);});
  var avgPace=paceCount?paceSum/paceCount:0;
  var avgDist=runs.length?totalMiles/runs.length:0;
  var avgHR=hrCount?Math.round(hrSum/hrCount):0;
  var avgCadence=cadenceCount?Math.round(cadenceSum/cadenceCount):0;
  var mileageWeeks={},mileageMonths={};
  runs.forEach(function(a){
    var miles=parseFloat(a.distance)||0;if(!miles||!a.date)return;
    var runDate=new Date(a.date+'T00:00:00');if(isNaN(runDate.getTime()))return;
    var monday=new Date(runDate),daysFromMonday=(runDate.getDay()+6)%7;monday.setDate(runDate.getDate()-daysFromMonday);
    var weekKey=dk(monday);mileageWeeks[weekKey]=(mileageWeeks[weekKey]||0)+miles;
    var monthKey=runDate.getFullYear()+'-'+String(runDate.getMonth()+1).padStart(2,'0');mileageMonths[monthKey]=(mileageMonths[monthKey]||0)+miles;
  });
  function highestMileage(bucket){return Object.keys(bucket).reduce(function(best,key){return !best||bucket[key]>best.miles?{key:key,miles:bucket[key]}:best;},null);}
  var highestWeek=highestMileage(mileageWeeks),highestMonth=highestMileage(mileageMonths);
  function weekRangeLabel(key){if(!key)return'';var start=new Date(key+'T00:00:00'),end=new Date(start);end.setDate(start.getDate()+6);return fmtDateShort(dk(start))+' - '+fmtDateShort(dk(end));}
  function monthLabel(key){if(!key)return'';var parts=key.split('-'),date=new Date(Number(parts[0]),Number(parts[1])-1,1);return date.toLocaleDateString('en-US',{month:'long',year:'numeric'});}
  var aqiValues=acts.map(function(a){return Number(a.aqi);}).filter(function(value){return Number.isFinite(value)&&value>0;});
  var avgAQI=aqiValues.length?Math.round(aqiValues.reduce(function(sum,value){return sum+value;},0)/aqiValues.length):0;
  function aqiCategory(value){return value<=50?'Good':value<=100?'Moderate':value<=150?'Unhealthy for sensitive groups':value<=200?'Unhealthy':value<=300?'Very unhealthy':'Hazardous';}
  var humidityValues=acts.map(function(a){return Number(a.humidity);}).filter(function(value){return Number.isFinite(value)&&value>0;});
  var avgHumidity=humidityValues.length?Math.round(humidityValues.reduce(function(sum,value){return sum+value;},0)/humidityValues.length):0;
  // Air Power: % of running power spent overcoming air resistance (Stryd). "wind" was its first field name.
  var airValues=acts.map(function(a){return Number(a.airPower||a.wind);}).filter(function(value){return Number.isFinite(value)&&value>0;});
  var avgAir=airValues.length?Math.round(airValues.reduce(function(sum,value){return sum+value;},0)/airValues.length*10)/10:0;

  function metBox(icon,label,val,sub){
    return '<div style="background:#fff;border:1px solid #f5cba7;border-radius:8px;padding:10px 12px;min-width:130px;flex:1;">'
      +'<div style="font-size:0.7rem;color:#b8860b;font-weight:700;margin-bottom:3px;">'+icon+' '+label+'</div>'
      +'<div style="font-size:1.05rem;font-weight:700;color:#e67e22;">'+val+'</div>'
      +(sub?'<div style="font-size:0.72rem;color:#888;margin-top:2px;">'+sub+'</div>':'')
      +'</div>';
  }

  // Build runs≥10 list
  var runs10html='<div style="margin-top:12px;border-top:1px solid #f5cba7;padding-top:10px;">'
    +'<div style="font-weight:700;color:#e67e22;font-size:0.82rem;margin-bottom:6px;">&#x2B50; Runs ≥ 10 Miles ('+runs10.length+')</div>';
  if(runs10.length){
    runs10html+=runs10.map(function(a){
      var title=a.title||a.type||'Run';
      var dateStr=fmtDate(a.date);
      var pace=a.pace?'<span style="color:#888;"> &bull; '+a.pace+'/mi</span>':'';
      return '<div style="display:flex;justify-content:space-between;align-items:center;padding:5px 8px;margin-bottom:4px;background:#fff8f0;border-radius:6px;font-size:0.82rem;">'
        +'<span style="font-weight:600;color:#2c3e50;">'+escHtml(title)+'</span>'
        +'<span style="color:#e67e22;font-weight:700;white-space:nowrap;margin-left:8px;">'+parseFloat(a.distance).toFixed(2)+' mi'+pace+'</span>'
        +'<span style="color:#aaa;font-size:0.75rem;white-space:nowrap;margin-left:8px;">'+dateStr+'</span>'
        +'</div>';
    }).join('');
  } else {
    runs10html+='<div style="color:#aaa;font-size:0.82rem;">No runs ≥ 10 miles yet.</div>';
  }
  runs10html+='</div>';

  panel.innerHTML='<div style="font-weight:700;color:#e67e22;font-size:0.95rem;margin-bottom:10px;">&#x1F4CA; Run Metrics Summary</div>'
    +'<div style="display:flex;flex-wrap:wrap;gap:8px;">'
    +metBox('&#x1F3C3;','Total Runs',runs.length,'all time')
    +metBox('&#x1F4CF;','Total Miles',totalMiles.toFixed(1)+' mi','all time')
    +metBox('&#x1F3C6;','Longest Run',longRun.toFixed(2)+' mi',
        (longRunTitle?escHtml(longRunTitle)+' &bull; ':'')+fmtDate(longDate))
    +metBox('&#x26A1;','Best Pace',bestPaceSec?fmtPace(bestPaceSec)+'/mi':'-',
        bestPaceSec?((bestPaceTitle?escHtml(bestPaceTitle)+' &bull; ':'')+fmtDateShort(bestPaceDate)):'')
    +metBox('&#x23F1;','Avg Pace',avgPace?fmtPace(avgPace)+'/mi':'-','per run')
    +metBox('&#x1F4D0;','Avg Distance',avgDist.toFixed(2)+' mi','per run')
    +metBox('&#x1F9B5;','Average Cadence',avgCadence?avgCadence+' spm':'-',avgCadence?cadenceCount+' run'+(cadenceCount!==1?'s':'')+' averaged':'No cadence recorded yet')
    +metBox('&#x1F4A8;','Average Air Power',avgAir?avgAir+'%':'-',avgAir?'of running power &bull; '+airValues.length+' run'+(airValues.length!==1?'s':''):'No air power recorded yet')
    +metBox('&#x1F32C;&#xFE0F;','Average AQI',avgAQI||'-',avgAQI?aqiCategory(avgAQI)+' &bull; '+aqiValues.length+' recorded workout'+(aqiValues.length!==1?'s':''):'No AQI recorded yet')
    +metBox('&#x1F4A7;','Average Humidity',avgHumidity?avgHumidity+'%':'-',avgHumidity?humidityValues.length+' recorded workout'+(humidityValues.length!==1?'s':''):'No humidity recorded yet')
    +metBox('&#x23F3;','Total Time',totalDurSec?fmtSecs(totalDurSec):'-','all runs')
    +metBox('&#x1F4C5;','This Month',thisMonthMiles.toFixed(1)+' mi','')
    +metBox('&#x1F4C6;','This Year',thisYearMiles.toFixed(1)+' mi','')
    +metBox('&#x1F4C8;','Highest Mileage Week',highestWeek?highestWeek.miles.toFixed(1)+' mi':'-',highestWeek?weekRangeLabel(highestWeek.key):'No mileage recorded yet')
    +metBox('&#x1F5D3;&#xFE0F;','Highest Mileage Month',highestMonth?highestMonth.miles.toFixed(1)+' mi':'-',highestMonth?monthLabel(highestMonth.key):'No mileage recorded yet')
    +(peakPowerAct?metBox('&#x26A1;','Peak Average Watt',Math.round(parseFloat(peakPowerAct.power))+' W',recordSub(peakPowerAct)):'')
    +(mostElevAct?metBox('&#x26F0;','Most Elevation Gain',Math.round(parseFloat(mostElevAct.elevGain)).toLocaleString()+' ft',recordSub(mostElevAct)):'')
    +(mostCalAct?metBox('&#x1F525;','Most Calories Burned',Math.round(parseFloat(mostCalAct.calories)).toLocaleString()+' cal',recordSub(mostCalAct)):'')
    +(avgHR?metBox('&#x2764;','Avg Heart Rate',avgHR+' bpm',hrCount+' run'+(hrCount!==1?'s':'')+' averaged'):'')
    +(totalElev>0?metBox('&#x26F0;','Total Elevation',Math.round(totalElev).toLocaleString()+' ft','all runs'):'')
    +'</div>'
    +runs10html;
}

// ── Habits tracker ────────────────────────────────────────────────────
var h2ViewDate=new Date(),h2Filter='all',h2EditingId=null,h2OpenMonths={};
function h2State(){
  try{var parsed=JSON.parse(localStorage.getItem('habits2_state')||'{}');return{habits:Array.isArray(parsed.habits)?parsed.habits:[],logs:parsed.logs&&typeof parsed.logs==='object'?parsed.logs:{},dismissedAuto:Array.isArray(parsed.dismissedAuto)?parsed.dismissedAuto:[]};}catch(e){return{habits:[],logs:{},dismissedAuto:[]};}
}
function h2SaveState(state){try{localStorage.setItem('habits2_state',JSON.stringify(state));return true;}catch(e){alert('Habits 2 could not save because this browser storage is full. Use Backup, then remove older large transcripts or uploads.');return false;}}
function h2Key(date){return date.getFullYear()+'-'+String(date.getMonth()+1).padStart(2,'0')+'-'+String(date.getDate()).padStart(2,'0');}
function h2Date(key){var p=String(key).split('-').map(Number);return new Date(p[0],p[1]-1,p[2],12);}
function h2StartWeek(date){var d=new Date(date),day=d.getDay();d.setDate(d.getDate()-((day+6)%7));d.setHours(12,0,0,0);return d;}
function h2Log(state,id,dateKey){return(state.logs[dateKey]&&state.logs[dateKey][id])||{value:0,status:''};}
function h2IsComplete(habit,log){return log&&((log.status==='done')||Number(log.value)>=Number(habit.target||1));}
function h2WeekDone(state,habit,date){var start=h2StartWeek(date),count=0;for(var i=0;i<7;i++){var d=new Date(start);d.setDate(d.getDate()+i);if(h2IsComplete(habit,h2Log(state,habit.id,h2Key(d))))count++;}return count;}
// Directly correlate the "Complete Training" habit with the My Schedule card's
// own rest-day detection, so it doesn't ask for training on days the active
// Training Plan schedules no workout.
function journalIsRestDay(date){
  try{
    var key=h2Key(date);
    var hasRestDayActivity=(getActivities()||[]).some(function(a){return a&&a.date===key&&a.type==='Rest Day';});
    if(hasRestDayActivity)return true;
  }catch(eAct){}
  try{
    if(typeof bsPlanCovers==='function'&&bsPlanCovers(h2Key(date))){var bt=bsPlanText(h2Key(date));return !bt||/rest day|no workout scheduled/i.test(bt);}
    var raw=localStorage.getItem('current_training_plan'),plan=raw?JSON.parse(raw):null;
    if(!plan||!plan.text||!plan.startDate)return false;
    var text=extractWorkoutFromActivePlan(plan,date)||'';
    if(!text)return false;
    return /rest day|no workout scheduled/i.test(text);
  }catch(e){return false;}
}
function h2IsDue(habit,date,state){
  if(habit.archived)return false;
  var created=habit.created?new Date(habit.created):null;if(created&&date<new Date(created.getFullYear(),created.getMonth(),created.getDate()))return false;
  if(habit.schedule==='weekdays')return date.getDay()!==0&&date.getDay()!==6;
  if(habit.schedule==='custom'){
    if((habit.days||[]).indexOf(date.getDay())===-1)return false;
    if(habit.autoSource==='training'){
      var customDone=h2IsComplete(habit,h2Log(state,habit.id,h2Key(date)));
      if(!customDone&&journalIsRestDay(date))return false;
    }
    return true;
  }
  if(habit.schedule==='weekly'){
    var weekOK=h2WeekDone(state,habit,date)<Number(habit.weeklyTarget||3),isDone=h2IsComplete(habit,h2Log(state,habit.id,h2Key(date)));
    if(habit.autoSource==='training'&&!isDone&&journalIsRestDay(date))return false;
    return weekOK||isDone;
  }
  return true;
}
function h2ScheduleLabel(h){if(h.schedule==='weekdays')return'Weekdays';if(h.schedule==='custom')return(h.days||[]).map(function(d){return['Sun','Mon','Tue','Wed','Thu','Fri','Sat'][d];}).join(', ')||'Custom';if(h.schedule==='weekly')return(h.weeklyTarget||3)+' times/week';return'Every day';}
function h2TypeLabel(h){if(h.autoSource==='steps')return Number(h.target||10000).toLocaleString()+' steps';if(h.autoSource==='hydration')return Number(h.target||100)+' oz';if(h.autoSource==='sleep-routine')return'4 of 6 routine steps';if(h.type==='duration')return(h.target||1)+' min';if(h.type==='count')return(h.target||1)+' times';return'Done / not done';}
function h2ToggleForm(show,habit){
  var card=document.getElementById('h2FormCard');if(!card)return;card.style.display=show?'block':'none';if(!show){h2EditingId=null;return;}
  h2EditingId=habit&&habit.id||null;document.getElementById('h2FormTitle').textContent=h2EditingId?'Edit Habit':'Create a Habit';
  document.getElementById('h2Name').value=habit&&habit.name||'';document.getElementById('h2Category').value=habit&&habit.category||'Health';document.getElementById('h2Color').value=habit&&habit.color||'#0a3161';document.getElementById('h2Type').value=habit&&habit.type||'check';document.getElementById('h2Target').value=habit&&habit.target||1;document.getElementById('h2Schedule').value=habit&&habit.schedule||'daily';document.getElementById('h2WeeklyTarget').value=habit&&habit.weeklyTarget||3;document.getElementById('h2Why').value=habit&&habit.why||'';document.getElementById('h2Cue').value=habit&&habit.cue||'';document.getElementById('h2Minimum').value=habit&&habit.minimum||'';
  h2BuildDays(habit&&habit.days||[1,2,3,4,5]);h2UpdateFormFields();card.scrollIntoView({behavior:'smooth',block:'start'});
}
function h2BuildDays(selected){var el=document.getElementById('h2DayPicker');if(!el)return;el.innerHTML=['S','M','T','W','T','F','S'].map(function(n,i){return'<button type="button" data-day="'+i+'" class="'+(selected.indexOf(i)!==-1?'active':'')+'" onclick="this.classList.toggle(\'active\')">'+n+'</button>';}).join('');}
function h2UpdateFormFields(){var type=document.getElementById('h2Type').value,schedule=document.getElementById('h2Schedule').value;document.getElementById('h2Target').parentElement.style.display=type==='check'?'none':'block';document.getElementById('h2DayPicker').style.display=schedule==='custom'?'flex':'none';document.getElementById('h2WeeklyTargetWrap').style.display=schedule==='weekly'?'block':'none';}
function h2SaveHabit(){
  var name=document.getElementById('h2Name').value.trim();if(!name){alert('Please enter a habit name.');return;}
  var state=h2State(),days=Array.from(document.querySelectorAll('#h2DayPicker button.active')).map(function(b){return Number(b.dataset.day);});
  var habit={id:h2EditingId||('h2-'+Date.now()),name:name,category:document.getElementById('h2Category').value,color:document.getElementById('h2Color').value,type:document.getElementById('h2Type').value,target:Math.max(1,Number(document.getElementById('h2Target').value)||1),schedule:document.getElementById('h2Schedule').value,weeklyTarget:Math.max(1,Math.min(7,Number(document.getElementById('h2WeeklyTarget').value)||3)),days:days,why:document.getElementById('h2Why').value.trim(),cue:document.getElementById('h2Cue').value.trim(),minimum:document.getElementById('h2Minimum').value.trim(),created:new Date().toISOString()};
  if(h2EditingId){var index=state.habits.findIndex(function(h){return h.id===h2EditingId;});if(index>=0){habit.created=state.habits[index].created||habit.created;habit.autoSource=state.habits[index].autoSource||'';state.habits[index]=habit;}}else state.habits.push(habit);
  if(h2SaveState(state)){h2ToggleForm(false);h2Render();}
}
function h2Edit(id){var habit=h2State().habits.find(function(h){return h.id===id;});if(habit)h2ToggleForm(true,habit);}
function h2Delete(id){if(!confirm('Delete this habit and its tracked history?'))return;var state=h2State(),removed=state.habits.find(function(h){return h.id===id;});if(removed&&removed.autoSource&&state.dismissedAuto.indexOf(removed.autoSource)===-1)state.dismissedAuto.push(removed.autoSource);state.habits=state.habits.filter(function(h){return h.id!==id;});Object.keys(state.logs).forEach(function(k){delete state.logs[k][id];if(!Object.keys(state.logs[k]).length)delete state.logs[k];});if(h2SaveState(state))h2Render();}
function h2SetLog(id,status,value){var state=h2State(),key=h2Key(h2ViewDate),habit=state.habits.find(function(h){return h.id===id;});if(!habit)return;if(!state.logs[key])state.logs[key]={};var old=h2Log(state,id,key);if(status&&old.status===status&&habit.type==='check'){delete state.logs[key][id];}else state.logs[key][id]={status:status||'',value:value==null?(status==='done'?Number(habit.target||1):Number(old.value||0)):Math.max(0,value),at:new Date().toISOString()};if(!Object.keys(state.logs[key]).length)delete state.logs[key];if(h2SaveState(state))h2Render();}
function h2Adjust(id,delta){var state=h2State(),habit=state.habits.find(function(h){return h.id===id;});if(!habit)return;var key=h2Key(h2ViewDate),old=h2Log(state,id,key),value=Math.max(0,Number(old.value||0)+delta),status=value>=Number(habit.target||1)?'done':value>0?'partial':'';h2SetLog(id,status,value);}
function h2ShiftDate(days){h2ViewDate.setDate(h2ViewDate.getDate()+days);h2Render();}
function h2GoToday(){h2ViewDate=new Date();h2Render();}
function h2SetFilter(filter,btn){h2Filter=filter;document.querySelectorAll('#h2Filters button').forEach(function(b){b.classList.toggle('active',b===btn);});h2Render();}
var h2SuggestionCache=[];
function h2SyncFromJournal(){
  var state=h2State(),before=JSON.stringify(state),todayDate=new Date(),todayKey=h2Key(todayDate),evidence={},evidenceValues={},firstSeen={};
  var sources=[
    {id:'bible',name:'Bible Reading',category:'Faith',color:'#8b5e34',schedule:'daily',weeklyTarget:7,why:'Stay grounded in Scripture.'},
    {id:'prayer',name:'Prayer',category:'Faith',color:'#6d28d9',schedule:'daily',weeklyTarget:7,why:'Make time for prayer and reflection.'},
    {id:'meditation',name:'Meditation',category:'Faith',color:'#7c3aed',schedule:'daily',weeklyTarget:7,why:'Practice intentional quiet and reflection.'},
    {id:'hydration',name:'Reach 64 oz Water',category:'Health',color:'#0284c7',schedule:'daily',weeklyTarget:7,why:'Support endurance, recovery, and long-distance running with consistent hydration.'},
    {id:'nutrition',name:'Log Meals & Nutrition',category:'Health',color:'#16a34a',schedule:'daily',weeklyTarget:7,why:'Build awareness of daily nutrition.'},
    {id:'stretch',name:'Daily Stretching',category:'Health',color:'#0d9488',schedule:'daily',weeklyTarget:7,why:'Keep muscles loose and prevent injury.'},
    {id:'training',name:'Follow Training Plan',category:'Fitness',color:'#4f46e5',schedule:'weekly',weeklyTarget:4,why:'Follow the active training plan.'},
    {id:'steps',name:'Reach 10,000 Steps',category:'Fitness',color:'#ea580c',schedule:'daily',weeklyTarget:7,why:'Build consistent daily movement by reaching 10,000 steps.'},
    {id:'library',name:'Read or Listen Daily',category:'Growth',color:'#2563eb',schedule:'daily',weeklyTarget:7,why:'Learn from reading or audiobooks every day.'},
    {id:'language',name:'Language Lessons',category:'Growth',color:'#0891b2',schedule:'weekdays',weeklyTarget:5,why:'Complete language lessons five days each week.'},
    {id:'udemy',name:'Complete a Udemy Lesson',category:'Growth',color:'#7c3aed',schedule:'custom',weeklyTarget:3,why:'Build consistent learning through current and future Udemy courses.'},
    {id:'retirement-videos',name:'Watch Financial Videos',category:'Growth',color:'#b45309',schedule:'custom',weeklyTarget:3,why:'Build financial knowledge through focused video learning.'},
    {id:'evening-planning',name:'Evening Planning',category:'Home',color:'#4f46e5',schedule:'daily',weeklyTarget:7,why:'Close the day with clear priorities for tomorrow.'},
    {id:'sleep-routine',name:'Sleep Routine',category:'Health',color:'#7c3aed',schedule:'daily',weeklyTarget:7,why:'Protect recovery with a consistent evening wind-down.'},
    {id:'daily-reflection',name:'Daily Reflection',category:'Growth',color:'#a855f7',schedule:'daily',weeklyTarget:7,why:'Reflect on accomplishments, gratitude, and lessons from the day.'}
  ];
  // Build the evidence/firstSeen picture BEFORE any habit gets created or backdated below,
  // so one-time creation migrations can stamp a realistic "created" date instead of "now".
  sources.forEach(function(s){evidence[s.id]={};evidenceValues[s.id]={};});
  function mark(source,key){if(!key||key>todayKey)return;evidence[source][key]=true;if(!firstSeen[source]||key<firstSeen[source])firstSeen[source]=key;}
  var activityDates={};
  try{(getActivities()||[]).forEach(function(a){if(a&&a.date&&a.type!=='Rest Day'){activityDates[a.date]=true;mark('training',a.date);}});}catch(ignore){}
  var skills=[];try{skills=getSkillEntries()||[];}catch(ignore2){}
  skills.forEach(function(entry){var key=entry&&entry.date,cat=String(entry&&entry.category||'').toLowerCase(),search=String([entry&&entry.name,entry&&entry.resource,entry&&entry.notes].filter(Boolean).join(' ')).toLowerCase();if(!key)return;if(cat==='language')mark('language',key);if(cat==='udemy'||/\budemy\b/.test(search)){var udemyMins=parseSkillTimeMins(entry.time);if(udemyMins>0){mark('udemy',key);evidenceValues.udemy[key]=Number(evidenceValues.udemy[key]||0)+udemyMins;}}if(cat==='retirement videos'||cat==='financial videos')mark('retirement-videos',key);});
  var scheduleClearedBySource={};
  function scheduleSourceFromId(id){id=String(id||'').toLowerCase();if(/prepare-priorities-for-tomorrow/.test(id))return'evening-planning';if(/stretch/.test(id))return'stretch';if(/financial-videos/.test(id))return'retirement-videos';if(/udemy/.test(id))return'udemy';if(/audiobook|reading-audiobook/.test(id))return'library';if(/language/.test(id))return'language';if(/today.?s-training|todays-training/.test(id))return'training';return'';}
  for(var offset=89;offset>=0;offset--){
    var date=new Date(todayDate);date.setDate(todayDate.getDate()-offset);var key=h2Key(date),row={};
    try{row=JSON.parse(localStorage.getItem('planner_'+key)||'{}')||{};}catch(ignore4){}
    if(row.spBibleAudio==='green')mark('bible',key);
    if(row.spAIPrayer==='green')mark('prayer',key);
    if(row.spMeditation==='green')mark('meditation',key);
    var dailyWater=Math.max(0,parseFloat(row.waterOz)||parseFloat(row.waterCount)*8||0);if(dailyWater>0){mark('hydration',key);evidenceValues.hydration[key]=dailyWater;}
    if((Array.isArray(row.foodLog)&&row.foodLog.length)||parseFloat(row.nuCalVal)>0||parseFloat(row.nuProtVal)>0)mark('nutrition',key);
    if(row.exStretch==='green')mark('stretch',key);
    var dailySteps=Math.max(0,parseFloat(row.exSteps)||0);if(dailySteps>0){mark('steps',key);evidenceValues.steps[key]=dailySteps;}
    if(row.gaAudiobook==='green'||parseTimedMins(row.gaAudiobookTime)>0||String(row.gaAudiobookText||'').trim())mark('library',key);
    if(row.gaFinancialVideos==='green'||parseFloat(row.gaFinancialVideosTime)>0)mark('retirement-videos',key);
    var savedScheduleStatuses={};try{savedScheduleStatuses=JSON.parse(localStorage.getItem('journal_schedule_status_'+key)||'{}')||{};}catch(ignoreEveningStatus){}
    Object.keys(savedScheduleStatuses).forEach(function(statusId){var sourceId=scheduleSourceFromId(statusId),status=savedScheduleStatuses[statusId];if(sourceId&&(status==='red'||status==='blank'))scheduleClearedBySource[sourceId+'|'+key]=true;});
    var eveningScheduleGreen=Object.keys(savedScheduleStatuses).some(function(statusId){return savedScheduleStatuses[statusId]==='green'&&(statusId==='schedule-prepare-priorities-for-tomorrow'||/prepare-priorities-for-tomorrow/.test(statusId));});
    if(row.prepJournalCheck||localStorage.getItem('schedule_eveningPlanning_'+key)==='1'||eveningScheduleGreen)mark('evening-planning',key);
    var sleepProgress=journalSleepRoutineState(key);if(sleepProgress.count>0){mark('sleep-routine',key);evidenceValues['sleep-routine'][key]=sleepProgress.count;}
    if(['jAccomplish','jImprov','jGratitude','jNotes','jCombined'].some(function(field){return String(row[field]||'').trim();}))mark('daily-reflection',key);
  }
  // Thinking section removed — drop any "Thinking Practice" habit card left over from before.
  if(!localStorage.getItem('habits2_thinking_removed_v1')){
    state.habits=state.habits.filter(function(h){return h.autoSource!=='thinking';});
    state.dismissedAuto=(state.dismissedAuto||[]).filter(function(id){return id!=='thinking';});
    localStorage.setItem('habits2_thinking_removed_v1','1');
  }
  // Financial Videos section removed — drop any "Financial Video Learning" habit card left over from before.
  if(!localStorage.getItem('habits2_financial_video_removed_v1')){
    state.habits=state.habits.filter(function(h){return h.autoSource!=='financial-video';});
    state.dismissedAuto=(state.dismissedAuto||[]).filter(function(id){return id!=='financial-video';});
    localStorage.setItem('habits2_financial_video_removed_v1','1');
  }
  // Coding does not match the user's current work, so remove the auto-created Coding habit.
  // Historical learning entries remain stored; this only removes the habit card.
  if(!localStorage.getItem('habits2_coding_removed_v1')){
    state.habits=state.habits.filter(function(h){return h.autoSource!=='coding'&&!/^(practice )?coding( \/ tech)?$/i.test(String(h.name||'').trim());});
    state.dismissedAuto=(state.dismissedAuto||[]).filter(function(id){return id!=='coding';});
    localStorage.setItem('habits2_coding_removed_v1','1');
  }
  // Udemy, Financial Videos, and Read-or-Listen habits removed per user request — drop any
  // existing habit cards for them and add them to dismissedAuto so they never get suggested
  // or auto-created again (unlike the removals above, these use the SAME autoSource ids the
  // auto-creation logic below still checks, so a plain filter alone would let them come right
  // back on the next sync).
  if(!localStorage.getItem('habits2_udemy_financial_library_removed_v1')){
    state.habits=state.habits.filter(function(h){return['udemy','retirement-videos','library'].indexOf(h.autoSource)===-1;});
    state.dismissedAuto=state.dismissedAuto||[];
    ['udemy','retirement-videos','library'].forEach(function(id){if(state.dismissedAuto.indexOf(id)===-1)state.dismissedAuto.push(id);});
    localStorage.setItem('habits2_udemy_financial_library_removed_v1','1');
  }
  // Daily Stretching and Daily Reflection removed per user request too — same pattern, but a
  // separate one-time flag so this still runs even if the migration above already has, and vice
  // versa (each batch of removals only needs to happen once, independent of the others).
  if(!localStorage.getItem('habits2_stretch_reflection_removed_v1')){
    state.habits=state.habits.filter(function(h){return['stretch','daily-reflection'].indexOf(h.autoSource)===-1;});
    state.dismissedAuto=state.dismissedAuto||[];
    ['stretch','daily-reflection'].forEach(function(id){if(state.dismissedAuto.indexOf(id)===-1)state.dismissedAuto.push(id);});
    localStorage.setItem('habits2_stretch_reflection_removed_v1','1');
  }
  if(!localStorage.getItem('habits2_language_lessons_v1')){
    var languageHabit=state.habits.find(function(h){return h.autoSource==='language'||/^(language learning|language lessons)$/i.test(String(h.name||'').trim());});
    if(languageHabit){languageHabit.name='Language Lessons';languageHabit.category='Growth';languageHabit.color=languageHabit.color||'#0891b2';languageHabit.type='check';languageHabit.target=1;languageHabit.schedule='weekdays';languageHabit.weeklyTarget=5;languageHabit.days=[1,2,3,4,5];languageHabit.why=languageHabit.why||'Complete language lessons five days each week.';languageHabit.autoSource=languageHabit.autoSource||'language';}
    else{state.habits.push({id:'h2-language-lessons',name:'Language Lessons',category:'Growth',color:'#0891b2',type:'check',target:1,schedule:'weekdays',weeklyTarget:5,days:[1,2,3,4,5],why:'Complete language lessons five days each week.',cue:'',minimum:'Complete one lesson',created:(firstSeen.language||todayKey)+'T12:00:00',autoSource:'language'});}
    state.dismissedAuto=(state.dismissedAuto||[]).filter(function(id){return id!=='language';});
    localStorage.setItem('habits2_language_lessons_v1','1');
  }
  // "Language Lessons" removed from Habits per user request (for now) — same pattern as
  // Hydration above: drop any existing habit card and record 'language' in dismissedAuto so
  // the self-heal and suggestion logic below (which both skip any sourceId already in
  // dismissedAuto) never brings it back. This supersedes the habits2_language_lessons_v1
  // block just above, which un-dismisses/creates this habit unconditionally the first time
  // it runs — that flag is already set on existing installs, so this new block is what
  // actually removes the habit going forward.
  if(!localStorage.getItem('habits2_language_removed_v1')){
    state.habits=state.habits.filter(function(h){return h.autoSource!=='language';});
    state.dismissedAuto=state.dismissedAuto||[];
    if(state.dismissedAuto.indexOf('language')===-1)state.dismissedAuto.push('language');
    localStorage.setItem('habits2_language_removed_v1','1');
  }
  ['evening-planning','sleep-routine','daily-reflection'].forEach(function(sourceId){
    if(state.habits.some(function(h){return h.autoSource===sourceId;})||(state.dismissedAuto||[]).indexOf(sourceId)!==-1)return;
    var source=sources.find(function(item){return item.id===sourceId;});if(!source)return;
    state.habits.push({id:'h2-auto-'+sourceId,name:source.name,category:source.category,color:source.color,type:'check',target:1,schedule:'daily',weeklyTarget:7,days:[0,1,2,3,4,5,6],why:source.why,cue:'',minimum:'',created:(firstSeen[sourceId]||todayKey)+'T12:00:00',autoSource:sourceId});
    state.dismissedAuto=(state.dismissedAuto||[]).filter(function(id){return id!==sourceId;});
  });
  if(!localStorage.getItem('habits2_training_color_v1')){
    var trainingHabit=state.habits.find(function(h){return h.autoSource==='training';});
    if(trainingHabit&&trainingHabit.color==='#dc2626')trainingHabit.color='#4f46e5';
    localStorage.setItem('habits2_training_color_v1','1');
  }
  // Self-heal every auto-linked habit on every sync (not just once) — if a habit's
  // autoSource is ever lost (e.g. an older cloud-sync snapshot overwriting local
  // data, or the habit was created manually before auto-linking existed), find it
  // again by name instead of leaving it stuck "not linked" forever. This previously
  // only covered Stretching; generalized so Training/Coding/etc. self-heal too.
  function normHabitName(value){return String(value||'').toLowerCase().replace(/[^a-z0-9]+/g,'').trim();}
  sources.forEach(function(source){
    if(state.habits.some(function(h){return h.autoSource===source.id;}))return;
    var match=state.habits.find(function(h){return !h.autoSource&&normHabitName(h.name)===normHabitName(source.name);});
    if(match){match.autoSource=source.id;state.dismissedAuto=(state.dismissedAuto||[]).filter(function(id){return id!==source.id;});}
  });
  // "Reach 64 oz Water" removed per user request — same pattern as Stretching/Reflection
  // above: drop any existing habit card and record 'hydration' in dismissedAuto so the
  // self-heal and suggestion logic below (which both skip any sourceId already in
  // dismissedAuto) never brings it back. This block used to force-create the habit here
  // unconditionally on every sync (guarded only by a one-time flag, not dismissedAuto), so
  // it has to stay deleted rather than just gated, or a browser that never set that flag
  // (e.g. a fresh install) would bring the habit right back.
  if(!localStorage.getItem('habits2_hydration_removed_v1')){
    state.habits=state.habits.filter(function(h){return h.autoSource!=='hydration';});
    state.dismissedAuto=state.dismissedAuto||[];
    if(state.dismissedAuto.indexOf('hydration')===-1)state.dismissedAuto.push('hydration');
    localStorage.setItem('habits2_hydration_removed_v1','1');
  }
  // "Log Meals & Nutrition" removed — Nutrition moved to a different app. Same pattern
  // as the hydration removal above: drop any existing habit card and record 'nutrition'
  // in dismissedAuto so it never gets recreated.
  if(!localStorage.getItem('habits2_nutrition_removed_v1')){
    state.habits=state.habits.filter(function(h){return h.autoSource!=='nutrition';});
    state.dismissedAuto=state.dismissedAuto||[];
    if(state.dismissedAuto.indexOf('nutrition')===-1)state.dismissedAuto.push('nutrition');
    localStorage.setItem('habits2_nutrition_removed_v1','1');
  }
  // "Follow Training Plan" removed per user request — same pattern as hydration/nutrition
  // above: drop any existing habit card and record 'training' in dismissedAuto so the
  // self-heal and suggestion logic below (which both skip any sourceId already in
  // dismissedAuto) never brings it back. Historical logs/streak data for this habit are
  // left in storage untouched, same as every other habit removal here.
  if(!localStorage.getItem('habits2_training_removed_v1')){
    state.habits=state.habits.filter(function(h){return h.autoSource!=='training';});
    state.dismissedAuto=state.dismissedAuto||[];
    if(state.dismissedAuto.indexOf('training')===-1)state.dismissedAuto.push('training');
    localStorage.setItem('habits2_training_removed_v1','1');
  }
  var habitRenameMap={hydration:'Reach 64 oz Water',nutrition:'Log Meals & Nutrition',training:'Follow Training Plan',steps:'Reach 10,000 Steps',library:'Read or Listen Daily',udemy:'Complete a Udemy Lesson','retirement-videos':'Watch Financial Videos'};
  state.habits.forEach(function(h){if(habitRenameMap[h.autoSource])h.name=habitRenameMap[h.autoSource];if(h.autoSource==='hydration'){h.type='count';h.target=64;h.schedule='daily';h.weeklyTarget=7;h.category='Health';h.why='Support endurance, recovery, and long-distance running with consistent hydration.';}if(h.autoSource==='steps'){h.type='count';h.target=10000;h.schedule='daily';h.weeklyTarget=7;h.category='Fitness';h.why='Build consistent daily movement by reaching 10,000 steps.';}if(h.autoSource==='udemy'){h.type='duration';h.target=20;h.category='Growth';h.why='Build consistent learning through current and future Udemy courses.';h.minimum='Complete one lesson or log at least 20 minutes';}if(h.autoSource==='retirement-videos'){h.category='Growth';h.why='Build financial knowledge through focused video learning.';}});
  if(!localStorage.getItem('habits2_udemy_tts_v1')){
    var scheduledUdemyHabit=state.habits.find(function(h){return h.autoSource==='udemy'||/udemy/i.test(String(h.name||''));});
    if(scheduledUdemyHabit){scheduledUdemyHabit.schedule='custom';scheduledUdemyHabit.days=[2,4,6];scheduledUdemyHabit.weeklyTarget=3;}
    localStorage.setItem('habits2_udemy_tts_v1','1');
  }
  if(!localStorage.getItem('habits2_financial_mwf_v1')){
    var financialHabit=state.habits.find(function(h){return h.autoSource==='retirement-videos'||/financial\s+videos?/i.test(String(h.name||''));});
    if(financialHabit){financialHabit.schedule='custom';financialHabit.days=[1,3,5];financialHabit.weeklyTarget=3;}
    localStorage.setItem('habits2_financial_mwf_v1','1');
  }
  // Udemy removed per user request (see habits2_udemy_financial_library_removed_v1 above) — this
  // used to force-create the habit unconditionally on every sync, ignoring dismissedAuto, so it
  // has to stay deleted rather than just gated, or it would come right back.
  // "Financial Videos" also gets auto-created if it doesn't exist anywhere yet (once only), so it
  // shows up right away instead of waiting on 3+ days of logged evidence like a normal suggestion.
  // (Now removed per user request too — the dismissedAuto entry set above is enough to suppress
  // this one, since it already respects dismissedAuto.)
  if(!state.habits.some(function(h){return h.autoSource==='retirement-videos';})&&(state.dismissedAuto||[]).indexOf('retirement-videos')===-1){
    state.habits.push({id:'h2-auto-retirement-videos',name:'Watch Financial Videos',category:'Growth',color:'#b45309',type:'check',target:1,schedule:'custom',weeklyTarget:3,days:[1,3,5],why:'Build financial knowledge through focused video learning.',cue:'',minimum:'Watch one financial video',created:(firstSeen['retirement-videos']||todayKey)+'T12:00:00',autoSource:'retirement-videos'});
    state.dismissedAuto=(state.dismissedAuto||[]).filter(function(id){return id!=='retirement-videos';});
  }
  localStorage.setItem('habits2_retirement_videos_link_v1','1');
  // Self-healing "created" date correction — any auto-linked habit whose recorded creation
  // date is later than the earliest real evidence for its source gets backdated. This fixes
  // habits (like the ones above) that were originally stamped with "now" instead of reflecting
  // when the tracked behavior actually started, which was silently undercounting streaks and
  // consistency by excluding real, already-logged completions from the stats math.
  state.habits.forEach(function(h){
    if(!h.autoSource||!firstSeen[h.autoSource])return;
    var createdKey=h.created?h2Key(new Date(h.created)):null;
    if(!createdKey||firstSeen[h.autoSource]<createdKey)h.created=firstSeen[h.autoSource]+'T12:00:00';
  });
  var dismissed=state.dismissedAuto||[],manualNames={};
  function norm(value){return String(value||'').toLowerCase().replace(/[^a-z0-9]+/g,'').trim();}
  state.habits.forEach(function(h){if(!h.autoSource)manualNames[norm(h.name)]=true;});
  h2SuggestionCache=[];
  sources.forEach(function(source){
    if(dismissed.indexOf(source.id)!==-1)return;
    var habit=state.habits.find(function(h){return h.autoSource===source.id;});
    var evidenceDays=Object.keys(evidence[source.id]).length;
    if(!habit&&evidenceDays<3)return;
    if(!habit&&manualNames[norm(source.name)])return;
    if(!habit){
      h2SuggestionCache.push({source:source,evidence:evidence[source.id],firstSeen:firstSeen[source.id],days:evidenceDays});return;
    }
    for(var i=89;i>=0;i--){
      var logDate=new Date(todayDate);logDate.setDate(todayDate.getDate()-i);var logKey=h2Key(logDate),has=!!evidence[source.id][logKey];
      if(!state.logs[logKey]&&has)state.logs[logKey]={};
      var old=state.logs[logKey]&&state.logs[logKey][habit.id];
      if(has&&(!old||old.autoSource===source.id)){
        var trackedValue=source.id==='steps'?Number(evidenceValues.steps[logKey]||0):source.id==='hydration'?Number(evidenceValues.hydration[logKey]||0):source.id==='sleep-routine'?Number(evidenceValues['sleep-routine'][logKey]||0):source.id==='udemy'?Number(evidenceValues.udemy[logKey]||0):1;
        var trackedStatus=source.id==='steps'?(trackedValue>=10000?'done':'partial'):source.id==='hydration'?(trackedValue>=100?'done':'partial'):source.id==='sleep-routine'?(trackedValue>=4?'done':'partial'):source.id==='udemy'?(trackedValue>=20?'done':'partial'):'done';
        state.logs[logKey][habit.id]={status:trackedStatus,value:trackedValue,at:logKey+'T12:00:00',autoSource:source.id};
      }
      else if(!has&&old&&(old.autoSource===source.id||scheduleClearedBySource[source.id+'|'+logKey])){delete state.logs[logKey][habit.id];if(!Object.keys(state.logs[logKey]).length)delete state.logs[logKey];}
    }
  });
  if(JSON.stringify(state)!==before)h2SaveState(state);
}
function h2ApproveSuggestion(sourceId){
  var pending=h2SuggestionCache.find(function(item){return item.source.id===sourceId;});if(!pending)return;
  var state=h2State(),source=pending.source;if(state.habits.some(function(h){return h.autoSource===sourceId;}))return;
  state.habits.push({id:'h2-auto-'+source.id,name:source.name,category:source.category,color:source.color,type:'check',target:1,schedule:source.schedule,weeklyTarget:source.weeklyTarget,days:[1,2,3,4,5],why:source.why,cue:'',minimum:'',created:(pending.firstSeen||h2Key(new Date()))+'T12:00:00',autoSource:source.id});
  if(h2SaveState(state))h2Render();
}
function h2DismissSuggestion(sourceId){
  var state=h2State();if(state.dismissedAuto.indexOf(sourceId)===-1)state.dismissedAuto.push(sourceId);if(h2SaveState(state))h2Render();
}
function h2RenderSuggestions(){
  var card=document.getElementById('h2SuggestionsCard'),list=document.getElementById('h2SuggestionList'),count=document.getElementById('h2SuggestionCount');if(!card||!list)return;
  card.style.display=h2SuggestionCache.length?'block':'none';if(!h2SuggestionCache.length){list.innerHTML='';return;}count.textContent=h2SuggestionCache.length+' awaiting approval';
  list.innerHTML=h2SuggestionCache.map(function(item){var s=item.source;return'<div class="h2-suggestion"><div><strong>'+escHtml(s.name)+'</strong><span>'+escHtml(s.category)+' &bull; recorded on '+item.days+' separate days in the last 90 days</span></div><div class="h2-suggestion-actions"><button class="h2-dismiss" onclick="h2DismissSuggestion(\''+s.id+'\')">Dismiss</button><button class="h2-approve" onclick="h2ApproveSuggestion(\''+s.id+'\')">Approve Habit</button></div></div>';}).join('');
}
function h2HabitStats(state,habit){
  var due=0,done=0,streak=0,best=0,current=0,started=new Date();started.setDate(started.getDate()-89);
  for(var i=0;i<90;i++){var d=new Date(started);d.setDate(d.getDate()+i);if(!h2IsDue(habit,d,state))continue;var log=h2Log(state,habit.id,h2Key(d));due++;if(h2IsComplete(habit,log)){done++;current++;best=Math.max(best,current);}else if(log.status!=='skip')current=0;}
  var cursor=new Date();for(var j=0;j<365;j++){if(h2IsDue(habit,cursor,state)){var lg=h2Log(state,habit.id,h2Key(cursor));if(h2IsComplete(habit,lg))streak++;else if(lg.status!=='skip'&&h2Key(cursor)!==h2Key(new Date()))break;}cursor.setDate(cursor.getDate()-1);}
  return{rate:due?Math.round(done/due*100):0,streak:streak,best:best};
}
function h2WeekStrip(state,habit){var start=h2StartWeek(h2ViewDate),out='';for(var i=0;i<7;i++){var d=new Date(start);d.setDate(d.getDate()+i);var log=h2Log(state,habit.id,h2Key(d)),cls=h2IsComplete(habit,log)?'done':log.status==='partial'?'partial':log.status==='skip'?'skip':'';out+='<div class="h2-week-day">'+['M','T','W','T','F','S','S'][i]+'<i class="'+cls+'" title="'+d.toLocaleDateString()+'"></i></div>';}return out;}
function h2ToggleMonth(id){h2OpenMonths[id]=!h2OpenMonths[id];h2Render();}
function h2MonthView(state,habit){
  var cells='',due=0,done=0,partial=0,skipped=0;
  for(var i=29;i>=0;i--){
    var date=new Date(h2ViewDate);date.setDate(date.getDate()-i);var key=h2Key(date),log=h2Log(state,habit.id,key),isDue=h2IsDue(habit,date,state),complete=h2IsComplete(habit,log),cls=!isDue?'not-due':complete?'done':log.status==='partial'?'partial':log.status==='skip'?'skip':'';
    if(isDue){due++;if(complete)done++;else if(log.status==='partial')partial++;else if(log.status==='skip')skipped++;}
    var result=!isDue?'Not scheduled':complete?'Complete':log.status==='partial'?'In progress':log.status==='skip'?'Skipped':'Not started',value=Number(log.value||0),detail=value>0?' — '+value+(habit.autoSource==='steps'?' steps':habit.autoSource==='hydration'?' oz':habit.autoSource==='sleep-routine'?' of 6 steps':habit.type==='duration'?' min':''):'';
    cells+='<i class="h2-month-day '+cls+'" title="'+date.toLocaleDateString()+': '+result+detail+'"></i>';
  }
  var rate=due?Math.round(done/due*100):0;
  return'<div class="h2-month-panel"><div class="h2-month-grid">'+cells+'</div><div class="h2-month-summary"><strong>'+done+' of '+due+' scheduled days complete &bull; '+rate+'%</strong>'+(partial?' &bull; '+partial+' in progress':'')+(skipped?' &bull; '+skipped+' skipped':'')+'</div></div>';
}
function h2Render(){
  h2SyncFromJournal();
  var state=h2State(),key=h2Key(h2ViewDate),realToday=h2Key(new Date()),dateBtn=document.getElementById('h2DateButton');if(!dateBtn)return;dateBtn.textContent=key===realToday?'Today':h2ViewDate.toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'});
  h2RenderSuggestions();
  var category=document.getElementById('h2CategoryFilter'),oldCategory=category.value||'all',cats=Array.from(new Set(state.habits.map(function(h){return h.category||'Other';}))).sort();category.innerHTML='<option value="all">All categories</option>'+cats.map(function(c){return'<option>'+escHtml(c)+'</option>';}).join('');category.value=cats.indexOf(oldCategory)!==-1?oldCategory:'all';
  var due=state.habits.filter(function(h){return h2IsDue(h,h2ViewDate,state);}),complete=due.filter(function(h){return h2IsComplete(h,h2Log(state,h.id,key));}),allStats=state.habits.map(function(h){return h2HabitStats(state,h);}),bestStreak=allStats.reduce(function(m,s){return Math.max(m,s.streak);},0),momentum=allStats.length?Math.round(allStats.reduce(function(a,s){return a+s.rate;},0)/allStats.length):0;
  var perfect=0;for(var p=0;p<30;p++){var pd=new Date();pd.setDate(pd.getDate()-p);var pdue=state.habits.filter(function(h){return h2IsDue(h,pd,state);});if(pdue.length&&pdue.every(function(h){return h2IsComplete(h,h2Log(state,h.id,h2Key(pd)));}))perfect++;}
  document.getElementById('h2SummaryGrid').innerHTML=[['Today',complete.length+'/'+due.length,'due habits complete'],['Momentum',momentum+'%','90-day consistency'],['Current streak',bestStreak+' days','best active streak'],['Perfect days',perfect,'in the last 30 days']].map(function(x){return'<div class="h2-summary"><span>'+x[0]+'</span><strong>'+x[1]+'</strong><small>'+x[2]+'</small></div>';}).join('');
  var filtered=state.habits.filter(function(h){var log=h2Log(state,h.id,key),isDue=h2IsDue(h,h2ViewDate,state),isDone=h2IsComplete(h,log);if(category.value!=='all'&&h.category!==category.value)return false;if(h2Filter==='due'&&!isDue)return false;if(h2Filter==='complete'&&!isDone)return false;if(h2Filter==='remaining'&&(!isDue||isDone))return false;return true;});
  var list=document.getElementById('h2HabitList');if(!filtered.length){list.innerHTML='<div class="h2-empty"><strong>'+(state.habits.length?'No habits match this view':'Build your first habit')+'</strong>'+(state.habits.length?'Try a different filter.':'Start small, choose a realistic schedule, and make today your first vote.')+'</div>';}else list.innerHTML=filtered.map(function(h,idx){
    var log=h2Log(state,h.id,key),done=h2IsComplete(h,log),dueNow=h2IsDue(h,h2ViewDate,state),stats=h2HabitStats(state,h),control,statusClass=done?'status-done':log.status==='partial'?'status-partial':log.status==='skip'?'status-skip':'status-open',statusLabel=done?'&#x2713; Complete':log.status==='partial'?'In progress':log.status==='skip'?'Skipped':'Not started';
    if(h.autoSource==='sleep-routine')control='<div class="h2-value-control"><span style="min-width:165px">'+Number(log.value||0)+' / 6 routine steps</span></div>';
    else if(h.autoSource==='udemy')control='<div class="h2-value-control"><span style="min-width:165px">'+Number(log.value||0)+' / 20 min from My Schedule</span></div>';
    else if(h.type==='check')control='<div class="h2-today-control"><button class="'+(done?'done':'')+'" onclick="h2SetLog(\''+h.id+'\',\'done\')">&#x2713; Complete</button><button class="'+(log.status==='partial'?'partial':'')+'" onclick="h2SetLog(\''+h.id+'\',\'partial\')">Partial</button><button class="'+(log.status==='skip'?'skip':'')+'" onclick="h2SetLog(\''+h.id+'\',\'skip\')">Skip</button></div>';
    else if(h.autoSource==='steps')control='<div class="h2-value-control"><span style="min-width:145px">'+Number(log.value||0).toLocaleString()+' / '+Number(h.target||10000).toLocaleString()+' steps</span></div>';
    else if(h.autoSource==='hydration')control='<div class="h2-value-control"><span style="min-width:145px">'+Number(log.value||0).toLocaleString()+' / '+Number(h.target||75).toLocaleString()+' oz water</span></div>';
    else control='<div class="h2-value-control"><button onclick="h2Adjust(\''+h.id+'\',-1)">−</button><span>'+Number(log.value||0)+' / '+Number(h.target||1)+(h.type==='duration'?' min':'')+'</span><button onclick="h2Adjust(\''+h.id+'\',1)">+</button></div>';
    var moveUpBtn='<button onclick="h2MoveHabit(\''+h.id+'\',-1)" title="Move up"'+(idx===0?' disabled style="opacity:.3;cursor:not-allowed;"':'')+'>&#x25B2;</button>';
    var moveDownBtn='<button onclick="h2MoveHabit(\''+h.id+'\',1)" title="Move down"'+(idx===filtered.length-1?' disabled style="opacity:.3;cursor:not-allowed;"':'')+'>&#x25BC;</button>';
    var monthOpen=!!h2OpenMonths[h.id],monthToggle='<button type="button" class="h2-month-toggle" onclick="h2ToggleMonth(\''+h.id+'\')"><span>'+(monthOpen?'&#x25BC;':'&#x25B6;')+' Last 30 Days</span><span>'+(monthOpen?'Hide monthly view':'Show monthly view')+'</span></button>';
    return'<div class="h2-habit-card '+statusClass+'"><div class="h2-habit-top"><div><div class="h2-habit-name">'+escHtml(h.name)+' <span class="h2-status-badge">'+statusLabel+'</span></div><div class="h2-habit-meta">'+escHtml(h.category)+' &bull; '+h2ScheduleLabel(h)+' &bull; '+h2TypeLabel(h)+(h.autoSource?' &bull; Journal-linked':'')+(dueNow?'':' &bull; Not scheduled')+'</div>'+(h.why?'<div class="h2-habit-why">“'+escHtml(h.why)+'”</div>':'')+'</div><div class="h2-habit-actions">'+moveUpBtn+moveDownBtn+'<button onclick="h2Edit(\''+h.id+'\')" title="Edit">&#x270E;</button><button onclick="h2Delete(\''+h.id+'\')" title="Delete">&#x1F5D1;</button></div></div><div class="h2-progress-row">'+control+'<div class="h2-stats"><span><strong>'+stats.streak+'d</strong> streak</span><span><strong>'+stats.rate+'%</strong> consistency</span><span><strong>'+stats.best+'d</strong> best</span></div></div>'+(h.cue||h.minimum?'<div class="h2-habit-meta" style="margin-top:8px;">'+(h.cue?'After I '+escHtml(h.cue)+', ':'')+(h.minimum?'minimum: '+escHtml(h.minimum):'')+'</div>':'')+'<div class="h2-week-strip">'+h2WeekStrip(state,h)+'</div>'+monthToggle+(monthOpen?h2MonthView(state,h):'')+'</div>';
  }).join('');
  h2RenderInsights(state);
}
function h2ShowDebugInfo(){
  var state=h2State();
  var targets=state.habits.filter(function(h){return['training','coding','language'].indexOf(h.autoSource)!==-1;});
  var weekStart=h2StartWeek(h2ViewDate);
  var out={today:h2Key(new Date()),viewDate:h2Key(h2ViewDate),weekStripStart:h2Key(weekStart),habits:targets.map(function(h){
    var days=[];
    for(var i=13;i>=0;i--){var d=new Date();d.setDate(d.getDate()-i);var k=h2Key(d);var log=h2Log(state,h.id,k);days.push(k+'('+['Sun','Mon','Tue','Wed','Thu','Fri','Sat'][d.getDay()]+'): due='+h2IsDue(h,d,state)+' status='+(log.status||'blank'));}
    var weekStripDays=[];
    for(var w=0;w<7;w++){var wd=new Date(weekStart);wd.setDate(wd.getDate()+w);var wk=h2Key(wd);var wlog=h2Log(state,h.id,wk);var cls=h2IsComplete(h,wlog)?'done':wlog.status==='partial'?'partial':wlog.status==='skip'?'skip':'(blank)';weekStripDays.push(['M','T','W','T','F','S','S'][w]+' '+wk+': '+cls);}
    var stats=h2HabitStats(state,h);
    return{name:h.name,id:h.id,autoSource:h.autoSource,schedule:h.schedule,days:h.days,weeklyTarget:h.weeklyTarget,created:h.created,stats:stats,weekStripDays:weekStripDays,last14:days};
  })};
  window.prompt('Copy everything in this box and send it back:',JSON.stringify(out,null,1));
}
function h2MoveHabit(id,dir){
  var state=h2State(),key=h2Key(h2ViewDate);
  var category=document.getElementById('h2CategoryFilter'),catVal=category?category.value:'all';
  var filtered=state.habits.filter(function(h){var log=h2Log(state,h.id,key),isDue=h2IsDue(h,h2ViewDate,state),isDone=h2IsComplete(h,log);if(catVal!=='all'&&h.category!==catVal)return false;if(h2Filter==='due'&&!isDue)return false;if(h2Filter==='complete'&&!isDone)return false;if(h2Filter==='remaining'&&(!isDue||isDone))return false;return true;});
  var visIdx=filtered.findIndex(function(h){return h.id===id;});
  if(visIdx===-1)return;
  var swapVisIdx=visIdx+dir;
  if(swapVisIdx<0||swapVisIdx>=filtered.length)return;
  var otherId=filtered[swapVisIdx].id;
  var realIdx=state.habits.findIndex(function(h){return h.id===id;});
  var otherRealIdx=state.habits.findIndex(function(h){return h.id===otherId;});
  if(realIdx===-1||otherRealIdx===-1)return;
  var tmp=state.habits[realIdx];state.habits[realIdx]=state.habits[otherRealIdx];state.habits[otherRealIdx]=tmp;
  if(h2SaveState(state))h2Render();
}
function h2RenderInsights(state){
  var heat=document.getElementById('h2Heatmap');if(!heat)return;var start=new Date();start.setDate(start.getDate()-83);var cells=[];for(var i=0;i<84;i++){var d=new Date(start);d.setDate(d.getDate()+i);var due=state.habits.filter(function(h){return h2IsDue(h,d,state);}),done=due.filter(function(h){return h2IsComplete(h,h2Log(state,h.id,h2Key(d)));}).length,ratio=due.length?done/due.length:0,level=ratio===0?'':ratio<.5?'l1':ratio<1?'l2':'l3';cells.push('<i class="'+level+'" title="'+d.toLocaleDateString()+': '+done+'/'+due.length+'"></i>');}heat.innerHTML=cells.join('');
  var perf=document.getElementById('h2Performance');perf.innerHTML=state.habits.length?state.habits.map(function(h){var s=h2HabitStats(state,h),color=s.rate<50?'#dc2626':s.rate<75?'#d97706':'#16835d';return'<div class="h2-performance-row"><div class="h2-performance-label"><span>'+escHtml(h.name)+'</span><strong style="color:'+color+'">'+s.rate+'%</strong></div><div class="h2-performance-bar"><i style="width:'+s.rate+'%;background:'+color+'"></i></div></div>';}).join(''):'<div style="font-size:.72rem;color:#94a3b8;">Add habits to see performance.</div>';
}

// ── Ask Journal Follow-up ────────────────────────────────────────
var _askJournalCtx=[];
var _askJournalAttachment=null;
function askJournalFileChosen(input){
  var file=input.files[0];if(!file)return;
  var lbl=document.getElementById('askJournalFileLabel');
  var reader=new FileReader();
  reader.onload=function(e){
    var b64=e.target.result.split(',')[1];
    _askJournalAttachment={name:file.name,mime:file.type||'application/octet-stream',b64:b64};
    if(lbl){lbl.style.display='block';lbl.innerHTML='&#x1F4CE; <strong>'+escHtml(file.name)+'</strong> attached &nbsp;<button onclick="clearAskJournalFile()" style="background:none;border:none;color:#e74c3c;cursor:pointer;font-size:0.8rem;padding:0;">&#x2715; Remove</button>';}
  };
  reader.readAsDataURL(file);
}
function clearAskJournalFile(){
  _askJournalAttachment=null;
  var lbl=document.getElementById('askJournalFileLabel');if(lbl)lbl.style.display='none';
  var inp=document.getElementById('askJournalFile');if(inp)inp.value='';
}
async function submitAskJournalFollowUp(){
  const q=(document.getElementById('askJournalFollowUp').value||'').trim();
  if(!q){alert('Enter a follow-up question first.');return;}
  const key=journalAIKey();
  if(!key){alert('Add your Anthropic API key first.');return;}
  // Build multi-turn from context + new question
  _askJournalCtx.push({role:'user',content:q});
  const followEl=document.getElementById('askJournalResult');
  if(followEl){followEl.style.display='block';followEl.textContent='⏳ Thinking...';}
  try{
    const data=await journalProtectedFunction({model:'claude-haiku-4-5-20251001',max_tokens:2000,messages:_askJournalCtx});
    if(data.error)throw new Error(data.error.message||'The AI request could not be completed.');
    const reply=(data.content[0].text||'').trim();
    _askJournalCtx.push({role:'assistant',content:reply});
    aiShow('askJournalResult',reply,'Ask AI Follow-up');
    document.getElementById('askJournalFollowUp').value='';
  }catch(e){if(followEl)followEl.textContent='Error: '+(e&&e.message?e.message:'Could not reach the protected AI service.');}
}

// ── Journal Search ────────────────────────────────────────
function openJournalSearch(){
  document.getElementById('journalSearchModal').style.display='flex';
  document.getElementById('journalSearchInput').value='';
  document.getElementById('journalSearchResults').innerHTML='<div style="text-align:center;color:#aaa;padding:30px;font-size:0.9rem;">Start typing to search your journal entries.</div>';
  setTimeout(()=>document.getElementById('journalSearchInput').focus(),50);
}
function closeJournalSearch(){document.getElementById('journalSearchModal').style.display='none';}

function storageFormatBytes(bytes){
  bytes=Math.max(0,Number(bytes)||0);
  if(bytes<1024)return bytes+' B';
  if(bytes<1048576)return (bytes/1024).toFixed(bytes<10240?1:0)+' KB';
  if(bytes<1073741824)return (bytes/1048576).toFixed(bytes<10485760?2:1)+' MB';
  return (bytes/1073741824).toFixed(2)+' GB';
}
function storageTextBytes(value){try{return new Blob([String(value||'')]).size;}catch(e){return String(value||'').length*2;}}
function storageValueBytes(value,seen){
  if(value==null)return 0;
  if(typeof value==='string')return storageTextBytes(value);
  if(typeof value==='number'||typeof value==='boolean')return 8;
  if(value instanceof Blob)return value.size||0;
  if(value instanceof ArrayBuffer)return value.byteLength||0;
  if(ArrayBuffer.isView(value))return value.byteLength||0;
  if(typeof value!=='object')return 0;
  seen=seen||new Set();if(seen.has(value))return 0;seen.add(value);
  var total=0;Object.keys(value).forEach(function(k){total+=storageTextBytes(k)+storageValueBytes(value[k],seen);});return total;
}
function journalIsExternalAppKey(key){
  var lower=String(key||'').toLowerCase();
  return lower.indexOf('vinylcollection')===0
    ||lower.indexOf('rcpt-')===0
    ||lower.indexOf('meridian_money_')===0
    ||lower.indexOf('retirementplanner')===0
    ||lower==='cc-register'||lower.indexOf('cc-register-')===0;
}
function storageLocalCategory(key){
  if(key.indexOf('planner_')===0)return'Daily entries';
  if(key.indexOf('bible_')===0)return'Bible & worship';
  if(key==='ai_archive'||key.indexOf('review_')===0)return'AI & reviews';
  if(key.indexOf('handwriting_')===0)return'Handwritten notes';
  if(key.indexOf('training')!==-1||key.indexOf('plan')!==-1||key==='weekly_goals')return'Training plans';
  if(key.indexOf('library')!==-1||key.indexOf('book')!==-1||key.indexOf('audible')!==-1)return'Library';
  if(key.indexOf('habit')!==-1||key.indexOf('skill')!==-1)return'Habits & skills';
  if(key.indexOf('meal')!==-1||key.indexOf('food')!==-1||key.indexOf('nutrition')!==-1)return'Meals & nutrition';
  if(key.indexOf('cache')!==-1||key.indexOf('weather')!==-1||key.indexOf('airnow')!==-1)return'Temporary caches';
  return'Settings & other data';
}
async function storageReadStore(dbName,storeName){
  if(indexedDB.databases){var dbs=await indexedDB.databases();if(!dbs.some(function(d){return d.name===dbName;}))return[];}
  return new Promise(function(resolve){
    var req=indexedDB.open(dbName);req.onerror=function(){resolve([]);};
    req.onsuccess=function(){var db=req.result;if(!db.objectStoreNames.contains(storeName)){db.close();resolve([]);return;}var get=db.transaction(storeName,'readonly').objectStore(storeName).getAll();get.onsuccess=function(){var rows=get.result||[];db.close();resolve(rows);};get.onerror=function(){db.close();resolve([]);};};
  });
}
function storageBarRow(name,bytes,maxBytes,note){
  var pct=maxBytes?Math.max(2,Math.round(bytes/maxBytes*100)):2;
  return '<div style="padding:9px 0;border-bottom:1px solid #eaecf0;"><div style="display:flex;justify-content:space-between;gap:10px;align-items:baseline;"><strong style="font-size:.84rem;color:#344054;">'+escHtml(name)+'</strong><span style="font-size:.82rem;font-weight:700;color:#101828;white-space:nowrap;">'+storageFormatBytes(bytes)+'</span></div><div style="height:7px;background:#f2f4f7;border-radius:99px;overflow:hidden;margin-top:6px;"><div style="height:100%;width:'+pct+'%;background:linear-gradient(90deg,#2563eb,#b91c1c);border-radius:99px;"></div></div>'+(note?'<div style="font-size:.7rem;color:#98a2b3;margin-top:4px;">'+escHtml(note)+'</div>':'')+'</div>';
}
async function renderStorageBreakdown(){
  var body=document.getElementById('storageBreakdownBody');if(!body)return;
  body.innerHTML='<div style="text-align:center;color:#98a2b3;padding:34px;">Calculating storage...</div>';
  var local={},largest=[],localTotal=0,externalLocal=0,transcriptBytes=0;
  for(var i=0;i<localStorage.length;i++){
    var key=localStorage.key(i),value=localStorage.getItem(key)||'',bytes=storageTextBytes(key)+storageTextBytes(value),category=storageLocalCategory(key);
    if(journalIsExternalAppKey(key)){externalLocal+=bytes;continue;}
    if(key.indexOf('bible_')===0){try{var bible=JSON.parse(value);var tb=storageTextBytes(bible.transcript||'');if(tb){transcriptBytes+=tb;bytes=Math.max(0,bytes-tb);}}catch(ignore){}}
    local[category]=(local[category]||0)+bytes;localTotal+=bytes;largest.push({name:key,bytes:bytes});
  }
  if(transcriptBytes){local['Sermon transcripts']=(local['Sermon transcripts']||0)+transcriptBytes;localTotal+=transcriptBytes;}
  var dbSpecs=[['JournalArchiveDB','journals','Uploaded journal archive'],['OtherUploadsDB','files','Other uploaded files'],['BookDocumentsDB','docs','Library documents'],['BookCoversDB','covers','Book covers'],['BibleImagesDB','images','Bible images'],['my_life_command_center_large_storage','journal','Activities & Bible archives']];
  var expanded=[];
  for(var j=0;j<dbSpecs.length;j++){var rows=await storageReadStore(dbSpecs[j][0],dbSpecs[j][1]),size=storageValueBytes(rows);if(size)expanded.push({name:dbSpecs[j][2],bytes:size,count:rows.length});}
  local['Sermon transcripts']=local['Sermon transcripts']||0;
  var localRows=Object.keys(local).map(function(name){return{name:name,bytes:local[name]};}).filter(function(x){return x.bytes>0;}).sort(function(a,b){return b.bytes-a.bytes;});
  expanded.sort(function(a,b){return b.bytes-a.bytes;});
  var maxLocal=Math.max.apply(null,localRows.map(function(x){return x.bytes;}).concat([1]));
  var maxExpanded=Math.max.apply(null,expanded.map(function(x){return x.bytes;}).concat([1]));
  var estimate=null;try{if(navigator.storage&&navigator.storage.estimate)estimate=await navigator.storage.estimate();}catch(ignore2){}
  var localLimit=5242880,localRemaining=Math.max(0,localLimit-localTotal),localRemainingPct=Math.max(0,Math.round(localRemaining/localLimit*100));
  var html='<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:10px;margin-bottom:14px;">'
    +'<div style="background:#eff6ff;border-radius:10px;padding:12px;"><div style="font-size:.7rem;font-weight:700;color:#1d4ed8;text-transform:uppercase;">Local journal data</div><div style="font-size:1.2rem;font-weight:800;color:#172554;margin-top:3px;">'+storageFormatBytes(localTotal)+'</div><div style="font-size:.7rem;color:#64748b;">Approx. '+Math.round(localTotal/5242880*100)+'% of a typical 5 MB limit</div></div>'
    +'<div style="background:#ecfdf3;border-radius:10px;padding:12px;"><div style="font-size:.7rem;font-weight:700;color:#047857;text-transform:uppercase;">Local journal space left</div><div style="font-size:1.2rem;font-weight:800;color:#064e3b;margin-top:3px;">'+storageFormatBytes(localRemaining)+'</div><div style="font-size:.7rem;color:#64748b;">Approx. '+localRemainingPct+'% remaining before the typical 5 MB limit</div></div>'
    +'<div style="background:#f0fdf4;border-radius:10px;padding:12px;"><div style="font-size:.7rem;font-weight:700;color:#15803d;text-transform:uppercase;">Expanded file storage</div><div style="font-size:1.2rem;font-weight:800;color:#14532d;margin-top:3px;">'+storageFormatBytes(expanded.reduce(function(s,x){return s+x.bytes;},0))+'</div><div style="font-size:.7rem;color:#64748b;">Uploads, images and large archives</div></div>';
  if(estimate)html+='<div style="background:#fff7ed;border-radius:10px;padding:12px;"><div style="font-size:.7rem;font-weight:700;color:#c2410c;text-transform:uppercase;">Browser origin total</div><div style="font-size:1.2rem;font-weight:800;color:#7c2d12;margin-top:3px;">'+storageFormatBytes(estimate.usage||0)+'</div><div style="font-size:.7rem;color:#64748b;">Browser-reported usage; quota '+storageFormatBytes(estimate.quota||0)+'</div></div>';
  if(externalLocal)html+='<div style="background:#f5f3ff;border-radius:10px;padding:12px;"><div style="font-size:.7rem;font-weight:700;color:#6d28d9;text-transform:uppercase;">Separate app data</div><div style="font-size:1.2rem;font-weight:800;color:#4c1d95;margin-top:3px;">'+storageFormatBytes(externalLocal)+'</div><div style="font-size:.7rem;color:#64748b;">Not listed as journal data, backed up, or synchronized</div></div>';
  html+='</div><h3 style="font-size:.86rem;color:#344054;margin:8px 0 2px;">Local journal categories</h3>';
  localRows.forEach(function(x){html+=storageBarRow(x.name,x.bytes,maxLocal,x.name==='Sermon transcripts'?'Long transcripts are usually the first items worth moving.':'');});
  html+='<h3 style="font-size:.86rem;color:#344054;margin:16px 0 2px;">Expanded file storage</h3>';
  if(expanded.length)expanded.forEach(function(x){html+=storageBarRow(x.name,x.bytes,maxExpanded,x.count+' stored item'+(x.count===1?'':'s'));});else html+='<div style="font-size:.8rem;color:#98a2b3;padding:12px 0;">No uploaded files or large archives were found on this device.</div>';
  largest.sort(function(a,b){return b.bytes-a.bytes;});
  html+='<details style="margin-top:14px;background:#f8fafc;border-radius:9px;padding:10px 12px;"><summary style="font-size:.8rem;font-weight:700;color:#475467;cursor:pointer;">Largest individual local records</summary><div style="margin-top:7px;">'+largest.slice(0,10).map(function(x){return '<div style="display:flex;justify-content:space-between;gap:10px;font-size:.74rem;padding:4px 0;border-bottom:1px solid #eaecf0;"><span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">'+escHtml(x.name)+'</span><strong>'+storageFormatBytes(x.bytes)+'</strong></div>';}).join('')+'</div></details>';
  body.innerHTML=html;
}
function openStorageBreakdown(){var modal=document.getElementById('storageBreakdownModal');if(modal)modal.style.display='flex';renderStorageBreakdown();}
function closeStorageBreakdown(){var modal=document.getElementById('storageBreakdownModal');if(modal)modal.style.display='none';}

function runJournalSearch(){
  const q=(document.getElementById('journalSearchInput').value||'').toLowerCase().trim();
  const scope=(document.getElementById('journalSearchScope')||{}).value||'all';
  const el=document.getElementById('journalSearchResults');
  if(!q){el.innerHTML='<div style="text-align:center;color:#aaa;padding:30px;font-size:0.9rem;">Start typing to search your journal entries and activities.</div>';return;}
  const results=[];
  // ── Search journal entries ──
  for(let i=0;i<localStorage.length;i++){
    const k=localStorage.key(i);
    if(!k.startsWith('planner_'))continue;
    let d;try{d=JSON.parse(localStorage.getItem(k));}catch{continue;}
    const foodNames=Array.isArray(d.foodLog)?d.foodLog.map(function(f){return f.name||'';}).filter(Boolean).join(', '):'';
    const fields={'Running Log':d.jRunLog,'Accomplishments':d.jAccomplish,'Improvements':d.jImprov,'Notes':d.jNotes,'Daily Bread':d.spDailyBreadText,'Meditation':d.spMeditationText,'Sermon':getSermonNotes(k.replace('planner_','')),'Audiobook':d.gaAudiobookText,'Skill Builder':d.gaSkillText,'BP':d.wBPVal,'Weight':d.wWghtVal,'Sleep':d.wSleepVal,'Meds':d.wMedsVal,'Strength Notes':d.exStrengthVal,'Bike':d.exBikeVal,'Walk':d.exWalkVal,'Stretch':d.exStretchVal,'Food Log':foodNames,'Priority 1':d.pt0t,'Priority 2':d.pt1t,'Priority 3':d.pt2t,'Daily Goal 1':d.dg0t,'Daily Goal 2':d.dg1t,'Daily Goal 3':d.dg2t};
    const date=k.replace('planner_','');
    const hits=[];
    Object.entries(fields).forEach(([label,val])=>{if(val&&val.toLowerCase().includes(q))hits.push({label,val});});
    if(hits.length)results.push({date,type:'journal',hits});
  }
  // ── Search Bible Notes ──
  const bibleResults=[];
  for(let i=0;i<localStorage.length;i++){
    const k=localStorage.key(i);
    if(!k.startsWith('bible_'))continue;
    let b;try{b=JSON.parse(localStorage.getItem(k));}catch{continue;}
    const bfields={'Sermon Title':b.sermonTitle,'Speaker':b.sermonSpeaker,'Sermon Notes':b.sermonNotes,'Scripture':b.scripture,'Key Verse':b.keyVerse,'Theme':b.theme,'Study Notes':b.notes,'Application':b.application,'Prayer':b.prayer};
    const date=k.replace('bible_','');
    const hits=[];
    Object.entries(bfields).forEach(([label,val])=>{if(val&&val.toLowerCase().includes(q))hits.push({label,val});});
    if(hits.length)bibleResults.push({date,hits});
  }
  // ── Search activities ──
  const acts=getActivities();
  const actHits=[];
  acts.forEach(a=>{
    const searchable=[a.notes,a.injuryReport,a.type,a.date,String(a.distance||''),a.pace].filter(Boolean).join(' ').toLowerCase();
    if(!searchable.includes(q))return;
    const parts=[];
    if(a.type)parts.push(a.type);
    if(a.distance)parts.push(a.distance+'mi');
    if(a.pace)parts.push(a.pace+'/mi');
    if(a.duration)parts.push(fmtDuration(a.duration));
    if(a.notes)parts.push(a.notes);
    if(a.injuryReport)parts.push('Injury report: '+a.injuryReport);
    actHits.push({date:a.date,summary:parts.join(' · ')});
  });
  // ── Search training plan ──
  const planRaw=localStorage.getItem('current_training_plan');
  let planSnippets=[];
  if(planRaw){
    const planText=(JSON.parse(planRaw).text||'');
    planText.split('\n').forEach(line=>{
      if(line.toLowerCase().includes(q))planSnippets.push(line.trim());
    });
  }
  if(scope==='uploads'){
    results.length=0;bibleResults.length=0;actHits.length=0;planSnippets=[];
  }
  // ── Render ──
  function hlEsc(text){return escHtml(text).replace(new RegExp('('+q.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+')','gi'),'<mark style="background:#fff176;border-radius:2px;">$1</mark>');}
  let html='';
  // Journal results
  results.sort((a,b)=>b.date.localeCompare(a.date));
  results.forEach(({date,hits})=>{
    const dt=new Date(date+'T00:00:00');
    const lbl=dt.toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric',year:'numeric'});
    html+=`<div style="border:1px solid #e9ecef;border-radius:8px;padding:12px;margin-bottom:10px;"><div style="font-weight:700;color:#667eea;font-size:0.88rem;margin-bottom:6px;">📅 ${lbl} <span style="font-size:.75rem;color:#aaa;font-weight:400;">Journal</span></div>${hits.map(({label:l,val})=>`<div style="margin-bottom:4px;"><span style="font-size:0.75rem;font-weight:600;color:#888;text-transform:uppercase;">${escHtml(l)}</span><div style="font-size:0.88rem;color:#333;margin-top:2px;">${hlEsc(val)}</div></div>`).join('')}</div>`;
  });
  // Activity results
  if(actHits.length){
    actHits.sort((a,b)=>b.date.localeCompare(a.date));
    html+='<div style="font-weight:700;color:#e67e22;font-size:0.82rem;text-transform:uppercase;letter-spacing:.05em;margin:10px 0 6px;">🏃 Activities ('+actHits.length+')</div>';
    actHits.forEach(({date,summary})=>{
      const dt=new Date(date+'T00:00:00');
      const lbl=dt.toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric',year:'numeric'});
      html+=`<div style="border:1px solid #fde8cc;border-radius:8px;padding:10px 12px;margin-bottom:8px;background:#fffaf5;"><div style="font-weight:700;color:#e67e22;font-size:0.85rem;">${lbl}</div><div style="font-size:0.85rem;color:#555;margin-top:3px;">${hlEsc(summary)}</div></div>`;
    });
  }
  // Bible Notes results
  if(bibleResults.length){
    bibleResults.sort((a,b)=>b.date.localeCompare(a.date));
    html+='<div style="font-weight:700;color:#e67e22;font-size:0.82rem;text-transform:uppercase;letter-spacing:.05em;margin:10px 0 6px;">📖 Bible Notes ('+bibleResults.length+')</div>';
    bibleResults.forEach(({date,hits})=>{
      const dt=new Date(date+'T00:00:00');
      const lbl=dt.toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric',year:'numeric'});
      html+=`<div style="border:1px solid #f0c080;border-radius:8px;padding:12px;margin-bottom:8px;background:#fffaf4;"><div style="font-weight:700;color:#d35400;font-size:0.85rem;margin-bottom:5px;">📖 ${lbl}</div>${hits.map(({label:l,val})=>`<div style="margin-bottom:4px;"><span style="font-size:0.75rem;font-weight:600;color:#888;text-transform:uppercase;">${escHtml(l)}</span><div style="font-size:0.88rem;color:#333;margin-top:2px;">${hlEsc(val)}</div></div>`).join('')}</div>`;
    });
  }
  // Training plan snippets
  if(planSnippets.length){
    html+='<div style="font-weight:700;color:#8e44ad;font-size:0.82rem;text-transform:uppercase;letter-spacing:.05em;margin:10px 0 6px;">📋 Training Plan ('+planSnippets.length+' lines)</div>';
    html+='<div style="border:1px solid #e8d5f5;border-radius:8px;padding:10px 12px;background:#faf5ff;">';
    planSnippets.slice(0,10).forEach(line=>{
      html+=`<div style="font-size:0.85rem;color:#333;padding:3px 0;border-bottom:1px solid #f0e6ff;">${hlEsc(line)}</div>`;
    });
    if(planSnippets.length>10)html+=`<div style="font-size:0.8rem;color:#aaa;margin-top:6px;">…and ${planSnippets.length-10} more lines</div>`;
    html+='</div>';
  }
  if(scope==='current'){
    el.innerHTML=html||'<div style="text-align:center;color:#aaa;padding:30px;font-size:0.9rem;">No results found in the current journal for "'+escHtml(q)+'".</div>';
    return;
  }
  el.innerHTML=html||'<div id="journalCurrentEmpty" style="text-align:center;color:#aaa;padding:12px;font-size:0.85rem;">No matching current-journal entries.</div>';
  appendUploadedJournalSearch(q,el,scope==='all');
}

function appendUploadedJournalSearch(query,container,append){
  jaOpenDB().then(function(db){
    var req=db.transaction('journals','readonly').objectStore('journals').getAll();
    req.onsuccess=function(e){
      var hits=[];
      (e.target.result||[]).forEach(function(r){
        var text=r.text||'',lower=text.toLowerCase(),pos=0,count=0,excerpts=[];
        if(lower.indexOf(query)===-1)return;
        while((pos=lower.indexOf(query,pos))!==-1){count++;pos+=query.length;}
        pos=0;
        while((pos=lower.indexOf(query,pos))!==-1&&excerpts.length<3){
          var from=Math.max(0,pos-80),to=Math.min(text.length,pos+query.length+80);
          excerpts.push((from?'…':'')+text.slice(from,to).replace(/\s+/g,' ').trim()+(to<text.length?'…':''));
          pos+=query.length+100;
        }
        hits.push({name:r.name||'Uploaded journal',count:count,excerpts:excerpts});
      });
      var html='';
      if(hits.length){
        var safeQ=query.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
        function mark(s){return escHtml(s).replace(new RegExp('('+safeQ+')','gi'),'<mark style="background:#fef08a;border-radius:2px;">$1</mark>');}
        html='<div style="font-weight:700;color:#0f766e;font-size:0.82rem;text-transform:uppercase;letter-spacing:.05em;margin:12px 0 6px;">&#x1F4DA; Uploaded Journals ('+hits.length+')</div>';
        html+=hits.map(function(h){return '<div style="background:#f0fdfa;border:1px solid #99f6e4;border-radius:8px;padding:10px 12px;margin-bottom:8px;"><div style="font-size:0.85rem;font-weight:700;color:#134e4a;margin-bottom:6px;">&#x1F4C4; '+escHtml(h.name)+' <span style="font-weight:400;color:#64748b;font-size:0.75rem;">('+h.count+' match'+(h.count===1?'':'es')+')</span></div>'+h.excerpts.map(function(x){return '<div style="font-size:0.82rem;color:#334155;line-height:1.55;margin-bottom:4px;padding:4px 8px;background:#fff;border-radius:4px;">'+mark(x)+'</div>';}).join('')+'</div>';}).join('');
      }else if(!append){
        html='<div style="text-align:center;color:#aaa;padding:30px;font-size:0.9rem;">No results found in uploaded journals for "'+escHtml(query)+'".</div>';
      }
      if(append)container.insertAdjacentHTML('beforeend',html);else container.innerHTML=html;
      var empty=document.getElementById('journalCurrentEmpty');
      if(empty&&hits.length)empty.remove();
      if(append&&!hits.length&&!container.querySelector('mark'))container.innerHTML='<div style="text-align:center;color:#aaa;padding:30px;font-size:0.9rem;">No results found for "'+escHtml(query)+'".</div>';
    };
  }).catch(function(){if(!append)container.innerHTML='<div style="text-align:center;color:#b42318;padding:20px;font-size:0.85rem;">Uploaded journals could not be searched on this device.</div>';});
}

// ── Library ───────────────────────────────────────────────
let newBookRating=0,sortOrder='newest',editingBookId=null,editBookRating=0;
function escHtml(s){return(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');}
function getBooks(){try{const r=localStorage.getItem('library_books');return r?JSON.parse(r):[];}catch(ignore){return [];}}
function saveBooks(b){localStorage.setItem('library_books',JSON.stringify(b));}
function sortBooks(books){
  const b=[...books];
  switch(sortOrder){
    case'oldest':return b.sort((a,c)=>(a.dateCompleted||'')>(c.dateCompleted||'')?1:-1);
    case'title':return b.sort((a,c)=>a.title.localeCompare(c.title));
    case'author':return b.sort((a,c)=>(a.author||'').localeCompare(c.author||''));
    case'rating':return b.sort((a,c)=>(c.rating||0)-(a.rating||0));
    default:return b.sort((a,c)=>(c.dateCompleted||'')>(a.dateCompleted||'')?1:-1);
  }
}
function buildNewBookStars(){
  const el=document.getElementById('newBookStars');if(!el)return;el.innerHTML='';
  for(let i=1;i<=5;i++){const s=document.createElement('span');s.className='star'+(i<=newBookRating?' active':'');s.textContent='★';s.style.fontSize='1.5rem';s.onclick=()=>{newBookRating=(newBookRating===i)?i-1:i;buildNewBookStars();};el.appendChild(s);}
}
function buildEditStars(){
  const el=document.getElementById('editStarsDisplay');if(!el)return;el.innerHTML='';
  for(let i=1;i<=5;i++){const s=document.createElement('span');s.className='star'+(i<=editBookRating?' active':'');s.textContent='★';s.style.fontSize='1.3rem';s.style.cursor='pointer';s.onclick=()=>{editBookRating=(editBookRating===i)?i-1:i;buildEditStars();};el.appendChild(s);}
}
function toggleAddBook(){
  const f=document.getElementById('addBookForm');if(!f)return;
  if(f.style.display!=='none'){f.style.display='none';return;}
  if(editingBookId){editingBookId=null;renderLibrary();}
  f.style.display='block';
  const dt=document.getElementById('newBookDate');if(dt&&!dt.value)dt.value=dk(new Date());
  newBookRating=0;buildNewBookStars();
  setTimeout(()=>{const t=document.getElementById('newBookTitle');if(t)t.focus();},50);
}
function addBook(){
  const title=(document.getElementById('newBookTitle').value||'').trim();
  if(!title){alert('Please enter a book title.');return;}
  const books=getBooks();
  var _newBookId=Date.now();
  books.unshift({id:_newBookId,title,author:(document.getElementById('newBookAuthor').value||'').trim(),dateCompleted:document.getElementById('newBookDate').value,rating:newBookRating,notes:(document.getElementById('newBookNotes').value||'').trim()});
  saveBooks(books);
  if(_pendingCoverData){var _cid=_newBookId;var _cd=_pendingCoverData;_pendingCoverData=null;setBookCover(_cid,_cd);}
  ['newBookTitle','newBookAuthor','newBookNotes'].forEach(id=>{const e=document.getElementById(id);if(e)e.value='';});
  document.getElementById('newBookDate').value='';newBookRating=0;
  document.getElementById('addBookForm').style.display='none';
  renderLibrary();
}
function deleteBook(id){
  if(!confirm('Remove this book from your library?'))return;
  if(editingBookId===id)editingBookId=null;
  saveBooks(getBooks().filter(b=>b.id!==id));renderLibrary();
}
function editBook(id){
  const book=getBooks().find(b=>b.id===id);if(!book)return;
  editBookRating=book.rating||0;editingBookId=id;
  document.getElementById('addBookForm').style.display='none';
  renderLibrary();
  setTimeout(()=>{const t=document.getElementById('editBookTitle');if(t)t.focus();},50);
}
function cancelBookEdit(){editingBookId=null;renderLibrary();}
function saveBookEdit(id){
  const title=(document.getElementById('editBookTitle').value||'').trim();
  if(!title){alert('Please enter a book title.');return;}
  const books=getBooks();const idx=books.findIndex(b=>b.id===id);if(idx===-1)return;
  books[idx]={...books[idx],title,author:(document.getElementById('editBookAuthor').value||'').trim(),dateCompleted:document.getElementById('editBookDate').value,rating:editBookRating,notes:(document.getElementById('editBookNotes').value||'').trim()};
  saveBooks(books);editingBookId=null;renderLibrary();
}


// ── Book Cover Functions ──────────────────────────────────────────────────────
function toggleAudiobookFinishPlanner(forceOpen){
  var panel=document.getElementById('audioPlanPanel'),arrow=document.getElementById('audioPlanArrow');
  if(!panel)return;
  var open=typeof forceOpen==='boolean'?forceOpen:(panel.style.display==='none'||!panel.style.display);
  panel.style.display=open?'block':'none';
  if(arrow)arrow.innerHTML=open?'&#x25BC;':'&#x25B6;';
  var startDate=document.getElementById('audioPlanStartDate'),date=document.getElementById('audioPlanDate');
  if(open&&startDate&&!startDate.value)startDate.value=dk(new Date());
  if(open&&date&&!date.value){var d=startDate&&startDate.value?new Date(startDate.value+'T00:00:00'):new Date();d.setDate(d.getDate()+30);date.value=dk(d);}
}
function createAudiobookFinishPlan(){
  var title=((document.getElementById('audioPlanBook')||{}).value||'').trim(),author=((document.getElementById('audioPlanAuthor')||{}).value||'').trim(),hours=Math.max(0,parseInt((document.getElementById('audioPlanHours')||{}).value,10)||0),minutes=Math.max(0,parseInt((document.getElementById('audioPlanMinutes')||{}).value,10)||0),startKey=(document.getElementById('audioPlanStartDate')||{}).value||'',finish=(document.getElementById('audioPlanDate')||{}).value||'';
  if(!title){alert('Enter the audiobook name first.');return;}
  if(!author){alert('Enter the audiobook author.');return;}
  if(minutes>59){alert('Additional minutes must be between 0 and 59.');return;}
  var totalSec=(hours*60+minutes)*60;
  if(totalSec<=0){alert('Enter the total audiobook length.');return;}
  if(!startKey){alert('Choose a start date.');return;}
  if(!finish){alert('Choose a finish date.');return;}
  var start=new Date(startKey+'T00:00:00'),end=new Date(finish+'T00:00:00'),days=Math.floor((end-start)/86400000)+1;
  if(days<1){alert('The finish date must be on or after the start date.');return;}
  var plan={query:title,queryAuthor:author,title:title,author:author,narrator:'',source:'the audiobook length you entered',startDate:startKey,finishDate:finish,totalSeconds:totalSec,dailySeconds:Math.ceil(totalSec/days),days:days,artwork:'',createdAt:new Date().toISOString()};
  localStorage.setItem('audiobook_finish_plan',JSON.stringify(plan));renderAudiobookFinishPlan();
}
function formatSecondsLong(sec){
  sec=Math.max(0,Math.round(Number(sec)||0));var h=Math.floor(sec/3600),m=Math.floor((sec%3600)/60),s=sec%60,parts=[];
  if(h)parts.push(h+' hr');if(m)parts.push(m+' min');if(s)parts.push(s+' sec');return parts.join(' ')||'0 sec';
}
function renderAudiobookFinishPlan(){
  var result=document.getElementById('audioPlanResult');if(!result)return;
  var raw=localStorage.getItem('audiobook_finish_plan');if(!raw){result.style.display='none';return;}
  var p;try{p=JSON.parse(raw);}catch(e){result.style.display='none';return;}
  var start=new Date(p.startDate+'T00:00:00'),end=new Date(p.finishDate+'T00:00:00'),milestones=[],cursor=new Date(start),week=1;
  var now=new Date();now.setHours(0,0,0,0);var throughDate=now>end?new Date(end):new Date(now),actualSeconds=0,elapsedDays=0;
  if(throughDate>=start){var listenDay=new Date(start);while(listenDay<=throughDate){var listenKey=dk(listenDay),listenData={};try{listenData=JSON.parse(localStorage.getItem('planner_'+listenKey)||'{}');}catch(ignoreListen){}actualSeconds+=journalAudiobookMinutesForDate(listenKey,listenData)*60;listenDay.setDate(listenDay.getDate()+1);}elapsedDays=Math.floor((throughDate-start)/86400000)+1;}
  actualSeconds=Math.min(Number(p.totalSeconds)||0,actualSeconds);var plannedSeconds=Math.min(Number(p.totalSeconds)||0,Math.round((Number(p.totalSeconds)||0)*(elapsedDays/Math.max(1,Number(p.days)||1)))),actualPct=p.totalSeconds?Math.min(100,Math.round(actualSeconds/p.totalSeconds*100)):0,plannedPct=p.totalSeconds?Math.min(100,Math.round(plannedSeconds/p.totalSeconds*100)):0,tolerance=Math.max(300,Math.round((Number(p.dailySeconds)||0)*0.1)),progressLabel='On Track',progressColor='#16a34a';
  if(actualSeconds>=Number(p.totalSeconds||0)&&Number(p.totalSeconds||0)>0){progressLabel='Complete';progressColor='#16a34a';}else if(actualSeconds>plannedSeconds+tolerance){progressLabel='Ahead';progressColor='#2563eb';}else if(actualSeconds+tolerance<plannedSeconds){progressLabel='Behind';progressColor='#dc2626';}
  var remainingSeconds=Math.max(0,(Number(p.totalSeconds)||0)-actualSeconds),progressHtml='<div style="margin-top:11px;padding:9px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;"><div style="display:flex;justify-content:space-between;gap:8px;align-items:center;margin-bottom:6px;"><strong style="color:'+progressColor+';font-size:0.82rem;">'+progressLabel+'</strong><span style="font-size:0.75rem;font-weight:800;color:#475569;">'+actualPct+'% complete</span></div><div style="height:13px;background:#e2e8f0;border-radius:999px;position:relative;overflow:visible;"><div style="height:100%;width:'+actualPct+'%;background:'+progressColor+';border-radius:999px;"></div><span title="Planned progress by today" style="position:absolute;left:calc('+plannedPct+'% - 1px);top:-3px;width:2px;height:19px;background:#334155;border-radius:2px;"></span></div><div style="display:flex;justify-content:space-between;gap:8px;flex-wrap:wrap;margin-top:6px;font-size:0.69rem;color:#64748b;"><span>Listened: <strong>'+formatSecondsLong(actualSeconds)+'</strong></span><span>Planned by today: <strong>'+formatSecondsLong(plannedSeconds)+'</strong></span><span>Remaining: <strong>'+formatSecondsLong(remainingSeconds)+'</strong></span></div></div>';
  while(cursor<=end){var weekEnd=new Date(cursor);weekEnd.setDate(weekEnd.getDate()+6);if(weekEnd>end)weekEnd=new Date(end);var through=Math.floor((weekEnd-start)/86400000)+1,pct=Math.min(100,Math.round(through/p.days*100));milestones.push('<div style="display:flex;justify-content:space-between;gap:8px;padding:4px 0;border-bottom:1px solid #eceeff;"><span>Week '+week+': '+cursor.toLocaleDateString('en-US',{month:'short',day:'numeric'})+'–'+weekEnd.toLocaleDateString('en-US',{month:'short',day:'numeric'})+'</span><strong>'+pct+'%</strong></div>');cursor=new Date(weekEnd);cursor.setDate(cursor.getDate()+1);week++;}
  result.style.display='block';
  result.innerHTML='<div style="background:#fff;border:1px solid #dfe3ff;border-radius:8px;padding:10px;"><div style="display:flex;gap:10px;align-items:flex-start;">'
    +(p.artwork?'<img src="'+p.artwork+'" style="width:54px;height:54px;object-fit:cover;border-radius:6px;flex-shrink:0;">':'')
    +'<div style="flex:1;min-width:0;"><div style="font-weight:800;color:#37308f;">'+escHtml(p.title)+'</div>'+(p.author?'<div style="font-size:0.76rem;color:#666;">by '+escHtml(p.author)+'</div>':'')
    +'<div style="font-size:0.76rem;color:#555;margin-top:4px;">Total: <strong>'+formatSecondsLong(p.totalSeconds)+'</strong> &bull; '+p.days+' day'+(p.days===1?'':'s')+'</div><div style="font-size:0.84rem;color:#4338ca;margin-top:3px;">Listen <strong>'+formatSecondsLong(p.dailySeconds)+'</strong> per day</div></div>'
    +'<button onclick="deleteAudiobookFinishPlan()" title="Remove plan" style="background:none;border:1px solid #ddd;border-radius:5px;color:#999;cursor:pointer;">&#x2715;</button></div>'+progressHtml+'<div style="font-size:0.75rem;color:#555;margin-top:9px;">'+milestones.join('')+'</div><div style="font-size:0.69rem;color:#888;margin-top:7px;">Schedule based on '+escHtml(p.source||'the audiobook length you entered')+'.</div></div>';
  var title=document.getElementById('audioPlanBook');if(title&&!title.value)title.value=p.query||p.title||'';
  var author=document.getElementById('audioPlanAuthor');if(author&&!author.value)author.value=p.queryAuthor||p.author||'';
  var hours=document.getElementById('audioPlanHours'),minutes=document.getElementById('audioPlanMinutes'),totalMinutes=Math.round((Number(p.totalSeconds)||0)/60);if(hours&&!hours.value)hours.value=Math.floor(totalMinutes/60)||'';if(minutes&&!minutes.value)minutes.value=totalMinutes%60||'';
  var startDate=document.getElementById('audioPlanStartDate');if(startDate&&!startDate.value)startDate.value=p.startDate||'';
  var date=document.getElementById('audioPlanDate');if(date&&!date.value)date.value=p.finishDate||'';
}
function deleteAudiobookFinishPlan(){if(!confirm('Remove this audiobook completion plan?'))return;localStorage.removeItem('audiobook_finish_plan');renderAudiobookFinishPlan();}

function _findCover(title, author, onResults){
  var covers=[];
  var done=0;
  function clean(v){return String(v||'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();}
  function score(candidateTitle){
    var wanted=clean(title),found=clean(candidateTitle);
    if(found===wanted)return 100;
    if(found.indexOf(wanted)>=0||wanted.indexOf(found)>=0)return 70;
    var words=wanted.split(' ').filter(Boolean);
    return words.reduce(function(n,w){return n+(found.indexOf(w)>=0?5:0);},0);
  }
  function addCover(item){
    if(!item||!item.url)return;
    if(covers.some(function(c){return c.url===item.url;}))return;
    item.score=score(item.title);
    covers.push(item);
  }
  function finish(){
    done++;
    if(done>=3){
      covers.sort(function(a,b){return(b.score||0)-(a.score||0);});
      onResults(covers.slice(0,12));
    }
  }

  // Google Books
  var gbTerms='intitle:"'+(title||'')+'"'+(author?' inauthor:"'+author+'"':'');
  fetch('https://www.googleapis.com/books/v1/volumes?q='+encodeURIComponent(gbTerms)+'&maxResults=10&printType=books')
    .then(function(r){return r.json();})
    .then(function(data){
      (data.items||[]).forEach(function(item){
        var info=item.volumeInfo||{};
        var imgs=info.imageLinks||{};
        var url=imgs.extraLarge||imgs.large||imgs.medium||imgs.thumbnail||'';
        if(url){
          url=url.replace('http://','https://').replace('&edge=curl','');
          addCover({title:info.title||title,url:url,source:'Google Books'});
        }
      });
    }).catch(function(){}).then(finish);

  // Open Library
  var olUrl='https://openlibrary.org/search.json?title='+encodeURIComponent(title||'')+(author?'&author='+encodeURIComponent(author):'')+'&fields=title,author_name,cover_i&limit=10';
  fetch(olUrl)
    .then(function(r){return r.json();})
    .then(function(data){
      (data.docs||[]).forEach(function(doc){
        if(doc.cover_i){
          var url='https://covers.openlibrary.org/b/id/'+doc.cover_i+'-L.jpg';
          addCover({title:doc.title||title,url:url,source:'Open Library'});
        }
      });
    }).catch(function(){}).then(finish);

  // Apple Books / Audiobooks often has the most accurate popular-edition artwork.
  fetch('https://itunes.apple.com/search?term='+encodeURIComponent((title||'')+(author?' '+author:''))+'&media=audiobook&entity=audiobook&limit=12')
    .then(function(r){return r.json();})
    .then(function(data){
      (data.results||[]).forEach(function(item){
        var url=item.artworkUrl600||item.artworkUrl100||item.artworkUrl60||'';
        if(url)url=url.replace(/\/\d+x\d+bb\./,'/600x600bb.');
        addCover({title:item.collectionName||item.trackName||title,url:url,source:'Apple Audiobooks'});
      });
    }).catch(function(){}).then(finish);
}

function findNewBookCover(){
  var title=(document.getElementById('newBookTitle')||{}).value||'';
  var author=(document.getElementById('newBookAuthor')||{}).value||'';
  if(!title){alert('Enter a book title first.');return;}
  var wrap=document.getElementById('newCoverPickerWrap');
  if(wrap){wrap.style.display='block';wrap.innerHTML='<div style="color:#aaa;font-size:0.8rem;padding:8px;">Searching...</div>';}
  _findCover(title,author,function(covers){
    if(!covers.length){
      if(wrap)wrap.innerHTML='<div style="color:#e74c3c;font-size:0.8rem;padding:8px;">No covers found. Try uploading manually.</div>';
      return;
    }
    if(wrap){
      wrap.innerHTML='<div style="display:flex;gap:6px;flex-wrap:wrap;padding:4px 0;">'
        +covers.map(function(c,i){
          return '<div style="cursor:pointer;border:2px solid transparent;border-radius:6px;padding:2px;" onclick="selectNewBookCover(\''+c.url.replace(/'/g,"\\'")+'\')">'
            +'<img src="'+c.url+'" style="width:50px;height:68px;object-fit:cover;border-radius:5px;" title="'+c.source+'">'
            +'<div style="font-size:0.62rem;color:#888;text-align:center;">'+c.source+'</div>'
            +'</div>';
        }).join('')
        +'</div>';
    }
  });
}

function selectNewBookCover(url){
  // Store pending cover for new book (will be saved to IndexedDB when book is added)
  var _img_nbc=new Image();_img_nbc.crossOrigin='anonymous';
  _img_nbc.onload=function(){
    try{_pendingCoverData=_resizeToDataUrl(_img_nbc,160,220,0.85);}catch(e){_pendingCoverData=url;}
    document.getElementById('newBookCoverUrl').value='__pending__';
  };
  _img_nbc.onerror=function(){_pendingCoverData=url;document.getElementById('newBookCoverUrl').value='__pending__';};
  _img_nbc.src=url;
  var prev=document.getElementById('newBookCoverPreview');
  if(prev){prev.src=url;prev.style.display='block';}
  var wrap=document.getElementById('newCoverPickerWrap');
  if(wrap)wrap.style.display='none';
}

var _pendingCoverData=null;
function uploadNewBookCover(ev){
  var file=ev.target.files[0];if(!file)return;
  var reader=new FileReader();
  reader.onload=function(e){
    var img=new Image();
    img.onload=function(){
      try{_pendingCoverData=_resizeToDataUrl(img,160,220,0.85);}
      catch(err){_pendingCoverData=e.target.result;}
      var prev=document.getElementById('newBookCoverPreview');
      if(prev){prev.src=_pendingCoverData;prev.style.display='block';}
      document.getElementById('newBookCoverUrl').value='__pending__';
    };
    img.src=e.target.result;
  };
  reader.readAsDataURL(file);
}

function clearNewBookCover(){
  _pendingCoverData=null;
  document.getElementById('newBookCoverUrl').value='';
  var prev=document.getElementById('newBookCoverPreview');
  if(prev){prev.src='';prev.style.display='none';}
}

function findEditBookCoverQuick(bookId){
  var books=getBooks();var b=books.find(function(x){return x.id===bookId;});
  if(!b)return;
  var wrap=document.getElementById('editCoverPickerWrap_'+bookId);
  if(!wrap)return;
  var isOpen=wrap.style.display!=='none';
  if(isOpen){wrap.style.display='none';return;}
  wrap.style.display='block';
  wrap.innerHTML='<div style="color:#aaa;font-size:0.8rem;padding:8px;">Searching...</div>';
  _findCover(b.title,b.author,function(covers){
    if(!covers.length){
      wrap.innerHTML='<div style="color:#e74c3c;font-size:0.8rem;padding:8px;">No covers found.</div>';
      return;
    }
    wrap.innerHTML='<div style="display:flex;gap:6px;flex-wrap:wrap;padding:4px 0;">'
      +covers.map(function(c){
        return '<div style="cursor:pointer;border-radius:6px;padding:2px;" onclick="applyBookCover('+bookId+',\''+c.url.replace(/'/g,"\\'")+'\')">'
          +'<img src="'+c.url+'" style="width:50px;height:68px;object-fit:cover;border-radius:5px;" title="'+c.source+'">'
          +'<div style="font-size:0.62rem;color:#888;text-align:center;">'+c.source+'</div>'
          +'</div>';
      }).join('')+'</div>';
  });
}

function uploadBookCoverQuick(bookId,input){
  var file=input.files[0];if(!file)return;
  var reader=new FileReader();
  reader.onload=function(e){
    var img=new Image();
    img.onload=function(){
      try{_storeBookCover(bookId,_resizeToDataUrl(img,160,220,0.85));}
      catch(err){_storeBookCover(bookId,e.target.result);}
    };
    img.src=e.target.result;
  };
  reader.readAsDataURL(file);
  input.value='';
}

function applyBookCover(bookId,url){
  if(!bookId)return;
  var _go=function(dataUrl){_storeBookCover(bookId,dataUrl);};
  if(url.startsWith('data:')){
    var img2=new Image();
    img2.onload=function(){try{_go(_resizeToDataUrl(img2,160,220,0.85));}catch(e){_go(url);}};
    img2.src=url;return;
  }
  var img=new Image();img.crossOrigin='anonymous';
  img.onload=function(){try{_go(_resizeToDataUrl(img,160,220,0.85));}catch(e){_go(url);}};
  img.onerror=function(){_go(url);};
  img.src=url;
}

function removeBookCover(bookId){
  if(!confirm('Remove book cover?'))return;
  deleteBookCover(bookId).then(function(){
    var imgEl=document.getElementById('bookCoverImg_'+bookId);
    if(imgEl){imgEl.src='';imgEl.style.display='none';}
  });
}

// AI auto-fill for reading notes
function aiAutoFillNote(bookId){
  var books=getBooks();
  var b=books.find(function(x){return x.id===bookId;});
  if(!b){alert('Book not found.');return;}
  var key=journalAIKey();
  if(!key){alert('Add your Anthropic API key in Settings first.');return;}
  var chapter=(document.getElementById('rnChapter').value||'').trim();
  var context='Book: "'+b.title+'"'+(b.author?' by '+b.author:'')+(chapter?' — Chapter/Section: '+chapter:'');
  var btn=document.getElementById('rnAIFillBtn');
  if(btn){btn.disabled=true;btn.textContent='Filling...';}
  fetch('https://api.anthropic.com/v1/messages',{
    method:'POST',
    headers:{'Content-Type':'application/json','x-api-key':key,'anthropic-version':'2023-06-01','anthropic-dangerous-direct-browser-access':'true'},
    body:JSON.stringify({model:'claude-haiku-4-5-20251001',max_tokens:2500,
      messages:[{role:'user',content:'You are a reading notes assistant. For the following book and chapter, provide concise reading notes. Return ONLY valid JSON with these keys: "quote" (one powerful key quote or passage), "summary" (2-3 sentence summary of the main idea), "insights" (3 bullet-point key insights/takeaways, each on its own line starting with "• ").\n\n'+context+'\n\nReturn only the JSON object, no other text.'}]})
  }).then(function(r){return r.json();}).then(function(d){
    var text=(d.content&&d.content[0]&&d.content[0].text)||'';
    var jsonMatch=text.match(/\{[\s\S]*\}/);
    if(!jsonMatch){throw new Error('No JSON in response');}
    var notes=JSON.parse(jsonMatch[0]);
    if(notes.quote)document.getElementById('rnQuote').value=notes.quote;
    if(notes.summary)document.getElementById('rnSummary').value=notes.summary;
    if(notes.insights)document.getElementById('rnInsights').value=notes.insights;
    if(btn){btn.disabled=false;btn.textContent='✨ AI Fill';}
  }).catch(function(e){
    alert('AI fill failed: '+e.message);
    if(btn){btn.disabled=false;btn.textContent='✨ AI Fill';}
  });
}

var bookCollapsed=new Set();
var bookCollapsedInit=false;
function toggleBookCard(id){
  if(bookCollapsed.has(id))bookCollapsed.delete(id);else bookCollapsed.add(id);
  var body=document.getElementById('bookBody_'+id);
  var chev=document.getElementById('bookChev_'+id);
  if(body)body.style.display=bookCollapsed.has(id)?'none':'block';
  if(chev)chev.innerHTML=bookCollapsed.has(id)?'&#x25B6;':'&#x25BC;';
}
function collapseAllBooks(){
  getBooks().forEach(function(b){bookCollapsed.add(b.id);});
  renderLibrary();
}
function expandAllBooks(){
  bookCollapsed=new Set();
  renderLibrary();
}

function renderLibrary(){
  if(!bookCollapsedInit){
    bookCollapsedInit=true;
    getBooks().forEach(function(b){bookCollapsed.add(b.id);});
  }
  var books=sortBooks(getBooks());
  var cnt=document.getElementById('bookCount');
  if(cnt)cnt.textContent=books.length?books.length+' book'+(books.length!==1?'s':''):'';
  var el=document.getElementById('bookList');if(!el)return;
  if(!books.length){
    el.innerHTML='<div style="text-align:center;color:#aaa;padding:40px 20px;font-size:0.95rem;">No books yet — tap <strong>+ Add Book</strong> to log your first completed audiobook.</div>';
    return;
  }

  var html2='';
  books.forEach(function(b){
    if(b.id===editingBookId){
      html2+='<div class="book-card" style="border-left-color:#e67e22;">'
        +'<div style="font-size:0.78rem;font-weight:700;color:#e67e22;margin-bottom:10px;">&#x270F;&#xFE0F; Editing: '+escHtml(b.title)+'</div>'
        +'<div class="lib-form-grid"><div><label class="lib-edit-label">Title</label><input type="text" id="editBookTitle" value="'+escHtml(b.title)+'" class="lib-edit-input"></div>'
        +'<div><label class="lib-edit-label">Author</label><input type="text" id="editBookAuthor" value="'+escHtml(b.author||'')+'" class="lib-edit-input"></div></div>'
        +'<div class="lib-form-grid" style="margin-bottom:10px;"><div><label class="lib-edit-label">Date Completed</label><input type="date" id="editBookDate" value="'+(b.dateCompleted||'')+'" class="lib-edit-input"></div>'
        +'<div><label class="lib-edit-label">Rating</label><div id="editStarsDisplay" style="display:flex;gap:3px;margin-top:5px;"></div></div></div>'
        +'<div style="margin-bottom:10px;"><label class="lib-edit-label">Notes / Takeaways</label><textarea id="editBookNotes" class="lib-edit-input" style="height:70px;resize:vertical;font-family:inherit;">'+escHtml(b.notes||'')+'</textarea></div>'
        +'<div style="display:flex;gap:8px;justify-content:flex-end;">'
        +'<button onclick="cancelBookEdit()" style="background:#f0f0f0;color:#555;border:none;padding:7px 14px;border-radius:6px;cursor:pointer;font-size:0.88rem;">Cancel</button>'
        +'<button onclick="saveBookEdit('+b.id+')" style="background:#667eea;color:white;border:none;padding:7px 14px;border-radius:6px;cursor:pointer;font-size:0.88rem;font-weight:600;">Save Changes</button>'
        +'</div></div>';
      return;
    }

    var stars=b.rating?('★'.repeat(b.rating)+'☆'.repeat(5-b.rating)):'';
    var date=b.dateCompleted?new Date(b.dateCompleted+'T00:00:00').toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'}):'';
    var collapsed=bookCollapsed.has(b.id);
    var chevron=collapsed?'&#x25B6;':'&#x25BC;';
    var bodyDisplay=collapsed?'none':'block';

    html2+='<div class="book-card" style="padding:0;">';

    // Header row (always visible) - click to collapse
    html2+='<div style="display:flex;align-items:center;gap:10px;padding:12px 14px;cursor:pointer;" onclick="toggleBookCard('+b.id+')">';
    html2+='<img id="bookCoverImg_'+b.id+'" src="" style="width:44px;height:60px;object-fit:cover;border-radius:5px;border:1px solid #e9ecef;flex-shrink:0;display:none;" onerror="this.style.display=\'none\'">';
    html2+='<div style="flex:1;min-width:0;">';
    html2+='<div class="book-title" style="margin:0 0 2px;">'+escHtml(b.title)+'</div>';
    if(b.author)html2+='<div class="book-author">by '+escHtml(b.author)+'</div>';
    html2+='<div class="book-meta" style="margin-top:3px;">';
    if(date)html2+='<span>&#x1F4C5; '+date+'</span>';
    if(stars)html2+='<span class="book-stars">'+stars+'</span>';
    html2+='</div></div>';
    html2+='<div style="display:flex;gap:4px;align-items:center;flex-shrink:0;">';
    html2+='<span id="bookChev_'+b.id+'" style="color:#aaa;font-size:0.75rem;">'+chevron+'</span>';
    html2+='</div></div>';

    // Expandable body
    html2+='<div id="bookBody_'+b.id+'" style="display:'+bodyDisplay+';padding:0 14px 12px;border-top:1px solid #f5f5f5;">';

    // Cover management row
    html2+='<div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:8px;margin-top:8px;align-items:center;">';
    html2+='<button id="bookCoverRemBtn_'+b.id+'" onclick="removeBookCover('+b.id+')" style="display:none;background:#f0f0f0;color:#e74c3c;border:none;border-radius:6px;padding:3px 10px;font-size:0.72rem;cursor:pointer;">Remove Cover</button>';
    html2+='<button id="bookCoverFindBtn_'+b.id+'" onclick="findEditBookCoverQuick('+b.id+')" style="background:#667eea;color:#fff;border:none;border-radius:6px;padding:3px 10px;font-size:0.72rem;cursor:pointer;">&#x1F50D; Find Cover</button>';
    html2+='<label style="background:#e67e22;color:#fff;border:none;border-radius:6px;padding:3px 10px;font-size:0.72rem;cursor:pointer;">&#x1F4F7; Upload Cover<input type="file" accept="image/*" style="display:none" onchange="uploadBookCoverQuick('+b.id+',this)"></label>';
    html2+='<button onclick="editBook('+b.id+')" style="background:none;border:1px solid #ddd;border-radius:6px;padding:3px 10px;font-size:0.72rem;cursor:pointer;color:#555;">&#x270F;&#xFE0F; Edit</button>';
    html2+='<button onclick="deleteBook('+b.id+')" style="background:none;border:1px solid #fcc;border-radius:6px;padding:3px 10px;font-size:0.72rem;cursor:pointer;color:#e74c3c;">&#x2715; Delete</button>';
    html2+='</div>';
    html2+='<div id="editCoverPickerWrap_'+b.id+'" style="display:none;margin-bottom:8px;"></div>';

    if(b.notes){
      html2+='<div class="book-notes" style="margin-bottom:8px;">'+escHtml(b.notes)+'</div>';
    }

    // Reading Notes
    html2+='<div style="border-top:1px solid #f0f0f0;padding-top:8px;">';
    html2+='<div style="display:flex;align-items:center;justify-content:space-between;">';
    html2+='<button onclick="toggleBookNotes('+b.id+')" style="background:none;border:none;color:#667eea;font-size:0.78rem;cursor:pointer;padding:0;font-weight:600;">&#x1F4DD; Reading Notes'+(b.readingNotes&&b.readingNotes.length?' ('+b.readingNotes.length+')':'')+'</button>';
    html2+='<button onclick="openReadingNoteModal('+b.id+')" style="background:#667eea;color:#fff;border:none;border-radius:5px;padding:3px 10px;font-size:0.75rem;cursor:pointer;font-weight:600;">+ Add Note</button>';
    html2+='</div>';
    html2+='<div id="rn-'+b.id+'" style="display:none;margin-top:8px;">';
    if(b.readingNotes&&b.readingNotes.length){
      html2+='<div style="max-height:320px;overflow-y:auto;">';
      b.readingNotes.forEach(function(n,ni){
        html2+='<div class="book-rn-entry"><div class="book-rn-header">'
          +'<div class="book-rn-meta">'+n.date+(n.chapter?' — '+escHtml(n.chapter):'')+'</div>'
          +'<button onclick="openReadingNoteModal('+b.id+','+ni+')" style="background:none;border:none;color:#667eea;cursor:pointer;font-size:0.82rem;padding:0 4px 0 0;">&#x270F;&#xFE0F;</button>'
          +'<button onclick="deleteBookNote('+b.id+','+ni+')" style="background:none;border:none;color:#ccc;cursor:pointer;font-size:0.75rem;padding:0;">&#x2715;</button>'
          +'</div>'
          +(n.quote?'<div class="book-rn-quote">'+escHtml(n.quote)+'</div>':'')
          +(n.summary?'<div class="book-rn-label">Summary</div><div class="book-rn-body">'+escHtml(n.summary)+'</div>':'')
          +(n.insights?'<div class="book-rn-label">&#x1F4A1; Key Insights</div><div class="book-rn-body">'+escHtml(n.insights)+'</div>':'')
          +(n.application?'<div class="book-rn-label">&#x1F3AF; Application</div><div class="book-rn-body">'+escHtml(n.application)+'</div>':'')
          +(n.questions?'<div class="book-rn-label">&#x2753; Questions</div><div class="book-rn-body">'+escHtml(n.questions)+'</div>':'')
          +'</div>';
      });
      html2+='</div>';
    }
    // Documents section
    html2+='<div style="border-top:1px solid #f0f0f0;padding-top:8px;margin-top:4px;">';
    html2+='<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:4px;">';
    html2+='<button onclick="toggleBookDocs('+b.id+')" style="background:none;border:none;color:#27ae60;font-size:0.78rem;cursor:pointer;padding:0;font-weight:600;">&#x1F4C1; Documents</button>';
    html2+='<label style="background:#27ae60;color:#fff;border:none;border-radius:5px;padding:3px 10px;font-size:0.72rem;cursor:pointer;font-weight:600;">+ Upload<input type="file" multiple style="display:none" onchange="uploadBookDocument('+b.id+',this)"></label>';
    html2+='</div>';
    html2+='<div id="bookDocsSection_'+b.id+'" style="display:none;">';
    html2+='<div id="bookDocsWrap_'+b.id+'" style="min-height:20px;"></div>';
    html2+='</div>';
    html2+='</div>';
    html2+='</div></div></div></div>';
  });

  el.innerHTML=html2;
  if(editingBookId)buildEditStars();
  _loadLibraryCovers(getBooks());
  renderAudiobookFinishPlan();
}
function _loadLibraryCovers(books){
  books.forEach(function(b){
    getBookCover(b.id).then(function(dataUrl){
      if(!dataUrl)return;
      var imgEl=document.getElementById('bookCoverImg_'+b.id);
      if(imgEl){imgEl.src=dataUrl;imgEl.style.display='block';}
      var remBtn=document.getElementById('bookCoverRemBtn_'+b.id);
      var findBtn=document.getElementById('bookCoverFindBtn_'+b.id);
      if(remBtn)remBtn.style.display='';
      if(findBtn)findBtn.style.display='none';
    });
  });
}


function toggleBookNotes(id){var el=document.getElementById('rn-'+id);if(el)el.style.display=el.style.display==='none'?'block':'none';}
function openReadingNoteModal(bookId,noteIdx){
  var idx=(noteIdx===undefined?-1:noteIdx);
  var books=getBooks();var b=books.find(function(x){return x.id===bookId;});
  document.getElementById('rnModalBookId').value=bookId;
  document.getElementById('rnModalNoteIdx').value=idx;
  document.getElementById('rnModalBookTitle').textContent=b?b.title:'';
  var n=(idx>=0&&b&&b.readingNotes&&b.readingNotes[idx])?b.readingNotes[idx]:{};
  var t2=new Date();
  document.getElementById('rnDate').value=n.isoDate||(t2.getFullYear()+'-'+String(t2.getMonth()+1).padStart(2,'0')+'-'+String(t2.getDate()).padStart(2,'0'));
  document.getElementById('rnChapter').value=n.chapter||'';
  document.getElementById('rnQuote').value=n.quote||'';
  document.getElementById('rnSummary').value=n.summary||'';
  document.getElementById('rnInsights').value=n.insights||'';
  document.getElementById('rnApplication').value=n.application||'';
  document.getElementById('rnQuestions').value=n.questions||'';
  document.getElementById('readingNoteModal').style.display='flex';
  setTimeout(function(){document.getElementById('rnChapter').focus();},80);
}
function closeReadingNoteModal(){document.getElementById('readingNoteModal').style.display='none';}
function saveReadingNoteFromModal(){
  var bookId=document.getElementById('rnModalBookId').value;
  var bIdNum=parseInt(bookId);if(!isNaN(bIdNum))bookId=bIdNum;
  var noteIdx=parseInt(document.getElementById('rnModalNoteIdx').value);
  var isoDate=document.getElementById('rnDate').value;
  var dateStr=isoDate?new Date(isoDate+'T00:00:00').toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'}):new Date().toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'});
  var note={date:dateStr,isoDate:isoDate,chapter:(document.getElementById('rnChapter').value||'').trim(),quote:(document.getElementById('rnQuote').value||'').trim(),summary:(document.getElementById('rnSummary').value||'').trim(),insights:(document.getElementById('rnInsights').value||'').trim(),application:(document.getElementById('rnApplication').value||'').trim(),questions:(document.getElementById('rnQuestions').value||'').trim()};
  if(!(note.chapter||note.quote||note.summary||note.insights||note.application||note.questions)){alert('Please fill in at least one field.');return;}
  var books=getBooks();var i=books.findIndex(function(b){return b.id===bookId;});if(i===-1)return;
  if(!Array.isArray(books[i].readingNotes))books[i].readingNotes=[];
  if(noteIdx>=0)books[i].readingNotes[noteIdx]=note;
  else books[i].readingNotes.unshift(note);
  saveBooks(books);closeReadingNoteModal();renderLibrary();
  var el=document.getElementById('rn-'+bookId);if(el)el.style.display='block';
}
function deleteBookNote(id,ni){
  if(!confirm('Delete this note?'))return;
  var books=getBooks();var i=books.findIndex(function(b){return b.id===id;});if(i===-1)return;
  if(Array.isArray(books[i].readingNotes))books[i].readingNotes.splice(ni,1);
  saveBooks(books);renderLibrary();
  var el=document.getElementById('rn-'+id);if(el)el.style.display='block';
}

// ── Library Export/Import ─────────────────────────────────
function openPasteLibrary(){document.getElementById('pasteLibraryModal').style.display='flex';document.getElementById('pasteLibraryText').value='';setTimeout(()=>document.getElementById('pasteLibraryText').focus(),50);}
function closePasteLibrary(){document.getElementById('pasteLibraryModal').style.display='none';}
function confirmPasteLibrary(){
  const raw=(document.getElementById('pasteLibraryText').value||'').trim();
  if(!raw){alert('Nothing pasted.');return;}
  let incoming;try{incoming=JSON.parse(raw);}catch{alert('Invalid JSON — make sure you copied the full output from the console command.');return;}
  if(!Array.isArray(incoming)||!incoming.length){alert('No books found in the pasted data.');return;}
  const existing=getBooks();
  if(existing.length){
    const choice=confirm('Merge with your '+existing.length+' existing book(s)?\n\nOK = Merge (keep both)\nCancel = Replace (overwrite)');
    if(choice){const ids=new Set(existing.map(b=>b.id));saveBooks([...existing,...incoming.filter(b=>!ids.has(b.id))]);}
    else saveBooks(incoming);
  }else saveBooks(incoming);
  closePasteLibrary();renderLibrary();alert('Done! '+getBooks().length+' book(s) in your library.');
}
function exportLibrary(){
  const books=getBooks();if(!books.length){alert('No books to export.');return;}
  Promise.all([getAllBookDocuments(),getAllBookCovers()]).then(function(results){
    var docs=results[0];var covers=results[1];
    var payload=JSON.stringify({library_books:JSON.stringify(books),book_documents:JSON.stringify(docs),book_covers:JSON.stringify(covers)},null,2);
    var a=document.createElement('a');
    a.href=URL.createObjectURL(new Blob([payload],{type:'application/json'}));
    a.download='planner_library_'+new Date().toISOString().slice(0,10)+'.json';a.click();
  }).catch(function(){
    var a=document.createElement('a');
    a.href=URL.createObjectURL(new Blob([JSON.stringify({library_books:JSON.stringify(books)},null,2)],{type:'application/json'}));
    a.download='planner_library_'+new Date().toISOString().slice(0,10)+'.json';a.click();
  });
}
function importLibrary(ev){
  const file=ev.target.files[0];if(!file)return;
  const reader=new FileReader();
  reader.onload=e=>{
    try{
      const d=JSON.parse(e.target.result);
      const raw=d.library_books;
      if(!raw){alert('No library data found in this file.');ev.target.value='';return;}
      const incoming=JSON.parse(raw);
      if(!Array.isArray(incoming)||!incoming.length){alert('Library file appears empty.');ev.target.value='';return;}
      const existing=getBooks();
      if(existing.length){
        const choice=confirm('Merge with your '+existing.length+' existing book(s)?\n\nOK = Merge\nCancel = Replace');
        if(choice){const ids=new Set(existing.map(b=>b.id));saveBooks([...existing,...incoming.filter(b=>!ids.has(b.id))]);}
        else saveBooks(incoming);
      }else saveBooks(incoming);
      renderLibrary();
      // Restore documents + covers
      try{
        var docPromises=[];var covPromises=[];
        if(d.book_documents){var incomingDocs=JSON.parse(d.book_documents);if(Array.isArray(incomingDocs))docPromises=incomingDocs.map(function(doc){return putBookDocument(doc);});}
        if(d.book_covers){var incomingCovers=JSON.parse(d.book_covers);if(Array.isArray(incomingCovers))covPromises=incomingCovers.map(function(c){return setBookCover(c.bookId,c.dataUrl);});}
        if(docPromises.length||covPromises.length){
          Promise.all(docPromises.concat(covPromises)).then(function(){
            renderLibrary();
            alert('Library imported: '+getBooks().length+' book(s), '+docPromises.length+' doc(s), '+covPromises.length+' cover(s) restored.');
          }).catch(function(){
            renderLibrary();
            alert('Library imported: '+getBooks().length+' book(s) (some items may not have fully restored).');
          });
          return;
        }
      }catch(importErr){}
      alert('Library imported: '+getBooks().length+' book(s) total.');
    }catch(err){alert('Invalid library file: '+err.message);}
    ev.target.value='';
  };
  reader.readAsText(file);
}

// ── AI Settings ───────────────────────────────────────────
function openSettings(){document.getElementById('settingsModal').style.display='flex';loadJournalSettings();}
function closeSettings(){document.getElementById('settingsModal').style.display='none';}
function saveApiKey(){saveJournalSettings();}

// ── Tabs / Print / Backup ─────────────────────────────────
function clearPage(tab){
  let msg='',keys=[];
  if(tab==='daily'){msg='Clear all data for '+fmtD(today)+'?\n\nThis cannot be undone.';keys=['planner_'+dk(today)];}
  else if(tab==='weekly'){const ws=wkStart(weekOff2),we=new Date(ws);we.setDate(ws.getDate()+6);msg='Clear all 7 days ('+ws.toLocaleDateString('en-US',{month:'short',day:'numeric'})+' – '+we.toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'})+')?\n\nThis cannot be undone.';for(let i=0;i<7;i++){const d=new Date(ws);d.setDate(ws.getDate()+i);keys.push('planner_'+dk(d));}}
  else if(tab==='monthly'){const ref=new Date(new Date().getFullYear(),new Date().getMonth()+monthOff2,1);const dim=new Date(ref.getFullYear(),ref.getMonth()+1,0).getDate();msg='Clear all data for '+ref.toLocaleDateString('en-US',{month:'long',year:'numeric'})+'?\n\nThis cannot be undone.';for(let d=1;d<=dim;d++){const dt=new Date(ref.getFullYear(),ref.getMonth(),d);keys.push('planner_'+dk(dt));}}
  else if(tab==='yearly'){const yr=new Date().getFullYear()+yearOff2;msg='Clear ALL data for '+yr+'?\n\nThis cannot be undone.';for(let mo=0;mo<12;mo++){const dim=new Date(yr,mo+1,0).getDate();for(let d=1;d<=dim;d++){keys.push('planner_'+dk(new Date(yr,mo,d)));}}}
  else if(tab==='dashboard'){const ws=wkStart(dashOff2),we=new Date(ws);we.setDate(ws.getDate()+6);msg='Clear all 7 days?\n\nThis cannot be undone.';for(let i=0;i<7;i++){const d=new Date(ws);d.setDate(ws.getDate()+i);keys.push('planner_'+dk(d));}}
  else if(tab==='review'){const ws=wkStart(revOff2);msg='Clear the review for this week?\n\nThis cannot be undone.';keys=['review_'+dk(ws)];}
  if(!keys.length)return;
  if(!confirm(msg))return;
  keys.forEach(k=>localStorage.removeItem(k));
  if(tab==='daily')load();
  else if(tab==='weekly')renderWeekly();
  else if(tab==='monthly')renderMonthly();
  else if(tab==='yearly')renderYearly();
  else if(tab==='dashboard')renderDashboard();
  else if(tab==='review')renderReview();
}
function copyDay(){
  const raw=localStorage.getItem('planner_'+dk(today));
  if(!raw){alert('No data to copy for '+fmtD(today)+'.');return;}
  copiedDayData=raw;
  const btn=document.getElementById('copyDayBtn');
  if(btn){btn.textContent='✓ Copied!';btn.style.background='#27ae60';setTimeout(()=>{btn.textContent='📋 Copy Day';btn.style.background='';},2000);}
}
function pasteDay(){
  if(!copiedDayData){alert('Nothing copied yet. Use Copy Day first.');return;}
  if(!confirm('Paste copied day data to '+fmtD(today)+'?\n\nThis will overwrite any existing data for this date.'))return;
  localStorage.setItem('planner_'+dk(today),copiedDayData);load();
}

/* ========= MEDITATION TAB JS ========= */
