<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <link-expanded-view id="settings">
        <div id="left">
            <link-user/>
            <link-stepper step="3"/>
        </div>

        <div id="right">
            <transition name="fade" mode="out-in">
                <div id="settings-form" v-if="!submitting" :key="0">
                    <div id="options">
                        <link-option v-model="saveEmail">
                            <p class="title" v-html="$t('settings.remember')"/>
                            <div class="id-prompt" v-html="idPrompt"/>
                        </link-option>
                        <link-option v-model="acceptConditions">
                            <p class="title">
                                {{ $t('settings.iAcceptThe') }}
                                <router-link :to="{ name: 'tos' }" v-html="$t('settings.terms')"/>

                                {{ $t('settings.andThe') }}
                                <router-link :to="{ name: 'privacy' }" v-html="$t('settings.policy')"/>
                            </p>
                        </link-option>
                    </div>

                    <link-button id="submit" :enabled="acceptConditions" @action="submit">{{ $t('settings.link') }}
                    </link-button>
                </div>

                <div id="submitting" v-else-if="submitting && !error" :key="1">
                    <link-loading/>
                </div>

                <link-error v-else :error="error" message="error.retry" @action="retry" :key="2"/>
            </transition>
        </div>
    </link-expanded-view>
</template>

<script>
    import { mapState } from 'vuex';

    import LinkExpandedView from '../components/ExpandedView.vue';
    import LinkButton       from '../components/Button.vue';
    import LinkCheckbox     from '../components/Checkbox.vue';
    import LinkError        from '../components/Error.vue';
    import LinkLoading      from '../components/Loading.vue';
    import LinkOption       from '../components/Option.vue';
    import LinkStepper      from '../components/Stepper.vue';
    import LinkUser         from '../components/User.vue';

    export default {
        name: 'link-settings',
        components: { LinkExpandedView, LinkOption, LinkButton, LinkError, LinkLoading, LinkCheckbox, LinkUser, LinkStepper },

        data() {
            return {
                saveEmail: false,
                acceptConditions: false,
                submitting: false,
                error: null
            }
        },
        methods: {
            submit() {
                if (!this.acceptConditions || this.submitting) {
                    return;
                }

                this.submitting = true;

                this.$store.dispatch('register', this.saveEmail)
                    .then(() => this.$router.push({name: 'success'}))
                    .catch(err => this.error = err);
            },
            retry() {
                this.submitting = false;
                this.error = false;
            }
        },
        computed: mapState({ idPrompt: state => state.meta && state.meta.idPrompt })
    }
</script>

<style lang="scss" scoped>
    @import '../styles/vars';
    @import '../styles/mixins';

    #left, #right {
        flex: 0.5;

        padding: 5px 35px;
    }

    #right {
        display: flex;
    }

    #left, #settings-form {
        display: flex;
        flex-direction: column;
        justify-content: space-evenly;
    }

    #settings-form, #submitting {
        flex: 1;
    }

    #options {
        display: flex;
        flex-direction: column;
    }

    #submitting {
        display: flex;
        justify-content: center;
        align-items: center;
    }

    @media screen and (max-width: $expanded-breakpoint) {
        #settings-wrapper {
            flex-direction: column;
            overflow-y: auto;
        }

        #left, #right, #settings-form {
            display: block;
        }

        .user {
            margin-top: 17px;
        }

        .stepper {
            margin-top: 23px;
        }

        .option {
            margin-top: 15px;
        }

        #submit {
            margin-top: 40px;
            margin-bottom: 30px;
        }
    }

    @media screen and (max-width: $stepper-breakpoint) {
        #left, #right {
            padding: 5px 25px;
        }
    }

    @media screen and (max-width: 375px) {
        #left, #right {
            padding: 5px 17px;
        }
    }
</style>
