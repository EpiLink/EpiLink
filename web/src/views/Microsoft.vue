<template>
    <div id="microsoft" v-if="user">
        <link-user />
        <link-stepper id="stepper" step="2" />

        <button id="login" @click="login()">
            <img id="icon" src="../../assets/ms_icon.svg" />
            <span id="text">Se connecter via Microsoft</span>
        </button>
    </div>
</template>

<script>
    import { mapState } from 'vuex';

    import { openPopup } from '../api';

    import LinkStepper from '../components/Stepper';
    import LinkUser    from '../components/User';

    export default {
        name: 'link-microsoft',
        components: { LinkUser, LinkStepper },

        beforeMount() {
            if (!this.$store.state.user) {
                this.$router.push({ name: 'home' });
            } else if (this.$store.state.user.email) {
                this.$router.push({ name: 'settings' });
            }
        },
        data() {
            return {
                submitting: false
            };
        },
        computed: mapState(['user']),
        methods: {
            login() {
                if (this.submitting) {
                    return;
                }

                this.submitting = true;

                this.$router.push({
                    name: 'auth',
                    params: { service: 'microsoft' }
                });

                setTimeout(() => {
                    const popup = openPopup('Connexion à Microsoft', 'microsoft', this.$store.state.meta.authorizeStub_msft);
                    this.$store.commit('openPopup', popup);
                }, 300);
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/fonts';

    #microsoft {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
    }

    #stepper {
        margin-top: 30px;
        width: 85%;
    }

    #login {
        display: flex;
        align-items: center;

        padding: 10px 20px;
        margin-top: 25px;

        background-color: #000;
        color: #FFF;

        border: none;

        box-shadow: 0 3px 5px rgba(0, 0, 0, 0.3);

        cursor: pointer;

        transition: background-color .175s;

        &:hover {
            background-color: #1a1a1a;
        }

        #icon {
            width: 21px;
            height: 21px;
            margin-right: 12px;
        }

        #text {
            @include lato();
            font-size: 17px;
        }
    }
</style>