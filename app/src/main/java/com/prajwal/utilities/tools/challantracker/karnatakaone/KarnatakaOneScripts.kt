package com.prajwal.utilities.tools.challantracker.karnatakaone

object KarnatakaOneScripts {

    const val KARNATAKA_ONE_QUICK_PAY_URL = "https://www.karnatakaone.gov.in/Home/GuestLoginWithOutMob"
    const val KARNATAKA_ONE_HOME_URL = "https://www.karnatakaone.gov.in/"

    val INJECTION_SCRIPT = """
        (function() {
            if (window._k1Injected) return;
            window._k1Injected = true;
            console.log("[K1] KarnatakaOne Assistant injected");

            function triggerChangeEvent(el) {
                try {
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    el.dispatchEvent(new Event('blur', { bubbles: true }));
                } catch(e) {}
            }

            window.k1FillVehicle = function(vehicleNo) {
                if (!vehicleNo) return false;
                var clean = vehicleNo.replace(/[^a-zA-Z0-9]/g, '').toUpperCase();
                var inputs = Array.from(document.querySelectorAll("input[type='text'], input:not([type]), input[type='search']"));
                var found = inputs.find(function(i) {
                    var s = ((i.id || "") + " " + (i.name || "") + " " + (i.placeholder || "")).toLowerCase();
                    return s.includes("reg") || s.includes("veh") || s.includes("plate") || s.includes("v_no");
                });
                if (!found && inputs.length > 0) {
                    found = inputs.find(function(i) {
                        var s = ((i.id || "") + " " + (i.name || "") + " " + (i.placeholder || "")).toLowerCase();
                        return !s.includes("mob") && !s.includes("phone") && !s.includes("otp") && !s.includes("city");
                    });
                }
                if (found) {
                    found.focus();
                    found.value = clean;
                    triggerChangeEvent(found);
                    if (window.KarnatakaOneBridge) {
                        window.KarnatakaOneBridge.onStatusUpdate("Filled vehicle " + clean);
                    }
                    return true;
                }
                return false;
            };

            window.k1FillMobile = function(mobile) {
                if (!mobile) return false;
                var clean = mobile.replace(/[^0-9]/g, '');
                var inputs = Array.from(document.querySelectorAll("input[type='text'], input[type='tel'], input[type='number']"));
                var found = inputs.find(function(i) {
                    var s = ((i.id || "") + " " + (i.name || "") + " " + (i.placeholder || "")).toLowerCase();
                    return s.includes("mob") || s.includes("phone") || s.includes("contact");
                });
                if (found) {
                    found.focus();
                    found.value = clean;
                    triggerChangeEvent(found);
                    if (window.KarnatakaOneBridge) {
                        window.KarnatakaOneBridge.onStatusUpdate("Filled mobile number");
                    }
                    return true;
                }
                return false;
            };

            window.k1ScrapeViolations = function(targetVehicleNo) {
                var tables = Array.from(document.querySelectorAll("table"));
                var violations = [];

                var candidateTable = null;
                for (var t of tables) {
                    var text = (t.innerText || "").toLowerCase();
                    if ((text.includes("violation") || text.includes("offence") || text.includes("notice") || text.includes("fine")) &&
                        (text.includes("amount") || text.includes("date") || text.includes("pay") || text.includes("action"))) {
                        candidateTable = t;
                        break;
                    }
                }

                if (!candidateTable && tables.length > 0) {
                    candidateTable = tables.slice().sort(function(a, b) { return b.rows.length - a.rows.length; })[0];
                }

                if (!candidateTable || candidateTable.rows.length < 2) {
                    if (window.KarnatakaOneBridge) {
                        window.KarnatakaOneBridge.onStatusUpdate("No violation table detected on current page");
                    }
                    return JSON.stringify([]);
                }

                var rows = Array.from(candidateTable.rows);
                var headerRow = rows[0];
                var headers = Array.from(headerRow.cells).map(function(c) {
                    return (c.innerText || "").trim().toLowerCase();
                });

                for (var i = 1; i < rows.length; i++) {
                    var cells = Array.from(rows[i].cells);
                    if (cells.length < 2) continue;

                    var cellTexts = cells.map(function(c) { return (c.innerText || "").trim(); });
                    if (cellTexts.some(function(txt) { return txt.toLowerCase().includes("total"); })) continue;

                    var noticeNo = "";
                    var offense = "";
                    var fineAmount = 0;
                    var date = "";
                    var location = "Bengaluru";
                    var imageUrl = null;

                    headers.forEach(function(h, idx) {
                        var val = cellTexts[idx] || "";
                        if (h.includes("notice") || h.includes("challan") || h.includes("ticket") || h.includes("ref") || h.includes("sl")) {
                            if (!noticeNo && val) noticeNo = val;
                        } else if (h.includes("offence") || h.includes("violation") || h.includes("section") || h.includes("nature") || h.includes("crime") || h.includes("rule")) {
                            if (!offense && val) offense = val;
                        } else if (h.includes("fine") || h.includes("amount") || h.includes("fee") || h.includes("penalty")) {
                            var num = parseFloat(val.replace(/[^0-9.]/g, ''));
                            if (!isNaN(num) && num > 0) fineAmount = num;
                        } else if (h.includes("date") || h.includes("time")) {
                            if (!date && val) date = val;
                        } else if (h.includes("place") || h.includes("location") || h.includes("station") || h.includes("area") || h.includes("point")) {
                            if (!location || location === "Bengaluru") location = val;
                        }
                    });

                    // Search cells for images and hyperlinks
                    for (var c of cells) {
                        var img = c.querySelector("img");
                        if (img && img.src && !img.src.includes("icon") && !img.src.includes("logo") && !img.src.includes("arrow") && !img.src.includes("cube.gif")) {
                            imageUrl = img.src;
                        }
                        var links = Array.from(c.querySelectorAll("a"));
                        for (var a of links) {
                            var href = a.href || "";
                            var onclick = a.getAttribute("onclick") || "";
                            var linkText = (a.innerText || "").toLowerCase();

                            if (href.match(/\.(jpeg|jpg|png|webp)/i) || href.toLowerCase().includes("image") || href.toLowerCase().includes("photo")) {
                                imageUrl = href;
                                break;
                            }
                            if (linkText.includes("view") || linkText.includes("image") || linkText.includes("photo") || linkText.includes("pic") || linkText.includes("evidence")) {
                                var urlMatch = (onclick + " " + href).match(/https?:\/\/[^\s'"\)]+/i);
                                if (urlMatch) {
                                    imageUrl = urlMatch[0];
                                } else {
                                    var relMatch = (onclick + " " + href).match(/['"](\/[^'"]+\.(?:jpg|png|jpeg|webp|aspx|php)[^'"]*)['"]/i);
                                    if (relMatch) {
                                        imageUrl = window.location.origin + relMatch[1];
                                    } else if (href && !href.startsWith("javascript:")) {
                                        imageUrl = href;
                                    }
                                }
                            }
                        }
                    }

                    if (!noticeNo && cellTexts[0]) noticeNo = cellTexts[0];
                    if (!offense && cellTexts.length > 1) offense = cellTexts[1];
                    if (fineAmount === 0) {
                        for (var txt of cellTexts) {
                            var n = parseFloat(txt.replace(/[^0-9.]/g, ''));
                            if (!isNaN(n) && (n === 100 || n === 500 || n === 1000 || n === 1500 || n === 2000 || n >= 50)) {
                                fineAmount = n;
                                break;
                            }
                        }
                    }
                    if (!date) {
                        var dateCell = cellTexts.find(function(t) { return t.match(/\d{2}[\/-]\d{2}[\/-]\d{2,4}/); });
                        if (dateCell) date = dateCell;
                    }

                    if (noticeNo) {
                        violations.push({
                            "challan_number": noticeNo,
                            "offense": offense || "Traffic Violation",
                            "fine_amount": fineAmount,
                            "violation_date": date || "Recent",
                            "location": location || "Bengaluru",
                            "image_url": imageUrl,
                            "status": "PENDING"
                        });
                    }
                }

                var json = JSON.stringify(violations);
                if (window.KarnatakaOneBridge) {
                    window.KarnatakaOneBridge.onViolationsScraped(targetVehicleNo || "", json);
                    window.KarnatakaOneBridge.onStatusUpdate("Saved " + violations.length + " violations (" + violations.filter(function(v){return !!v.image_url;}).length + " photos)");
                }
                return json;
            };

            // Observer for dynamically added tables
            try {
                var observer = new MutationObserver(function() {
                    var tables = document.querySelectorAll("table");
                    if (tables.length > 0 && window.KarnatakaOneBridge) {
                        window.KarnatakaOneBridge.onTableDetected(tables.length);
                    }
                });
                observer.observe(document.body, { childList: true, subtree: true });
            } catch(e) {}

            // Intercept image click events
            document.addEventListener("click", function(e) {
                var target = e.target.closest("a");
                if (target) {
                    var href = target.href || "";
                    var text = (target.innerText || "").toLowerCase();
                    if (href.match(/\.(jpeg|jpg|png|webp)/i) || text.includes("image") || text.includes("photo")) {
                        if (window.KarnatakaOneBridge && href) {
                            window.KarnatakaOneBridge.onImageCaptured("", href);
                        }
                    }
                }
            }, true);
        })();
    """.trimIndent()

    fun getFillVehicleScript(vehicleNo: String): String {
        val clean = vehicleNo.replace("'", "\\'")
        return "javascript:if(window.k1FillVehicle) { window.k1FillVehicle('$clean'); }"
    }

    fun getFillMobileScript(phone: String): String {
        val clean = phone.replace("'", "\\'")
        return "javascript:if(window.k1FillMobile) { window.k1FillMobile('$clean'); }"
    }

    fun getScrapeViolationsScript(vehicleNo: String): String {
        val clean = vehicleNo.replace("'", "\\'")
        return "javascript:if(window.k1ScrapeViolations) { window.k1ScrapeViolations('$clean'); }"
    }
}
