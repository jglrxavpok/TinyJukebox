function getCookie(cname) {
    var name = cname + "=";
    var decodedCookie = decodeURIComponent(document.cookie);
    var ca = decodedCookie.split(';');
    for(var i = 0; i <ca.length; i++) {
        var c = ca[i];
        while (c.charAt(0) == ' ') {
            c = c.substring(1);
        }
        if (c.indexOf(name) == 0) {
            return c.substring(name.length, c.length);
        }
    }
    return "";
}

define(['jquery', 'jsencrypt'], function($, jsencrypt) {
    var authModal = $('#authModal');
    var alertContainer = $("#alertContainer");
    var auth = {
        currentCallback: undefined,
        publicKey: undefined,

        sessionID: getCookie("SessionId"),
        username: getCookie("Username"),
        permissions: getCookie("Permissions"),

        handleSessionInfo(lines) {
            document.cookie = "SessionId="+lines[1]+";"; // TODO: expiration
            document.cookie = "Username="+lines[2]+";"; // TODO: expiration
            document.cookie = "Permissions="+lines[3]+";"; // TODO: expiration
            auth.sessionID = lines[1];
            auth.username = lines[2];
            auth.permissions = lines[3];
        },

        hasPermission(permission) {
            return auth.permissions.split(',').includes(permission);
        },

        /**
         * Opens the auth modal and calls the given callback if the auth passed
         * @param callback
         */
        requestAuth(callback) {
            callback(auth.sessionID)
        },

        encrypt(txt) {
            var encrypt = new jsencrypt.JSEncrypt();
            encrypt.setPublicKey(auth.publicKey);

            return encrypt.encrypt(txt);
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
                    auth.handleSessionInfo(lines);
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
                console.log(text);
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
                    auth.handleSessionInfo(lines);
                    location.reload(true);
                }
            };
            xhttp.setRequestHeader("Content-Type", "application/octet-stream");
            xhttp.send(username + "\n" + encodedPassword+"\n");
        },

        logout() {
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
                    document.cookie = "Username=undefined; expires=Thu, 01 Jan 1970 00:00:01 GMT;"; // delete cookie
                    document.cookie = "Permissions=NONE; expires=Thu, 01 Jan 1970 00:00:01 GMT;"; // delete cookie

                    window.location.href = "/";
                }
            };
            xhttp.setRequestHeader("Content-Type", "application/octet-stream");
            xhttp.send(auth.sessionID + "\n");
        }
    };

    var usernameInput = $('#authUsername');
    var passwordInput = $('#authPassword');
    $('#authCheckButton').on('click', function(e) {
        auth.sendRequest(usernameInput.val(), passwordInput.val());
    });
    passwordInput.on('keyup', function(e) {
        if (e.which === 13) {
            auth.login(usernameInput.val(), auth.encrypt(passwordInput.val()));
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
        auth.signup(signupUsername.val(), auth.encrypt(signupPassword.val()));
    });

    $("#mainLoginButton").on('click', function(e) {
        auth.login(usernameInput.val(), auth.encrypt(passwordInput.val()));
    });

    $("#logoutButton").on('click', function(e) {
        auth.logout();
    });
    return auth;
});