<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <link-expanded-view class="meta-text" :column="true">
        <h1 class="title" :class="{ overflow: title.length > 30 }">
            <a @click="back()"><img class="back-icon" :alt="$t('back')" src="../../assets/back.svg" /></a>
            {{ title }}
        </h1>
        <a class="back" @click="back()" v-html="$t('back')" />

        <transition name="fade" mode="out-in">
            <link-loading v-if="!content" :key="0" />

            <div class="text-content" v-else-if="contentText" :key="1">
                <p class="text" v-html="contentText"/>
            </div>
            <div class="pdf-things" v-else-if="contentPdf" :key="2">
                <p><a :href="contentUrl" v-html="$t('meta.downloadPdf')" rel="noreferrer" target="_blank"></a></p>
                <iframe class="pdf-frame" :src="contentPdf"></iframe>
            </div>
        </transition>
    </link-expanded-view>
</template>

<script>
    import LinkExpandedView from '../components/ExpandedView.vue';
    import LinkLoading      from '../components/Loading.vue';

    export default {
        name: 'link-meta-text',
        components: { LinkExpandedView, LinkLoading },

        mounted() {
            this.$store.dispatch(this.isPrivacyPolicy ? 'fetchPrivacyPolicy' : 'fetchTermsOfService');
        },
        data() {
            const title = this.$t(`settings.${this.$route.name === 'privacy' ? 'policy' : 'terms'}`);
            return {
                title: title[0].toUpperCase() + title.substring(1)
            };
        },
        methods: {
            back() {
                if (!history) {
                    this.$router.back();
                } else if (history.length > 2) {
                    history.back();
                } else {
                    this.$router.push({ name: 'home' });
                }
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
            contentUrl() {
                return this.content.url;
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
        padding: 15px 20px;
        padding-bottom: 25px;

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

    .loading {
        margin-top: 50px;
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
            margin-top: 0;
        }

        a {
            text-decoration: underline;
        }

        .pdf-frame {
            width: 100%;
            flex-grow: 1;
            border: none;
        }
    }

    @media screen and (max-width: 500px) {
        .title {
            font-size: 26px;
        }
    }
</style>
