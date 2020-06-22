<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
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
                <img id="menu" src="../assets/menu.svg" @click="sidebar = !sidebar" />
                <router-link id="home-button" to="/">
                    <img id="logo" src="../assets/logo.svg" />
                    <span id="title">EpiLink</span>
                </router-link>
                <template v-if="instance">
                    <div id="instance-separator"></div>
                    <img id="logo-instance" v-if="instanceLogo" :src="instanceLogo">
                    <span id="instance">{{ instance }}</span>
                </template>
                <template v-if="canLogout">
                    <div id="logout-separator"></div>
                    <a id="logout" @click="logout">{{ canLogout === 'link' ? $t('layout.cancel') : $t('layout.logout') }}</a>
                </template>
            </div>
            <ul id="navigation">
                <link-route class="navigation-item" v-for="r of routes" :r="r" />
            </ul>
        </div>

        <div id="sidebar-shadow" :class="{ opened: sidebar }" @click="sidebar = false" />

        <div id="sidebar" :class="{ opened: sidebar }">
            <div id="header">
                <img id="side-logo" v-if="instanceLogo" :src="instanceLogo" />
                {{ instance || 'EpiLink' }}
            </div>

            <ul id="side-navigation">
                <link-route class="navigation-item" v-for="r of routes" :r="r" />
            </ul>
        </div>
    </div>
</template>

<script>
    import { mapState } from 'vuex';

    import LinkError   from './components/Error';
    import LinkLoading from './components/Loading';
    import LinkRoute from "./components/Route";

    export default {
        name: 'link-app',
        components: { LinkRoute, LinkError, LinkLoading },

        mounted() {
            this.load();
        },
        data() {
            return {
                error: null,
                sidebar: false
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
                const instance = meta && [{ route: 'instance' }];

                return [
                    { route: 'home' },

                    ...(urls || []),
                    ...(instance || []),
                    { route: 'about' }
                ];
            },
            instance() {
                const meta = this.$store.state.meta;
                return meta && meta.title;
            },
            instanceLogo() {
                const meta = this.$store.state.meta;
                return meta && meta.logo;
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
        },
        watch: {
            '$route.name'() {
                this.sidebar = false;
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
                width: calc(100vw - 50px);
                max-width: $content-width;
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
            #menu {
                display: none;
                cursor: pointer;
            }

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

            #logo-instance {
                width: 27px;
                height: 27px;

                margin: 9px;
                margin-left: 3px;

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

            .route {
                margin-right: 20px;
            }
        }
    }

    #sidebar, #sidebar-shadow {
        position: absolute;
        top: 0;
        left: 0;

        height: calc(100vh - #{$footer-height});
    }

    #sidebar-shadow {
        width: 100vw;
        transition: background-color .4s;

        &.opened {
            background-color: rgba(0, 0, 0, .6);
        }

        &:not(.opened) {
            pointer-events: none;
        }
    }

    #sidebar {
        width: $sidebar-width;

        background-color: white;
        color: black;

        box-sizing: border-box;
        padding-left: 20px;

        box-shadow: inset rgba(17, 17, 17, 0.35) 10px -3px 9px 3px, rgba(17, 17, 17, 0.45) 0px -10px 7px 3px;

        transform: translateX(-$sidebar-width);
        transition: transform .4s;

        &.opened {
            transform: translateX(-20px);
        }

        #header {
            display: flex;
            justify-content: center;
            align-items: center;

            margin-top: 25px;

            font-size: 24px;
            font-weight: 600;

            #side-logo {
                width: 50px;
                margin-right: 15px;

                border-radius: 4px;
            }
        }

        #side-navigation {
            @include lato(600);
            list-style: none;

            margin-top: 35px;
            padding-left: 25px;

            .route {
                margin-top: 12px;
                font-size: 18px;
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

    @media screen and (max-width: 1000px) {
        #footer {
            #navigation, #instance-separator, #instance {
                display: none;
            }

            #left-footer {
                #menu {
                    display: inline-block;
                    margin-left: 7px;
                }

                #logo {
                    margin-left: 9px;
                }
            }
        }
    }

    @media screen and (max-width: 450px) {
        #main-view #content {
            &, #loading, #content-wrapper > div {
                height: calc(100vh - #{$footer-height} - 30px);
                max-height: 400px;
                width: calc(100vw - 35px);
            }
        }
    }
</style>