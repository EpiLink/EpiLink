<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div class="error">
        <h1 class="title" v-html="$t('error.title')" />
        <p class="message">{{ actualError }}</p>

        <a class="action" @click="action" v-html="$t(message)" />
    </div>
</template>

<script>
    import { mapState } from 'vuex';

    export default {
        name: 'link-error',
        props: ['error', 'message'],

        methods: {
            action() {
                this.$emit('action');
            }
        },
        computed: {
            ...mapState({ meta: state => state.meta }),
            providerName() {
                return (this.meta && this.meta.providerName) || 'Identity Provider';
            },
            actualError() {
                if (this.error.key !== undefined && this.error.replace !== undefined) {
                    // Back-end I18n string
                    return this.$t(
                        this.error.key,
                        {
                            ...this.error.replace,
                            provider: this.providerName
                        }
                    );
                }
                const error = this.error.toString();

                if (error.toLowerCase().includes('network') || error.toLowerCase().includes('failed to fetch')) {
                    return this.$t('error.network');
                }

                if (error === 'rate-limit') {
                    return this.$t('error.rateLimit');
                }

                return error;
            }
        }
    };
</script>

<style lang="scss" scoped>
    @import '../styles/vars';

    .error {
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;

        padding: 0 25px;
        box-sizing: border-box;

        height: 100%;
    }

    .title {
        margin-top: 0;

        font-size: 38px;
    }

    .message {
        text-align: center;

        color: #C01616;
    }

    .action {
        margin-top: 25px;

        font-size: 22px;
    }
</style>
