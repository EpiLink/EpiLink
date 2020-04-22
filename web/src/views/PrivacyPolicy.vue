<template>
    <div id="privacy-policy">
        <h1 id="title" v-html="capitalize($t('settings.policy'))" :key="1" />

        <div id="policy-content">
            <transition name="fade" mode="out-in">
                <link-loading v-if="!privacyPolicy" :key="0" />
                <p id="policy-text" v-if="privacyPolicy" v-html="privacyPolicy" :key="2" />
            </transition>
        </div>
    </div>
</template>

<script>
    import { mapState } from 'vuex';
    import LinkLoading  from '../components/Loading';

    export default {
        name: 'link-privacy-policy',
        components: { LinkLoading },

        mounted() {
            this.$store.dispatch('fetchPrivacyPolicy');
        },
        methods: {
            capitalize(str) {
                if (!str) {
                    return str;
                }

                return str[0].toUpperCase() + str.substring(1);
            }
        },
        computed: mapState(['privacyPolicy'])
    }
</script>

<style lang="scss">
    #privacy-policy {
        overflow: auto;

        display: flex;
        flex-direction: column;
        align-items: center;

        padding: 15px 20px;
        padding-bottom: 50px;

        box-sizing: border-box;
    }

    #policy-content {
        flex-grow: 1;

        display: flex;
        justify-content: center;
        align-items: center;
    }

    #policy-text {
        text-align: center;
    }
</style>