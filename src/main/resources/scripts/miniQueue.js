define(["jquery", "numeraljs"], function($, numeral) {
    var miniQueue = {
        update: function(lines) {
            var miniQueueElement = $('#miniQueue');
            if( ! miniQueueElement)
                return;
            var html = "";
            for (let i = 1; i < lines.length; i++) {
                var musicObj = JSON.parse(lines[i]);
                html += `<li class="miniQueueElement">`;
                var duration = numeral(musicObj.duration/1000).format('00:00:00');
                html += musicObj.title+" ("+duration+")";
                html += `</li>`;
            }
            miniQueueElement.html(`<p class="text-center">Current Queue</p><ul class="container" id="miniQueueContainer">${html}</ul>`);

        },
    };
    return miniQueue;
});