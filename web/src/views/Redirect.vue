<template>
    <!-- TODO: Gérer l'erreur -->

    <div id="redirect">
        <div id="check-container">
            <div id="check">
            </div>
        </div>

        <p id="text" v-html="$t('redirect.success')" />
    </div>
</template>

<script>
    export default {
        name: 'link-redirect',

        mounted() {
            const service = this.$route.params.service;
            const query = window.location.search;

            console.log(`Redirected from service ${service}`);

            if (!window.opener || !query || query.length <= 6) {
                this.status = false;
                return;
            }

            let code = query.substring(6);
            const max = code.indexOf('&');
            if (max !== -1) {
                code = code.substring(0, max);
            }

            console.log(`Code : ${code}`);
            this.status = true;

            setTimeout(() => {
                window.opener.postMessage({ code });
                window.close();
            }, 1250);
        },
        data() {
            return {
                status: null
            };
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/fonts';
    @import '../styles/vars';

    #redirect {
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
    }

    #check-container, #text {
        animation: fade 0.75s 0.25s ease 1 both;
    }

    #check-container {
        width: 200px;
        height: 200px;

        border-radius: 50%;

        background-color: lighten(#37C837, 4.5%);

        #check {
            width: 100px;
            height: 50px;

            margin-top: 107.5px;
            margin-left: 37.5px;

            border: solid 15px white;
            border-top: none;
            border-right: none;
            border-radius: 4px;

            transform: rotate(-45deg);
            transform-origin: top left;

            animation: check 0.5s 0.5s ease 1 both;
        }
    }

    #text {
        @include lato(500);
        font-size: 34px;

        margin-top: 70px;
        margin-bottom: 0;

        height: 50px;
    }

    @keyframes check {
        0% {
            opacity: 0;
            width: 0;
            height: 0;
        }

        50% {
            opacity: 1;
            width: 0;
            height: 50px;
        }

        100% {
            width: 100px;
        }
    }

    @keyframes fade {
        0% {
            opacity: 0;
        }

        100% {
            opacity: 1;
        }
    }
</style>