<template>
    <div class="error">
        <h1 class="title" v-html="$t('error.title')" />
        <p class="message">{{ error }}</p>

        <a class="action" @click="action" v-html="$t(message)" />
    </div>
</template>

<script>
    export default {
        name: 'link-error',
        props: ['error', 'message'],

        methods: {
            action() {
                this.$emit('action');
            }
        }
    }
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

        height: $content-height;
    }

    .title {
        margin-top: 0;

        font-size: 38px;
    }

    .message {
        text-align: center;

        font-style: italic;
        color: #C01616;
    }

    .action {
        margin-top: 25px;

        font-size: 22px;
    }
</style>