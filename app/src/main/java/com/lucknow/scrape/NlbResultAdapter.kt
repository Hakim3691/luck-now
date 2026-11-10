package com.lucknow.scrape

/**
 * National Lotteries Board (nlb.lk) results.
 *
 * The results are server-rendered: each row of `table.tbl` holds the draw number
 * in its first bold cell and the drawn numbers as `li[title^="Number-"]` circles
 * (the `li[title="Letter"]` circle is the draw letter and is skipped). The site
 * also answers the first request with a cookie/JavaScript stub that reloads
 * itself, so this runs only after the driver has settled on the final document.
 *
 * Verified against the live page: label `Dhana Nidhanaya Lottery Results`, 208
 * draws from 2396 downwards, four numbers per draw.
 */
object NlbResultAdapter : SiteDrawAdapter {

    override val id: String = "nlb-results"

    override val site: String = "nlb.lk"

    override fun matches(url: String): Boolean = SiteDrawAdapters.hostEndsWith(url, "nlb.lk")

    override val script: String = """
        (function () {
          try {
            var bridge = window.LuckNowBridge;
            if (!bridge || !bridge.postDraws) { return 'no-bridge'; }
            var table = document.querySelector('table.tbl') || document.querySelector('table');
            if (!table) { return 'no-table'; }
            var heading = document.querySelector('h1');
            var label = heading && heading.textContent ? heading.textContent.trim() : null;
            var rows = table.querySelectorAll('tbody tr');
            if (!rows.length) { rows = table.querySelectorAll('tr'); }
            var draws = [];
            for (var i = 0; i < rows.length; i++) {
              var row = rows[i];
              var drawCell = row.querySelector('td b');
              var draw = drawCell ? parseInt((drawCell.textContent || '').trim(), 10) : null;
              if (isNaN(draw)) { draw = null; }
              var numbers = row.querySelectorAll('li[title^="Number-"]');
              var values = [];
              for (var n = 0; n < numbers.length; n++) {
                var parsed = parseInt((numbers[n].textContent || '').trim(), 10);
                if (!isNaN(parsed)) { values.push(parsed); }
              }
              if (values.length) { draws.push({ draw: draw, values: values }); }
            }
            bridge.postDraws(JSON.stringify({ adapter: 'nlb-results', label: label, draws: draws }));
            return 'ok:' + draws.length;
          } catch (e) { return 'error'; }
        })();
    """.trimIndent()
}
