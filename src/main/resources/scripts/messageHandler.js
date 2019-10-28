define(['jquery', 'bootstrap', 'playerControl', 'auth', 'quote', 'numeraljs', 'miniQueue', 'youtube-search/search', 'vueapp'],
function($, bootstrap, playerControl, auth, quote, numeral, miniQueue, ytsearch, app) {
    var queueContainer = $("#queueContainer");
    var alertContainer = $("#alertContainer");

    return function(lines) {
        switch (lines[0]) {
            case "ytsearch":
                ytsearch.addResult(lines[1], JSON.parse(lines[2]));
                break;

            case "queue":
                var totalTime = 0;
                miniQueue.update(lines);
                app.queue = [];
                for (let i = 1; i < lines.length; i++) {
                    //queueHTML += "<tr><i>"+lines[i]+`</i> <a href='#' class='queueRemoval' data-name='${lines[i]}'>&times;</a></tr>`;
                    var musicObj = JSON.parse(lines[i]);
                    var duration = numeral(musicObj.duration/1000).format('00:00:00');
                    totalTime += musicObj.duration;
                    app.queue.push(app.Music(musicObj.title, duration, musicObj.uploader, musicObj.locked));
                }
                var totalDurationStr = numeral(totalTime/1000).format('00:00:00');
                app.queue.totalDuration = totalDurationStr;
                app.updateQueueEventHandlers();
                break;

            case "playerUpdate":
                var actuallyPlaying = lines[1] === "true";
                if(actuallyPlaying) {
                    var name = lines[2];
                    var currentTime = numeral(lines[3]/1000).format('00:00:00');
                    var totalTime = numeral(lines[4]/1000).format('00:00:00');
                    document.title="♪ TinyJukebox - "+name+` (${totalTime})`;
                    var percent = Math.round(lines[5]*1000)/10;
                    var isLoading = lines[6];
                    app.playerState.loading = isLoading === "true";
                    app.playerState.hasMusic = true;
                    app.playerState.name = name;
                    app.playerState.percent = percent;
                    app.playerState.duration = totalTime;
                    app.playerState.currentTime = currentTime;
                } else {
                    document.title="TinyJukebox";
                    app.playerState.hasMusic = false;
                }
                break;

            case "error":
                var message = lines[1];
                alertContainer.html(alertContainer.html()+
                    `
                    <div class="alert alert-warning alert-dismissible fade show" role="alert">
                        <strong>Error!</strong> ${message}
                        <button type="button" class="close" data-dismiss="alert" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                        </button>
                    </div>
                    `
                );
                break;

            case "quote":
                quote.handleNewQuote(lines[1]);
                break;

            case "PublicKey":
                auth.publicKey = lines.slice(1).join("\n");
                console.log("Received pub key: "+auth.publicKey);
                break;

            case "connected": {
                var connectedListContainer = $('#connectedList');
                var html = "";
                for (let i = 1; i < lines.length; i++) {
                    if(i !== 1) {
                        html += ' | ';
                    }
                    html += `<a class="usernameLink" href="/user/${lines[i]}">${lines[i]}</a>`
                }
                connectedListContainer.html(html);
                break;
            }

            default:
                console.error("Unknown message type "+lines[0]);
                break;
        }
    };
}
);