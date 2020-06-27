<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div id="microsoft" v-if="user">
        <link-user />
        <link-stepper id="stepper" step="2" />

        <button id="login" @click="login()">
            <img id="icon" src="../../assets/ms_icon.svg" />
            <span id="text" v-html="$t('microsoft.connect')" />
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
            const user = this.$store.state.auth;
            if (!user) {
                this.$router.push({ name: 'home' });
            } else if (user.email) {
                this.$router.push({ name: 'settings' });
            }
        },
        data() {
            return {
                submitting: false
            };
        },
        computed: mapState({ user: state => state.auth.user }),
        methods: {
            login() {
                if (this.submitting) {
                    return;
                }

                this.submitting = true;

                const name = this.$t('popups.microsoft');

                this.$router.push({
                    name: 'auth',
                    params: { service: 'microsoft' }
                });

                setTimeout(() => {
                    const popup = openPopup(name, 'microsoft', this.$store.state.meta.authorizeStub_msft);
                    this.$store.commit('openPopup', popup);
                }, 300);
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/vars';
    @import 'src/styles/mixins';

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

    @media screen and (max-width: $height-wrap-breakpoint) {
        #stepper {
            margin-top: 15px;
        }

        #login {
            margin-top: 15px;

            padding: 9px 18px;

            #icon {
                width: 19px;
                height: 19px;
            }

            #text {
                font-size: 16px;
            }
        }
    }

    @media screen and (max-height: 455px) {
        #stepper {
            margin-top: 10px;
        }

        #login {
            margin-top: 10px;
        }
    }
</style>