/* Shared seasonal theme switcher for My Life Command Center + the apps hub.
   Reads/writes one localStorage key so the chosen theme is shared across every page
   that includes this script. Safe to include on pages that don't have every hook
   (h2Color input, .header h1 emoji span, etc.) -- each lookup is guarded. */
(function(){
  var THEME_KEY = 'app_theme';
  var THEMES = {
    halloween:    { label: 'Halloween',    emoji: '🎃', color: '#ff6b00' },
    thanksgiving: { label: 'Thanksgiving', emoji: '🦃', color: '#d97706' },
    christmas:    { label: 'Christmas',    emoji: '🎄', color: '#c21807' },
    newyear:      { label: "New Year's",   emoji: '🎉', color: '#ffc107' },
    winter:       { label: 'Winter',       emoji: '❄️', color: '#0c4a6e' },
    spring:       { label: 'Spring',       emoji: '🌸', color: '#15803d' },
    july4:        { label: '4th of July',  emoji: '🎆', color: '#dc2626' }
  };
  var DEFAULT_THEME = 'halloween';
  var THEME_ORDER = ['halloween','thanksgiving','christmas','newyear','winter','spring','july4'];

  function getSavedTheme(){
    try{
      var t = localStorage.getItem(THEME_KEY);
      if(t && THEMES[t]) return t;
    }catch(e){}
    return DEFAULT_THEME;
  }

  function applyThemeAttr(theme){
    var meta = THEMES[theme] || THEMES[DEFAULT_THEME];
    document.documentElement.setAttribute('data-theme', theme);
    var metaTag = document.querySelector('meta[name="theme-color"]');
    if(metaTag) metaTag.setAttribute('content', meta.color);
  }

  /* Apply immediately -- this script tag must load before the page paints
     (placed early in <head>, before the main stylesheet) so there's no flash
     of the wrong theme. */
  applyThemeAttr(getSavedTheme());

  function syncDomForTheme(theme){
    var meta = THEMES[theme] || THEMES[DEFAULT_THEME];
    var emojiEls = document.querySelectorAll('[data-theme-emoji]');
    for(var i=0;i<emojiEls.length;i++){ emojiEls[i].textContent = meta.emoji; }

    var h2Color = document.getElementById('h2Color');
    if(h2Color){
      try{
        var brand = getComputedStyle(document.documentElement).getPropertyValue('--brand').trim();
        if(brand) h2Color.value = brand;
      }catch(e){}
    }

    var btns = document.querySelectorAll('.theme-btn[data-theme-choice]');
    for(var j=0;j<btns.length;j++){
      var isActive = btns[j].getAttribute('data-theme-choice') === theme;
      btns[j].classList.toggle('active', isActive);
      btns[j].setAttribute('aria-pressed', isActive ? 'true' : 'false');
    }

    var sel = document.getElementById('themeSelect');
    if(sel) sel.value = theme;
  }

  window.setAppTheme = function(theme){
    if(!THEMES[theme]) theme = DEFAULT_THEME;
    try{ localStorage.setItem(THEME_KEY, theme); }catch(e){}
    applyThemeAttr(theme);
    syncDomForTheme(theme);
  };

  window.getAppTheme = getSavedTheme;
  window.APP_THEME_ORDER = THEME_ORDER;
  window.APP_THEMES = THEMES;

  document.addEventListener('DOMContentLoaded', function(){
    syncDomForTheme(getSavedTheme());
  });
})();
