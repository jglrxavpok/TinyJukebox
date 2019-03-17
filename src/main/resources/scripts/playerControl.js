define(['jquery', 'auth'], function($, auth) {
    var alertContainer = $("#alertContainer");
    var playerControl = {
        sendSkipRequest: function(username, password) {
            var xhttp = new XMLHttpRequest();
            xhttp.open("POST", "/action/playercontrol/skip", true);

            xhttp.onload = function() {
                var text = xhttp.responseText;
                if(text.indexOf('no') !== -1) {
                    alertContainer.html(alertContainer.html()+
                        `
                    <div class="alert alert-warning alert-dismissible fade show" role="alert">
                        <strong>Error!</strong> Invalid credentials
                        <button type="button" class="close" data-dismiss="alert" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                        </button>
                    </div>
                    `
                    );
                }
            };
            xhttp.send(username+"\n"+password+"\n");
        },

        sendEmptyRequest: function(username, password) {
            var xhttp = new XMLHttpRequest();
            xhttp.open("POST", "/action/playerControl/empty", true);
            xhttp.onload = function() {
                var text = xhttp.responseText;
                if(text.indexOf('no') !== -1) {
                    alertContainer.html(alertContainer.html()+
                        `
                    <div class="alert alert-warning alert-dismissible fade show" role="alert">
                        <strong>Error!</strong> Invalid credentials
                        <button type="button" class="close" data-dismiss="alert" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                        </button>
                    </div>
                    `
                    );
                }
            };
            xhttp.setRequestHeader("Content-Type", "application/octet-stream");
            xhttp.send(username+"\n"+password+"\n");
        },

        sendRemoveRequestSupplier: function(name) {
            return function(username, password) {
                var xhttp = new XMLHttpRequest();
                xhttp.open("POST", "/action/playerControl/remove", true);
                xhttp.onload = function () {
                    var text = xhttp.responseText;
                    if(text.indexOf('no') !== -1) {
                        alertContainer.html(alertContainer.html() +
                            `
                        <div class="alert alert-warning alert-dismissible fade show" role="alert">
                            <strong>Error!</strong> Invalid credentials
                            <button type="button" class="close" data-dismiss="alert" aria-label="Close">
                                <span aria-hidden="true">&times;</span>
                            </button>
                        </div>
                        `
                        );
                    }
                };
                xhttp.setRequestHeader("Content-Type", "application/octet-stream");
                xhttp.send(username + "\n" + password + "\n"+name+"\n");
            }
        }

    };
    $('#skipButton').on('click', function() {
        auth.requestAuth(playerControl.sendSkipRequest);
    });
    $('#empty').on('click', function() {
        auth.requestAuth(playerControl.sendEmptyRequest);
    });
    return playerControl;
});