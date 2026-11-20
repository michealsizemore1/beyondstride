// Shared floating home button, used by every app in this suite except the
// ones that build their own home link into their topbar (msiz-terminal.html,
// track-field.html) — those keep their own markup on purpose.
//
// This used to be the exact same <a id="homeButton">...</a> line pasted into
// every file. Include this script at the same spot that line used to sit
// (position:fixed means the exact DOM position doesn't affect layout, only
// that the element exists) and it inserts itself right there.
(function(){
  var HOME_BUTTON_HTML = '<a id="homeButton" href="index.html" title="Home" aria-label="Return to My Life Master App home" style="position:fixed;left:10px;bottom:10px;z-index:2147483000;display:inline-flex;align-items:center;justify-content:center;width:36px;height:36px;background:#14161a;color:#fff;text-decoration:none;font:700 18px/1 -apple-system,Segoe UI,Roboto,Arial,sans-serif;padding:0;border-radius:50%;box-shadow:0 4px 12px rgba(0,0,0,.24);opacity:.68;border:1px solid rgba(255,255,255,.18)" onmouseover="this.style.opacity=1" onmouseout="this.style.opacity=.68">\u2302</a>';
  var thisScript = document.currentScript;
  if (thisScript && thisScript.insertAdjacentHTML) {
    thisScript.insertAdjacentHTML('afterend', HOME_BUTTON_HTML);
  } else if (document.body) {
    document.body.insertAdjacentHTML('afterbegin', HOME_BUTTON_HTML);
  }
})();
