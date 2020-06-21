<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div class="meta-text">
        <h1 class="title" :class="{ overflow: title.length > 30 }">
            <a @click="$router.back()"><img class="back-icon" src="../../assets/back.svg" /></a>
            {{ title | capitalize }}
        </h1>
        <a class="back" @click="$router.back()" v-html="$t('back')" />

        <div class="text-content">
            <transition name="fade" mode="out-in">
                <link-loading v-if="!content" :key="0" />
                <p class="text" v-if="content" v-html="content" :key="2" />
            </transition>
        </div>
    </div>
</template>

<script>
    import LinkLoading from '../components/Loading';

    export default {
        name: 'link-meta-text',
        components: { LinkLoading },

        mounted() {
            this.$store.dispatch(this.isPrivacyPolicy ? 'fetchPrivacyPolicy' : 'fetchTermsOfService');
        },
        data() {
            return {
                title: this.$t(`settings.${this.$route.name === 'privacy' ? 'policy' : 'terms'}`)
            };
        },
        filters: {
            capitalize(str) {
                if (!str) {
                    return str;
                }

                this.$route.name

                return str[0].toUpperCase() + str.substring(1);
            }
        },
        computed: {
            content() {
                const prop = this.isPrivacyPolicy ? 'privacyPolicy' : 'termsOfService';
                return this.$store.state.texts[prop];
            }
        }
    }
</script>

<style lang="scss" scoped>
    .meta-text {
        overflow: auto;

        display: flex;
        flex-direction: column;
        align-items: center;

        padding: 15px 20px;
        padding-bottom: 50px;

        box-sizing: border-box;
    }

    .title {
        margin-bottom: 10px;

        font-size: 30px;
        text-align: center;

        .back-icon {
            margin-right: 10px;
            margin-bottom: -3px;

            width: 28px;
            height: 28px;
        }

        &.overflow .back-icon {
            margin-left: -25px;
        }
    }

    .back {
        margin-bottom: 15px;
    }

    .text-content {
        flex-grow: 1;

        display: flex;
        justify-content: center;
        align-items: center;

        .text {
            text-align: center;
        }
    }
</style>