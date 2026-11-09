package com.lucknow.scrape

/** JavaScript used by the hidden browser session. */
internal object ScrollScript {

    /**
     * Installed at document start so that `fetch` and `XMLHttpRequest` payloads
     * that omit an `Accept: application/json` header are still captured.
     */
    val documentStartHooks = """
        (function () {
          if (window.__luckNowHooked) { return; }
          window.__luckNowHooked = true;
          var MAX = 512000;
          function send(url, body) {
            try {
              if (typeof body !== 'string' || body.length < 2) { return; }
              var head = body.trim().charAt(0);
              if (head !== '{' && head !== '[') { return; }
              if (window.LuckNowBridge && window.LuckNowBridge.postJson) {
                window.LuckNowBridge.postJson(String(url || location.href), body.slice(0, MAX));
              }
            } catch (e) {}
          }
          var originalFetch = window.fetch;
          if (originalFetch) {
            window.fetch = function (input, init) {
              var requestUrl = (typeof input === 'string') ? input : (input && input.url) || location.href;
              return originalFetch.apply(this, arguments).then(function (response) {
                try {
                  response.clone().text().then(function (text) { send(requestUrl, text); }).catch(function () {});
                } catch (e) {}
                return response;
              });
            };
          }
          var originalOpen = XMLHttpRequest.prototype.open;
          var originalSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function (method, url) {
            try { this.__luckNowUrl = url; } catch (e) {}
            return originalOpen.apply(this, arguments);
          };
          XMLHttpRequest.prototype.send = function () {
            var xhr = this;
            xhr.addEventListener('load', function () {
              try {
                if (typeof xhr.responseText === 'string') { send(xhr.__luckNowUrl, xhr.responseText); }
              } catch (e) {}
            });
            return originalSend.apply(this, arguments);
          };
        })();
    """.trimIndent()

    /**
     * One pagination/scroll step: scroll to the bottom, then click an obvious
     * "next" control if the page exposes one. Returns the new scroll height.
     */
    val scrollOnce = """
        (function () {
          try {
            window.scrollBy(0, Math.max(window.innerHeight * 0.9, 400));
            var selectors = [
              '[rel="next"]',
              'a[aria-label*="next" i]',
              'button[aria-label*="next" i]',
              'a.next:not([disabled])',
              'button.next:not([disabled])',
              '.pagination .next a',
              '[data-testid="next-page"]'
            ];
            for (var i = 0; i < selectors.length; i++) {
              var node = document.querySelector(selectors[i]);
              if (node && !node.disabled) { node.click(); break; }
            }
            return String(document.body ? document.body.scrollHeight : 0);
          } catch (e) { return '0'; }
        })();
    """.trimIndent()
}
