<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <link-expanded-view id="profile">
        <div id="left">
            <link-user />

            <h2 id="accesses-title" v-html="$t('profile.identityAccesses')" />
            <div id="accesses-container">
                <transition name="fade" mode="out-in">
                    <div id="accesses" v-if="accesses && accesses.length" :key="0">
                        <div class="access" v-for="(access, i) of accesses" :class="{ first: !i }" :key="i">
                            <div>
                                <span class="author">{{ access.author || $t('profile.admin') }}</span>
                                -
                                <span class="type" v-html="$t(`profile.${access.automated ? 'automated' : 'manual'}Access`)" />
                            </div>
                            <span class="date">- {{ formatDate(access) }}</span>
                            <p class="reason">{{ access.reason }}</p>
                        </div>
                    </div>
                    <div class="center-container" v-else-if="accesses && !accesses.length" :key="1" v-html="$t('profile.noAccess')" />
                    <div class="center-container" v-else :key="2">
                        <link-loading />
                    </div>
                </transition>
            </div>
        </div>
        <div id="right">
            <transition name="fade" mode="out-in">
                <div id="form" v-if="!submitting && !error" :key="0">
                    <link-option v-model="saveEmail">
                        <p class="title" v-html="$t('settings.remember')" />
                        <div class="id-prompt" v-html="meta.idPrompt" />
                        <p class="notice">Note : {{ $t('profile.notice' + (wasChecked ? 'Uncheck' : 'Check'), { provider: meta.providerName }) }}</p>
                    </link-option>

                    <link-button id="submit" :enabled="saveEmail !== wasChecked" @action="submit">
                        {{ saveEmail && !wasChecked ? $t('idProvider.connect', { provider: meta.providerName }) : $t('profile.save') }}
                    </link-button>
                </div>

                <link-loading v-else-if="submitting && !error" :key="1" />

                <link-error v-else :error="error" :message="accesses ? 'back' : 'error.retry'" @action="retry" :key="2" />
            </transition>
        </div>
    </link-expanded-view>
</template>

<script>
    import { mapState } from 'vuex';

    import { openPopup } from '../api';

    import LinkExpandedView from '../components/ExpandedView.vue';
    import LinkButton       from '../components/Button.vue';
    import LinkError        from '../components/Error.vue';
    import LinkLoading      from '../components/Loading.vue';
    import LinkOption       from '../components/Option.vue';
    import LinkUser         from '../components/User.vue';

    export default {
        name: 'link-profile',
        components: { LinkExpandedView, LinkError, LinkLoading, LinkButton, LinkOption, LinkUser },

        mounted() {
            this.saveEmail = this.wasChecked = this.user.identifiable;
            this.loadAccesses();
        },

        data() {
            return {
                wasChecked: false,
                saveEmail: false,

                submitting: false,
                error: null
            }
        },
        computed: mapState({
            user: state => state.auth.user,
            meta: state => state.meta,
            accesses: state => state.accesses.accesses
        }),
        methods: {
            loadAccesses() {
                this.$store.dispatch('fetchAccesses')
                    .catch(err => this.error = err);
            },
            formatDate({ timestamp }) {
                return new Date(timestamp).toLocaleDateString(navigator.language, {
                    day: 'numeric',
                    month: 'long',
                    year: 'numeric',
                    hour: 'numeric',
                    minute: 'numeric'
                });
            },
            submit() {
                if (this.wasChecked && !this.saveEmail) {
                    this.submitting = true;
                    this.$store.dispatch('removeIdentity')
                        .then(() => {
                            this.submitting = false;
                            this.saveEmail = false;
                            this.wasChecked = false;

                            this.loadAccesses();
                        })
                        .catch(err => this.error = err);
                } else if (!this.wasChecked && this.saveEmail) {
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
            },
            retry() {
                this.error = null;

                if (this.accesses) {
                    this.submitting = false;
                } else {
                    this.loadAccesses();
                }
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/vars';

    #left, #right {
        flex: 0.5;

        display: flex;
        flex-direction: column;

        padding: 30px;
        box-sizing: border-box;
    }

    .center-container {
        flex: 1;

        display: flex;
        justify-content: center;
        align-items: center;
    }

    #accesses-title {
        margin: 0;
        margin-top: 20px;

        text-align: center;
        font-size: 20px;
    }

    #accesses-container {
        display: flex;
        flex: 1;

        margin: 10px;
        margin-top: 15px;

        border-radius: 5px;

        background-color: #E5EAEC;

        overflow-y: auto;

        #accesses {
            display: flex;
            flex-direction: column;

            min-width: 250px;

            .access {
                padding: 10px;

                &:not(.first) {
                    border-top: solid 1px #222222;
                }

                .author {
                    font-weight: bold;
                    font-size: 18px;
                }

                .type, .date {
                    font-style: italic;
                }

                .date {
                    margin-left: 15px;
                }

                .reason {
                    margin: 0;
                    margin-top: 10px;
                }
            }
        }
    }

    #right {
        justify-content: center;
        align-items: center;

        #form {
            display: flex;
            flex-direction: column;
            justify-content: space-evenly;

            flex: 1;
        }
    }

    @media screen and (max-width: $expanded-breakpoint) {
        #left {
            min-height: 400px;

            padding-bottom: 0;
        }

        #right {
            padding: 20px;

            display: block;
        }

        #submit {
            margin-top: 25px;
            margin-bottom: 20px;
        }
    }
</style>
