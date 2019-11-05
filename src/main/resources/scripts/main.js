var pathsObject = {
    jquery: '/jslib/jquery-3.3.1.min',
    bootstrap: '/jslib/bootstrap.bundle.min',
    numeraljs: '/jslib/numeral.min',
    jsencrypt: '/jslib/jsencrypt.min',
};
requirejs.config({
    //By default load any module IDs from scripts/
    //except, for jQuery
    paths: pathsObject
});

requirejs(['jquery', 'bootstrap', 'quote', 'noext!scripts/config.js.twig', 'about', 'messageHandler', 'youtube-search/search', 'playerControl', 'auth', 'reupload', 'vueapp'],
function   ($, bootstrap, quote, config, about, messageHandler, ytsearch, playerControl, auth, reupload, app) {
    $('.alert').alert();

    quote.init(); // force the quote to appear right at the start
    var queueContainer = $("#queueContainer");
    var socketProtocol;
    if(config.useSSL) {
        socketProtocol = "wss";
    } else {
        socketProtocol = "ws";
    }
    var socket = new WebSocket(`${socketProtocol}://${window.location.hostname}:${config.websocketPort}`);
    ytsearch.websocket = socket;
    playerControl.websocket = socket;
    console.log("Socket addr is "+`${socketProtocol}://${window.location.hostname}:${config.websocketPort}`);

    socket.onopen = function (ev) {
        socket.send("SessionId\n"+getCookie("SessionId")+"\n");
    };

    socket.onmessage = function (ev) {
//        console.log(ev.data);
        var lines = ev.data.split("\n");
        messageHandler(lines);
    };

    socket.onerror = function (ev) {
        console.error("Error: "+ev.data);
    };

    function initHttpRequest(id) {
        var xhttp = new XMLHttpRequest();
        xhttp.upload.addEventListener("progress", updateProgress, false);
        xhttp.upload.addEventListener("load", transferComplete, false);
        xhttp.upload.addEventListener("error", transferFailed, false);
        xhttp.upload.addEventListener("abort", transferCanceled, false);

        xhttp.open("POST", "/"+id, true);

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

    function uploadLocal() {
        var xhttp = initHttpRequest("upload");
        xhttp.setRequestHeader("Content-Type", "application/octet-stream");
        var field = document.getElementById("musicFile");
        var file = field.files[0];
        xhttp.setRequestHeader("File-Size", file.size);
        xhttp.setRequestHeader("File-Name", file.name);
        xhttp.setRequestHeader("File-Source", "Local");

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

    function uploadYoutube() {
        var xhttp = initHttpRequest("upload");
        xhttp.setRequestHeader("Content-Type", "application/octet-stream");
        xhttp.setRequestHeader("File-Source", "Youtube");

        var url = $("#youtubeLink").val();
        xhttp.send(url+"\n");
    }

    if(document.getElementById("uploadLocal")) {
        document.getElementById("uploadLocal").onclick = uploadLocal;
    }

    if(document.getElementById("uploadYoutube")) {
        document.getElementById("uploadYoutube").onclick = uploadYoutube;
    }
}
);
