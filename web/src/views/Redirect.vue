<template>
    <div id="redirect">
        <link-check id="check" v-if="status" />

        <div id="refusal-container" v-if="!status">
            <div id="cross-left"></div>
            <div id="cross-right"></div>
        </div>

        <p id="text" v-html="$t(`redirect.${status ? 'success' : 'failure'}`)" />
    </div>
</template>

<script>
    import LinkCheck from '../components/Check';

    export default {
        name: 'link-redirect',
        components: { LinkCheck },

        mounted() {
            if (!window.opener) {
                this.$router.push({ name: 'home' });
                return;
            }

            const service = this.$route.params.service;
            const query = window.location.search;

            console.log(`Redirected from service ${service}`);

            if (!query || !query.startsWith('?code=')) {
                this.status = false;
                setTimeout(() => window.close(), 1250);

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

    #check, #text {
        animation: fade 0.75s 0.25s ease 1 both;
    }

    #refusal-container {
        display: inline-flex;
        box-sizing: border-box;

        width: 200px;
        height: 200px;
        padding: 32px 35px 38px;

        border-radius: 50%;

        background-color: #db1e0c;

        #cross-left, #cross-right {
            width: 50px;
            height: 135px;

            border-radius: 4px;

            animation: fade 0.3s ease 1 both;
        }

        #cross-left {
            transform: rotate(45deg) translateY(-22px);
            border-right: solid 15px white;

            animation-delay: 0.75s;
        }

        #cross-right {
            transform: rotate(-45deg) translateY(-22px);
            border-left: solid 15px white;

            animation-delay: 0.4s;
        }
    }

    #text {
        @include lato(500);
        font-size: 34px;

        margin-top: 70px;
        margin-bottom: 0;

        height: 50px;
    }
</style>