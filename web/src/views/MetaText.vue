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

        <transition name="fade" mode="out-in">
            <link-loading v-if="!content" :key="0" />
            <div class="text-content" v-if="content && contentText" :key="2">
                <p class="text" v-html="contentText"/>
            </div>
            <div class="pdf-things" v-if="content && contentPdf" :key="2">
                <p><a :href="contentPdf" v-html="$t('meta.downloadPdf')" target="_blank"></a></p>
                <iframe :src="contentPdf"></iframe>
            </div>
        </transition>
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

                return str[0].toUpperCase() + str.substring(1);
            }
        },
        computed: {
            isPrivacyPolicy() {
                return this.$route.name === 'privacy';
            },
            content() {
                const prop = this.isPrivacyPolicy ? 'privacyPolicy' : 'termsOfService';
                return this.$store.state.texts[prop];
            },
            contentText() {
                return this.content.textContent;
            },
            contentPdf() {
                return this.content.pdfContent;
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
        max-width: calc(100% - 20px);

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

    .pdf-things {
        display: flex;
        flex-direction: column;
        min-width: 100%;
        height: 100%;

        p {
            text-align: center;
        }
        a {
            text-decoration: underline;
        }
        iframe {
            width: 100%;
            flex-grow: 1;
        }
    }

    @media screen and (max-width: 500px) {
        .title {
            font-size: 26px;
        }
    }
</style>