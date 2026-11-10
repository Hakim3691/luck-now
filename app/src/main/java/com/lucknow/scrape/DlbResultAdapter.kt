package com.lucknow.scrape

/**
 * Development Lotteries Board (dlb.lk) results.
 *
 * The history table on `/result/<language>/` is empty in the served HTML: the
 * page fills each lottery tab from `POST /result/pagination_re` with `pageId`,
 * `resultID`, `lotteryID` and `lastsegment`. Nine lotteries share that page, so
 * the adapter reads only the panel of the tab the page opens on and labels the
 * payload with that lottery's name; mixing nine lotteries into one sequence
 * would describe none of them.
 *
 * Each row carries the draw number and date in its first cell and the drawn
 * numbers as `li.res_number`; the `li.res_eng_letter` is the draw letter and the
 * gold-highlighted number appended to that cell belongs to the 2nd-chance draw,
 * so both are skipped. Results arrive through the page bridge, which is why the
 * script can be asynchronous while the driver still bounds the wait.
 *
 * Verified against the live page: label `Kapruka`, draws from 2515 downwards,
 * four numbers per draw.
 */
object DlbResultAdapter : SiteDrawAdapter {

    override val id: String = "dlb-results"

    override val site: String = "dlb.lk"

    override fun matches(url: String): Boolean = SiteDrawAdapters.hostEndsWith(url, "dlb.lk")

    override val script: String = """
        (function () {
          try {
            var bridge = window.LuckNowBridge;
            if (!bridge || !bridge.postDraws) { return 'no-bridge'; }

            function panelFor(link) {
              var href = link.getAttribute('href') || '';
              if (href.charAt(0) !== '#') { return null; }
              var element = document.querySelector(href);
              return (element && element.querySelector('tbody[id^="pageData"]')) ? element : null;
            }

            var root = null;
            var label = null;

            function scan(links) {
              for (var i = 0; i < links.length; i++) {
                var element = panelFor(links[i]);
                if (!element) { continue; }
                root = element;
                var heading = document.querySelector('a[data-toggle="tab"][href="' + links[i].getAttribute('href') + '"] h2');
                label = heading && heading.textContent ? heading.textContent.trim() : null;
                return true;
              }
              return false;
            }

            if (!scan(document.querySelectorAll('li.active a[data-toggle="tab"]'))) {
              scan(document.querySelectorAll('a[data-toggle="tab"]'));
            }
            if (!root) { root = document; }

            var body = root.querySelector('tbody[id^="pageData"]');
            if (!body) { return 'no-table'; }

            var lotteryId = (body.id || '').replace(/^pageData/, '');

            var resultInput = document.getElementById('resultID' + lotteryId);
            var resultId = resultInput ? resultInput.value : '';
            if (!resultId) { return 'no-result-id'; }

            var MAX_PAGES = 6;
            var draws = [];
            var seen = {};

            function collect(html) {
              var doc = new DOMParser().parseFromString('<table><tbody>' + html + '</tbody></table>', 'text/html');
              var rows = doc.querySelectorAll('tr');
              var found = 0;
              for (var i = 0; i < rows.length; i++) {
                var cells = rows[i].children;
                var values = null;
                for (var c = 0; c < cells.length; c++) {
                  var numbers = cells[c].querySelectorAll('li.res_number');
                  if (numbers.length) {
                    values = [];
                    for (var n = 0; n < numbers.length; n++) {
                      // The 2nd-chance number is appended to this cell highlighted in gold.
                      // It comes from a separate draw, so it is not part of the sequence.
                      if (numbers[n].getAttribute('style')) { continue; }
                      var parsed = parseInt((numbers[n].textContent || '').trim(), 10);
                      if (!isNaN(parsed)) { values.push(parsed); }
                    }
                    break;
                  }
                }
                if (!values || !values.length) { continue; }
                var firstCell = cells.length ? cells[0] : null;
                var drawText = firstCell ? (firstCell.textContent || '').trim() : '';
                var match = /^[0-9]+/.exec(drawText);
                var draw = match ? parseInt(match[0], 10) : null;
                if (draw !== null && seen[draw]) { continue; }
                if (draw !== null) { seen[draw] = true; }
                draws.push({ draw: draw, values: values });
                found++;
              }
              return found;
            }

            function finish() {
              try {
                bridge.postDraws(JSON.stringify({ adapter: 'dlb-results', label: label, draws: draws }));
              } catch (e) {}
            }

            function fetchPage(pageId, remaining) {
              if (remaining <= 0) { finish(); return; }
              var request = new XMLHttpRequest();
              request.open('POST', '/result/pagination_re', true);
              request.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
              request.timeout = 4000;
              request.onload = function () {
                var added = 0;
                try { added = request.status === 200 ? collect(request.responseText) : 0; } catch (e) { added = 0; }
                if (added > 0) { fetchPage(pageId + 1, remaining - 1); } else { finish(); }
              };
              request.onerror = finish;
              request.ontimeout = finish;
              request.send('pageId=' + pageId + '&resultID=' + resultId + '&lotteryID=' + lotteryId + '&lastsegment=12');
            }

            fetchPage(0, MAX_PAGES);
            return 'started';
          } catch (e) { return 'error'; }
        })();
    """.trimIndent()
}
