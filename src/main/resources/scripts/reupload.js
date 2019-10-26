define(["jquery"], function($) {
    $(function() {
        setInterval(function() {
            $(".playLink").each(function() {
                var link = $(this);
                link.off('click').click(function() {
                    var musicName = link.data("musicname");

                    var xhttp = new XMLHttpRequest();
                    xhttp.open("POST", "/play/"+musicName, true);
                    xhttp.setRequestHeader("Content-Type", "application/octet-stream");
                    xhttp.setRequestHeader("File-Source", "Youtube");
                    xhttp.send("\n");
                });
            });
        }, 1000);
    });
});