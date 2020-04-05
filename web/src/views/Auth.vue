<template>
    <div id="auth" :class="{ 'seen': contentSeen }">
        <h1 id="title">{{ doneWithAnimation ? 'Chargement' : 'En attente' }}</h1>
        <span id="subtitle">{{ doneWithAnimation ? 'Récupération des informations...' : 'En attente de confirmation dans la fenêtre extérieure' }}</span>

        <link-loading />
    </div>
</template>

<script>
    import { getRedirectURI } from '../api';
    import LinkLoading        from '../components/Loading';

    export default {
        name: 'link-auth',
        components: { LinkLoading },

        mounted() {
            // TODO: Check state
            // TODO: Handle refusal

            window.addEventListener('message', this.onMessage);
            this.closeListener = setInterval(() => {
                const popup = this.$store.state.popup;
                if (popup && popup.closed) {
                    this.onDestroy();

                    setTimeout(() => {
                        if (!this.done) {
                            this.$router.push({ name: this.$route.params.service === 'discord' ? 'home' : 'microsoft' });
                        }
                    }, 150);
                }
            }, 200);
        },
        beforeDestroy() {
            this.onDestroy();
            this.$store.commit('closePopup');
        },
        data() {
            return {
                closeListener: null,
                done: false,
                doneWithAnimation: false,
                contentSeen: true
            }
        },
        methods: {
            onMessage(msg) {
                if (msg.origin !== window.origin || this.done) {
                    return;
                }

                if (msg.data.code) {
                    this.done = true;
                    this.onDestroy();

                    this.contentSeen = false;
                    setTimeout(() => {
                        this.contentSeen = true;
                        this.doneWithAnimation = true;
                    }, 200);

                    const service = this.$route.params.service;
                    console.log(`Received code for service ${service}`);

                    this.$store.dispatch('postCode', {
                        service: service === 'microsoft' ? 'msft' : 'discord',
                        code: msg.data.code,
                        uri: getRedirectURI(service)
                    }).then(() => setTimeout(() => {
                        this.$router.push({ name: service === 'discord' ? 'microsoft' : 'settings' });
                    }, 250));
                }
            },
            onDestroy() {
                window.removeEventListener('message', this.onMessage);
                clearInterval(this.closeListener);
            }
        }
    }
</script>

<style lang="scss" scoped>
    #auth {
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;

        & * { // We do this here to keep the router transition working correctly
            opacity: 0;
            transition: opacity 0.2s;
        }

        &.seen * {
            opacity: 1;
        }
    }

    #title {
        margin: 0;
        padding: 0;

        font-size: 48px;
    }

    #subtitle {
        margin-top: 35px;
        font-size: 17px;
    }
</style>