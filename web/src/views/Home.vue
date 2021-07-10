<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div id="home">
        <img id="logo" alt="Logo" :src="logo"/>
        <h1 id="title" v-html="$t('home.welcome')" />

        <button id="discord" @click="login">
            <img id="discord-logo" :alt="$t('home.discord')" src="../../assets/discord.svg"/>
            <span id="discord-text" v-html="$t('home.discord')" />
        </button>
    </div>
</template>

<script>
    import { openPopup } from '../api';

    export default {
        name: 'link-home',

        mounted() {
            const user = this.$store.state.auth.user;
            if (user) {
                this.$router.push({ name: user.temp ? (user.email ? 'settings' : 'idProvider') : 'profile' });
            }
        },
        computed: {
            logo() {
                return this.$store.state.meta.logo || require("../../assets/logo.svg")
            }
        },
        methods: {
            login() {
                const name = this.$t('popups.discord');

                this.$router.push({
                    name: 'auth',
                    params: { service: 'discord' }
                });

                setTimeout(() => {
                    const popup = openPopup(name, 'discord', this.$store.state.meta.authorizeStub_discord);
                    this.$store.commit('openPopup', popup);
                }, 300);
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/vars';
    @import '../styles/mixins';

    #home {
        display: flex;
        flex-direction: column;
        justify-content: space-evenly;
        align-items: center;

        padding: 50px;
    }

    #logo {
        width: 165px;
        height: 165px;

        border-radius: 7px;
    }

    #title {
        @include lato(600);
        font-size: 66px;
    }

    #discord {
        $color: #5865F2;

        color: white;
        background-color: $color;

        border: none;
        border-radius: 3px;

        width: 320px;
        height: 50px;

        @include lato(500);
        font-size: 22px;

        padding-left: 10px;
        padding-right: 10px;

        box-shadow: rgba(37, 37, 37, 0.25) 0 3px 6px 3px;

        display: flex;
        justify-content: space-evenly;
        align-items: center;

        transition: background-color 150ms;

        #discord-logo {
            height: 24px;
        }

        #discord-text {
            margin-top: -1px;
        }

        &:hover {
            background-color: lighten($color, 2.5%);
            cursor: pointer;
        }
    }

    @media screen and (max-width: $height-wrap-breakpoint) {
        #home {
            padding: 30px;
        }

        #logo {
            width: 145px;
            height: 145px;
        }

        #title {
            font-size: 54px;
        }

        #discord {
            width: 280px;
            height: 50px;

            font-size: 19px;
        }
    }

    @media screen and (max-width: 375px) {
        #logo {
            width: 135px;
            height: 135px;
        }

        #title {
            font-size: 48px;
        }

        #discord {
            width: 245px;
            height: 40px;

            font-size: 17px;

            #discord-logo {
                height: 25px;
            }
        }
    }
</style>
