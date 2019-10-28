define(['jquery', 'vueapp'], function($, app) {

    var ytsearch = {
        websocket: undefined,
        loadingFirst: false,
        currentQuery: "",

        sendRequest: function(query) {
            ytsearch.loadingFirst = true;
            ytsearch.currentQuery = query;
            var searchResults = $('#yt-search-results');
            searchResults.html("<span class='loader'></span>"); // clear search results and show loading animation
            ytsearch.websocket.send("ytsearch\n"+query+"\n")
        },

        addResult: function(query, item) {
            var searchResults = $('#yt-search-results');
            if(ytsearch.currentQuery !== query || ytsearch.loadingFirst) {
                searchResults.html(""); // clear search results
                if(ytsearch.loadingFirst) {
                    ytsearch.loadingFirst = false;
                }
            }
            var videoID = item.id;
            var title = item.title;
            var channel = item.channel;
            var duration = item.duration;
            var thumbnail = "https://i.ytimg.com/vi/"+videoID+"/mqdefault.jpg";
            searchResults.append(
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
                `);
            $(".videoSelect").each(function() {
                var link = $(this);
                link.off('click').on('click', function(e) {
                    var videoID = link.data("videoid");

                    var xhttp = new XMLHttpRequest();
                    xhttp.open("POST", "/upload", true);
                    xhttp.setRequestHeader("Content-Type", "application/octet-stream");
                    xhttp.setRequestHeader("File-Source", "Youtube");
                    $('#ytModal').modal('hide');
                    xhttp.send("https://youtube.com/watch?v="+videoID+"\n");
                });

            });
        }
    };
    // open youtube modal
    var searchInput = $('#ytSearchInput');
    var ytModal = $('#ytModal');
    ytModal.on('shown.bs.modal', function () {
        searchInput.trigger('focus')
    });
    $('#ytSearchButton').on('click', function(e) {
        ytsearch.sendRequest(searchInput.val());
    });
    searchInput.on('keyup', function(e) {
        if (e.which === 13) {
            ytsearch.sendRequest(searchInput.val());
        }
    });

    return ytsearch;
});
