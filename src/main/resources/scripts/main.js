requirejs.config({
    //By default load any module IDs from scripts/
    baseUrl: 'scripts/',
    //except, for jQuery
    paths: {
        jquery: 'https://code.jquery.com/jquery-3.3.1.min' // TODO: add option to use a local version? (in case TinyJukebox is only on a local network)
    }
});

requirejs(['jquery', 'quote', 'config'],
function   ($, quote, config) {
    quote.update(); // force the quote to appear right at the start
    setInterval(quote.update, config.quoteChangePeriod); // TODO: configurable
    var queueContainer = $("#queueContainer");
    var playingContainer = $("#playingContainer");
    // TODO: configurable port
    var socket = new WebSocket(`ws://${window.location.hostname}:${config.websocketPort}`);
    console.log("Socket addr is "+`ws://${window.location.hostname}:${config.websocketPort}`);

    socket.onopen = function (ev) {
        queueContainer.text("Connected!");
    };

    socket.onmessage = function (ev) {
        console.log(ev.data);
        var lines = ev.data.split("\n");
        console.log(lines[0]);
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
        }
    };

    socket.onerror = function (ev) {
        queueContainer.text("Error: "+ev.data);
    };

    function initHttpRequest(id) {
        var xhttp = new XMLHttpRequest();
        xhttp.upload.addEventListener("progress", updateProgress, false);
        xhttp.upload.addEventListener("load", transferComplete, false);
        xhttp.upload.addEventListener("error", transferFailed, false);
        xhttp.upload.addEventListener("abort", transferCanceled, false);

        xhttp.open("POST", "/action/"+id, true);

        var transferDiv = document.getElementById("transferProgress");
        // downloading
        function updateProgress (oEvent) {
            if (oEvent.lengthComputable) {
                var percentComplete = oEvent.loaded / oEvent.total;
                var percent = Math.round(percentComplete*100);
                transferDiv.innerHTML =
                    `<div class="progress">
                        <div class="progress-bar progress-bar-striped progress-bar-animated" role="progressbar" style="width: ${percent}%" aria-valuenow=percent aria-valuemin="0" aria-valuemax="100"></div>
                    </div>`;
            } else {
                // Unknown size
            }
        }

        function transferComplete(evt) {
            transferDiv.innerHTML = "Complete!";
        }

        function transferFailed(evt) {
            transferDiv.innerHTML = "Failed :(";
        }

        function transferCanceled(evt) {
            transferDiv.innerHTML = "Cancelled";
        }
        return xhttp;
    }

    function empty() {
        var xhttp = initHttpRequest("empty");
        xhttp.setRequestHeader("Content-Type", "application/octet-stream");
        xhttp.send(null);
    }

    if(document.getElementById("empty")) {
        document.getElementById("empty").onclick = empty;
    }

    function upload() {
        var xhttp = initHttpRequest("upload");
        xhttp.setRequestHeader("Content-Type", "application/octet-stream");
        var field = document.getElementById("musicFile");
        var file = field.files[0];
        xhttp.setRequestHeader("File-Size", file.size);
        xhttp.setRequestHeader("File-Name", file.name);

        var transferDiv = document.getElementById("transferProgress");
        transferDiv.innerHTML = "Loading file...";
        var reader = new FileReader();
        reader.onload = function(e) {
            var arrayBuffer = reader.result;
            transferDiv.innerHTML = "Sending!";
            xhttp.send(arrayBuffer+"\n");
        };

        reader.readAsDataURL(file);
    }

    if(document.getElementById("upload")) {
        document.getElementById("upload").onclick = upload;
    }
}
);
