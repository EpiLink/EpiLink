<template>
    <div id="app">
        <div id="main-view">
            <div id="content" :class="{ 'expanded': expanded }">
                <div v-if="redirected || loadedWithAnimation" id="content-wrapper" :class="{ 'seen': contentSeen || redirected }">
                    <transition name="fade">
                        <router-view></router-view>
                    </transition>
                </div>

                <div id="loading" v-if="!redirected" :class="{ 'seen': !loaded }">
                    <link-loading />
                </div>
            </div>
        </div>

        <div id="footer" v-if="!redirected">
            <div id="left-footer">
                <router-link id="home-button" to="/">
                    <img id="logo" src="../assets/logo.svg" />
                    <span id="title">EpiLink</span>
                </router-link>
                <template v-if="canLogout">
                    <div id="separator"></div>
                    <a id="logout" @click="logout">{{ canLogout === 'link' ? 'Annuler la procédure' : 'Se déconnecter' }}</a>
                </template>
            </div>
            <ul id="navigation">
                <li class="navigation-item" v-for="route of routes">
                    <router-link :to="route.path" v-html="route.title" />
                </li>
            </ul>
        </div>
    </div>
</template>

<script>
    import { mapState } from 'vuex';
    import LinkLoading  from './components/Loading';

    const ROUTES = [
        { title: 'Instance', path: '/instance' },
        { title: 'Confidentialité', path: '/privacy' },
        { title: 'Sources', path: 'https://github.com/Litarvan/EpiLink' }, // TODO: Dynamic
        { title: 'À Propos', path: '/about' }
    ];

    export default {
        name: 'link-app',
        components: { LinkLoading },

        mounted() {
            if (!this.redirected) {
                this.$store.dispatch('load').then(() => {
                    setTimeout(() => this.loadedWithAnimation = true, 200);
                    setTimeout(() => this.contentSeen = true, 250);
                });
            }
        },
        data() {
            return {
                routes: ROUTES,
                redirected: this.$route.name === 'redirect',
                loadedWithAnimation: false,
                contentSeen: false
            };
        },
        computed: {
            ...mapState(['expanded']),
            loaded() {
                return !!this.$store.state.meta;
            },
            canLogout() {
                const user = this.$store.state.user;
                return user && (user.temp ? 'link' : 'real');
            }
        },
        methods: {
            logout() {
                this.$store.commit('logout');
                this.$router.push({ name: 'home' });
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

            animation: content-fade 0.25s 0.3s ease 1 both;

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

            #content-wrapper {
                opacity: 0;
                transition: opacity 0.2s;

                &.seen {
                    opacity: 1;
                }
            }

            #loading {
                display: flex;
                align-items: center;
                justify-content: center;

                opacity: 0;
                transition: opacity 0.2s;

                &.seen {
                    opacity: 1;
                }

                .loading {
                    margin-bottom: 60px;
                }
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

            #separator {
                width: 10px;
                height: 1px;

                background-color: #313338;

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
                    cursor: pointer;
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

    .fade-enter-active, .fade-leave-active {
        transition: opacity .2s;
    }

    .fade-enter-active {
        transition-delay: .2s;
    }

    .fade-enter, .fade-leave-active {
        opacity: 0;
    }

    @keyframes content-fade {
        0% {
            opacity: 0;
        }

        100% {
            opacity: 1;
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