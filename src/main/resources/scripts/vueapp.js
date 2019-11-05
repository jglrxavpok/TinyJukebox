define(["jquery", "auth", 'playerControl'], function($, auth, playerControl) {
    var player = Vue.component("playing-container", {
        props: ['playerState'],
        template: `
            <div class="playingContainer" id="playingContainer">
                <div v-if="playerState.hasMusic">
                    <div class="foreground border rounded m-1">
                        <p class="fluid-container text-center"><h3 class="display-3 text-center">
                        <div v-if="playerState.loading">
                            <div class="d-inline"><div class="loadingIcon d-inline-block">\u231B</div> Loading</div>
                        </div>
                        <div v-else>
                            {{ playerState.name }}
                        </div>
                        </h3></p>
                        <div class="progress">
                          <div class="progress-bar bg-success text-left" role="progressbar" :style="{ width: playerState.percent + '%' }" :aria-valuenow="playerState.percent" aria-valuemin="0" aria-valuemax="100">
                            {{ playerState.currentTime }}
                          </div>
                          <div class="progress-bar bg-light text-right text-black-50" role="progressbar" :style="{ width: (100-playerState.percent) + '%' }" :aria-valuenow="100-playerState.percent" aria-valuemin="0" aria-valuemax="100">
                            {{ playerState.remaining }}
                          </div>
                        </div>
                    </div>
                </div>
                <div v-else>
                    <div class="foreground border rounded m-1">
                        <p class="fluid-container text-center"><h3 class="display-3 text-center">¯\\_(ツ)_/¯ Not playing anything ¯\\_(ツ)_/¯</h3></p>
                    </div>
                </div>
            </div>
            `,
    });

    var queue = Vue.component('queue-container', {
        props: ['queue', 'auth'],
        data: function() {
           return {

           };
        },
        computed: {
            showControls() {
                return !(auth.hasNoControl() || auth.sessionID === undefined);
            },
        },
        template: `
        <div class="queueContainer foreground" id="queueContainer">
            <table class="table foreground">
                <thead>
                    <tr>
                        <th scope="col">#</th>
                        <th scope="col">Title</th>
                        <th scope="col">Duration</th>
                        <th scope="col">Sender</th>
                        <th scope="col" v-if="showControls">Controls</th>
                    </tr>
                </thead>

                <tr v-for="(music, index) in queue">
                    <th scope="row">{{ index+1 }}</th>
                    <td>
                        <i class="fas fa-lock" style="color: lightgray;" v-show="music.locked"></i>
                        {{music.name}}
                    </td>
                    <td>{{music.duration}}</td>
                    <td>{{music.uploader}}</td>
                    <td v-if="showControls" class="text-nowrap text-center">
                        <i v-if="auth.hasPermission('Lock')" class="fas lockMusic fa-3x clickable" :class="{ 'fa-lock': music.locked, 'fa-lock-open': !music.locked}" :data-index="index" :data-name="music.escapedName" :data-locked="music.locked"></i>
                        <i v-if="index !== 0 && ((auth.hasPermission('Move') && !music.locked) || auth.hasPermission('MoveLocked'))" class="fas fa-angle-double-up moveToStart fa-3x clickable" :data-index="index" :data-name="music.escapedName"></i>
                        <i v-if="index !== 0 && ((auth.hasPermission('Move') && !music.locked) || auth.hasPermission('MoveLocked'))" class="fas fa-chevron-up moveUp fa-3x clickable" :data-index="index" :data-name="music.escapedName"></i>
                        <i v-if="(auth.hasPermission('Remove') && !music.locked) || auth.hasPermission('RemoveLocked')" class="fas fa-times queueRemoval fa-3x clickable" :data-index="index" :data-name="music.escapedName"></i>
                        <i v-if="index !== queue.length-1 && ((auth.hasPermission('Move') && !music.locked) || auth.hasPermission('MoveLocked'))" class="fas fa-chevron-down moveDown fa-3x clickable" :data-index="index" :data-name="music.escapedName"></i>
                        <i v-if="index !== queue.length-1 && ((auth.hasPermission('Move') && !music.locked) || auth.hasPermission('MoveLocked'))" class="fas fa-angle-double-down moveToEnd fa-3x clickable" :data-index="index" :data-name="music.escapedName"></i>
                    </td>
                </tr>
                <tr>
                    <th scope="row">Total</th>
                    <td>{{ queue.length }} tracks</td>
                    <td>{{ queue.totalDuration }}</td>
                    <td> - </td>
                    <td v-if="showControls">
                        <button v-if="auth.hasPermission('EmptyQueue')" class="btn btn-danger" id="empty">
                            Clear queue
                        </button>
                    </td>
                </tr>
            </table>
        </div>
        `
    });

    var app = new Vue({
        el: '#vueapp',
        components: {
            "playing-container": player,
            "queue-container": queue,
        },
        data: {
            playerState: {
                name: "<none>",
                duration: "0:00",
                remaining: "0:00",
                loading: false,
                hasMusic: false,
                currentTime: "0:00",
                percent: 10,
            },
            queue: [],
            auth: auth,
        },
        computed: {

        },
        methods: {
            Music(name, duration, uploader, locked) {
                return {
                    name: name,
                    duration: duration,
                    uploader: uploader,
                    locked: locked,
                    escapedName: encodeURIComponent(name),
                }
            },

            updateQueueEventHandlers() {
                Vue.nextTick(function() {
                    $(".moveUp").each(function() {
                        var link = $(this);
                        link.off('click').on('click', function(e) {
                            var name = link.attr("data-name");
                            var index = link.attr("data-index");
                            auth.requestAuth(playerControl.sendMoveUpRequestSupplier(name, index));
                        });
                    });

                    $(".moveDown").each(function() {
                        var link = $(this);
                        link.off('click').on('click', function(e) {
                            var name = link.attr("data-name");
                            var index = link.attr("data-index");
                            auth.requestAuth(playerControl.sendMoveDownRequestSupplier(name, index));
                        });
                    });

                    $(".moveToStart").each(function() {
                        var link = $(this);
                        link.off('click').on('click', function(e) {
                            var name = link.attr("data-name");
                            var index = link.attr("data-index");
                            auth.requestAuth(playerControl.sendMoveToStartRequestSupplier(name, index));
                        });
                    });

                    $(".moveToEnd").each(function() {
                        var link = $(this);
                        link.off('click').on('click', function(e) {
                            var name = link.attr("data-name");
                            var index = link.attr("data-index");
                            auth.requestAuth(playerControl.sendMoveToEndRequestSupplier(name, index));
                        });
                    });

                    $(".lockMusic").each(function() {
                        var link = $(this);
                        link.off('click').on('click', function(e) {
                            var name = link.attr("data-name");
                            var index = link.attr("data-index");
                            var locked = link.attr("data-locked");
                            if(locked) {
                                auth.requestAuth(playerControl.sendUnlockRequestSupplier(name, index));
                            } else {
                                auth.requestAuth(playerControl.sendLockRequestSupplier(name, index));
                            }
                        });
                    });

                    $(".queueRemoval").each(function() {
                        var link = $(this);
                        link.off('click').on('click', function(e) {
                            var name = link.attr("data-name");
                            var index = link.attr("data-index");
                            auth.requestAuth(playerControl.sendRemoveRequestSupplier(name, index));
                        });
                    });

                    $('#empty').off('click').on('click', function() {
                        auth.requestAuth(playerControl.sendEmptyRequest);
                    });
                });
            },
        }
    });

    return app;
});