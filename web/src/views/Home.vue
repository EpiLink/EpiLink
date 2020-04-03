<template>
    <div id="home">
        <img id="logo" src="../../assets/logo.svg"/>
        <h1 id="title">Bienvenue</h1>

        <button id="discord" @click="login">
            <img id="discord-logo" src="../../assets/discord.svg"/>
            <span id="discord-text">Se connecter via Discord</span>
        </button>
    </div>
</template>

<script>
    import { getRedirectURI } from '../api';

    export default {
        name: 'link-home',

        mounted() {
            // TODO: Is user logged for REAL

            if (this.$store.state.user && !this.$store.state.email) {
                this.$router.push({ name: 'microsoft' });
            }
        },
        methods: {
            login() {
                const width = 650, height = 750;
                const x = screen.width / 2 - width / 2, y = screen.height / 2 - height / 2 - 65;

                const url = `${this.$store.state.meta.authorizeStub_discord}&redirect_uri=${getRedirectURI('discord')}`;
                const options = `menubar=no, status=no, scrollbars=no, menubar=no, width=${width}, height=${height}, top=${y}, left=${x}`;

                this.$router.push({
                    name: 'auth',
                    params: { service: 'discord' }
                });

                setTimeout(() => {
                    this.$store.commit('openPopup', window.open(url,'EpiLink - Discord', options));
                }, 300);
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/fonts';

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
        $color: #7289DA;

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
            height: 30px;
            margin-bottom: -2px;
        }

        #discord-text {
            margin-top: -1px;
        }

        &:hover {
            background-color: lighten($color, 2.5%);
            cursor: pointer;
        }
    }
</style>