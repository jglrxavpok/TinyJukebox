define(['jquery', 'auth'], function($, auth) {
    var playerControl = {
        sendSkipRequest: function(username, password) {
            var xhttp = new XMLHttpRequest();
            xhttp.open("POST", "/action/playercontrol/skip", true);

            xhttp.onload = function() {
                var text = xhttp.responseText;
                if(text === 'no') {
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
        }
    };
    $('#skipButton').on('click', function() {
        auth.requestAuth(playerControl.sendSkipRequest);
    });

    return playerControl;
});