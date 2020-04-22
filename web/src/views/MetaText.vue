<template>
    <div class="meta-text">
        <h1 class="title" v-html="capitalize($t(`settings.${isPrivacyPolicy ? 'policy' : 'terms'}`))" />
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
                isPrivacyPolicy: this.$route.name === 'privacy'
            };
        },
        methods: {
            capitalize(str) {
                if (!str) {
                    return str;
                }

                return str[0].toUpperCase() + str.substring(1);
            }
        },
        computed: {
            content() {
                const prop = this.isPrivacyPolicy ? 'privacyPolicy' : 'termsOfService';
                return this.$store.state[prop];
            }
        }
    }
</script>

<style lang="scss">
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
    }

    .back {
        cursor: pointer;

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