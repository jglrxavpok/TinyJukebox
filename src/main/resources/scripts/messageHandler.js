define(['jquery', 'bootstrap', 'playerControl', 'auth', 'quote', 'numeraljs', 'miniQueue', 'youtube-search/search', 'vueapp'],
function($, bootstrap, playerControl, auth, quote, numeral, miniQueue, ytsearch, app) {
    var queueContainer = $("#queueContainer");
    var alertContainer = $("#alertContainer");

    function hasNoControl() {
        return !auth.hasPermission("Move") && !auth.hasPermission("Remove") && !auth.hasPermission("RemoveLocked") && !auth.hasPermission("Lock");
    }

    return function(lines) {
        switch (lines[0]) {
            case "ytsearch":
                ytsearch.addResult(lines[1], JSON.parse(lines[2]));
                break;

            case "queue":
                var controlsHeaderHTML = '<th scope="col" class="text-nowrap text-center">Controls</th>';
                const noControl = hasNoControl();
                if(auth.sessionID === undefined || noControl) {
                    controlsHeaderHTML = '';
                }
                var queueHTML = `                
                <table class="table foreground">
                  <thead>
                    <tr>
                      <th scope="col">#</th>
                      <th scope="col" class="text-nowrap text-center">Name</th>
                      <th scope="col" class="text-nowrap text-center">Duration</th>
                      ${controlsHeaderHTML}
                    </tr>
                  </thead>
                  <tbody>`;
                var totalTime = 0;
                miniQueue.update(lines);
                for (let i = 1; i < lines.length; i++) {
                    //queueHTML += "<tr><i>"+lines[i]+`</i> <a href='#' class='queueRemoval' data-name='${lines[i]}'>&times;</a></tr>`;
                    var musicObj = JSON.parse(lines[i]);
                    var duration = numeral(musicObj.duration/1000).format('00:00:00');
                    totalTime += musicObj.duration;
                    var escapedTitle = encodeURIComponent(musicObj.title);
                    console.log(escapedTitle);
                    var moveUpHTML = `<i class="fas fa-chevron-up moveUp fa-3x clickable" data-index="${i-1}" data-name="${escapedTitle}"></i>`;
                    if(i === 1)
                        moveUpHTML = "";
                    var moveDownHTML = `<i class="fas fa-chevron-down moveDown fa-3x clickable" data-index="${i-1}" data-name="${escapedTitle}"></i>`;
                    if(i === lines.length-1)
                        moveDownHTML = "";
                    var moveStartHTML = `<i class="fas fa-angle-double-up moveToStart fa-3x clickable" data-index="${i-1}" data-name="${escapedTitle}"></i>`;
                    if(i === 1)
                        moveStartHTML = "";
                    var moveEndHTML = `<i class="fas fa-angle-double-down moveToEnd fa-3x clickable" data-index="${i-1}" data-name="${escapedTitle}"></i>`;
                    if(i === lines.length-1)
                        moveEndHTML = "";

                    if( ! auth.hasPermission("Move")) {
                        moveUpHTML = "";
                        moveDownHTML = "";
                        moveStartHTML = "";
                        moveEndHTML = "";
                    }
                    var removeHTML = `<i class="fas fa-times queueRemoval fa-3x clickable" data-index="${i-1}" data-name="${escapedTitle}"></i>`;
                    if( ! auth.hasPermission("Remove") || (musicObj.locked && ! auth.hasPermission("RemoveLocked"))) {
                        removeHTML = "";
                    }

                    var lockType = musicObj.locked ? "lock" : "lock-open";
                    var lockHTML = `<i class="fas fa-${lockType} fa-3x lockMusic clickable" data-locked="${musicObj.locked}" data-index="${i-1}" data-name="${escapedTitle}"></i>`;
                    if( !auth.hasPermission("Lock")) {
                        lockHTML = '';
                    }
                    var controlsHTML = `<td class="text-nowrap text-center">
                            ${lockHTML}
                            ${moveStartHTML}
                            ${moveUpHTML}
                            ${removeHTML}
                            ${moveDownHTML}
                            ${moveEndHTML}
                        </td>`;
                    if(auth.sessionID === undefined || noControl) {
                        controlsHTML = '';
                    }
                    var lock = "";
                    if(musicObj.locked) {
                        lock = `<i class="fas fa-lock" style="color: lightgray;"></i>`;
                    }
                    queueHTML += `
                    <tr>
                        <th scope="row">${i}</th>
                        <td>${lock}${musicObj.title}</td>
                        <td>${duration}</td>
                        ${controlsHTML}
                    </tr>
                    `;
                }
                var clearQueueHTML =
                    `<td class="text-nowrap text-center">
                        <button class="btn btn-danger" id="empty">
                            Clear queue
                        </button>
                    </td>`;
                if(auth.sessionID === undefined || noControl || !auth.hasPermission("EmptyQueue")) {
                    clearQueueHTML = '';
                }
                var totalDurationStr = numeral(totalTime/1000).format('00:00:00');
                queueHTML += `
                    <tr>
                        <th scope="row">Total</th>
                        <td>${lines.length-1} tracks</td>
                        <td>${totalDurationStr}</td>
                        ${clearQueueHTML}
                    </tr>
                    `;

                queueHTML += "</tbody></table>";
                queueContainer.html(queueHTML);

                $('#empty').on('click', function() {
                    auth.requestAuth(playerControl.sendEmptyRequest);
                });

                $(".queueRemoval").each(function() {
                    var link = $(this);
                    link.off('click').on('click', function(e) {
                        var name = link.data("name");
                        var index = link.data("index");
                        auth.requestAuth(playerControl.sendRemoveRequestSupplier(name, index));
                    });
                });

                $(".moveUp").each(function() {
                    var link = $(this);
                    link.off('click').on('click', function(e) {
                        var name = link.data("name");
                        var index = link.data("index");
                        auth.requestAuth(playerControl.sendMoveUpRequestSupplier(name, index));
                    });
                });

                $(".moveDown").each(function() {
                    var link = $(this);
                    link.off('click').on('click', function(e) {
                        var name = link.data("name");
                        var index = link.data("index");
                        auth.requestAuth(playerControl.sendMoveDownRequestSupplier(name, index));
                    });
                });

                $(".moveToStart").each(function() {
                    var link = $(this);
                    link.off('click').on('click', function(e) {
                        var name = link.data("name");
                        var index = link.data("index");
                        auth.requestAuth(playerControl.sendMoveToStartRequestSupplier(name, index));
                    });
                });

                $(".moveToEnd").each(function() {
                    var link = $(this);
                    link.off('click').on('click', function(e) {
                        var name = link.data("name");
                        var index = link.data("index");
                        auth.requestAuth(playerControl.sendMoveToEndRequestSupplier(name, index));
                    });
                });

                $(".lockMusic").each(function() {
                    var link = $(this);
                    link.off('click').on('click', function(e) {
                        var name = link.data("name");
                        var index = link.data("index");
                        var locked = link.data("locked");
                        if(locked) {
                            auth.requestAuth(playerControl.sendUnlockRequestSupplier(name, index));
                        } else {
                            auth.requestAuth(playerControl.sendLockRequestSupplier(name, index));
                        }
                    });
                });
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