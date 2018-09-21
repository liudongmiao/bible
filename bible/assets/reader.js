!function(){void 0===window.android&&(window.android={setVerse:function(e){console.log("android.setVerse: "+e)},setHighlight:function(e){console.log("android.setHighlight: "+e)},setCopyText:function(e){console.log("android.setCopyText: "+e)},showAnnotation:function(e,t){console.log("android.showAnnotation: "+e+", "+t)},showNote:function(e){console.log("android.showNote: "+e)},setTop:function(e){console.log("android.setTop: "+e)},onLoaded:function(){console.log("android.onLoaded")}});var s,l,c,d=window.android,u="selection",m="-",f="(?:^|\\s)",g="";function p(e){var t=[];function n(e,t,n,o){var i=e.querySelectorAll(n);Array.prototype.forEach.call(i,function(e){o&&!C(e,o,!1)||t.push(e)})}return n(e=e||document,t,".chapternum"),n(e,t,"sup.versenum"),0==t.length&&n(e,t,"sup",l),t}function N(e){return C(e,"chapternum")?["1"]:e.textContent.trim().split(m)}function n(e){var t,n,o,i,r,a,s,l,c,d=g,u=g,f=NaN;for(o=document.createElement("div"),n=(i=document.querySelectorAll(e)).length,t=0;t<n;++t)d+=" "+i.item(t).innerHTML;for(o.innerHTML=d,n=(r=p(o)).length,t=0;t<n;++t)if(s=N(r[t]),l=parseInt(s[0].trim()),!isNaN(l))for(r[t].parentNode.removeChild(r[t]),(c=1<s.length?parseInt(s[1].trim()):NaN)&&!isNaN(c)||(c=l),a=l;a<=c;++a)isNaN(f)?u+=a:a==f||(a-f==1?u.endsWith(m)||(u+=m):(u.endsWith(m)&&(u+=f),u+=",",u+=a)),f=a;function h(e){var t=o.querySelectorAll(e);Array.prototype.forEach.call(t,function(e){e.parentNode.removeChild(e)})}return u.endsWith(m)&&!isNaN(f)&&(u+=f),h(".notelink"),h(".footnote"),h(".crossreference"),{verse:u,text:o.textContent.replace(/^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g,g).trim()}}function a(){var e,t=n("."+u);t.text?(e=document.querySelectorAll(".highlight."+u).length,d.setCopyText(g+e+"\n"+t.verse+"\n"+t.text)):d.setCopyText(g)}function h(e){var i,t=e.className;i=!C(e,u),t.match(/\S+/g).forEach(function(e){var t,n,o;if(l&&-1!=e.indexOf(l))for(t=(o=document.getElementsByClassName(e)).length-1;-1<t;--t)"SUP"!=(n=o.item(t)).nodeName&&(i?S(n,u):A(n,u))}),a()}function w(e){var t,n,o,i,r,a;return(o=e.getBoundingClientRect()).width||o.height?(i=e.ownerDocument,n=null!=(a=r=i)&&a===a.window?r:9===r.nodeType&&r.defaultView,t=i.documentElement,Math.round(o.top+n.pageYOffset-t.clientTop)):Math.round(o.top)}function v(e,n){var t=document.querySelectorAll(e);Array.prototype.forEach.call(t,function(e){var t=e.getAttribute("value")||e.getAttribute("data-link");e.innerHTML=t?n?t.replace(/^.*?(<a.*?>).*?(<\/a>).*$/g,"$1"+n+"$2"):t:n})}function y(e){var t,n,o;if(window.search){for(e=null==e?"highlight":e.trim(),t=(o=document.getElementsByClassName(e)).length-1;-1<t;--t)(n=o.item(t)).className.trim()==e&&1==n.childNodes.length&&3==n.firstChild.nodeType?n.parentNode.replaceChild(n.firstChild,n):A(n,e);window.search=g,document.normalize()}}function x(e){var t,n,o,i,r=l+c+e,a=document.getElementsByClassName(r);if(a.length)return a;if("v"==r[0])for(n=["a","b","c"],t=0;t<3&&!(a=document.getElementsByClassName(r+n[t])).length;++t);else{for(i=new RegExp(f+r+"-\\S+"),s||(s=document.querySelectorAll(".text")),t=0;t<s.length&&!(o=s.item(t).className.match(i));++t);t!=s.length&&(a=document.getElementsByClassName(o[0]))}return a.length||e<2?a:x(e-1)}function b(e,t,n){var o,i,r;if(e)for(o=(r=x(e)).length-1;-1<o;--o)"SUP"!=(i=r.item(o)).nodeName&&(!1===t?A(i,n):S(i,n))}function S(e,t){e&&(e.className?C(e,t)||(e.className+=" "+t):e.className=t)}function C(e,t,n){var o;return!(!e||!e.className)&&(o=!1===n?new RegExp(f+t):new RegExp(f+t+"(?!\\S)"),null!=e.className.match(o))}function A(e,t){var n;e&&e.className&&(n=new RegExp(f+t+"(?!\\S)"),e.className.match(n)&&(e.className=e.className.replace(n,g).trim()))}function E(){if(null==l||null==c){try{c=m,l=document.querySelector(".text").className.replace(/\s*text\s*/g,g).split(c).splice(0,2).join(c)}catch(e){}try{l&&-1!=l.indexOf(c)||(c="_",(l=document.querySelector("sup").className.split(c).splice(0,2).join(c))&&-1!=l.indexOf(c)||(c=l=g))}catch(e){}}}function T(e,i,r){E(),l&&(void 0===r&&(r="highlight"),e.split(",").forEach(function(e){var t,n=e.split(m),o=parseInt(n[0]);if(2==n.length)for(t=parseInt(n[1]);o<=t;++o)b(o,i,r);else b(o,i,r)}))}function L(e,t){T(e,t,u)}function q(e,t){var n,o,i,r=x(e);if(r.length)for(n=0;n<r.length;++n)if(1==(i=r.item(n)).childNodes.length&&(i=i.querySelector("sup.versenum")||i.querySelector(".chapternum")||i.querySelector("sup"))&&(i=i.parentNode),i&&i.firstChild&&i.firstChild.nextSibling)return void(!1===t&&"IMG"==i.firstChild.nextSibling.nodeName?i.removeChild(i.firstChild.nextSibling):!1!==t&&"IMG"!=i.firstChild.nextSibling.nodeName&&((o=document.createElement("img")).setAttribute("class","note"),o.setAttribute("data-note",e),o.setAttribute("src","note.png"),i.insertBefore(o,i.firstChild.nextSibling)))}String.prototype.endsWith||Object.defineProperty(String.prototype,"endsWith",{writable:!1,enumerable:!1,configurable:!1,value:function(e){return-1!==this.indexOf(e,this.length-e.length)}}),window.addEventListener("load",function(){var e,t,n,o,i='<span class="icon-footnote"></span>',r='<span class="icon-crossreference"></span>';E(),function(){var e,t,n,o,i,r,a=g;if(n=window.search){for(i=(o=document.body.innerHTML).toLowerCase(),r=n.toLowerCase();0<o.length&&(t=n,!((e=i.indexOf(r))<0));)o.lastIndexOf(">",e)>=o.lastIndexOf("<",e)&&(t='<span class="'+u+'">'+n+"</span>"),a+=o.substring(0,e)+t,o=o.substr(e+n.length),i=i.substr(e+n.length);a+=o,document.body.innerHTML=a}}(),e=window.highlighted,t=window.selected,n=window.notes,e&&T(e),t&&(L(t),a()),"[object Array]"==Object.prototype.toString.call(n)&&n.forEach(function(e){q(e)}),document.querySelector("#content").addEventListener("click",function(e){for(var t,n,o=e.target;o&&"BODY"!=o.nodeName;){if("IMG"==o.nodeName){if(t=o.getAttribute("data-note"))return e.preventDefault(),void d.showNote(t)}else if("A"==o.nodeName&&(C(o,"notelink")||o.parentNode&&"SUP"==o.parentNode.nodeName)){if(n=o.hash.replace("#",g))return e.preventDefault(),void d.showAnnotation(n,g)}else{if("A"==o.nodeName&&C(o,"bibleref"))return e.preventDefault(),void d.showAnnotation("cross",o.outerHTML);if("SPAN"==o.nodeName&&(C(o,"text")||C(o,l,!1))){y(u),h(o);break}}o=o.parentNode}}),(o=document.querySelector("#pb-demo"))&&(o.style.display="block"),v('a[id*="!f."]',i),v('a[id*="!x."]',r),v("sup.footnote",i),v("sup.crossreference",r),function i(e){setTimeout(function(){try{d.onLoaded(),!(o=window.verse)||parseInt(o)<=1||0<(n=x(o)).length&&(t=n.item(0),e=w(t)-5,d.setTop(e))}catch(e){console.log("error calling android.onLoaded"),console.log(e),i(500)}var e,t,n,o},e)}(100)}),window.getFirstVisibleVerse=function(e){var t,n,o,i,r,a=NaN;for(n=(i=p()).length,t=0;t<n;++t){if(e<=(r=w(i[t]))){o=isNaN(a)||Math.abs(a-e)>3*(r-e)?i[t]:i[t-1];break}a=r}o&&d.setVerse(parseInt(N(o)[0].trim()))},window.setHighlightVerses=function(e,t){T(e,t),d.setHighlight(n(".highlight").verse),T(e,!1,u)},window.addNote=q,window.selectVerses=L}();