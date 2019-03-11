define(['jquery', 'bootstrap'],
function($, bootstrap) {
    var playingContainer = $("#playingContainer");
    var queueContainer = $("#queueContainer");
    var alertContainer = $("#alertContainer");

    return function(lines) {
        switch (lines[0]) {
            case "queue":
                var queueHTML = "<ol>";
                for (let i = 1; i < lines.length; i++) {
                    queueHTML += "<li><i>"+lines[i]+"</i></li>";
                }
                queueHTML += "</ol>";
                queueContainer.html(queueHTML);
                break;

            case "playerUpdate":
                var actuallyPlaying = lines[1] === "true";
                if(actuallyPlaying) {
                    var name = lines[2];
                    var currentTime = lines[3];
                    var totalTime = lines[4];
                    var percent = Math.round(lines[5]*1000)/10;
                    console.log(percent);
                    playingContainer.html(`
                        <h1>Currently playing: <i>${name}</i> (${currentTime} - ${totalTime})</h1>
                        <div class="progress">
                          <div class="progress-bar bg-success" role="progressbar" style="width: ${percent}%" aria-valuenow="${percent}" aria-valuemin="0" aria-valuemax="100"></div>
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
        }
    };
}
);