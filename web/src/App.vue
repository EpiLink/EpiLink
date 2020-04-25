<template>
    <div id="app">
        <div id="main-view">
            <div id="content" :class="{ 'expanded': expanded }">
                <transition name="fade" mode="out-in">
                    <div v-if="redirected || (loaded && !error)" id="content-wrapper" :key="0">
                        <transition name="fade">
                            <router-view :key="$route.path"></router-view>
                        </transition>
                    </div>

                    <div id="loading" v-if="!redirected && !loaded && !error" :key="1">
                        <link-loading />
                    </div>

                    <link-error v-if="error" :error="error" message="error.retry" @action="retry" :key="2" />
                </transition>
            </div>
        </div>

        <div id="footer" v-if="!redirected">
            <div id="left-footer">
                <router-link id="home-button" to="/">
                    <img id="logo" src="../assets/logo.svg" />
                    <span id="title">EpiLink</span>
                </router-link>
                <template v-if="instance">
                    <div id="instance-separator"></div>
                    <span id="instance">{{ instance}}</span>
                </template>
                <template v-if="canLogout">
                    <div id="logout-separator"></div>
                    <a id="logout" @click="logout">{{ canLogout === 'link' ? $t('layout.cancel') : $t('layout.logout') }}</a>
                </template>
            </div>
            <ul id="navigation">
                <li class="navigation-item" v-for="r of routes">
                    <router-link v-if="r.route" :to="{ name: r.name }" v-html="$t(`layout.navigation.${r.name}`)" />
                    <a v-if="r.url" :href="r.url" target="_blank">{{ r.name }}</a>
                </li>
            </ul>
        </div>
    </div>
</template>

<script>
    import { mapState } from 'vuex';

    import LinkError   from './components/Error';
    import LinkLoading from './components/Loading';

    export default {
        name: 'link-app',
        components: { LinkError, LinkLoading },

        mounted() {
            this.load();
        },
        data() {
            return {
                error: null
            };
        },
        computed: {
            ...mapState(['expanded']),

            loaded() {
                return !!this.$store.state.meta;
            },
            canLogout() {
                const user = this.$store.state.auth.user;
                return user && (user.temp ? 'link' : 'real');
            },
            redirected() {
                return this.$route.name === 'redirect';
            },
            routes() {
                const meta = this.$store.state.meta;
                const urls = meta && meta.footerUrls;

                return [
                    ...(urls || []),

                    { name: 'tos', route: 'tos' },
                    { name: 'privacy', route: 'privacy' },
                    { name: 'about', route: 'about' }
                ];
            },
            instance() {
                const meta = this.$store.state.meta;
                return meta && meta.title;
            }
        },
        methods: {
            load() {
                if (!this.redirected) {
                    this.$store.dispatch('load').catch(err => this.error = err);
                }
            },
            logout() {
                this.$store.dispatch('logout').then(() => {
                    this.$router.push({ name: 'home' });
                });
            },
            retry() {
                this.error = null;
                this.load();
            }
        }
    }
</script>

<style lang="scss">
    @import './styles/app';
    @import './styles/vars';

    #app {
        display: flex;
        flex-direction: column;
        align-items: center;

        width: 100vw;
        height: 100vh;
    }

    #main-view {
        flex: 1;

        display: flex;
        align-items: center;

        #content {
            background-color: #FDFDFD;
            color: black;

            box-shadow: rgba(10, 10, 10, 0.65) 0 4px 10px 4px;

            border-radius: 4px;

            animation: fade 0.25s 0.3s ease 1 both;

            &, #loading, #content-wrapper > div {
                width: $content-width;
                height: $content-height;

                box-sizing: border-box;
            }

            &, #content-wrapper > div:not(.fade-enter-active):not(.fade-leave-active) {
                transition: width 0.5s;
            }

            &.expanded, &.expanded > #content-wrapper > div {
                width: 1000px;
            }

            #loading {
                display: flex;
                align-items: center;
                justify-content: center;
            }
        }
    }

    #footer {
        width: 100vw;
        height: $footer-height;

        background-color: white;
        color: black;

        box-shadow: rgba(17, 17, 17, 0.35) 0 -3px 9px 3px;

        &, #left-footer, #navigation {
            display: flex;
            align-items: center;
        }

        justify-content: space-between;

        animation: footer-pop 0.25s 0.3s ease 1 both;

        #left-footer {
            #home-button {
                display: contents;
            }

            #logo {
                width: 27px;
                height: 27px;

                margin: 9px;
                margin-left: 12px;

                border-radius: 3px;
            }

            #title, #version {
                font-size: 23px;
                margin-top: -1px;
            }

            #title {
                @include lato(bold);
            }

            #instance-separator, #logout-separator {
                background-color: #313338;
            }

            #instance-separator {
                width: 1px;
                height: $footer-height / 2;

                margin-left: 8px;
                margin-right: 9px;
            }

            #instance {
                font-size: 18px;
            }

            #logout-separator {
                width: 10px;
                height: 1px;

                margin-left: 10px;
                margin-right: 10px;
                margin-top: 1px;
            }

            #logout {
                @include lato(500);
                font-style: italic;
                font-size: 21px;

                color: #C01616;

                &:hover {
                    text-decoration: underline;
                }
            }
        }

        #navigation {
            @include lato(600);
            list-style: none;

            .navigation-item {
                margin-right: 20px;
            }
        }
    }

    @keyframes footer-pop {
        0% {
            transform: translateY(#{$footer-height});
        }

        100% {
            transform: translateY(0);
        }
    }
</style>