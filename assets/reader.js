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

// http://stackoverflow.com/questions/280634/endswith-in-javascript
if (typeof String.prototype.endsWith !== 'function') {
    String.prototype.endsWith = function(suffix) {
        return this.indexOf(suffix, this.length - suffix.length) !== -1;
    };
}

function getCopyText() {
    var html = "", text="", content, span, spans, verse = "", lastverse=NaN;
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
        var title = parseInt(spans[i].getAttribute("title"));
        if (isNaN(title)) {
            continue;
        }
        if (isNaN(lastverse)) {
            verse += title;
        } else if (title - lastverse == 1) {
            if (!verse.endsWith("-")) {
                verse += "-";
            }
        } else {
            if (verse.endsWith("-")) {
                verse += lastverse;
            }
            verse += ",";
            verse += title;
        }
        lastverse = title;
    }

    if (verse.endsWith("-") && !isNaN(lastverse)) {
        verse += lastverse;
    }

    // get content
    span.innerHTML = content;
    text = span.textContent.replace(/^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g, "");

    if (text) {
        android.setCopyText(verse + "\n" + text);
    } else {
        android.setCopyText("");
    }
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

function highlight(search) {
    var newbody = "", body, lowerbody, lowersearch;
    if (!search) {
        return false;
    }
    body = document.body.innerHTML;
    lowerbody = body.toLowerCase();
    lowersearch = search.toLowerCase();

    while (body.length > 0) {
        var replace = search;
        var i = lowerbody.indexOf(lowersearch);
        if (i < 0) {
            break;
        }
        if (body.lastIndexOf(">", i) >= body.lastIndexOf("<", i)) {
            replace = "<span class=\"highlight\">" + search + "</span>";
        }
        newbody += body.substring(0, i) + replace;
        body = body.substr(i + search.length);
        lowerbody = lowerbody.substr(i + search.length);
    }

    newbody += body;
    document.body.innerHTML = newbody;
    return true;
}

function load() {
    if (verse_start > 1) {
        window.location.hash = "#" + versename + "-" + verse_start;
    }
    highlight(search);
    addListener();
}

function unhighlight() {
    while (true) {
        var results = document.getElementsByClassName("highlight");
        if (results.length == 0) {
            break;
        }
        results[0].className = "";
    }
}

function addListener() {
    document.getElementById("content").addEventListener("click", function(e) {
        var element = e.target;
        while (element && element.nodeName != "BODY") {
            if (element.nodeName == "SPAN" && (hasClass(element, "text", true) || hasClass(element, "v", false))) {
                unhighlight();
                selectVerse(element);
                break;
            }
            element = element.parentNode;
        }
    });
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
