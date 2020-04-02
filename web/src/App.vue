<template>
    <div id="app">
        <div id="content">
            <router-view></router-view>
        </div>

        <div id="footer">
            <div id="left-footer">
                <router-link id="home-button" to="/">
                    <img id="logo" src="../assets/logo.svg" />
                    <span id="title">EpiLink</span>
                </router-link>
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
    const ROUTES = [
        { title: 'Instance', path: '/instance' },
        { title: 'Confidentialité', path: '/privacy' },
        { title: 'Sources', path: 'https://github.com/Litarvan/EpiLink' }, // TODO: Dynamic
        { title: 'À Propos', path: '/about' }
    ];

    export default {
        name: 'link-app',

        mounted() {
            this.$store.dispatch('fetchMeta');
        },
        data() {
            return {
                routes: ROUTES
            };
        }
    }
</script>

<style lang="scss">
    @import './styles/app';
    @import './styles/vars';

    #app {
        display: flex;
        justify-content: center;
        align-items: center;

        width: 100vw;
        height: 100vh;
    }

    #content {
        &, & div {
            width: $content-width;
            height: $content-height;

            box-sizing: border-box;

            &.expanded {
                width: 1000px;
            }
        }

        margin-bottom: $footer-height;

        background-color: #FDFDFD;
        color: black;

        box-shadow: rgba(10, 10, 10, 0.65) 0 4px 10px 4px;

        border-radius: 4px;
    }

    #footer {
        position: absolute;
        bottom: 0;
        left: 0;

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

            #version {
                @include lato(500);
                font-style: italic;

                color: #575757;

                margin-left: 7px;
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
</style>