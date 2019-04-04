define(['jquery'], function($) {
    var authModal = $('#authModal');
    var alertContainer = $("#alertContainer");
    var auth = {
        currentCallback: undefined,

        /**
         * Opens the auth modal and calls the given callback if the auth passed
         * @param callback
         */
        requestAuth(callback) {
            auth.currentCallback = callback;
            authModal.modal('show');
        },

        /**
         * Asks the server if the credentials are valid
         * @param username
         * @param password
         */
        sendRequest(username, password) {
            var xhttp = new XMLHttpRequest();
            xhttp.open("POST", "/action/auth", true);
            const encoder = new TextEncoder("UTF-8");
            const data = encoder.encode(password);
            window.crypto.subtle.digest('SHA-256', data).then(digestValue => {
                const passwordHash = [...new Uint8Array(digestValue)].map(value => {
                    return value.toString(16).padStart(2, '0')
                }).join('');
                xhttp.onload = function() {
                    var text = xhttp.responseText;
                    authModal.modal('hide');
                    if (text.indexOf('no') !== -1) {
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
                    } else {
                        auth.currentCallback(username, passwordHash);
                    }
                };
                xhttp.send(username+"\n"+passwordHash+"\n");
            });
        },

        login(username, hash) {
            var xhttp = new XMLHttpRequest();
            xhttp.open("POST", "/action/login", true);
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
                } else {
                    var lines = text.split("\n"); // first line is 'yes' to confirm auth
                    document.cookie = "SessionId="+lines[1]+";"; // TODO: expiration
                    location.reload(true);
                }
            };
            xhttp.setRequestHeader("Content-Type", "application/octet-stream");
            xhttp.send(username + "\n" + hash+"\n");
        },

        logout(username, hash) {
            var xhttp = new XMLHttpRequest();
            xhttp.open("POST", "/action/logout", true);
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
                } else {
                    document.cookie = "SessionId=00000000-0000-0000-0000-000000000000; expires=Thu, 01 Jan 1970 00:00:01 GMT;"; // delete cookie
                    location.reload(true);
                }
            };
            xhttp.setRequestHeader("Content-Type", "application/octet-stream");
            xhttp.send(username + "\n" + hash+"\n");
        }
    };

    var usernameInput = $('#authUsername');
    var passwordInput = $('#authPassword');
    authModal.on('shown.bs.modal', function () {
        usernameInput.trigger('focus')
    });
    $('#authCheckButton').on('click', function(e) {
        auth.sendRequest(usernameInput.val(), passwordInput.val());
    });
    passwordInput.on('keyup', function(e) {
        if (e.which === 13) {
            auth.sendRequest(usernameInput.val(), passwordInput.val());
        }
    });


    $("#loginButton").on('click', function(e) {
        auth.requestAuth(auth.login);
    });

    $("#logout").on('click', function(e) {
        auth.requestAuth(auth.logout);
    });
    return auth;
});