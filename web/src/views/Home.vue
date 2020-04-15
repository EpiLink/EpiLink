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
    import { openPopup } from '../api';

    export default {
        name: 'link-home',

        mounted() {
            // TODO: Is user logged for REAL

            const user = this.$store.state.user;
            if (user) {
                this.$router.push({ name: user.temp ? (user.email ? 'settings' : 'microsoft') : 'profile' });
            }
        },
        methods: {
            login() {
                this.$router.push({
                    name: 'auth',
                    params: { service: 'discord' }
                });

                setTimeout(() => {
                    const popup = openPopup('Connexion à Discord', 'discord', this.$store.state.meta.authorizeStub_discord);
                    this.$store.commit('openPopup', popup);
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