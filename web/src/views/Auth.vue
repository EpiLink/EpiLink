<template>
    <div id="auth">
        <transition name="fade" mode="out-in">
            <div id="waiting" class="auth-dialog" v-if="!fetching" :key="0">
                <h1 class="title" v-html="$t('auth.waiting.title')" />
                <span class="subtitle" v-html="$t('auth.waiting.description')" />

                <link-loading />
            </div>

            <div id="fetching" class="auth-dialog" v-if="fetching" :key="1">
                <h1 class="title" v-html="$t('auth.fetching.title')" />
                <span class="subtitle" v-html="$t('auth.fetching.description')" />

                <link-loading />
            </div>
        </transition>
    </div>
</template>

<script>
    import { getRedirectURI } from '../api';
    import LinkLoading        from '../components/Loading';

    export default {
        name: 'link-auth',
        components: { LinkLoading },

        mounted() {
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
                fetching: false
            }
        },
        methods: {
            onMessage(msg) {
                if (msg.origin !== window.origin || this.done) {
                    return;
                }

                if (msg.data.code) {
                    this.fetching = true;
                    this.onDestroy();

                    const service = this.$route.params.service;
                    console.log(`Received code for service ${service}`);

                    this.$store.dispatch('postCode', {
                        service: service === 'microsoft' ? 'msft' : 'discord',
                        code: msg.data.code,
                        uri: getRedirectURI(service)
                    }).then(() => {
                        let route = 'profile';
                        if (this.$store.state.user.temp) {
                            route = service === 'discord' ? 'microsoft' : 'settings';
                        }

                        setTimeout(() => {
                            this.$router.push({ name: route });
                        }, 250);
                    });
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
    #auth, .auth-dialog {
        display: flex;
        justify-content: center;
    }

    .auth-dialog {
        flex-direction: column;
        align-items: center;
    }

    .title {
        margin: 0;
        padding: 0;

        font-size: 48px;
    }

    .subtitle {
        margin-top: 35px;
        font-size: 17px;
    }

    .loading {
        margin-top: 65px;
    }
</style>