define(["jquery"], function($) {
    $(function() {
        $(".playLink").each(function() {
            var link = $(this);
            link.click(function() {
                var musicName = link.data("musicname");

                var xhttp = new XMLHttpRequest();
                xhttp.open("POST", "/play/"+musicName, true);
                xhttp.setRequestHeader("Content-Type", "application/octet-stream");
                xhttp.setRequestHeader("File-Source", "Youtube");
                xhttp.send("\n");
            });
        });
    });
});