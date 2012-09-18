/*jslint white: true, onevar: true, undef: true, nomen: true, eqeqeq: true, plusplus: true, bitwise: true, regexp: true, newcap: true, immed: true, strict: true */
/*global window, document */
/*global ActiveXObject, XMLHttpRequest*/
/*global setInterval, clearInterval*/
/*jslint plusplus: false */
"use strict";
var jwig = {

    /////// Debug info ///////////////////////////////////////////////////////////

    debug: false, // enables debug info

    debugelement: undefined,

    // writes message to log (firebug console or 'pre' element)
    log: function (msg) {
        if (jwig.debug) {
            var d, m;
            d = new Date();
            m = "[" + d.toLocaleTimeString() + "," + d.getMilliseconds() + "] " + msg;
            if (window.console && window.console.firebug) {
                window.console.info(m);
            } else {
                if (!jwig.debugelement) {
                    jwig.debugelement = document.createElement("pre");
                    document.body.appendChild(jwig.debugelement);
                }
                jwig.debugelement.appendChild(document.createTextNode("\r\n" + m));
            }
        }
    },

    /////// Alert function for user feedback ///////////////////////////////////////////

    alerted: false,

    // show alert window but only once
    alert: function (message) {
        jwig.log("alert: " + message);
        if (!jwig.alerted) {
            jwig.alerted = true;
            if (jwig.debug) {
                window.alert(message);
            }
        }
    },

    /////// Hidden form fields for submit buttons ///////////////////////////////////////////

    // for each form, creates a hidden field named 'submit' with the name of the clicked submit input button
    fixSubmitButtons: function () {
        var i, forms;
        forms = document.getElementsByTagName("form");
        for (i = 0; i < forms.length; i++) {
            jwig.fixSubmitButton(forms, i);
        }
    },

    fixSubmitButton: function (forms, i) {
        var s, form, submits, j, type, targ, elements, hasSubmitField;
        s = document.createElement("input");
        s.setAttribute("type", "hidden");
        s.setAttribute("name", "jwig_submit");
        s.setAttribute("value", "");
        form = forms[i];
        elements = form.elements;
        hasSubmitField = (elements.length > 0 && form.elements[elements.length - 1].name === "jwig_submit");
        if (form.action === "NONE" || hasSubmitField) {
            // skip pseudo-forms and forms with submit-tag already (it is expected to be placed last)
            return;
        }
        form.appendChild(s);
        submits = form.getElementsByTagName("input");
        for (j = 0; j < submits.length; j++) {
            type = submits[j].getAttribute("type");
            if (type === "submit" || type === "image") {
                submits[j].onclick = function (e) {
                    if (!e) {
                        e = window.event;
                    }
                    if (e.target) {
                        targ = e.target;
                    }
                    else if (e.srcElement) {
                        targ = e.srcElement;
                    }
                    s.setAttribute("value", targ.getAttribute("name"));
                };
            }
        }
    },

    submitHandlerData: undefined, //This value will be set by the JWIG augment function

    installValidateForm: function() {
        var i, forms;
        forms = document.getElementsByTagName("form");
        for (i = 0; i < forms.length; i++) {
            var form = forms[i];
            jwig.addSubmitHandler(form);
        }
    },

    addSubmitHandler: function(form) {
        var messageDiv = document.getElementById("form_feedback");
        if (!messageDiv) {
            messageDiv = document.createElement("div");
            messageDiv.style.color = "red";
            form.appendChild(messageDiv);
        }
        form.onsubmit = function () {
            var action = form.getAttribute("action");
            var fields = jwig.submitHandlerData[action];
            if (!fields || fields.length == 0)
                return true;
            var fieldString = jwig.getFields(fields);
            var http = jwig.getHttp();
            http.open("POST", action + "-validate", true);
            http.onreadystatechange = function() {
                if (http.readyState == 4) {
                    if (http.status == 404) {
                        //No validation errors were found
                        messageDiv.innerHTML = "";
                        jwig.submitFormNoValidate(form);
                    } else if (http.status == 200) {
                        //Validation error. Get the text and show it
                        messageDiv.style.display = "block";
                        messageDiv.innerHTML = '';
                        if (document.importNode && http.responseXML != null) {
                            // Due to IE9 weirdness, we have to parse
                            // the XML from responseText instead of
                            // using responseXML. See:
                            //   http://blogs.msdn.com/b/ie/archive/2012/07/19/
                            //   xmlhttprequest-responsexml-in-ie10-release-preview.aspx
                            var parser = new DOMParser();
                            var doc = parser.parseFromString(http.responseText, "application/xml");
                            var content = document.importNode(doc.documentElement, true);
                            messageDiv.appendChild(content);
                        } else {
                            messageDiv.innerHTML = http.responseText;
                        }
                    } else {
                        jwig.alert(http.message);
                    }
                }
            };
            http.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
            http.send(fieldString);
            return false;
        }
    },

    submitFormNoValidate : function(form) {
        //remove the validator and submit the form
        form.onsubmit = null;
        form.submit();
    },



    //////// Ajax ///////////////////////////////////////////////////////////

    // returns fresh xmlhttp object, if possible
    getHttp: function () {
        if (window.ActiveXObject) {
            try {
                return new ActiveXObject("Msxml2.XMLHTTP");
            } catch (e) {
                return new ActiveXObject("Microsoft.XMLHTTP");
            }
        } else if (window.XMLHttpRequest) {
            return new XMLHttpRequest();
        }
        jwig.alert("Unable to send message to the server.\nYour browser does not support Ajax.");
        return undefined;
    },

    // sends a message to the server
    send: function (url, msg, opt_http, opt_method, opt_etag, opt_responsehandler) {
        var success, http;
        success = false;
        http = opt_http;
        if (!http) {
            http = jwig.getHttp();
        }
        if (http) {
            try {
                http.open(opt_method || "POST", url, true);
            } catch (e) {
                jwig.alert("Unable to send message to the server.\n\n(Error: " + e.message + ")");
                return false;
            }
            http.onreadystatechange = function () {
                if (http.readyState == 4) {
                    http.onreadystatechange = new Function; // avoids leak in old IE
                    var ok = false;
                    try {
                        if (http.status == 200) {
                            ok = true;
                        }
                    } catch (e) {
                        // !ok
                    }
                    if (opt_responsehandler) {
                        opt_responsehandler(ok, http);
                    }
                    else if (!ok) {
                        jwig.alert('Unable to send message to the server. (Error code ' + http.status + ')');
                    }
                }
            };
            if (!opt_method || opt_method == "POST") {
                http.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
            }
            if (opt_etag) {
                http.setRequestHeader("If-None-Match", opt_etag);
            }
            if (!msg) {
                msg = "";
            }
            http.send(msg);
            success = true;
        }
        return success;
    },

    //////// Shared state ///////////////////////////////////////////////////////////

    storageexists: false, // if true, the browser supports shared state
    storage: undefined,

    // initializes values for shared state
    initShared: function () {
        if (window.localStorage) {
            jwig.storage = window.localStorage;
        }
        if (!jwig.storage && window.globalStorage) {
            jwig.storage = window.globalStorage[document.domain];
        }
        if (jwig.storage) {
            jwig.storageexists = true;
        }
        jwig.log("Storage exists: " + jwig.storageexists);
    },

    // stores shared state as (key,value) string pair
    storeShared: function (key, value) {
        if (jwig.storageexists) {
            jwig.storage.setItem(key, value);
            var t = jwig.storage.getItem(key);
            if (t != value) {// XXX
                jwig.alert("DOM storage write/read inconsistency: written=" + value + ", read=" + t + ", type=" + typeof(value));
            }
        }
    },

    // checks whether the given shared state key has nonempty value
    existsShared: function (key) {
        if (jwig.storageexists) {
            return jwig.loadShared(key) != "";
        }
        else {
            return false;
        }
    },

    // deletes shared state
    deleteShared: function (key) {
        if (jwig.storageexists) {
            jwig.storage.removeItem(key);
        }
    },

    // loads shared state
    loadShared: function (key) {
        if (jwig.storageexists) {
            var v = jwig.storage.getItem(key);
            if (v) {
                v = String(v);
            }
            else {
                v = "";
            }
            return v;
        }
        else {
            return undefined;
        }

    },

    //////// Event handler ///////////////////////////////////////////////////////////

    // sends field values to an EventHandler on the server
    sendFields: function (url, fieldnames) {
        jwig.log("Sending field values");
        jwig.send(url, jwig.getFields(fieldnames));
    },
    // constructs query string from fields
    getFields: function (names) {
        var res, first, i, form, n, name, field, value;
        res = "";
        first = true;
        // TODO use the version in dynamicContent..
        for (i in document.forms) {
            form = document.forms[i];
            if (form){
                for (n in names) {
                    name = names[n];
                    field = form[name];
                    if (field) {
                        if(field.type === 'radio' && !field.checked){
                            // unchecked radio buttons should not be sent
                            continue;
                        }
                        if (field.type == 'checkbox') {
                            value = field.checked;
                        }
                        else {
                            value = field.value;
                        }
                        if (first) {
                            first = false;
                        }
                        else {
                            res += '&';
                        }
                        res += name + "=" + encodeURIComponent(value);
                    }
                }
            }
        }
        return res;
    },

    /////// Refresh sessions ///////////////////////////////////////////////////////////

    show_refresh_alerts: true, // if true, alert user if session refresh fails

    failmsg: "",
    refreshurl: undefined,
    refreshtimer: undefined,

    // starts periodic session refresher
    startRefreshSessions: function (url, minutes) {
        var hours, days, t;
        hours = Math.floor(minutes / 60);
        days = Math.floor(hours / 24);
        hours = hours % 24;
        minutes = minutes % 60;
        if (days > 0) {
            t = days + " day" + (days > 1 ? "s" : "");
        }
        if (hours > 0) {
            if (t) {
                if (minutes > 0) {
                    t = t + ", ";
                }
                else {
                    t = t + " and ";
                }
            } else {
                t = "";
            }
            t = t + hours + " hour" + (hours > 1 ? "s" : "");
        }
        if (minutes > 0) {
            if (t) {
                t = t + " and ";
            }
            else {
                t = "";
            }
            t = t + minutes + " minute" + (minutes > 1 ? "s" : "");
        }
        jwig.failmsg =
            "\n\nI'm unable to refresh the session timeout regularly\n" +
            "while you view this page.\n\n" +
            "This means that your form data may be lost if it takes\n" +
            "more than " + t + " before you submit the data.";
        jwig.refreshurl = url.replace("&amp;", "&");
        jwig.refreshtimer = setInterval("jwig.refreshSessions()", 60000); // calling setInterval with a function doesn't work in all browsers :-(
    },

    // sends session refresh request
    refreshSessions : function () {
        jwig.log("Refreshing session state");
        jwig.send(jwig.refreshurl, null, null, "GET", null, function (ok) {
                      if (!ok && jwig.show_refresh_alerts) {
                          clearInterval(jwig.refreshtimer);
                          jwig.alert("Lost contact with the server." + jwig.failmsg);
                      }
                  });
    },

    /////// Polling ///////////////////////////////////////////////////////////

    pollurl: undefined,              // base URL for Synchronizer
    pollid: undefined,               // ID number of this window tab
    polltimer: undefined,            // timer for calling longPoll/shortPoll
    pollslavetimer: undefined,       // timer for calling slavePoll
    pollhttp: undefined,             // HTTP connection, used by longPoll master
    pollobservers: [],               // array of (observer,etag,node) triples
    pollshort: false,                // if set, fall back to short polling
    shortpollinterval: 30000,         //
    pollgetnotifications: undefined, // last time of executing getNotifications
    pollhttptime: undefined,          // last time of starting a long poll

    // starts polling if any observers registered
    startPolling: function (u) {
        var timestamp, nextid;
        if (jwig.pollobservers.length > 0) {
            jwig.pollurl = u;
            if (jwig.storageexists) {
                if (jwig.existsShared("jwig_timestamp")) {
                    timestamp = parseInt(jwig.loadShared("jwig_timestamp"), 10);
                    if (timestamp + 60000 < new Date().getTime()) {
                        jwig.log("Shared state stale, resetting");
                        jwig.deleteShared("jwig_nextid");
                        jwig.deleteShared("jwig_polling");
                        jwig.deleteShared("jwig_notifications");
                        jwig.deleteShared("jwig_observers");
                    }
                }
                if (!jwig.existsShared("jwig_nextid")) {
                    jwig.log("Initializing jwig_nextid");
                    jwig.storeShared("jwig_nextid", 0);
                }
                nextid = parseInt(jwig.loadShared("jwig_nextid"), 10);
                jwig.pollid = nextid++;
                jwig.storeShared("jwig_nextid", nextid);
                jwig.log("Assigned ID " + jwig.pollid);
            }
            var polltime = 3000;
            if (jwig.pollshort)
                polltime = jwig.shortpollinterval;
            jwig.polltimer = setInterval("jwig.poll()", polltime);
            window.onunload = jwig.stopPolling;
            window.onabort = jwig.stopPolling;
            jwig.poll();
        }
    },

    // stops polling
    stopPolling: function () {
        if (jwig.polltimer) {
            clearInterval(jwig.polltimer);
            jwig.polltimer = undefined;
            jwig.pollhttptime = undefined;
        }
        if (jwig.pollhttp) {
            jwig.pollhttp.onreadystatechange = new Function; // avoids leak in old IE
            jwig.pollhttp.abort();
            jwig.deleteShared("jwig_polling");
            jwig.pollhttp = undefined;
        }
        jwig.cleanObservers();
    },

    // switches to short polling
    switchToShortPolling: function () {
        clearInterval(jwig.polltimer);
        jwig.polltimer = setInterval("jwig.poll()", jwig.shortpollinterval);
        jwig.pollshort = true;
    },

    // adds a notification to slaves
    addNotification: function (cmd) {
        var ns, t;
        jwig.getNotifications();
        ns = jwig.loadShared("jwig_notifications");
        t = new Date().getTime() + "$" + cmd;
        ns += t + ";";
        jwig.storeShared("jwig_notifications", ns);
    },

    // gets array of new notifications from master
    getNotifications: function () {
        var cmds, ns, ar, set, i, n, t, cmd;
        cmds = [];
        ns = jwig.loadShared("jwig_notifications");
        ar = ns.split(";");
        set = {};
        for (i in ar) {
            n = ar[i];
            if (n.length > 0) {
                i = n.indexOf('$');
                t = parseInt(n.substring(0, i), 10);
                cmd = n.substring(i + 1);
                if ((!jwig.pollgetnotifications || jwig.pollgetnotifications < t) && !set[cmd]) {
                    //jwig.log("getNotifications pushing " + cmd);
                    cmds.push(cmd);
                    set[cmd] = true;
                }
            }
        }
        jwig.pollgetnotifications = new Date().getTime();
        return cmds;
    },

    // removes stale entries in jwig_notifications
    cleanNotifications: function () {
        var ns, ns2, now, ar, i, n, t;
        ns = jwig.loadShared("jwig_notifications");
        ns2 = "";
        now = new Date().getTime();
        ar = ns.split(";");
        for (i in ar) {
            n = ar[i];
            if (n.length > 0) {
                i = n.indexOf('$');
                t = parseInt(n.substring(0, i), 10);
                if (t + 30000 > now) {
                    ns2 += n + ";";
                }
            }
        }
        jwig.storeShared("jwig_notifications", ns2);
    },

    // adds an observer to jwig_observers
    addObserver: function (observer, etag) {
        var os, t;
        os = jwig.loadShared("jwig_observers");
        t = jwig.pollid + "$" + new Date().getTime() + "$" + observer + "$" + etag;
        os += t + ";";
        jwig.storeShared("jwig_observers", os);
    },

    // removes stale entries in jwig_observers
    cleanObservers: function () {
        var os, os2, now, ar, i, n, i1, i2, i3, pollid, t, obs, etag;
        os = jwig.loadShared("jwig_observers");
        os2 = "";
        now = new Date().getTime();
        ar = os.split(";");
        for (i in ar) {
            n = ar[i];
            if (n.length > 0) {
                i1 = n.indexOf('$');
                i2 = n.indexOf('$', i1 + 1);
                i3 = n.indexOf('$', i2 + 1);
                pollid = parseInt(n.substring(0, i1), 10);
                t = parseInt(n.substring(i1 + 1, i2), 10);
                obs = n.substring(i2 + 1, i3);
                etag = n.substring(i3 + 1);
                if (t + 30000 > now && pollid != jwig.pollid) {
                    //jwig.log("cleanObservers retaining " + n);
                    os2 += n + ";";
                }
            }
        }
        jwig.storeShared("jwig_observers", os2);
    },

    // returns array of slave observers from jwig_observers
    getObservers: function () {
        var rs, os, now, ar, i, n, i1, i2, i3, pollid, t, obs, etag, k;
        rs = [];
        os = jwig.loadShared("jwig_observers");
        now = new Date().getTime();
        ar = os.split(";");
        for ( i in ar) {
            n = ar[i];
            if (n.length > 0) {
                i1 = n.indexOf("$");
                i2 = n.indexOf("$", i1 + 1);
                i3 = n.indexOf("$", i2 + 1);
                pollid = parseInt(n.substring(0, i1), 10);
                t = parseInt(n.substring(i1 + 1, i2), 10);
                obs = n.substring(i2 + 1, i3);
                etag = n.substring(i3 + 1);
                k = obs + "$" + etag;
                if (t + 30000 > now && pollid != jwig.pollid) {
                    //jwig.log("getObservers adding " + obs + ", etag=" + etag);
                    rs.push({observer: obs, etag: etag});
                }
            }
        }
        return rs;
    },

    // returns description of current observers from jwig_observers
    getMyObservers: function () {
        var rs, os, ar, i, n, i1, i2, i3, pollid, obs, etag;
        rs = [];
        os = jwig.loadShared("jwig_observers");
        ar = os.split(";");
        for (i in ar) {
            n = ar[i];
            if (n.length > 0) {
                i1 = n.indexOf("$");
                i2 = n.indexOf("$", i1 + 1);
                i3 = n.indexOf("$", i2 + 1);
                pollid = parseInt(n.substring(0, i1), 10);
                obs = n.substring(i2 + 1, i3);
                etag = n.substring(i3 + 1);
                if (pollid == jwig.pollid) {
                    rs.push(obs + "$" + etag);
                }
            }
        }
        return rs.toString();
    },

    // issues a long or short poll
    poll: function () {
        jwig.storeShared("jwig_timestamp", new Date().getTime());
        if (jwig.pollshort) {
            jwig.shortPoll();
        }
        else {
            jwig.longPoll();
        }
    },

    // issues a long poll
    longPoll: function () {
        var ret, observerstime, polling, hs, set, i, k, ar, cmd;
        if (jwig.pollhttp) {
            jwig.log("I'm already polling");
            jwig.storeShared("jwig_polling", new Date().getTime());
            ret = true;
            if (jwig.existsShared("jwig_observerstime")) {
                observerstime = parseInt(jwig.loadShared("jwig_observerstime"), 10);
                if (jwig.pollhttptime < observerstime) {
                    jwig.log("New observers arrived, aborting current polling"); // TODO: instead of aborting and restarting, just send a message (?)
                    jwig.pollhttp.abort();
                    ret = false;
                }
            }
            if (ret) {
                return;
            }
        }
        if (!jwig.storageexists) {
            jwig.log("Unable to use shared state, switching to short polling");
            jwig.switchToShortPolling();
            jwig.shortPoll();
            return;
        }
        if (jwig.existsShared("jwig_polling")) {
            polling = parseInt(jwig.loadShared("jwig_polling"), 10);
            if (polling + 10000 < new Date().getTime()) {
                jwig.log("Shared polling is stale: " + polling);
            }
            else {
                jwig.log("Someone else is polling");
                if (!jwig.pollslavetimer) {
                    jwig.pollslavetimer = setInterval("jwig.slavePoll()", 2000);
                }
                return;
            }
        }
        jwig.log("I'll start polling");
        if (jwig.pollslavetimer) {
            clearInterval(jwig.pollslavetimer);
            jwig.pollslavetimer = undefined;
        }
        jwig.cleanObservers();
        jwig.cleanNotifications();
        jwig.pollhttp = jwig.getHttp();
        if (!jwig.pollhttp) {
            return;
        }
        jwig.pollhttptime = new Date().getTime();
        hs = [];
        set = {};
        for (i in jwig.pollobservers) {
            k = jwig.pollobservers[i].observer + "$" + jwig.pollobservers[i].etag;
            if (!set[k]) {
                hs.push("h=" + encodeURIComponent(jwig.pollobservers[i].observer) + "&e=" + encodeURIComponent(jwig.pollobservers[i].etag));
                set[k] = true;
            }
        }
        ar = jwig.getObservers();
        for (i in ar) {
            k = ar[i].observer + "$" + ar[i].etag;
            if (!set[k]) {
                hs.push("h=" + encodeURIComponent(ar[i].observer) + "&e=" + encodeURIComponent(ar[i].etag));
                set[k] = true;
            }
        }
        jwig.storeShared("jwig_polling", new Date().getTime());
        jwig.log("Starting long poll: " + hs.join("&"));
        if (jwig.send(jwig.pollurl + "==", hs.join("&"), jwig.pollhttp, "POST", null, function (ok, http) {
                          cmd = http.responseText;
                          if (ok && cmd.length > 0) {
                              jwig.log("Processing command: " + cmd);
                              ar = cmd.split(";");
                              for (var i in ar) {
                                  var c = ar[i];
                                  if (c.length > 0) {
                                      jwig.execute(c);
                                      jwig.addNotification(c);
                                  }
                              }
                          } else {
                              try {
                                  if (http.status == 503) {
                                      jwig.log("Service unavailable, stop polling");
                                      jwig.stopPolling();
                                  }
                                  else if (http.status >= 500) {
                                      jwig.log("Server error (probably too busy?), switching to short polling");
                                      jwig.switchToShortPolling();
                                  }
                              } catch (e) {
                                  jwig.log("HTTP error: " + e.message);
                              }
                          }
                          jwig.deleteShared("jwig_polling");
                          jwig.pollhttp = undefined;
                      }))
            jwig.log('Long poll started');
        else {
            jwig.log('Long poll failed');
            jwig.deleteShared("jwig_polling");
            jwig.pollhttp = undefined;
        }
    },

    // issues a short poll round
    shortPoll: function () {
        for (var i in jwig.pollobservers)
            jwig.runObserver(jwig.pollobservers[i]);
    },

    // updates observers and checks for recent notifications from master
    slavePoll: function () {
        var old = jwig.getMyObservers();
        jwig.cleanObservers();
        for (var i in jwig.pollobservers) {
            var obs = jwig.pollobservers[i];
            jwig.addObserver(obs.observer, obs.etag);
        }
        if (old != jwig.getMyObservers()) {
            jwig.log("Observers changed, setting timestamp");
            jwig.storeShared("jwig_observerstime", new Date().getTime());
        }
        jwig.log("Checking notifications");
        var cmds = jwig.getNotifications();
        for (var i in cmds)
            jwig.execute(cmds[i]);
    },

    // invokes fragment reload for all observers registered for the given name
    execute: function (observer) {
        for (var i in jwig.pollobservers) {
            var obs = jwig.pollobservers[i];
            if (obs.observer == observer)
                jwig.runObserver(obs);
        }
    },

    // invokes fragment reload for a single registered observer
    runObserver: function (obs) {
        var p = obs.node;
        while (p && p.nodeType == 1)
            p = p.parentNode;
        if (p != document) {
            jwig.log("Node no longer in document, removing observer");
            for (var i in jwig.pollobservers)
                if (jwig.pollobservers[i] == obs)
                    jwig.pollobservers.splice(i, 1);
            return;
        }
        jwig.log("Sending GET " + obs.observer);
        jwig.send(obs.observer, null, null, "GET", obs.etag, function (ok, http) {
                      if (ok) {
                          obs.etag = http.getResponseHeader("ETag");
                          var t = http.responseText;
                          t = t.replace(/\<\?xml[^\?]*\?\>/, "");
                          var x = document.createElement("div");
                          x.innerHTML = t; // TODO: character encoding OK?
                          var cn = x.childNodes;
                          for (var i = 0; i<cn.length; i++) {
               		      if (cn[i].nodeType == 1) {
                                  x = cn[i];
                                  break;
                	      }
                          }
                          // TODO use XMLChangerHandler.runScripts(node), also added two other markers: {start|end}XMLMarker(){}
                          // XXX: run scripts in x
                          var thestart = obs.node;
                          var theend = thestart;
                          var d = 0;
                          do {
                	      if (!theend) {
                                  jwig.log("endXML not found");
                                  return;
                	      }
                              if (theend.nodeType == 1 && (theend.nodeName == "script" || theend.nodeName == "SCRIPT")) {
                                  if (theend.text.indexOf("jwig.startXML") == 0) {
                                      d++;
                                  }
                                  else if (theend.text.indexOf("jwig.endXML") == 0) {
                                      d--;
                                  }
                              }
                              if (d > 0)
                                  theend = theend.nextSibling;
                          } while (d > 0);
                          while (thestart.nextSibling != theend)
                          thestart.parentNode.removeChild(thestart.nextSibling);
                          while (x.hasChildNodes())
                              thestart.parentNode.insertBefore(x.removeChild(x.firstChild), theend);
                      } else {
                          if (http.status == 304)
                              jwig.log("Response: resource not modified");
                          else
                              jwig.alert("Send to server failed (status code " + http.status + ")");
                      }
                  });
    },

    // registers the node for the XMLProducer observer, also works as a marker in the XML page
    startXML: function (observer, etag, id) {
        //jwig.log("jwig.startXML: " + observer + "," + etag + "," + id);
        var e = document.getElementById(id);
        if (e)
    	    jwig.pollobservers.push({observer: observer, etag: etag, node: e});
        else
            jwig.log("element ID not found: " + id);
    },
    // XXX: test nested XMLProducers (make sure their startXML's get invoked)

    // does nothing, just a marker in the XML page
    endXML: function () {
        // do nothing
    },
    /////// Starts stuff after loading ///////////////////////////////////////////////////////////

    run: function (u) {
        jwig.fixSubmitButtons();
        jwig.installValidateForm();
        jwig.initShared();
        jwig.startPolling(u);
    }
};
