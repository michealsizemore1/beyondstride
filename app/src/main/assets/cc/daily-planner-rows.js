/* Settings-style rows for every collapsible area (matches the BeyondStride app, Nov 2026).
 * - Section headers become clean rows: icon · title · gray count on the right · › chevron that turns down when open.
 * - Every ▶ / ▼ toggle arrow on the page (static or drawn later by any tab) is shown as that › chevron.
 *   The arrow characters stay in the page (only hidden), so all existing open/close code keeps working.
 * Nothing is removed. */
(function(){
  var css=''
  // The chevron that replaces ▶ ▼ ▲ ► ▸ ▾.
  +'.cc-chev{font-size:0!important;display:inline-flex;align-items:center;justify-content:center;width:14px;flex:0 0 auto;line-height:1;}'
  +'.cc-chev::before{content:"\\203A";font-size:20px;font-weight:700;color:#98a2b3;display:inline-block;transition:transform .15s ease,color .15s ease;transform:rotate(var(--cc-rot,0deg));}'
  +'.cc-chev[data-open="1"]::before{color:#6366f1;}'
  // In a row (flex) the chevron sits at the right end.
  +'.cc-chev.cc-end{order:99;margin-left:8px;}'
  // Section header rows.
  +'.section-title.compact-toggle{text-transform:none!important;letter-spacing:0!important;font-size:.95rem!important;font-weight:700!important;color:#1f2937!important;margin:0!important;padding:4px 2px!important;}'
  +'.section-title.compact-toggle[aria-expanded="true"]{padding-bottom:10px!important;margin-bottom:8px!important;border-bottom:1px solid #eef0f4;}'
  +'.compact-toggle-count{font-size:.8rem!important;color:#98a2b3!important;font-weight:600!important;}'
  // ✨ AI Tools: a row card like the others.
  +'#aiToolbar{background:transparent!important;border-bottom:none!important;padding:8px 10px 0!important;}'
  +'#aiToolsToggle{background:#fff;border:1px solid #e4e7ec;border-radius:14px;padding:12px 14px;color:#1f2937!important;font-size:.95rem;box-shadow:0 1px 3px rgba(16,24,40,.06);box-sizing:border-box;}'
  +'#aiToolsToggle[aria-expanded="true"]{border-color:#c7d2fe;}'
  // 📝 Tab Notes row.
  +'.compact-mini-bar{border-radius:14px!important;background:#fff!important;box-shadow:0 1px 3px rgba(16,24,40,.06);}'
  +'.compact-mini-head{padding:12px 14px!important;font-size:.92rem!important;color:#1f2937!important;}'
  +'.compact-mini-head span:last-child{font-size:.8rem!important;}'
  // <details> rows (Calendar, My Schedule, …): › points right when closed, down when open.
  +'.journal-home summary:after,.journal-calendar summary:after{content:"\\203A"!important;font-size:20px!important;font-weight:700;transform:none!important;transition:transform .15s ease;color:#98a2b3!important;}'
  +'.journal-home[open]>summary:after,.journal-calendar[open]>summary:after{transform:rotate(90deg)!important;color:#6366f1!important;}'
  +'.journal-home summary,.journal-calendar summary{background:#fff!important;font-size:.92rem!important;color:#1f2937!important;padding:12px 14px!important;}'
  +'.h2-card>summary,.handwriting-wrap>summary{list-style:none;display:flex;align-items:center;gap:8px;}'
  +'.h2-card>summary::-webkit-details-marker,.handwriting-wrap>summary::-webkit-details-marker{display:none;}'
  +'.h2-card>summary:after,.handwriting-wrap>summary:after{content:"\\203A";margin-left:auto;font-size:20px;font-weight:700;color:#98a2b3;transition:transform .15s ease;}'
  +'.h2-card[open]>summary:after,.handwriting-wrap[open]>summary:after{transform:rotate(90deg);color:#6366f1;}';
  var st=document.createElement('style');st.id='ccRowsStyle';st.textContent=css;document.head.appendChild(st);

  var ROT={'▶':0,'►':0,'▸':0,'▼':90,'▾':90,'▲':-90};
  var GLYPH=/^[▶►▸▼▾▲]$/;
  var LEAD=/^\s*([▶►▸▼▾▲])\s+(\S[\s\S]*)$/;
  // Play / start buttons and move up/down buttons keep their arrows.
  var KEEP=/^(Start|Resume|Play|Pause|Fill|Listen|Read aloud|Run|Go|Next|Prev)/i;
  function keepEl(el){
    if(!el)return true;
    if(el.closest&&el.closest('svg,script,style,textarea,input,select,option,[contenteditable="true"],.cc-no-chev'))return true;
    var t=(el.getAttribute&&((el.getAttribute('title')||'')+' '+(el.getAttribute('aria-label')||'')))||'';
    return /move|up|down|play|start/i.test(t);
  }
  function mark(el,g){
    el.classList.add('cc-chev');
    var r=ROT[g]||0;el.style.setProperty('--cc-rot',r+'deg');el.setAttribute('data-open',r!==0?'1':'0');
    var p=el.parentElement;
    if(p&&el===p.firstElementChild){var d=getComputedStyle(p).display;if(d==='flex'||d==='inline-flex')el.classList.add('cc-end');}
  }
  function doText(n){
    var el=n.parentElement;if(!el||keepEl(el))return;
    var s=n.data,t=s.trim();if(!t)return;
    if(GLYPH.test(t)){
      // The arrow is the element's only text: style the element itself.
      if(el.childNodes.length===1&&el.tagName!=='BUTTON'){mark(el,t);return;}
      if(el.tagName==='BUTTON'&&el.childNodes.length===1&&!keepEl(el)){var sp0=document.createElement('span');sp0.textContent=t;n.parentNode.replaceChild(sp0,n);mark(sp0,t);return;}
      if(el.classList.contains('cc-chev'))return;
      var sp=document.createElement('span');sp.textContent=t;n.parentNode.replaceChild(sp,n);mark(sp,t);return;
    }
    var m=LEAD.exec(s);
    if(m&&!KEEP.test(m[2])){
      if(el.classList.contains('cc-chev'))return;
      var c=document.createElement('span');c.textContent=m[1];
      n.parentNode.insertBefore(c,n);n.data=' '+m[2];mark(c,m[1]);c.classList.remove('cc-end');c.style.marginRight='4px';
    }
  }
  function scan(root){
    if(!root)return;
    if(root.nodeType===3){doText(root);return;}
    if(root.nodeType!==1)return;
    var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){return /[▶►▸▼▾▲]/.test(n.data)?1:3;}});
    var list=[],n;while((n=w.nextNode()))list.push(n);
    list.forEach(doText);
  }
  // Re-mark when code flips an arrow (▶ ↔ ▼) or draws new content.
  function refresh(el){if(el&&el.classList&&el.classList.contains('cc-chev')){var g=el.textContent.trim();if(GLYPH.test(g)){var r=ROT[g]||0;el.style.setProperty('--cc-rot',r+'deg');el.setAttribute('data-open',r!==0?'1':'0');}}}
  var busy=false;
  var mo=new MutationObserver(function(muts){
    if(busy)return;busy=true;
    try{muts.forEach(function(m){
      if(m.type==='characterData'){refresh(m.target.parentElement);doText(m.target);}
      else{refresh(m.target);for(var i=0;i<m.addedNodes.length;i++)scan(m.addedNodes[i]);}
    });}catch(e){}
    busy=false;
  });
  function start(){scan(document.body);mo.observe(document.body,{childList:true,subtree:true,characterData:true});}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);else start();
  window.ccRowsScan=scan;
})();
