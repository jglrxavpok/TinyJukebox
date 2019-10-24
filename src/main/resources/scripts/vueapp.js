define(["jquery"], function($) {
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
                        <div class="d-flex justify-content-between">
                            <div>00:00:00</div>
                            <div>{{ playerState.duration }}</div>
                        </div>
                        <div class="progress">
                          <div class="progress-bar bg-success" role="progressbar" :style="{ width: playerState.percent + '%' }" :aria-valuenow="playerState.percent" aria-valuemin="0" aria-valuemax="100">
                            {{ playerState.currentTime }}
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

    var app = new Vue({
        el: '#vueapp',
        components: {
            "playing-container": player,
        },
        data: {
            playerState: {
                name: "<none>",
                duration: "0:00",
                loading: false,
                hasMusic: false,
                currentTime: "0:00",
                percent: 10,
            },
        },
        computed: {

        },
        methods: {
        }
    });

    return app;
});