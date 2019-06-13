define(['jquery', 'jsencrypt'], function($, jsencrypt) {
    var authModal = $('#authModal');
    var alertContainer = $("#alertContainer");
    var auth = {
        currentCallback: undefined,
        publicKey: undefined,
        username: undefined,
        sessionID: undefined,

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
                var encrypt = new jsencrypt.JSEncrypt();
                encrypt.setPublicKey(auth.publicKey);

                const encodedPassword = encrypt.encrypt(password);

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
                        auth.currentCallback(username, encodedPassword);
                    }
                };
                xhttp.send(username+"\n"+encodedPassword+"\n");
            });
        },

        login(username, encodedPassword) {
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
                    auth.username = username;
                    auth.sessionID = lines[1];
                    location.reload(true);
                }
            };
            xhttp.setRequestHeader("Content-Type", "application/octet-stream");
            xhttp.send(username + "\n" + encodedPassword+"\n");
        },

        signup(username, encodedPassword) {
            var xhttp = new XMLHttpRequest();
            xhttp.open("POST", "/action/signup", true);
            xhttp.onload = function () {
                var text = xhttp.responseText;
                if(text.indexOf('no') !== -1) {
                    alertContainer.html(alertContainer.html() +
                        `
                        <div class="alert alert-warning alert-dismissible fade show" role="alert">
                            <strong>Error!</strong> Impossible to signup: ${text.split("\n")[1]}
                            <button type="button" class="close" data-dismiss="alert" aria-label="Close">
                                <span aria-hidden="true">&times;</span>
                            </button>
                        </div>
                        `
                    );
                } else {
                    var lines = text.split("\n"); // first line is 'yes' to confirm auth
                    document.cookie = "SessionId="+lines[1]+";"; // TODO: expiration
                    auth.username = username;
                    auth.sessionID = lines[1];
                    location.reload(true);
                }
            };
            xhttp.setRequestHeader("Content-Type", "application/octet-stream");
            xhttp.send(username + "\n" + encodedPassword+"\n");
        },

        logout(username) {
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
            xhttp.send(username + "\n");
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

    var signupButton = $('#signupButton');

    var signupUsername = $('#signupUsername');
    var signupPassword = $('#signupPassword');
    var signupPasswordConfirm = $('#signupPasswordConfirm');

    function checkPasswordMatch(e) {
        var enabled = signupPassword.val().length > 0 && signupPassword.val() === signupPasswordConfirm.val();
        signupButton.prop('disabled', ! enabled);
    }

    signupPassword.on('keyup', checkPasswordMatch);
    signupPasswordConfirm.on('keyup', checkPasswordMatch);

    signupButton.on('click', function(e) {
        var encrypt = new jsencrypt.JSEncrypt();
        encrypt.setPublicKey(auth.publicKey);

        const encodedPassword = encrypt.encrypt(signupPassword.val());
        auth.signup(signupUsername.val(), encodedPassword);
    });

    $("#mainLoginButton").on('click', function(e) {
        auth.currentCallback = auth.login;
        auth.sendRequest(usernameInput.val(), passwordInput.val());
    });

    $("#loginButton").on('click', function(e) {
        auth.requestAuth(auth.login);
    });

    $("#logoutButton").on('click', function(e) {
        auth.logout(auth.username);
    });
    return auth;
});