function getFirstVisibleVerse() {
    var verse="", position=NaN, verses;
    verses = document.getElementsByClassName("pb-verse");
    for (var i = 0; i < verses.length; i++) {
        var newposition = Math.round(verses[i].getBoundingClientRect().top);
        if (newposition >= 0) {
            if (isNaN(position) || Math.abs(position) > 3 * newposition) {
                verse = verses[i].getAttribute("title");
            } else {
                verse = verses[i - 1].getAttribute("title");
            }
            break;
        } else {
            position = newposition;
        }
    }

    android.setVerse(verse);
}

function getCopyText() {
    var html = "", text="", content, span, spans, verse = "", verses = [];
    span = document.createElement("span");
    spans = document.getElementsByTagName("span");
    for (var i = 0; i < spans.length; i++) {
        if (hasClass(spans[i], "selected", true)) {
            html += " " + spans[i].innerHTML;
        }
    }
    content = html.replace(new RegExp('<span class="pb-verse.*?</span>', 'gi'), "");
    
    // get verse
    span.innerHTML = html;
    spans = span.getElementsByClassName("pb-verse");
    for (var i = 0; i < spans.length; i++) {
        verses.push(spans[i].getAttribute("title"))
    }
    verse = verses.join(",");

    // get content
    span.innerHTML = content;

    text = verse + "\n" + span.textContent.replace(/^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g, "");

    android.setCopyText(text);
}

function selectVerse(element) {
    if (element.className.match(/(?:^|\s)selected(?!\S)/)) {
        element.className = element.className.replace(/(?:^|\s)selected(?!\S)/g, '');
    } else {
        element.className += " selected";
    }
    alarm.setup(getCopyText, 3000);
}

function hasClass(element, name, strict) {
    var className = " " + name;
    if (strict) {
        className += " ";
    }
    return (" " + element.className + " ").replace(/[\t\r\n]/g, " ").indexOf(className) >= 0;
}

function load() {
    var spans = document.getElementsByTagName("span");
    for (var i = 0; i < spans.length; i++) {
        if (hasClass(spans[i], "text", true) || hasClass(spans[i], "v", false)) {
            spans[i].addEventListener("click", function() {
                selectVerse(this);
            });
        }
    }
}

var alarm = {
  setup: function(callback, timeout) {
    this.cancel();
    this.timeoutID = window.setTimeout(function(func) {
        func();
    }, timeout, callback);
  },
 
  cancel: function() {
    if(typeof this.timeoutID == "number") {
      window.clearTimeout(this.timeoutID);
      delete this.timeoutID;
    }
  }
};

window.addEventListener("load", load);
