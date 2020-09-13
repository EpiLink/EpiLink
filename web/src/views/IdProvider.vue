<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div id="idProvider" v-if="user">
        <link-user />
        <link-stepper id="stepper" step="2" />

        <button id="login" @click="login()">
            <img v-if="meta.providerIcon" id="icon" :alt="$t('idProvider.connect', {provider: meta.providerName}
)" :src="providerIconUrl" />
            <span id="text" v-html="$t('idProvider.connect', {provider: meta.providerName}
)" />
        </button>
    </div>
</template>

<script>
    import { mapState } from 'vuex';

    import { openPopup } from '../api';

    import LinkStepper from '../components/Stepper';
    import LinkUser    from '../components/User';

    export default {
        name: 'link-idProvider',
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
        computed: {
            ...mapState({ user: state => state.auth.user, meta: state => state.meta }),
            providerIconUrl() {
                //const meta = this.$store.state.meta;
                const logoUrl = this.meta && this.meta.providerIcon;
                if (logoUrl)
                    return logoUrl.startsWith('/api/v1/') ? BACKEND_URL + logoUrl.substring(7) : logoUrl;
                else
                    return false;
            }
        },
        methods: {
            login() {
                if (this.submitting) {
                    return;
                }

                this.submitting = true;

                const name = this.$t('popups.idProvider');

                this.$router.push({
                    name: 'auth',
                    params: { service: 'idProvider' }
                });

                setTimeout(() => {
                    const popup = openPopup(name, 'idProvider', this.$store.state.meta.authorizeStub_idProvider);
                    this.$store.commit('openPopup', popup);
                }, 300);
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/vars';
    @import '../styles/mixins';

    #idProvider {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        // slight padding to avoid having long usernames sticking to the side of the frame
        padding: 8px;
    }

    #stepper {
        margin-top: 30px;
        // Previous versions didn't have padding on #idProvider and used 85% here. This precised % value matches the old
        // size (well, there's a .2 pixel difference, but come on)
        width: 88.5%;
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