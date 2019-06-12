define(["jquery", "config"], function($, config) {
    var obj = {
        init: function() {
            var quoteRequest = new XMLHttpRequest();
            quoteRequest.open('GET', '/quote', true);
            quoteRequest.send(null);
            quoteRequest.onreadystatechange = function () {
                if (quoteRequest.readyState === 4 && quoteRequest.status === 200) {
                    var type = quoteRequest.getResponseHeader('Content-Type');
                    if (type.indexOf("text") !== 1) {
                        obj.handleNewQuote(quoteRequest.responseText);
                    }
                }
            };
        },

        handleNewQuote(response) {
            if(response.indexOf("|||") !== -1) {
                var parts = response.split("|||");
                if(parts.length < 3)
                    obj.setNewQuote(parts[0], parts[1].trim());
                else
                    obj.setNewQuote(parts[0], parts[1].trim(), parts[2].trim());
            } else {
                obj.setNewQuote(response, "Anonymous");
            }
        },

        // update quote by fading-out the old one and fading-in the new one
        setNewQuote: function(quote, author, source) {
            console.log("quote: "+quote+", "+author+", "+source);
            var quoteContainer = $("#quoteContainer");
            // generate the source HTML if a source was provided
            var sourceHTML =
                source
                    ? ` in <cite title="${source}">${source}</cite>`
                    : "";
            // generate the name of the author if one was provided
            var naming =
                author
                    ? `<footer class="blockquote-footer">${author}${sourceHTML}</footer>`
                    : "";
            quoteContainer.fadeOut(config.quoteFadeOutDuration, function() { // fade out

                // generate the HTML for this quote
                quoteContainer.html(
                    `
                        <blockquote class="blockquote">
                          <p class="mb-0">${quote}</p>
                          ${naming}
                        </blockquote>
                `
                ); // change quote
                quoteContainer.fadeIn(config.quoteFadeInDuration); // fade in
            });
        }
    };
    return obj;
});