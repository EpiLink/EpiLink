<template>
    <div id="success">
        <link-check />

        <p id="message">
            {{ $t('success.created') }}<br />
            {{ $t('success.access') }}

            <span id="close" v-html="$t('success.close')" />
        </p>

        <router-link id="profile" :to="{ name: 'profile' }" v-html="$t('success.profile')" />
    </div>
</template>

<script>
    import LinkCheck from '../components/Check';

    export default {
        name: 'link-success',
        components: { LinkCheck }
    };
</script>

<style lang="scss" scoped>
    #success {
        display: flex;
        justify-content: space-evenly;
        align-items: center;
        flex-direction: column;

        padding: 20px 45px;
    }

    #message {
        text-align: center;
        font-size: 19px;

        #close {
            display: block;
            margin-top: 10px;

            font-weight: bold;
        }
    }

    #profile {
        text-decoration: underline;
    }
</style>