<template>
    <div id="not-found">
        <h1 id="title">404</h1>
        <span id="description" v-html="$t('notFound.description')" />
    </div>
</template>

<script>
    export default {
        name: 'link-not-found'
    }
</script>

<style lang="scss" scoped>
    @import '../styles/fonts';

    #not-found {
        display: flex;
        flex-direction: column;
        justify-content: space-evenly;
        align-items: center;

        padding: 50px;
    }

    #title {
        @include lato(bold);
        font-size: 112px;

        margin: 0;
    }

    #description {
        font-size: 25px;
    }
</style>