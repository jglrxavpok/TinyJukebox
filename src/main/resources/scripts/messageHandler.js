define(['jquery', 'bootstrap', 'playerControl', 'auth', 'quote'],
function($, bootstrap, playerControl, auth, quote) {
    var playingContainer = $("#playingContainer");
    var queueContainer = $("#queueContainer");
    var alertContainer = $("#alertContainer");

    return function(lines) {
        switch (lines[0]) {
            case "queue":
                var queueHTML = `                
                <table class="table">
                  <thead>
                    <tr>
                      <th scope="col">#</th>
                      <th scope="col">Name</th>
                      <th scope="col">Duration</th>
                      <th scope="col">Controls</th>
                    </tr>
                  </thead>
                  <tbody>`;
                for (let i = 1; i < lines.length; i++) {
                    //queueHTML += "<tr><i>"+lines[i]+`</i> <a href='#' class='queueRemoval' data-name='${lines[i]}'>&times;</a></tr>`;
                    var musicObj = JSON.parse(lines[i]);
                    queueHTML += `
                    <tr>
                        <th scope="row">${i}</th>
                        <td>${musicObj.title}</td>
                        <td>${musicObj.duration}</td>
                        <td><a href='#' class='queueRemoval' data-name='${musicObj.title}'><h5 class="display-5">&times;</h5></a></td>
                    </tr>
                    `;
                }
                queueHTML += "</tbody></table>";
                queueContainer.html(queueHTML);

                $(".queueRemoval").each(function() {
                    var link = $(this);
                    link.on('click', function(e) {
                        var name = link.data("name");
                        auth.requestAuth(playerControl.sendRemoveRequestSupplier(name));
                    });
                });
                break;

            case "playerUpdate":
                var actuallyPlaying = lines[1] === "true";
                if(actuallyPlaying) {
                    var name = lines[2];
                    var currentTime = lines[3];
                    var totalTime = lines[4];
                    var percent = Math.round(lines[5]*1000)/10;
                    playingContainer.html(`
                        <div class="border rounded m-1">
                            <p class="fluid-container text-center"><h3 class="display-3 text-center">${name}</h3></p>
                            <div class="d-flex justify-content-between">
                                <div>0:00</div>
                                <div style="position: absolute; left: ${percent}%;"><h4 class="display-6">↓ ${currentTime}</h4></div>
                                <div>${totalTime}</div>
                            </div>
                            <div class="progress">
                              <div class="progress-bar bg-success" role="progressbar" style="width: ${percent}%" aria-valuenow="${percent}" aria-valuemin="0" aria-valuemax="100"></div>
                            </div>
                        </div>
                        `);
                } else {
                    playingContainer.html("<h1>Not playing anything</h1>")
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
        }
    };
}
);