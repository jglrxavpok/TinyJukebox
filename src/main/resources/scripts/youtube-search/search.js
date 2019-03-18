define(['jquery'], function($) {
    // open youtube modal
    var searchInput = $('#ytSearchInput');
    var ytModal = $('#ytModal');
    ytModal.on('shown.bs.modal', function () {
        searchInput.trigger('focus')
    });
    $('#ytSearchButton').on('click', function(e) {
        sendRequest(searchInput.val());
    });
    searchInput.on('keyup', function(e) {
        if (e.which === 13) {
            sendRequest(searchInput.val());
        }
    });
});

function sendRequest(query) {
    var xhttp = new XMLHttpRequest();

    xhttp.open("POST", "/action/ytsearch", true);
    xhttp.onload = function() {
        var text = xhttp.responseText;
        var items = JSON.parse(text);
        var htmlContent = "";
        for(item of items) {
            var videoID = item.id;
            var title = item.title;
            var channel = item.channel;
            var duration = item.duration;
            var thumbnail = "https://i.ytimg.com/vi/"+videoID+"/mqdefault.jpg";
            htmlContent +=
                `
                    <div class="col-md-5" style="margin: 10px;">
                        <a class="videoSelect" href="#" data-videoid="${videoID}">
                            <div class="yt-preview">
                                <div class="container">
                                    <img src='${thumbnail}' style="width:100%;" alt='Thumbnail for ${title}'/>
                                    <div class="bg-dark text-right text-light bottom-right">${duration}</div>
                                </div>
                                <div class="yt-details"><b>${title}<br/>${channel}</b></div> 
                            </div>
                        </a>
                    </div>
                `;
        }
        $('#yt-search-results').html(
            `
            ${htmlContent}
            `
        );
        $(".videoSelect").each(function() {
            var link = $(this);
            link.on('click', function(e) {
                var videoID = link.data("videoid");

                var xhttp = new XMLHttpRequest();
                xhttp.open("POST", "/action/upload", true);
                xhttp.setRequestHeader("Content-Type", "application/octet-stream");
                xhttp.setRequestHeader("File-Source", "Youtube");
                $('#ytModal').modal('hide');
                xhttp.send("https://youtube.com/watch?v="+videoID+"\n");
            });
        });
    };
    xhttp.send(query+"\n");
}