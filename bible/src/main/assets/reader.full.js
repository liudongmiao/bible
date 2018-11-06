(function () {
    if (typeof window.android === "undefined") {
        var debug = false;
        window.android = {
            setVerse: function (verse) {
                if (debug) {
                    console.log("android.setVerse: " + verse);
                }
            },
            setHighlight: function (verse) {
                if (debug) {
                    console.log("android.setHighlight: " + verse);
                }
            },
            setCopyText: function (selected) {
                if (debug) {
                    console.log("android.setCopyText: " + selected);
                }
            },
            showAnnotation: function (link, annotation) {
                if (debug) {
                    console.log("android.showAnnotation: " + link + ", " + annotation);
                }
            },
            showNote: function (note) {
                if (debug) {
                    console.log("android.showNote: " + note);

                }
            },
            setTop: function (top) {
                if (debug) {
                    console.log("android.setTop: " + top);
                }
            },
            onLoaded: function () {
                if (debug) {
                    console.log("android.onLoaded");
                }
            }
        };
    }

    var texts, prefix, join, android = window.android,
        selection = "selection", hypen = "-", classPrefix = "(?:^|\\s)", empty = "";

    if (!String.prototype.endsWith) {
        Object.defineProperty(String.prototype, "endsWith", {
            writable: false,
            enumerable: false,
            configurable: false,
            value: function (suffix) {
                return this.indexOf(suffix, this.length - suffix.length) !== -1;
            }
        });
    }

    function getPossibleVerses(node) {
        var verses = [];
        node = node || document;

        function addSelector(node, verses, selector, prefix) {
            var elements = node.querySelectorAll(selector);
            Array.prototype.forEach.call(elements, function (element) {
                if (!prefix || hasClass(element, prefix, false)) {
                    verses.push(element);
                }
            });
        }

        // test for biblegateway
        addSelector(node, verses, ".chapternum");
        addSelector(node, verses, "sup.versenum");
        if (verses.length == 0) {
            addSelector(node, verses, "sup", prefix);
        }
        return verses;
    }

    function getFirstVisibleVerse(top) {
        var i, length,
            verse, verses,
            oldPosition = NaN, position;
        verses = getPossibleVerses();
        length = verses.length;
        for (i = 0; i < length; ++i) {
            position = getTop(verses[i]);
            if (position >= top) {
                if (isNaN(oldPosition) || Math.abs(oldPosition - top) > 3 * (position - top)) {
                    verse = verses[i];
                } else {
                    verse = verses[i - 1];
                }
                break;
            } else {
                oldPosition = position;
            }
        }

        if (verse) {
            android.setVerse(parseInt(getVerseNums(verse)[0].trim()));
        }
    }

    function getVerseNums(verse) {
        if (hasClass(verse, "chapternum")) {
            return ["1"];
        } else {
            return verse.textContent.trim().split(hypen);
        }
    }

    function dumpVerses() {
        var i, length, verse, verses;
        verses = getPossibleVerses();
        length = verses.length;
        for (i = 0; i < length; ++i) {
            verse = verses[i];
            console.log("verse: " + verse.textContent + ", top: " + getTop(verse));
        }
        return verse;
    }

    function getVerses(selector) {
        var i, length,
            html = empty, text, div, elements, verses, verse = empty, lastVerse = NaN,
            title, titles, titleStart, titleEnd, e1, e2;
        div = document.createElement("div");
        elements = document.querySelectorAll(selector);
        length = elements.length;
        if (false && selector == ("." + selection)) {
            if (length > 0) {
                e1 = elements.item(0);
                e2 = elements.item(length - 1);
                window.getSelection().setBaseAndExtent(e1, 0, e2, e2.childNodes.length);
            } else {
                window.getSelection().empty();
            }
        }
        for (i = 0; i < length; ++i) {
            // we need a copy of original node
            html += " " + elements.item(i).innerHTML;
        }
        div.innerHTML = html;
        verses = getPossibleVerses(div);
        length = verses.length;
        for (i = 0; i < length; ++i) {
            titles = getVerseNums(verses[i]);
            titleStart = parseInt(titles[0].trim());
            if (isNaN(titleStart)) {
                continue;
            }
            verses[i].parentNode.removeChild(verses[i]);
            if (titles.length > 1) {
                titleEnd = parseInt(titles[1].trim())
            } else {
                titleEnd = NaN;
            }
            if (!titleEnd || isNaN(titleEnd)) {
                titleEnd = titleStart;
            }
            for (title = titleStart; title <= titleEnd; ++title) {
                if (isNaN(lastVerse)) {
                    verse += title;
                } else if (title == lastVerse) {
                    // do nothing
                } else if (title - lastVerse == 1) {
                    if (!verse.endsWith(hypen)) {
                        verse += hypen;
                    }
                } else {
                    if (verse.endsWith(hypen)) {
                        verse += lastVerse;
                    }
                    verse += ",";
                    verse += title;
                }
                lastVerse = title;
            }
        }

        if (verse.endsWith(hypen) && !isNaN(lastVerse)) {
            verse += lastVerse;
        }

        function removeSelector(selector) {
            var noteLinks = div.querySelectorAll(selector);
            Array.prototype.forEach.call(noteLinks, function (noteLink) {
                noteLink.parentNode.removeChild(noteLink);
            });
        }

        // get content
        // content = html.replace(new RegExp('<a .*?class="note-?link.*?</a>', "gi"), "");
        // div.innerHTML = content;
        removeSelector(".notelink");
        removeSelector(".footnote");
        removeSelector(".crossreference");
        text = div.textContent.replace(/^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g, empty).trim();
        return {verse: verse, text: text};
    }

    function isAllHighlight(selector) {
        var i, length, elements;
        elements = document.querySelectorAll(selector);
        length = elements.length;
        for (i = 0; i < length; ++i) {
            if (!hasClass(elements.item(i), "highlight")) {
                return false;
            }
        }
        return true;
    }

    function getCopyText() {
        var verses = getVerses("." + selection);
        if (verses.text) {
            android.setCopyText(empty + isAllHighlight("." + selection) + "\n" + verses.verse + "\n" + verses.text);
        } else {
            android.setCopyText(empty);
        }
    }

    function selectVerse(element) {
        var className = element.className, add;
        add = !hasClass(element, selection);
        className.match(/\S+/g).forEach(function (item) {
            var i, verse, verses;
            if (prefix && item.indexOf(prefix) != -1) {
                verses = document.getElementsByClassName(item);
                for (i = verses.length - 1; i > -1; --i) {
                    verse = verses.item(i);
                    if (verse.nodeName == "SUP") {
                        continue;
                    }
                    if (!add) {
                        removeClass(verse, selection);
                    } else {
                        addClass(verse, selection);
                    }
                }
            }
        });
        getCopyText();
    }

    function highlightSearch() {
        var i, replace, search,
            body, newBody = empty, lowerBody, lowerSearch;
        search = window.search;
        if (!search) {
            return false;
        }
        body = document.body.innerHTML;
        lowerBody = body.toLowerCase();
        lowerSearch = search.toLowerCase();

        while (body.length > 0) {
            replace = search;
            i = lowerBody.indexOf(lowerSearch);
            if (i < 0) {
                break;
            }
            if (body.lastIndexOf(">", i) >= body.lastIndexOf("<", i)) {
                replace = "<span class=\"" + selection + "\">" + search + "</span>";
            }
            newBody += body.substring(0, i) + replace;
            body = body.substr(i + search.length);
            lowerBody = lowerBody.substr(i + search.length);
        }

        newBody += body;
        document.body.innerHTML = newBody;
        return true;
    }

    function isWindow(obj) {
        return obj != null && obj === obj.window;
    }

    function getWindow(elem) {
        return isWindow(elem) ? elem : elem.nodeType === 9 && elem.defaultView;
    }

    function getTop(elem) {
        var docElem, win, rect, doc;
        rect = elem.getBoundingClientRect();
        if (rect.width || rect.height) {
            doc = elem.ownerDocument;
            win = getWindow(doc);
            docElem = doc.documentElement;
            return Math.round(rect.top + win.pageYOffset - docElem.clientTop);
        }
        return Math.round(rect.top);
    }

    function load() {
        var highlighted, selected, notes,
            demo, id,
            footnote = '<span class="icon-footnote"></span>',
            reference = '<span class="icon-crossreference"></span>';
        initPrefix();
        highlightSearch();
        highlighted = window.highlighted;
        selected = window.selected;
        notes = window.notes;
        if (highlighted) {
            highlightVerses(highlighted);
        }
        if (selected) {
            selectVerses(selected);
        }
        if (Object.prototype.toString.call(notes) == "[object Array]") {
            notes.forEach(function (note) {
                addNote(note);
            });
        }
        addListener();
        demo = document.querySelector("#pb-demo");
        if (demo) {
            demo.style.display = "block";
        }
        replace('a[id*="!f."]', footnote);
        replace('a[id*="!x."]', reference);
        replace("sup.footnote", footnote);
        replace("sup.crossreference", reference);
        onReaderReady(100);
    }

    function onReaderReady(t) {
        setTimeout(function () {
            try {
                android.onLoaded();
                jumpToVerse();
            } catch (e) {
                console.log("error calling android.onLoaded");
                console.log(e);
                onReaderReady(500);
            }
        }, t);
    }

    function jumpToVerse() {
        var top, element, elements, verse;
        verse = window.verse;
        if (!verse || parseInt(verse) <= 1) {
            return;
        }
        elements = getElements(verse);
        if (elements.length > 0) {
            element = elements.item(0);
            // FIXME: hard code for 5px
            top = getTop(element) - 5;
            android.setTop(top);
            if (false) {
                dumpVerses();
                console.log("element: " + element.textContent + ", top: " + top);
            }
        }
    }

    function replace(selector, content) {
        var elements = document.querySelectorAll(selector);
        Array.prototype.forEach.call(elements, function (element) {
            var value = element.getAttribute("value") || element.getAttribute("data-link");
            if (value) {
                if (content) {
                    element.innerHTML = value.replace(/^.*?(<a.*?>).*?(<\/a>).*$/g, "$1" + content + "$2");
                } else {
                    element.innerHTML = value;
                }
            } else {
                element.innerHTML = content;
            }
        });
    }

    function removeHighlight(className) {
        var search, i, result, results;
        search = window.search;
        if (!search) {
            return;
        }
        if (className == undefined) {
            className = "highlight";
        } else {
            className = className.trim();
        }
        results = document.getElementsByClassName(className);
        for (i = results.length - 1; i > -1; --i) {
            result = results.item(i);
            // the only child is text node, just replace it with the text
            if (result.className.trim() == className && result.childNodes.length == 1 && result.firstChild.nodeType == 3) {
                result.parentNode.replaceChild(result.firstChild, result);
            } else {
                removeClass(result, className);
            }
        }
        window.search = empty;
        document.normalize();
    }

    function addListener() {
        document.querySelector("#content").addEventListener("click", function (e) {
            var verseNum, hash, element = e.target;
            while (element && element.nodeName != "BODY") {
                if (element.nodeName == "IMG") {
                    verseNum = element.getAttribute("data-note");
                    if (verseNum) {
                        e.preventDefault();
                        android.showNote(verseNum);
                        return;
                    }
                } else if (element.nodeName == "A" && (hasClass(element, "notelink") || (element.parentNode && element.parentNode.nodeName == "SUP"))) {
                    hash = element.hash.replace("#", empty);
                    if (hash) {
                        e.preventDefault();
                        android.showAnnotation(hash, empty);
                        return;
                    }
                } else if (element.nodeName == "A" && hasClass(element, "bibleref")) {
                    e.preventDefault();
                    android.showAnnotation("cross", element.outerHTML);
                    return;
                } else if (element.nodeName == "SPAN" && (hasClass(element, "text") || hasClass(element, prefix, false))) {
                    removeHighlight(selection);
                    selectVerse(element);
                    break;
                }
                element = element.parentNode;
            }
        });
    }

    function getElements(verseNum) {
        var i, extras, match, reg,
            verse = prefix + join + verseNum,
            elements = document.getElementsByClassName(verse);
        if (elements.length) {
            return elements;
        }
        if (verse[0] == "v") {
            // bibles.org
            extras = ["a", "b", "c"];
            for (i = 0; i < 3; ++i) {
                elements = document.getElementsByClassName(verse + extras[i]);
                if (elements.length) {
                    break;
                }
            }
        } else {
            // biblegateway
            reg = new RegExp(classPrefix + verse + "-\\S+");
            if (!texts) {
                texts = document.querySelectorAll(".text");
            }
            for (i = 0; i < texts.length; ++i) {
                match = texts.item(i).className.match(reg);
                if (match) {
                    break;
                }
            }
            if (i != texts.length) {
                elements = document.getElementsByClassName(match[0]);
            }
        }
        if (elements.length || verseNum < 2) {
            return elements;
        } else {
            return getElements(verseNum - 1);
        }
    }

    function highlightVerse(verseNum, clear, className) {
        var i, element, elements;
        if (!verseNum) {
            return;
        }
        elements = getElements(verseNum);
        for (i = elements.length - 1; i > -1; --i) {
            element = elements.item(i);
            if (element.nodeName == "SUP") {
                continue;
            }
            if (clear === false) {
                removeClass(element, className);
            } else {
                addClass(element, className);
            }
        }
    }

    function addClass(node, className) {
        if (!node) {
        } else if (node.classList) {
            node.classList.add(className);
        } else if (!node.className) {
            node.className = className;
        } else if (!hasClass(node, className)) {
            node.className += " " + className;
        }
    }

    function hasClass(node, className, strict) {
        var reg, i;
        if (!node) {
            return false;
        } else if (node.classList) {
            if (strict === false) {
                for (i = 0; i < node.classList.length; ++i) {
                    if (node.classList.item(i).indexOf(className) === 0) {
                        return true;
                    }
                }
                return false;
            } else {
                return node.classList.contains(className);
            }
        } else if (!node.className) {
            return false;
        } else if (strict === false) {
            reg = new RegExp(classPrefix + className);
        } else {
            reg = new RegExp(classPrefix + className + "(?!\\S)");
        }
        return node.className.match(reg) != null;
    }

    function removeClass(node, className) {
        var reg;
        if (!node) {
            // do nothing
        } else if (node.classList) {
            node.classList.remove(className);
        } else if (node.className) {
            reg = new RegExp(classPrefix + className + "(?!\\S)");
            if (node.className.match(reg)) {
                node.className = node.className.replace(reg, empty).trim();
            }
        }
    }

    function initPrefix() {
        if (prefix != undefined && join != undefined) {
            return;
        }
        try {
            // biblegateway.com
            join = hypen;
            prefix = document.querySelector(".text").className.replace(/\s*text\s*/g, empty).split(join).splice(0, 2).join(join);
        } catch (e) {
        }
        try {
            if (!prefix || prefix.indexOf(join) == -1) {
                // bibles.org
                join = "_";
                prefix = document.querySelector("sup").className.split(join).splice(0, 2).join(join);
                if (!prefix || prefix.indexOf(join) == -1) {
                    prefix = empty;
                    join = empty;
                }
            }
        } catch (e) {
        }
    }

    function highlightVerses(verses, clear, className) {
        initPrefix();
        if (!prefix) {
            return;
        }
        if (className === undefined) {
            className = "highlight";
        }
        verses.split(",").forEach(function (fragment) {
            var end, intervals = fragment.split(hypen),
                verseNum = parseInt(intervals[0]);
            if (intervals.length == 2) {
                end = parseInt(intervals[1]);
                for (; verseNum <= end; ++verseNum) {
                    highlightVerse(verseNum, clear, className);
                }
            } else {
                highlightVerse(verseNum, clear, className);
            }
        });
    }

    function selectVerses(verses, clear) {
        highlightVerses(verses, clear, selection);
    }

    function setHighlightVerses(verses, added) {
        highlightVerses(verses, added);
        android.setHighlight(getVerses(".highlight").verse);
        highlightVerses(verses, false, selection);
    }

    function addNote(verseNum, clear) {
        var i, img, element, elements = getElements(verseNum);
        if (!elements.length) {
            return;
        }
        for (i = 0; i < elements.length; ++i) {
            element = elements.item(i);
            if (element.childNodes.length == 1) {
                element = element.querySelector("sup.versenum") || element.querySelector(".chapternum") || element.querySelector("sup");
                if (element) {
                    element = element.parentNode;
                }
            }
            if (element && element.firstChild && element.firstChild.nextSibling) {
                if (clear === false && element.firstChild.nextSibling.nodeName == "IMG") {
                    element.removeChild(element.firstChild.nextSibling);
                } else if (clear !== false && element.firstChild.nextSibling.nodeName != "IMG") {
                    img = document.createElement("img");
                    img.setAttribute("class", "note");
                    img.setAttribute("data-note", verseNum);
                    img.setAttribute("src", "note.png");
                    element.insertBefore(img, element.firstChild.nextSibling);
                }
                return;
            }
        }
    }

    window.addEventListener("load", load);
    window.getFirstVisibleVerse = getFirstVisibleVerse;
    window.setHighlightVerses = setHighlightVerses;
    window.addNote = addNote;
    window.selectVerses = selectVerses;

})();
